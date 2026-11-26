// Einstellungs-Sync über den Friends-Server (GET/PUT /api/sync): Mytic-Einstellungen, HUD-Layouts und -Profile,
// Fadenkreuz/Chat (Einstellungen der Module) und die Cosmetics-Auswahl je Konto auf alle PCs.
//
// Warum im Launcher (und nicht in der Mod): Die Einstellungen liegen in einer Datei, die sich alle 18 Mod-Versionen
// (auch 1.8.9) teilen. Der Launcher lädt vor dem Start herunter, solange das Spiel die Datei noch nicht gelesen hat (kein
// Übernehmen zur Laufzeit nötig), und lädt nach dem Beenden hoch, wenn alles geschrieben ist. So gibt es eine einzige
// Umsetzung mit Tests statt zwei (Fabric und Forge) und das Friends-Token bleibt, wo es ohnehin ist. Während des Spiels
// schaut der Launcher nur lokal auf die Dateien (30 s gesammelt, dann ein Upload); die Mod bietet die Knöpfe über die
// Launcher-Verbindung (gamebridge) an.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const zlib = require("node:zlib");
const crypto = require("node:crypto");

/** Höchstgröße auf dem Server (gzip-Daten) */
const MAX_BYTES = 256 * 1024;
/** Änderungen so lange sammeln, bevor hochgeladen wird */
const DEBOUNCE_MS = 30 * 1000;
/** So oft während des Spiels lokal nach Änderungen schauen (keine Netz-Anfrage) */
const WATCH_MS = 5 * 1000;
const MAX_BACKUPS = 10;

/**
 * Welche Dateien (im config-Ordner des Spiels) mitwandern und was dabei auf dem PC bleibt:
 * exclude = Felder, die nur zu diesem PC gehören oder vom Server kommen (nie hochladen, beim Herunterladen behalten);
 * localModules = Module, deren an/aus und Einstellungen von der Hardware abhängen (Clips: Arbeitsspeicher, FPS-Boost:
 * gesicherte Grafik-Optionen dieses PCs).
 */
const SYNC_FILES = [
  {
    name: "myticclient.json",
    exclude: ["savedGamma", "savedOptions", "ownedCache", "adminCache", "miniMe", "miniMePos"],
    localModules: ["clips", "fpsboost"],
  },
  { name: "mytic-styles.json", exclude: [], localModules: [] },
];
/** Teile von Feldnamen, die nie hochgeladen werden (falls eine spätere Mod-Version so etwas speichert) */
const SECRET_KEY = /token|secret|password|passwort|session|cookie|apikey|api_key|privatekey/i;

/** JSON mit sortierten Schlüsseln (gleicher Inhalt → gleicher Hash, egal in welcher Reihenfolge gespeichert) */
function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().filter((k) => value[k] !== undefined).map((k) => `${JSON.stringify(k)}:${canonical(value[k])}`).join(",")}}`;
  }
  return JSON.stringify(value === undefined ? null : value);
}

function sha256(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

function isObject(v) {
  return Boolean(v) && typeof v === "object" && !Array.isArray(v);
}

/** Geheimnisse rekursiv entfernen */
function stripSecrets(value) {
  if (Array.isArray(value)) return value.map(stripSecrets);
  if (!isObject(value)) return value;
  const out = {};
  for (const [k, v] of Object.entries(value)) if (!SECRET_KEY.test(k)) out[k] = stripSecrets(v);
  return out;
}

/** Hardware-abhängige Module aus enabled/settings (auch in HUD- und Server-Profilen) entfernen */
function stripModules(obj, modules) {
  if (!modules.length) return obj;
  const isLocal = (key) => modules.includes(key) || modules.some((m) => key.startsWith(`${m}.`));
  const clean = (map) => (isObject(map) ? Object.fromEntries(Object.entries(map).filter(([k]) => !isLocal(k))) : map);
  const out = { ...obj };
  for (const field of ["enabled", "settings", "positions"]) if (field in out) out[field] = clean(out[field]);
  if (isObject(out.serverProfiles)) {
    out.serverProfiles = Object.fromEntries(Object.entries(out.serverProfiles).map(([k, p]) => [k, isObject(p) ? { ...p, modules: clean(p.modules) } : p]));
  }
  return out;
}

/** Synchronisierter Teil einer Datei */
function syncedPart(spec, obj) {
  const out = {};
  for (const [k, v] of Object.entries(obj)) if (!spec.exclude.includes(k)) out[k] = v;
  return stripSecrets(stripModules(out, spec.localModules));
}

/** Stand aus der Cloud mit den lokalen Teilen (exclude, Hardware-Module) zur neuen Datei zusammensetzen */
function mergeLocal(spec, remote, local) {
  const out = stripModules(stripSecrets(remote), spec.localModules);
  for (const k of spec.exclude) if (k in local) out[k] = local[k];
  const isLocal = (key) => spec.localModules.includes(key) || spec.localModules.some((m) => key.startsWith(`${m}.`));
  for (const field of ["enabled", "settings", "positions"]) {
    if (!isObject(local[field])) continue;
    const keep = Object.entries(local[field]).filter(([k]) => isLocal(k));
    if (keep.length) out[field] = { ...(isObject(out[field]) ? out[field] : {}), ...Object.fromEntries(keep) };
  }
  return out;
}

/**
 * Was ist zu tun? local = { hash, mtime, empty }, state = zuletzt synchronisierter Stand dieses Kontos auf diesem PC
 * ({ hash, version } oder null), remote = { hash, updatedAt } (hash null = in der Cloud noch nichts).
 * → "none" | "upload" | "download"
 */
function decide(local, state, remote) {
  if (!remote || !remote.hash) return local.empty ? "none" : "upload";
  if (remote.hash === local.hash) return "none";
  if (local.empty) return "download";
  if (!state || !state.hash) return "download"; // erster Sync dieses Kontos auf diesem PC: Cloud gewinnt (lokaler Stand wird gesichert)
  if (state.hash === remote.hash) return "upload"; // nur hier geändert
  if (state.hash === local.hash) return "download"; // nur woanders geändert
  return (remote.updatedAt || 0) >= (local.mtime || 0) ? "download" : "upload"; // beides geändert: zuletzt geschrieben gewinnt
}

function pack(snapshot) {
  const text = canonical(snapshot);
  const data = zlib.gzipSync(Buffer.from(text, "utf8"), { level: 9 });
  if (data.length > MAX_BYTES) throw new Error(`Einstellungen zu groß für die Cloud (${Math.ceil(data.length / 1024)} KB, höchstens 256 KB)`);
  return { data: data.toString("base64"), hash: sha256(text) };
}

function unpack(base64) {
  const raw = Buffer.from(String(base64 || ""), "base64");
  if (raw.length > MAX_BYTES) throw new Error("Cloud-Daten zu groß");
  const text = zlib.gunzipSync(raw, { maxOutputLength: 16 * 1024 * 1024 }).toString("utf8");
  const snapshot = JSON.parse(text);
  if (!isObject(snapshot) || !isObject(snapshot.files)) throw new Error("Cloud-Daten fehlerhaft");
  return snapshot;
}

class CloudSync {
  /**
   * configDir: config-Ordner des Spiels; dataDir: eigener Ordner für Zustand und Sicherungen (wird nicht synchronisiert);
   * call(method, path, data): Anfrage am Friends-Server für das aktuelle Konto; uuid(): aktuelles Konto.
   */
  constructor({ configDir, dataDir, call, uuid, device = "", log = () => {}, now = () => Date.now(), debounceMs = DEBOUNCE_MS, watchMs = WATCH_MS }) {
    Object.assign(this, { configDir, dataDir, call, uuid, device, log, now, debounceMs, watchMs });
    this.timer = null;
    this.watching = null;
    this.queue = Promise.resolve();
    this.lastResult = null;
  }

  /** Nacheinander ausführen (nie zwei Syncs gleichzeitig) */
  serial(task) {
    const run = this.queue.then(task, task);
    this.queue = run.catch(() => {});
    return run;
  }

  stateFile() {
    return path.join(this.dataDir, "state.json");
  }

  backupDir() {
    return path.join(this.dataDir, "backups");
  }

  readState() {
    try {
      const all = JSON.parse(fs.readFileSync(this.stateFile(), "utf8"));
      return isObject(all.accounts) ? all : { accounts: {} };
    } catch {
      return { accounts: {} };
    }
  }

  /** Zuletzt synchronisierter Stand des aktuellen Kontos auf diesem PC */
  state() {
    return this.readState().accounts[this.uuid()] || null;
  }

  async saveState(entry) {
    const all = this.readState();
    all.accounts[this.uuid()] = { ...entry, at: this.now() };
    await fsp.mkdir(this.dataDir, { recursive: true });
    await fsp.writeFile(this.stateFile(), JSON.stringify(all, null, 2));
  }

  /** Lokaler Stand: { snapshot, hash, mtime, empty } */
  local() {
    const files = {};
    let mtime = 0;
    for (const spec of SYNC_FILES) {
      const file = path.join(this.configDir, spec.name);
      try {
        const obj = JSON.parse(fs.readFileSync(file, "utf8"));
        if (!isObject(obj)) continue;
        files[spec.name] = syncedPart(spec, obj);
        mtime = Math.max(mtime, fs.statSync(file).mtimeMs);
      } catch {
        // Datei fehlt oder ist kaputt: nicht mitnehmen
      }
    }
    const snapshot = { v: 1, files };
    return { snapshot, hash: sha256(canonical(snapshot)), mtime, empty: Object.keys(files).length === 0 };
  }

  /** Stand in die Dateien schreiben (lokale Felder bleiben erhalten) */
  async apply(snapshot) {
    await fsp.mkdir(this.configDir, { recursive: true });
    for (const spec of SYNC_FILES) {
      const remote = snapshot.files[spec.name];
      if (!isObject(remote)) continue;
      const file = path.join(this.configDir, spec.name);
      let local = {};
      try {
        const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
        if (isObject(parsed)) local = parsed;
      } catch {
        // neu
      }
      const tmp = `${file}.sync.tmp`;
      await fsp.writeFile(tmp, JSON.stringify(mergeLocal(spec, remote, local), null, 2));
      await fsp.rename(tmp, file);
    }
  }

  // ---------------------------------------------------------------------------------------------- Sicherungen

  /** Stand sichern, bevor er überschrieben wird. reason: "lokal" (vor dem Herunterladen) oder "cloud" (vor dem Hochladen) */
  async backup(snapshot, reason, meta = {}) {
    const dir = this.backupDir();
    await fsp.mkdir(dir, { recursive: true });
    const t = this.now();
    const id = `${t}-${reason}`;
    await fsp.writeFile(path.join(dir, `${id}.json.gz`), zlib.gzipSync(JSON.stringify({ reason, at: t, ...meta, snapshot })));
    const old = (await this.backups()).slice(MAX_BACKUPS);
    for (const b of old) await fsp.rm(path.join(dir, `${b.id}.json.gz`), { force: true });
    return id;
  }

  /** Sicherungen, neueste zuerst: [{ id, reason, at, device }] */
  async backups() {
    let names = [];
    try {
      names = await fsp.readdir(this.backupDir());
    } catch {
      return [];
    }
    const list = [];
    for (const name of names) {
      const m = /^(\d+)-(lokal|cloud|wiederherstellen)\.json\.gz$/.exec(name);
      if (m) list.push({ id: `${m[1]}-${m[2]}`, at: Number(m[1]), reason: m[2] });
    }
    return list.sort((a, b) => b.at - a.at);
  }

  async readBackup(id) {
    if (!/^\d+-(lokal|cloud|wiederherstellen)$/.test(String(id))) throw new Error("Unbekannte Sicherung");
    const raw = await fsp.readFile(path.join(this.backupDir(), `${id}.json.gz`));
    const data = JSON.parse(zlib.gunzipSync(raw).toString("utf8"));
    if (!isObject(data.snapshot) || !isObject(data.snapshot.files)) throw new Error("Sicherung beschädigt");
    return data;
  }

  /** Sicherung zurückholen (der aktuelle Stand wird vorher selbst gesichert); hochgeladen wird beim nächsten Sync */
  restore(id) {
    return this.serial(async () => {
      const data = await this.readBackup(id);
      const current = this.local();
      if (!current.empty) await this.backup(current.snapshot, "wiederherstellen");
      await this.apply(data.snapshot);
      this.log(`[Sync] Sicherung vom ${new Date(data.at).toLocaleString("de-DE")} wiederhergestellt`);
      return { restored: id, ...this.result("restore") };
    });
  }

  // ---------------------------------------------------------------------------------------------- Abgleich

  result(action, extra = {}) {
    const state = this.state();
    this.lastResult = { action, at: this.now(), hash: state ? state.hash : null, version: state ? state.version : 0, ...extra };
    return this.lastResult;
  }

  /**
   * Abgleich (vor dem Spielstart): hint = Stand aus einem Lebenszeichen ({ hash, version, updatedAt }) – passt er zum
   * lokalen Stand, ist keine Anfrage nötig. Sonst eine GET-Anfrage (ohne Daten, wenn schon gleich).
   */
  sync({ hint, deadline } = {}) {
    return this.serial(async () => {
      const local = this.local();
      const state = this.state();
      if (hint && hint.hash && hint.hash === local.hash) {
        if (!state || state.hash !== local.hash) await this.saveState({ hash: hint.hash, version: hint.version || 0, updatedAt: hint.updatedAt || 0 });
        return this.result("none", { requests: 0 });
      }
      // Cloud unverändert seit dem letzten Abgleich, nur hier geändert: gleich hochladen (ohne GET)
      if (hint && hint.hash && state && state.hash === hint.hash && !local.empty) return this.uploadNow(local, null, 0);
      const remote = await this.call("GET", `api/sync?have=${local.hash}`);
      const action = decide(local, state, remote);
      if (action === "none") {
        if (remote.hash) await this.saveState({ hash: remote.hash, version: remote.version, updatedAt: remote.updatedAt });
        return this.result("none", { requests: 1 });
      }
      if (action === "download") return this.downloadFrom(remote, local, 1, deadline);
      return this.uploadNow(local, remote, 1);
    });
  }

  /** Hochladen, wenn sich lokal etwas geändert hat (beim Beenden, gesammelte Änderungen) */
  uploadIfChanged() {
    return this.serial(async () => {
      const local = this.local();
      const state = this.state();
      if (local.empty || (state && state.hash === local.hash)) return this.result("none", { requests: 0 });
      return this.uploadNow(local, null, 0);
    });
  }

  /** Knopf „Jetzt hochladen“ */
  upload() {
    return this.serial(async () => {
      const local = this.local();
      if (local.empty) throw new Error("Noch keine Einstellungen auf diesem PC");
      return this.uploadNow(local, null, 0);
    });
  }

  /** Knopf „Jetzt herunterladen“ */
  download() {
    return this.serial(async () => {
      const remote = await this.call("GET", "api/sync");
      if (!remote.hash) throw new Error("In der Cloud ist noch nichts gespeichert");
      return this.downloadFrom(remote, this.local(), 1);
    });
  }

  /** deadline: danach nichts mehr schreiben (das Spiel startet schon mit dem lokalen Stand) */
  async downloadFrom(remote, local, requests, deadline = 0) {
    if (!remote.data) remote = await this.call("GET", "api/sync"), requests++;
    if (deadline && Date.now() > deadline) throw new Error("Zeit abgelaufen – Stand der Cloud nicht übernommen");
    if (local.hash !== remote.hash) {
      const snapshot = unpack(remote.data);
      if (!local.empty) await this.backup(local.snapshot, "lokal");
      await this.apply(snapshot);
    }
    await this.saveState({ hash: remote.hash, version: remote.version, updatedAt: remote.updatedAt });
    this.log(`[Sync] Einstellungen aus der Cloud übernommen (Stand ${remote.version}${remote.device ? `, von ${remote.device}` : ""})`);
    return this.result("download", { requests, device: remote.device || null, updatedAt: remote.updatedAt });
  }

  /** remote: schon geholter Stand der Cloud (mit Daten) – wird vor dem Überschreiben gesichert */
  async uploadNow(local, remote, requests) {
    const state = this.state();
    if (remote && remote.data && remote.hash && (!state || state.hash !== remote.hash)) {
      try {
        await this.backup(unpack(remote.data), "cloud", { device: remote.device || null, version: remote.version });
      } catch (error) {
        this.log(`[Sync] Stand der Cloud nicht gesichert: ${error.message}`);
      }
    }
    const { data, hash } = pack(local.snapshot);
    const body = { data, hash, device: this.device };
    if (state && Number.isInteger(state.version)) body.ifVersion = state.version;
    else if (remote) body.ifVersion = remote.version || 0;
    let res = await this.call("PUT", "api/sync", body);
    requests++;
    if (res.conflict) {
      // ein anderer PC war schneller: dessen Stand sichern, dann gewinnt dieser (zuletzt geschrieben)
      if (res.data) await this.backup(unpack(res.data), "cloud", { device: res.device || null, version: res.version });
      res = await this.call("PUT", "api/sync", { ...body, force: true });
      requests++;
    }
    await this.saveState({ hash: res.hash, version: res.version, updatedAt: res.updatedAt });
    if (!res.unchanged) this.log(`[Sync] Einstellungen hochgeladen (Stand ${res.version})`);
    return this.result("upload", { requests, version: res.version });
  }

  // ---------------------------------------------------------------------------------------------- Während des Spiels

  /** Lokal auf Änderungen schauen und gesammelt (debounce) hochladen */
  watch() {
    if (this.watching) return;
    let seen = this.local().hash;
    this.watching = setInterval(() => {
      let hash;
      try {
        hash = this.local().hash;
      } catch {
        return;
      }
      if (hash === seen) return;
      seen = hash;
      clearTimeout(this.timer);
      this.timer = setTimeout(() => {
        this.timer = null;
        this.uploadIfChanged().catch((error) => this.log(`[Sync] Hochladen fehlgeschlagen: ${error.message}`));
      }, this.debounceMs);
    }, this.watchMs);
    if (this.watching.unref) this.watching.unref();
  }

  /** Spiel beendet: nicht mehr schauen, Ausstehendes sofort hochladen */
  async stop() {
    clearInterval(this.watching);
    this.watching = null;
    clearTimeout(this.timer);
    this.timer = null;
    return this.uploadIfChanged();
  }
}

module.exports = { CloudSync, SYNC_FILES, SECRET_KEY, stripSecrets, canonical, decide, pack, unpack, syncedPart, mergeLocal, DEBOUNCE_MS, MAX_BYTES };
