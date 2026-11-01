import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../src/node-server.js";
import { createApi } from "../src/api.js";

const DAY = 24 * 60 * 60 * 1000;

async function setup(names = ["Alice", "Bob", "Carol"]) {
  // 2001-09-09 01:46:40 UTC
  let clock = 1_000_000_000_000;
  const fakeFetch = async () => new Response(null, { status: 404 });
  const api = createApi(openDb(":memory:"), { fetch: fakeFetch, now: () => clock, allowOffline: true });
  const call = async (method, path, data, token) => {
    const res = await api(new Request(`http://x${path}`, {
      method,
      headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: data ? JSON.stringify(data) : undefined,
    }));
    return { status: res.status, data: await res.json() };
  };
  const users = {};
  for (const name of names) {
    const res = await call("POST", "/api/auth/offline", { name });
    assert.equal(res.status, 200);
    users[name] = { token: res.data.token, uuid: res.data.me.uuid };
  }
  const t = (name) => users[name].token;
  const launch = (name, data) => call("POST", "/api/stats/launch", data, t(name));
  const play = (name, data) => call("POST", "/api/stats/play", data, t(name));
  const stats = async (name, tz = 0) => {
    const res = await call("GET", `/api/stats?tz=${tz}`, null, t(name));
    assert.equal(res.status, 200, JSON.stringify(res.data));
    return res.data;
  };
  return { call, users, t, launch, play, stats, tick: (ms) => { clock += ms; }, now: () => clock };
}

test("Statistik: Anmeldung erforderlich", async () => {
  const s = await setup();
  assert.equal((await s.call("GET", "/api/stats")).status, 401);
  assert.equal((await s.call("POST", "/api/stats/launch", { version: "1.21" })).status, 401);
  assert.equal((await s.call("POST", "/api/stats/play", { seconds: 60 })).status, 401);
  assert.equal((await s.call("GET", "/api/stats", null, "0".repeat(64))).status, 401);
});

test("Statistik: leer für neue Spieler", async () => {
  const s = await setup();
  const data = await s.stats("Alice");
  assert.deepEqual(data.totals, { launches: 0, seconds: 0, longest: 0, first: 0, last: 0, today: 0, week: 0 });
  assert.equal(data.days.length, 30);
  assert.ok(data.days.every((d) => d.seconds === 0 && d.launches === 0));
  assert.deepEqual(data.servers, []);
  assert.deepEqual(data.versions, []);
  assert.deepEqual(data.chess, { played: 0, wins: 0, losses: 0, draws: 0, opponents: [] });
});

test("Statistik: Starts zählen", async () => {
  const s = await setup();
  const t0 = s.now();
  assert.deepEqual((await s.launch("Alice", { version: "1.21.4", profile: "PvP", tz: 120 })).data, { ok: true });
  s.tick(60 * 1000);
  await s.launch("Alice", { version: "1.21.4", profile: "Survival", tz: 120 });
  s.tick(60 * 1000);
  await s.launch("Alice", { version: " 1.20.1\u0000\n", tz: 120 });
  const data = await s.stats("Alice", 120);
  assert.equal(data.totals.launches, 3);
  assert.equal(data.totals.seconds, 0);
  assert.equal(data.totals.first, t0);
  assert.equal(data.totals.last, t0 + 120 * 1000);
  assert.equal(data.days.at(-1).day, "2001-09-09");
  assert.equal(data.days.at(-1).launches, 3);
  assert.deepEqual(data.versions.map((v) => [v.name, v.launches]).sort(), [["1.20.1", 1], ["1.21.4", 2]]);
  assert.deepEqual(data.profiles.map((p) => [p.name, p.launches]).sort(), [["PvP", 1], ["Survival", 1]]);
  // andere Spieler sehen nichts davon
  assert.equal((await s.stats("Bob")).totals.launches, 0);
});

test("Statistik: Spielzeit je Tag, Server, Version und Profil", async () => {
  const s = await setup();
  await s.launch("Alice", { version: "1.21.4", profile: "PvP", tz: 0 });
  const report = (seconds, server, version = "1.21.4", profile = "PvP") => s.play("Alice", { seconds, server, version, profile, session: "abc123", tz: 0 });
  s.tick(60 * 1000);
  assert.deepEqual((await report(60, "hypixel.net")).data, { ok: true });
  s.tick(60 * 1000);
  await report(60, "hypixel.net");
  s.tick(60 * 1000);
  await report(30, "");
  s.tick(60 * 1000);
  await report(45, "  play.example.org\u0007 ", "1.20.1", "Survival");
  const data = await s.stats("Alice");
  assert.equal(data.totals.launches, 1);
  assert.equal(data.totals.seconds, 195);
  assert.equal(data.totals.today, 195);
  assert.equal(data.totals.week, 195);
  assert.equal(data.totals.last, s.now());
  assert.deepEqual(data.days.at(-1), { day: "2001-09-09", seconds: 195, launches: 1 });
  assert.deepEqual(data.servers.map((x) => [x.name, x.seconds]), [["hypixel.net", 120], ["play.example.org", 45], ["Einzelspieler", 30]]);
  assert.deepEqual(data.versions.map((x) => [x.name, x.seconds, x.launches]), [["1.21.4", 150, 1], ["1.20.1", 45, 0]]);
  assert.deepEqual(data.profiles.map((x) => [x.name, x.seconds, x.launches]), [["PvP", 150, 1], ["Survival", 45, 0]]);
});

test("Statistik: Tagesgrenze nach Zeitzone", async () => {
  const s = await setup();
  // 01:46 UTC: in UTC−2 noch der 8., in UTC der 9. September
  await s.play("Alice", { seconds: 60, tz: -120 });
  s.tick(60 * 1000);
  await s.play("Alice", { seconds: 40, tz: 0 });
  s.tick(60 * 1000);
  await s.launch("Alice", { tz: -9999 }); // begrenzt auf −840 → ebenfalls der 8.
  const utc = await s.stats("Alice", 0);
  assert.deepEqual(utc.days.slice(-2), [{ day: "2001-09-08", seconds: 60, launches: 1 }, { day: "2001-09-09", seconds: 40, launches: 0 }]);
  assert.equal(utc.totals.today, 40);
  assert.equal(utc.totals.week, 100);
  // aus Sicht von UTC−2 ist heute noch der 8.
  const west = await s.stats("Alice", -120);
  assert.equal(west.days.at(-1).day, "2001-09-08");
  assert.equal(west.totals.today, 60);
  // Unsinn als Zeitzone → UTC
  assert.equal((await s.call("GET", "/api/stats?tz=abc", null, s.t("Alice"))).data.days.at(-1).day, "2001-09-09");
});

test("Statistik: längste Sitzung", async () => {
  const s = await setup();
  for (let i = 0; i < 3; i++) {
    s.tick(60 * 1000);
    await s.play("Alice", { seconds: 60, session: "a1b2c3", tz: 0 });
  }
  for (let i = 0; i < 2; i++) {
    s.tick(60 * 1000);
    await s.play("Alice", { seconds: 60, session: "SecondSession9", tz: 0 });
  }
  let data = await s.stats("Alice");
  assert.equal(data.totals.seconds, 300);
  assert.equal(data.totals.longest, 180);
  // ohne Sitzung zählt die Zeit, aber nicht für die längste Sitzung
  s.tick(60 * 1000);
  await s.play("Alice", { seconds: 60, tz: 0 });
  data = await s.stats("Alice");
  assert.equal(data.totals.seconds, 360);
  assert.equal(data.totals.longest, 180);
  // ungültige Sitzungskennung
  assert.equal((await s.play("Alice", { seconds: 60, session: "a-b" })).status, 400);
  assert.equal((await s.play("Alice", { seconds: 60, session: "x".repeat(33) })).status, 400);
  // alte Sitzungen werden aufgeräumt, die längste bleibt
  s.tick(3 * DAY);
  await s.play("Alice", { seconds: 10, session: "a1b2c3", tz: 0 });
  data = await s.stats("Alice");
  assert.equal(data.totals.longest, 180);
});

test("Statistik: Sekunden prüfen", async () => {
  const s = await setup();
  for (const seconds of [0, 121, -5, 1.5, "60", null, undefined]) {
    const res = await s.play("Alice", { seconds, tz: 0 });
    assert.equal(res.status, 400, `seconds=${seconds}`);
    assert.ok(res.data.error);
  }
  assert.equal((await s.play("Alice", { seconds: 120 })).status, 200);
  s.tick(60 * 1000);
  assert.equal((await s.play("Alice", { seconds: 1 })).status, 200);
  assert.equal((await s.stats("Alice")).totals.seconds, 121);
});

test("Statistik: zu schnelle Meldungen werden gekürzt", async () => {
  const s = await setup(["Alice", "Bob"]);
  // höchstens (Zeit seit letzter Meldung + 5 s)
  await s.play("Alice", { seconds: 60, server: "a", tz: 0 });
  s.tick(10 * 1000);
  await s.play("Alice", { seconds: 60, server: "a", tz: 0 }); // → 15
  s.tick(1000);
  await s.play("Alice", { seconds: 120, server: "a", tz: 0 }); // → 6
  let data = await s.stats("Alice");
  assert.equal(data.totals.seconds, 81);
  assert.equal(data.servers[0].seconds, 81);
  assert.equal(data.days.at(-1).seconds, 81);
  // normale Meldungen danach wieder voll
  s.tick(60 * 1000);
  await s.play("Alice", { seconds: 60, server: "a", tz: 0 });
  assert.equal((await s.stats("Alice")).totals.seconds, 141);

  // höchstens 150 s je Minute
  await s.play("Bob", { seconds: 120, tz: 0 });
  s.tick(30 * 1000);
  await s.play("Bob", { seconds: 120, tz: 0 }); // → 30 (Fenster voll)
  s.tick(25 * 1000);
  await s.play("Bob", { seconds: 120, tz: 0 }); // → 0
  data = await s.stats("Bob");
  assert.equal(data.totals.seconds, 150);
  s.tick(10 * 1000); // neues Fenster
  await s.play("Bob", { seconds: 120, tz: 0 }); // → 15 (10 s + 5 s)
  assert.equal((await s.stats("Bob")).totals.seconds, 165);
});

test("Statistik: 30-Tage-Reihe mit leeren Tagen", async () => {
  const s = await setup();
  await s.play("Alice", { seconds: 100, tz: 0 }); // 2001-09-09, fällt später aus der Reihe
  s.tick(10 * DAY);
  await s.launch("Alice", { tz: 0 });
  await s.play("Alice", { seconds: 50, tz: 0 }); // 2001-09-19
  s.tick(25 * DAY);
  await s.play("Alice", { seconds: 20, tz: 0 }); // 2001-10-14 (heute)
  const data = await s.stats("Alice");
  assert.equal(data.days.length, 30);
  assert.equal(data.days[0].day, "2001-09-15");
  assert.equal(data.days.at(-1).day, "2001-10-14");
  for (let i = 1; i < data.days.length; i++) {
    assert.equal(Date.parse(data.days[i].day) - Date.parse(data.days[i - 1].day), DAY);
  }
  const nonZero = data.days.filter((d) => d.seconds || d.launches);
  assert.deepEqual(nonZero, [{ day: "2001-09-19", seconds: 50, launches: 1 }, { day: "2001-10-14", seconds: 20, launches: 0 }]);
  assert.equal(data.totals.seconds, 170);
  assert.equal(data.totals.today, 20);
  assert.equal(data.totals.week, 20);
});

test("Statistik: Schach-Bilanz aus beendeten Partien", async () => {
  const s = await setup();
  const start = async (white, black) => {
    const invite = await s.call("POST", "/api/games", { to: black, time: 300, color: "white" }, s.t(white));
    assert.equal(invite.status, 200, JSON.stringify(invite.data));
    const accepted = await s.call("POST", `/api/games/${invite.data.game.id}/accept`, null, s.t(black));
    assert.equal(accepted.status, 200, JSON.stringify(accepted.data));
    return accepted.data.game.id;
  };
  // Narrenmatt: Alice (Weiß) verliert gegen Bob
  let id = await start("Alice", "Bob");
  const moves = ["f2f3", "e7e5", "g2g4", "d8h4"];
  for (let ply = 0; ply < moves.length; ply++) {
    const res = await s.call("POST", `/api/games/${id}/move`, { move: moves[ply], ply }, s.t(ply % 2 ? "Bob" : "Alice"));
    assert.equal(res.status, 200, JSON.stringify(res.data));
  }
  // Carol (Schwarz) gibt gegen Alice auf
  id = await start("Alice", "Carol");
  assert.equal((await s.call("POST", `/api/games/${id}/resign`, null, s.t("Carol"))).status, 200);
  // Remis zwischen Bob (Weiß) und Alice
  id = await start("Bob", "Alice");
  await s.call("POST", `/api/games/${id}/draw`, { action: "offer" }, s.t("Bob"));
  assert.equal((await s.call("POST", `/api/games/${id}/draw`, { action: "accept" }, s.t("Alice"))).status, 200);
  // laufende Partie zählt nicht
  await start("Carol", "Bob");

  const alice = (await s.stats("Alice")).chess;
  assert.deepEqual({ ...alice, opponents: undefined }, { played: 3, wins: 1, losses: 1, draws: 1, opponents: undefined });
  assert.deepEqual(alice.opponents, [
    { name: "Bob", played: 2, wins: 0, losses: 1, draws: 1 },
    { name: "Carol", played: 1, wins: 1, losses: 0, draws: 0 },
  ]);
  const bob = (await s.stats("Bob")).chess;
  assert.deepEqual(bob, { played: 2, wins: 1, losses: 0, draws: 1, opponents: [{ name: "Alice", played: 2, wins: 1, losses: 0, draws: 1 }] });
  const carol = (await s.stats("Carol")).chess;
  assert.deepEqual(carol, { played: 1, wins: 0, losses: 1, draws: 0, opponents: [{ name: "Alice", played: 1, wins: 0, losses: 1, draws: 0 }] });
});
