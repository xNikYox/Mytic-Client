// Friends-Server als Cloudflare Worker. Die Daten liegen in einem Durable Object mit eingebautem SQLite
// (eine Instanz "main"), siehe deploy.mjs.
import { DurableObject } from "cloudflare:workers";
import { createApi } from "./api.js";
import { SCHEMA, MIGRATIONS } from "./schema.js";
import { EmoteHub } from "./live.js";

export class FriendsStore extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    const sql = ctx.storage.sql;
    for (const statement of SCHEMA.split(";").map((s) => s.trim()).filter(Boolean)) sql.exec(statement);
    for (const statement of MIGRATIONS) {
      try {
        sql.exec(statement);
      } catch {
        // schon vorhanden
      }
    }
    const db = {
      all: async (query, params = []) => sql.exec(query, ...params).toArray(),
      get: async (query, params = []) => sql.exec(query, ...params).toArray()[0] || null,
      run: async (query, params = []) => sql.exec(query, ...params),
    };
    // Offline-Konten gibt es auf Cloudflare nie (nur Entwicklerversion unter Node) – ALLOW_OFFLINE wird ignoriert
    if (env.ALLOW_OFFLINE) console.warn("WARNUNG: ALLOW_OFFLINE ist gesetzt, wird auf Cloudflare aber ignoriert – Offline-Konten bleiben aus");
    // Alle Anfragen landen in diesem einen Durable Object, daher gilt die Bremse je Adresse (CF-Connecting-IP) überall
    this.api = createApi(db, { allowOffline: false, adminKey: env.ADMIN_KEY, ipHeader: "cf-connecting-ip", log: (line) => console.log(line) });
    // Emotes in Echtzeit: WebSockets mit Hibernation – der Zustand jeder Verbindung steht in ihrem Anhang, das Objekt
    // darf also zwischen zwei Nachrichten schlafen. "ping" beantwortet Cloudflare selbst, ohne das Objekt zu wecken.
    this.hub = new EmoteHub({
      sockets: {
        all: () => ctx.getWebSockets(),
        meta: (ws) => {
          try {
            return ws.deserializeAttachment() || null;
          } catch {
            return null;
          }
        },
        save: (ws, meta) => {
          try {
            ws.serializeAttachment(meta);
          } catch {
            // Verbindung schon zu
          }
        },
        send: (ws, text) => {
          try {
            ws.send(text);
          } catch {
            // Verbindung schon zu
          }
        },
        close: (ws, code, reason) => {
          try {
            ws.close(code, reason);
          } catch {
            // schon zu
          }
        },
      },
      ownsEmote: this.api.ownsEmote,
    });
    try {
      ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
    } catch {
      // ältere Laufzeit: dann beantwortet der EmoteHub "ping"
    }
  }

  async fetch(request) {
    if (/\/api\/live\/?$/.test(new URL(request.url).pathname)) return this.live(request);
    return this.api(request);
  }

  /** GET /api/live: WebSocket für Emotes (Anmeldung wie sonst mit "Authorization: Bearer …"). */
  async live(request) {
    if ((request.headers.get("upgrade") || "").toLowerCase() !== "websocket") return Response.json({ error: "WebSocket erwartet" }, { status: 426 });
    const user = await this.api.liveAuth(request);
    if (user.response) return user.response;
    const [client, server] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(server);
    await this.hub.opened(server, user);
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, message) {
    await this.hub.message(ws, typeof message === "string" ? message : null);
  }

  async webSocketClose(ws, code) {
    try {
      ws.close(code === 1005 || code === 1006 ? 1000 : code, "");
    } catch {
      // schon zu
    }
  }
}

export default {
  async fetch(request, env) {
    return env.FRIENDS.get(env.FRIENDS.idFromName("main")).fetch(request);
  },
};
