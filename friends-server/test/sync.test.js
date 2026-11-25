import { test } from "node:test";
import assert from "node:assert/strict";
import zlib from "node:zlib";
import nodeCrypto from "node:crypto";
import { openDb } from "../src/node-server.js";
import { createApi, SYNC_MAX } from "../src/api.js";

function setup() {
  let clock = 1_000_000_000_000;
  const api = createApi(openDb(":memory:"), { fetch: async () => new Response(null, { status: 404 }), now: () => clock, allowOffline: true });
  const call = async (method, path, data, token) => {
    const res = await api(new Request(`http://x${path}`, {
      method,
      headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: data ? JSON.stringify(data) : undefined,
    }));
    return { status: res.status, data: await res.json() };
  };
  const login = async (name) => {
    const res = await call("POST", "/api/auth/offline", { name });
    assert.equal(res.status, 200);
    return res.data.token;
  };
  return { call, login, tick: (ms) => { clock += ms; }, now: () => clock };
}

/** Stand wie vom Launcher: gzip(JSON) als Base64 und SHA-256 des JSON */
function blob(content) {
  const text = JSON.stringify(content);
  return { data: zlib.gzipSync(text).toString("base64"), hash: nodeCrypto.createHash("sha256").update(text).digest("hex") };
}

test("Sync: leer, hochladen, auf anderem PC holen, pro Konto", async () => {
  const s = setup();
  const a = await s.login("Alice");
  assert.equal((await s.call("GET", "/api/sync")).status, 401);
  assert.equal((await s.call("PUT", "/api/sync", blob({ x: 1 }))).status, 401);
  const empty = await s.call("GET", "/api/sync", null, a);
  assert.deepEqual(empty.data, { version: 0, hash: null, updatedAt: 0, data: null });

  const one = blob({ files: { "myticclient.json": { enabled: { zoom: true } } } });
  const put = await s.call("PUT", "/api/sync", { ...one, device: "Desktop" }, a);
  assert.equal(put.status, 200);
  assert.equal(put.data.version, 1);
  assert.equal(put.data.hash, one.hash);
  assert.equal(put.data.updatedAt, s.now());

  const other = await s.login("Alice"); // zweiter PC
  const got = await s.call("GET", "/api/sync", null, other);
  assert.equal(got.data.version, 1);
  assert.equal(got.data.data, one.data);
  assert.equal(got.data.device, "Desktop");
  assert.deepEqual(JSON.parse(zlib.gunzipSync(Buffer.from(got.data.data, "base64"))), { files: { "myticclient.json": { enabled: { zoom: true } } } });

  // gleicher Stand → ohne Daten
  const same = await s.call("GET", `/api/sync?have=${one.hash}`, null, other);
  assert.equal(same.data.unchanged, true);
  assert.equal(same.data.data, undefined);

  assert.equal((await s.call("GET", "/api/sync", null, await s.login("Bob"))).data.version, 0, "pro Konto");
});

test("Sync: gleicher Stand schreibt nicht, neuer Stand erhöht die Version", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const one = blob({ v: 1 });
  await s.call("PUT", "/api/sync", one, a);
  s.tick(5000);
  const again = await s.call("PUT", "/api/sync", one, a);
  assert.equal(again.data.unchanged, true);
  assert.equal(again.data.version, 1);
  assert.equal(again.data.updatedAt, s.now() - 5000);
  const two = await s.call("PUT", "/api/sync", blob({ v: 2 }), a);
  assert.equal(two.data.version, 2);
});

test("Sync: Konflikt mit ifVersion liefert den Stand des Servers, force überschreibt", async () => {
  const s = setup();
  const pc1 = await s.login("Alice");
  const pc2 = await s.login("Alice");
  const first = blob({ from: "pc1" });
  await s.call("PUT", "/api/sync", { ...first, ifVersion: 0 }, pc1);
  const mine = blob({ from: "pc2" });
  // PC 2 kennt Version 0 – inzwischen gibt es Version 1
  const clash = await s.call("PUT", "/api/sync", { ...mine, ifVersion: 0 }, pc2);
  assert.equal(clash.status, 200);
  assert.equal(clash.data.conflict, true);
  assert.equal(clash.data.version, 1);
  assert.equal(clash.data.data, first.data, "überschriebener Stand kommt zum Sichern mit");
  const forced = await s.call("PUT", "/api/sync", { ...mine, ifVersion: 0, force: true }, pc2);
  assert.equal(forced.data.version, 2);
  // passende Version: kein Konflikt
  const next = await s.call("PUT", "/api/sync", { ...blob({ from: "pc1", n: 2 }), ifVersion: 2 }, pc1);
  assert.equal(next.data.version, 3);
});

test("Sync: Prüfungen (Prüfsumme, Base64, gzip, Größe)", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const ok = blob({ a: 1 });
  assert.equal((await s.call("PUT", "/api/sync", { ...ok, hash: "xyz" }, a)).status, 400);
  assert.equal((await s.call("PUT", "/api/sync", { ...ok, data: "nicht base64!" }, a)).status, 400);
  assert.equal((await s.call("PUT", "/api/sync", { ...ok, data: Buffer.from("{}").toString("base64") }, a)).status, 400, "kein gzip");
  // zu groß (zufällige Daten lassen sich nicht packen)
  const big = zlib.gzipSync(nodeCrypto.randomBytes(SYNC_MAX + 1024), { level: 0 }).toString("base64");
  assert.equal((await s.call("PUT", "/api/sync", { data: big, hash: ok.hash }, a)).status, 413);
  // knapp unter der Grenze geht
  const fits = zlib.gzipSync(nodeCrypto.randomBytes(200 * 1024), { level: 0 }).toString("base64");
  assert.equal((await s.call("PUT", "/api/sync", { data: fits, hash: ok.hash }, a)).status, 200);
});

test("Sync: Lebenszeichen bringt den Stand mit (ohne eigene Abfrage), caps enthält sync1", async () => {
  const s = setup();
  const a = await s.login("Alice");
  let beat = await s.call("POST", "/api/presence", { place: "game" }, a);
  assert.ok(beat.data.caps.includes("sync1"));
  assert.ok(beat.data.caps.includes("presence2"), "alte Fähigkeit bleibt");
  assert.equal(beat.data.sync, undefined, "noch nichts hochgeladen");
  const one = blob({ v: 1 });
  await s.call("PUT", "/api/sync", one, a);
  beat = await s.call("POST", "/api/presence", { place: "launcher" }, a);
  assert.deepEqual(beat.data.sync, { version: 1, hash: one.hash, updatedAt: s.now() });
  beat = await s.call("POST", "/api/presence", { place: "game", hidden: true }, a);
  assert.equal(beat.data.sync.hash, one.hash);
});

test("Sync: alte Cloud-Einstellungen (/api/settings) funktionieren weiter", async () => {
  const s = setup();
  const a = await s.login("Alice");
  assert.equal((await s.call("POST", "/api/settings", { data: { enabled: { zoom: true } } }, a)).status, 200);
  await s.call("PUT", "/api/sync", blob({ v: 1 }), a);
  assert.equal((await s.call("GET", "/api/settings", null, a)).data.data.enabled.zoom, true);
});
