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
CREATE TABLE IF NOT EXISTS requests (
  sender TEXT NOT NULL,
  target TEXT NOT NULL,
  created INTEGER NOT NULL,
  PRIMARY KEY (sender, target)
);
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
];
