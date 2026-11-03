// Verbindung zwischen Launcher und laufendem Spiel für den Kontowechsel im Hauptmenü.
// Nur auf 127.0.0.1, jede Instanz bekommt einen eigenen Zufallsschlüssel (Umgebungsvariable, nicht in der Kommandozeile).
// Ohne gültigen Schlüssel gibt es nichts – auch keine Kontoliste. Anfragen aus Browsern (Origin-Kopf) werden abgelehnt.
const http = require("node:http");
const crypto = require("node:crypto");

const MAX_BODY = 2048;

/**
 * listAccounts(): [{ uuid, name }]
 * switchAccount(uuid, key): Konto mit frischem Token { name, uuid, accessToken, xuid, clientId, type, friendsToken }
 */
function createBridge({ listAccounts, switchAccount, log = () => {} }) {
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
      return send(res, 200, { accounts: listAccounts().map(({ uuid, name }) => ({ uuid, name })) });
    }
    if (req.method === "POST" && req.url === "/switch") {
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
        return send(res, 200, await switchAccount(uuid, key));
      } catch (error) {
        log(`[Kontowechsel] ${error.message}`);
        return send(res, 502, { error: error.message });
      }
    }
    return send(res, 404, { error: "not found" });
  }

  /** Startet den Server (einmal) und gibt die Adresse zurück */
  function start() {
    if (server && port) return Promise.resolve(`http://127.0.0.1:${port}/`);
    if (starting) return starting;
    starting = new Promise((resolve, reject) => {
      server = http.createServer((req, res) => handle(req, res).catch(() => send(res, 500, { error: "internal" })));
      server.requestTimeout = 30000;
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
