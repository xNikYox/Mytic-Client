import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { openDb } from "../src/node-server.js";
import { createApi } from "../src/api.js";
import * as Coins from "../src/coins.js";
import { restoreLocal } from "../scripts/restore-ownership.mjs";

const DAY = 24 * 60 * 60 * 1000;
const ADMIN_KEY = "test-admin-key-1234567890";

async function setup({ names = ["Alice", "Bob", "Carol"], file = ":memory:", limits } = {}) {
  // 2001-09-09 01:46:40 UTC
  let clock = 1_000_000_000_000;
  const db = openDb(file);
  const api = createApi(db, { fetch: async () => new Response(null, { status: 404 }), now: () => clock, allowOffline: true, adminKey: ADMIN_KEY, limits });
  const call = async (method, p, data, token, adminKey) => {
    const res = await api(new Request(`http://x${p}`, {
      method,
      headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}), ...(adminKey ? { "x-admin-key": adminKey } : {}) },
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
  const admin = (p, data) => call(data === undefined ? "GET" : "POST", p, data, null, ADMIN_KEY);
  const give = async (name, delta, reason = "Test") => {
    const res = await admin("/api/admin/coins", { player: name, delta, reason });
    assert.equal(res.status, 200, JSON.stringify(res.data));
    return res.data;
  };
  const balance = async (name) => (await call("GET", "/api/coins", null, t(name))).data.balance;
  const beat = (name, data = {}) => call("POST", "/api/presence", { place: "game", ...data }, t(name));
  const friends = async (x, y) => {
    await call("POST", "/api/friends/request", { name: y }, t(x));
    await call("POST", "/api/friends/accept", { uuid: users[x].uuid }, t(y));
  };
  let n = 0;
  const key = () => `kauf-${++n}-abcdefgh`;
  const buy = (name, what, extra = {}) => call("POST", "/api/shop/buy", { ...what, idemKey: key(), ...extra }, t(name));
  return { call, users, t, admin, give, balance, beat, friends, buy, key, db, api, tick: (ms) => { clock += ms; }, now: () => clock };
}

test("Preise: Standard je Art, Event-Aufschläge, Sets mit Rabatt", () => {
  const prices = Coins.defaultPrices();
  assert.equal(prices.get("cape:galaxie").price, 500);
  assert.equal(prices.get("cape:lebkuchen").price, 630); // Winterzauber 2026: +25 %
  assert.equal(prices.get("cape:kaminabend").price, 750); // Winterzauber II (HD): +50 %
  assert.equal(prices.get("wings:rabe").price, 800);
  assert.equal(prices.get("aura:frost").price, 300);
  assert.equal(prices.get("shield:void").price, 400);
  assert.equal(prices.get("acc:schal").price, 500);
  assert.equal(prices.get("minime").price, 600);
  assert.equal(prices.get("nametag").price, 250);
  assert.equal(prices.get("nametag:hohoho").price, 100);
  const sets = Coins.catalogSets(prices);
  assert.deepEqual(sets.map((x) => x.id), ["winterzauber-2026", "winterzauber-ii"]);
  assert.equal(Coins.bundlePrice(1000, 20), 800);
  assert.deepEqual([1, 2, 7, 8, 30].map(Coins.dailyAmount), [10, 15, 40, 40, 40]);
  assert.equal(Coins.dayBefore("2026-03-01"), "2026-02-28");
});

test("Coins: Anmeldung nötig, Kontostand 0, Lebenszeichen meldet coins1", async () => {
  const s = await setup();
  assert.equal((await s.call("GET", "/api/coins")).status, 401);
  assert.equal((await s.call("POST", "/api/shop/buy", { item: "cape:galaxie", idemKey: s.key() })).status, 401);
  const res = await s.call("GET", "/api/coins?shop=1", null, s.t("Alice"));
  assert.equal(res.data.balance, 0);
  assert.equal(res.data.shop.items["cape:galaxie"], 500);
  assert.equal(res.data.shop.v, res.data.pv);
  const beat = await s.call("POST", "/api/presence", { place: "launcher" }, s.t("Alice"));
  assert.ok(beat.data.caps.includes("coins1"));
  // Launcher: Kontostand ja, aber kein Tagesbonus
  assert.deepEqual(beat.data.coins, { balance: 0, pv: res.data.pv, tx: 0 });
});

test("Tagesbonus: einmal je Ortstag, Serie bis 40, Abbruch nach verpasstem Tag", async () => {
  const s = await setup();
  // Uhr: 2001-09-09 01:46 UTC; tz +120 → 03:46 Ortszeit
  let beat = await s.beat("Alice", { tz: 120 });
  assert.deepEqual(beat.data.coins.daily, { amount: 10, streak: 1, day: "2001-09-09" });
  assert.equal(beat.data.coins.balance, 10);
  // gleicher Tag: nichts mehr
  beat = await s.beat("Alice", { tz: 120 });
  assert.equal(beat.data.coins.daily, undefined);
  assert.equal(beat.data.coins.balance, 10);
  // Zeitzone in der Spielzeit-Meldung (ältere Felder) zählt auch – aber ein Tag zurück bringt nichts
  beat = await s.beat("Alice", { tz: -600 });
  assert.equal(beat.data.coins.daily, undefined);
  // sieben Tage in Folge: 10, 15, 20, 25, 30, 35, 40, dann 40
  let total = 10;
  for (let i = 2; i <= 8; i++) {
    s.tick(DAY);
    beat = await s.beat("Alice", { tz: 120 });
    assert.equal(beat.data.coins.daily.streak, i);
    assert.equal(beat.data.coins.daily.amount, Math.min(40, 10 + 5 * (i - 1)));
    total += beat.data.coins.daily.amount;
  }
  assert.equal(beat.data.coins.balance, total);
  // Tag verpasst → wieder 10
  s.tick(2 * DAY);
  beat = await s.beat("Alice", { tz: 120 });
  assert.deepEqual([beat.data.coins.daily.amount, beat.data.coins.daily.streak], [10, 1]);
  const info = (await s.call("GET", "/api/coins?tz=120", null, s.t("Alice"))).data;
  assert.deepEqual(info.daily, { claimed: true, streak: 1, amount: 10, max: 40 });
  assert.equal(info.tx[0].kind, "daily");
  assert.equal(info.tx[0].reason, "Tag 1");
  // nächster Tag noch nicht abgeholt: Vorschau 15
  s.tick(DAY);
  assert.deepEqual((await s.call("GET", "/api/coins?tz=120", null, s.t("Alice"))).data.daily, { claimed: false, streak: 1, amount: 15, max: 40 });
  // nur im Spiel, nicht im Launcher
  assert.equal((await s.call("POST", "/api/presence", { place: "launcher", tz: 120 }, s.t("Alice"))).data.coins.daily, undefined);
  // Zeitzonen-Trick: Tage müssen streng steigen – höchstens einmalig ein Vorsprung (Tag ≤ UTC-Tag + 1), danach je Tag einer
  const b1 = await s.beat("Bob", { tz: -840 });
  const b2 = await s.beat("Bob", { tz: 840 });
  const b3 = await s.beat("Bob", { tz: 840 });
  assert.ok(b1.data.coins.daily && b2.data.coins.daily && !b3.data.coins.daily);
  s.tick(DAY / 2);
  assert.ok((await s.beat("Bob", { tz: 840 })).data.coins.daily);
  for (const tz of [840, -840, 0, 840]) assert.equal((await s.beat("Bob", { tz })).data.coins.daily, undefined);
  s.tick(DAY / 2);
  for (const tz of [840, -840, 0]) assert.equal((await s.beat("Bob", { tz })).data.coins.daily, undefined);
});

test("Shop: Teil kaufen, zu wenig Coins, schon gekauft, Preis geändert", async () => {
  const s = await setup();
  let res = await s.buy("Alice", { item: "cape:galaxie" });
  assert.equal(res.status, 402);
  assert.match(res.data.error, /fehlen 500/);
  await s.give("Alice", 1200);
  assert.equal((await s.buy("Alice", { item: "cape:gibtsnicht" })).status, 404);
  assert.equal((await s.call("POST", "/api/shop/buy", { item: "cape:galaxie" }, s.t("Alice"))).status, 400); // ohne idemKey
  assert.equal((await s.buy("Alice", { item: "cape:galaxie" }, { price: 400 })).status, 409);
  res = await s.buy("Alice", { item: "cape:galaxie" }, { price: 500 });
  assert.equal(res.status, 200, JSON.stringify(res.data));
  assert.deepEqual([res.data.balance, res.data.price, res.data.items], [700, 500, ["cape:galaxie"]]);
  assert.ok(res.data.owned.includes("cape:galaxie"));
  assert.equal((await s.buy("Alice", { item: "cape:galaxie" })).status, 409);
  // gekauft = freigeschaltet: auswählen und andere sehen es
  const cos = await s.call("POST", "/api/cosmetics", { cape: "galaxie" }, s.t("Alice"));
  assert.equal(cos.data.cape, "galaxie");
  // Wildcard-Freischaltung: schon „gekauft“
  const tx = (await s.call("GET", "/api/coins", null, s.t("Alice"))).data.tx;
  assert.deepEqual(tx.map((x) => [x.kind, x.delta, x.balance]), [["purchase", -500, 700], ["grant", 1200, 1200]]);
  assert.equal(tx[0].ref, "cape:galaxie");
  assert.equal(tx[1].admin, undefined); // Spieler sehen nicht, welcher Admin
});

test("Shop: Set kaufen – nur fehlende Teile, mit Rabatt; Event-Sets immer kaufbar", async () => {
  const s = await setup();
  const shop = (await s.call("GET", "/api/shop", null, s.t("Alice"))).data;
  const set = shop.sets.find((x) => x.id === "winterzauber-2026");
  assert.equal(set.price, Coins.bundlePrice(set.sum, 20));
  await s.give("Alice", 20000);
  // ein Teil schon gekauft → das Set kostet nur noch die übrigen (mit Rabatt)
  await s.buy("Alice", { item: "cape:lebkuchen" });
  const before = await s.balance("Alice");
  const expected = Coins.bundlePrice(set.sum - shop.items["cape:lebkuchen"], 20);
  const res = await s.buy("Alice", { set: "winterzauber-2026" }, { price: expected });
  assert.equal(res.status, 200, JSON.stringify(res.data));
  assert.equal(res.data.items.length, set.items.length - 1);
  assert.equal(await s.balance("Alice"), before - expected);
  assert.equal((await s.buy("Alice", { set: "winterzauber-2026" })).status, 409);
  assert.equal((await s.buy("Alice", { set: "gibtsnicht" })).status, 404);
  // Set-Kauf steht als ein Eintrag mit allen Teilen im Verlauf
  const tx = (await s.call("GET", "/api/coins", null, s.t("Alice"))).data.tx[0];
  assert.equal(tx.ref, "set:winterzauber-2026");
  assert.equal(tx.items.length, set.items.length - 1);
});

test("Shop: Idempotenz und gleichzeitige Käufe (kein doppeltes Abbuchen)", async () => {
  const s = await setup();
  await s.give("Alice", 1000);
  // derselbe Kauf 5× gleichzeitig mit demselben Schlüssel: einmal abgebucht, alle bekommen dasselbe Ergebnis
  const key = s.key();
  const same = await Promise.all(Array.from({ length: 5 }, () => s.call("POST", "/api/shop/buy", { item: "cape:galaxie", idemKey: key }, s.t("Alice"))));
  assert.ok(same.every((r) => r.status === 200), JSON.stringify(same.map((r) => r.data)));
  assert.equal(same.filter((r) => !r.data.repeated).length, 1);
  assert.equal(await s.balance("Alice"), 500);
  // Wiederholung später: gleiches Ergebnis, nichts abgebucht
  const again = await s.call("POST", "/api/shop/buy", { item: "cape:galaxie", idemKey: key }, s.t("Alice"));
  assert.deepEqual([again.status, again.data.repeated, again.data.balance], [200, true, 500]);
  // 500 Coins, viele verschiedene Käufe gleichzeitig (je 300–500): höchstens so viel, wie das Guthaben hergibt
  const items = ["aura:frost", "aura:glut", "aura:runen", "shield:void", "shield:lava", "cape:mond", "cape:ozean"];
  const results = await Promise.all(items.map((item) => s.buy("Alice", { item })));
  const ok = results.filter((r) => r.status === 200);
  const spent = ok.reduce((n, r) => n + r.data.price, 0);
  assert.ok(ok.length >= 1 && spent <= 500, `${ok.length} Käufe für ${spent}`);
  assert.ok(results.every((r) => r.status === 200 || r.status === 402));
  assert.equal(await s.balance("Alice"), 500 - spent);
  // Buchungen und Freischaltungen passen zusammen
  const rows = await s.db.all("SELECT SUM(delta) AS sum FROM coin_tx WHERE uuid = ?", [s.users.Alice.uuid]);
  assert.equal(rows[0].sum, 500 - spent);
  const grants = await s.db.all("SELECT item FROM grants WHERE uuid = ?", [s.users.Alice.uuid]);
  assert.equal(grants.length, 1 + ok.length);
  // dasselbe Teil gleichzeitig mit verschiedenen Schlüsseln: nur einmal gekauft
  await s.give("Bob", 5000);
  const dup = await Promise.all(Array.from({ length: 4 }, () => s.buy("Bob", { item: "wings:rabe" })));
  assert.equal(dup.filter((r) => r.status === 200).length, 1);
  assert.equal(await s.balance("Bob"), 4200);
});

test("Geschenke: nur Freunde, mindestens 10, nicht an sich selbst, Tageslimit, Hinweis beim Empfänger", async () => {
  const s = await setup();
  const gift = (from, to, amount, extra = {}) => s.call("POST", "/api/coins/gift", { to, amount, ...extra }, s.t(from));
  await s.give("Alice", 2000);
  assert.equal((await gift("Alice", "Bob", 50)).status, 403); // noch keine Freunde
  await s.friends("Alice", "Bob");
  // Empfänger kennt seinen letzten Stand aus dem Lebenszeichen
  const start = (await s.beat("Bob")).data.coins.tx;
  assert.equal((await gift("Alice", "Bob", 5)).status, 400);
  assert.equal((await gift("Alice", "Bob", 10.5)).status, 400);
  assert.equal((await gift("Alice", "Alice", 50)).status, 400);
  assert.equal((await gift("Alice", "Niemand", 50)).status, 404);
  let res = await gift("Alice", "Bob", 120);
  assert.equal(res.status, 200, JSON.stringify(res.data));
  assert.deepEqual([res.data.balance, res.data.sentToday, res.data.to], [1880, 120, "Bob"]);
  // Hinweis für Bob mit dem nächsten Lebenszeichen
  const beat = (await s.beat("Bob", { coinTx: start })).data.coins;
  assert.equal(beat.balance, 120 + 10); // + Tagesbonus vom ersten Lebenszeichen
  assert.deepEqual(beat.events.map((e) => [e.kind, e.delta, e.from]), [["gift_in", 120, "Alice"]]);
  // Tageslimit 500 (UTC): 120 + 380 geht, dann nichts mehr
  assert.equal((await gift("Alice", "Bob", 400)).status, 429);
  assert.equal((await gift("Alice", "Bob", 380)).status, 200);
  assert.equal((await gift("Alice", "Bob", 10)).status, 429);
  // nächster Tag: wieder möglich; idemKey verhindert doppeltes Schenken
  s.tick(DAY);
  const key = s.key();
  const both = await Promise.all([gift("Alice", "Bob", 100, { idemKey: key }), gift("Alice", "Bob", 100, { idemKey: key })]);
  assert.ok(both.every((r) => r.status === 200));
  assert.equal(await s.balance("Alice"), 2000 - 500 - 100);
  // zu wenig Coins
  assert.equal((await gift("Bob", "Alice", 10000)).status, 400);
  await s.give("Bob", -100000);
  assert.equal((await gift("Bob", "Alice", 10)).status, 402);
  const hist = (await s.call("GET", "/api/coins", null, s.t("Alice"))).data;
  assert.equal(hist.tx[0].kind, "gift_out");
  assert.equal(hist.tx[0].other, "Bob");
  assert.deepEqual(hist.gift, { sent: 100, limit: 500, min: 10 });
});

test("Admin: Coins geben und nehmen (mit Grund, nicht unter 0), Verlauf, Admin-Konto im Spiel", async () => {
  const s = await setup();
  assert.equal((await s.call("POST", "/api/admin/coins", { player: "Alice", delta: 10 }, s.t("Bob"))).status, 403);
  assert.equal((await s.admin("/api/admin/coins", { player: "Alice", delta: 0 })).status, 400);
  assert.equal((await s.admin("/api/admin/coins", { player: "Niemand", delta: 5 })).status, 404);
  let view = await s.give("Alice", 300, "Turniersieg");
  assert.equal(view.balance, 300);
  view = await s.give("Alice", -1000, "Korrektur");
  assert.deepEqual([view.balance, view.changed], [0, -300]);
  assert.equal((await s.admin("/api/admin/coins", { player: "Alice", delta: -5 })).status, 409);
  // Bob wird Admin und gibt im Spiel Coins (wie /mytic coins give)
  await s.admin("/api/admin/grants", { uuid: s.users.Bob.uuid, items: ["admin"] });
  const res = await s.call("POST", "/api/admin/coins", { player: "alice", delta: 50, reason: "Danke" }, s.t("Bob"));
  assert.equal(res.status, 200);
  const hist = await s.call("GET", "/api/admin/coins/Alice", null, s.t("Bob"));
  assert.deepEqual(hist.data.tx.map((x) => [x.kind, x.delta, x.reason, x.admin]), [["grant", 50, "Danke", "Bob"], ["take", -300, "Korrektur", "Admin-Passwort"], ["grant", 300, "Turniersieg", "Admin-Passwort"]]);
  assert.equal((await s.admin(`/api/admin/coins/${s.users.Alice.uuid}`)).data.balance, 50);
  // Alice sieht die Gutschrift als Hinweis im Lebenszeichen
  const events = (await s.beat("Alice", { coinTx: 1 })).data.coins.events;
  assert.deepEqual(events.map((e) => [e.kind, e.delta, e.reason]), [["take", -300, "Korrektur"], ["grant", 50, "Danke"]]);
  // Admins besitzen alles (auch ohne Kauf)
  const cos = await s.call("POST", "/api/cosmetics", { cape: "galaxie", nametag: "&aBoss" }, s.t("Bob"));
  assert.deepEqual([cos.data.cape, cos.data.nametag], ["galaxie", "&aBoss"]);
  assert.equal((await s.buy("Bob", { item: "cape:galaxie" })).status, 409);
});

test("Admin: Preise überschreiben, Set-Rabatt, Preisstand ändert sich, Erstattung", async () => {
  const s = await setup();
  const v0 = (await s.call("GET", "/api/shop", null, s.t("Alice"))).data.v;
  assert.equal((await s.admin("/api/admin/prices", { prices: { "cape:gibtsnicht": 5 } })).status, 400);
  assert.equal((await s.admin("/api/admin/prices", { prices: { "cape:galaxie": -1 } })).status, 400);
  assert.equal((await s.admin("/api/admin/prices", { discount: 95 })).status, 400);
  let res = await s.admin("/api/admin/prices", { prices: { "cape:galaxie": 50, "minime": 0 }, discount: 50 });
  assert.equal(res.status, 200);
  const galaxie = res.data.items.find((x) => x.item === "cape:galaxie");
  assert.deepEqual([galaxie.base, galaxie.price, galaxie.override], [500, 50, true]);
  assert.equal(res.data.discount, 50);
  const shop = (await s.call("GET", "/api/shop", null, s.t("Alice"))).data;
  assert.notEqual(shop.v, v0);
  assert.equal(shop.items["cape:galaxie"], 50);
  assert.equal(shop.sets[0].price, Coins.bundlePrice(shop.sets[0].sum, 50));
  assert.equal((await s.call("POST", "/api/presence", { place: "launcher" }, s.t("Alice"))).data.coins.pv, shop.v);
  // Preis 0: kostenlos kaufbar
  assert.equal((await s.buy("Alice", { item: "minime" })).status, 200);
  // zurück auf Standard
  res = await s.admin("/api/admin/prices", { prices: { "cape:galaxie": null }, discount: null });
  assert.equal(res.data.items.find((x) => x.item === "cape:galaxie").price, 500);
  assert.equal(res.data.discount, 20);
  // Erstattung: Coins zurück, Teil weg, Auswahl abgelegt – nur einmal
  await s.give("Alice", 500);
  const bought = await s.buy("Alice", { item: "cape:galaxie" });
  await s.call("POST", "/api/cosmetics", { cape: "galaxie" }, s.t("Alice"));
  const txId = (await s.admin("/api/admin/coins/Alice")).data.tx[0].id;
  res = await s.admin("/api/admin/refund", { tx: txId, reason: "Versehen" });
  assert.equal(res.status, 200, JSON.stringify(res.data));
  assert.equal(res.data.balance, 500);
  assert.equal(res.data.tx[0].kind, "refund");
  assert.equal(bought.data.price, 500);
  assert.equal((await s.admin("/api/admin/refund", { tx: txId })).status, 409);
  assert.equal((await s.db.get("SELECT cape FROM users WHERE uuid = ?", [s.users.Alice.uuid])).cape, null);
  assert.equal((await s.admin("/api/admin/refund", { tx: 99999 })).status, 404);
});

test("Nametag-Vorlagen: einzeln kaufbar, nur der Text der Vorlage", async () => {
  const s = await setup();
  await s.give("Alice", 1000);
  const text = Coins.presetText("hohoho");
  assert.ok(text);
  // ohne Kauf: kein Nametag
  assert.equal((await s.call("POST", "/api/cosmetics", { nametag: text }, s.t("Alice"))).data.nametag, null);
  assert.equal((await s.buy("Alice", { item: "nametag:hohoho" })).status, 200);
  assert.equal((await s.call("POST", "/api/cosmetics", { nametag: text }, s.t("Alice"))).data.nametag, text);
  // anderer Text braucht den freien Nametag
  assert.equal((await s.call("POST", "/api/cosmetics", { nametag: "&cEigener Text" }, s.t("Alice"))).data.nametag, null);
  await s.call("POST", "/api/cosmetics", { nametag: text }, s.t("Alice"));
  const look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [s.users.Alice.uuid] }, s.t("Bob"));
  assert.equal(look.data.nametags[s.users.Alice.uuid], text);
  assert.equal((await s.buy("Alice", { item: "nametag" })).status, 200);
  assert.equal((await s.call("POST", "/api/cosmetics", { nametag: "&cEigener Text" }, s.t("Alice"))).data.nametag, "&cEigener Text");
  // Admin-Seite kann Vorlagen freischalten
  const view = await s.admin("/api/admin/grants", { uuid: s.users.Bob.uuid, items: ["nametag:hohoho", "nametag:*"] });
  assert.deepEqual(view.data.items, ["nametag:hohoho"]);
});

test("Besitz zurücksetzen: zwei Schritte, Sicherung, Admins behalten alles, einmalig, Wiederherstellen", async () => {
  const s = await setup();
  const A = s.users.Alice.uuid, B = s.users.Bob.uuid, C = s.users.Carol.uuid;
  await s.admin("/api/admin/grants", { uuid: A, items: ["cape:galaxie", "wings:*", "nametag"] });
  await s.admin("/api/admin/grants", { uuid: B, items: ["admin"] });
  await s.admin("/api/admin/grants", { uuid: C, items: ["aura:frost"] });
  await s.call("POST", "/api/cosmetics", { cape: "galaxie", wings: "rabe", nametag: "&aHi" }, s.t("Alice"));
  await s.call("POST", "/api/cosmetics", { cape: "galaxie" }, s.t("Bob"));
  await s.give("Alice", 100);
  // Schritt 1: nur Zusammenfassung + Token, noch nichts geändert
  assert.equal((await s.call("POST", "/api/admin/reset-ownership", {}, s.t("Alice"))).status, 403);
  let res = await s.admin("/api/admin/reset-ownership", {});
  assert.deepEqual(res.data.summary, { users: 2, items: 4, admins: 1, equipped: 1 });
  assert.equal(res.data.word, "ZURÜCKSETZEN");
  assert.equal((await s.db.all("SELECT * FROM grants")).length, 5);
  // falsches Wort / falsches Token / abgelaufen
  assert.equal((await s.admin("/api/admin/reset-ownership", { token: res.data.token, confirm: "ja" })).status, 400);
  assert.equal((await s.admin("/api/admin/reset-ownership", { token: res.data.token, confirm: "ZURÜCKSETZEN" })).status, 400); // Token verbraucht
  res = await s.admin("/api/admin/reset-ownership", {});
  s.tick(11 * 60 * 1000);
  assert.equal((await s.admin("/api/admin/reset-ownership", { token: res.data.token, confirm: "ZURÜCKSETZEN" })).status, 400);
  // Schritt 2 richtig
  res = await s.admin("/api/admin/reset-ownership", {});
  res = await s.admin("/api/admin/reset-ownership", { token: res.data.token, confirm: "ZURÜCKSETZEN" });
  assert.equal(res.status, 200, JSON.stringify(res.data));
  assert.equal(res.data.done, true);
  const batch = res.data.batch;
  // Sicherung vollständig (auch Admin), danach nur noch der Admin mit Freischaltungen; Auswahl abgelegt
  assert.equal((await s.db.all("SELECT * FROM grants_backup WHERE batch = ?", [batch])).length, 5);
  assert.deepEqual((await s.db.all("SELECT uuid, item FROM grants")).map((r) => [r.uuid, r.item]), [[B, "admin"]]);
  const alice = await s.db.get("SELECT cape, wings, nametag FROM users WHERE uuid = ?", [A]);
  assert.deepEqual([alice.cape, alice.wings, alice.nametag], [null, null, null]);
  assert.equal((await s.db.get("SELECT cape FROM users WHERE uuid = ?", [B])).cape, "galaxie");
  assert.equal(await s.balance("Alice"), 100); // Coins bleiben
  // andere sehen sofort nichts mehr (Änderungsmeldung)
  const look = await s.call("POST", "/api/cosmetics/lookup", { uuids: [A, B] }, s.t("Carol"));
  assert.deepEqual(look.data.capes, { [B]: "galaxie" });
  // idempotent: zweiter Lauf ändert nichts
  await s.buy("Alice", { item: "aura:frost" }).then((r) => assert.equal(r.status, 402));
  await s.give("Alice", 300);
  assert.equal((await s.buy("Alice", { item: "aura:frost" })).status, 200);
  res = await s.admin("/api/admin/reset-ownership", {});
  assert.deepEqual([res.data.done, res.data.already, res.data.batch], [true, true, batch]);
  assert.ok((await s.db.all("SELECT item FROM grants WHERE uuid = ?", [A])).some((r) => r.item === "aura:frost"));
  assert.equal((await s.admin("/api/admin/prices")).data.reset.batch, batch);
  // Wiederherstellen über die Admin-API (zwei Schritte): Altes zurück, Gekauftes bleibt, Auswahl zurück
  res = await s.admin("/api/admin/restore-ownership", {});
  assert.equal(res.data.summary.items, 5);
  res = await s.admin("/api/admin/restore-ownership", { token: res.data.token, confirm: "WIEDERHERSTELLEN" });
  assert.equal(res.status, 200, JSON.stringify(res.data));
  const items = (await s.db.all("SELECT item FROM grants WHERE uuid = ? ORDER BY item", [A])).map((r) => r.item);
  assert.deepEqual(items, ["aura:frost", "cape:galaxie", "nametag", "wings:*"]);
  const back = await s.db.get("SELECT cape, wings, nametag FROM users WHERE uuid = ?", [A]);
  assert.deepEqual([back.cape, back.wings, back.nametag], ["galaxie", "rabe", "&aHi"]);
});

test("Besitz wiederherstellen per Skript (Node-Datenbank)", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-coins-"));
  const file = path.join(dir, "friends.db");
  try {
    const s = await setup({ file });
    const A = s.users.Alice.uuid;
    await s.admin("/api/admin/grants", { uuid: A, items: ["cape:galaxie", "minime"] });
    await s.call("POST", "/api/cosmetics", { cape: "galaxie", minimes: { head: "Dream" } }, s.t("Alice"));
    assert.throws(() => restoreLocal(file), /Keine Sicherung/);
    let res = await s.admin("/api/admin/reset-ownership", {});
    res = await s.admin("/api/admin/reset-ownership", { token: res.data.token, confirm: "ZURÜCKSETZEN" });
    assert.equal((await s.db.all("SELECT * FROM grants")).length, 0);
    s.db.close();
    // ohne --yes nur anzeigen
    let summary = restoreLocal(file);
    assert.deepEqual([summary.batch, summary.grants, summary.missing, summary.applied], [res.data.batch, 2, 2, false]);
    summary = restoreLocal(file, { apply: true });
    assert.equal(summary.applied, true);
    const db = openDb(file);
    assert.deepEqual((await db.all("SELECT item FROM grants WHERE uuid = ? ORDER BY item", [A])).map((r) => r.item), ["cape:galaxie", "minime"]);
    const row = await db.get("SELECT cape, minime_set FROM users WHERE uuid = ?", [A]);
    assert.deepEqual([row.cape, JSON.parse(row.minime_set)], ["galaxie", { head: "Dream" }]);
    assert.ok((await db.get("SELECT restored FROM migrations")).restored);
    db.close();
    // zweimal ausführen schadet nicht
    assert.equal(restoreLocal(file, { apply: true }).missing, 0);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("Coins: Bremse für Käufe und Geschenke", async () => {
  const s = await setup({ limits: { coins: 3 } });
  const codes = [];
  for (let i = 0; i < 5; i++) codes.push((await s.buy("Alice", { item: "cape:galaxie" })).status);
  assert.deepEqual(codes, [402, 402, 402, 429, 429]);
});

test("Transaktion: Abbruch rollt alles zurück", async () => {
  const db = openDb(":memory:");
  await db.run("INSERT INTO coins (uuid, balance) VALUES ('x', 5)");
  await assert.rejects(db.batch([
    ["UPDATE coins SET balance = balance + 100 WHERE uuid = 'x'"],
    ["INSERT INTO grants (uuid, item) VALUES ('x', 'cape:a')"],
    ["UPDATE coins SET balance = balance - 1000 WHERE uuid = 'x' AND balance >= 1000", [], true],
  ]), (e) => e.code === "NEED" && e.index === 2);
  assert.equal((await db.get("SELECT balance FROM coins WHERE uuid = 'x'")).balance, 5);
  assert.equal((await db.all("SELECT * FROM grants")).length, 0);
});
