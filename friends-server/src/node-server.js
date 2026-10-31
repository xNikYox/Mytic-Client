// Friends-Server unter Node 22 mit SQLite (node:sqlite). Start: node src/node-server.js [--port 8766] [--db friends.db] [--offline]
// Offline-Konten nur für die Entwicklerversion erlauben (--offline oder MYTIC_FRIENDS_OFFLINE=1) – dann nur auf 127.0.0.1.
// Hinter einem eigenen Reverse-Proxy: TRUST_PROXY=1, damit X-Forwarded-For als Adresse gilt.
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { DatabaseSync } from "node:sqlite";
import { createApi, SHOT_BODY_MAX } from "./api.js";
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

/**
 * Adresse des Aufrufers. X-Forwarded-For nur hinter einem eigenen Proxy beachten (TRUST_PROXY=1), sonst könnte
 * jeder die Bremse mit erfundenen Adressen umgehen. Dann zählt der letzte Eintrag (vom eigenen Proxy angehängt).
 */
export function clientIp(req, trustProxy = process.env.TRUST_PROXY === "1") {
  if (trustProxy && req.headers["x-forwarded-for"]) {
    const last = String(req.headers["x-forwarded-for"]).split(",").map((s) => s.trim()).filter(Boolean).pop();
    if (last) return last;
  }
  return String(req.socket.remoteAddress || "");
}

export function createServer(api, { trustProxy } = {}) {
  return http.createServer(async (req, res) => {
    const ip = clientIp(req, trustProxy);
    // Inhalt mit harter Grenze lesen (Screenshot-Upload größer); api.js prüft zusätzlich und bremst je Adresse
    const max = /\/api\/shots\/?(\?|$)/.test(req.url || "") ? SHOT_BODY_MAX : 16 * 1024;
    const chunks = [];
    let size = 0;
    if (Number(req.headers["content-length"] || 0) > max) {
      res.writeHead(413, { "content-type": "application/json", connection: "close" });
      res.end(JSON.stringify({ error: "Anfrage zu groß" }));
      return;
    }
    for await (const chunk of req) {
      size += chunk.length;
      if (size > max) {
        res.writeHead(413, { "content-type": "application/json", connection: "close" });
        res.end(JSON.stringify({ error: "Anfrage zu groß" }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    }
    const request = new Request(`http://localhost${req.url}`, {
      method: req.method,
      headers: req.headers,
      body: ["GET", "HEAD"].includes(req.method) ? undefined : Buffer.concat(chunks),
    });
    const response = await api(request, { ip });
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
  // mit Offline-Konten nur auf diesem PC erreichbar (Entwicklerversion), sonst HOST oder alle Adressen
  const host = allowOffline ? "127.0.0.1" : process.env.HOST || undefined;
  if (allowOffline) {
    console.warn("!".repeat(78));
    console.warn("!! WARNUNG: OFFLINE-KONTEN ERLAUBT – Anmeldung ohne Microsoft-Konto unter beliebigem Namen.");
    console.warn("!! Nur für die Entwicklerversion. Der Server lauscht deshalb nur auf 127.0.0.1.");
    console.warn("!".repeat(78));
  }
  const trustProxy = process.env.TRUST_PROXY === "1";
  const api = createApi(openDb(file), { allowOffline, adminKey, log: (line) => console.log(new Date().toISOString(), line) });
  createServer(api, { trustProxy }).listen(port, host, () => console.log(`Mytic Friends-Server auf ${host || "allen Adressen"}, Port ${port} (Offline-Konten: ${allowOffline ? "ja" : "nein"}, X-Forwarded-For: ${trustProxy ? "ja" : "nein"}, DB: ${file})`));
}
