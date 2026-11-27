// Echtzeit-Kanal für Emotes (GET /api/live, WebSocket). Läuft unter Node (ws-node.js) und im Durable Object
// (worker.js, WebSocket-Hibernation) – die Logik hier kennt nur einen kleinen Adapter für die Verbindungen.
//
// Warum WebSocket? Auf dem Cloudflare-Free-Plan zählt jede HTTP-Anfrage (100 000/Tag). Ein Emote soll in 1–2 s bei
// den anderen sein – Abfragen im Sekundentakt wären ~3 600 Anfragen je Spieler und Stunde. Beim WebSocket zählt der
// Verbindungsaufbau als eine Anfrage, eingehende Nachrichten nur zu 1/20, ausgehende gar nicht. Das Durable Object
// schläft zwischen den Nachrichten (Hibernation); "ping" beantwortet Cloudflare selbst (Auto-Response), ohne es zu wecken.
//
// Protokoll (JSON-Text, höchstens LIVE_MSG_MAX Bytes):
//   Client → Server  {"t":"srv","s":"<Hash>"|null}   aktueller Server (SHA-256 der Adresse, nie die Adresse selbst)
//                    {"t":"emote","e":"<id>"}         Emote starten (höchstens eins je EMOTE_GAP_MS)
//                    {"t":"stop"}                     Emote abgebrochen (bewegt, angegriffen)
//                    "ping"                           Lebenszeichen der Verbindung → "pong"
//   Server → Client  {"t":"hi","time":…,"emotes":[…],"gap":…}
//                    {"t":"emote","u":uuid,"n":Name,"e":id,"at":Serverzeit}  an alle anderen auf demselben Server
//                    {"t":"stop","u":uuid}
//                    {"t":"wait","ms":…}               zu schnell – Emote nicht verschickt

/**
 * Emote-Katalog. free = für alle (keine Freischaltung nötig); weitere Emotes können später mit free: false kommen und
 * werden dann auf der Admin-Seite als "emote:<id>" freigeschaltet. Länge in ms nur zur Info (die Mod kennt die Animation).
 */
export const EMOTE_CATALOG = [
  { id: "winken", name: "Winken", ms: 2000, free: true },
  { id: "jubeln", name: "Jubeln", ms: 2200, free: true },
  { id: "klatschen", name: "Klatschen", ms: 2400, free: true },
  { id: "tanzen", name: "Tanzen", ms: 4000, free: true },
  { id: "verbeugen", name: "Verbeugen", ms: 2200, free: true },
  { id: "facepalm", name: "Facepalm", ms: 2400, free: true },
  { id: "hinsetzen", name: "Hinsetzen", ms: 4000, free: true },
];
export const EMOTE_GAP_MS = 3000;      // höchstens ein Emote je 3 s und Spieler
export const STOP_GAP_MS = 500;        // Abbruch-Meldungen höchstens alle 0,5 s
export const LIVE_MSG_MAX = 256;       // längste erlaubte Nachricht
export const LIVE_PER_USER = 2;        // gleichzeitige Verbindungen je Konto (z. B. zwei Spiele); älteste fliegt raus
export const LIVE_FLOOD_MS = 10_000;   // Flutschutz: höchstens LIVE_FLOOD_MAX Nachrichten je 10 s …
export const LIVE_FLOOD_MAX = 30;      // … sonst wird die Verbindung getrennt
const SERVER_HASH = /^[0-9a-f]{16,64}$/;
const EMOTE_IDS = new Map(EMOTE_CATALOG.map((e) => [e.id, e]));

/**
 * sockets: Adapter für die offenen Verbindungen
 *   all()            → alle offenen Verbindungen
 *   meta(ws)         → gespeicherter Zustand ({ u, n, s, at, last, stop, win, cnt, ... }) oder null
 *   save(ws, meta)   → Zustand speichern (Durable Object: überlebt den Ruhezustand)
 *   send(ws, text), close(ws, code, reason)
 * ownsEmote(uuid, id) → Promise<boolean> für nicht freie Emotes.
 */
export class EmoteHub {
  constructor({ sockets, now = () => Date.now(), ownsEmote = async () => false }) {
    this.sockets = sockets;
    this.now = now;
    this.ownsEmote = ownsEmote;
  }

  /** Neue Verbindung von user ({ uuid, name }) – angemeldet ist sie schon. */
  async opened(ws, user) {
    const t = this.now();
    const meta = { u: user.uuid, n: user.name, s: null, at: t, last: 0, stop: 0, win: t, cnt: 0 };
    this.sockets.save(ws, meta);
    // höchstens LIVE_PER_USER Verbindungen je Konto: die ältesten schließen
    const mine = [];
    for (const other of this.sockets.all()) {
      const m = this.sockets.meta(other);
      if (m && m.u === user.uuid) mine.push([m.at, other]);
    }
    mine.sort((a, b) => a[0] - b[0]);
    while (mine.length > LIVE_PER_USER) {
      const [, old] = mine.shift();
      if (old !== ws) this.sockets.close(old, 4000, "Neue Verbindung");
    }
    const emotes = [];
    for (const e of EMOTE_CATALOG) if (e.free || (await this.ownsEmote(user.uuid, e.id))) emotes.push(e.id);
    this.sockets.send(ws, JSON.stringify({ t: "hi", time: t, emotes, gap: EMOTE_GAP_MS }));
  }

  /** Nachricht eines Clients (Text). */
  async message(ws, text) {
    const meta = this.sockets.meta(ws);
    if (!meta) return this.sockets.close(ws, 1008, "Unbekannt");
    const t = this.now();
    if (t - meta.win > LIVE_FLOOD_MS) {
      meta.win = t;
      meta.cnt = 0;
    }
    if (++meta.cnt > LIVE_FLOOD_MAX) return this.sockets.close(ws, 1008, "Zu viele Nachrichten");
    if (typeof text !== "string") return this.sockets.save(ws, meta); // Binärdaten werden ignoriert
    if (text === "ping") {
      this.sockets.save(ws, meta);
      return this.sockets.send(ws, "pong");
    }
    let data = null;
    if (text.length <= LIVE_MSG_MAX) {
      try {
        data = JSON.parse(text);
      } catch {
        data = null;
      }
    }
    if (!data || typeof data !== "object") return this.sockets.save(ws, meta);
    if (data.t === "srv") {
      meta.s = typeof data.s === "string" && SERVER_HASH.test(data.s) ? data.s : null;
    } else if (data.t === "emote") {
      const emote = typeof data.e === "string" ? EMOTE_IDS.get(data.e) : null;
      if (emote && meta.s) {
        const wait = meta.last + EMOTE_GAP_MS - t;
        if (wait > 0) {
          this.sockets.send(ws, JSON.stringify({ t: "wait", ms: wait }));
        } else if (emote.free || (await this.ownsEmote(meta.u, emote.id))) {
          meta.last = t;
          this.broadcast(ws, meta, { t: "emote", u: meta.u, n: meta.n, e: emote.id, at: t });
        }
      }
    } else if (data.t === "stop") {
      if (meta.s && t - meta.stop >= STOP_GAP_MS) {
        meta.stop = t;
        this.broadcast(ws, meta, { t: "stop", u: meta.u });
      }
    }
    this.sockets.save(ws, meta);
  }

  /** An alle anderen Spieler auf demselben Server (gleicher Hash), nicht an das eigene Konto. */
  broadcast(from, meta, payload) {
    const text = JSON.stringify(payload);
    let n = 0;
    for (const ws of this.sockets.all()) {
      if (ws === from) continue;
      const m = this.sockets.meta(ws);
      if (!m || m.s !== meta.s || m.u === meta.u) continue;
      this.sockets.send(ws, text);
      n++;
    }
    return n;
  }
}

/** Einfacher Adapter für Node und Tests: Zustand im Speicher. ws braucht send(text) und close(code, reason). */
export class MemorySockets {
  constructor() {
    this.map = new Map();
  }

  all() {
    return [...this.map.keys()];
  }

  meta(ws) {
    return this.map.get(ws) || null;
  }

  save(ws, meta) {
    if (this.map.has(ws) || !ws.closed) this.map.set(ws, meta);
  }

  send(ws, text) {
    try {
      ws.send(text);
    } catch {
      this.map.delete(ws);
    }
  }

  close(ws, code, reason) {
    this.map.delete(ws);
    try {
      ws.close(code, reason);
    } catch {
      // schon zu
    }
  }

  remove(ws) {
    this.map.delete(ws);
  }
}
