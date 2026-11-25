// Einstellungs-Sync: Entscheidung, Dateien, Sicherungen und Abgleich mit dem echten Friends-Server-Code (In-Memory-SQLite).
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { CloudSync, decide, canonical, pack, unpack, syncedPart, mergeLocal, SYNC_FILES } = require("../src/core/cloudsync");

const MAIN = SYNC_FILES[0];

function tmpDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "mytic-sync-"));
}

function writeConfig(dir, obj, name = "myticclient.json") {
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, name), JSON.stringify(obj, null, 2));
}

function readConfig(dir, name = "myticclient.json") {
  return JSON.parse(fs.readFileSync(path.join(dir, name), "utf8"));
}

/** Echter Friends-Server (api.js) mit Offline-Konten, Zähler für Anfragen */
async function server() {
  const { createApi } = await import("../../friends-server/src/api.js");
  const { openDb } = await import("../../friends-server/src/node-server.js");
  let clock = 1_700_000_000_000;
  const api = createApi(openDb(":memory:"), { fetch: async () => new Response(null, { status: 404 }), now: () => clock, allowOffline: true });
  const requests = [];
  const raw = async (method, p, data, token) => {
    const res = await api(new Request(`http://x/${p}`, {
      method, headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: data ? JSON.stringify(data) : undefined,
    }));
    const body = await res.json();
    if (!res.ok) throw new Error(body.error);
    return body;
  };
  const login = async (name) => (await raw("POST", "api/auth/offline", { name })).token;
  /** call() für CloudSync mit eigenem Token (= ein PC) */
  const client = (token) => async (method, p, data) => {
    requests.push(`${method} ${p.split("?")[0]}`);
    return raw(method, p, data, token);
  };
  return { raw, login, client, requests, tick: (ms) => { clock += ms; }, now: () => clock };
}

function pc(srv, token, opts = {}) {
  const configDir = tmpDir();
  const dataDir = tmpDir();
  const sync = new CloudSync({ configDir, dataDir, call: srv.client(token), uuid: () => opts.uuid || "alice", device: opts.device || "PC", now: () => srv.now(), ...opts.extra });
  return { configDir, dataDir, sync };
}

test("decide: alle Fälle", () => {
  const L = (hash, extra = {}) => ({ hash, mtime: 100, empty: false, ...extra });
  assert.equal(decide(L("a"), null, { hash: null }), "upload", "Cloud leer");
  assert.equal(decide(L("a", { empty: true }), null, { hash: null }), "none", "beides leer");
  assert.equal(decide(L("a"), { hash: "x" }, { hash: "a" }), "none", "gleich");
  assert.equal(decide(L("a"), null, { hash: "b" }), "download", "neuer PC: Cloud gewinnt");
  assert.equal(decide(L("a", { empty: true }), { hash: "a" }, { hash: "b" }), "download");
  assert.equal(decide(L("a"), { hash: "b" }, { hash: "b" }), "upload", "nur lokal geändert");
  assert.equal(decide(L("a"), { hash: "a" }, { hash: "b" }), "download", "nur woanders geändert");
  assert.equal(decide(L("a", { mtime: 50 }), { hash: "c" }, { hash: "b", updatedAt: 60 }), "download", "beides: Cloud neuer");
  assert.equal(decide(L("a", { mtime: 70 }), { hash: "c" }, { hash: "b", updatedAt: 60 }), "upload", "beides: lokal neuer");
});

test("Snapshot: sortiert, ohne PC-Werte und Geheimnisse; Zusammenführen behält Lokales", () => {
  assert.equal(canonical({ b: 1, a: [2, { d: 1, c: 2 }] }), '{"a":[2,{"c":2,"d":1}],"b":1}');
  const local = {
    enabled: { zoom: true, clips: true, fpsboost: false }, settings: { "crosshair.color": 1, "clips.length": 30, "chat.timestamps": true },
    positions: { fps: [1, 2] }, savedGamma: 0.4, savedOptions: { gamma: "1" }, ownedCache: ["cape:x"], adminCache: true, cape: "nordlicht",
    hudProfiles: { PvP: { positions: {} } }, serverProfiles: { "hypixel.net": { hudProfile: "PvP", modules: { zoom: true, clips: false } } },
    authToken: "geheim", nested: { sessionId: "x", ok: 1 },
  };
  const part = syncedPart(MAIN, local);
  assert.deepEqual(part.enabled, { zoom: true });
  assert.deepEqual(part.settings, { "crosshair.color": 1, "chat.timestamps": true });
  assert.equal(part.cape, "nordlicht", "Cosmetics-Auswahl wandert mit");
  assert.ok(part.hudProfiles.PvP);
  assert.deepEqual(part.serverProfiles["hypixel.net"].modules, { zoom: true });
  for (const k of ["savedGamma", "savedOptions", "ownedCache", "adminCache", "authToken"]) assert.equal(k in part, false, k);
  assert.deepEqual(part.nested, { ok: 1 });

  const merged = mergeLocal(MAIN, { enabled: { zoom: false }, settings: { "crosshair.color": 2 }, cape: null }, local);
  assert.deepEqual(merged.enabled, { zoom: false, clips: true, fpsboost: false });
  assert.deepEqual(merged.settings, { "crosshair.color": 2, "clips.length": 30 });
  assert.equal(merged.savedGamma, 0.4);
  assert.deepEqual(merged.ownedCache, ["cape:x"]);
  assert.equal(merged.cape, null);
});

test("pack/unpack und Größengrenze", () => {
  const { data, hash } = pack({ v: 1, files: { "myticclient.json": { a: 1 } } });
  assert.match(hash, /^[0-9a-f]{64}$/);
  assert.ok(data.startsWith("H4sI"));
  assert.deepEqual(unpack(data), { v: 1, files: { "myticclient.json": { a: 1 } } });
  const crypto = require("node:crypto");
  assert.throws(() => pack({ v: 1, files: { x: { junk: crypto.randomBytes(300 * 1024).toString("base64") } } }), /zu groß/);
});

test("Zwei PCs: hochladen, auf neuem PC herunterladen (mit Sicherung), Änderungen hin und her", async (t) => {
  let srv;
  try {
    srv = await server();
  } catch (error) {
    t.skip(`Friends-Server nicht ladbar: ${error.message}`);
    return;
  }
  const token1 = await srv.login("Alice");
  const token2 = await srv.login("Alice");
  const a = pc(srv, token1, { device: "Desktop" });
  const b = pc(srv, token2, { device: "Laptop" });
  writeConfig(a.configDir, { enabled: { zoom: true, clips: true }, positions: { fps: [5, 5] }, settings: { "crosshair.color": 7 }, savedGamma: 0.3, cape: "inferno" });
  writeConfig(a.configDir, { chosen: { gui: "dark" } }, "mytic-styles.json");
  writeConfig(b.configDir, { enabled: { zoom: false, clips: false }, savedGamma: 0.9 });

  // PC A: Cloud leer → hochladen
  let r = await a.sync.sync();
  assert.equal(r.action, "upload");
  assert.deepEqual(srv.requests, ["GET api/sync", "PUT api/sync"]);

  // PC B (neu, eigene Standardwerte): Cloud gewinnt, lokaler Stand wird gesichert
  srv.tick(1000);
  r = await b.sync.sync();
  assert.equal(r.action, "download");
  let cfg = readConfig(b.configDir);
  assert.equal(cfg.enabled.zoom, true);
  assert.equal(cfg.enabled.clips, false, "Clips bleiben je PC");
  assert.equal(cfg.savedGamma, 0.9, "PC-Werte bleiben");
  assert.equal(cfg.cape, "inferno");
  assert.deepEqual(readConfig(b.configDir, "mytic-styles.json"), { chosen: { gui: "dark" } });
  const backups = await b.sync.backups();
  assert.equal(backups.length, 1);
  assert.equal(backups[0].reason, "lokal");

  // nichts geändert: Abgleich mit Stand aus dem Lebenszeichen kostet keine Anfrage
  srv.requests.length = 0;
  const hint = (await srv.raw("POST", "api/presence", { place: "launcher" }, token2)).sync;
  r = await b.sync.sync({ hint });
  assert.equal(r.action, "none");
  assert.equal(r.requests, 0);
  // ohne Hinweis: eine GET-Anfrage ohne Daten
  r = await b.sync.sync();
  assert.equal(r.action, "none");
  assert.deepEqual(srv.requests, ["GET api/sync"]);

  // B ändert etwas (im Spiel) → beim Beenden hochladen; A holt es beim nächsten Start
  srv.requests.length = 0;
  writeConfig(b.configDir, { ...readConfig(b.configDir), positions: { fps: [9, 9] } });
  r = await b.sync.uploadIfChanged();
  assert.equal(r.action, "upload");
  r = await b.sync.uploadIfChanged();
  assert.equal(r.action, "none", "nur bei Änderung");
  assert.deepEqual(srv.requests, ["PUT api/sync"]);
  r = await a.sync.sync();
  assert.equal(r.action, "download");
  assert.deepEqual(readConfig(a.configDir).positions, { fps: [9, 9] });
  assert.equal(readConfig(a.configDir).enabled.clips, true, "Clips auf A bleiben an");
  assert.equal(readConfig(a.configDir).savedGamma, 0.3);
});

test("Konflikt: beide geändert – zuletzt geschrieben gewinnt, Stand der Cloud wird gesichert und lässt sich wiederherstellen", async (t) => {
  let srv;
  try {
    srv = await server();
  } catch (error) {
    t.skip(`Friends-Server nicht ladbar: ${error.message}`);
    return;
  }
  const a = pc(srv, await srv.login("Alice"));
  const b = pc(srv, await srv.login("Alice"));
  writeConfig(a.configDir, { settings: { "chat.x": 1 } });
  await a.sync.sync();
  await b.sync.sync();
  // A lädt hoch, B hat gleichzeitig (ohne Abgleich) etwas anderes geändert
  writeConfig(a.configDir, { settings: { "chat.x": 2 } });
  await a.sync.uploadIfChanged();
  writeConfig(b.configDir, { settings: { "chat.x": 3 } });
  srv.requests.length = 0;
  const r = await b.sync.uploadIfChanged();
  assert.equal(r.action, "upload");
  assert.deepEqual(srv.requests, ["PUT api/sync", "PUT api/sync"], "Konflikt → sichern → erzwingen");
  const saved = (await b.sync.backups()).find((x) => x.reason === "cloud");
  assert.ok(saved, "Stand von A liegt als Sicherung auf B");
  await b.sync.restore(saved.id);
  assert.equal(readConfig(b.configDir).settings["chat.x"], 2);
  assert.ok((await b.sync.backups()).some((x) => x.reason === "wiederherstellen"), "Stand vor dem Wiederherstellen ist gesichert");
  // wiederhergestellter Stand geht beim nächsten Abgleich hoch
  assert.equal((await b.sync.uploadIfChanged()).action, "upload");
});

test("Während des Spiels: Änderungen gesammelt hochladen, beim Beenden sofort", async (t) => {
  let srv;
  try {
    srv = await server();
  } catch (error) {
    t.skip(`Friends-Server nicht ladbar: ${error.message}`);
    return;
  }
  const a = pc(srv, await srv.login("Alice"), { extra: { debounceMs: 80, watchMs: 20 } });
  writeConfig(a.configDir, { settings: { "zoom.x": 1 } });
  await a.sync.sync();
  srv.requests.length = 0;
  a.sync.watch();
  for (let i = 2; i < 5; i++) {
    writeConfig(a.configDir, { settings: { "zoom.x": i } });
    await new Promise((r) => setTimeout(r, 30));
  }
  await new Promise((r) => setTimeout(r, 250));
  assert.deepEqual(srv.requests, ["PUT api/sync"], "drei Änderungen → ein Upload");
  writeConfig(a.configDir, { settings: { "zoom.x": 9 } });
  const r = await a.sync.stop();
  assert.equal(r.action, "upload");
  assert.equal(srv.requests.length, 2);
  assert.equal((await a.sync.stop()).action, "none");
});

test("Sicherungen: höchstens 10, ungültige Kennung abgelehnt", async () => {
  let clock = 1000;
  const s = new CloudSync({ configDir: tmpDir(), dataDir: tmpDir(), call: async () => ({}), uuid: () => "u", now: () => clock++ });
  for (let i = 0; i < 13; i++) await s.backup({ v: 1, files: {} }, "lokal");
  assert.equal((await s.backups()).length, 10);
  await assert.rejects(() => s.restore("../../etc/passwd"), /Unbekannte Sicherung/);
});
