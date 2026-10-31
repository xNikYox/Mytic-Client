// Prüft Einstellungen, die die Oberfläche schickt: nur bekannte Schlüssel mit passenden Werten.

/** Designs der Oberfläche (gleiche IDs wie THEMES in renderer/app.js). */
const THEMES = ["sunset", "midnight", "ocean", "forest", "nether", "end", "ice", "sakura", "gold", "matrix", "mono"];
const AFTER_LAUNCH = ["minimize", "keep", "hide"];
/** JVM-Optionen, mit denen fremder Code geladen oder Befehle ausgeführt werden können. */
const BLOCKED_JVM = ["-javaagent", "-agentlib", "-agentpath", "-xx:onoutofmemoryerror", "-xx:onerror", "-xbootclasspath", "-xx:vmoptionsfile", "-xx:flags"];
const SERVER_PATTERN = /^[A-Za-z0-9.\-_[\]:]{1,255}$/;
const CLIENT_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function int(value, min, max) {
  if (!Number.isInteger(value) || value < min || value > max) throw new Error("Ungültiger Wert");
  return value;
}

function bool(value) {
  if (typeof value !== "boolean") throw new Error("Ungültiger Wert");
  return value;
}

function oneOf(list) {
  return (value) => {
    if (!list.includes(value)) throw new Error("Ungültiger Wert");
    return value;
  };
}

function server(value) {
  if (typeof value !== "string") throw new Error("Ungültige Server-Adresse");
  const text = value.trim();
  if (!text) return "";
  if (!SERVER_PATTERN.test(text) || text.startsWith("-")) throw new Error("Ungültige Server-Adresse");
  return text;
}

function jvmArgs(value) {
  if (typeof value !== "string" || value.length > 2000) throw new Error("Ungültige JVM-Argumente");
  const args = value.trim().split(/\s+/).filter(Boolean);
  for (const arg of args) {
    const lower = arg.toLowerCase();
    if (arg.startsWith("@") || BLOCKED_JVM.some((flag) => lower.startsWith(flag))) throw new Error(`JVM-Argument nicht erlaubt: ${arg}`);
  }
  return args.join(" ");
}

function clientId(value) {
  if (typeof value !== "string") throw new Error("Ungültige Client-ID");
  const text = value.trim();
  if (text && !CLIENT_ID_PATTERN.test(text)) throw new Error("Ungültige Client-ID");
  return text;
}

/** Erlaubte Schlüssel und ihre Prüfung (alles, was die Oberfläche über settings:set setzt). */
const SCHEMA = {
  memoryMb: (v) => int(v, 512, 1024 * 1024),
  width: (v) => int(v, 1, 16384),
  height: (v) => int(v, 1, 16384),
  fullscreen: bool,
  afterLaunch: oneOf(AFTER_LAUNCH),
  discordPresence: bool,
  jvmArgs,
  server,
  recentServers: (v) => {
    if (!Array.isArray(v) || v.length > 10) throw new Error("Ungültige Server-Liste");
    return v.map(server).filter(Boolean);
  },
  customClientId: clientId,
  verifyNext: bool,
  launcherTheme: oneOf(THEMES),
};

/** Liefert einen geprüften Patch; unbekannte Schlüssel oder falsche Werte → Fehler. */
function sanitizePatch(patch) {
  if (!patch || typeof patch !== "object" || Array.isArray(patch)) throw new Error("Ungültige Einstellungen");
  const clean = {};
  for (const [key, value] of Object.entries(patch)) {
    if (!Object.prototype.hasOwnProperty.call(SCHEMA, key)) throw new Error(`Unbekannte Einstellung: ${key}`);
    clean[key] = SCHEMA[key](value);
  }
  return clean;
}

module.exports = { sanitizePatch, jvmArgs, THEMES };
