// Friends-Server als Cloudflare Worker. Die Daten liegen in einem Durable Object mit eingebautem SQLite
// (eine Instanz "main"), siehe deploy.mjs.
import { DurableObject } from "cloudflare:workers";
import { createApi } from "./api.js";
import { SCHEMA, MIGRATIONS } from "./schema.js";

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
  }

  async fetch(request) {
    return this.api(request);
  }
}

export default {
  async fetch(request, env) {
    return env.FRIENDS.get(env.FRIENDS.idFromName("main")).fetch(request);
  },
};
