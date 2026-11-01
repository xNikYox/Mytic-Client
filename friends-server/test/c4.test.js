import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../src/node-server.js";
import { createApi } from "../src/api.js";

// ------------------------------------------------------------------ Vier gewinnt (Connect Four)

const NAMES = ["Alice", "Bob", "Carol", "Dave"];
const EMPTY = ".".repeat(42);
// volles Brett ohne Vierer (Spalten, abwechselnd ab a)
const DRAW = [2, 4, 0, 0, 6, 0, 0, 0, 2, 2, 5, 3, 0, 6, 6, 4, 3, 1, 2, 3, 4, 2, 1, 2, 3, 3, 1, 1, 6, 5, 1, 5, 1, 5, 5, 6, 3, 5, 4, 6, 4, 4];

async function setup() {
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
  for (const name of NAMES) {
    const res = await call("POST", "/api/auth/offline", { name });
    assert.equal(res.status, 200);
    users[name] = { token: res.data.token, uuid: res.data.me.uuid };
  }
  const t = (name) => users[name].token;
  const invite = (from, to, opts = {}) => call("POST", "/api/games", { to, game: "c4", rounds: 3, moveTime: 0, color: "white", ...opts }, t(from));
  /** Einladen und annehmen, Einladender spielt a (Weiß). */
  const start = async (a, b, opts = {}) => {
    const inv = await invite(a, b, opts);
    assert.equal(inv.status, 200, JSON.stringify(inv.data));
    const accepted = await call("POST", `/api/games/${inv.data.game.id}/accept`, null, t(b));
    assert.equal(accepted.status, 200, JSON.stringify(accepted.data));
    return accepted.data.game;
  };
  const move = (id, name, col, ply) => call("POST", `/api/games/${id}/move`, { move: col, ply }, t(name));
  /** Spalten abwechselnd ab dem Spieler first; gibt die letzte Antwort zurück. */
  const play = async (id, first, second, cols) => {
    let res;
    for (let i = 0; i < cols.length; i++) {
      res = await move(id, i % 2 ? second : first, cols[i], i);
      assert.equal(res.status, 200, JSON.stringify(res.data));
    }
    return res.data.game;
  };
  return { call, users, t, invite, start, move, play, tick: (ms) => { clock += ms; } };
}

test("Vier gewinnt: einladen, annehmen, senkrechter Vierer gewinnt die Runde", async () => {
  const s = await setup();
  const inv = await s.invite("Alice", "Bob", { rounds: 3, moveTime: 10, color: "black" });
  assert.equal(inv.status, 200);
  assert.equal(inv.data.game.kind, "c4");
  assert.equal(inv.data.game.rounds, 3);
  assert.equal(inv.data.game.moveTime, 10);
  assert.equal(inv.data.game.xo, null);
  assert.equal(inv.data.game.c4.moveLeft, null);
  const game = (await s.call("POST", `/api/games/${inv.data.game.id}/accept`, null, s.t("Bob"))).data.game;
  assert.equal(game.status, "active");
  assert.equal(game.white.name, "Bob", "Alice wollte Schwarz – Bob ist a");
  assert.equal(game.clock, null);
  assert.equal(game.fen, null);
  assert.deepEqual(game.c4, { board: EMPTY, round: 1, starter: "a", turn: "a", score: { a: 0, b: 0 }, results: [], moveLeft: 10000 });

  const g = await s.play(game.id, "Bob", "Alice", [0, 1, 0, 1, 0, 1, 0]);
  assert.equal(g.status, "active");
  assert.deepEqual(g.c4.score, { a: 1, b: 0 });
  assert.equal(g.c4.results.length, 1);
  assert.equal(g.c4.results[0].winner, "a");
  assert.equal(g.c4.results[0].reason, "line");
  assert.deepEqual(g.c4.results[0].line, [14, 21, 28, 35]);
  assert.equal(g.c4.results[0].board, EMPTY.slice(0, 14) + "a......ab.....ab.....ab.....");
  // nächste Runde: Brett leer, b beginnt
  assert.equal(g.c4.board, EMPTY);
  assert.equal(g.c4.round, 2);
  assert.equal(g.c4.starter, "b");
  assert.equal(g.c4.turn, "b");
  assert.equal(g.turn, "b");
});

test("Vier gewinnt: waagrechter und diagonaler Vierer, Steine fallen nach unten", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob", { rounds: 5 });
  let res = await s.move(game.id, "Alice", 0, 0);
  res = await s.move(game.id, "Bob", 0, 1);
  res = await s.move(game.id, "Alice", 1, 2);
  assert.equal(res.data.game.c4.board.slice(28), "b......aa.....", "Stein fällt in die tiefste freie Zeile");
  for (const [name, col, ply] of [["Bob", 1, 3], ["Alice", 2, 4], ["Bob", 2, 5], ["Alice", 3, 6]]) res = await s.move(game.id, name, col, ply);
  let g = res.data.game;
  assert.equal(g.c4.results[0].winner, "a");
  assert.deepEqual(g.c4.results[0].line, [35, 36, 37, 38]);
  assert.equal(g.c4.starter, "b");
  // Runde 2 (b beginnt): Alice (a) baut die Diagonale 35, 29, 23, 17
  g = await s.play(game.id, "Bob", "Alice", [6, 0, 1, 1, 2, 3, 2, 2, 3, 4, 3, 3]);
  assert.equal(g.c4.results[1].winner, "a");
  assert.equal(g.c4.results[1].reason, "line");
  assert.deepEqual(g.c4.results[1].line, [17, 23, 29, 35]);
  assert.deepEqual(g.c4.score, { a: 2, b: 0 });
  // Runde 3 (a beginnt): andere Diagonale 41, 33, 25, 17 → a hat 3 Runden, Partie vorbei
  g = await s.play(game.id, "Alice", "Bob", [6, 5, 5, 4, 4, 3, 4, 3, 3, 0, 3]);
  assert.equal(g.c4.results[2].winner, "a");
  assert.deepEqual(g.c4.results[2].line, [17, 25, 33, 41]);
  assert.equal(g.status, "over");
  assert.equal(g.result, "1-0");
  assert.equal(g.reason, "rounds");
});

test("Vier gewinnt: volle Spalte, falscher Spieler, falscher ply, ungültige Spalte, kein Remis-Angebot", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob");
  assert.equal((await s.move(game.id, "Bob", 0, 0)).status, 409, "b ist nicht am Zug");
  assert.equal((await s.move(game.id, "Alice", 0, 1)).status, 409, "ply passt nicht");
  assert.equal((await s.move(game.id, "Alice", 7, 0)).status, 400);
  assert.equal((await s.move(game.id, "Alice", -1, 0)).status, 400);
  assert.equal((await s.move(game.id, "Alice", "e2e4", 0)).status, 400);
  await s.play(game.id, "Alice", "Bob", [0, 0, 0, 0, 0, 0]);
  const full = await s.move(game.id, "Alice", 0, 6);
  assert.equal(full.status, 400);
  assert.equal(full.data.error, "Spalte ist voll");
  assert.equal((await s.move(game.id, "Alice", "1", 6)).status, 200, "Spalte als Text geht auch");
  assert.equal((await s.call("POST", `/api/games/${game.id}/draw`, { action: "offer" }, s.t("Bob"))).status, 409);
});

test("Vier gewinnt: Unentschieden zählt nicht, Anfang wechselt, best of 3 nach 2 Siegen vorbei", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob", { rounds: 3 });
  // Runde 1 (a beginnt): volles Brett ohne Vierer
  let g = await s.play(game.id, "Alice", "Bob", DRAW);
  assert.deepEqual(g.c4.score, { a: 0, b: 0 });
  assert.deepEqual(g.c4.results, [{ winner: null, reason: "draw", line: null, board: "aababbbbabbaababaaabababbababaaabbbababbaa" }]);
  assert.equal(g.c4.round, 2);
  assert.equal(g.c4.starter, "b");
  // Runde 2 (b beginnt): b gewinnt senkrecht
  g = await s.play(game.id, "Bob", "Alice", [3, 4, 3, 4, 3, 4, 3]);
  assert.deepEqual(g.c4.score, { a: 0, b: 1 });
  assert.equal(g.c4.results[1].winner, "b");
  assert.equal(g.c4.starter, "a");
  assert.equal(g.c4.round, 3);
  // Runde 3 (a beginnt): b gewinnt erneut → Partie vorbei
  g = await s.play(game.id, "Alice", "Bob", [0, 6, 1, 6, 0, 6, 1, 6]);
  assert.equal(g.status, "over");
  assert.equal(g.result, "0-1");
  assert.equal(g.reason, "rounds");
  assert.deepEqual(g.c4.score, { a: 0, b: 2 });
  assert.equal(g.c4.moveLeft, null);
  assert.equal((await s.move(game.id, "Alice", 0, 0)).status, 409);
  // zählt nicht in die Schach-Bilanz
  assert.equal((await s.call("GET", "/api/stats", null, s.t("Alice"))).data.chess.played, 0);
});

test("Vier gewinnt: Zeit je Zug abgelaufen → Runde verloren", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob", { rounds: 3, moveTime: 5 });
  await s.move(game.id, "Alice", 3, 0);
  s.tick(5001); // b denkt zu lange
  let g = (await s.call("GET", "/api/games", null, s.t("Alice"))).data.games[0];
  assert.equal(g.status, "active");
  assert.deepEqual(g.c4.score, { a: 1, b: 0 });
  assert.deepEqual(g.c4.results, [{ winner: "a", reason: "timeout", line: null, board: EMPTY.slice(0, 38) + "a..." }]);
  assert.equal(g.c4.round, 2);
  assert.equal(g.c4.turn, "b");
  assert.equal(g.c4.moveLeft, 4999, "neue Runde beginnt mit dem Ablauf");
  s.tick(5000);
  assert.equal((await s.move(game.id, "Bob", 0, 0)).status, 409, "Zug nach Ablauf zählt nicht");
  g = (await s.call("GET", "/api/games", null, s.t("Bob"))).data.games[0];
  assert.equal(g.status, "over");
  assert.equal(g.result, "1-0");
  assert.deepEqual(g.c4.score, { a: 2, b: 0 });
});

test("Vier gewinnt: Eingaben prüfen, Aufgeben, Verlassen, Verbindung verloren, Revanche je Spielart", async () => {
  const s = await setup();
  assert.equal((await s.invite("Alice", "Bob", { rounds: 2 })).status, 400);
  assert.equal((await s.invite("Alice", "Bob", { moveTime: 20 })).status, 400);
  assert.equal((await s.invite("Alice", "Bob", { color: "pink" })).status, 400);
  const game = await s.start("Alice", "Bob");
  const res = await s.call("POST", `/api/games/${game.id}/resign`, null, s.t("Bob"));
  assert.equal(res.data.game.result, "1-0");
  assert.equal(res.data.game.reason, "resign");
  const left = await s.start("Carol", "Dave");
  const r2 = await s.call("POST", `/api/games/${left.id}/resign`, { reason: "left" }, s.t("Carol"));
  assert.equal(r2.data.game.result, "0-1");
  assert.equal(r2.data.game.reason, "left");
  const gone = await s.start("Alice", "Bob");
  s.tick(31000);
  const g = (await s.call("GET", "/api/games", null, s.t("Alice"))).data.games.find((x) => x.id === gone.id);
  assert.equal(g.status, "over");
  assert.equal(g.reason, "abandoned");
  // Gegeneinladung anderer Art: kein automatisches Annehmen
  assert.equal((await s.invite("Carol", "Dave")).status, 200);
  const other = await s.call("POST", "/api/games", { to: "Carol", game: "xo", rounds: 1, moveTime: 0 }, s.t("Dave"));
  assert.equal(other.status, 409);
  assert.match(other.data.error, /Vier gewinnt herausgefordert/);
  const same = await s.invite("Dave", "Carol", { rounds: 1 });
  assert.equal(same.status, 200);
  assert.equal(same.data.game.status, "active");
  assert.equal(same.data.game.rounds, 3);
});
