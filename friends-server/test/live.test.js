import { test } from "node:test";
import assert from "node:assert/strict";
import { EmoteHub, MemorySockets, EMOTE_CATALOG, EMOTE_GAP_MS, LIVE_PER_USER, LIVE_FLOOD_MAX } from "../src/live.js";
import { joinable, shownServer, parseAddress } from "../src/join.js";

/** Verbindung zum Mitlesen */
function sock() {
  return { out: [], closed: false, code: null, send(t) { this.out.push(t === "pong" ? t : JSON.parse(t)); }, close(code) { this.closed = true; this.code = code; } };
}

function hub(extra = {}) {
  let clock = 1_000_000;
  const sockets = new MemorySockets();
  const h = new EmoteHub({ sockets, now: () => clock, ...extra });
  return { h, sockets, tick: (ms) => { clock += ms; } };
}

const HASH_A = "a1".repeat(16), HASH_B = "b2".repeat(16);
const ip = (...parts) => parts.join(".");

test("Emote-Katalog: acht freie Emotes mit eindeutigen IDs", () => {
  assert.equal(EMOTE_CATALOG.length, 7);
  assert.equal(new Set(EMOTE_CATALOG.map((e) => e.id)).size, 7);
  assert.ok(EMOTE_CATALOG.every((e) => e.free && /^[a-z]+$/.test(e.id) && e.ms >= 1000 && e.ms <= 4000));
});

test("Emotes nur an andere auf demselben Server, mit Serverzeit", async () => {
  const { h } = hub();
  const a = sock(), b = sock(), c = sock(), a2 = sock();
  await h.opened(a, { uuid: "u1", name: "Alice" });
  await h.opened(b, { uuid: "u2", name: "Bob" });
  await h.opened(c, { uuid: "u3", name: "Carl" });
  await h.opened(a2, { uuid: "u1", name: "Alice" }); // zweites Spiel von Alice
  assert.equal(a.out[0].t, "hi");
  for (const [s, hash] of [[a, HASH_A], [b, HASH_A], [c, HASH_B], [a2, HASH_A]]) await h.message(s, JSON.stringify({ t: "srv", s: hash }));
  await h.message(a, JSON.stringify({ t: "emote", e: "winken" }));
  assert.deepEqual(b.out.at(-1), { t: "emote", u: "u1", n: "Alice", e: "winken", at: 1_000_000 });
  assert.equal(c.out.length, 1, "anderer Server bekommt nichts");
  assert.equal(a2.out.length, 1, "eigenes Konto bekommt nichts");
});

test("Emotes: Bremse 1 je 3 s, unbekannte Emotes und ohne Server werden ignoriert", async () => {
  const { h, tick } = hub();
  const a = sock(), b = sock();
  await h.opened(a, { uuid: "u1", name: "A" });
  await h.opened(b, { uuid: "u2", name: "B" });
  await h.message(a, JSON.stringify({ t: "emote", e: "winken" })); // noch kein Server
  await h.message(b, JSON.stringify({ t: "srv", s: HASH_A }));
  assert.equal(b.out.length, 1);
  await h.message(a, JSON.stringify({ t: "srv", s: "kein-hash" }));
  await h.message(a, JSON.stringify({ t: "emote", e: "winken" }));
  assert.equal(b.out.length, 1, "ungültiger Hash = kein Server");
  await h.message(a, JSON.stringify({ t: "srv", s: HASH_A }));
  await h.message(a, JSON.stringify({ t: "emote", e: "gibtsnicht" }));
  await h.message(a, "kaputt{");
  await h.message(a, null);
  await h.message(a, JSON.stringify({ t: "emote", e: "x".repeat(300) }));
  assert.equal(b.out.length, 1);
  await h.message(a, JSON.stringify({ t: "emote", e: "tanzen" }));
  assert.equal(b.out.length, 2);
  tick(EMOTE_GAP_MS - 1);
  await h.message(a, JSON.stringify({ t: "emote", e: "jubeln" }));
  assert.equal(b.out.length, 2);
  assert.deepEqual(a.out.at(-1), { t: "wait", ms: 1 });
  tick(1);
  await h.message(a, JSON.stringify({ t: "emote", e: "jubeln" }));
  assert.equal(b.out.at(-1).e, "jubeln");
  // Abbruch weitergeben, aber höchstens alle 0,5 s
  await h.message(a, JSON.stringify({ t: "stop" }));
  await h.message(a, JSON.stringify({ t: "stop" }));
  assert.equal(b.out.filter((m) => m.t === "stop").length, 1);
});

test("Emotes: höchstens zwei Verbindungen je Konto, Flutschutz, ping", async () => {
  const { h, sockets, tick } = hub();
  const list = [sock(), sock(), sock()];
  for (const s of list) {
    await h.opened(s, { uuid: "u1", name: "A" });
    tick(1);
  }
  assert.equal(LIVE_PER_USER, 2);
  assert.equal(list[0].closed, true, "älteste Verbindung geschlossen");
  assert.equal(sockets.all().length, 2);
  const s = list[2];
  await h.message(s, "ping");
  assert.equal(s.out.at(-1), "pong");
  for (let i = 0; i < LIVE_FLOOD_MAX; i++) await h.message(s, JSON.stringify({ t: "srv", s: HASH_A }));
  assert.equal(s.closed, true);
  assert.equal(s.code, 1008);
});

test("Emotes: freie Emotes ohne Abfrage der Freischaltungen", async () => {
  const asked = [];
  const { h } = hub({ ownsEmote: async (uuid, id) => (asked.push(id), false) });
  const a = sock(), b = sock();
  await h.opened(a, { uuid: "u1", name: "A" });
  await h.opened(b, { uuid: "u2", name: "B" });
  assert.equal(a.out[0].emotes.length, 7);
  await h.message(a, JSON.stringify({ t: "srv", s: HASH_A }));
  await h.message(b, JSON.stringify({ t: "srv", s: HASH_A }));
  await h.message(a, JSON.stringify({ t: "emote", e: "facepalm" }));
  assert.equal(b.out.at(-1).e, "facepalm");
  assert.deepEqual(asked, [], "freie Emotes brauchen keine Abfrage");
});

test("Beitreten: öffentliche Adressen ja, private/LAN/Realms nein", () => {
  for (const ok of ["play.hypixel.net", "mc.example.de:25570", ip(1, 2, 3, 4), ip(8, 8, 8, 8) + ":25565", "[2001:db8::1]:25565", "Play.Example.NET", ip(172, 32, 0, 1)]) {
    assert.equal(joinable(ok), true, ok);
  }
  for (const no of ["Einzelspieler", "Realms", "LAN-Welt", "Privater Server", "localhost", "localhost:25565", ip(127, 0, 0, 1), ip(10, 0, 0, 5),
    ip(192, 168, 0, 10) + ":25565", ip(172, 16, 4, 4), ip(172, 31, 255, 1), ip(169, 254, 1, 1), ip(100, 64, 0, 1), ip(0, 0, 0, 0), "[::1]:25565",
    "fe80::1", "fd00::5", "server", "mc.local", "nas.home.arpa", "a b.de", "x.de:99999", "x.de:", "user@x.de", "", null, undefined, 5]) {
    assert.equal(joinable(no), false, String(no));
  }
  assert.deepEqual(parseAddress("X.de:1234"), { host: "x.de", port: 1234 });
  assert.equal(shownServer(ip(192, 168, 1, 2)), "Privater Server");
  assert.equal(shownServer("play.x.net"), "play.x.net");
  assert.equal(shownServer("Einzelspieler"), "Einzelspieler");
  assert.equal(shownServer(null), null);
});
