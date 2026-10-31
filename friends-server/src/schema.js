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
`;

/** Spätere Erweiterungen bestehender Datenbanken (Fehler "Spalte gibt es schon" wird ignoriert). */
export const MIGRATIONS = [
  "ALTER TABLE users ADD COLUMN cape TEXT",
  "ALTER TABLE users ADD COLUMN minime TEXT",
  "ALTER TABLE users ADD COLUMN wings TEXT",
  "ALTER TABLE users ADD COLUMN minime_pos TEXT",
  "ALTER TABLE users ADD COLUMN minime_set TEXT",
  "ALTER TABLE users ADD COLUMN shield TEXT",
  "ALTER TABLE users ADD COLUMN aura TEXT",
  "ALTER TABLE users ADD COLUMN nametag TEXT",
  "ALTER TABLE challenges ADD COLUMN ip TEXT",
];
