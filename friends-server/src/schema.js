// Datenbank-Schema (SQLite), gemeinsam für Node und Cloudflare.
export const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
  uuid TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  name_lower TEXT NOT NULL,
  offline INTEGER NOT NULL DEFAULT 0,
  head TEXT,
  head_at INTEGER NOT NULL DEFAULT 0,
  last_seen INTEGER NOT NULL DEFAULT 0,
  place TEXT,
  server TEXT,
  version TEXT,
  created INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS users_name ON users (name_lower);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  uuid TEXT NOT NULL,
  created INTEGER NOT NULL,
  used INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS challenges (
  id TEXT PRIMARY KEY,
  created INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS friends (
  a TEXT NOT NULL,
  b TEXT NOT NULL,
  created INTEGER NOT NULL,
  PRIMARY KEY (a, b)
);
CREATE TABLE IF NOT EXISTS grants (
  uuid TEXT NOT NULL,
  item TEXT NOT NULL,
  PRIMARY KEY (uuid, item)
);
CREATE TABLE IF NOT EXISTS cloud_settings (
  uuid TEXT PRIMARY KEY,
  data TEXT NOT NULL,
  updated INTEGER NOT NULL
);
-- Einstellungs-Sync (Launcher): je Konto ein Stand (gzip+Base64), Versionsnummer, Prüfsumme des Inhalts und Zeitpunkt
CREATE TABLE IF NOT EXISTS sync_blobs (
  uuid TEXT PRIMARY KEY,
  version INTEGER NOT NULL,
  hash TEXT NOT NULL,
  data TEXT NOT NULL,
  size INTEGER NOT NULL,
  device TEXT,
  updated INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS shots (
  id TEXT PRIMARY KEY,
  uuid TEXT NOT NULL,
  created INTEGER NOT NULL,
  data TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS shots_uuid ON shots (uuid, created);
CREATE TABLE IF NOT EXISTS shot_quota (
  uuid TEXT NOT NULL,
  day INTEGER NOT NULL,
  n INTEGER NOT NULL,
  PRIMARY KEY (uuid, day)
);
CREATE TABLE IF NOT EXISTS requests (
  sender TEXT NOT NULL,
  target TEXT NOT NULL,
  created INTEGER NOT NULL,
  PRIMARY KEY (sender, target)
);
CREATE TABLE IF NOT EXISTS games (
  id TEXT PRIMARY KEY,
  inviter TEXT NOT NULL,
  invitee TEXT NOT NULL,
  white TEXT,
  black TEXT,
  color TEXT NOT NULL,
  time_s INTEGER NOT NULL,
  status TEXT NOT NULL,
  result TEXT,
  reason TEXT,
  moves TEXT NOT NULL DEFAULT '',
  fen TEXT NOT NULL,
  white_ms INTEGER NOT NULL,
  black_ms INTEGER NOT NULL,
  turn_since INTEGER NOT NULL DEFAULT 0,
  draw_offer TEXT,
  created INTEGER NOT NULL,
  updated INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS games_inviter ON games (inviter, status);
CREATE INDEX IF NOT EXISTS games_invitee ON games (invitee, status);
CREATE INDEX IF NOT EXISTS games_status ON games (status, created);
-- Spielstatistik je Konto (vom Launcher/Mod gemeldet): Summen, Tage (Ortszeit), Server, Versionen und Profile
CREATE TABLE IF NOT EXISTS stat_totals (
  uuid TEXT PRIMARY KEY,
  launches INTEGER NOT NULL DEFAULT 0,
  seconds INTEGER NOT NULL DEFAULT 0,
  longest INTEGER NOT NULL DEFAULT 0,
  first INTEGER NOT NULL DEFAULT 0,
  last INTEGER NOT NULL DEFAULT 0,
  play_at INTEGER NOT NULL DEFAULT 0,
  win_start INTEGER NOT NULL DEFAULT 0,
  win_sec INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS stat_days (
  uuid TEXT NOT NULL,
  day TEXT NOT NULL,
  seconds INTEGER NOT NULL DEFAULT 0,
  launches INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, day)
);
CREATE TABLE IF NOT EXISTS stat_servers (
  uuid TEXT NOT NULL,
  name TEXT NOT NULL,
  seconds INTEGER NOT NULL DEFAULT 0,
  last INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, name)
);
CREATE TABLE IF NOT EXISTS stat_items (
  uuid TEXT NOT NULL,
  kind TEXT NOT NULL,
  name TEXT NOT NULL,
  seconds INTEGER NOT NULL DEFAULT 0,
  launches INTEGER NOT NULL DEFAULT 0,
  last INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, kind, name)
);
CREATE TABLE IF NOT EXISTS stat_sessions (
  uuid TEXT NOT NULL,
  session TEXT NOT NULL,
  seconds INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL,
  PRIMARY KEY (uuid, session)
);
CREATE INDEX IF NOT EXISTS stat_sessions_updated ON stat_sessions (updated);
CREATE INDEX IF NOT EXISTS games_white ON games (white, status);
CREATE INDEX IF NOT EXISTS games_black ON games (black, status);
-- Coins: Kontostand je Konto, Tagesbonus (Tag in Ortszeit, Tage in Folge) und heute verschenkte Coins (UTC-Tag)
CREATE TABLE IF NOT EXISTS coins (
  uuid TEXT PRIMARY KEY,
  balance INTEGER NOT NULL DEFAULT 0,
  streak INTEGER NOT NULL DEFAULT 0,
  daily_day TEXT,
  gift_day TEXT,
  gift_sent INTEGER NOT NULL DEFAULT 0
);
-- Buchungen: kind = grant | take | purchase | daily | gift_in | gift_out | refund, balance = Stand danach,
-- ref = gekauftes Teil/Set, Gegenüber (UUID) bei Geschenken oder "tx:<id>" bei Erstattungen, items = freigeschaltete Teile
CREATE TABLE IF NOT EXISTS coin_tx (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  uuid TEXT NOT NULL,
  delta INTEGER NOT NULL,
  balance INTEGER NOT NULL DEFAULT 0,
  kind TEXT NOT NULL,
  ref TEXT,
  reason TEXT,
  admin TEXT,
  items TEXT,
  at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS coin_tx_uuid ON coin_tx (uuid, id);
CREATE UNIQUE INDEX IF NOT EXISTS coin_tx_refund ON coin_tx (ref) WHERE kind = 'refund';
-- Idempotenz: je Konto und Schlüssel nur ein Kauf/Geschenk (Wiederholung liefert das gespeicherte Ergebnis)
CREATE TABLE IF NOT EXISTS coin_idem (
  uuid TEXT NOT NULL,
  key TEXT NOT NULL,
  result TEXT NOT NULL,
  at INTEGER NOT NULL,
  PRIMARY KEY (uuid, key)
);
-- Preise: Überschreibungen je Teil (sonst Standardpreis aus coins.js), "@set-discount" = Set-Rabatt in Prozent
CREATE TABLE IF NOT EXISTS prices (
  item TEXT PRIMARY KEY,
  price INTEGER NOT NULL,
  admin TEXT,
  updated INTEGER NOT NULL
);
-- Einmalige Änderungen auf Knopfdruck (Besitz zurücksetzen) mit Sicherung, restored = Zeitpunkt der Wiederherstellung
CREATE TABLE IF NOT EXISTS migrations (
  id TEXT PRIMARY KEY,
  batch TEXT NOT NULL,
  at INTEGER NOT NULL,
  admin TEXT,
  users INTEGER NOT NULL DEFAULT 0,
  items INTEGER NOT NULL DEFAULT 0,
  restored INTEGER
);
CREATE TABLE IF NOT EXISTS grants_backup (
  batch TEXT NOT NULL,
  uuid TEXT NOT NULL,
  item TEXT NOT NULL,
  PRIMARY KEY (batch, uuid, item)
);
CREATE TABLE IF NOT EXISTS cos_backup (
  batch TEXT NOT NULL,
  uuid TEXT NOT NULL,
  cape TEXT, wings TEXT, shield TEXT, aura TEXT, acc TEXT, nametag TEXT, minime TEXT, minime_pos TEXT, minime_set TEXT,
  PRIMARY KEY (batch, uuid)
);
-- Bestätigungen in zwei Schritten (Admin: Besitz zurücksetzen/wiederherstellen)
CREATE TABLE IF NOT EXISTS confirm_tokens (
  token TEXT PRIMARY KEY,
  purpose TEXT NOT NULL,
  admin TEXT,
  data TEXT,
  at INTEGER NOT NULL
);
`;

/** Spätere Erweiterungen bestehender Datenbanken (Fehler "Spalte gibt es schon" wird ignoriert). */
export const MIGRATIONS = [
  // Schach: letztes Lebenszeichen je Spieler (Absturz/Beenden → nach 30 s verloren)
  "ALTER TABLE games ADD COLUMN white_seen INTEGER",
  "ALTER TABLE games ADD COLUMN black_seen INTEGER",
  "ALTER TABLE users ADD COLUMN cape TEXT",
  "ALTER TABLE users ADD COLUMN minime TEXT",
  "ALTER TABLE users ADD COLUMN wings TEXT",
  "ALTER TABLE users ADD COLUMN minime_pos TEXT",
  "ALTER TABLE users ADD COLUMN minime_set TEXT",
  "ALTER TABLE users ADD COLUMN shield TEXT",
  "ALTER TABLE users ADD COLUMN aura TEXT",
  "ALTER TABLE users ADD COLUMN nametag TEXT",
  "ALTER TABLE challenges ADD COLUMN ip TEXT",
  // Zweites Spiel „X und O“: Spielart, Runden (best of), Zeit je Zug (Sekunden, 0 = ohne) und Spielstand (JSON)
  "ALTER TABLE games ADD COLUMN kind TEXT DEFAULT 'chess'",
  "ALTER TABLE games ADD COLUMN rounds INTEGER",
  "ALTER TABLE games ADD COLUMN move_s INTEGER",
  "ALTER TABLE games ADD COLUMN state TEXT",
  // Accessoires „Kopf & Hals“ (Mods ab 2.58): je Platz eine ID als JSON, z. B. {"kopf":"weihnachtsmuetze","hals":"schal"}
  "ALTER TABLE users ADD COLUMN acc TEXT",
  // Zeitpunkt der letzten Cosmetics-Änderung (Auswahl oder Freischaltungen) – das Lebenszeichen meldet Änderungen (cos1)
  "ALTER TABLE users ADD COLUMN cos_at INTEGER NOT NULL DEFAULT 0",
  "CREATE INDEX IF NOT EXISTS users_cos_at ON users (cos_at)",
];
