// Friends-API des Mytic Clients. Reine Web-API (Request → Response), damit derselbe Code unter Node
// (node-server.js, SQLite) und als Cloudflare Worker (worker.js, D1) läuft.
//
// Anmeldung mit Mojang-Zertifikat und signiertem Profil (mojang.js) – der Server fragt dafür nicht bei Mojang an
// (Mojang sperrt Cloudflare) und erfährt nie das Minecraft-Token.
import { headFromPng } from "./skin.js";
import { verifyLogin } from "./mojang.js";
import { MOJANG_KEYS } from "./mojang-keys.js";

/** Ab wann ein Spieler als offline gilt (Mod und Launcher melden sich jede Minute). */
export const ONLINE_MS = 150 * 1000;
const CHALLENGE_MS = 2 * 60 * 1000;
const SESSION_MS = 30 * 24 * 60 * 60 * 1000;
const HEAD_MS = 24 * 60 * 60 * 1000;
const MAX_FRIENDS = 300;
const MAX_PENDING = 50;

const NAME = /^[A-Za-z0-9_]{3,16}$/;
const UUID = /^[0-9a-f]{32}$/;

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "access-control-allow-origin": "*" },
  });
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
 * options: { allowOffline, fetch, now, log }
 */
export function createApi(db, options = {}) {
  const fetchImpl = options.fetch || ((...args) => fetch(...args));
  const now = options.now || (() => Date.now());
  const allowOffline = Boolean(options.allowOffline);
  const log = options.log || (() => {});
  const mojangKeys = options.mojangKeys || MOJANG_KEYS;

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
    return { me: { uuid: me.uuid, name: me.name, head: me.head || null, cape: me.cape || null }, friends: list, incoming: incoming.map(short), outgoing: outgoing.map(short), serverTime: now() };
  }

  async function body(request) {
    try {
      const data = await request.json();
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

  const routes = {
    "GET /api/health": async () => ({ ok: true, offline: allowOffline }),

    "POST /api/auth/challenge": async () => {
      const id = randomHex(20);
      await db.run("INSERT INTO challenges (id, created) VALUES (?, ?)", [id, now()]);
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

    /** Eigenes Cosmetic wählen (null = keins). */
    "POST /api/cosmetics": async (request) => {
      const me = await auth(request);
      const { cape } = await body(request);
      const value = typeof cape === "string" && /^[a-z0-9_-]{1,32}$/.test(cape) ? cape : null;
      await db.run("UPDATE users SET cape = ? WHERE uuid = ?", [value, me.uuid]);
      return { cape: value };
    },

    /** Cosmetics anderer Spieler (z. B. alle auf dem Server), höchstens 100 auf einmal. */
    "POST /api/cosmetics/lookup": async (request) => {
      await auth(request);
      const { uuids } = await body(request);
      const list = (Array.isArray(uuids) ? uuids : []).map((u) => String(u).replace(/-/g, "").toLowerCase()).filter((u) => UUID.test(u)).slice(0, 100);
      const capes = {};
      if (list.length) {
        const rows = await db.all(`SELECT uuid, cape FROM users WHERE cape IS NOT NULL AND uuid IN (${list.map(() => "?").join(",")})`, list);
        for (const row of rows) capes[row.uuid] = row.cape;
      }
      return { capes };
    },

    "POST /api/friends/request": async (request) => {
      const me = await auth(request);
      const { name } = await body(request);
      const target = (await findUser(name)) || (await placeholder(name));
      if (!target) throw new HttpError(404, "Diesen Minecraft-Namen gibt es nicht");
      if (target.uuid === me.uuid) throw new HttpError(400, "Du kannst dich nicht selbst hinzufügen");
      if (await areFriends(me.uuid, target.uuid)) throw new HttpError(409, `${target.name} ist schon dein Freund`);
      // hat der andere schon angefragt? Dann gleich befreunden
      if (await db.get("SELECT 1 AS ok FROM requests WHERE sender = ? AND target = ?", [target.uuid, me.uuid])) {
        await makeFriends(me.uuid, target.uuid);
        return { ...(await overview(me)), message: `Du bist jetzt mit ${target.name} befreundet` };
      }
      if ((await count("SELECT COUNT(*) AS n FROM friends WHERE a = ? OR b = ?", [me.uuid, me.uuid])) >= MAX_FRIENDS) throw new HttpError(409, "Freundesliste ist voll");
      if ((await count("SELECT COUNT(*) AS n FROM requests WHERE sender = ?", [me.uuid])) >= MAX_PENDING) throw new HttpError(429, "Zu viele offene Anfragen");
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
    "POST /api/friends/remove": async (request) => {
      const me = await auth(request);
      const { uuid } = await body(request);
      await db.run("DELETE FROM friends WHERE a = ? AND b = ?", pair(me.uuid, String(uuid)));
      await db.run("DELETE FROM requests WHERE sender = ? AND target = ?", [me.uuid, String(uuid)]);
      return overview(me);
    },
  };

  return async function handle(request) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: { "access-control-allow-origin": "*", "access-control-allow-headers": "authorization, content-type", "access-control-allow-methods": "GET, POST" } });
    }
    // Pfad ab "/api/" – so funktioniert der Server auch hinter einem Proxy mit Präfix
    const index = url.pathname.indexOf("/api/");
    const path = index >= 0 ? url.pathname.slice(index).replace(/\/+$/, "") : url.pathname;
    const route = routes[`${request.method} ${path}`];
    if (!route) return json({ error: "Nicht gefunden" }, 404);
    try {
      return json(await route(request));
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status);
      log(`Fehler bei ${path}: ${error.stack || error.message}`);
      return json({ error: "Serverfehler" }, 500);
    }
  };
}
