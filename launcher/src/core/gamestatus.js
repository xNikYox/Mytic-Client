// Status des laufenden Spiels für Discord ("auf <Server>"), ohne den Friends-Server zu fragen:
// Die Mod schreibt eine kleine JSON-Datei (Pfad über -Dmytic.status.file), der Launcher liest sie lokal –
// sofort per fs.watch und zur Sicherheit alle 15 s. Ältere Mods schreiben keine Datei; dann (und nur dann) fragt der
// Launcher alle 2 Minuten beim Friends-Server nach (poll).
const fs = require("node:fs");
const path = require("node:path");

const MAX_BYTES = 4096;

/** Inhalt der Statusdatei prüfen → { place, server, version, ts } oder null (fehlt, kaputt, unerwartet). */
function parseStatus(text) {
  let data;
  try {
    data = JSON.parse(String(text));
  } catch {
    return null;
  }
  if (!data || typeof data !== "object" || Array.isArray(data)) return null;
  if (data.place !== "game") return null;
  const ts = Number(data.ts);
  if (!Number.isFinite(ts) || ts <= 0) return null;
  const clean = (value, max) => (typeof value === "string" && value.trim() ? value.trim().slice(0, max) : null);
  return { place: "game", server: clean(data.server, 128), version: clean(data.version, 32), ts };
}

/** Statusdatei lesen (null, wenn es sie nicht gibt oder sie unlesbar/zu groß ist). */
async function readStatus(file, fsp = fs.promises) {
  try {
    const stat = await fsp.stat(file);
    if (!stat.isFile() || stat.size > MAX_BYTES) return null;
    return parseStatus(await fsp.readFile(file, "utf8"));
  } catch {
    return null;
  }
}

/**
 * Status aller laufenden Spiele: je Spiel eine Datei in dir. onChange(status) bei jeder Änderung (neuester Stand
 * aller Spiele). poll(): Rückfall für Mods ohne Statusdatei, alle pollInterval ms, solange keine Datei aufgetaucht ist.
 */
class GameStatusHub {
  constructor({ dir, onChange = () => {}, poll = null, interval = 15000, pollInterval = 120000, fsImpl = fs, log = () => {} }) {
    this.dir = dir;
    this.onChange = onChange;
    this.poll = poll;
    this.interval = interval;
    this.pollInterval = pollInterval;
    this.fs = fsImpl;
    this.log = log;
    this.games = new Map(); // id → { file, status, key, watcher, timer }
    this.pollTimer = null;
    this.polling = false;
  }

  fileFor(id) {
    return path.join(this.dir, `game-${id}.json`);
  }

  /** Spiel anmelden: alte Datei löschen, beobachten; gibt den Pfad für -Dmytic.status.file zurück. */
  add(id) {
    const file = this.fileFor(id);
    try {
      this.fs.mkdirSync(this.dir, { recursive: true });
      this.fs.rmSync(file, { force: true });
    } catch {
      // Ordner nicht beschreibbar: die Mod schreibt dann auch nichts → Rückfall greift
    }
    const entry = { file, status: null, key: null, watcher: null, timer: null, pending: null };
    this.games.set(id, entry);
    const check = () => this.check(id);
    try {
      // Ordner beobachten (die Mod ersetzt die Datei per Umbenennen, dabei ginge eine Datei-Beobachtung verloren)
      entry.watcher = this.fs.watch(this.dir, (event, name) => {
        if (name && String(name) !== path.basename(file)) return;
        clearTimeout(entry.pending);
        entry.pending = setTimeout(check, 100);
      });
      entry.watcher.on("error", () => {});
    } catch {
      // ohne fs.watch reicht das regelmäßige Lesen
    }
    entry.timer = setInterval(check, this.interval);
    if (entry.timer.unref) entry.timer.unref();
    this.startPolling();
    return file;
  }

  /** Spiel beendet: Beobachtung beenden, Datei löschen. */
  remove(id) {
    const entry = this.games.get(id);
    if (!entry) return;
    this.games.delete(id);
    clearInterval(entry.timer);
    clearTimeout(entry.pending);
    try {
      if (entry.watcher) entry.watcher.close();
    } catch {
      // schon zu
    }
    try {
      this.fs.rmSync(entry.file, { force: true });
    } catch {
      // egal
    }
    if (!this.games.size) this.stopPolling();
  }

  async check(id) {
    const entry = this.games.get(id);
    if (!entry) return null;
    const status = await readStatus(entry.file, this.fs.promises);
    if (!status || !this.games.has(id)) return entry.status;
    const key = `${status.server}|${status.version}`;
    entry.status = status;
    if (key !== entry.key) {
      entry.key = key;
      this.onChange(this.current());
    }
    return status;
  }

  /** Neuester Status aller laufenden Spiele (oder null). */
  current() {
    let best = null;
    for (const { status } of this.games.values()) if (status && (!best || status.ts > best.ts)) best = status;
    return best;
  }

  /** Schreibt mindestens ein laufendes Spiel eine Statusdatei? Dann braucht es keine Abfrage beim Friends-Server. */
  supported() {
    for (const { status } of this.games.values()) if (status) return true;
    return false;
  }

  startPolling() {
    if (!this.poll || this.pollTimer) return;
    this.pollTimer = setInterval(() => this.pollOnce(), this.pollInterval);
    if (this.pollTimer.unref) this.pollTimer.unref();
  }

  stopPolling() {
    clearInterval(this.pollTimer);
    this.pollTimer = null;
  }

  /** Rückfall: nur fragen, wenn ein Spiel läuft und keines eine Statusdatei schreibt. */
  async pollOnce() {
    if (!this.poll || !this.games.size || this.supported() || this.polling) return false;
    this.polling = true;
    try {
      await this.poll();
    } catch (error) {
      this.log(`[Discord] Server nicht abgefragt: ${error.message}`);
    } finally {
      this.polling = false;
    }
    return true;
  }

  close() {
    for (const id of [...this.games.keys()]) this.remove(id);
    this.stopPolling();
  }
}

module.exports = { parseStatus, readStatus, GameStatusHub };
