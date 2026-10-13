// Friends-Server unter Node 22 mit SQLite (node:sqlite). Start: node src/node-server.js [--port 8766] [--db friends.db] [--offline]
// Offline-Konten nur für die Entwicklerversion erlauben (--offline oder MYTIC_FRIENDS_OFFLINE=1).
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { DatabaseSync } from "node:sqlite";
import { createApi } from "./api.js";
import { SCHEMA, MIGRATIONS } from "./schema.js";

const here = path.dirname(fileURLToPath(import.meta.url));

export function openDb(file) {
  const sqlite = new DatabaseSync(file);
  sqlite.exec("PRAGMA journal_mode = WAL");
  sqlite.exec(SCHEMA);
  for (const sql of MIGRATIONS) {
    try {
      sqlite.exec(sql);
    } catch {
      // schon vorhanden
    }
  }
  return {
    all: async (sql, params = []) => sqlite.prepare(sql).all(...params),
    get: async (sql, params = []) => sqlite.prepare(sql).get(...params) || null,
    run: async (sql, params = []) => sqlite.prepare(sql).run(...params),
    close: () => sqlite.close(),
  };
}

/** Einfache Bremse gegen zu viele Anfragen pro Adresse (120 pro Minute). */
function limiter(perMinute = 120) {
  const hits = new Map();
  return (key) => {
    const t = Date.now();
    const entry = hits.get(key) || { start: t, n: 0 };
    if (t - entry.start > 60000) {
      entry.start = t;
      entry.n = 0;
    }
    entry.n++;
    hits.set(key, entry);
    if (hits.size > 10000) hits.clear();
    return entry.n <= perMinute;
  };
}

export function createServer(api) {
  const allow = limiter();
  return http.createServer(async (req, res) => {
    const ip = String(req.headers["x-forwarded-for"] || req.socket.remoteAddress || "").split(",")[0].trim();
    if (!allow(ip)) {
      res.writeHead(429, { "content-type": "application/json" });
      res.end(JSON.stringify({ error: "Zu viele Anfragen" }));
      return;
    }
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
      size += chunk.length;
      if (size > 16 * 1024) {
        res.writeHead(413);
        res.end();
        return;
      }
      chunks.push(chunk);
    }
    const request = new Request(`http://localhost${req.url}`, {
      method: req.method,
      headers: req.headers,
      body: ["GET", "HEAD"].includes(req.method) ? undefined : Buffer.concat(chunks),
    });
    const response = await api(request);
    res.writeHead(response.status, Object.fromEntries(response.headers));
    res.end(Buffer.from(await response.arrayBuffer()));
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const arg = (name, fallback) => {
    const i = process.argv.indexOf(name);
    return i > 0 ? process.argv[i + 1] : fallback;
  };
  const port = Number(arg("--port", process.env.PORT || 8766));
  const file = arg("--db", process.env.MYTIC_FRIENDS_DB || path.join(here, "..", "friends.db"));
  const allowOffline = process.argv.includes("--offline") || process.env.MYTIC_FRIENDS_OFFLINE === "1";
  // Admin-Passwort: MYTIC_ADMIN_KEY oder Datei .admin-key neben dem Server (nicht im Repository)
  const keyFile = new URL("../.admin-key", import.meta.url);
  let adminKey = process.env.MYTIC_ADMIN_KEY || "";
  try {
    if (!adminKey) adminKey = (await import("node:fs")).readFileSync(keyFile, "utf8").trim();
  } catch {
    // keine Admin-Seite
  }
  const api = createApi(openDb(file), { allowOffline, adminKey, log: (line) => console.log(new Date().toISOString(), line) });
  createServer(api).listen(port, () => console.log(`Mytic Friends-Server auf Port ${port} (Offline-Konten: ${allowOffline ? "ja" : "nein"}, DB: ${file})`));
}
