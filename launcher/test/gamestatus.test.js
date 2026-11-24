// Statusdatei der Mod für Discord: lesen, beobachten, Rückfall auf den Friends-Server nur ohne Statusdatei.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { parseStatus, readStatus, GameStatusHub } = require("../src/core/gamestatus");

const tmp = () => fs.mkdtempSync(path.join(os.tmpdir(), "mytic-status-"));
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
/** wartet, bis cond() wahr ist (höchstens ms) */
async function until(cond, ms = 2000) {
  const end = Date.now() + ms;
  while (!cond()) {
    if (Date.now() > end) throw new Error("Zeit abgelaufen");
    await wait(10);
  }
}
/** schreibt wie die Mod: erst .tmp, dann umbenennen */
function writeStatus(file, data) {
  fs.writeFileSync(`${file}.tmp`, JSON.stringify(data));
  fs.renameSync(`${file}.tmp`, file);
}

test("Statusdatei prüfen: nur gültige Inhalte, Texte begrenzt", () => {
  assert.deepEqual(parseStatus('{"place":"game","server":"play.hypixel.net","version":"1.21.11","ts":5}'),
    { place: "game", server: "play.hypixel.net", version: "1.21.11", ts: 5 });
  assert.deepEqual(parseStatus('{"place":"game","server":null,"version":"1.8.9","ts":1}'), { place: "game", server: null, version: "1.8.9", ts: 1 });
  assert.equal(parseStatus('{"place":"game","server":"x"}'), null); // ohne Zeit
  assert.equal(parseStatus('{"place":"launcher","ts":1}'), null);
  assert.equal(parseStatus("[1,2]"), null);
  assert.equal(parseStatus("kaputt"), null);
  assert.equal(parseStatus('{"place":"game","server":"' + "a".repeat(500) + '","ts":1}').server.length, 128);
  assert.equal(parseStatus('{"place":"game","server":"  ","ts":1}').server, null);
});

test("Statusdatei lesen: fehlt, zu groß, gültig", async () => {
  const dir = tmp();
  const file = path.join(dir, "s.json");
  assert.equal(await readStatus(file), null);
  fs.writeFileSync(file, " ".repeat(5000));
  assert.equal(await readStatus(file), null);
  writeStatus(file, { place: "game", server: "Einzelspieler", version: "26.1", ts: 7 });
  assert.equal((await readStatus(file)).server, "Einzelspieler");
});

test("Beobachten: Änderungen kommen sofort (fs.watch) und ohne Watch über das regelmäßige Lesen", async () => {
  for (const noWatch of [false, true]) {
    const dir = tmp();
    const seen = [];
    const fsImpl = noWatch ? { ...fs, promises: fs.promises, watch: () => { throw new Error("kein watch"); } } : fs;
    const hub = new GameStatusHub({ dir, interval: noWatch ? 30 : 60000, fsImpl, onChange: (s) => seen.push(s && s.server) });
    const file = hub.add(1);
    assert.equal(file, path.join(dir, "game-1.json"));
    assert.equal(hub.supported(), false);
    writeStatus(file, { place: "game", server: null, version: "1.21.11", ts: 1 });
    await until(() => seen.length === 1);
    writeStatus(file, { place: "game", server: "play.example.net", version: "1.21.11", ts: 2 });
    await until(() => seen.length === 2);
    // nur die Zeit neu (Lebenszeichen der Mod): keine Meldung
    writeStatus(file, { place: "game", server: "play.example.net", version: "1.21.11", ts: 3 });
    await hub.check(1);
    await wait(noWatch ? 80 : 200);
    assert.deepEqual(seen, [null, "play.example.net"]);
    assert.equal(hub.supported(), true);
    assert.equal(hub.current().ts, 3);
    hub.remove(1);
    assert.equal(fs.existsSync(file), false, "Datei wird beim Beenden gelöscht");
    hub.close();
  }
});

test("Mehrere Spiele: der neueste Stand zählt; alte Datei beim Start gelöscht", async () => {
  const dir = tmp();
  fs.writeFileSync(path.join(dir, "game-2.json"), JSON.stringify({ place: "game", server: "alt", ts: 1 }));
  const hub = new GameStatusHub({ dir, interval: 60000 });
  const a = hub.add(1);
  const b = hub.add(2);
  assert.equal(fs.existsSync(b), false);
  writeStatus(a, { place: "game", server: "a.net", version: "1.21.11", ts: 10 });
  writeStatus(b, { place: "game", server: "b.net", version: "1.8.9", ts: 20 });
  await hub.check(1);
  await hub.check(2);
  assert.equal(hub.current().server, "b.net");
  hub.remove(2);
  assert.equal(hub.current().server, "a.net");
  hub.close();
});

test("Rückfall: Friends-Server nur ohne Statusdatei fragen, alle pollInterval", async () => {
  const dir = tmp();
  let polls = 0;
  const hub = new GameStatusHub({ dir, interval: 60000, pollInterval: 40, poll: async () => { polls++; } });
  // ohne Spiel: nie
  assert.equal(await hub.pollOnce(), false);
  const file = hub.add(1);
  await until(() => polls >= 2);
  // Mod schreibt die Datei → keine Abfragen mehr
  writeStatus(file, { place: "game", server: "x.net", version: "1.21.11", ts: 1 });
  await hub.check(1);
  const before = polls;
  await wait(150);
  assert.equal(polls, before);
  assert.equal(await hub.pollOnce(), false);
  // Spiel zu → Timer aus
  hub.remove(1);
  assert.equal(hub.pollTimer, null);
  // Fehler beim Abfragen werden nur protokolliert
  const lines = [];
  const failing = new GameStatusHub({ dir, interval: 60000, pollInterval: 60000, poll: async () => { throw new Error("offline"); }, log: (l) => lines.push(l) });
  failing.add(5);
  assert.equal(await failing.pollOnce(), true);
  assert.match(lines[0], /offline/);
  failing.close();
});
