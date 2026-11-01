import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../src/node-server.js";
import { createApi } from "../src/api.js";
import { initialState, parseFen, toFen, applyUci, legalMoves, status, perft, START_FEN } from "../src/chess.js";

const KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

/** Zugfolge ausführen (jeder Zug muss erlaubt sein). */
function play(state, moves) {
  for (const move of moves.split(" ")) {
    state = applyUci(state, move);
    assert.ok(state, `Zug ${move} sollte erlaubt sein`);
  }
  return state;
}

// ------------------------------------------------------------------ Regeln

test("Schach: Perft aus der Ausgangsstellung", () => {
  const start = initialState();
  assert.equal(perft(start, 1), 20);
  assert.equal(perft(start, 2), 400);
  assert.equal(perft(start, 3), 8902);
});

test("Schach: Perft Kiwipete (Rochade, en passant, Umwandlung)", () => {
  const state = parseFen(KIWIPETE);
  assert.equal(perft(state, 1), 48);
  assert.equal(perft(state, 2), 2039);
  assert.equal(perft(state, 3), 97862);
});

test("Schach: FEN lesen und schreiben", () => {
  assert.equal(toFen(initialState()), START_FEN);
  assert.equal(toFen(parseFen(KIWIPETE)), KIWIPETE);
  assert.equal(parseFen("kaputt"), null);
  assert.equal(toFen(applyUci(initialState(), "e2e4")), "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
});

test("Schach: Sonderzüge, Matt und Patt", () => {
  // Rochade kurz und lang
  const castle = parseFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
  assert.equal(toFen(applyUci(castle, "e1g1")), "r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1");
  assert.equal(toFen(applyUci(castle, "e1c1")), "r3k2r/8/8/8/8/8/8/2KR3R b kq - 1 1");
  // keine Rochade durch ein angegriffenes Feld
  assert.equal(applyUci(parseFen("r3k2r/8/8/8/8/8/5r2/R3K2R w KQkq - 0 1"), "e1g1"), null);
  // en passant
  const ep = play(initialState(), "e2e4 a7a6 e4e5 d7d5");
  assert.equal(toFen(applyUci(ep, "e5d6")), "rnbqkbnr/1pp1pppp/p2P4/8/8/8/PPPP1PPP/RNBQKBNR b KQkq - 0 3");
  // Umwandlung: ohne Angabe Dame, sonst die gewählte Figur
  const promo = parseFen("8/P6k/8/8/8/8/8/K7 w - - 0 1");
  assert.match(toFen(applyUci(promo, "a7a8")), /^Q7/);
  assert.match(toFen(applyUci(promo, "a7a8n")), /^N7/);
  assert.equal(applyUci(initialState(), "e2e4q"), null);
  assert.equal(applyUci(initialState(), "e2e5"), null);
  assert.equal(legalMoves(promo).filter((m) => m.startsWith("a7a8")).length, 4);
  // Narrenmatt und Patt
  assert.equal(status(play(initialState(), "f2f3 e7e5 g2g4 d8h4")), "checkmate");
  assert.equal(status(parseFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")), "stalemate");
  assert.equal(status(initialState()), "ok");
});

// ------------------------------------------------------------------ API

const NAMES = ["Alice", "Bob", "Carol", "Dave", "Erin", "Frank", "Gina", "Hank"];

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
  /** Einladen und annehmen, Einladender spielt Weiß. */
  const start = async (white, black, time = 300) => {
    const invite = await call("POST", "/api/games", { to: black, time, color: "white" }, t(white));
    assert.equal(invite.status, 200, JSON.stringify(invite.data));
    const accepted = await call("POST", `/api/games/${invite.data.game.id}/accept`, null, t(black));
    assert.equal(accepted.status, 200, JSON.stringify(accepted.data));
    return accepted.data.game;
  };
  const move = (id, token, uci, ply) => call("POST", `/api/games/${id}/move`, { move: uci, ply }, token);
  return { call, users, t, start, move, tick: (ms) => { clock += ms; } };
}

test("Partie: einladen, annehmen, ziehen, Narrenmatt", async () => {
  const s = await setup();
  const list0 = await s.call("GET", "/api/games", null, s.t("Bob"));
  assert.deepEqual(list0.data.games, []);
  assert.ok(list0.data.serverTime);
  assert.equal((await s.call("GET", "/api/games")).status, 401);

  const invite = await s.call("POST", "/api/games", { to: "bob", time: 180, color: "black" }, s.t("Alice"));
  assert.equal(invite.status, 200);
  const g = invite.data.game;
  assert.equal(g.status, "invited");
  assert.equal(g.inviter.name, "Alice");
  assert.equal(g.invitee.name, "Bob");
  assert.equal(g.white, null);
  assert.equal(g.time, 180);
  // Bob sieht die Einladung
  const bobList = await s.call("GET", "/api/games", null, s.t("Bob"));
  assert.equal(bobList.data.games[0].id, g.id);
  // nur der Eingeladene nimmt an
  assert.equal((await s.call("POST", `/api/games/${g.id}/accept`, null, s.t("Alice"))).status, 403);
  s.tick(5000);
  const accepted = await s.call("POST", `/api/games/${g.id}/accept`, null, s.t("Bob"));
  assert.equal(accepted.status, 200);
  const game = accepted.data.game;
  assert.equal(game.status, "active");
  assert.equal(game.white.name, "Bob"); // Alice wollte Schwarz
  assert.equal(game.black.name, "Alice");
  assert.deepEqual(game.clock, { white: 180000, black: 180000 });
  assert.equal(game.turn, "w");

  const bob = s.t("Bob");
  const alice = s.t("Alice");
  // Reihenfolge, Halbzug-Zähler, unerlaubte Züge
  assert.equal((await s.move(g.id, alice, "e7e5", 0)).status, 409);
  assert.equal((await s.move(g.id, bob, "e2e5", 0)).status, 400);
  assert.equal((await s.move(g.id, bob, "quatsch", 0)).status, 400);
  assert.equal((await s.move(g.id, bob, "f2f3", 1)).status, 409);
  s.tick(3000);
  let res = await s.move(g.id, bob, "f2f3", 0);
  assert.equal(res.status, 200);
  assert.deepEqual(res.data.game.moves, ["f2f3"]);
  assert.equal(res.data.game.turn, "b");
  assert.deepEqual(res.data.game.clock, { white: 177000, black: 180000 });
  assert.equal((await s.move(g.id, bob, "e2e4", 1)).status, 409); // nicht am Zug
  s.tick(2000);
  assert.equal((await s.move(g.id, alice, "e7e5", 1)).status, 200);
  assert.equal((await s.move(g.id, bob, "g2g4", 2)).status, 200);
  // Uhr der Seite am Zug läuft beim Lesen weiter
  s.tick(4000);
  const view = await s.call("GET", "/api/games", null, alice);
  assert.deepEqual(view.data.games[0].clock, { white: 177000, black: 174000 });
  res = await s.move(g.id, alice, "d8h4", 3);
  assert.equal(res.status, 200);
  assert.equal(res.data.game.status, "over");
  assert.equal(res.data.game.result, "0-1");
  assert.equal(res.data.game.reason, "checkmate");
  assert.equal((await s.move(g.id, bob, "a2a3", 4)).status, 409);
  // beendete Partie bleibt 2 Minuten in der Liste
  assert.equal((await s.call("GET", "/api/games", null, bob)).data.games[0].result, "0-1");
  s.tick(2 * 60 * 1000 + 1);
  assert.deepEqual((await s.call("GET", "/api/games", null, bob)).data.games, []);
});

test("Partie: Fremde sehen nichts (404)", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob");
  const carol = s.t("Carol");
  for (const action of ["accept", "decline", "cancel", "resign"]) {
    assert.equal((await s.call("POST", `/api/games/${game.id}/${action}`, null, carol)).status, 404);
  }
  assert.equal((await s.call("POST", `/api/games/${game.id}/draw`, { action: "offer" }, carol)).status, 404);
  assert.equal((await s.move(game.id, carol, "e2e4", 0)).status, 404);
  assert.deepEqual((await s.call("GET", "/api/games", null, carol)).data.games, []);
  assert.equal((await s.call("POST", "/api/games/unbekannt/accept", null, carol)).status, 404);
});

test("Partie: Aufgeben und Remis", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob");
  assert.equal(game.white.name, "Alice");
  const res = await s.call("POST", `/api/games/${game.id}/resign`, null, s.t("Bob"));
  assert.equal(res.data.game.status, "over");
  assert.equal(res.data.game.result, "1-0");
  assert.equal(res.data.game.reason, "resign");
  assert.equal((await s.call("POST", `/api/games/${game.id}/resign`, null, s.t("Alice"))).status, 409);

  const draw = await s.start("Carol", "Dave");
  const carol = s.t("Carol");
  const dave = s.t("Dave");
  assert.equal((await s.call("POST", `/api/games/${draw.id}/draw`, { action: "accept" }, dave)).status, 409);
  let offer = await s.call("POST", `/api/games/${draw.id}/draw`, { action: "offer" }, carol);
  assert.equal(offer.data.game.drawOffer, s.users.Carol.uuid);
  // eigenes Angebot kann man nicht selbst annehmen
  assert.equal((await s.call("POST", `/api/games/${draw.id}/draw`, { action: "accept" }, carol)).status, 409);
  // Ablehnen und ein Zug löschen das Angebot
  assert.equal((await s.call("POST", `/api/games/${draw.id}/draw`, { action: "decline" }, dave)).data.game.drawOffer, null);
  await s.call("POST", `/api/games/${draw.id}/draw`, { action: "offer" }, carol);
  assert.equal((await s.move(draw.id, carol, "e2e4", 0)).data.game.drawOffer, null);
  offer = await s.call("POST", `/api/games/${draw.id}/draw`, { action: "offer" }, carol);
  assert.equal(offer.data.game.status, "active");
  const accepted = await s.call("POST", `/api/games/${draw.id}/draw`, { action: "accept" }, dave);
  assert.equal(accepted.data.game.status, "over");
  assert.equal(accepted.data.game.result, "1/2-1/2");
  assert.equal(accepted.data.game.reason, "agreement");
  assert.equal((await s.call("POST", `/api/games/${draw.id}/draw`, { action: "nix" }, dave)).status, 409);
});

test("Partie: Zeit abgelaufen", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob", 180);
  const alice = s.t("Alice");
  const bob = s.t("Bob");
  s.tick(10000);
  assert.equal((await s.move(game.id, alice, "e2e4", 0)).data.game.clock.white, 170000);
  // Schwarz denkt zu lange: beim nächsten Lesen verloren
  s.tick(180000);
  const list = await s.call("GET", "/api/games", null, alice);
  const over = list.data.games[0];
  assert.equal(over.status, "over");
  assert.equal(over.result, "1-0");
  assert.equal(over.reason, "timeout");
  assert.deepEqual(over.clock, { white: 170000, black: 0 });
  assert.equal((await s.move(game.id, bob, "e7e5", 1)).status, 409);

  // Zug genau nach Ablauf wird nicht mehr angenommen
  const second = await s.start("Carol", "Dave", 180);
  s.tick(180000);
  const late = await s.move(second.id, s.t("Carol"), "e2e4", 0);
  assert.equal(late.status, 409);
  const after = await s.call("GET", "/api/games", null, s.t("Dave"));
  assert.equal(after.data.games[0].result, "0-1");
  assert.equal(after.data.games[0].reason, "timeout");
});

test("Einladungen: Eingaben, Grenzen, ablehnen, zurückziehen, verfallen", async () => {
  const s = await setup();
  const alice = s.t("Alice");
  assert.equal((await s.call("POST", "/api/games", { to: "Niemand", time: 300 }, alice)).status, 404);
  assert.equal((await s.call("POST", "/api/games", { to: "Alice", time: 300 }, alice)).status, 400);
  assert.equal((await s.call("POST", "/api/games", { to: "Bob", time: 99 }, alice)).status, 400);
  assert.equal((await s.call("POST", "/api/games", { to: "Bob", time: 300, color: "pink" }, alice)).status, 400);
  assert.equal((await s.call("POST", "/api/games", { to: "x", time: 300 }, alice)).status, 400);

  // höchstens 5 offene ausgehende Einladungen, keine doppelte Einladung
  const ids = [];
  for (const name of ["Bob", "Carol", "Dave", "Erin", "Frank"]) {
    const res = await s.call("POST", "/api/games", { to: name, time: 300, color: "random" }, alice);
    assert.equal(res.status, 200);
    ids.push(res.data.game.id);
  }
  assert.equal((await s.call("POST", "/api/games", { to: "Gina", time: 300 }, alice)).status, 429);
  // doppelt einladen geht nicht (Gegenseitig: siehe Test „Revanche“ – dort wird angenommen)
  assert.equal((await s.call("POST", "/api/games", { to: "Bob", time: 300 }, alice)).status, 409);
  // ablehnen (nur Eingeladener) und zurückziehen (nur Einladender)
  assert.equal((await s.call("POST", `/api/games/${ids[0]}/decline`, null, alice)).status, 403);
  assert.equal((await s.call("POST", `/api/games/${ids[0]}/decline`, null, s.t("Bob"))).data.game.status, "declined");
  assert.equal((await s.call("POST", `/api/games/${ids[1]}/cancel`, null, s.t("Carol"))).status, 403);
  assert.equal((await s.call("POST", `/api/games/${ids[1]}/cancel`, null, alice)).data.game.status, "cancelled");
  assert.equal((await s.call("POST", `/api/games/${ids[1]}/accept`, null, s.t("Carol"))).status, 409);
  // zufällige Farbe: einer ist Weiß, einer Schwarz
  const random = (await s.call("POST", `/api/games/${ids[2]}/accept`, null, s.t("Dave"))).data.game;
  assert.deepEqual([random.white.uuid, random.black.uuid].sort(), [s.users.Alice.uuid, s.users.Dave.uuid].sort());
  // nach 10 Minuten verfallen offene Einladungen
  s.tick(10 * 60 * 1000);
  assert.equal((await s.call("POST", `/api/games/${ids[3]}/accept`, null, s.t("Erin"))).status, 409);
  const list = await s.call("GET", "/api/games", null, alice);
  // (die angenommene 5-Minuten-Partie ist inzwischen auf Zeit verloren, die Einladungen sind verfallen)
  assert.deepEqual(list.data.games.map((g) => [g.status, g.reason]), [["over", "timeout"]]);
  assert.equal((await s.call("POST", "/api/games", { to: "Erin", time: 300 }, alice)).status, 200);
});

test("Partien: höchstens 3 gleichzeitig", async () => {
  const s = await setup();
  await s.start("Alice", "Bob");
  await s.start("Alice", "Carol");
  await s.start("Dave", "Alice");
  assert.equal((await s.call("POST", "/api/games", { to: "Erin", time: 300 }, s.t("Alice"))).status, 429);
  // Einladung an Alice kann sie nicht annehmen
  const invite = await s.call("POST", "/api/games", { to: "Alice", time: 300 }, s.t("Frank"));
  assert.equal(invite.status, 200);
  assert.equal((await s.call("POST", `/api/games/${invite.data.game.id}/accept`, null, s.t("Alice"))).status, 429);
});

test("Partien abfragen: eigener Topf, verdrängt die übrigen Anfragen nicht", async () => {
  const s = await setup();
  // mehr Abfragen als der allgemeine Topf (120/min) erlaubt – /api/friends bleibt trotzdem frei
  for (let i = 0; i < 130; i++) assert.equal((await s.call("GET", "/api/games", null, s.t("Alice"))).status, 200);
  assert.equal((await s.call("GET", "/api/friends", null, s.t("Alice"))).status, 200);
});

test("Partie: Verlassen und Verbindung verloren", async () => {
  const s = await setup();
  // Brett geschlossen / Welt verlassen: Aufgeben mit Grund "left"
  const game = await s.start("Alice", "Bob");
  const left = await s.call("POST", `/api/games/${game.id}/resign`, { reason: "left" }, s.t("Bob"));
  assert.equal(left.data.game.result, "1-0");
  assert.equal(left.data.game.reason, "left");

  // Absturz: Dave fragt 31 s lang nicht ab → Carol gewinnt beim nächsten Abfragen
  const second = await s.start("Carol", "Dave");
  await s.move(second.id, s.t("Carol"), "e2e4", 0);
  s.tick(20000);
  let list = await s.call("GET", "/api/games", null, s.t("Carol"));
  assert.equal(list.data.games[0].status, "active", "20 s ohne Lebenszeichen sind noch erlaubt");
  s.tick(11000);
  list = await s.call("GET", "/api/games", null, s.t("Carol"));
  assert.equal(list.data.games[0].status, "over");
  assert.equal(list.data.games[0].result, "1-0");
  assert.equal(list.data.games[0].reason, "abandoned");

  // wer regelmäßig abfragt, bleibt drin – auch wenn der Gegner lange nachdenkt
  const third = await s.start("Erin", "Frank", 600);
  for (let i = 0; i < 10; i++) {
    s.tick(15000);
    await s.call("GET", "/api/games", null, s.t("Erin"));
    await s.call("GET", "/api/games", null, s.t("Frank"));
  }
  const still = await s.call("GET", "/api/games", null, s.t("Erin"));
  assert.equal(still.data.games.find((g) => g.id === third.id).status, "active");
});

test("Revanche: beide fordern sich gegenseitig heraus → Partie startet", async () => {
  const s = await setup();
  const first = await s.start("Alice", "Bob");
  await s.call("POST", `/api/games/${first.id}/resign`, null, s.t("Bob"));
  // nach der Partie: Alice drückt zuerst „Revanche“, dann Bob
  const a = await s.call("POST", "/api/games", { to: "Bob", time: 180, color: "random" }, s.t("Alice"));
  assert.equal(a.status, 200);
  assert.equal(a.data.game.status, "invited");
  const again = await s.call("POST", "/api/games", { to: "Bob", time: 300, color: "white" }, s.t("Alice"));
  assert.equal(again.status, 409);
  assert.match(again.data.error, /schon herausgefordert/);
  const b = await s.call("POST", "/api/games", { to: "Alice", time: 600, color: "white" }, s.t("Bob"));
  assert.equal(b.status, 200, JSON.stringify(b.data));
  assert.equal(b.data.game.id, a.data.game.id, "Bobs Herausforderung nimmt Alices Einladung an");
  assert.equal(b.data.game.status, "active");
  assert.equal(b.data.game.time, 180, "es gelten Alices Einstellungen");
  const running = await s.call("POST", "/api/games", { to: "Alice", time: 300, color: "random" }, s.t("Bob"));
  assert.equal(running.status, 409);
  assert.match(running.data.error, /spielt schon eine Partie/);
});

test("Bedenkzeit 1 Minute (Bullet)", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob", 60);
  assert.equal(game.time, 60);
  assert.deepEqual(game.clock, { white: 60000, black: 60000 });
  assert.equal((await s.call("POST", "/api/games", { to: "Dave", time: 120 }, s.t("Carol"))).status, 400, "2 Minuten gibt es nicht");
});
