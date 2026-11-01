import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../src/node-server.js";
import { createApi } from "../src/api.js";
import { START_FEN } from "../src/chess.js";

// ------------------------------------------------------------------ X und O (Tic-Tac-Toe)

const NAMES = ["Alice", "Bob", "Carol", "Dave"];

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
  const invite = (from, to, opts = {}) => call("POST", "/api/games", { to, game: "xo", rounds: 3, moveTime: 0, color: "white", ...opts }, t(from));
  /** Einladen und annehmen, Einladender spielt X. */
  const start = async (x, o, opts = {}) => {
    const inv = await invite(x, o, opts);
    assert.equal(inv.status, 200, JSON.stringify(inv.data));
    const accepted = await call("POST", `/api/games/${inv.data.game.id}/accept`, null, t(o));
    assert.equal(accepted.status, 200, JSON.stringify(accepted.data));
    return accepted.data.game;
  };
  const move = (id, name, cell, ply) => call("POST", `/api/games/${id}/move`, { move: cell, ply }, t(name));
  /** Zugfolge abwechselnd ab dem Spieler first; gibt die letzte Antwort zurück. */
  const play = async (id, first, second, cells) => {
    let res;
    for (let i = 0; i < cells.length; i++) {
      res = await move(id, i % 2 ? second : first, cells[i], i);
      assert.equal(res.status, 200, JSON.stringify(res.data));
    }
    return res.data.game;
  };
  return { call, users, t, invite, start, move, play, tick: (ms) => { clock += ms; } };
}

test("X und O: einladen, annehmen, Runde mit Reihe gewinnen", async () => {
  const s = await setup();
  const inv = await s.invite("Alice", "Bob", { rounds: 3, moveTime: 10, color: "black" });
  assert.equal(inv.status, 200);
  assert.equal(inv.data.game.kind, "xo");
  assert.equal(inv.data.game.status, "invited");
  assert.equal(inv.data.game.rounds, 3);
  assert.equal(inv.data.game.moveTime, 10);
  assert.equal(inv.data.game.time, 0);
  assert.equal(inv.data.game.xo.moveLeft, null);
  const game = (await s.call("POST", `/api/games/${inv.data.game.id}/accept`, null, s.t("Bob"))).data.game;
  assert.equal(game.status, "active");
  assert.equal(game.white.name, "Bob", "Alice wollte O (Schwarz) – Bob ist X");
  assert.equal(game.black.name, "Alice");
  assert.equal(game.clock, null);
  assert.equal(game.fen, null);
  assert.deepEqual(game.xo, { board: ".........", round: 1, starter: "x", turn: "x", score: { x: 0, o: 0 }, results: [], moveLeft: 10000 });
  s.tick(3000);
  assert.equal((await s.call("GET", "/api/games", null, s.t("Alice"))).data.games[0].xo.moveLeft, 7000);

  const g = await s.play(game.id, "Bob", "Alice", [0, 3, 1, 4, 2]);
  assert.equal(g.status, "active");
  assert.deepEqual(g.xo.score, { x: 1, o: 0 });
  assert.deepEqual(g.xo.results, [{ winner: "x", reason: "line", line: [0, 1, 2], board: "xxxoo...." }]);
  // nächste Runde: Brett leer, O beginnt
  assert.equal(g.xo.board, ".........");
  assert.equal(g.xo.round, 2);
  assert.equal(g.xo.starter, "o");
  assert.equal(g.xo.turn, "o");
  assert.equal(g.turn, "b");
  assert.equal(g.xo.moveLeft, 10000);
});

test("X und O: Unentschieden zählt nicht, Anfang wechselt, best of 3 nach 2 Siegen vorbei", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob", { rounds: 3 });
  // Runde 1 (X beginnt): volles Brett ohne Reihe
  let g = await s.play(game.id, "Alice", "Bob", [0, 1, 2, 4, 3, 5, 7, 6, 8]);
  assert.deepEqual(g.xo.score, { x: 0, o: 0 });
  assert.deepEqual(g.xo.results, [{ winner: null, reason: "draw", line: null, board: "xoxxoooxx" }]);
  assert.equal(g.xo.round, 2);
  assert.equal(g.xo.starter, "o");
  // Runde 2 (O beginnt): O gewinnt
  g = await s.play(game.id, "Bob", "Alice", [0, 3, 1, 4, 2]);
  assert.deepEqual(g.xo.score, { x: 0, o: 1 });
  assert.equal(g.xo.results[1].winner, "o");
  assert.equal(g.xo.results[1].board, "oooxx....");
  assert.equal(g.xo.starter, "x");
  assert.equal(g.xo.round, 3);
  // Runde 3 (X beginnt): O gewinnt erneut → Partie vorbei
  g = await s.play(game.id, "Alice", "Bob", [0, 3, 1, 4, 8, 5]);
  assert.equal(g.status, "over");
  assert.equal(g.result, "0-1");
  assert.equal(g.reason, "rounds");
  assert.deepEqual(g.xo.score, { x: 0, o: 2 });
  assert.deepEqual(g.xo.results[2].line, [3, 4, 5]);
  assert.equal(g.xo.moveLeft, null);
  assert.equal((await s.move(game.id, "Alice", 6, 0)).status, 409);
  // X-und-O-Partien zählen nicht in die Schach-Bilanz
  const stats = await s.call("GET", "/api/stats", null, s.t("Alice"));
  assert.equal(stats.status, 200);
  assert.equal(stats.data.chess.played, 0);
});

test("X und O: belegtes Feld, falscher Spieler, falscher ply, ungültiges Feld", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob");
  assert.equal((await s.move(game.id, "Bob", 0, 0)).status, 409, "O ist nicht am Zug");
  assert.equal((await s.move(game.id, "Alice", 0, 1)).status, 409, "ply passt nicht");
  assert.equal((await s.move(game.id, "Alice", 9, 0)).status, 400);
  assert.equal((await s.move(game.id, "Alice", "e2e4", 0)).status, 400);
  assert.equal((await s.move(game.id, "Alice", 4, 0)).status, 200);
  const taken = await s.move(game.id, "Bob", 4, 1);
  assert.equal(taken.status, 400);
  assert.equal(taken.data.error, "Feld ist belegt");
  assert.equal((await s.move(game.id, "Alice", 0, 1)).status, 409, "X ist nicht am Zug");
  // Remis-Angebote gibt es nicht
  assert.equal((await s.call("POST", `/api/games/${game.id}/draw`, { action: "offer" }, s.t("Bob"))).status, 409);
});

test("X und O: Zeit je Zug abgelaufen → Runde verloren", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob", { rounds: 3, moveTime: 5 });
  await s.move(game.id, "Alice", 4, 0);
  s.tick(5001); // O denkt zu lange
  let g = (await s.call("GET", "/api/games", null, s.t("Alice"))).data.games[0];
  assert.equal(g.status, "active");
  assert.deepEqual(g.xo.score, { x: 1, o: 0 });
  assert.deepEqual(g.xo.results, [{ winner: "x", reason: "timeout", line: null, board: "....x...." }]);
  assert.equal(g.xo.round, 2);
  assert.equal(g.xo.turn, "o");
  assert.equal(g.xo.moveLeft, 4999, "neue Runde beginnt mit dem Ablauf");
  // ein Zug nach Ablauf zählt nicht mehr
  s.tick(5000);
  assert.equal((await s.move(game.id, "Bob", 0, 0)).status, 409);
  g = (await s.call("GET", "/api/games", null, s.t("Bob"))).data.games[0];
  assert.equal(g.status, "over");
  assert.equal(g.result, "1-0");
  assert.equal(g.reason, "rounds");
  assert.deepEqual(g.xo.score, { x: 2, o: 0 });

  // lange Abwesenheit beider: höchstens bis zum Partie-Ende nachgeholt
  const second = await s.start("Carol", "Dave", { rounds: 5, moveTime: 5 });
  s.tick(29000);
  const late = (await s.call("GET", "/api/games", null, s.t("Carol"))).data.games.find((x) => x.id === second.id);
  assert.equal(late.status, "over");
  assert.equal(late.reason, "rounds");
  assert.equal(late.xo.results.length, 5);
  // Anfang wechselt, jeder läuft abwechselnd in die Zeit: O, X, O, X, O gewinnen die Runden
  assert.deepEqual(late.xo.score, { x: 2, o: 3 });
  assert.equal(late.result, "0-1");

  // ohne Zeitlimit kein Ablauf (bis zur 30-s-Regel)
  const third = await s.start("Alice", "Bob", { moveTime: 0 });
  s.tick(20000);
  const free = (await s.call("GET", "/api/games", null, s.t("Alice"))).data.games.find((x) => x.id === third.id);
  assert.equal(free.status, "active");
  assert.equal(free.xo.moveLeft, null);
  assert.deepEqual(free.xo.results, []);
});

test("X und O: Aufgeben, Verlassen, Verbindung verloren", async () => {
  const s = await setup();
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
});

test("X und O: Einladung verfällt nach 60 s und bleibt für den Einladenden sichtbar", async () => {
  const s = await setup();
  const inv = await s.invite("Alice", "Bob");
  s.tick(59999);
  assert.equal((await s.call("GET", "/api/games", null, s.t("Alice"))).data.games[0].status, "invited");
  s.tick(1);
  assert.equal((await s.call("POST", `/api/games/${inv.data.game.id}/accept`, null, s.t("Bob"))).status, 409);
  const list = (await s.call("GET", "/api/games", null, s.t("Alice"))).data.games;
  assert.equal(list.length, 1);
  assert.equal(list[0].id, inv.data.game.id);
  assert.equal(list[0].status, "expired");
  assert.equal(list[0].kind, "xo");
  // nach ~2 Minuten verschwindet sie; neu einladen geht sofort
  s.tick(2 * 60 * 1000 + 1);
  assert.deepEqual((await s.call("GET", "/api/games", null, s.t("Alice"))).data.games, []);
  assert.equal((await s.invite("Alice", "Bob")).status, 200);
});

test("X und O: Eingaben prüfen, altes Client-Format ist Schach", async () => {
  const s = await setup();
  const alice = s.t("Alice");
  assert.equal((await s.invite("Alice", "Bob", { rounds: 2 })).status, 400);
  assert.equal((await s.invite("Alice", "Bob", { rounds: undefined })).status, 400);
  assert.equal((await s.invite("Alice", "Bob", { moveTime: 15 })).status, 400);
  assert.equal((await s.invite("Alice", "Bob", { moveTime: -1 })).status, 400);
  assert.equal((await s.invite("Alice", "Bob", { color: "pink" })).status, 400);
  const bad = await s.call("POST", "/api/games", { to: "Bob", game: "go", time: 300 }, alice);
  assert.equal(bad.status, 400);
  assert.equal(bad.data.error, "Unbekanntes Spiel");
  // ohne game-Feld: Schach wie bisher (Bedenkzeit Pflicht)
  assert.equal((await s.call("POST", "/api/games", { to: "Bob" }, alice)).status, 400);
  const chess = await s.call("POST", "/api/games", { to: "Bob", time: 300, color: "white" }, alice);
  assert.equal(chess.status, 200);
  assert.equal(chess.data.game.kind, "chess");
  assert.equal(chess.data.game.xo, null);
  assert.equal(chess.data.game.rounds, null);
  assert.equal(chess.data.game.moveTime, null);
  assert.equal(chess.data.game.fen, START_FEN);
  assert.equal(chess.data.game.time, 300);
});

test("X und O: Einladungs-Regeln und Revanche je Spielart", async () => {
  const s = await setup();
  // offene Schach-Einladung blockiert auch X und O zwischen denselben Spielern
  assert.equal((await s.call("POST", "/api/games", { to: "Bob", time: 300 }, s.t("Alice"))).status, 200);
  assert.equal((await s.invite("Alice", "Bob")).status, 409);
  // Gegeneinladung anderer Art: kein automatisches Annehmen
  const other = await s.invite("Bob", "Alice");
  assert.equal(other.status, 409);
  assert.match(other.data.error, /Schach herausgefordert/);
  // gleiche Art: beide fordern sich heraus → Partie startet mit den Einstellungen des Ersten
  const a = await s.invite("Carol", "Dave", { rounds: 5, moveTime: 30, color: "white" });
  const b = await s.invite("Dave", "Carol", { rounds: 1, moveTime: 0, color: "white" });
  assert.equal(b.status, 200, JSON.stringify(b.data));
  assert.equal(b.data.game.id, a.data.game.id);
  assert.equal(b.data.game.status, "active");
  assert.equal(b.data.game.rounds, 5);
  assert.equal(b.data.game.white.name, "Carol");
  // laufende X-und-O-Partie blockiert neue Einladungen beider Art
  assert.equal((await s.call("POST", "/api/games", { to: "Carol", time: 300 }, s.t("Dave"))).status, 409);
});
