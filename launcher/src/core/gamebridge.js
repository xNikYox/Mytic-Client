// Verbindung zwischen Launcher und laufendem Spiel für den Kontowechsel im Hauptmenü.
// Nur auf 127.0.0.1, jede Instanz bekommt einen eigenen Zufallsschlüssel (Umgebungsvariable, nicht in der Kommandozeile).
// Ohne gültigen Schlüssel gibt es nichts – auch keine Kontoliste. Anfragen aus Browsern (Origin-Kopf) werden abgelehnt.
const http = require("node:http");
const crypto = require("node:crypto");

const MAX_BODY = 2048;

/**
 * listAccounts(): [{ uuid, name, type, expiresAt, needsLogin }] (nie Tokens)
 * switchAccount(uuid, key): Konto mit frischem Token { name, uuid, accessToken, xuid, clientId, type, friendsToken }
 * refreshAccount(uuid, key): wie switchAccount, aber die Anmeldung wird in jedem Fall erneuert (Refresh-Token, sonst
 *   Microsoft-Login im Launcher); zusätzlich { relogin: true }, wenn neu angemeldet werden musste
 * addAccount(key): Microsoft-Login im Launcher, liefert { uuid, name }
 * removeAccount(uuid, key): Konto aus dem Launcher entfernen
 * sync (optional, Einstellungs-Sync): { status(), upload(), download(), backups(), restore(id) } – Knöpfe im Spiel
 */
const FEATURES = ["refresh", "add", "remove", "sync"];

function createBridge({ listAccounts, switchAccount, refreshAccount, addAccount, removeAccount, sync, log = () => {} }) {
  const keys = new Set();
  let server = null;
  let port = 0;
  let starting = null;

  function validKey(header) {
    const m = /^Bearer ([A-Za-z0-9_-]{20,})$/.exec(header || "");
    if (!m) return null;
    const given = Buffer.from(m[1]);
    for (const key of keys) {
      const expected = Buffer.from(key);
      if (expected.length === given.length && crypto.timingSafeEqual(expected, given)) return key;
    }
    return null;
  }

  function send(res, status, body) {
    const data = JSON.stringify(body);
    res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "Content-Length": Buffer.byteLength(data) });
    res.end(data);
  }

  async function handle(req, res) {
    // nur das Spiel: kein Browser (Origin), richtiger Host (gegen DNS-Rebinding), gültiger Schlüssel
    if (req.headers.origin || req.headers.host !== `127.0.0.1:${port}`) return send(res, 403, { error: "forbidden" });
    const key = validKey(req.headers.authorization);
    if (!key) return send(res, 401, { error: "unauthorized" });
    if (req.method === "GET" && req.url === "/accounts") {
      const accounts = listAccounts().map(({ uuid, name, type, expiresAt, needsLogin }) => ({
        uuid, name, type: type || "microsoft", expiresAt: Number(expiresAt) || 0, needsLogin: Boolean(needsLogin),
      }));
      return send(res, 200, { accounts, features: FEATURES.filter((f) => ({ refresh: refreshAccount, add: addAccount, remove: removeAccount, sync })[f]) });
    }
    if (sync && req.url.startsWith("/sync")) return handleSync(req, res);
    if (req.method !== "POST") return send(res, 404, { error: "not found" });
    const actions = { "/switch": switchAccount, "/refresh": refreshAccount, "/remove": removeAccount };
    if (req.url === "/add" && addAccount) {
      try {
        return send(res, 200, await addAccount(key));
      } catch (error) {
        log(`[Konto hinzufügen] ${error.message}`);
        return send(res, 502, { error: error.message });
      }
    }
    const action = actions[req.url];
    if (!action) return send(res, 404, { error: "not found" });
    let raw = "";
    for await (const chunk of req) {
      raw += chunk;
      if (raw.length > MAX_BODY) return send(res, 413, { error: "too large" });
    }
    let uuid;
    try {
      uuid = String(JSON.parse(raw).uuid || "");
    } catch {
      return send(res, 400, { error: "bad json" });
    }
    if (!listAccounts().some((a) => a.uuid === uuid)) return send(res, 404, { error: "Konto nicht im Launcher" });
    try {
      return send(res, 200, (await action(uuid, key)) || { ok: true });
    } catch (error) {
      log(`[Konto ${req.url.slice(1)}] ${error.message}`);
      return send(res, 502, { error: error.message });
    }
  }

  /** Einstellungs-Sync: GET /sync (Status), GET /sync/backups, POST /sync/upload|download, POST /sync/restore { id } */
  async function handleSync(req, res) {
    const route = `${req.method} ${req.url}`;
    let raw = "";
    for await (const chunk of req) {
      raw += chunk;
      if (raw.length > MAX_BODY) return send(res, 413, { error: "too large" });
    }
    try {
      if (route === "GET /sync") return send(res, 200, await sync.status());
      if (route === "GET /sync/backups") return send(res, 200, { backups: await sync.backups() });
      if (route === "POST /sync/upload") return send(res, 200, await sync.upload());
      if (route === "POST /sync/download") return send(res, 200, await sync.download());
      if (route === "POST /sync/restore") {
        let id;
        try {
          id = String(JSON.parse(raw).id || "");
        } catch {
          return send(res, 400, { error: "bad json" });
        }
        return send(res, 200, await sync.restore(id));
      }
    } catch (error) {
      log(`[Sync] ${error.message}`);
      return send(res, 502, { error: error.message });
    }
    return send(res, 404, { error: "not found" });
  }

  /** Startet den Server (einmal) und gibt die Adresse zurück */
  function start() {
    if (server && port) return Promise.resolve(`http://127.0.0.1:${port}/`);
    if (starting) return starting;
    starting = new Promise((resolve, reject) => {
      server = http.createServer((req, res) => handle(req, res).catch(() => send(res, 500, { error: "internal" })));
      server.requestTimeout = 30000; // nur für das Empfangen der Anfrage; Antworten (Microsoft-Login) dürfen länger dauern
      server.once("error", (error) => {
        server = null;
        starting = null;
        reject(error);
      });
      server.listen(0, "127.0.0.1", () => {
        port = server.address().port;
        starting = null;
        resolve(`http://127.0.0.1:${port}/`);
      });
    });
    return starting;
  }

  /** Neuer Schlüssel für eine Spielinstanz */
  function issueKey() {
    const key = crypto.randomBytes(32).toString("base64url");
    keys.add(key);
    return key;
  }

  /** Schlüssel ungültig machen; ohne laufende Spiele den Server schließen */
  function revoke(key) {
    keys.delete(key);
    if (!keys.size && server) {
      server.close();
      server = null;
      port = 0;
    }
  }

  return { start, issueKey, revoke };
}

module.exports = { createBridge };
