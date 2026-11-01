// Friends-API des Mytic Clients. Reine Web-API (Request → Response), damit derselbe Code unter Node
// (node-server.js, SQLite) und als Cloudflare Worker (worker.js, D1) läuft.
//
// Anmeldung mit Mojang-Zertifikat und signiertem Profil (mojang.js) – der Server fragt dafür nicht bei Mojang an
// (Mojang sperrt Cloudflare) und erfährt nie das Minecraft-Token.
import { headFromPng } from "./skin.js";
import { verifyLogin } from "./mojang.js";
import { MOJANG_KEYS } from "./mojang-keys.js";
import { CATALOG } from "./catalog.js";
import { ADMIN_PAGE } from "./admin-page.js";
import { START_FEN, parseFen, toFen, applyUci, status as chessStatus } from "./chess.js";

/** Ab wann ein Spieler als offline gilt (Mod und Launcher melden sich jede Minute). */
export const ONLINE_MS = 150 * 1000;
const CHALLENGE_MS = 2 * 60 * 1000;
const SESSION_MS = 30 * 24 * 60 * 60 * 1000;
const HEAD_MS = 24 * 60 * 60 * 1000;
const SETTINGS_MAX = 256 * 1024;      // Cloud-Einstellungen (JSON-Text)
const SHOT_MAX = 1900 * 1024;         // Screenshot als Base64 (≈ 1,4 MB JPEG; SQLite-Zeile max. 2 MB)
const SHOTS_PER_USER = 100;           // ältere Uploads werden gelöscht
const SHOTS_PER_DAY = 30;             // Uploads je Konto und Tag (UTC), auch gelöschte zählen
const SHOTS_TOTAL = 2000;             // Screenshots insgesamt (≤ 2000 × 1,9 MB ≈ 3,7 GB)
const MAX_FRIENDS = 300;
const MAX_PENDING = 50;
const BODY_MAX = 16 * 1024;           // Anfrage-Inhalt allgemein
export const SHOT_BODY_MAX = 2200 * 1024; // Anfrage-Inhalt beim Screenshot-Upload
const CHALLENGES_TOTAL = 5000;        // offene Anmelde-Aufgaben insgesamt
const CHALLENGES_PER_IP = 10;         // offene Anmelde-Aufgaben je Adresse
const ADMIN_KEY_MIN = 16;
// Schach-Partien unter Spielern
const GAME_INVITE_MS = 60 * 1000;       // Einladung verfällt nach 60 Sekunden (danach noch GAME_RECENT_MS als „abgelaufen“ sichtbar)
const GAME_RECENT_MS = 2 * 60 * 1000;   // beendete Partien bleiben so lange in der Liste
const GAME_ABANDON_MS = 30 * 1000; // so lange ohne Lebenszeichen (Abfrage) → Partie verloren (Absturz, Minecraft beendet)
const GAME_KEEP_MS = 7 * 24 * 60 * 60 * 1000; // abgelehnte/zurückgezogene/verfallene Einladungen danach löschen
const GAME_TIMES = [60, 180, 300, 600]; // Bedenkzeit je Spieler in Sekunden (1, 3, 5, 10 Minuten)
const GAME_COLORS = ["random", "white", "black"];
const GAME_KINDS = ["chess", "xo", "c4", "ships"]; // Schach, X und O (Tic-Tac-Toe), Vier gewinnt, Schiffe versenken
const GAME_NAMES = { chess: "Schach", xo: "X und O", c4: "Vier gewinnt", ships: "Schiffe versenken" };
const XO_ROUNDS = [1, 3, 5];             // X und O: Runden (best of)
const XO_MOVE_TIMES = [0, 5, 10, 30];    // X und O: Sekunden je Zug, 0 = ohne Limit
const XO_EMPTY = ".........";            // 3×3-Brett zeilenweise, "." frei, "x"/"o" belegt
const XO_LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
const XO_SETTLE_MAX = 10;                // höchstens so viele verpasste Runden auf einmal nachholen
const C4_ROUNDS = [1, 3, 5];             // Vier gewinnt: Runden (best of)
const C4_MOVE_TIMES = [0, 5, 10, 30];    // Vier gewinnt: Sekunden je Zug, 0 = ohne Limit
const C4_COLS = 7, C4_ROWS = 6;          // Vier gewinnt: 7 Spalten × 6 Zeilen, Zeile 0 = oben
const C4_EMPTY = ".".repeat(C4_COLS * C4_ROWS); // zeilenweise, "." frei, "a" (Weiß) / "b" (Schwarz) belegt
const SHIPS_MOVE_TIMES = [0, 10, 20, 30]; // Schiffe versenken: Sekunden je Schuss, 0 = ohne Limit
const SHIPS_SIZE = 10;                   // Schiffe versenken: 10×10-Feld, Feld = Zeile * 10 + Spalte
const SHIPS_FLEET = [5, 4, 3, 3, 2];     // Schiffslängen der Flotte
const SHIPS_SETUP_MS = 120 * 1000;       // Zeit zum Aufstellen der Flotte ab Annahme
const GAME_INVITES = 5;                 // offene ausgehende Einladungen je Spieler
const GAME_ACTIVE = 3;                  // laufende Partien je Spieler
const STAT_PLAY_MAX = 120;               // Sekunden je Spielzeit-Meldung (Client meldet etwa jede Minute)
const STAT_WINDOW_MS = 60 * 1000;       // Zeitfenster gegen zu schnelle Meldungen …
const STAT_WINDOW_SEC = 150;            // … darin höchstens so viele Sekunden gutschreiben
const STAT_SLACK_SEC = 5;               // Spielraum: Gutschrift höchstens (Zeit seit letzter Meldung + 5 s)
const STAT_SESSION_KEEP_MS = 2 * 24 * 60 * 60 * 1000; // Sitzungen (für "längste Sitzung") danach löschen
const STAT_DAYS = 30;                   // Tagesreihe in GET /api/stats
const STAT_TZ_MAX = 840;                // Zeitzone: Minuten östlich von UTC, -840 … 840
const STAT_SINGLEPLAYER = "Einzelspieler";

/** Anfragen je Adresse und Minute (pro Topf). */
export const RATE_LIMITS = {
  all: 120,      // alle API-Anfragen (auch Statistik: Start + ~1 Spielzeit-Meldung je Minute)
  auth: 20,      // Anmeldung ohne Konto (challenge, login, offline)
  upload: 10,    // Screenshot-Upload
  view: 300,     // öffentliche Screenshot-Links
  games: 240,    // Partien abfragen (GET /api/games, ~40/min je Client während einer Partie) – statt "all"
};

/**
 * Bremse gegen zu viele Anfragen pro Schlüssel (Adresse): festes Zeitfenster, höchstens maxKeys Einträge.
 * Die Map ist nach letzter Nutzung sortiert – ist sie voll, fällt der am längsten unbenutzte Eintrag weg.
 */
export class RateLimiter {
  constructor(limit, windowMs = 60 * 1000, maxKeys = 10000, now = () => Date.now()) {
    this.limit = limit;
    this.windowMs = windowMs;
    this.maxKeys = maxKeys;
    this.now = now;
    this.hits = new Map();
  }

  /** Zählt einen Treffer; gibt 0 zurück, wenn erlaubt, sonst die Wartezeit in Sekunden. */
  hit(key) {
    const t = this.now();
    let entry = this.hits.get(key);
    if (!entry || t - entry.start >= this.windowMs) entry = { start: t, n: 0 };
    this.hits.delete(key);
    entry.n++;
    this.hits.set(key, entry);
    while (this.hits.size > this.maxKeys) this.hits.delete(this.hits.keys().next().value);
    return entry.n <= this.limit ? 0 : Math.max(1, Math.ceil((entry.start + this.windowMs - t) / 1000));
  }
}

/**
 * Fehlversuche mit dem Admin-Passwort je Adresse: nach 5 Fehlern innerhalb von 15 Minuten gesperrt,
 * erst 1 Minute, bei jedem weiteren Fehler doppelt so lange (höchstens 1 Stunde).
 */
export class AdminGuard {
  constructor(now = () => Date.now(), maxKeys = 10000) {
    this.now = now;
    this.maxKeys = maxKeys;
    this.fails = new Map();
  }

  /** Sekunden, die diese Adresse noch gesperrt ist (0 = frei). */
  locked(key) {
    const entry = this.fails.get(key);
    if (!entry) return 0;
    const t = this.now();
    if (entry.until > t) return Math.ceil((entry.until - t) / 1000);
    return 0;
  }

  fail(key) {
    const t = this.now();
    let entry = this.fails.get(key);
    if (!entry || (t - entry.last > 15 * 60 * 1000 && entry.until <= t)) entry = { n: 0, last: t, until: 0 };
    this.fails.delete(key);
    entry.n++;
    entry.last = t;
    if (entry.n >= 5) entry.until = t + Math.min(60 * 60 * 1000, 60 * 1000 * 2 ** (entry.n - 5));
    this.fails.set(key, entry);
    while (this.fails.size > this.maxKeys) this.fails.delete(this.fails.keys().next().value);
  }

  success(key) {
    this.fails.delete(key);
  }
}

const NAME = /^[A-Za-z0-9_]{3,16}$/;
/** Wo der MiniMe sitzt (ohne Angabe: Schulter). */
const MINIME_POS = ["shoulder", "head", "arm", "leg"];
const UUID = /^[0-9a-f]{32}$/;
/** Freischaltungen: alles (*), Admin, MiniMe, Nametag oder ein Teil bzw. eine ganze Art (cape:*). */
const GRANT = /^(\*|admin|minime|nametag|(cape|wings|shield|aura):([a-z0-9_-]{1,32}|\*))$/;
const ID = /^[a-z0-9_-]{1,32}$/;

/** Darf jemand mit diesen Freischaltungen das haben? kind: cape, wings, shield, aura, minime, nametag. */
function owns(items, kind, id) {
  if (items.has("*")) return true;
  if (kind === "minime" || kind === "nametag" || kind === "admin") return items.has(kind);
  return items.has(`${kind}:*`) || items.has(`${kind}:${id}`);
}

/** MiniMes eines Nutzers je Stelle ({ shoulder: "Name", ... }), auch aus den alten Feldern (ein MiniMe + Position). */
function miniMeSet(row) {
  if (row.minime_set) {
    try {
      return JSON.parse(row.minime_set);
    } catch {
      return {};
    }
  }
  return row.minime ? { [row.minime_pos || "shoulder"]: row.minime } : {};
}

class HttpError extends Error {
  constructor(status, message, retryAfter = 0) {
    super(message);
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

function json(data, status = 200, retryAfter = 0) {
  const headers = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "access-control-allow-origin": "*" };
  if (retryAfter) headers["retry-after"] = String(retryAfter);
  return new Response(JSON.stringify(data), { status, headers });
}

/** Inhalt einer Anfrage als Text, höchstens max Bytes (Content-Length und tatsächlich gelesene Menge), sonst 413. */
async function readText(request, max) {
  const length = Number(request.headers.get("content-length") || 0);
  if (!(length <= max)) throw new HttpError(413, "Anfrage zu groß");
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > max) {
      reader.cancel().catch(() => {});
      throw new HttpError(413, "Anfrage zu groß");
    }
    chunks.push(value);
  }
  const all = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    all.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(all);
}

function hex(bytes) {
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomHex(n) {
  return hex(crypto.getRandomValues(new Uint8Array(n)));
}

async function sha256(text) {
  return hex(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))));
}

/** UUID eines Offline-Kontos – genau wie Minecraft: MD5("OfflinePlayer:" + name), Version 3. */
export async function offlineUuid(name) {
  const { createHash } = await import("node:crypto");
  const md5 = createHash("md5").update(`OfflinePlayer:${name}`).digest();
  md5[6] = (md5[6] & 0x0f) | 0x30;
  md5[8] = (md5[8] & 0x3f) | 0x80;
  return md5.toString("hex");
}

/** Freier Text (Server-Adresse, Version) ohne Steuerzeichen, gekürzt. */
function clean(value, max) {
  if (typeof value !== "string") return null;
  const text = value.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, max);
  return text || null;
}

/**
 * db: { all(sql, params), get(sql, params), run(sql, params) } – alle async.
 * options: { allowOffline, adminKey, fetch, now, log, ipHeader, limits, shotsPerDay, shotsTotal }
 * ipHeader: Kopfzeile mit der Adresse des Aufrufers (Cloudflare: cf-connecting-ip). Unter Node übergibt
 * node-server.js die Adresse selbst: handle(request, { ip }).
 */
export function createApi(db, options = {}) {
  const log = options.log || (() => {});
  const adminKey = typeof options.adminKey === "string" && options.adminKey.length >= ADMIN_KEY_MIN ? options.adminKey : null;
  if (options.adminKey && !adminKey) log(`WARNUNG: Admin-Passwort zu kurz (mindestens ${ADMIN_KEY_MIN} Zeichen) – Anmeldung mit Passwort ist abgeschaltet`);
  const fetchImpl = options.fetch || ((...args) => fetch(...args));
  const now = options.now || (() => Date.now());
  const allowOffline = Boolean(options.allowOffline);
  if (allowOffline) log("WARNUNG: OFFLINE-KONTEN ERLAUBT – jeder kann sich ohne Microsoft-Konto unter beliebigem Namen anmelden. Nur für die Entwicklerversion!");
  const mojangKeys = options.mojangKeys || MOJANG_KEYS;
  const shotsPerDay = options.shotsPerDay || SHOTS_PER_DAY;
  const shotsTotal = options.shotsTotal || SHOTS_TOTAL;
  const limits = { ...RATE_LIMITS, ...options.limits };
  const limiters = Object.fromEntries(Object.entries(limits).map(([name, n]) => [name, new RateLimiter(n, 60 * 1000, 10000, now)]));
  const adminGuard = new AdminGuard(now);

  async function getJson(url) {
    const response = await fetchImpl(url, { headers: { accept: "application/json", "user-agent": "MyticClient-Friends/1.0" } });
    if (response.status === 204 || response.status === 404) return null;
    if (!response.ok) throw new Error(`HTTP ${response.status} bei ${url}`);
    return response.json();
  }

  /** Kopf aus einem Skin auf textures.minecraft.net (von dort lädt auch Cloudflare). */
  async function headFromUrl(url) {
    try {
      if (!url || !/^https?:\/\/textures\.minecraft\.net\//.test(url)) return null;
      const response = await fetchImpl(url.replace(/^http:/, "https:"), { headers: { "user-agent": "MyticClient-Friends/1.0" } });
      if (!response.ok) return null;
      return await headFromPng(new Uint8Array(await response.arrayBuffer()));
    } catch (error) {
      log(`Skin nicht lesbar: ${error.message}`);
      return null;
    }
  }

  /** Kopf aus dem "textures"-Eintrag eines Mojang-Profils. */
  async function headFromProfile(profile) {
    try {
      const property = ((profile && profile.properties) || []).find((p) => p.name === "textures");
      if (!property) return null;
      const textures = JSON.parse(atob(property.value)).textures || {};
      return await headFromUrl(textures.SKIN && textures.SKIN.url);
    } catch (error) {
      log(`Skin nicht lesbar: ${error.message}`);
      return null;
    }
  }

  async function grantsOf(uuid) {
    return new Set((await db.all("SELECT item FROM grants WHERE uuid = ?", [uuid])).map((r) => r.item));
  }

  /** Gewähltes entfernen, das jemand nicht (mehr) freigeschaltet hat. */
  async function dropUnowned(uuid) {
    const items = await grantsOf(uuid);
    const row = await db.get("SELECT cape, wings, shield, aura FROM users WHERE uuid = ?", [uuid]);
    if (!row) return;
    for (const [column, kind] of [["cape", "cape"], ["wings", "wings"], ["shield", "shield"], ["aura", "aura"]]) {
      if (row[column] && !owns(items, kind, row[column])) await db.run(`UPDATE users SET ${column} = NULL WHERE uuid = ?`, [uuid]);
    }
    if (!owns(items, "minime")) await db.run("UPDATE users SET minime = NULL, minime_pos = NULL, minime_set = NULL WHERE uuid = ?", [uuid]);
    if (!owns(items, "nametag")) await db.run("UPDATE users SET nametag = NULL WHERE uuid = ?", [uuid]);
  }

  /** Admin: entweder mit dem Admin-Passwort (Kopfzeile x-admin-key) oder angemeldet mit Admin-Freischaltung. */
  async function adminAuth(request, ip) {
    const key = request.headers.get("x-admin-key");
    if (key) {
      // Raten bremsen: nach mehreren Fehlversuchen ist die Adresse eine Zeit lang gesperrt
      const wait = adminGuard.locked(ip);
      if (wait) throw new HttpError(429, "Zu viele falsche Admin-Passwörter – bitte später erneut versuchen", wait);
      if (adminKey) {
        // Vergleich über die Hashes: gleich lang, ohne frühen Abbruch
        const [x, y] = [await sha256(key), await sha256(adminKey)];
        let diff = 0;
        for (let i = 0; i < x.length; i++) diff |= x.charCodeAt(i) ^ y.charCodeAt(i);
        if (diff === 0) {
          adminGuard.success(ip);
          return { admin: true };
        }
      }
      adminGuard.fail(ip);
      log(`Falsches Admin-Passwort von ${ip}`);
    }
    if (request.headers.get("authorization")) {
      const me = await auth(request);
      if (owns(await grantsOf(me.uuid), "admin")) return me;
    }
    throw new HttpError(403, "Keine Admin-Rechte");
  }

  async function grantView(uuid) {
    const user = await db.get("SELECT uuid, name FROM users WHERE uuid = ?", [uuid]);
    return { uuid, name: user ? user.name : null, items: [...(await grantsOf(uuid))].sort() };
  }

  async function upsertUser(uuid, name, offline, head) {
    const t = now();
    const existing = await db.get("SELECT uuid FROM users WHERE uuid = ?", [uuid]);
    if (existing) {
      await db.run("UPDATE users SET name = ?, name_lower = ?, offline = ? WHERE uuid = ?", [name, name.toLowerCase(), offline ? 1 : 0, uuid]);
    } else {
      await db.run("INSERT INTO users (uuid, name, name_lower, offline, created) VALUES (?, ?, ?, ?, ?)", [uuid, name, name.toLowerCase(), offline ? 1 : 0, t]);
    }
    if (head) await db.run("UPDATE users SET head = ?, head_at = ? WHERE uuid = ?", [head, t, uuid]);
  }

  async function newSession(uuid) {
    const token = randomHex(32);
    const t = now();
    await db.run("INSERT INTO sessions (token_hash, uuid, created, used) VALUES (?, ?, ?, ?)", [await sha256(token), uuid, t, t]);
    // alte Sitzungen und Anmelde-Aufgaben aufräumen
    await db.run("DELETE FROM sessions WHERE used < ?", [t - SESSION_MS]);
    await db.run("DELETE FROM challenges WHERE created < ?", [t - CHALLENGE_MS]);
    return token;
  }

  async function auth(request) {
    const header = request.headers.get("authorization") || "";
    const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
    if (!/^[0-9a-f]{64}$/.test(token)) throw new HttpError(401, "Nicht angemeldet");
    const hash = await sha256(token);
    const session = await db.get("SELECT uuid, used FROM sessions WHERE token_hash = ?", [hash]);
    if (!session || session.used < now() - SESSION_MS) throw new HttpError(401, "Anmeldung abgelaufen");
    if (session.used < now() - 60 * 60 * 1000) await db.run("UPDATE sessions SET used = ? WHERE token_hash = ?", [now(), hash]);
    const user = await db.get("SELECT * FROM users WHERE uuid = ?", [session.uuid]);
    if (!user) throw new HttpError(401, "Konto unbekannt");
    return user;
  }

  function publicUser(row) {
    const t = now();
    return {
      uuid: row.uuid,
      name: row.name,
      head: row.head || null,
      online: row.last_seen > t - ONLINE_MS,
      place: row.last_seen > t - ONLINE_MS ? row.place || "launcher" : null,
      server: row.last_seen > t - ONLINE_MS ? row.server || null : null,
      version: row.version || null,
      lastSeen: row.last_seen || 0,
    };
  }

  async function overview(me) {
    const friends = await db.all(
      "SELECT u.* FROM friends f JOIN users u ON u.uuid = CASE WHEN f.a = ? THEN f.b ELSE f.a END WHERE f.a = ? OR f.b = ?",
      [me.uuid, me.uuid, me.uuid],
    );
    const incoming = await db.all("SELECT u.*, r.created AS at FROM requests r JOIN users u ON u.uuid = r.sender WHERE r.target = ? ORDER BY r.created DESC", [me.uuid]);
    const outgoing = await db.all("SELECT u.*, r.created AS at FROM requests r JOIN users u ON u.uuid = r.target WHERE r.sender = ? ORDER BY r.created DESC", [me.uuid]);
    const list = friends.map(publicUser).sort((x, y) => (y.online - x.online) || (y.place === "game") - (x.place === "game") || y.lastSeen - x.lastSeen || x.name.localeCompare(y.name));
    const short = (row) => ({ uuid: row.uuid, name: row.name, head: row.head || null, at: row.at });
    const playing = me.place === "game" && me.last_seen > now() - ONLINE_MS;
    return { me: { uuid: me.uuid, name: me.name, head: me.head || null, cape: me.cape || null, place: playing ? "game" : me.place || null,
      server: playing ? me.server || null : null, version: playing ? me.version || null : null }, friends: list, incoming: incoming.map(short), outgoing: outgoing.map(short), serverTime: now() };
  }

  /** JSON-Inhalt der Anfrage (höchstens max Bytes, sonst 413); kaputtes JSON gilt als leer. */
  async function body(request, max = BODY_MAX) {
    const text = await readText(request, max);
    try {
      const data = JSON.parse(text);
      return data && typeof data === "object" ? data : {};
    } catch {
      return {};
    }
  }

  async function findUser(input) {
    if (typeof input !== "string") return null;
    const value = input.trim().replace(/-/g, "").toLowerCase();
    if (UUID.test(value)) return db.get("SELECT * FROM users WHERE uuid = ?", [value]);
    if (!NAME.test(input.trim())) return null;
    // gleicher Name bei Microsoft- und Offline-Konto (nur Entwicklerversion): echtes Konto bevorzugen
    return db.get("SELECT * FROM users WHERE name_lower = ? ORDER BY offline ASC, last_seen DESC LIMIT 1", [value]);
  }

  /**
   * Spieler, der den Mytic Client noch nie gestartet hat: Namen nachschlagen und vormerken, damit die Anfrage
   * wartet, bis er sich das erste Mal anmeldet. Öffentlicher Server: Minecraft-Konto über playerdb.co
   * (Mojang selbst sperrt Cloudflare); mit Offline-Konten (Entwicklerversion): Offline-UUID des Namens.
   */
  async function placeholder(input) {
    const name = typeof input === "string" ? input.trim() : "";
    if (!NAME.test(name)) return null;
    if (allowOffline) {
      await upsertUser(await offlineUuid(name), name, true, null);
      return db.get("SELECT * FROM users WHERE uuid = ?", [await offlineUuid(name)]);
    }
    let player;
    try {
      const response = await fetchImpl(`https://playerdb.co/api/player/minecraft/${encodeURIComponent(name)}`, { headers: { "user-agent": "MyticClient-Friends/1.0" } });
      if (!response.ok) return null;
      player = ((await response.json()).data || {}).player;
    } catch (error) {
      log(`Namenssuche fehlgeschlagen: ${error.message}`);
      throw new HttpError(502, "Spielersuche gerade nicht möglich – bitte später erneut versuchen");
    }
    const uuid = String((player && player.raw_id) || "").toLowerCase();
    if (!UUID.test(uuid) || !NAME.test(String(player.username || ""))) return null;
    const existing = await db.get("SELECT * FROM users WHERE uuid = ?", [uuid]);
    if (existing) return existing;
    await upsertUser(uuid, player.username, false, await headFromUrl(player.skin_texture));
    return db.get("SELECT * FROM users WHERE uuid = ?", [uuid]);
  }

  function pair(x, y) {
    return x < y ? [x, y] : [y, x];
  }

  async function areFriends(x, y) {
    return Boolean(await db.get("SELECT 1 AS ok FROM friends WHERE a = ? AND b = ?", pair(x, y)));
  }

  async function makeFriends(x, y) {
    const [a, b] = pair(x, y);
    await db.run("INSERT OR IGNORE INTO friends (a, b, created) VALUES (?, ?, ?)", [a, b, now()]);
    await db.run("DELETE FROM requests WHERE (sender = ? AND target = ?) OR (sender = ? AND target = ?)", [x, y, y, x]);
  }

  async function count(sql, params) {
    const row = await db.get(sql, params);
    return row ? Number(Object.values(row)[0]) : 0;
  }

  // ------------------------------------------------------------------ Schach-Partien

  /** Uhren zum Zeitpunkt t: die Seite am Zug verliert seit turn_since Zeit. */
  function gameClock(row, t) {
    const clock = { white: row.white_ms, black: row.black_ms };
    if (row.status === "active") {
      const side = gameTurn(row) === "w" ? "white" : "black";
      clock[side] = Math.max(0, clock[side] - (t - row.turn_since));
    }
    return clock;
  }

  function gameMoves(row) {
    return row.moves ? row.moves.split(" ") : [];
  }

  function gameTurn(row) {
    return gameMoves(row).length % 2 ? "b" : "w";
  }

  /** Lebenszeichen: jede Anfrage eines Spielers hält seine laufenden Partien am Leben. */
  async function markSeen(uuid) {
    const t = now();
    await db.run("UPDATE games SET white_seen = ? WHERE white = ? AND status = 'active'", [t, uuid]);
    await db.run("UPDATE games SET black_seen = ? WHERE black = ? AND status = 'active'", [t, uuid]);
  }

  /** Zeitabläufe nachholen (beim Lesen und Schreiben): verfallene Einladung, abgelaufene Uhr der Seite am Zug, Spieler ohne Lebenszeichen. */
  async function settleGame(row) {
    const t = now();
    if (row.status === "invited" && row.created <= t - GAME_INVITE_MS) {
      // updated = Zeitpunkt des Verfalls: so bleibt die Einladung GAME_RECENT_MS lang als „abgelaufen“ in der Liste
      const expired = row.created + GAME_INVITE_MS;
      await db.run("UPDATE games SET status = 'expired', updated = ? WHERE id = ? AND status = 'invited'", [expired, row.id]);
      return { ...row, status: "expired", updated: expired };
    }
    if (row.status === "active" && isXo(row)) row = await settleXo(row, t);
    if (row.status === "active" && isC4(row)) row = await settleC4(row, t);
    if (row.status === "active" && isShips(row)) row = await settleShips(row, t);
    if (row.status === "active") {
      const clock = gameClock(row, t);
      const turn = gameTurn(row);
      if (isChess(row) && (turn === "w" ? clock.white : clock.black) <= 0) {
        const result = turn === "w" ? "0-1" : "1-0";
        await db.run("UPDATE games SET status = 'over', result = ?, reason = 'timeout', white_ms = ?, black_ms = ?, draw_offer = NULL, updated = ? WHERE id = ? AND status = 'active'",
          [result, clock.white, clock.black, t, row.id]);
        return { ...row, status: "over", result, reason: "timeout", white_ms: clock.white, black_ms: clock.black, draw_offer: null, updated: t };
      }
      // Absturz oder Minecraft beendet: wer länger als GAME_ABANDON_MS nichts von sich hören ließ, verliert
      const seenW = row.white_seen || row.turn_since, seenB = row.black_seen || row.turn_since;
      const goneW = t - seenW > GAME_ABANDON_MS, goneB = t - seenB > GAME_ABANDON_MS;
      if (goneW || goneB) {
        const whiteLost = goneW && (!goneB || seenW <= seenB);
        const result = whiteLost ? "0-1" : "1-0";
        await db.run("UPDATE games SET status = 'over', result = ?, reason = 'abandoned', white_ms = ?, black_ms = ?, draw_offer = NULL, updated = ? WHERE id = ? AND status = 'active'",
          [result, clock.white, clock.black, t, row.id]);
        return { ...row, status: "over", result, reason: "abandoned", white_ms: clock.white, black_ms: clock.black, draw_offer: null, updated: t };
      }
    }
    return row;
  }

  // ------------------------------------------------------------------ X und O

  function isXo(row) {
    return row.kind === "xo";
  }

  /** Schach: auch alte Partien ohne kind; nur hier laufen Uhren und Remis-Angebote. */
  function isChess(row) {
    return !GAME_KINDS.includes(row.kind) || row.kind === "chess";
  }

  /** Spielstand vor der ersten Runde: X beginnt. */
  function xoInitial() {
    return { board: XO_EMPTY, round: 1, starter: "x", turn: "x", score: { x: 0, o: 0 }, results: [] };
  }

  /** Spielstand aus der Spalte state (JSON); Einladung oder kaputt → Anfangsstand. */
  function xoState(row) {
    try {
      const state = row.state ? JSON.parse(row.state) : null;
      if (state && typeof state.board === "string") return state;
    } catch {
      // kaputt → Anfangsstand
    }
    return xoInitial();
  }

  /** Anzahl Zeichen auf dem Brett der laufenden Runde (= ply beim Zug). */
  function xoPly(state) {
    return state.board.replace(/\./g, "").length;
  }

  function xoWinLine(board) {
    return XO_LINES.find(([a, b, c]) => board[a] !== "." && board[a] === board[b] && board[a] === board[c]) || null;
  }

  /** Runde beenden (Ergebnis samt Endbrett in results); winner = "x"/"o", sobald einer die Partie gewonnen hat, sonst nächste Runde. */
  function xoFinishRound(state, rounds, entry) {
    const next = { ...state, score: { ...state.score }, results: [...state.results, { ...entry, board: state.board }] };
    if (entry.winner) next.score[entry.winner]++;
    const need = Math.ceil(rounds / 2);
    if (next.score.x >= need || next.score.o >= need) return { state: next, winner: next.score.x >= need ? "x" : "o" };
    // Unentschieden zählt nicht; nächste Runde, Anfang wechselt (x, o, x …)
    next.board = XO_EMPTY;
    next.round++;
    next.starter = state.starter === "x" ? "o" : "x";
    next.turn = next.starter;
    return { state: next, winner: null };
  }

  /** Zeit je Zug: wer zu lange braucht, verliert die Runde (nachgeholt, höchstens XO_SETTLE_MAX Runden). */
  async function settleXo(row, t) {
    const limit = (row.move_s || 0) * 1000;
    if (!limit) return row;
    let state = xoState(row);
    let since = row.turn_since;
    let winner = null;
    for (let i = 0; i < XO_SETTLE_MAX && !winner && t - since > limit; i++) {
      const loser = state.turn;
      since += limit; // nächste Runde beginnt mit dem Ablauf, nicht erst beim Nachholen
      ({ state, winner } = xoFinishRound(state, row.rounds, { winner: loser === "x" ? "o" : "x", reason: "timeout", line: null }));
    }
    if (since === row.turn_since) return row;
    // nur anwenden, wenn niemand anderes inzwischen nachgeholt oder gezogen hat (turn_since unverändert)
    if (winner) {
      await db.run("UPDATE games SET status = 'over', result = ?, reason = 'rounds', state = ?, turn_since = ?, draw_offer = NULL, updated = ? WHERE id = ? AND status = 'active' AND turn_since = ?",
        [winner === "x" ? "1-0" : "0-1", JSON.stringify(state), since, t, row.id, row.turn_since]);
    } else {
      await db.run("UPDATE games SET state = ?, turn_since = ?, updated = ? WHERE id = ? AND status = 'active' AND turn_since = ?",
        [JSON.stringify(state), since, t, row.id, row.turn_since]);
    }
    return db.get("SELECT * FROM games WHERE id = ?", [row.id]);
  }

  /** X und O: Zeichen setzen; Reihe → Punkt, volles Brett → Unentschieden; danach nächste Runde oder Partie-Ende. */
  async function xoMove(row, side, data) {
    const state = xoState(row);
    if (data.ply !== xoPly(state)) throw new HttpError(409, "Partie hat sich geändert – bitte neu laden");
    if (state.turn !== side) throw new HttpError(409, "Du bist nicht am Zug");
    const cell = typeof data.move === "string" && /^[0-8]$/.test(data.move) ? Number(data.move) : data.move;
    if (!Number.isInteger(cell) || cell < 0 || cell > 8) throw new HttpError(400, "Ungültiges Feld");
    if (state.board[cell] !== ".") throw new HttpError(400, "Feld ist belegt");
    const t = now();
    const board = state.board.slice(0, cell) + side + state.board.slice(cell + 1);
    let next = { ...state, board, turn: side === "x" ? "o" : "x" };
    let winner = null;
    const line = xoWinLine(board);
    if (line || !board.includes(".")) {
      const entry = line ? { winner: side, reason: "line", line } : { winner: null, reason: "draw", line: null };
      ({ state: next, winner } = xoFinishRound(next, row.rounds, entry));
    }
    const updated = { ...row, state: JSON.stringify(next), turn_since: t, draw_offer: null, updated: t };
    if (winner) return endGame(updated, winner === "x" ? "1-0" : "0-1", "rounds", updated);
    await db.run("UPDATE games SET state = ?, turn_since = ?, draw_offer = NULL, updated = ? WHERE id = ?", [updated.state, t, t, row.id]);
    return updated;
  }

  // ------------------------------------------------------------------ Vier gewinnt

  function isC4(row) {
    return row.kind === "c4";
  }

  /** Spielstand vor der ersten Runde: a (Weiß) beginnt. */
  function c4Initial() {
    return { board: C4_EMPTY, round: 1, starter: "a", turn: "a", score: { a: 0, b: 0 }, results: [] };
  }

  /** Spielstand aus der Spalte state (JSON); Einladung oder kaputt → Anfangsstand. */
  function c4State(row) {
    try {
      const state = row.state ? JSON.parse(row.state) : null;
      if (state && typeof state.board === "string" && state.board.length === C4_COLS * C4_ROWS) return state;
    } catch {
      // kaputt → Anfangsstand
    }
    return c4Initial();
  }

  /** Anzahl Steine auf dem Brett der laufenden Runde (= ply beim Zug). */
  function c4Ply(state) {
    return state.board.replace(/\./g, "").length;
  }

  /** Vier in einer Reihe durch das Feld cell (waagrecht, senkrecht, beide Diagonalen) → 4 Feld-Indizes, sonst null. */
  function c4WinLine(board, cell) {
    const side = board[cell];
    const row = Math.floor(cell / C4_COLS), col = cell % C4_COLS;
    for (const [dr, dc] of [[0, 1], [1, 0], [1, 1], [1, -1]]) {
      // Anfang der Kette in Gegenrichtung suchen, dann die Kette vorwärts sammeln
      let r = row, c = col;
      while (r - dr >= 0 && r - dr < C4_ROWS && c - dc >= 0 && c - dc < C4_COLS && board[(r - dr) * C4_COLS + (c - dc)] === side) { r -= dr; c -= dc; }
      const run = [];
      while (r >= 0 && r < C4_ROWS && c >= 0 && c < C4_COLS && board[r * C4_COLS + c] === side) { run.push(r * C4_COLS + c); r += dr; c += dc; }
      if (run.length >= 4) {
        // längere Kette: das erste Viererstück, das den gesetzten Stein enthält
        const at = run.indexOf(cell);
        const start = Math.max(0, at - 3);
        return run.slice(start, start + 4);
      }
    }
    return null;
  }

  /** Runde beenden (Ergebnis samt Endbrett in results); winner = "a"/"b", sobald einer die Partie gewonnen hat, sonst nächste Runde. */
  function c4FinishRound(state, rounds, entry) {
    const next = { ...state, score: { ...state.score }, results: [...state.results, { ...entry, board: state.board }] };
    if (entry.winner) next.score[entry.winner]++;
    const need = Math.ceil(rounds / 2);
    if (next.score.a >= need || next.score.b >= need) return { state: next, winner: next.score.a >= need ? "a" : "b" };
    // Unentschieden zählt nicht; nächste Runde, Anfang wechselt (a, b, a …)
    next.board = C4_EMPTY;
    next.round++;
    next.starter = state.starter === "a" ? "b" : "a";
    next.turn = next.starter;
    return { state: next, winner: null };
  }

  /** Zeit je Zug: wer zu lange braucht, verliert die Runde (nachgeholt, höchstens XO_SETTLE_MAX Runden). */
  async function settleC4(row, t) {
    const limit = (row.move_s || 0) * 1000;
    if (!limit) return row;
    let state = c4State(row);
    let since = row.turn_since;
    let winner = null;
    for (let i = 0; i < XO_SETTLE_MAX && !winner && t - since > limit; i++) {
      const loser = state.turn;
      since += limit; // nächste Runde beginnt mit dem Ablauf, nicht erst beim Nachholen
      ({ state, winner } = c4FinishRound(state, row.rounds, { winner: loser === "a" ? "b" : "a", reason: "timeout", line: null }));
    }
    if (since === row.turn_since) return row;
    // nur anwenden, wenn niemand anderes inzwischen nachgeholt oder gezogen hat (turn_since unverändert)
    if (winner) {
      await db.run("UPDATE games SET status = 'over', result = ?, reason = 'rounds', state = ?, turn_since = ?, draw_offer = NULL, updated = ? WHERE id = ? AND status = 'active' AND turn_since = ?",
        [winner === "a" ? "1-0" : "0-1", JSON.stringify(state), since, t, row.id, row.turn_since]);
    } else {
      await db.run("UPDATE games SET state = ?, turn_since = ?, updated = ? WHERE id = ? AND status = 'active' AND turn_since = ?",
        [JSON.stringify(state), since, t, row.id, row.turn_since]);
    }
    return db.get("SELECT * FROM games WHERE id = ?", [row.id]);
  }

  /** Vier gewinnt: Stein in Spalte werfen (fällt nach unten); Vierer → Punkt, volles Brett → Unentschieden; danach nächste Runde oder Partie-Ende. */
  async function c4Move(row, side, data) {
    const state = c4State(row);
    if (data.ply !== c4Ply(state)) throw new HttpError(409, "Partie hat sich geändert – bitte neu laden");
    if (state.turn !== side) throw new HttpError(409, "Du bist nicht am Zug");
    const col = typeof data.move === "string" && /^[0-6]$/.test(data.move) ? Number(data.move) : data.move;
    if (!Number.isInteger(col) || col < 0 || col >= C4_COLS) throw new HttpError(400, "Ungültige Spalte");
    // tiefste freie Zeile (Zeile 0 = oben)
    let cell = -1;
    for (let r = C4_ROWS - 1; r >= 0 && cell < 0; r--) if (state.board[r * C4_COLS + col] === ".") cell = r * C4_COLS + col;
    if (cell < 0) throw new HttpError(400, "Spalte ist voll");
    const t = now();
    const board = state.board.slice(0, cell) + side + state.board.slice(cell + 1);
    let next = { ...state, board, turn: side === "a" ? "b" : "a" };
    let winner = null;
    const line = c4WinLine(board, cell);
    if (line || !board.includes(".")) {
      const entry = line ? { winner: side, reason: "line", line } : { winner: null, reason: "draw", line: null };
      ({ state: next, winner } = c4FinishRound(next, row.rounds, entry));
    }
    const updated = { ...row, state: JSON.stringify(next), turn_since: t, draw_offer: null, updated: t };
    if (winner) return endGame(updated, winner === "a" ? "1-0" : "0-1", "rounds", updated);
    await db.run("UPDATE games SET state = ?, turn_since = ?, draw_offer = NULL, updated = ? WHERE id = ?", [updated.state, t, t, row.id]);
    return updated;
  }

  // ------------------------------------------------------------------ Schiffe versenken

  function isShips(row) {
    return row.kind === "ships";
  }

  /**
   * Spielstand: phase "setup" (Flotten aufstellen) | "battle"; fleets je Farbe (Liste von Schiffen = Feld-Indizes, null = noch nicht gesetzt);
   * shots je Farbe = eigene Schüsse aufs gegnerische Feld { "<feld>": "h" Treffer | "m" Wasser | "a" automatisch Wasser (neben versenktem Schiff) };
   * turn = Farbe am Schuss; last = letzter Schuss { by: "w"|"b", cell, result: "h"|"m"|"s" }.
   * Während der Aufstellung zählt turn_since die Aufstellzeit (seit Annahme), danach die Zeit je Schuss.
   */
  function shipsInitial() {
    return { phase: "setup", fleets: { w: null, b: null }, shots: { w: {}, b: {} }, turn: "w", last: null };
  }

  function shipsState(row) {
    try {
      const state = row.state ? JSON.parse(row.state) : null;
      if (state && state.fleets && state.shots) return state;
    } catch {
      // kaputt → Anfangsstand
    }
    return shipsInitial();
  }

  /** Echte Schüsse einer Farbe (ohne automatische Wasserfelder) = ply beim Schuss. */
  function shipsPly(shots) {
    return Object.values(shots).filter((v) => v !== "a").length;
  }

  /** Schiffe der Flotte, die durch diese Schüsse komplett getroffen sind. */
  function shipsSunk(fleet, shots) {
    return (fleet || []).filter((ship) => ship.every((cell) => shots[cell] === "h"));
  }

  /** Nachbarfelder (8 Richtungen, innerhalb des Felds) einer Zelle. */
  function shipsAround(cell) {
    const row = Math.floor(cell / SHIPS_SIZE), col = cell % SHIPS_SIZE;
    const result = [];
    for (let dr = -1; dr <= 1; dr++) {
      for (let dc = -1; dc <= 1; dc++) {
        const r = row + dr, c = col + dc;
        if ((dr || dc) && r >= 0 && r < SHIPS_SIZE && c >= 0 && c < SHIPS_SIZE) result.push(r * SHIPS_SIZE + c);
      }
    }
    return result;
  }

  /** Flotte prüfen: 5 gerade, zusammenhängende Schiffe (5, 4, 3, 3, 2) im Feld, ohne Überschneidung und ohne Berührung (auch nicht diagonal). */
  function shipsFleet(ships) {
    if (!Array.isArray(ships) || ships.length !== SHIPS_FLEET.length || !ships.every(Array.isArray)) throw new HttpError(400, "Flotte muss aus 5 Schiffen bestehen");
    const sizes = ships.map((s) => s.length).sort((x, y) => y - x);
    if (sizes.join(",") !== SHIPS_FLEET.join(",")) throw new HttpError(400, "Flotte muss aus Schiffen der Längen 5, 4, 3, 3 und 2 bestehen");
    const fleet = [];
    for (const ship of ships) {
      if (!ship.every((c) => Number.isInteger(c))) throw new HttpError(400, "Ungültiges Feld");
      if (!ship.every((c) => c >= 0 && c < SHIPS_SIZE * SHIPS_SIZE)) throw new HttpError(400, "Schiff liegt außerhalb des Felds");
      const cells = [...ship].sort((x, y) => x - y);
      const rows = cells.map((c) => Math.floor(c / SHIPS_SIZE)), cols = cells.map((c) => c % SHIPS_SIZE);
      const across = rows.every((r) => r === rows[0]) && cols.every((c, i) => c === cols[0] + i);
      const down = cols.every((c) => c === cols[0]) && rows.every((r, i) => r === rows[0] + i);
      if (!across && !down) {
        // fortlaufende Nummern über das Zeilenende hinaus: ragt über den Rand
        if (cells.every((c, i) => c === cells[0] + i)) throw new HttpError(400, "Schiff liegt außerhalb des Felds");
        throw new HttpError(400, "Schiffe müssen gerade und zusammenhängend sein");
      }
      fleet.push(cells);
    }
    const owner = new Map();
    fleet.forEach((ship, i) => ship.forEach((c) => {
      if (owner.has(c)) throw new HttpError(400, "Schiffe dürfen sich nicht überschneiden");
      owner.set(c, i);
    }));
    fleet.forEach((ship, i) => ship.forEach((c) => {
      if (shipsAround(c).some((n) => owner.has(n) && owner.get(n) !== i)) throw new HttpError(400, "Schiffe dürfen sich nicht berühren (auch nicht diagonal)");
    }));
    return fleet;
  }

  /** Aufstellzeit und Zeit je Schuss nachholen: ohne Flotte nach SHIPS_SETUP_MS verloren (beide ohne → abgebrochen); Schusszeit abgelaufen → Gegner ist dran. */
  async function settleShips(row, t) {
    const state = shipsState(row);
    if (state.phase === "setup") {
      if (t - row.turn_since <= SHIPS_SETUP_MS) return row;
      const readyW = Boolean(state.fleets.w), readyB = Boolean(state.fleets.b);
      const end = row.turn_since + SHIPS_SETUP_MS;
      if (!readyW && !readyB) {
        await db.run("UPDATE games SET status = 'cancelled', reason = 'setup', draw_offer = NULL, updated = ? WHERE id = ? AND status = 'active' AND turn_since = ?", [end, row.id, row.turn_since]);
      } else {
        await db.run("UPDATE games SET status = 'over', result = ?, reason = 'setup', draw_offer = NULL, updated = ? WHERE id = ? AND status = 'active' AND turn_since = ?",
          [readyW ? "1-0" : "0-1", t, row.id, row.turn_since]);
      }
      return db.get("SELECT * FROM games WHERE id = ?", [row.id]);
    }
    const limit = (row.move_s || 0) * 1000;
    if (!limit) return row;
    let since = row.turn_since;
    let turn = state.turn;
    // jede abgelaufene Schusszeit gibt den Schuss ab (kein Punktverlust), höchstens XO_SETTLE_MAX auf einmal
    for (let i = 0; i < XO_SETTLE_MAX && t - since > limit; i++) {
      since += limit;
      turn = turn === "w" ? "b" : "w";
    }
    if (since === row.turn_since) return row;
    await db.run("UPDATE games SET state = ?, turn_since = ?, updated = ? WHERE id = ? AND status = 'active' AND turn_since = ?",
      [JSON.stringify({ ...state, turn }), since, t, row.id, row.turn_since]);
    return db.get("SELECT * FROM games WHERE id = ?", [row.id]);
  }

  /** Eigene Flotte setzen (endgültig); stehen beide, beginnt die Schlacht mit Weiß. */
  async function shipsSetFleet(row, color, data) {
    const state = shipsState(row);
    if (state.phase !== "setup") throw new HttpError(409, "Die Flotten stehen schon");
    if (state.fleets[color]) throw new HttpError(409, "Deine Flotte steht schon");
    const fleet = shipsFleet(data && data.ships);
    const t = now();
    const next = { ...state, fleets: { ...state.fleets, [color]: fleet } };
    const both = next.fleets.w && next.fleets.b;
    if (both) {
      next.phase = "battle";
      next.turn = "w";
    }
    // nur anwenden, wenn der Spielstand unverändert ist (beide setzen gleichzeitig)
    await db.run("UPDATE games SET state = ?, turn_since = ?, updated = ? WHERE id = ? AND status = 'active' AND state IS ?",
      [JSON.stringify(next), both ? t : row.turn_since, t, row.id, row.state]);
    const saved = await db.get("SELECT * FROM games WHERE id = ?", [row.id]);
    if (!shipsState(saved).fleets[color]) throw new HttpError(409, "Partie hat sich geändert – bitte neu laden");
    return saved;
  }

  /** Schuss aufs gegnerische Feld: Treffer → nochmal, Wasser → Gegner ist dran; versenkt → Umgebung automatisch Wasser; alle versenkt → gewonnen. */
  async function shipsShoot(row, color, data) {
    const state = shipsState(row);
    if (state.phase !== "battle") throw new HttpError(409, "Die Flotten werden noch aufgestellt");
    const mine = state.shots[color] || {};
    if (data.ply !== shipsPly(mine)) throw new HttpError(409, "Partie hat sich geändert – bitte neu laden");
    if (state.turn !== color) throw new HttpError(409, "Du bist nicht am Zug");
    const cell = typeof data.move === "string" && /^\d{1,2}$/.test(data.move) ? Number(data.move) : data.move;
    if (!Number.isInteger(cell) || cell < 0 || cell >= SHIPS_SIZE * SHIPS_SIZE) throw new HttpError(400, "Ungültiges Feld");
    if (mine[cell]) throw new HttpError(400, "Feld schon beschossen");
    const op = color === "w" ? "b" : "w";
    const fleet = state.fleets[op] || [];
    const ship = fleet.find((s) => s.includes(cell));
    const shots = { ...mine, [cell]: ship ? "h" : "m" };
    let result = ship ? "h" : "m";
    if (ship && ship.every((c) => shots[c] === "h")) {
      result = "s";
      for (const c of ship) for (const n of shipsAround(c)) if (!shots[n]) shots[n] = "a";
    }
    const t = now();
    const next = { ...state, shots: { ...state.shots, [color]: shots }, turn: ship ? color : op, last: { by: color, cell, result } };
    const updated = { ...row, state: JSON.stringify(next), turn_since: t, draw_offer: null, updated: t };
    if (shipsSunk(fleet, shots).length === fleet.length) return endGame(updated, color === "w" ? "1-0" : "0-1", "sunk", updated);
    await db.run("UPDATE games SET state = ?, turn_since = ?, draw_offer = NULL, updated = ? WHERE id = ?", [updated.state, t, t, row.id]);
    return updated;
  }

  /** Sicht eines Spielers (viewer = uuid): die gegnerische Flotte bleibt bis zum Ende geheim, nur versenkte Schiffe werden gezeigt. */
  function shipsView(row, viewer, t) {
    const state = shipsState(row);
    const me = row.white && row.white === viewer ? "w" : row.black && row.black === viewer ? "b" : null;
    const op = me === "w" ? "b" : me === "b" ? "w" : null;
    const myFleet = (me && state.fleets[me]) || [];
    const opFleet = (op && state.fleets[op]) || [];
    const myShots = (me && state.shots[me]) || {};
    const opShots = (op && state.shots[op]) || {};
    const active = row.status === "active";
    const limit = (row.move_s || 0) * 1000;
    const last = state.last && me ? { by: state.last.by === me ? "me" : "op", cell: state.last.cell, result: state.last.result } : null;
    return {
      phase: state.phase,
      myReady: Boolean(me && state.fleets[me]),
      opReady: Boolean(op && state.fleets[op]),
      setupLeft: active && state.phase === "setup" ? Math.max(0, SHIPS_SETUP_MS - (t - row.turn_since)) : null,
      myFleet,
      myShots,
      opShots,
      mySunk: shipsSunk(myFleet, opShots),
      theirSunk: shipsSunk(opFleet, myShots),
      opFleet: row.status === "over" && op ? state.fleets[op] || null : null,
      turn: state.turn,
      moveLeft: active && state.phase === "battle" && limit ? Math.max(0, limit - (t - row.turn_since)) : null,
      lastShot: last,
    };
  }

  /** Partie, an der ich beteiligt bin (sonst 404), mit nachgeholten Zeitabläufen. */
  async function loadGame(id, me) {
    await markSeen(me.uuid);
    const row = await db.get("SELECT * FROM games WHERE id = ? AND (inviter = ? OR invitee = ?)", [String(id), me.uuid, me.uuid]);
    if (!row) throw new HttpError(404, "Partie nicht gefunden");
    return settleGame(row);
  }

  /** Meine Einladungen und laufenden Partien (Zeitabläufe nachgeholt). */
  async function openGames(uuid) {
    const rows = await db.all("SELECT * FROM games WHERE (inviter = ? OR invitee = ?) AND status IN ('invited', 'active')", [uuid, uuid]);
    const result = [];
    for (const row of rows) result.push(await settleGame(row));
    return result;
  }

  /** Partien für die Antwort; viewer = uuid des Abfragenden (Schiffe versenken zeigt je Spieler nur, was er sehen darf). */
  async function gameViews(rows, viewer) {
    const uuids = [...new Set(rows.flatMap((r) => [r.inviter, r.invitee]))];
    const names = new Map();
    if (uuids.length) {
      for (const u of await db.all(`SELECT uuid, name FROM users WHERE uuid IN (${uuids.map(() => "?").join(",")})`, uuids)) names.set(u.uuid, u.name);
    }
    const t = now();
    const person = (uuid) => (uuid ? { uuid, name: names.get(uuid) || null } : null);
    return rows.map((row) => {
      const xo = isXo(row) ? xoState(row) : null;
      const c4 = isC4(row) ? c4State(row) : null;
      const ships = isShips(row) ? shipsView(row, viewer, t) : null;
      const chess = !xo && !c4 && !ships;
      const limit = (row.move_s || 0) * 1000;
      const moveLeft = row.status === "active" && limit ? Math.max(0, limit - (t - row.turn_since)) : null;
      const view = {
        id: row.id,
        kind: xo ? "xo" : c4 ? "c4" : ships ? "ships" : "chess",
        status: row.status,
        inviter: person(row.inviter),
        invitee: person(row.invitee),
        white: person(row.white),   // X und O: Spieler X
        black: person(row.black),   // X und O: Spieler O
        color: row.color,
        time: row.time_s,
        rounds: xo || c4 ? row.rounds : null,
        moveTime: chess ? null : row.move_s,
        moves: gameMoves(row),
        fen: chess ? row.fen : null,
        turn: xo ? (xo.turn === "x" ? "w" : "b") : c4 ? (c4.turn === "a" ? "w" : "b") : ships ? ships.turn : gameTurn(row),
        clock: chess ? gameClock(row, t) : null,
        drawOffer: row.draw_offer || null,
        result: row.result || null,
        reason: row.reason || null,
        xo: xo && { ...xo, moveLeft },
        created: row.created,
        updated: row.updated,
      };
      // neue Spiele bekommen ihr eigenes Feld; Schach und X und O bleiben unverändert
      if (c4) view.c4 = { ...c4, moveLeft };
      if (ships) view.ships = ships;
      return view;
    });
  }

  async function gameResponse(row, viewer) {
    return { game: (await gameViews([row], viewer))[0], serverTime: now() };
  }

  /** Partie beenden: result "1-0" | "0-1" | "1/2-1/2". */
  async function endGame(row, result, reason, extra = {}) {
    const t = now();
    const clock = gameClock(row, t);
    const next = { ...row, white_ms: clock.white, black_ms: clock.black, ...extra, status: "over", result, reason, draw_offer: null, updated: t };
    await db.run("UPDATE games SET status = 'over', result = ?, reason = ?, white_ms = ?, black_ms = ?, moves = ?, fen = ?, state = ?, draw_offer = NULL, updated = ? WHERE id = ?",
      [result, reason, next.white_ms, next.black_ms, next.moves, next.fen, next.state ?? null, t, row.id]);
    return next;
  }

  /** Einladung annehmen: Farben verteilen (Zufall = 50/50; X und O: Weiß = X; Vier gewinnt: Weiß = a), Uhren bzw. erste Runde oder Aufstellen starten. */
  async function startGame(row) {
    for (const uuid of [row.inviter, row.invitee]) {
      if ((await openGames(uuid)).filter((g) => g.status === "active").length >= GAME_ACTIVE) throw new HttpError(429, `Höchstens ${GAME_ACTIVE} Partien gleichzeitig`);
    }
    const inviterWhite = row.color === "white" || (row.color === "random" && crypto.getRandomValues(new Uint8Array(1))[0] < 128);
    const [white, black] = inviterWhite ? [row.inviter, row.invitee] : [row.invitee, row.inviter];
    const t = now();
    const initial = isXo(row) ? xoInitial() : isC4(row) ? c4Initial() : isShips(row) ? shipsInitial() : null;
    const state = initial ? JSON.stringify(initial) : null;
    await db.run("UPDATE games SET status = 'active', white = ?, black = ?, white_ms = ?, black_ms = ?, turn_since = ?, white_seen = ?, black_seen = ?, state = ?, updated = ? WHERE id = ? AND status = 'invited'",
      [white, black, row.time_s * 1000, row.time_s * 1000, t, t, t, state, t, row.id]);
    return db.get("SELECT * FROM games WHERE id = ?", [row.id]);
  }

  /** Laufende Partie, in der ich mitspiele; meine Farbe ("w"/"b"). */
  function myColor(row, me) {
    if (row.status !== "active") throw new HttpError(409, "Partie läuft nicht");
    return row.white === me.uuid ? "w" : "b";
  }

  // ------------------------------------------------------------------ Statistik
  /** Zeitzone aus der Anfrage: Minuten östlich von UTC (MEZ = 60), begrenzt; Unsinn → 0. */
  function statTz(value) {
    const tz = Math.round(Number(value));
    return Number.isFinite(tz) ? Math.max(-STAT_TZ_MAX, Math.min(STAT_TZ_MAX, tz)) : 0;
  }

  /** Tag "JJJJ-MM-TT" in der Ortszeit des Spielers. */
  function statDay(t, tz) {
    return new Date(t + tz * 60 * 1000).toISOString().slice(0, 10);
  }

  /** Version, Profil und Server der Meldung (bereinigt, gekürzt). */
  function statNames(data) {
    return {
      version: clean(data.version, 32),
      profile: clean(data.profile, 40),
      server: clean(data.server, 64) || STAT_SINGLEPLAYER,
    };
  }

  /** Zeile in stat_totals anlegen (falls neu); first = erste Meldung. */
  async function statEnsure(uuid, t) {
    await db.run("INSERT INTO stat_totals (uuid, first, last) VALUES (?, ?, ?) ON CONFLICT(uuid) DO NOTHING", [uuid, t, t]);
  }

  /** Version und Profil hochzählen (leere Namen werden übersprungen). */
  async function statItems(uuid, names, seconds, launches, t) {
    for (const kind of ["version", "profile"]) {
      if (!names[kind]) continue;
      await db.run("INSERT INTO stat_items (uuid, kind, name, seconds, launches, last) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(uuid, kind, name) DO UPDATE SET seconds = seconds + excluded.seconds, launches = launches + excluded.launches, last = excluded.last",
        [uuid, kind, names[kind], seconds, launches, t]);
    }
  }

  /** Schach-Bilanz aus beendeten Partien (games, status 'over'). */
  async function chessStats(uuid) {
    const rows = await db.all("SELECT g.white, g.black, g.result, u.name AS opponent FROM games g LEFT JOIN users u ON u.uuid = (CASE WHEN g.white = ? THEN g.black ELSE g.white END) WHERE g.status = 'over' AND (g.kind IS NULL OR g.kind = 'chess') AND (g.white = ? OR g.black = ?)",
      [uuid, uuid, uuid]);
    const total = { played: 0, wins: 0, losses: 0, draws: 0 };
    const opponents = new Map();
    for (const row of rows) {
      const white = row.white === uuid;
      const key = white ? row.black : row.white;
      if (!opponents.has(key)) opponents.set(key, { name: row.opponent || "?", played: 0, wins: 0, losses: 0, draws: 0 });
      const outcome = row.result === "1/2-1/2" ? "draws" : (row.result === "1-0") === white ? "wins" : "losses";
      for (const entry of [total, opponents.get(key)]) {
        entry.played++;
        entry[outcome]++;
      }
    }
    const top = [...opponents.values()].sort((x, y) => y.played - x.played || x.name.localeCompare(y.name)).slice(0, 5);
    return { ...total, opponents: top };
  }

  const routes = {
    "GET /api/health": async () => ({ ok: true, offline: allowOffline }),

    "POST /api/auth/challenge": async (request, ip) => {
      const t = now();
      // abgelaufene Aufgaben sofort aufräumen, offene Aufgaben insgesamt und je Adresse begrenzen
      await db.run("DELETE FROM challenges WHERE created < ?", [t - CHALLENGE_MS]);
      if ((await count("SELECT COUNT(*) AS n FROM challenges WHERE ip = ?", [ip])) >= CHALLENGES_PER_IP) {
        throw new HttpError(429, "Zu viele Anmeldeversuche – bitte kurz warten", Math.ceil(CHALLENGE_MS / 1000));
      }
      if ((await count("SELECT COUNT(*) AS n FROM challenges", [])) >= CHALLENGES_TOTAL) {
        throw new HttpError(503, "Gerade zu viele Anmeldungen – bitte gleich erneut versuchen", 30);
      }
      const id = randomHex(20);
      await db.run("INSERT INTO challenges (id, created, ip) VALUES (?, ?, ?)", [id, t, ip]);
      return { serverId: id };
    },

    /** Anmeldung mit Microsoft-Konto: Zertifikat + signiertes Profil von Mojang (siehe mojang.js). */
    "POST /api/auth/login": async (request) => {
      const proof = await body(request);
      const serverId = proof.serverId;
      if (typeof serverId !== "string" || !/^[0-9a-f]{40}$/.test(serverId)) throw new HttpError(400, "Ungültige Anmeldung");
      const challenge = await db.get("SELECT created FROM challenges WHERE id = ?", [serverId]);
      await db.run("DELETE FROM challenges WHERE id = ?", [serverId]);
      if (!challenge || challenge.created < now() - CHALLENGE_MS) throw new HttpError(401, "Anmeldung abgelaufen – bitte erneut versuchen");
      let account;
      try {
        account = await verifyLogin(proof, serverId, mojangKeys, now());
      } catch (reason) {
        throw new HttpError(401, `Minecraft-Konto nicht bestätigt: ${typeof reason === "string" ? reason : "ungültige Daten"}`);
      }
      if (!NAME.test(account.name)) throw new HttpError(400, "Ungültiger Name");
      const known = await db.get("SELECT head_at FROM users WHERE uuid = ?", [account.uuid]);
      const head = account.skinUrl && (!known || known.head_at < now() - HEAD_MS) ? await headFromUrl(account.skinUrl) : null;
      await upsertUser(account.uuid, account.name, false, head);
      const user = await db.get("SELECT * FROM users WHERE uuid = ?", [account.uuid]);
      return { token: await newSession(account.uuid), me: { uuid: user.uuid, name: user.name, head: user.head || null } };
    },

    "POST /api/auth/offline": async (request) => {
      if (!allowOffline) throw new HttpError(403, "Offline-Konten sind hier nicht erlaubt");
      const { name } = await body(request);
      if (typeof name !== "string" || !NAME.test(name)) throw new HttpError(400, "Ungültiger Name");
      const uuid = await offlineUuid(name);
      const known = await db.get("SELECT head_at FROM users WHERE uuid = ?", [uuid]);
      let head = null;
      if (!known || known.head_at < now() - HEAD_MS) {
        // Skin des gleichnamigen Microsoft-Kontos verwenden, falls es eines gibt
        try {
          const id = await getJson(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(name)}`);
          if (id && UUID.test(id.id || "")) head = await headFromProfile(await getJson(`https://sessionserver.mojang.com/session/minecraft/profile/${id.id}`));
        } catch (error) {
          log(`Skin für ${name} nicht geladen: ${error.message}`);
        }
      }
      await upsertUser(uuid, name, true, head);
      const user = await db.get("SELECT * FROM users WHERE uuid = ?", [uuid]);
      return { token: await newSession(uuid), me: { uuid: user.uuid, name: user.name, head: user.head || null } };
    },

    "POST /api/auth/logout": async (request) => {
      const header = request.headers.get("authorization") || "";
      if (header.startsWith("Bearer ")) await db.run("DELETE FROM sessions WHERE token_hash = ?", [await sha256(header.slice(7).trim())]);
      return { ok: true };
    },

    "GET /api/friends": async (request) => overview(await auth(request)),

    /** Lebenszeichen von Launcher oder Spiel; Antwort ist gleich die Freundesliste. */
    "POST /api/presence": async (request) => {
      const me = await auth(request);
      const data = await body(request);
      const place = data.place === "game" ? "game" : "launcher";
      const server = place === "game" && !data.hideServer ? clean(data.server, 64) : null;
      const version = clean(data.version, 16);
      await db.run("UPDATE users SET last_seen = ?, place = ?, server = ?, version = ? WHERE uuid = ?", [now(), place, server, version, me.uuid]);
      return overview({ ...me, last_seen: now() });
    },

    "POST /api/presence/offline": async (request) => {
      const me = await auth(request);
      // sofort als offline zeigen ("zuletzt online" bleibt praktisch jetzt)
      await db.run("UPDATE users SET last_seen = ?, place = NULL, server = NULL WHERE uuid = ?", [now() - ONLINE_MS - 1, me.uuid]);
      return { ok: true };
    },

    /** Eigene Cosmetics wählen: Umhang, MiniMes je Stelle (Minecraft-Namen), Flügel; null = keins. Fehlende Felder bleiben. */
    "POST /api/cosmetics": async (request) => {
      const me = await auth(request);
      const data = await body(request);
      const items = await grantsOf(me.uuid);
      // nicht freigeschaltete Teile gelten als "keins"
      for (const [field, kind] of [["cape", "cape"], ["wings", "wings"], ["shield", "shield"], ["aura", "aura"]]) {
        if (typeof data[field] === "string" && !owns(items, kind, data[field])) data[field] = null;
      }
      if (!owns(items, "minime")) {
        if ("minimes" in data) data.minimes = {};
        if ("minime" in data) data.minime = null;
      }
      if (!owns(items, "nametag") && "nametag" in data) data.nametag = null;
      // ältere Mods schicken nur "cape" (oder gar nichts für "kein Umhang")
      if ("cape" in data || !("minime" in data || "wings" in data || "minimePos" in data || "minimes" in data || "shield" in data || "aura" in data || "nametag" in data)) {
        const cape = typeof data.cape === "string" && /^[a-z0-9_-]{1,32}$/.test(data.cape) ? data.cape : null;
        await db.run("UPDATE users SET cape = ? WHERE uuid = ?", [cape, me.uuid]);
      }
      if ("minimes" in data) {
        // ein MiniMe je Stelle; für ältere Mods zusätzlich der erste als einzelner MiniMe
        const set = {};
        const given = data.minimes && typeof data.minimes === "object" ? data.minimes : {};
        for (const pos of MINIME_POS) {
          if (typeof given[pos] === "string" && NAME.test(given[pos])) set[pos] = given[pos];
        }
        const first = MINIME_POS.find((pos) => set[pos]);
        await db.run("UPDATE users SET minime_set = ?, minime = ?, minime_pos = ? WHERE uuid = ?",
          [first ? JSON.stringify(set) : null, first ? set[first] : null, first || null, me.uuid]);
      } else if ("minime" in data || "minimePos" in data) {
        // Mods bis 2.18: ein MiniMe mit Position
        if ("minime" in data) {
          const minime = typeof data.minime === "string" && NAME.test(data.minime) ? data.minime : null;
          await db.run("UPDATE users SET minime = ? WHERE uuid = ?", [minime, me.uuid]);
        }
        if ("minimePos" in data) {
          const pos = MINIME_POS.includes(data.minimePos) ? data.minimePos : null;
          await db.run("UPDATE users SET minime_pos = ? WHERE uuid = ?", [pos, me.uuid]);
        }
        await db.run("UPDATE users SET minime_set = NULL WHERE uuid = ?", [me.uuid]);
      }
      if ("shield" in data) {
        const shield = typeof data.shield === "string" && /^[a-z0-9_-]{1,32}$/.test(data.shield) ? data.shield : null;
        await db.run("UPDATE users SET shield = ? WHERE uuid = ?", [shield, me.uuid]);
      }
      if ("nametag" in data) {
        // Text mit &-Farbcodes; Steuerzeichen und § fallen weg, höchstens 80 Zeichen
        let tag = typeof data.nametag === "string" ? data.nametag.replace(/\u00a7./g, "").replace(/[\u0000-\u001f\u007f\u00a7]/g, "").slice(0, 80) : "";
        if (!tag.replace(/&[0-9a-fk-or]/gi, "").trim()) tag = null;
        await db.run("UPDATE users SET nametag = ? WHERE uuid = ?", [tag, me.uuid]);
      }
      if ("aura" in data) {
        const aura = typeof data.aura === "string" && /^[a-z0-9_-]{1,32}$/.test(data.aura) ? data.aura : null;
        await db.run("UPDATE users SET aura = ? WHERE uuid = ?", [aura, me.uuid]);
      }
      if ("wings" in data) {
        const wings = typeof data.wings === "string" && /^[a-z0-9_-]{1,32}$/.test(data.wings) ? data.wings : null;
        await db.run("UPDATE users SET wings = ? WHERE uuid = ?", [wings, me.uuid]);
      }
      const row = await db.get("SELECT cape, minime, minime_pos, minime_set, wings, shield, aura, nametag FROM users WHERE uuid = ?", [me.uuid]);
      return { cape: row.cape || null, minime: row.minime || null, minimePos: row.minime_pos || null, minimes: miniMeSet(row), wings: row.wings || null, shield: row.shield || null, aura: row.aura || null, nametag: row.nametag || null, owned: [...items].sort() };
    },

    /** Eigene Freischaltungen (für das Cosmetics-Menü). */
    "POST /api/cosmetics/owned": async (request) => {
      const me = await auth(request);
      const items = await grantsOf(me.uuid);
      return { items: [...items].sort(), admin: owns(items, "admin") };
    },

    // ------------------------------------------------------------------ Admin: Cosmetics freischalten
    "GET /api/admin/catalog": async (request, ip) => {
      await adminAuth(request, ip);
      return { catalog: CATALOG };
    },

    /** Spieler suchen (Name oder UUID); unbekannte Namen werden wie bei Freundschaftsanfragen angelegt. */
    "POST /api/admin/user": async (request, ip) => {
      await adminAuth(request, ip);
      const { name } = await body(request);
      const key = String(name || "").replace(/-/g, "").toLowerCase();
      let user = UUID.test(key) ? await db.get("SELECT uuid, name FROM users WHERE uuid = ?", [key]) : null;
      if (!user) user = (await findUser(name)) || (await placeholder(name));
      if (!user) throw new HttpError(404, "Spieler nicht gefunden");
      return grantView(user.uuid);
    },

    /** Freischaltungen eines Spielers festlegen (ersetzt die bisherigen). */
    "POST /api/admin/grants": async (request, ip) => {
      const admin = await adminAuth(request, ip);
      const { uuid, items } = await body(request);
      const id = String(uuid || "").replace(/-/g, "").toLowerCase();
      if (!UUID.test(id) || !(await db.get("SELECT uuid FROM users WHERE uuid = ?", [id]))) throw new HttpError(404, "Spieler nicht gefunden");
      const list = [...new Set((Array.isArray(items) ? items : []).map(String).filter((i) => GRANT.test(i)))].slice(0, 500);
      // sich selbst die Admin-Rechte nehmen geht nur mit dem Passwort
      if (admin.uuid === id && !list.includes("admin") && !list.includes("*")) throw new HttpError(400, "Du kannst dir die Admin-Rechte nicht selbst nehmen");
      await db.run("DELETE FROM grants WHERE uuid = ?", [id]);
      for (const item of list) await db.run("INSERT INTO grants (uuid, item) VALUES (?, ?)", [id, item]);
      await dropUnowned(id);
      return grantView(id);
    },

    /** Alle Spieler mit Freischaltungen. */
    "GET /api/admin/users": async (request, ip) => {
      await adminAuth(request, ip);
      const rows = await db.all("SELECT g.uuid AS uuid, u.name AS name, g.item AS item FROM grants g LEFT JOIN users u ON u.uuid = g.uuid ORDER BY u.name_lower");
      const map = new Map();
      for (const r of rows) {
        if (!map.has(r.uuid)) map.set(r.uuid, { uuid: r.uuid, name: r.name, items: [] });
        map.get(r.uuid).items.push(r.item);
      }
      return { users: [...map.values()] };
    },

    /** Cosmetics anderer Spieler (z. B. alle auf dem Server), höchstens 100 auf einmal. */
    "POST /api/cosmetics/lookup": async (request) => {
      await auth(request);
      const { uuids } = await body(request);
      const list = (Array.isArray(uuids) ? uuids : []).map((u) => String(u).replace(/-/g, "").toLowerCase()).filter((u) => UUID.test(u)).slice(0, 100);
      const capes = {};
      const minimes = {};
      const minimePos = {};
      const minimeSets = {};
      const wings = {};
      const shields = {};
      const auras = {};
      const nametags = {};
      if (list.length) {
        const rows = await db.all(`SELECT uuid, cape, minime, minime_pos, minime_set, wings, shield, aura, nametag FROM users WHERE (cape IS NOT NULL OR minime IS NOT NULL OR wings IS NOT NULL OR shield IS NOT NULL OR aura IS NOT NULL OR nametag IS NOT NULL) AND uuid IN (${list.map(() => "?").join(",")})`, list);
        const grantRows = await db.all(`SELECT uuid, item FROM grants WHERE uuid IN (${list.map(() => "?").join(",")})`, list);
        const grantMap = new Map();
        for (const g of grantRows) {
          if (!grantMap.has(g.uuid)) grantMap.set(g.uuid, new Set());
          grantMap.get(g.uuid).add(g.item);
        }
        for (const row of rows) {
          const items = grantMap.get(row.uuid) || new Set();
          if (row.cape && !owns(items, "cape", row.cape)) row.cape = null;
          if (row.wings && !owns(items, "wings", row.wings)) row.wings = null;
          if (row.shield && !owns(items, "shield", row.shield)) row.shield = null;
          if (row.aura && !owns(items, "aura", row.aura)) row.aura = null;
          if (!owns(items, "minime")) row.minime = row.minime_set = row.minime_pos = null;
          if (!owns(items, "nametag")) row.nametag = null;
          if (row.cape) capes[row.uuid] = row.cape;
          if (row.minime) minimes[row.uuid] = row.minime;
          if (row.minime && row.minime_pos) minimePos[row.uuid] = row.minime_pos;
          if (row.minime) minimeSets[row.uuid] = miniMeSet(row);
          if (row.wings) wings[row.uuid] = row.wings;
          if (row.shield) shields[row.uuid] = row.shield;
          if (row.aura) auras[row.uuid] = row.aura;
          if (row.nametag) nametags[row.uuid] = row.nametag;
        }
      }
      return { capes, minimes, minimePos, minimeSets, wings, shields, auras, nametags };
    },

    "POST /api/friends/request": async (request) => {
      const me = await auth(request);
      const { name } = await body(request);
      // eigene Grenzen zuerst prüfen – vor dem Nachschlagen (playerdb.co) und Anlegen unbekannter Spieler
      if ((await count("SELECT COUNT(*) AS n FROM friends WHERE a = ? OR b = ?", [me.uuid, me.uuid])) >= MAX_FRIENDS) throw new HttpError(409, "Freundesliste ist voll");
      const found = await findUser(name);
      const mutual = found && (await db.get("SELECT 1 AS ok FROM requests WHERE sender = ? AND target = ?", [found.uuid, me.uuid]));
      if (!mutual && (await count("SELECT COUNT(*) AS n FROM requests WHERE sender = ?", [me.uuid])) >= MAX_PENDING) throw new HttpError(429, "Zu viele offene Anfragen");
      const target = found || (await placeholder(name));
      if (!target) throw new HttpError(404, "Diesen Minecraft-Namen gibt es nicht");
      if (target.uuid === me.uuid) throw new HttpError(400, "Du kannst dich nicht selbst hinzufügen");
      if (await areFriends(me.uuid, target.uuid)) throw new HttpError(409, `${target.name} ist schon dein Freund`);
      // hat der andere schon angefragt? Dann gleich befreunden
      if (mutual) {
        await makeFriends(me.uuid, target.uuid);
        return { ...(await overview(me)), message: `Du bist jetzt mit ${target.name} befreundet` };
      }
      if ((await count("SELECT COUNT(*) AS n FROM requests WHERE target = ?", [target.uuid])) >= MAX_PENDING) throw new HttpError(429, `${target.name} hat zu viele offene Anfragen`);
      await db.run("INSERT OR IGNORE INTO requests (sender, target, created) VALUES (?, ?, ?)", [me.uuid, target.uuid, now()]);
      const waiting = !(await db.get("SELECT 1 AS ok FROM sessions WHERE uuid = ? LIMIT 1", [target.uuid]));
      return { ...(await overview(me)), message: waiting ? `Anfrage an ${target.name} gesendet – sichtbar, sobald er den Mytic Client startet` : `Anfrage an ${target.name} gesendet` };
    },

    "POST /api/friends/accept": async (request) => {
      const me = await auth(request);
      const { uuid } = await body(request);
      const row = await db.get("SELECT u.name FROM requests r JOIN users u ON u.uuid = r.sender WHERE r.sender = ? AND r.target = ?", [String(uuid), me.uuid]);
      if (!row) throw new HttpError(404, "Anfrage nicht gefunden");
      if ((await count("SELECT COUNT(*) AS n FROM friends WHERE a = ? OR b = ?", [me.uuid, me.uuid])) >= MAX_FRIENDS) throw new HttpError(409, "Freundesliste ist voll");
      await makeFriends(me.uuid, String(uuid));
      return { ...(await overview(me)), message: `Du bist jetzt mit ${row.name} befreundet` };
    },

    "POST /api/friends/deny": async (request) => {
      const me = await auth(request);
      const { uuid } = await body(request);
      await db.run("DELETE FROM requests WHERE sender = ? AND target = ?", [String(uuid), me.uuid]);
      return overview(me);
    },

    /** Freund entfernen oder eigene Anfrage zurückziehen. */
    /** Cloud-Einstellungen: Mods, HUD, Profile und Tasten eines Kontos (vom Spiel gesichert, auf anderen PCs wiederhergestellt) */
    "GET /api/settings": async (request) => {
      const me = await auth(request);
      const row = await db.get("SELECT data, updated FROM cloud_settings WHERE uuid = ?", [me.uuid]);
      return { data: row ? JSON.parse(row.data) : null, updated: row ? row.updated : 0 };
    },

    "POST /api/settings": async (request) => {
      const me = await auth(request);
      const { data } = await body(request);
      if (!data || typeof data !== "object" || Array.isArray(data)) throw new HttpError(400, "Keine Einstellungen");
      const text = JSON.stringify(data);
      if (text.length > SETTINGS_MAX) throw new HttpError(413, "Einstellungen zu groß");
      const t = now();
      await db.run("INSERT INTO cloud_settings (uuid, data, updated) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET data = excluded.data, updated = excluded.updated", [me.uuid, text, t]);
      return { updated: t };
    },

    /** Screenshot hochladen (JPEG als Base64, vom Launcher verkleinert) → öffentlicher Link */
    "POST /api/shots": async (request) => {
      const me = await auth(request);
      const { image } = await body(request, SHOT_BODY_MAX);
      if (typeof image !== "string" || !image.startsWith("/9j/") || !/^[A-Za-z0-9+/]+=*$/.test(image)) throw new HttpError(400, "Kein JPEG");
      if (image.length > SHOT_MAX) throw new HttpError(413, "Bild zu groß");
      const t = now();
      // Tagesgrenze je Konto (Zähler, damit Löschen nicht hilft) und Gesamtgrenze für den Speicher
      const day = Math.floor(t / (24 * 60 * 60 * 1000));
      await db.run("DELETE FROM shot_quota WHERE day < ?", [day]);
      const used = await db.get("SELECT n FROM shot_quota WHERE uuid = ? AND day = ?", [me.uuid, day]);
      if (used && used.n >= shotsPerDay) throw new HttpError(429, `Höchstens ${shotsPerDay} Screenshots am Tag`, Math.ceil(((day + 1) * 24 * 60 * 60 * 1000 - t) / 1000));
      if ((await count("SELECT COUNT(*) AS n FROM shots", [])) >= shotsTotal) {
        log("WARNUNG: Screenshot-Speicher voll");
        throw new HttpError(507, "Screenshot-Speicher des Servers ist voll");
      }
      await db.run("INSERT INTO shot_quota (uuid, day, n) VALUES (?, ?, 1) ON CONFLICT(uuid, day) DO UPDATE SET n = n + 1", [me.uuid, day]);
      const id = randomHex(8);
      await db.run("INSERT INTO shots (id, uuid, created, data) VALUES (?, ?, ?, ?)", [id, me.uuid, t, image]);
      // nur die neuesten SHOTS_PER_USER behalten
      await db.run("DELETE FROM shots WHERE uuid = ? AND id NOT IN (SELECT id FROM shots WHERE uuid = ? ORDER BY created DESC LIMIT ?)", [me.uuid, me.uuid, SHOTS_PER_USER]);
      return { id, url: new URL(request.url).origin + "/api/shots/" + id + ".jpg" };
    },

    "POST /api/shots/delete": async (request) => {
      const me = await auth(request);
      const { id } = await body(request);
      await db.run("DELETE FROM shots WHERE id = ? AND uuid = ?", [String(id), me.uuid]);
      return { ok: true };
    },

    "POST /api/friends/remove": async (request) => {
      const me = await auth(request);
      const { uuid } = await body(request);
      await db.run("DELETE FROM friends WHERE a = ? AND b = ?", pair(me.uuid, String(uuid)));
      await db.run("DELETE FROM requests WHERE sender = ? AND target = ?", [me.uuid, String(uuid)]);
      return overview(me);
    },

    // ------------------------------------------------------------------ Statistik
    /** Start des Spiels: { version, profile, tz } (tz = Minuten östlich von UTC). */
    "POST /api/stats/launch": async (request) => {
      const me = await auth(request);
      const data = await body(request);
      const names = statNames(data);
      const t = now();
      const day = statDay(t, statTz(data.tz));
      await statEnsure(me.uuid, t);
      await db.run("UPDATE stat_totals SET launches = launches + 1, last = ? WHERE uuid = ?", [t, me.uuid]);
      await db.run("INSERT INTO stat_days (uuid, day, seconds, launches) VALUES (?, ?, 0, 1) ON CONFLICT(uuid, day) DO UPDATE SET launches = launches + 1", [me.uuid, day]);
      await statItems(me.uuid, names, 0, 1, t);
      return { ok: true };
    },

    /**
     * Gespielte Zeit seit der letzten Meldung: { seconds: 1–120, server, version, profile, session, tz }.
     * Gegen Missbrauch: höchstens (Zeit seit letzter Meldung + 5 s) und 150 s je Minute werden gutgeschrieben.
     */
    "POST /api/stats/play": async (request) => {
      const me = await auth(request);
      const data = await body(request);
      const seconds = data.seconds;
      if (!Number.isInteger(seconds) || seconds < 1 || seconds > STAT_PLAY_MAX) throw new HttpError(400, `Ungültige Spielzeit (1–${STAT_PLAY_MAX} Sekunden)`);
      const session = data.session === undefined || data.session === null || data.session === "" ? null : data.session;
      if (session !== null && (typeof session !== "string" || !/^[A-Za-z0-9]{1,32}$/.test(session))) throw new HttpError(400, "Ungültige Sitzung");
      const names = statNames(data);
      const t = now();
      const day = statDay(t, statTz(data.tz));
      await statEnsure(me.uuid, t);
      // alte Sitzungen aufräumen (die längste steht schon in stat_totals) – vor dem Hochzählen, sonst lebt eine alte Sitzung weiter
      await db.run("DELETE FROM stat_sessions WHERE updated < ?", [t - STAT_SESSION_KEEP_MS]);
      const totals = await db.get("SELECT play_at, win_start, win_sec FROM stat_totals WHERE uuid = ?", [me.uuid]);
      // zu schnelle Meldungen kürzen: nicht mehr als seit der letzten Meldung vergangen ist (+ Spielraum) …
      let credit = seconds;
      if (totals.play_at) credit = Math.min(credit, Math.floor(Math.max(0, t - totals.play_at) / 1000) + STAT_SLACK_SEC);
      // … und höchstens STAT_WINDOW_SEC je Zeitfenster
      const fresh = t - totals.win_start >= STAT_WINDOW_MS;
      const windowStart = fresh ? t : totals.win_start;
      const windowUsed = fresh ? 0 : totals.win_sec;
      credit = Math.max(0, Math.min(credit, STAT_WINDOW_SEC - windowUsed));
      await db.run("UPDATE stat_totals SET seconds = seconds + ?, last = ?, play_at = ?, win_start = ?, win_sec = ? WHERE uuid = ?",
        [credit, t, t, windowStart, windowUsed + credit, me.uuid]);
      if (credit > 0) {
        await db.run("INSERT INTO stat_days (uuid, day, seconds, launches) VALUES (?, ?, ?, 0) ON CONFLICT(uuid, day) DO UPDATE SET seconds = seconds + excluded.seconds", [me.uuid, day, credit]);
        await db.run("INSERT INTO stat_servers (uuid, name, seconds, last) VALUES (?, ?, ?, ?) ON CONFLICT(uuid, name) DO UPDATE SET seconds = seconds + excluded.seconds, last = excluded.last",
          [me.uuid, names.server, credit, t]);
        await statItems(me.uuid, names, credit, 0, t);
        if (session) {
          await db.run("INSERT INTO stat_sessions (uuid, session, seconds, updated) VALUES (?, ?, ?, ?) ON CONFLICT(uuid, session) DO UPDATE SET seconds = seconds + excluded.seconds, updated = excluded.updated",
            [me.uuid, session, credit, t]);
          const row = await db.get("SELECT seconds FROM stat_sessions WHERE uuid = ? AND session = ?", [me.uuid, session]);
          await db.run("UPDATE stat_totals SET longest = MAX(longest, ?) WHERE uuid = ?", [row.seconds, me.uuid]);
        }
      }
      return { ok: true };
    },

    /** Eigene Statistik; ?tz=<Minuten östlich von UTC> bestimmt "heute" und die Tagesreihe. */
    "GET /api/stats": async (request) => {
      const me = await auth(request);
      const t = now();
      const tz = statTz(new URL(request.url).searchParams.get("tz"));
      const totals = (await db.get("SELECT launches, seconds, longest, first, last FROM stat_totals WHERE uuid = ?", [me.uuid])) || { launches: 0, seconds: 0, longest: 0, first: 0, last: 0 };
      // die letzten STAT_DAYS Tage (Ortszeit), älteste zuerst, auch Tage ohne Spielzeit
      const dayList = [];
      for (let i = STAT_DAYS - 1; i >= 0; i--) dayList.push(statDay(t - i * 24 * 60 * 60 * 1000, tz));
      const rows = await db.all("SELECT day, seconds, launches FROM stat_days WHERE uuid = ? AND day >= ?", [me.uuid, dayList[0]]);
      const byDay = new Map(rows.map((r) => [r.day, r]));
      const days = dayList.map((day) => ({ day, seconds: byDay.get(day)?.seconds || 0, launches: byDay.get(day)?.launches || 0 }));
      const servers = await db.all("SELECT name, seconds, last FROM stat_servers WHERE uuid = ? ORDER BY seconds DESC, last DESC LIMIT 10", [me.uuid]);
      const items = (kind) => db.all("SELECT name, seconds, launches, last FROM stat_items WHERE uuid = ? AND kind = ? ORDER BY seconds DESC, launches DESC, last DESC LIMIT 50", [me.uuid, kind]);
      return {
        totals: {
          launches: totals.launches,
          seconds: totals.seconds,
          longest: totals.longest,
          first: totals.first,
          last: totals.last,
          today: days[days.length - 1].seconds,
          week: days.slice(-7).reduce((sum, d) => sum + d.seconds, 0),
        },
        days,
        servers,
        versions: await items("version"),
        profiles: await items("profile"),
        chess: await chessStats(me.uuid),
      };
    },

    // ------------------------------------------------------------------ Schach
    /** Meine Einladungen (ein- und ausgehend), laufende Partien und kürzlich beendete. */
    "GET /api/games": async (request) => {
      const me = await auth(request);
      await markSeen(me.uuid);
      const open = (await openGames(me.uuid)).filter((g) => g.status === "invited" || g.status === "active");
      const recent = await db.all("SELECT * FROM games WHERE (inviter = ? OR invitee = ?) AND status IN ('over', 'declined', 'cancelled', 'expired') AND updated > ?",
        [me.uuid, me.uuid, now() - GAME_RECENT_MS]);
      const ids = new Set(open.map((g) => g.id));
      const rows = [...open, ...recent.filter((g) => !ids.has(g.id))].sort((x, y) => y.updated - x.updated);
      return { games: await gameViews(rows, me.uuid), serverTime: now() };
    },

    /**
     * Einladen: { to: Minecraft-Name, game: "chess"|"xo"|"c4"|"ships" (fehlt = Schach), color: "random"|"white"|"black" (eigene Farbe) }.
     * Schach: time: 60|180|300|600. X und O: rounds: 1|3|5, moveTime: 0|5|10|30 (Sekunden je Zug, 0 = ohne); Weiß = X.
     * Vier gewinnt: rounds: 1|3|5, moveTime: 0|5|10|30; Weiß = a. Schiffe versenken: moveTime: 0|10|20|30 (Sekunden je Schuss); Weiß schießt zuerst.
     */
    "POST /api/games": async (request) => {
      const me = await auth(request);
      const data = await body(request);
      const kind = data.game === undefined || data.game === null ? "chess" : data.game;
      if (!GAME_KINDS.includes(kind)) throw new HttpError(400, "Unbekanntes Spiel");
      const xo = kind === "xo", c4 = kind === "c4", ships = kind === "ships";
      const chess = kind === "chess";
      const time = chess ? Number(data.time) : 0;
      const rounds = xo || c4 ? Number(data.rounds) : null;
      const moveTime = chess ? null : Number(data.moveTime);
      const color = data.color === undefined ? "random" : data.color;
      if (chess && !GAME_TIMES.includes(time)) throw new HttpError(400, "Ungültige Bedenkzeit");
      if (xo && !XO_ROUNDS.includes(rounds)) throw new HttpError(400, "Ungültige Rundenzahl");
      if (xo && !XO_MOVE_TIMES.includes(moveTime)) throw new HttpError(400, "Ungültige Zeit je Zug");
      if (c4 && !C4_ROUNDS.includes(rounds)) throw new HttpError(400, "Ungültige Rundenzahl");
      if (c4 && !C4_MOVE_TIMES.includes(moveTime)) throw new HttpError(400, "Ungültige Zeit je Zug");
      if (ships && !SHIPS_MOVE_TIMES.includes(moveTime)) throw new HttpError(400, "Ungültige Zeit je Schuss");
      if (!GAME_COLORS.includes(color)) throw new HttpError(400, "Ungültige Farbe");
      if (typeof data.to !== "string" || !NAME.test(data.to.trim())) throw new HttpError(400, "Ungültiger Name");
      const target = await findUser(data.to);
      if (!target) throw new HttpError(404, "Dieser Spieler nutzt den Mytic Client nicht");
      if (target.uuid === me.uuid) throw new HttpError(400, "Du kannst dich nicht selbst einladen");
      const t = now();
      await db.run("DELETE FROM games WHERE status IN ('declined', 'cancelled', 'expired') AND updated < ?", [t - GAME_KEEP_MS]);
      const mine = await openGames(me.uuid);
      const between = mine.filter((g) => (g.status === "invited" || g.status === "active") && (g.inviter === target.uuid || g.invitee === target.uuid));
      // beide drücken „Revanche“: die Einladung des anderen gilt als angenommen
      const theirs = between.find((g) => g.status === "invited" && g.inviter === target.uuid);
      if (theirs && (theirs.kind || "chess") !== kind) {
        throw new HttpError(409, `${target.name} hat dich schon zu ${GAME_NAMES[theirs.kind] || "Schach"} herausgefordert – nimm die Einladung an oder lehne sie ab`);
      }
      if (theirs) return gameResponse(await startGame(theirs), me.uuid);
      if (between.some((g) => g.status === "active")) throw new HttpError(409, `Ihr spielt schon eine Partie – /myticgame öffnet sie`);
      if (between.length) throw new HttpError(409, `Du hast ${target.name} schon herausgefordert – warte auf Antwort`);
      if (mine.filter((g) => g.status === "invited" && g.inviter === me.uuid).length >= GAME_INVITES) throw new HttpError(429, "Zu viele offene Einladungen");
      if (mine.filter((g) => g.status === "active").length >= GAME_ACTIVE) throw new HttpError(429, `Höchstens ${GAME_ACTIVE} Partien gleichzeitig`);
      const id = randomHex(8);
      await db.run("INSERT INTO games (id, inviter, invitee, color, time_s, status, moves, fen, white_ms, black_ms, turn_since, kind, rounds, move_s, created, updated) VALUES (?, ?, ?, ?, ?, 'invited', '', ?, ?, ?, 0, ?, ?, ?, ?, ?)",
        [id, me.uuid, target.uuid, color, time, chess ? START_FEN : "", time * 1000, time * 1000, kind, rounds, moveTime, t, t]);
      return gameResponse(await db.get("SELECT * FROM games WHERE id = ?", [id]), me.uuid);
    },

    "POST /api/games/:id/accept": async (request, ip, id) => {
      const me = await auth(request);
      const row = await loadGame(id, me);
      if (row.invitee !== me.uuid) throw new HttpError(403, "Nur der Eingeladene kann annehmen");
      if (row.status !== "invited") throw new HttpError(409, "Einladung ist nicht mehr offen");
      return gameResponse(await startGame(row), me.uuid);
    },

    "POST /api/games/:id/decline": async (request, ip, id) => {
      const me = await auth(request);
      const row = await loadGame(id, me);
      if (row.invitee !== me.uuid) throw new HttpError(403, "Nur der Eingeladene kann ablehnen");
      if (row.status !== "invited") throw new HttpError(409, "Einladung ist nicht mehr offen");
      await db.run("UPDATE games SET status = 'declined', updated = ? WHERE id = ?", [now(), row.id]);
      return gameResponse(await db.get("SELECT * FROM games WHERE id = ?", [row.id]), me.uuid);
    },

    "POST /api/games/:id/cancel": async (request, ip, id) => {
      const me = await auth(request);
      const row = await loadGame(id, me);
      if (row.inviter !== me.uuid) throw new HttpError(403, "Nur der Einladende kann zurückziehen");
      if (row.status !== "invited") throw new HttpError(409, "Einladung ist nicht mehr offen");
      await db.run("UPDATE games SET status = 'cancelled', updated = ? WHERE id = ?", [now(), row.id]);
      return gameResponse(await db.get("SELECT * FROM games WHERE id = ?", [row.id]), me.uuid);
    },

    /**
     * Zug: { move: "e2e4" | "e7e8q", ply: Anzahl bereits gespielter Halbzüge }.
     * X und O: { move: 0..8 (Feld zeilenweise), ply: Anzahl Zeichen auf dem Brett der laufenden Runde }.
     * Vier gewinnt: { move: Spalte 0..6, ply: Anzahl Steine auf dem Brett der laufenden Runde }.
     * Schiffe versenken: { move: Feld 0..99 (Zeile * 10 + Spalte), ply: Anzahl meiner bisherigen Schüsse (ohne automatische Wasserfelder) }.
     */
    "POST /api/games/:id/move": async (request, ip, id) => {
      const me = await auth(request);
      const data = await body(request);
      const row = await loadGame(id, me);
      const color = myColor(row, me);
      if (isXo(row)) return gameResponse(await xoMove(row, color === "w" ? "x" : "o", data), me.uuid);
      if (isC4(row)) return gameResponse(await c4Move(row, color === "w" ? "a" : "b", data), me.uuid);
      if (isShips(row)) return gameResponse(await shipsShoot(row, color, data), me.uuid);
      const moves = gameMoves(row);
      if (data.ply !== moves.length) throw new HttpError(409, "Partie hat sich geändert – bitte neu laden");
      if (gameTurn(row) !== color) throw new HttpError(409, "Du bist nicht am Zug");
      const move = typeof data.move === "string" ? data.move.trim().toLowerCase() : "";
      const state = /^[a-h][1-8][a-h][1-8][qrbn]?$/.test(move) ? parseFen(row.fen) : null;
      const next = state && applyUci(state, move);
      if (!next) throw new HttpError(400, "Unerlaubter Zug");
      const t = now();
      const clock = gameClock(row, t);
      const updated = { ...row, moves: [...moves, move].join(" "), fen: toFen(next), white_ms: clock.white, black_ms: clock.black, turn_since: t, draw_offer: null, updated: t };
      const result = chessStatus(next);
      if (result === "checkmate") return gameResponse(await endGame(updated, color === "w" ? "1-0" : "0-1", "checkmate", updated), me.uuid);
      if (result === "stalemate") return gameResponse(await endGame(updated, "1/2-1/2", "stalemate", updated), me.uuid);
      await db.run("UPDATE games SET moves = ?, fen = ?, white_ms = ?, black_ms = ?, turn_since = ?, draw_offer = NULL, updated = ? WHERE id = ?",
        [updated.moves, updated.fen, updated.white_ms, updated.black_ms, t, t, row.id]);
      return gameResponse(updated, me.uuid);
    },

    /** Schiffe versenken: eigene Flotte setzen { ships: [[Felder …] × 5] } (Längen 5, 4, 3, 3, 2) – endgültig. */
    "POST /api/games/:id/fleet": async (request, ip, id) => {
      const me = await auth(request);
      const data = await body(request);
      const row = await loadGame(id, me);
      const color = myColor(row, me);
      if (!isShips(row)) throw new HttpError(409, "Nur bei Schiffe versenken");
      return gameResponse(await shipsSetFleet(row, color, data), me.uuid);
    },

    /** Aufgeben; { reason: "left" } = Brett geschlossen, Welt/Server verlassen oder Minecraft beendet. */
    "POST /api/games/:id/resign": async (request, ip, id) => {
      const me = await auth(request);
      const data = await body(request).catch(() => ({}));
      const row = await loadGame(id, me);
      const color = myColor(row, me);
      return gameResponse(await endGame(row, color === "w" ? "0-1" : "1-0", data && data.reason === "left" ? "left" : "resign"), me.uuid);
    },

    /** Remis: { action: "offer" | "accept" | "decline" }. */
    "POST /api/games/:id/draw": async (request, ip, id) => {
      const me = await auth(request);
      const { action } = await body(request);
      const row = await loadGame(id, me);
      myColor(row, me);
      if (isXo(row)) throw new HttpError(409, "Remis gibt es bei X und O nicht");
      if (!isChess(row)) throw new HttpError(409, `Remis gibt es bei ${GAME_NAMES[row.kind]} nicht`);
      const opponentOffered = Boolean(row.draw_offer) && row.draw_offer !== me.uuid;
      if (action === "offer") {
        // hat der Gegner schon angeboten, gilt das eigene Angebot als Annahme
        if (opponentOffered) return gameResponse(await endGame(row, "1/2-1/2", "agreement"), me.uuid);
        await db.run("UPDATE games SET draw_offer = ?, updated = ? WHERE id = ?", [me.uuid, now(), row.id]);
        return gameResponse({ ...row, draw_offer: me.uuid, updated: now() }, me.uuid);
      }
      if (action === "accept") {
        if (!opponentOffered) throw new HttpError(409, "Kein Remis-Angebot");
        return gameResponse(await endGame(row, "1/2-1/2", "agreement"), me.uuid);
      }
      if (action === "decline") {
        if (!opponentOffered) throw new HttpError(409, "Kein Remis-Angebot");
        await db.run("UPDATE games SET draw_offer = NULL, updated = ? WHERE id = ?", [now(), row.id]);
        return gameResponse({ ...row, draw_offer: null, updated: now() }, me.uuid);
      }
      throw new HttpError(400, "Ungültige Aktion");
    },
  };

  /** Adresse des Aufrufers: von node-server.js übergeben oder (Cloudflare) aus der Kopfzeile ipHeader. */
  function clientIp(request, info) {
    if (info && info.ip) return String(info.ip);
    if (options.ipHeader) return request.headers.get(options.ipHeader) || "unbekannt";
    return "lokal";
  }

  /** Anfrage im Topf zählen; zu viele → 429 mit Retry-After. */
  function limit(name, ip) {
    const wait = limiters[name].hit(ip);
    if (wait) throw new HttpError(429, "Zu viele Anfragen – bitte kurz warten", wait);
  }

  /** Strengere Töpfe für Anmeldung ohne Konto und für Uploads. */
  const STRICT = { "POST /api/auth/challenge": "auth", "POST /api/auth/login": "auth", "POST /api/auth/offline": "auth", "POST /api/shots": "upload" };

  return async function handle(request, info) {
    try {
      return await dispatch(request, clientIp(request, info));
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status, error.retryAfter);
      throw error;
    }
  };

  async function dispatch(request, ip) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: { "access-control-allow-origin": "*", "access-control-allow-headers": "authorization, content-type, x-admin-key", "access-control-allow-methods": "GET, POST" } });
    }
    // Pfad ab "/api/" – so funktioniert der Server auch hinter einem Proxy mit Präfix
    const index = url.pathname.indexOf("/api/");
    const path = index >= 0 ? url.pathname.slice(index).replace(/\/+$/, "") : url.pathname;
    if (request.method === "GET" && /\/admin\/?$/.test(url.pathname)) {
      return new Response(ADMIN_PAGE, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "x-frame-options": "DENY" } });
    }
    // öffentlicher Screenshot-Link: /api/shots/<id>.jpg
    const shot = request.method === "GET" && path.match(/^\/api\/shots\/([0-9a-f]{16})(?:\.jpg)?$/);
    if (shot) {
      limit("view", ip);
      const row = await db.get("SELECT data FROM shots WHERE id = ?", [shot[1]]);
      if (!row) return json({ error: "Nicht gefunden" }, 404);
      const bin = atob(row.data), bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      return new Response(bytes, { headers: { "content-type": "image/jpeg", "cache-control": "public, max-age=31536000, immutable", "access-control-allow-origin": "*", "x-content-type-options": "nosniff" } });
    }
    let name = `${request.method} ${path}`;
    // Partie-Aktionen: /api/games/<id>/<aktion>
    const game = path.match(/^\/api\/games\/([^/]+)\/([a-z]+)$/);
    if (game) name = `${request.method} /api/games/:id/${game[2]}`;
    const route = routes[name];
    if (!route) return json({ error: "Nicht gefunden" }, 404);
    // Abfragen der Partien (häufig während einer Partie) haben einen eigenen Topf statt "all"
    limit(name === "GET /api/games" ? "games" : "all", ip);
    if (STRICT[name]) limit(STRICT[name], ip);
    try {
      return json(await route(request, ip, game ? game[1] : null));
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status, error.retryAfter);
      log(`Fehler bei ${path}: ${error.stack || error.message}`);
      return json({ error: "Serverfehler" }, 500);
    }
  }
}
