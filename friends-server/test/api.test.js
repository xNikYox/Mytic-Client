import { test } from "node:test";
import assert from "node:assert/strict";
import zlib from "node:zlib";
import nodeCrypto from "node:crypto";
import { certificatePayload, challengeText, epochMillis } from "../src/mojang.js";
import { openDb } from "../src/node-server.js";
import { createApi, offlineUuid, ONLINE_MS } from "../src/api.js";
import { headFromPng } from "../src/skin.js";

function crc32(buf) {
  let c;
  let crc = 0xffffffff;
  for (const b of buf) {
    c = (crc ^ b) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}

/** 64×64-Skin: Gesicht rot, Hut-Pixel (40,8) blau. */
function skinPng() {
  const w = 64;
  const raw = Buffer.alloc((w * 4 + 1) * w);
  for (let y = 0; y < w; y++) {
    raw[y * (w * 4 + 1)] = y % 2 ? 2 : 1; // Filter abwechseln (Sub/Up), damit beide geprüft werden
    for (let x = 0; x < w; x++) {
      let px = [0, 0, 0, 0];
      if (x >= 8 && x < 16 && y >= 8 && y < 16) px = [200, 10, 10, 255];
      if (x === 40 && y === 8) px = [0, 0, 255, 255];
      raw.set(px, y * (w * 4 + 1) + 1 + x * 4);
    }
  }
  // Filter anwenden
  const out = Buffer.from(raw);
  for (let y = w - 1; y >= 0; y--) {
    const f = raw[y * (w * 4 + 1)];
    for (let i = w * 4 - 1; i >= 0; i--) {
      const pos = y * (w * 4 + 1) + 1 + i;
      const left = i >= 4 ? raw[pos - 4] : 0;
      const up = y > 0 ? raw[pos - (w * 4 + 1)] : 0;
      out[pos] = (raw[pos] - (f === 1 ? left : up)) & 255;
    }
  }
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(w, 4);
  ihdr.set([8, 6, 0, 0, 0], 8);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(out)), chunk("IEND", Buffer.alloc(0))]);
}

const ALICE = "a".repeat(32);
const BOB = "b".repeat(32);

// Eigene "Mojang"-Schlüssel für die Tests
const rsa = () => nodeCrypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
const CERT_KEY = rsa();
const PROPERTY_KEY = rsa();
const spki = (key) => key.publicKey.export({ type: "spki", format: "der" }).toString("base64");
const MOJANG_KEYS = { playerCertificateKeys: [spki(CERT_KEY)], profilePropertyKeys: [spki(PROPERTY_KEY)] };

/** Anmeldenachweis wie vom Launcher (Zertifikat, Signatur der Aufgabe, signiertes Profil). */
function proof(serverId, uuid, name, clock, overrides = {}) {
  const player = rsa();
  const publicKey = player.publicKey.export({ type: "spki", format: "der" });
  const expiresAt = "2099-01-01T00:00:00.123456Z";
  const keySignature = nodeCrypto.sign("sha1", certificatePayload(uuid, epochMillis(expiresAt), publicKey), CERT_KEY.privateKey).toString("base64");
  const signature = nodeCrypto.sign("sha256", challengeText(serverId, uuid), player.privateKey).toString("base64");
  const value = Buffer.from(JSON.stringify({ timestamp: clock, profileId: overrides.profileId || uuid, profileName: name, textures: { SKIN: { url: "http://textures.minecraft.net/texture/abc" } } })).toString("base64");
  const textures = { value, signature: nodeCrypto.sign("sha1", Buffer.from(value), PROPERTY_KEY.privateKey).toString("base64") };
  return { serverId, uuid, publicKey: publicKey.toString("base64"), keySignature, expiresAt, signature, textures, ...overrides.extra };
}

function setup(opts = {}) {
  let clock = 1_000_000_000_000;
  const accounts = { Alice: ALICE, Bob: BOB };
  const fakeFetch = async (url) => {
    const u = new URL(url);
    if (u.hostname === "textures.minecraft.net") return new Response(skinPng());
    if (u.hostname === "api.mojang.com") return new Response(null, { status: 404 });
    if (u.hostname === "playerdb.co") {
      if (u.pathname.toLowerCase().endsWith("/carol")) return Response.json({ data: { player: { username: "Carol", raw_id: "d".repeat(32), skin_texture: "http://textures.minecraft.net/texture/abc" } } });
      return Response.json({ code: "player.not_found" }, { status: 400 });
    }
    throw new Error(`unerwartet: ${url}`);
  };
  const api = createApi(openDb(":memory:"), { fetch: fakeFetch, now: () => clock, allowOffline: opts.allowOffline, mojangKeys: MOJANG_KEYS });
  const call = async (method, path, data, token) => {
    const res = await api(new Request(`http://x/proxy/8766${path}`, {
      method,
      headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: data ? JSON.stringify(data) : undefined,
    }));
    return { status: res.status, data: await res.json() };
  };
  const login = async (name) => {
    const { data } = await call("POST", "/api/auth/challenge");
    const res = await call("POST", "/api/auth/login", proof(data.serverId, accounts[name], name, clock));
    assert.equal(res.status, 200, JSON.stringify(res.data));
    return res.data.token;
  };
  return { call, login, tick: (ms) => { clock += ms; }, clock: () => clock };
}

test("Kopf aus Skin: Gesicht mit Hut-Ebene", async () => {
  const head = await headFromPng(skinPng());
  assert.equal(head.length, 64 * 8);
  assert.equal(head.slice(0, 8), "FF0000FF"); // Hut-Pixel blau
  assert.equal(head.slice(8, 16), "FFC80A0A"); // Gesicht rot
});

test("Anmeldung nur mit bestätigtem Minecraft-Konto", async () => {
  const s = setup();
  let { data } = await s.call("POST", "/api/auth/challenge");
  // fremdes Profil (gehört Bob) mit Alices Zertifikat
  let bad = await s.call("POST", "/api/auth/login", proof(data.serverId, ALICE, "Bob", s.clock(), { profileId: BOB }));
  assert.equal(bad.status, 401);
  // Zertifikat für eine andere UUID umgedeutet
  ({ data } = await s.call("POST", "/api/auth/challenge"));
  const p = proof(data.serverId, ALICE, "Alice", s.clock());
  bad = await s.call("POST", "/api/auth/login", { ...p, uuid: BOB });
  assert.equal(bad.status, 401);
  // Aufgabe nur einmal verwendbar
  bad = await s.call("POST", "/api/auth/login", p);
  assert.match(bad.data.error, /abgelaufen/);
  const token = await s.login("Alice");
  const me = await s.call("GET", "/api/friends", null, token);
  assert.equal(me.data.me.name, "Alice");
  assert.ok(me.data.me.head);
  assert.equal((await s.call("GET", "/api/friends", null, "f".repeat(64))).status, 401);
  assert.equal((await s.call("POST", "/api/auth/offline", { name: "Steve" })).status, 403);
});

test("Anfrage, annehmen, Status mit Server, entfernen", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  assert.equal((await s.call("POST", "/api/friends/request", { name: "Nobody" }, a)).status, 404);
  // Spieler ohne Mytic Client: Anfrage wird vorgemerkt
  const carol = await s.call("POST", "/api/friends/request", { name: "carol" }, a);
  assert.equal(carol.status, 200);
  assert.match(carol.data.message, /sobald/);
  assert.equal(carol.data.outgoing[0].name, "Carol");
  assert.ok(carol.data.outgoing[0].head);
  await s.call("POST", "/api/friends/remove", { uuid: "d".repeat(32) }, a);
  assert.equal((await s.call("POST", "/api/friends/request", { name: "alice" }, a)).status, 400);
  const sent = await s.call("POST", "/api/friends/request", { name: "bob" }, a);
  assert.equal(sent.data.outgoing[0].name, "Bob");
  const bobView = await s.call("GET", "/api/friends", null, b);
  assert.equal(bobView.data.incoming[0].uuid, ALICE);
  const accepted = await s.call("POST", "/api/friends/accept", { uuid: ALICE }, b);
  assert.equal(accepted.data.friends[0].name, "Alice");
  assert.equal(accepted.data.incoming.length, 0);

  await s.call("POST", "/api/presence", { place: "game", server: "play.hypixel.net", version: "1.21.11" }, a);
  let list = (await s.call("POST", "/api/presence", { place: "launcher" }, b)).data.friends;
  assert.deepEqual([list[0].online, list[0].place, list[0].server, list[0].version], [true, "game", "play.hypixel.net", "1.21.11"]);
  // Server verbergen
  await s.call("POST", "/api/presence", { place: "game", server: "geheim.de", hideServer: true }, a);
  list = (await s.call("GET", "/api/friends", null, b)).data.friends;
  assert.equal(list[0].server, null);
  // nach 75 s ohne Lebenszeichen offline, "zuletzt online" bleibt
  s.tick(ONLINE_MS + 1000);
  list = (await s.call("GET", "/api/friends", null, b)).data.friends;
  assert.equal(list[0].online, false);
  assert.ok(list[0].lastSeen > 0);

  const removed = await s.call("POST", "/api/friends/remove", { uuid: BOB }, a);
  assert.equal(removed.data.friends.length, 0);
});

test("Gegenseitige Anfrage macht sofort befreundet", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  await s.call("POST", "/api/friends/request", { name: "Bob" }, a);
  const res = await s.call("POST", "/api/friends/request", { name: "Alice" }, b);
  assert.equal(res.data.friends.length, 1);
  assert.match(res.data.message, /befreundet/);
});

test("Offline-Konten nur wenn erlaubt", async () => {
  const s = setup({ allowOffline: true });
  const res = await s.call("POST", "/api/auth/offline", { name: "Steve" });
  assert.equal(res.status, 200);
  assert.equal(res.data.me.uuid, await offlineUuid("Steve"));
  assert.equal(res.data.me.uuid, "5627dd98e6be3c21b8a8e92344183641");
});

test("Cosmetics: wählen und für andere abfragen", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  assert.equal((await s.call("POST", "/api/cosmetics", { cape: "galaxie" }, a)).data.cape, "galaxie");
  assert.equal((await s.call("POST", "/api/cosmetics", { cape: "../böse" }, b)).data.cape, null);
  const res = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE, BOB, "kaputt"] }, b);
  assert.deepEqual(res.data.capes, { [ALICE]: "galaxie" });
  assert.equal((await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] })).status, 401);
});

test("Cosmetics: MiniMe wählen, Umhang bleibt; alte Mods ohne Felder löschen nur den Umhang", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  await s.call("POST", "/api/cosmetics", { cape: "inferno" }, a);
  let res = await s.call("POST", "/api/cosmetics", { minime: "Technoblade" }, a);
  assert.deepEqual(res.data, { cape: "inferno", minime: "Technoblade" });
  res = await s.call("POST", "/api/cosmetics", { cape: null, minime: "x y" }, a);
  assert.deepEqual(res.data, { cape: null, minime: null });
  await s.call("POST", "/api/cosmetics", { cape: "galaxie", minime: "Dream" }, a);
  res = await s.call("POST", "/api/cosmetics", {}, a);
  assert.deepEqual(res.data, { cape: null, minime: "Dream" });
  const look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data, { capes: {}, minimes: { [ALICE]: "Dream" } });
});
