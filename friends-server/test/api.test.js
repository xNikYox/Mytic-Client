import { test } from "node:test";
import assert from "node:assert/strict";
import zlib from "node:zlib";
import nodeCrypto from "node:crypto";
import { certificatePayload, challengeText, epochMillis } from "../src/mojang.js";
import { openDb, clientIp } from "../src/node-server.js";
import { createApi, offlineUuid, ONLINE_MS, RateLimiter } from "../src/api.js";
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
  const fetched = [];
  const fakeFetch = async (url) => {
    const u = new URL(url);
    fetched.push(u.hostname);
    if (u.hostname === "textures.minecraft.net") return new Response(skinPng());
    if (u.hostname === "api.mojang.com") return new Response(null, { status: 404 });
    if (u.hostname === "playerdb.co") {
      if (u.pathname.toLowerCase().endsWith("/carol")) return Response.json({ data: { player: { username: "Carol", raw_id: "d".repeat(32), skin_texture: "http://textures.minecraft.net/texture/abc" } } });
      return Response.json({ code: "player.not_found" }, { status: 400 });
    }
    throw new Error(`unerwartet: ${url}`);
  };
  const db = openDb(":memory:");
  const api = createApi(db, { fetch: fakeFetch, now: () => clock, allowOffline: opts.allowOffline, mojangKeys: MOJANG_KEYS, adminKey: opts.adminKey || ADMIN_KEY,
    limits: opts.limits, shotsPerDay: opts.shotsPerDay, shotsTotal: opts.shotsTotal });
  const call = async (method, path, data, token, adminKey, ip) => {
    const res = await api(new Request(`http://x/proxy/8766${path}`, {
      method,
      headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}), ...(adminKey ? { "x-admin-key": adminKey } : {}) },
      body: data ? JSON.stringify(data) : undefined,
    }), ip ? { ip } : undefined);
    return { status: res.status, data: await res.json(), retryAfter: res.headers.get("retry-after") };
  };
  const login = async (name) => {
    const { data } = await call("POST", "/api/auth/challenge");
    const res = await call("POST", "/api/auth/login", proof(data.serverId, accounts[name], name, clock));
    assert.equal(res.status, 200, JSON.stringify(res.data));
    return res.data.token;
  };
  /** Freischaltungen setzen (mit dem Admin-Passwort) */
  const grant = async (uuid, items) => {
    const res = await call("POST", "/api/admin/grants", { uuid, items }, null, ADMIN_KEY);
    assert.equal(res.status, 200, JSON.stringify(res.data));
    return res.data;
  };
  return { call, login, grant, api, db, fetched, tick: (ms) => { clock += ms; }, clock: () => clock };
}

const ADMIN_KEY = "test-admin-key-1234567890";

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

test("Lebenszeichen der Mod: Spielzeit, Einladungen, Freischaltungen und versteckter Status in einer Anfrage", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  await s.call("POST", "/api/friends/request", { name: "bob" }, a);
  await s.call("POST", "/api/friends/accept", { uuid: ALICE }, b);
  // Launcher: nur Freundesliste (mit caps), keine Extras
  let res = await s.call("POST", "/api/presence", { place: "launcher" }, a);
  assert.deepEqual(res.data.caps, ["presence2", "sync1", "cos1", "join1", "emote1"]);
  assert.equal(res.data.games, undefined);
  assert.equal(res.data.owned, undefined);
  // Mod: Spielzeit mitschicken, Einladungen und Freischaltungen zurück
  await s.grant(ALICE, ["cape:galaxie"]);
  res = await s.call("POST", "/api/presence", { place: "game", server: "x.de", version: "1.21.11",
    play: { seconds: 60, server: "x.de", version: "1.21.11", session: "abc", tz: 0 } }, a);
  assert.equal(res.status, 200);
  assert.equal(res.data.played, true);
  assert.deepEqual(res.data.games, { invites: 0 });
  assert.deepEqual(res.data.owned, { items: ["cape:galaxie"], admin: false });
  assert.equal(res.data.me.server, "x.de");
  const stats = await s.call("GET", "/api/stats?tz=0", null, a);
  assert.equal(stats.data.totals.seconds, 60);
  // ungültige Spielzeit: Lebenszeichen gilt trotzdem
  s.tick(60 * 1000);
  res = await s.call("POST", "/api/presence", { place: "game", play: { seconds: 9999 } }, a);
  assert.equal(res.status, 200);
  assert.equal(res.data.played, false);
  // Einladung von Bob erscheint im nächsten Lebenszeichen
  assert.equal((await s.call("POST", "/api/games", { to: "Alice", time: 300 }, b)).status, 200);
  res = await s.call("POST", "/api/presence", { place: "game" }, a);
  assert.deepEqual(res.data.games, { invites: 1 });
  s.tick(61 * 1000);
  res = await s.call("POST", "/api/presence", { place: "game" }, a);
  assert.deepEqual(res.data.games, { invites: 0 });
  // versteckt: gilt nicht als online, Liste und Extras kommen trotzdem
  s.tick(ONLINE_MS + 1000);
  res = await s.call("POST", "/api/presence", { place: "game", hidden: true, server: "geheim.de" }, a);
  assert.equal(res.data.friends[0].name, "Bob");
  assert.ok(res.data.owned);
  const list = (await s.call("GET", "/api/friends", null, b)).data.friends;
  assert.equal(list[0].online, false);
  assert.equal(list[0].server, null);
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
  await s.grant(ALICE, ["*"]);
  const b = await s.login("Bob");
  assert.equal((await s.call("POST", "/api/cosmetics", { cape: "galaxie" }, a)).data.cape, "galaxie");
  assert.equal((await s.call("POST", "/api/cosmetics", { cape: "../böse" }, b)).data.cape, null);
  const res = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE, BOB, "kaputt"] }, b);
  assert.deepEqual(res.data.capes, { [ALICE]: "galaxie" });
  assert.deepEqual(res.data.wings, {});
  assert.equal((await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] })).status, 401);
});

test("Cosmetics: MiniMe wählen, Umhang bleibt; alte Mods ohne Felder löschen nur den Umhang", async () => {
  const s = setup();
  const a = await s.login("Alice");
  await s.grant(ALICE, ["*"]);
  const b = await s.login("Bob");
  await s.call("POST", "/api/cosmetics", { cape: "inferno" }, a);
  // Mods bis 2.18: ein MiniMe mit Position
  let res = await s.call("POST", "/api/cosmetics", { minime: "Technoblade" }, a);
  assert.deepEqual(res.data, { cape: "inferno", minime: "Technoblade", minimePos: null, minimes: { shoulder: "Technoblade" }, wings: null, shield: null, aura: null, nametag: null, acc: {}, owned: ["*"] });
  res = await s.call("POST", "/api/cosmetics", { cape: null, minime: "x y" }, a);
  assert.deepEqual(res.data, { cape: null, minime: null, minimePos: null, minimes: {}, wings: null, shield: null, aura: null, nametag: null, acc: {}, owned: ["*"] });
  await s.call("POST", "/api/cosmetics", { cape: "galaxie", minime: "Dream" }, a);
  res = await s.call("POST", "/api/cosmetics", {}, a);
  assert.deepEqual(res.data, { cape: null, minime: "Dream", minimePos: null, minimes: { shoulder: "Dream" }, wings: null, shield: null, aura: null, nametag: null, acc: {}, owned: ["*"] });
  res = await s.call("POST", "/api/cosmetics", { wings: "neon", minimePos: "head" }, a);
  assert.deepEqual(res.data, { cape: null, minime: "Dream", minimePos: "head", minimes: { head: "Dream" }, wings: "neon", shield: null, aura: null, nametag: null, acc: {}, owned: ["*"] });
  res = await s.call("POST", "/api/cosmetics", { minimePos: "bauch" }, a);
  assert.equal(res.data.minimePos, null);

  // ab 2.19: ein MiniMe je Stelle, ungültige Stellen und Namen fallen weg
  res = await s.call("POST", "/api/cosmetics", { minimes: { head: "Dream", leg: "Technoblade", bauch: "Steve", arm: "x y" } }, a);
  assert.deepEqual(res.data.minimes, { head: "Dream", leg: "Technoblade" });
  assert.equal(res.data.minime, "Dream");
  assert.equal(res.data.minimePos, "head");
  let look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data, {
    capes: {}, minimes: { [ALICE]: "Dream" }, minimePos: { [ALICE]: "head" },
    minimeSets: { [ALICE]: { head: "Dream", leg: "Technoblade" } }, wings: { [ALICE]: "neon" }, shields: {}, auras: {}, nametags: {}, acc: {},
  });
  res = await s.call("POST", "/api/cosmetics", { shield: "death" }, a);
  assert.equal(res.data.shield, "death");
  assert.equal(res.data.wings, "neon");
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data.shields, { [ALICE]: "death" });
  res = await s.call("POST", "/api/cosmetics", { aura: "funken" }, a);
  assert.equal(res.data.aura, "funken");
  assert.equal(res.data.shield, "death");
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data.auras, { [ALICE]: "funken" });
  res = await s.call("POST", "/api/cosmetics", { nametag: "&a&lPro\u0000 &rGamer§c" }, a);
  assert.equal(res.data.nametag, "&a&lPro &rGamer");
  assert.equal(res.data.aura, "funken");
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data.nametags, { [ALICE]: "&a&lPro &rGamer" });
  res = await s.call("POST", "/api/cosmetics", { nametag: "&a&l  " }, a);
  assert.equal(res.data.nametag, null);
  res = await s.call("POST", "/api/cosmetics", { minimes: {} }, a);
  assert.deepEqual(res.data.minimes, {});
  assert.equal(res.data.minime, null);
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data.minimeSets, {});
});

test("Cosmetics gesperrt: nur Freigeschaltetes, Admin über Passwort oder Admin-Konto", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  // ohne Freischaltung bleibt alles leer
  let res = await s.call("POST", "/api/cosmetics", { cape: "galaxie", wings: "rabe", nametag: "&aHi", minimes: { head: "Dream" } }, a);
  assert.equal(res.data.cape, null);
  assert.equal(res.data.wings, null);
  assert.equal(res.data.nametag, null);
  assert.deepEqual(res.data.minimes, {});
  // Admin ohne Passwort oder mit falschem: verboten
  assert.equal((await s.call("POST", "/api/admin/grants", { uuid: ALICE, items: ["*"] }, b)).status, 403);
  assert.equal((await s.call("GET", "/api/admin/users", null, null, "falsch-falsch-falsch")).status, 403);
  // einzelne Teile und ganze Arten
  await s.grant(ALICE, ["cape:galaxie", "wings:*", "nametag", "quatsch", "cape:BÖSE"]);
  res = await s.call("POST", "/api/cosmetics", { cape: "galaxie", wings: "rabe", shield: "death", nametag: "&aHi" }, a);
  assert.equal(res.data.cape, "galaxie");
  assert.equal(res.data.wings, "rabe");
  assert.equal(res.data.shield, null);
  assert.equal(res.data.nametag, "&aHi");
  assert.deepEqual(res.data.owned, ["cape:galaxie", "nametag", "wings:*"]);
  res = await s.call("POST", "/api/cosmetics", { cape: "totenkopf" }, a);
  assert.equal(res.data.cape, null);
  // andere sehen nur Freigeschaltetes; Entziehen löscht die Auswahl
  await s.call("POST", "/api/cosmetics", { cape: "galaxie" }, a);
  let look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.equal(look.data.capes[ALICE], "galaxie");
  await s.grant(ALICE, ["wings:*"]);
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.equal(look.data.capes[ALICE], undefined);
  assert.equal(look.data.nametags[ALICE], undefined);
  assert.equal(look.data.wings[ALICE], "rabe");
  // eigene Freischaltungen und Admin-Konto
  assert.deepEqual((await s.call("POST", "/api/cosmetics/owned", {}, b)).data, { items: [], admin: false });
  await s.grant(BOB, ["admin"]);
  assert.equal((await s.call("POST", "/api/cosmetics/owned", {}, b)).data.admin, true);
  res = await s.call("POST", "/api/admin/grants", { uuid: ALICE, items: ["aura:*"] }, b);
  assert.equal(res.status, 200);
  assert.deepEqual(res.data.items, ["aura:*"]);
  assert.equal((await s.call("POST", "/api/admin/grants", { uuid: BOB, items: [] }, b)).status, 400);
  const user = await s.call("POST", "/api/admin/user", { name: "alice" }, b);
  assert.equal(user.data.uuid, ALICE);
  const users = await s.call("GET", "/api/admin/users", null, b);
  assert.equal(users.data.users.length, 2);
  assert.equal((await s.call("GET", "/api/admin/catalog", null, b)).data.catalog.cape.length > 10, true);
  // Admin-Seite wird ausgeliefert
  const page = await s.api(new Request("http://x/proxy/8766/admin"));
  assert.equal(page.status, 200);
  assert.match(await page.text(), /Mytic Admin/);
});

test("Accessoires (Kopf & Hals): je Platz eins, nur Freigeschaltetes, Lookup und Entziehen", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  // ohne Freischaltung: nichts
  let res = await s.call("POST", "/api/cosmetics", { acc: { kopf: "weihnachtsmuetze", hals: "schal" } }, a);
  assert.deepEqual(res.data.acc, {});
  // einzelne Teile und ganze Art; unbekannte Plätze, kaputte IDs und Arrays fallen weg
  await s.grant(ALICE, ["acc:weihnachtsmuetze", "acc:schal", "acc:BÖSE", "acc:*x"]);
  assert.deepEqual((await s.call("POST", "/api/cosmetics/owned", {}, a)).data.items, ["acc:schal", "acc:weihnachtsmuetze"]);
  res = await s.call("POST", "/api/cosmetics", { acc: { kopf: "weihnachtsmuetze", hals: "schal", bauch: "schal", ruecken: "../x" } }, a);
  assert.deepEqual(res.data.acc, { kopf: "weihnachtsmuetze", hals: "schal" });
  // nicht freigeschaltetes Geweih gilt als „keins“, der Schal bleibt
  res = await s.call("POST", "/api/cosmetics", { acc: { kopf: "rentiergeweih", hals: "schal" } }, a);
  assert.deepEqual(res.data.acc, { hals: "schal" });
  res = await s.call("POST", "/api/cosmetics", { acc: ["schal"] }, a);
  assert.deepEqual(res.data.acc, {});
  // Felder ohne acc lassen die Accessoires stehen (z. B. ältere Mods)
  await s.call("POST", "/api/cosmetics", { acc: { kopf: "weihnachtsmuetze", hals: "schal" } }, a);
  res = await s.call("POST", "/api/cosmetics", { cape: null, wings: null }, a);
  assert.deepEqual(res.data.acc, { kopf: "weihnachtsmuetze", hals: "schal" });
  // „acc“ allein ändert den Umhang nicht
  await s.grant(ALICE, ["acc:*", "cape:galaxie"]);
  await s.call("POST", "/api/cosmetics", { cape: "galaxie" }, a);
  res = await s.call("POST", "/api/cosmetics", { acc: { kopf: "rentiergeweih_nase", hals: "schal" } }, a);
  assert.equal(res.data.cape, "galaxie");
  assert.deepEqual(res.data.acc, { kopf: "rentiergeweih_nase", hals: "schal" });
  // andere sehen sie
  let look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE, BOB] }, b);
  assert.deepEqual(look.data.acc, { [ALICE]: { kopf: "rentiergeweih_nase", hals: "schal" } });
  // nur noch der Schal freigeschaltet: das Geweih verschwindet sofort (dropUnowned) und im Lookup
  await s.grant(ALICE, ["acc:schal"]);
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data.acc[ALICE], { hals: "schal" });
  assert.equal(look.data.capes[ALICE], undefined);
  assert.equal((await s.db.get("SELECT acc FROM users WHERE uuid = ?", [ALICE])).acc, JSON.stringify({ hals: "schal" }));
  // alles entzogen: Spalte leer, Spieler fällt aus dem Lookup
  await s.grant(ALICE, []);
  assert.equal((await s.db.get("SELECT acc FROM users WHERE uuid = ?", [ALICE])).acc, null);
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data.acc, {});
  // „*“ schaltet auch Accessoires frei
  await s.grant(ALICE, ["*"]);
  res = await s.call("POST", "/api/cosmetics", { acc: { kopf: "rentiergeweih" } }, a);
  assert.deepEqual(res.data.acc, { kopf: "rentiergeweih" });
  // Katalog für die Admin-Seite enthält die Accessoires und das Set
  const cat = (await s.call("GET", "/api/admin/catalog", null, null, ADMIN_KEY)).data.catalog;
  assert.deepEqual(cat.acc.map((x) => x[0]), ["weihnachtsmuetze", "rentiergeweih", "rentiergeweih_nase", "schal",
    "elfenmuetze", "strickmuetze", "ohrenschuetzer", "heiligenschein", "mistelreif", "lamettaboa", "lichterschal"]);
  const known = new Set(["cape", "wings", "shield", "aura", "acc"].flatMap((k) => cat[k].map((x) => `${k}:${x[0]}`)));
  assert.deepEqual(cat.sets.map((x) => x.name), ["Winterzauber 2026", "Winterzauber II"]);
  const set = cat.sets.find((x) => x.name === "Winterzauber 2026");
  assert.equal(set.items.length, 17);
  for (const item of set.items) assert.ok(known.has(item), `${item} im Katalog`);
  // zweites Set: 6 Umhänge, 3 Flügel, 3 Schilde, 5 Auren, 7 Accessoires (Nametag-Vorlagen brauchen nur "nametag")
  const set2 = cat.sets.find((x) => x.name === "Winterzauber II");
  assert.equal(set2.items.length, 24);
  assert.equal(new Set(set2.items).size, 24);
  for (const item of set2.items) assert.ok(known.has(item), `${item} im Katalog`);
  for (const k of ["cape", "wings", "shield", "aura", "acc"]) assert.ok(set2.items.some((i) => i.startsWith(k + ":")), `${k} im Set`);
  assert.deepEqual(set2.items.filter((i) => set.items.includes(i)), []);
  // neue Accessoires lassen sich freischalten und tragen
  await s.grant(ALICE, set2.items);
  res = await s.call("POST", "/api/cosmetics", { acc: { kopf: "heiligenschein", hals: "lichterschal" }, aura: "sternschnuppen", cape: "schlittenflug" }, a);
  assert.deepEqual(res.data.acc, { kopf: "heiligenschein", hals: "lichterschal" });
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [ALICE] }, b);
  assert.deepEqual(look.data.acc[ALICE], { kopf: "heiligenschein", hals: "lichterschal" });
  assert.equal(look.data.capes[ALICE], "schlittenflug");
  // die Admin-Seite kennt die neue Art
  const page = await (await s.api(new Request("http://x/proxy/8766/admin"))).text();
  assert.match(page, /acc: "Kopf & Hals"/);
});

test("Datenbank von vor 2.58 (ohne users.acc) wird beim Start ergänzt, Daten bleiben", async () => {
  const fs = await import("node:fs");
  const os = await import("node:os");
  const path = await import("node:path");
  const { DatabaseSync } = await import("node:sqlite");
  const { SCHEMA } = await import("../src/schema.js");
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), "mytic-friends-")), "old.db");
  // alte Datenbank: Schema ohne acc und mit den Spalten der älteren Migrationen
  const old = new DatabaseSync(file);
  old.exec(SCHEMA);
  for (const col of ["cape", "minime", "wings", "minime_pos", "minime_set", "shield", "aura", "nametag"]) old.exec(`ALTER TABLE users ADD COLUMN ${col} TEXT`);
  old.exec("INSERT INTO users (uuid, name, name_lower, cape) VALUES ('" + ALICE + "', 'Alice', 'alice', 'galaxie')");
  old.close();
  for (let i = 0; i < 2; i++) { // zweimal öffnen: die Migration darf nicht stören, wenn die Spalte schon da ist
    const db = openDb(file);
    const cols = (await db.all("PRAGMA table_info(users)")).map((c) => c.name);
    assert.ok(cols.includes("acc"));
    const row = await db.get("SELECT cape, acc FROM users WHERE uuid = ?", [ALICE]);
    assert.equal(row.cape, "galaxie");
    assert.equal(row.acc, i === 0 ? null : JSON.stringify({ kopf: "schal" }));
    if (i === 0) await db.run("UPDATE users SET acc = ? WHERE uuid = ?", [JSON.stringify({ kopf: "schal" }), ALICE]);
    db.close();
  }
});

test("Cloud-Einstellungen: sichern und auf anderem PC holen", async () => {
  const s = setup();
  const a = await s.login("Alice");
  assert.equal((await s.call("GET", "/api/settings", null, a)).data.data, null);
  assert.equal((await s.call("GET", "/api/settings")).status, 401);
  const saved = await s.call("POST", "/api/settings", { data: { enabled: { zoom: true }, keys: { "key.myticclient.zoom": "key.keyboard.c" } } }, a);
  assert.equal(saved.status, 200);
  const again = await s.login("Alice"); // neue Anmeldung = anderer PC
  const got = await s.call("GET", "/api/settings", null, again);
  assert.equal(got.data.data.enabled.zoom, true);
  assert.equal(got.data.updated, saved.data.updated);
  assert.equal((await s.call("GET", "/api/settings", null, await s.login("Bob"))).data.data, null, "pro Konto");
  assert.equal((await s.call("POST", "/api/settings", { data: [1] }, a)).status, 400);
  assert.equal((await s.call("POST", "/api/settings", { data: { x: "y".repeat(300 * 1024) } }, a)).status, 413);
});

test("Screenshots: hochladen, öffentlicher Link, löschen nur eigene", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4, 0xff, 0xd9]).toString("base64");
  assert.equal((await s.call("POST", "/api/shots", { image: "iVBORw0KGgo=" }, a)).status, 400, "nur JPEG");
  assert.equal((await s.call("POST", "/api/shots", { image: jpeg })).status, 401);
  const up = await s.call("POST", "/api/shots", { image: jpeg }, a);
  assert.equal(up.status, 200);
  assert.match(up.data.url, /\/api\/shots\/[0-9a-f]{16}\.jpg$/);
  const img = await s.api(new Request(up.data.url));
  assert.equal(img.status, 200);
  assert.equal(img.headers.get("content-type"), "image/jpeg");
  assert.deepEqual(Buffer.from(await img.arrayBuffer()), Buffer.from(jpeg, "base64"));
  await s.call("POST", "/api/shots/delete", { id: up.data.id }, b);
  assert.equal((await s.api(new Request(up.data.url))).status, 200, "fremder Löschversuch ohne Wirkung");
  await s.call("POST", "/api/shots/delete", { id: up.data.id }, a);
  assert.equal((await s.api(new Request(up.data.url))).status, 404);
});

test("Bremse je Adresse: 429 mit Retry-After, Anmeldung strenger, Adressen getrennt", async () => {
  const s = setup({ limits: { all: 5, auth: 3 } });
  for (let i = 0; i < 3; i++) assert.equal((await s.call("POST", "/api/auth/challenge", null, null, null, "1.1.1.1")).status, 200);
  const blocked = await s.call("POST", "/api/auth/challenge", null, null, null, "1.1.1.1");
  assert.equal(blocked.status, 429);
  assert.ok(Number(blocked.retryAfter) > 0);
  // andere Adresse unabhängig; allgemeiner Topf für die erste Adresse noch frei
  assert.equal((await s.call("POST", "/api/auth/challenge", null, null, null, "2.2.2.2")).status, 200);
  assert.equal((await s.call("GET", "/api/health", null, null, null, "1.1.1.1")).status, 200);
  assert.equal((await s.call("GET", "/api/health", null, null, null, "1.1.1.1")).status, 429);
  s.tick(61 * 1000);
  assert.equal((await s.call("POST", "/api/auth/challenge", null, null, null, "1.1.1.1")).status, 200);
});

test("Bremse: begrenzter Speicher, älteste Adresse fällt weg", () => {
  const limiter = new RateLimiter(1, 60000, 3);
  assert.equal(limiter.hit("a"), 0);
  assert.ok(limiter.hit("a") > 0);
  for (const key of ["b", "c", "d"]) limiter.hit(key);
  assert.equal(limiter.hits.size, 3);
  assert.equal(limiter.hits.has("a"), false);
});

test("Anmelde-Aufgaben: höchstens 10 offene je Adresse, abgelaufene werden aufgeräumt", async () => {
  const s = setup({ limits: { auth: 1000, all: 1000 } });
  for (let i = 0; i < 10; i++) assert.equal((await s.call("POST", "/api/auth/challenge", null, null, null, "3.3.3.3")).status, 200);
  assert.equal((await s.call("POST", "/api/auth/challenge", null, null, null, "3.3.3.3")).status, 429);
  assert.equal((await s.call("POST", "/api/auth/challenge", null, null, null, "4.4.4.4")).status, 200);
  s.tick(2 * 60 * 1000 + 1);
  assert.equal((await s.call("POST", "/api/auth/challenge", null, null, null, "3.3.3.3")).status, 200);
  assert.equal((await s.db.get("SELECT COUNT(*) AS n FROM challenges")).n, 1);
});

test("Anfrage-Größe: 413 bei Content-Length und beim Lesen ohne Angabe", async () => {
  const s = setup();
  const big = await s.call("POST", "/api/auth/login", { serverId: "a".repeat(40), junk: "x".repeat(20 * 1024) });
  assert.equal(big.status, 413);
  // Datenstrom ohne Content-Length
  const stream = new ReadableStream({
    start(controller) {
      for (let i = 0; i < 20; i++) controller.enqueue(new TextEncoder().encode("x".repeat(1024)));
      controller.close();
    },
  });
  const res = await s.api(new Request("http://x/api/auth/login", { method: "POST", body: stream, duplex: "half" }));
  assert.equal(res.status, 413);
  // Screenshot darf größer sein
  const a = await s.login("Alice");
  const jpeg = "/9j/" + "A".repeat(100 * 1024);
  assert.equal((await s.call("POST", "/api/shots", { image: jpeg }, a)).status, 200);
});

test("Screenshots: Tagesgrenze je Konto (Löschen hilft nicht) und Gesamtgrenze", async () => {
  const s = setup({ shotsPerDay: 2, shotsTotal: 3 });
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4, 0xff, 0xd9]).toString("base64");
  const first = await s.call("POST", "/api/shots", { image: jpeg }, a);
  await s.call("POST", "/api/shots/delete", { id: first.data.id }, a);
  assert.equal((await s.call("POST", "/api/shots", { image: jpeg }, a)).status, 200);
  const over = await s.call("POST", "/api/shots", { image: jpeg }, a);
  assert.equal(over.status, 429);
  assert.ok(Number(over.retryAfter) > 0);
  assert.equal((await s.call("POST", "/api/shots", { image: jpeg }, b)).status, 200);
  assert.equal((await s.call("POST", "/api/shots", { image: jpeg }, b)).status, 200);
  // jetzt 3 gespeichert = voll
  s.tick(24 * 60 * 60 * 1000);
  assert.equal((await s.call("POST", "/api/shots", { image: jpeg }, a)).status, 507);
});

test("Admin-Passwort: mindestens 16 Zeichen, Fehlversuche sperren die Adresse", async () => {
  const short = setup({ adminKey: "kurz-15-zeichen" });
  assert.equal((await short.call("GET", "/api/admin/catalog", null, null, "kurz-15-zeichen")).status, 403);
  const s = setup();
  assert.equal((await s.call("GET", "/api/admin/catalog", null, null, ADMIN_KEY, "5.5.5.5")).status, 200);
  for (let i = 0; i < 5; i++) assert.equal((await s.call("GET", "/api/admin/catalog", null, null, "falsch-falsch-falsch", "5.5.5.5")).status, 403);
  // gesperrt – auch mit dem richtigen Passwort
  const locked = await s.call("GET", "/api/admin/catalog", null, null, ADMIN_KEY, "5.5.5.5");
  assert.equal(locked.status, 429);
  assert.ok(Number(locked.retryAfter) > 0);
  assert.equal((await s.call("GET", "/api/admin/catalog", null, null, ADMIN_KEY, "6.6.6.6")).status, 200);
  s.tick(61 * 1000);
  assert.equal((await s.call("GET", "/api/admin/catalog", null, null, ADMIN_KEY, "5.5.5.5")).status, 200);
});

test("Freundschaftsanfrage: eigene Grenze vor der Namenssuche", async () => {
  const s = setup();
  const a = await s.login("Alice");
  for (let i = 0; i < 50; i++) await s.db.run("INSERT INTO requests (sender, target, created) VALUES (?, ?, ?)", [ALICE, i.toString(16).padStart(32, "0"), s.clock()]);
  s.fetched.length = 0;
  assert.equal((await s.call("POST", "/api/friends/request", { name: "carol" }, a)).status, 429);
  assert.deepEqual(s.fetched, [], "kein Nachschlagen bei playerdb.co");
});

test("Node: X-Forwarded-For nur mit TRUST_PROXY", () => {
  const req = { headers: { "x-forwarded-for": "9.9.9.9, 7.7.7.7" }, socket: { remoteAddress: "127.0.0.1" } };
  assert.equal(clientIp(req, false), "127.0.0.1");
  assert.equal(clientIp(req, true), "7.7.7.7");
});

test("Cosmetics-Änderungen kommen mit dem Lebenszeichen (cos1), nur echte Änderungen zählen", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  // erstes Lebenszeichen: nur der Stand, keine Liste
  let beat = await s.call("POST", "/api/presence", { place: "game", cosSince: 0 }, b);
  assert.ok(beat.data.caps.includes("cos1"));
  assert.deepEqual(beat.data.cos.changed, []);
  let since = beat.data.cos.since;
  // ohne cosSince (ältere Mods, Launcher) kein Feld
  assert.equal((await s.call("POST", "/api/presence", { place: "game" }, b)).data.cos, undefined);
  s.tick(5000);
  // Freischaltung (Admin) und Auswahl melden die Änderung
  await s.grant(ALICE, ["acc:weihnachtsmuetze"]);
  s.tick(5000);
  beat = await s.call("POST", "/api/presence", { place: "game", cosSince: since }, b);
  assert.deepEqual(beat.data.cos.changed, [ALICE]);
  since = beat.data.cos.since;
  s.tick(5000);
  await s.call("POST", "/api/cosmetics", { cape: null, acc: { kopf: "weihnachtsmuetze" } }, a);
  s.tick(5000);
  beat = await s.call("POST", "/api/presence", { place: "game", cosSince: since }, b);
  assert.deepEqual(beat.data.cos.changed, [ALICE]);
  since = beat.data.cos.since;
  // dieselbe Auswahl noch einmal (Spielstart): keine Änderung
  s.tick(5000);
  await s.call("POST", "/api/cosmetics", { cape: null, acc: { kopf: "weihnachtsmuetze" } }, a);
  s.tick(5000);
  beat = await s.call("POST", "/api/presence", { place: "game", cosSince: since }, b);
  assert.deepEqual(beat.data.cos.changed, []);
  // viel zu alter oder kaputter Stand: neu beginnen statt alles zu lesen
  beat = await s.call("POST", "/api/presence", { place: "game", cosSince: 1 }, b);
  assert.deepEqual(beat.data.cos.changed, []);
  assert.ok(beat.data.cos.since > s.clock() - 2000);
  beat = await s.call("POST", "/api/presence", { place: "game", cosSince: "x" }, b);
  assert.deepEqual(beat.data.cos.changed, []);
  // Launcher bekommt nichts davon
  assert.equal((await s.call("POST", "/api/presence", { place: "launcher", cosSince: since }, b)).data.cos, undefined);
});

test("Cosmetics-Lookup: Offline-UUID (Server im Offline-Modus) über den Namen zum Konto", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  await s.grant(ALICE, ["acc:*", "cape:galaxie"]);
  await s.call("POST", "/api/cosmetics", { cape: "galaxie", acc: { kopf: "weihnachtsmuetze" } }, a);
  const off = await offlineUuid("Alice");
  // ohne Namen: unbekannt
  let look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [off] }, b);
  assert.deepEqual(look.data.acc, {});
  // mit Namen: unter der angefragten UUID
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [off, ALICE], names: { [off]: "Alice" } }, b);
  assert.deepEqual(look.data.acc, { [off]: { kopf: "weihnachtsmuetze" }, [ALICE]: { kopf: "weihnachtsmuetze" } });
  assert.deepEqual(look.data.capes, { [off]: "galaxie", [ALICE]: "galaxie" });
  assert.deepEqual(look.data.aliases, { [off]: ALICE });
  // Name passt nicht zur Offline-UUID oder echte UUID mit Namen: nichts umgeleitet
  look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [off, BOB], names: { [off]: "Bob", [BOB]: "Alice" } }, b);
  assert.deepEqual(look.data.acc, {});
});

test("Freunden beitreten: join nur für öffentliche Adressen, private Adressen bleiben privat", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  await s.call("POST", "/api/friends/request", { name: "bob" }, a);
  await s.call("POST", "/api/friends/accept", { uuid: ALICE }, b);
  const seen = async (data) => {
    await s.call("POST", "/api/presence", { place: "game", version: "1.21.11", ...data }, a);
    return (await s.call("GET", "/api/friends", null, b)).data.friends[0];
  };
  let f = await seen({ server: "play.example.net:25570" });
  assert.deepEqual([f.server, f.join], ["play.example.net:25570", true]);
  f = await seen({ server: [192, 168, 1, 20].join(".") });
  assert.deepEqual([f.server, f.join], ["Privater Server", false]);
  f = await seen({ server: "localhost:25565" });
  assert.deepEqual([f.server, f.join], ["Privater Server", false]);
  f = await seen({ server: "Einzelspieler" });
  assert.deepEqual([f.server, f.join], ["Einzelspieler", false]);
  f = await seen({ server: "Realms" });
  assert.deepEqual([f.server, f.join], ["Realms", false]);
  f = await seen({ server: "play.example.net", hideServer: true });
  assert.deepEqual([f.server, f.join], [null, false]);
  // im Launcher oder offline: nie beitretbar
  await s.call("POST", "/api/presence", { place: "launcher" }, a);
  f = (await s.call("GET", "/api/friends", null, b)).data.friends[0];
  assert.equal(f.join, false);
  await seen({ server: "play.example.net" });
  s.tick(ONLINE_MS + 1000);
  f = (await s.call("GET", "/api/friends", null, b)).data.friends[0];
  assert.deepEqual([f.online, f.server, f.join], [false, null, false]);
});

test("Emotes in Echtzeit über GET /api/live (WebSocket, Node)", async () => {
  const s = setup();
  const a = await s.login("Alice");
  const b = await s.login("Bob");
  const { createServer } = await import("../src/node-server.js");
  const server = createServer(s.api);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const url = `ws://127.0.0.1:${server.address().port}/api/live`;
  const open = (token) => new Promise((resolve, reject) => {
    const ws = new WebSocket(url, { headers: token ? { authorization: `Bearer ${token}` } : {} });
    const inbox = [];
    const waiters = [];
    ws.onmessage = (e) => {
      const msg = e.data === "pong" ? "pong" : JSON.parse(e.data);
      const w = waiters.shift();
      if (w) w(msg);
      else inbox.push(msg);
    };
    ws.next = (ms = 2000) => (inbox.length ? Promise.resolve(inbox.shift()) : new Promise((res, rej) => {
      const timer = setTimeout(() => {
        waiters.splice(waiters.indexOf(done), 1);
        rej(new Error("keine Nachricht"));
      }, ms);
      const done = (m) => {
        clearTimeout(timer);
        res(m);
      };
      waiters.push(done);
    }));
    ws.json = (data) => ws.send(typeof data === "string" ? data : JSON.stringify(data));
    ws.onopen = () => resolve(ws);
    ws.onerror = () => reject(new Error("abgelehnt"));
  });
  try {
    await assert.rejects(open(null), /abgelehnt/); // ohne Anmeldung keine Verbindung
    const wa = await open(a);
    const wb = await open(b);
    const hi = await wa.next();
    assert.equal(hi.t, "hi");
    assert.equal(hi.emotes.length, 7);
    assert.equal(hi.gap, 3000);
    await wb.next();
    const hash = "ab".repeat(16);
    wa.json({ t: "srv", s: hash });
    wb.json({ t: "srv", s: hash });
    wa.json("ping");
    assert.equal(await wa.next(), "pong");
    wb.json("ping");
    assert.equal(await wb.next(), "pong");
    wa.json({ t: "emote", e: "winken" });
    const got = await wb.next();
    assert.deepEqual([got.t, got.u, got.n, got.e, typeof got.at], ["emote", ALICE, "Alice", "winken", "number"]);
    // zu schnell: Hinweis statt Emote
    wa.json({ t: "emote", e: "jubeln" });
    assert.equal((await wa.next()).t, "wait");
    wa.json({ t: "stop" });
    assert.deepEqual(await wb.next(), { t: "stop", u: ALICE });
    // anderer Server: nichts mehr
    wb.json({ t: "srv", s: "cd".repeat(16) });
    wb.json("ping");
    assert.equal(await wb.next(), "pong"); // srv ist verarbeitet
    s.tick(3000);
    wa.json({ t: "emote", e: "tanzen" });
    await assert.rejects(wb.next(300), /keine Nachricht/);
    wa.close();
    wb.close();
  } finally {
    server.close();
    server.closeAllConnections?.();
  }
});
