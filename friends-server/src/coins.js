// Coins und Shop des Mytic Clients: Preise (Katalog + Überschreibungen), Kontostand, Tagesbonus, Geschenke unter
// Freunden, Käufe und das einmalige Zurücksetzen des Besitzes (mit Sicherung und Wiederherstellung).
//
// Alles, was Kontostand und Freischaltungen gemeinsam ändert, läuft über db.batch([...]): die Anweisungen laufen
// synchron in EINER Transaktion (Node: BEGIN … COMMIT, Cloudflare: transactionSync) – dazwischen kann keine andere
// Anfrage etwas ändern. Abbuchungen sind bedingt ("… AND balance >= ?", need = true): reicht das Guthaben nicht
// (z. B. zwei Käufe gleichzeitig), bricht die ganze Transaktion ab und nichts ist passiert.
import { CATALOG } from "./catalog.js";

/** Grundpreise je Art (Coins). Nametag-Vorlagen einzeln, „Nametag“ = freier Text mit Farben und Formaten. */
export const PRICE_BASE = {
  "nametag-preset": 100,
  nametag: 250,
  aura: 300,
  shield: 400,
  acc: 400,
  cape: 500,
  minime: 600,
  wings: 800,
};
/**
 * Aufschlag in Prozent für Event-Teile: Winterzauber 2026 +25 %, Winterzauber II +50 % (HD-Umhänge in voller
 * Auflösung, doppelt aufgelöste Flügel/Accessoires, eigene Partikel und blinkende Lichter). Preise auf 10 gerundet.
 */
export const EVENT_SURCHARGE = { "Winterzauber 2026": 25, "Winterzauber II": 50 };
/** Rabatt in Prozent beim Kauf eines ganzen Sets (nur auf die noch fehlenden Teile). */
export const SET_DISCOUNT = 20;

// Wirtschaft
export const DAILY_BASE = 10;          // Tagesbonus am ersten Tag …
export const DAILY_STEP = 5;           // … +5 je Tag in Folge …
export const DAILY_MAX_STREAK = 7;     // … bis Tag 7 (= 40 Coins), danach bleibt es bei 40
export const GIFT_MIN = 10;            // kleinstes Geschenk
export const GIFT_DAY_LIMIT = 500;     // höchstens so viel verschenken je Tag (UTC)
export const ADMIN_DELTA_MAX = 1_000_000;
export const PRICE_MAX = 1_000_000;
export const BALANCE_MAX = 1_000_000_000;
export const TX_RECENT = 20;
export const RESET_ID = "ownership-reset-1";   // das einmalige Zurücksetzen (idempotent: läuft höchstens einmal)
export const CONFIRM_MS = 10 * 60 * 1000;
export const RESET_WORD = "ZURÜCKSETZEN";
export const RESTORE_WORD = "WIEDERHERSTELLEN";
export const KINDS = ["grant", "take", "purchase", "daily", "gift_in", "gift_out", "refund"];

/** Tagesbonus für den n-ten Tag in Folge (1 = erster Tag). */
export function dailyAmount(streak) {
  return DAILY_BASE + DAILY_STEP * (Math.max(1, Math.min(DAILY_MAX_STREAK, streak)) - 1);
}

/** Vortag eines Tages "JJJJ-MM-TT". */
export function dayBefore(day) {
  return new Date(Date.parse(`${day}T00:00:00Z`) - 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

function round10(n) {
  return Math.max(10, Math.round(n / 10) * 10);
}

/** Kennung eines Sets aus dem Namen: "Winterzauber 2026" → "winterzauber-2026". */
export function setId(name) {
  return String(name).toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

/**
 * Alle kaufbaren Teile mit Grundpreis: Map item → { item, kind, id, name, price, event }.
 * item: "cape:<id>", "wings:<id>", "shield:<id>", "aura:<id>", "acc:<id>", "nametag:<vorlage>", "minime", "nametag".
 */
export function defaultPrices(catalog = CATALOG) {
  const eventOf = new Map();
  for (const set of catalog.sets || []) for (const item of set.items) if (EVENT_SURCHARGE[set.name] !== undefined) eventOf.set(item, set.name);
  const out = new Map();
  const add = (item, kind, id, name, base) => {
    const event = eventOf.get(item) || null;
    const price = event ? round10(base * (1 + EVENT_SURCHARGE[event] / 100)) : base;
    out.set(item, { item, kind, id, name, price, event });
  };
  for (const kind of ["cape", "wings", "shield", "aura", "acc"]) for (const [id, name] of catalog[kind] || []) add(`${kind}:${id}`, kind, id, name, PRICE_BASE[kind]);
  add("minime", "minime", null, "MiniMe", PRICE_BASE.minime);
  add("nametag", "nametag", null, "Nametag (eigener Text, Farben & Formate)", PRICE_BASE.nametag);
  for (const [id, name] of catalog.nametag || []) add(`nametag:${id}`, "nametag", id, `Nametag-Vorlage „${name}“`, PRICE_BASE["nametag-preset"]);
  return out;
}

/** Text einer Nametag-Vorlage (aus dem Katalog) oder null. */
export function presetText(id, catalog = CATALOG) {
  const found = (catalog.nametag || []).find((p) => p[0] === id);
  return found ? found[2] : null;
}

/** Vorlagen-ID zu einem Nametag-Text (genau gleich) oder null. */
export function presetOfText(text, catalog = CATALOG) {
  if (typeof text !== "string") return null;
  const found = (catalog.nametag || []).find((p) => p[2] === text);
  return found ? found[0] : null;
}

/** Sets mit Kennung, Name und Teilen (nur Teile, die es im Katalog gibt). */
export function catalogSets(prices, catalog = CATALOG) {
  return (catalog.sets || []).map((s) => ({ id: setId(s.name), name: s.name, items: s.items.filter((i) => prices.has(i)) }));
}

/** Set-Preis für die noch fehlenden Teile: Summe abzüglich Rabatt (gerundet). */
export function bundlePrice(sum, discount) {
  return Math.max(0, Math.round((sum * (100 - discount)) / 100));
}

/**
 * Anweisungen zum Wiederherstellen einer Sicherung (für den Admin-Endpunkt und scripts/restore-ownership.mjs):
 * Freischaltungen wieder eintragen (zusätzlich zu inzwischen gekauften) und die damalige Auswahl zurückholen, wo
 * jetzt nichts gewählt ist. Coins werden dabei nicht verändert.
 */
export function restoreStatements(batch, t) {
  const cols = ["cape", "wings", "shield", "aura", "acc", "nametag", "minime", "minime_pos", "minime_set"];
  const sets = cols.map((c) => `${c} = COALESCE(${c}, (SELECT b.${c} FROM cos_backup b WHERE b.batch = ? AND b.uuid = users.uuid))`).join(", ");
  return [
    ["INSERT OR IGNORE INTO grants (uuid, item) SELECT uuid, item FROM grants_backup WHERE batch = ?", [batch]],
    [`UPDATE users SET ${sets}, cos_at = ? WHERE uuid IN (SELECT uuid FROM cos_backup WHERE batch = ?)`, [...cols.map(() => batch), t, batch]],
    ["UPDATE users SET cos_at = ? WHERE uuid IN (SELECT uuid FROM grants_backup WHERE batch = ?)", [t, batch]],
    ["UPDATE migrations SET restored = ? WHERE batch = ?", [t, batch]],
  ];
}
