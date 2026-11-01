import { test } from "node:test";
import assert from "node:assert/strict";
import { openDb } from "../src/node-server.js";
import { createApi } from "../src/api.js";

// ------------------------------------------------------------------ Schiffe versenken (Battleship)

const NAMES = ["Alice", "Bob", "Carol", "Dave"];
// waagrechte Flotte (Zeilen 0, 2, 4, 6, 8 ab Spalte 0)
const FLEET_A = [[0, 1, 2, 3, 4], [20, 21, 22, 23], [40, 41, 42], [60, 61, 62], [80, 81]];
// senkrechte Flotte (Spalten 9, 7, 5, 3, 9)
const FLEET_B = [[9, 19, 29, 39, 49], [7, 17, 27, 37], [5, 15, 25], [63, 73, 83], [89, 99]];
const CELLS_B = FLEET_B.flat();

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
  const invite = (from, to, opts = {}) => call("POST", "/api/games", { to, game: "ships", moveTime: 0, color: "white", ...opts }, t(from));
  /** Einladen und annehmen, Einladender ist Weiß (schießt zuerst). */
  const start = async (w, b, opts = {}) => {
    const inv = await invite(w, b, opts);
    assert.equal(inv.status, 200, JSON.stringify(inv.data));
    const accepted = await call("POST", `/api/games/${inv.data.game.id}/accept`, null, t(b));
    assert.equal(accepted.status, 200, JSON.stringify(accepted.data));
    return accepted.data.game;
  };
  const fleet = (id, name, ships) => call("POST", `/api/games/${id}/fleet`, { ships }, t(name));
  /** Partie starten und beide Flotten setzen (w: FLEET_A, b: FLEET_B). */
  const battle = async (w, b, opts = {}) => {
    const game = await start(w, b, opts);
    assert.equal((await fleet(game.id, w, FLEET_A)).status, 200);
    const res = await fleet(game.id, b, FLEET_B);
    assert.equal(res.status, 200, JSON.stringify(res.data));
    return res.data.game;
  };
  const shoot = (id, name, cell, ply) => call("POST", `/api/games/${id}/move`, { move: cell, ply }, t(name));
  const view = async (name, id) => (await call("GET", "/api/games", null, t(name))).data.games.find((g) => g.id === id);
  return { call, users, t, invite, start, fleet, battle, shoot, view, tick: (ms) => { clock += ms; } };
}

/** Enthält die Antwort (als Text) die Flotte bzw. irgendeine Zelle davon als Schiff? */
function leaks(game, fleet) {
  const text = JSON.stringify(game);
  return fleet.some((ship) => text.includes(JSON.stringify(ship)));
}

test("Schiffe versenken: Flotte prüfen (Längen, Berührung, diagonal, Knick, Rand, Überschneidung)", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob");
  assert.equal(game.kind, "ships");
  assert.equal(game.ships.phase, "setup");
  assert.equal(game.ships.setupLeft, 120000);
  const bad = async (ships, message) => {
    const res = await s.fleet(game.id, "Alice", ships);
    assert.equal(res.status, 400, JSON.stringify(ships));
    if (message) assert.match(res.data.error, message);
  };
  const swap = (i, ship) => FLEET_A.map((x, j) => (j === i ? ship : x));
  await bad(FLEET_A.slice(0, 4), /5 Schiffen/);
  await bad(null, /5 Schiffen/);
  await bad(swap(4, [80, 81, 82]), /Längen 5, 4, 3, 3 und 2/);
  await bad(swap(3, [60, 61]), /Längen/);
  await bad(swap(4, [5, 15]), /nicht berühren/);           // direkt neben dem Fünfer (Feld 4)
  await bad(swap(4, [15, 16]), /nicht berühren/);          // nur diagonal an Feld 4
  await bad(swap(2, [40, 41, 51]), /gerade/);              // geknickt
  await bad(swap(2, [40, 42, 44]), /gerade/);              // Lücken
  await bad(swap(4, [99, 100]), /außerhalb/);
  await bad(swap(4, [-1, 0]), /außerhalb/);
  await bad(swap(4, [69, 70]), /außerhalb/);               // über das Zeilenende
  await bad(swap(4, [0, 10]), /überschneiden/);
  await bad(swap(4, [80.5, 81]), /Ungültiges Feld/);
  assert.equal((await s.view("Alice", game.id)).ships.myReady, false);
  // gültig, Reihenfolge der Schiffe und Felder egal
  const ok = await s.fleet(game.id, "Alice", [[81, 80], ...FLEET_A.slice(0, 4).reverse()]);
  assert.equal(ok.status, 200, JSON.stringify(ok.data));
  assert.equal(ok.data.game.ships.myReady, true);
  assert.deepEqual(ok.data.game.ships.myFleet, [[80, 81], ...FLEET_A.slice(0, 4).reverse()]);
  // endgültig: zweites Setzen geht nicht
  assert.equal((await s.fleet(game.id, "Alice", FLEET_A)).status, 409);
  // Flotte nur bei Schiffe versenken
  const xo = await s.call("POST", "/api/games", { to: "Dave", game: "xo", rounds: 1, moveTime: 0 }, s.t("Carol"));
  await s.call("POST", `/api/games/${xo.data.game.id}/accept`, null, s.t("Dave"));
  assert.equal((await s.fleet(xo.data.game.id, "Carol", FLEET_A)).status, 409);
});

test("Schiffe versenken: Schlacht erst, wenn beide Flotten stehen; gegnerische Flotte bleibt geheim", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob");
  let a = (await s.fleet(game.id, "Alice", FLEET_A)).data.game;
  assert.equal(a.ships.phase, "setup");
  let b = await s.view("Bob", game.id);
  assert.equal(b.ships.phase, "setup");
  assert.equal(b.ships.myReady, false);
  assert.equal(b.ships.opReady, true);
  assert.deepEqual(b.ships.myFleet, []);
  assert.equal(b.ships.opFleet, null);
  assert.equal(leaks(b, FLEET_A), false, "Bob sieht Alices Flotte nicht");
  assert.equal((await s.shoot(game.id, "Alice", 9, 0)).status, 409, "noch keine Schlacht");

  b = (await s.fleet(game.id, "Bob", FLEET_B)).data.game;
  assert.equal(b.ships.phase, "battle");
  assert.equal(b.ships.turn, "w");
  assert.equal(b.turn, "w");
  assert.equal(b.ships.setupLeft, null);
  assert.deepEqual(b.ships.myFleet, FLEET_B);
  assert.equal(b.ships.opFleet, null);
  assert.equal(leaks(b, FLEET_A), false);
  a = await s.view("Alice", game.id);
  assert.deepEqual(a.ships.myFleet, FLEET_A);
  assert.equal(a.ships.opFleet, null);
  assert.equal(leaks(a, FLEET_B), false, "Alice sieht Bobs Flotte nicht");
  assert.equal(JSON.stringify(a).includes("fleets"), false, "kein roher Spielstand in der Antwort");
});

test("Schiffe versenken: Treffer = nochmal, Wasser = Gegner, versenkt zeigt nur dieses Schiff samt Wasser drumherum", async () => {
  const s = await setup();
  const game = await s.battle("Alice", "Bob");
  assert.equal((await s.shoot(game.id, "Bob", 0, 0)).status, 409, "Bob ist nicht am Zug");
  assert.equal((await s.shoot(game.id, "Alice", 9, 1)).status, 409, "ply passt nicht");
  assert.equal((await s.shoot(game.id, "Alice", 100, 0)).status, 400);
  // Treffer: Alice bleibt dran
  let a = (await s.shoot(game.id, "Alice", 89, 0)).data.game;
  assert.deepEqual(a.ships.myShots, { 89: "h" });
  assert.equal(a.ships.turn, "w");
  assert.deepEqual(a.ships.lastShot, { by: "me", cell: 89, result: "h" });
  assert.deepEqual(a.ships.theirSunk, []);
  // schon beschossen
  const again = await s.shoot(game.id, "Alice", 89, 1);
  assert.equal(again.status, 400);
  assert.equal(again.data.error, "Feld schon beschossen");
  // versenkt: nur dieses Schiff wird sichtbar, Umgebung automatisch Wasser
  a = (await s.shoot(game.id, "Alice", 99, 1)).data.game;
  assert.deepEqual(a.ships.lastShot, { by: "me", cell: 99, result: "s" });
  assert.deepEqual(a.ships.theirSunk, [[89, 99]]);
  assert.deepEqual(a.ships.myShots, { 78: "a", 79: "a", 88: "a", 89: "h", 98: "a", 99: "h" });
  assert.equal(a.ships.opFleet, null);
  assert.equal(leaks(a, FLEET_B.slice(0, 4)), false, "übrige Schiffe bleiben geheim");
  assert.equal(a.ships.turn, "w");
  assert.equal((await s.shoot(game.id, "Alice", 88, 2)).status, 400, "automatisches Wasser kann man nicht beschießen");
  // Wasser: Bob ist dran; ply zählt nur echte Schüsse
  a = (await s.shoot(game.id, "Alice", 0, 2)).data.game;
  assert.equal(a.ships.myShots[0], "m");
  assert.equal(a.ships.turn, "b");
  assert.deepEqual(a.ships.lastShot, { by: "me", cell: 0, result: "m" });
  assert.equal((await s.shoot(game.id, "Alice", 1, 3)).status, 409);
  let b = await s.view("Bob", game.id);
  assert.deepEqual(b.ships.lastShot, { by: "op", cell: 0, result: "m" });
  assert.deepEqual(b.ships.opShots, a.ships.myShots);
  assert.deepEqual(b.ships.mySunk, [[89, 99]]);
  assert.deepEqual(b.ships.myShots, {});
  assert.deepEqual(b.ships.theirSunk, []);
  assert.equal(leaks(b, FLEET_A), false);
  b = (await s.shoot(game.id, "Bob", 0, 0)).data.game;
  assert.equal(b.ships.myShots[0], "h");
  assert.equal(b.ships.turn, "b");
  b = (await s.shoot(game.id, "Bob", 55, 1)).data.game;
  assert.equal(b.ships.turn, "w");
  // kein Remis-Angebot
  assert.equal((await s.call("POST", `/api/games/${game.id}/draw`, { action: "offer" }, s.t("Bob"))).status, 409);
});

test("Schiffe versenken: alle versenkt → gewonnen, erst dann ist die gegnerische Flotte sichtbar", async () => {
  const s = await setup();
  const game = await s.battle("Alice", "Bob");
  let res;
  for (let i = 0; i < CELLS_B.length; i++) {
    const b = await s.view("Bob", game.id);
    assert.equal(b.ships.opFleet, null);
    assert.equal(leaks(b, FLEET_A), false, "vor dem Ende nie Alices Flotte bei Bob");
    res = await s.shoot(game.id, "Alice", CELLS_B[i], i);
    assert.equal(res.status, 200, JSON.stringify(res.data));
    if (i < CELLS_B.length - 1) assert.equal(res.data.game.status, "active");
  }
  const a = res.data.game;
  assert.equal(a.status, "over");
  assert.equal(a.result, "1-0");
  assert.equal(a.reason, "sunk");
  assert.equal(a.ships.theirSunk.length, 5);
  assert.deepEqual(a.ships.opFleet, FLEET_B);
  assert.equal(a.ships.moveLeft, null);
  const b = await s.view("Bob", game.id);
  assert.deepEqual(b.ships.opFleet, FLEET_A);
  assert.equal(b.ships.mySunk.length, 5);
  assert.equal((await s.shoot(game.id, "Alice", 50, 17)).status, 409);
  assert.equal((await s.call("GET", "/api/stats", null, s.t("Alice"))).data.chess.played, 0);
});

test("Schiffe versenken: Zeit je Schuss abgelaufen → Gegner ist dran", async () => {
  const s = await setup();
  const game = await s.battle("Alice", "Bob", { moveTime: 10 });
  assert.equal(game.moveTime, 10);
  assert.equal(game.ships.moveLeft, 10000);
  s.tick(4000);
  assert.equal((await s.view("Bob", game.id)).ships.moveLeft, 6000);
  s.tick(6001);
  let a = await s.view("Alice", game.id);
  assert.equal(a.status, "active");
  assert.equal(a.ships.turn, "b");
  assert.equal(a.ships.moveLeft, 9999, "nächster Schuss beginnt mit dem Ablauf");
  assert.deepEqual(a.ships.myShots, {}, "kein Punktverlust");
  assert.equal((await s.shoot(game.id, "Alice", 9, 0)).status, 409);
  // mehrere Abläufe auf einmal nachgeholt
  s.tick(20000);
  a = await s.view("Alice", game.id);
  assert.equal(a.ships.turn, "b");
  assert.equal(a.ships.moveLeft, 9999);
  assert.equal((await s.shoot(game.id, "Bob", 0, 0)).status, 200);
  // ohne Zeitlimit kein Ablauf
  const free = await s.battle("Carol", "Dave");
  s.tick(20000);
  const f = await s.view("Carol", free.id);
  assert.equal(f.ships.turn, "w");
  assert.equal(f.ships.moveLeft, null);
});

test("Schiffe versenken: Aufstellzeit 120 s – ohne Flotte verloren, beide ohne → abgebrochen", async () => {
  const s = await setup();
  const game = await s.start("Alice", "Bob");
  const other = await s.start("Carol", "Dave", { color: "black" });
  await s.fleet(game.id, "Bob", FLEET_B);
  // beide fragen weiter ab (sonst greift nach 30 s „Verbindung verloren“)
  for (let i = 0; i < 6; i++) {
    s.tick(20000);
    for (const name of NAMES) await s.view(name, game.id);
  }
  let a = await s.view("Alice", game.id);
  assert.equal(a.status, "active");
  assert.equal(a.ships.setupLeft, 0);
  s.tick(1);
  a = await s.view("Alice", game.id);
  assert.equal(a.status, "over");
  assert.equal(a.result, "0-1", "Alice (Weiß) hat keine Flotte gesetzt");
  assert.equal(a.reason, "setup");
  assert.deepEqual(a.ships.opFleet, FLEET_B);
  const c = await s.view("Carol", other.id);
  assert.equal(c.status, "cancelled");
  assert.equal(c.ships.opFleet, null);
  assert.equal((await s.fleet(game.id, "Alice", FLEET_A)).status, 409);
});

test("Schiffe versenken: Eingaben, Aufgeben, Verlassen, Verbindung verloren", async () => {
  const s = await setup();
  assert.equal((await s.invite("Alice", "Bob", { moveTime: 5 })).status, 400);
  const bad = await s.invite("Alice", "Bob", { moveTime: undefined });
  assert.equal(bad.status, 400);
  assert.equal(bad.data.error, "Ungültige Zeit je Schuss");
  const inv = await s.invite("Alice", "Bob", { moveTime: 20 });
  assert.equal(inv.data.game.rounds, null);
  assert.equal(inv.data.game.ships.phase, "setup");
  assert.equal(inv.data.game.ships.setupLeft, null);
  await s.call("POST", `/api/games/${inv.data.game.id}/cancel`, null, s.t("Alice"));
  const game = await s.battle("Alice", "Bob");
  const res = await s.call("POST", `/api/games/${game.id}/resign`, null, s.t("Bob"));
  assert.equal(res.data.game.result, "1-0");
  assert.equal(res.data.game.reason, "resign");
  assert.deepEqual(res.data.game.ships.opFleet, FLEET_A, "nach dem Ende sichtbar");
  const left = await s.start("Carol", "Dave");
  const r2 = await s.call("POST", `/api/games/${left.id}/resign`, { reason: "left" }, s.t("Carol"));
  assert.equal(r2.data.game.result, "0-1");
  assert.equal(r2.data.game.reason, "left");
  const gone = await s.start("Alice", "Bob");
  s.tick(31000);
  const g = await s.view("Alice", gone.id);
  assert.equal(g.status, "over");
  assert.equal(g.reason, "abandoned");
});
