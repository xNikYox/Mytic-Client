// Diagnose-Paket („Diagnose erstellen“): sammelt alles, was man bei einem Problem braucht, in eine ZIP im
// Launcher-Ordner (diagnose/Mytic-Diagnose-<Datum>.zip) – Launcher-Log, latest.log, Absturzberichte, hs_err-Dateien,
// Mod-Liste, Texturpakete, Mytic-Einstellungen, Zeiten der Mod (mytic-diagnose.json), Berichte der Diagnose-Messung
// und Systeminfos – und erkennt typische Probleme. Alles wird vorher geschwärzt (Tokens, E-Mails, private IPs,
// Windows-Benutzername in Pfaden). Es wird nichts hochgeladen: die Datei bleibt auf dem PC.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const zlib = require("node:zlib");
const AdmZip = require("adm-zip");
const { SECRET_KEY } = require("./cloudsync");
const { compareVersions } = require("./updater");

const REMOVED = "<entfernt>";
const MAX_LOG_BYTES = 4 * 1024 * 1024;
const KEEP_BUNDLES = 10;

// ---------------------------------------------------------------------------------------------- Schwärzen

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const SEP = "(?:\\\\\\\\|\\\\|/)"; // \\ (JSON), \ oder /
const PRIVATE_V4 = /(?<![\d.])(?:10\.(?:\d{1,3}\.){2}\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}|127\.(?:\d{1,3}\.){2}\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|100\.(?:6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3})(?![\d]|\.\d)/g;
const PRIVATE_V6 = /(?<![0-9a-f:])(?:fe80|f[cd][0-9a-f]{2}):[0-9a-f:]*[0-9a-f](?:%\w+)?(?![0-9a-f:])/gi;
const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}/g;
const JWT = /eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{6,}/g;
const CLI_SECRET = /(--(?:accessToken|session|xuid|clientId|refreshToken)[ =])(?:"[^"]*"|\S+)/gi;
const BEARER = /\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{8,}/g;
const KEY_VALUE = /((?:access_?token|refresh_?token|id_?token|auth_?token|[a-z_]*token|secret|password|passwort|session(?:_?id)?|cookie|api_?key|private_?key|authorization|MYTIC_LAUNCHER_KEY)["']?\s*[:=]\s*["']?)(?!<entfernt>)([^"'\s,;}&]{4,})/gi;
const USER_PATH = new RegExp(`((?:[A-Za-z]:${SEP}+(?:Users|Benutzer|Documents and Settings)${SEP}+)|(?:${SEP}(?:home|Users)${SEP}+))(?!%USER%)([^\\\\/"'\\s:*?<>|]+)`, "gi");

/**
 * Text schwärzen. ctx.userNames: Windows-/Systembenutzer (auch außerhalb von Users\… ersetzen, wenn in Pfaden),
 * ctx.homeDirs: Benutzerordner (werden zu %USERPROFILE%-ähnlichen Pfaden mit %USER%).
 */
function redactText(text, ctx = {}) {
  let out = String(text == null ? "" : text);
  for (const home of ctx.homeDirs || []) {
    if (!home || home.length < 4) continue;
    const name = home.replace(/[\\/]+$/, "").split(/[\\/]/).pop();
    const variants = new Set([home, home.replace(/\\/g, "/"), home.replace(/\\/g, "\\\\"), home.replace(/\//g, "\\")]);
    for (const v of variants) out = out.replace(new RegExp(escapeRe(v), "gi"), (m) => `${m.slice(0, m.length - name.length)}%USER%`);
  }
  out = out.replace(USER_PATH, (m, prefix, name) => (/^(Public|Default|All Users|Öffentlich)$/i.test(name) ? m : `${prefix}%USER%`));
  for (const name of ctx.userNames || []) {
    if (!name || name.length < 3) continue;
    // Benutzername als Pfadteil (z. B. OneDrive-Ordner, Temp-Pfade)
    out = out.replace(new RegExp(`(${SEP})${escapeRe(name)}(?=${SEP}|["'\\s]|$)`, "gi"), "$1%USER%");
  }
  out = out.replace(JWT, "<Token>");
  out = out.replace(CLI_SECRET, `$1${REMOVED}`);
  out = out.replace(BEARER, `$1 ${REMOVED}`);
  out = out.replace(KEY_VALUE, `$1${REMOVED}`);
  out = out.replace(EMAIL, "<E-Mail>");
  out = out.replace(PRIVATE_V4, "<privat-IP>");
  out = out.replace(PRIVATE_V6, "<privat-IP>");
  return out;
}

/** Schlüssel mit Daten des Cloud-Syncs (Stände, Hashes, Konten) – Schalter (true/false) bleiben sichtbar */
const SYNC_KEY = /sync|backup/i;
const PRIVATE_KEY = /e-?mail|accounts?$|refresh|xuid|ownedCache|adminCache/i;

/** JSON schwärzen: Geheimnisse wie beim Cloud-Sync (SECRET_KEY), Sync-Daten, E-Mails; Texte wie redactText */
function redactValue(value, ctx = {}, key = "") {
  if (key && SECRET_KEY.test(key)) return REMOVED;
  if (key && (PRIVATE_KEY.test(key) || SYNC_KEY.test(key)) && value !== null && typeof value !== "boolean" && typeof value !== "number") return REMOVED;
  if (Array.isArray(value)) return value.map((v) => redactValue(v, ctx));
  if (value && typeof value === "object") {
    const out = {};
    for (const [k, v] of Object.entries(value)) out[k] = redactValue(v, ctx, k);
    return out;
  }
  return typeof value === "string" ? redactText(value, ctx) : value;
}

// ---------------------------------------------------------------------------------------------- ZIP-Einträge lesen

/** Einen Eintrag aus einer ZIP/Jar lesen, ohne die ganze Datei zu laden (große Texturpakete) */
async function readZipEntry(file, wanted) {
  const handle = await fsp.open(file, "r");
  try {
    const { size } = await handle.stat();
    const tailSize = Math.min(size, 65557);
    const tail = Buffer.alloc(tailSize);
    await handle.read(tail, 0, tailSize, size - tailSize);
    let eocd = -1;
    for (let i = tailSize - 22; i >= 0; i--) if (tail.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
    if (eocd < 0) throw new Error("keine ZIP-Datei");
    const count = tail.readUInt16LE(eocd + 10);
    const cdSize = tail.readUInt32LE(eocd + 12);
    const cdOffset = tail.readUInt32LE(eocd + 16);
    const cd = Buffer.alloc(cdSize);
    await handle.read(cd, 0, cdSize, cdOffset);
    let p = 0;
    for (let n = 0; n < count && p + 46 <= cd.length; n++) {
      if (cd.readUInt32LE(p) !== 0x02014b50) break;
      const method = cd.readUInt16LE(p + 10);
      const compSize = cd.readUInt32LE(p + 20);
      const nameLen = cd.readUInt16LE(p + 28);
      const extraLen = cd.readUInt16LE(p + 30);
      const commentLen = cd.readUInt16LE(p + 32);
      const localOffset = cd.readUInt32LE(p + 42);
      const name = cd.toString("utf8", p + 46, p + 46 + nameLen);
      p += 46 + nameLen + extraLen + commentLen;
      if (name !== wanted) continue;
      if (compSize > 8 * 1024 * 1024) throw new Error("Eintrag zu groß");
      const local = Buffer.alloc(30);
      await handle.read(local, 0, 30, localOffset);
      const dataStart = localOffset + 30 + local.readUInt16LE(26) + local.readUInt16LE(28);
      const data = Buffer.alloc(compSize);
      await handle.read(data, 0, compSize, dataStart);
      if (method === 0) return data;
      if (method === 8) return zlib.inflateRawSync(data);
      throw new Error("Kompression nicht unterstützt");
    }
    return null;
  } finally {
    await handle.close();
  }
}

const parseJsonLoose = (buffer) => JSON.parse(String(buffer).replace(/^﻿/, ""));

/** Mod-Infos aus einer Jar: Fabric (fabric.mod.json), Forge 1.8.9 (mcmod.info), Quilt */
async function modInfo(file) {
  const tries = [
    ["fabric.mod.json", (j) => ({ id: j.id, name: j.name || j.id, version: j.version, loader: "fabric", provides: j.provides || [] })],
    ["quilt.mod.json", (j) => ({ id: j.quilt_loader && j.quilt_loader.id, name: (j.quilt_loader && j.quilt_loader.metadata && j.quilt_loader.metadata.name) || "", version: j.quilt_loader && j.quilt_loader.version, loader: "quilt", provides: [] })],
    ["mcmod.info", (j) => {
      const list = Array.isArray(j) ? j : j.modList || j.modlist || [];
      const m = list[0] || {};
      return { id: m.modid, name: m.name || m.modid, version: m.version, loader: "forge", provides: [] };
    }],
  ];
  for (const [entry, map] of tries) {
    try {
      const data = await readZipEntry(file, entry);
      if (!data) continue;
      const info = map(parseJsonLoose(data));
      if (info.id) return { ...info, id: String(info.id).toLowerCase(), version: String(info.version || "?") };
    } catch {
      // nächster Versuch
    }
  }
  return null;
}

// ---------------------------------------------------------------------------------------------- Texturpakete

/** pack.mcmeta prüfen: seit 1.21.9 brauchen Pakete mit pack_format ≥ 65 min_format und max_format */
function checkPackMeta(meta) {
  const pack = meta && meta.pack;
  if (!pack || typeof pack !== "object") return { ok: false, issue: "pack.mcmeta ohne „pack“-Abschnitt" };
  const fmt = pack.pack_format;
  const hasMin = pack.min_format !== undefined;
  const hasMax = pack.max_format !== undefined;
  const major = (v) => (Array.isArray(v) ? Number(v[0]) : Number(v));
  if (fmt === undefined && !hasMin) return { ok: false, issue: "weder pack_format noch min_format/max_format" };
  if (hasMin !== hasMax) return { ok: false, issue: hasMin ? "min_format ohne max_format" : "max_format ohne min_format" };
  if (fmt !== undefined && major(fmt) >= 65 && !(hasMin && hasMax)) return { ok: false, issue: `pack_format ${major(fmt)} ohne min_format/max_format (Pflicht seit 1.21.9)` };
  if (hasMin && hasMax && major(pack.min_format) > major(pack.max_format)) return { ok: false, issue: "min_format ist größer als max_format" };
  return { ok: true };
}

/** Aktive Pakete aus options.txt (resourcePacks:["vanilla","file/x.zip"]) */
function activePacks(optionsText) {
  const m = /^resourcePacks:(.*)$/m.exec(optionsText || "");
  if (!m) return [];
  try {
    return JSON.parse(m[1]).map(String);
  } catch {
    return [];
  }
}

async function listPacks(dir, optionsText) {
  const active = new Set(activePacks(optionsText).map((p) => p.replace(/^file\//, "")));
  let names = [];
  try {
    names = await fsp.readdir(dir);
  } catch {
    return [];
  }
  const out = [];
  for (const name of names.sort()) {
    const full = path.join(dir, name);
    let stat;
    try {
      stat = await fsp.stat(full);
    } catch {
      continue;
    }
    const isZip = stat.isFile() && /\.zip$/i.test(name);
    if (!isZip && !stat.isDirectory()) continue;
    const entry = { name, active: active.has(name), sizeMb: isZip ? Math.round(stat.size / 104857.6) / 10 : null };
    try {
      const buffer = isZip ? await readZipEntry(full, "pack.mcmeta") : await fsp.readFile(path.join(full, "pack.mcmeta"));
      if (!buffer) throw new Error("pack.mcmeta fehlt");
      const meta = parseJsonLoose(buffer);
      const p = meta.pack || {};
      Object.assign(entry, { packFormat: p.pack_format, minFormat: p.min_format, maxFormat: p.max_format, supportedFormats: p.supported_formats });
      const check = checkPackMeta(meta);
      if (!check.ok) entry.issue = check.issue;
    } catch (error) {
      entry.issue = /fehlt/.test(error.message) ? "pack.mcmeta fehlt" : "pack.mcmeta nicht lesbar";
    }
    out.push(entry);
  }
  return out;
}

// ---------------------------------------------------------------------------------------------- Mods

/** Alle Jars im Mods-Ordner des Profils mit Herkunft (Mytic, integriert, Mod-Browser, selbst hinzugefügt) */
async function listMods(modsDir, { managedFiles = [], userState = {} } = {}) {
  let names = [];
  try {
    names = await fsp.readdir(modsDir);
  } catch {
    return [];
  }
  const tracked = new Map(Object.values(userState.mods || {}).map((m) => [m.file, m]));
  const managed = new Set(managedFiles);
  const out = [];
  for (const file of names.sort()) {
    if (!/\.jar(\.disabled)?$/i.test(file)) continue;
    const enabled = !/\.disabled$/i.test(file);
    const jar = file.replace(/\.disabled$/i, "");
    const full = path.join(modsDir, file);
    const info = await modInfo(full).catch(() => null);
    const t = tracked.get(jar) || tracked.get(file);
    const source = /^mytic-client-/i.test(jar) ? "mytic" : managed.has(jar) ? "integriert" : t ? "mod-browser" : "eigene";
    out.push({
      file, enabled, source,
      id: info ? info.id : null, name: (info && info.name) || (t && t.title) || jar, version: (info && info.version) || (t && t.versionNumber) || "?",
      provides: info ? info.provides : [],
    });
  }
  return out;
}

// ---------------------------------------------------------------------------------------------- Probleme erkennen

/** Mods mit gleicher Funktion wie ein Mytic-Modul (gleichzeitig aktiv = doppelt) */
const OVERLAPS = [
  { ids: ["invseesummary", "invsee-summary", "invsee_summary"], file: /invsee[-_ ]?summary/i, title: "InvSee Summary doppelt", detail: "Mytic enthält die InvSee-Übersicht schon als Modul. Die eigenständige Mod daneben verursacht doppelte Panels und Konflikte – im Mod-Browser bzw. mods-Ordner ausschalten.", level: "warn" },
  { ids: ["xaerominimap", "xaerominimapfair", "xaeroworldmap", "journeymap", "voxelmap", "map_atlases"], title: "Zweite Minimap", detail: "Mytic hat eine eigene Minimap. Zwei Minimaps kosten doppelt Leistung – eine davon ausschalten (Mytic: Rechts-Shift → Minimap).", level: "info" },
  { ids: ["zoomify", "ok_zoomer", "logical_zoom", "wi_zoom"], title: "Zweite Zoom-Mod", detail: "Mytic hat einen eigenen Zoom (C). Zwei Zoom-Mods können sich die Taste streitig machen.", level: "info" },
  { ids: ["freelook", "perspectivemod", "perspective", "freecam"], ignore: ["freecam"], title: "Zweite Freelook-Mod", detail: "Mytic hat Freelook (Alt) und Snaplook schon eingebaut.", level: "info" },
];

/** Bekannt schwere oder unverträgliche Kombinationen */
const HEAVY = [
  { all: ["optifabric"], any: ["sodium", "iris", "lithium"], level: "error", title: "OptiFine (OptiFabric) zusammen mit Sodium/Iris", detail: "OptiFine ist mit Sodium, Iris und Lithium nicht verträglich – Abstürze und schwarze Bildschirme. OptiFabric entfernen (Sodium + Iris ersetzen es)." },
  { all: ["essential"], level: "warn", title: "Essential ist installiert", detail: "Essential lädt beim Start und beim Server-Join viel nach und braucht zusätzlich Arbeitsspeicher – häufige Ursache für lange Joins." },
  { all: ["distanthorizons", "iris"], level: "warn", title: "Distant Horizons + Shader", detail: "Die Kombination ist sehr anspruchsvoll für Grafikkarte und Arbeitsspeicher (mind. 6–8 GB zuweisen)." },
  { all: ["physicsmod"], level: "info", title: "Physics Mod", detail: "Physik-Effekte kosten viel CPU, besonders bei vielen Spielern und Explosionen." },
  { all: ["entity_model_features", "entity_texture_features"], any: ["iris"], level: "info", title: "EMF/ETF + Shader", detail: "Eigene Entity-Modelle und Texturen zusammen mit Shadern kosten spürbar FPS bei vielen Mobs." },
  { all: ["c2me"], level: "info", title: "C2ME auf dem Client", detail: "C2ME hilft nur beim Erzeugen neuer Welten im Einzelspieler und braucht dafür viele Threads." },
];

const ms = (v) => (v >= 10000 ? `${(v / 1000).toFixed(0)} s` : v >= 1000 ? `${(v / 1000).toFixed(1).replace(".", ",")} s` : `${v} ms`);

/**
 * input: { mods, packs, memoryMb, totalMb, live (mytic-diagnose.json), crashReports: [{name, mtimeMs}], hsErr: [...],
 * lastLaunch, cosmetics: { local, latest }, newestMytic, mcVersion }
 * → [{ id, level: error|warn|info, title, detail }]
 */
function detectProblems(input) {
  const { mods = [], packs = [], memoryMb = 4096, totalMb = 8192, live = null, crashReports = [], hsErr = [], lastLaunch = null, cosmetics = null, newestMytic = null } = input;
  const out = [];
  const add = (p) => out.push(p);
  const on = mods.filter((m) => m.enabled);
  const ids = new Set();
  for (const m of on) {
    if (m.id) ids.add(m.id);
    for (const p of m.provides || []) ids.add(String(p).toLowerCase());
  }
  const mytic = on.find((m) => m.source === "mytic");

  // doppelte Mods (gleiche ID in zwei Jars)
  const byId = new Map();
  for (const m of on) if (m.id) byId.set(m.id, [...(byId.get(m.id) || []), m]);
  for (const [id, list] of byId) {
    if (list.length < 2) continue;
    add({ id: `duplicate-${id}`, level: "error", title: `Mod doppelt: ${list[0].name}`, detail: `${list.map((m) => `${m.file} (${m.version})`).join(", ")} – Fabric startet so nicht oder lädt eine zufällige Version. Eine davon entfernen.` });
  }
  // Funktion schon in Mytic enthalten
  if (mytic) {
    for (const o of OVERLAPS) {
      const hits = on.filter((m) => m.source !== "mytic" && ((m.id && o.ids.includes(m.id) && !(o.ignore || []).includes(m.id)) || (o.file && o.file.test(m.file))));
      if (hits.length) add({ id: `overlap-${o.ids[0]}`, level: o.level, title: o.title, detail: `${hits.map((m) => m.name).join(", ")}: ${o.detail}` });
    }
  }
  // schwere Kombinationen
  for (const h of HEAVY) {
    if (h.all.every((id) => ids.has(id)) && (!h.any || h.any.some((id) => ids.has(id)))) add({ id: `heavy-${h.all.join("+")}`, level: h.level, title: h.title, detail: h.detail });
  }
  // Arbeitsspeicher
  const userCount = on.filter((m) => m.source === "eigene" || m.source === "mod-browser").length;
  if (memoryMb < 2048) add({ id: "ram-low", level: "error", title: `Sehr wenig Arbeitsspeicher (${memoryMb} MB)`, detail: "Unter 2 GB ruckelt Minecraft mit Mods fast immer. Einstellungen → Arbeitsspeicher auf mindestens 4 GB." });
  else if (memoryMb < 3072 || (memoryMb < 4096 && (userCount > 30 || ids.has("iris") || ids.has("distanthorizons")))) {
    add({ id: "ram-low", level: "warn", title: `Wenig Arbeitsspeicher (${(memoryMb / 1024).toFixed(1).replace(".", ",")} GB)`, detail: `${userCount} eigene Mods${ids.has("iris") ? " und Shader" : ""} – 4 GB oder mehr verhindern Ruckler durch die Speicherbereinigung.` });
  } else if (memoryMb > totalMb * 0.75) add({ id: "ram-high", level: "warn", title: "Zu viel Arbeitsspeicher zugewiesen", detail: `${(memoryMb / 1024).toFixed(1)} von ${(totalMb / 1024).toFixed(1)} GB – Windows lagert dann aus und das Spiel ruckelt.` });
  // Abstürze
  if (hsErr.length) add({ id: "hs-err", level: "error", title: "Java ist hart abgestürzt (hs_err)", detail: `${hsErr.map((f) => f.name).join(", ")} – meist Grafiktreiber, Overlay-Programme oder defekter Arbeitsspeicher. Grafiktreiber aktualisieren.` });
  const since = lastLaunch && lastLaunch.at ? lastLaunch.at : 0;
  const recentCrash = crashReports.filter((c) => !since || c.mtimeMs >= since);
  if (recentCrash.length) add({ id: "crash", level: "error", title: "Absturzbericht vom letzten Spiel", detail: `${recentCrash[0].name}${recentCrash[0].description ? ` – ${recentCrash[0].description}` : ""}` });
  if (lastLaunch && lastLaunch.exitCode !== undefined && lastLaunch.exitCode !== null && lastLaunch.exitCode !== 0 && !recentCrash.length) {
    add({ id: "exit-code", level: "warn", title: `Spiel wurde unerwartet beendet (Code ${lastLaunch.exitCode})`, detail: "Ohne Absturzbericht – oft zu wenig Arbeitsspeicher, ein Treiber-Absturz oder das Spiel wurde von außen beendet." });
  }
  // Texturpakete
  for (const p of packs.filter((x) => x.issue)) {
    add({ id: `pack-${p.name}`, level: p.active ? "warn" : "info", title: `Texturpaket ${p.active ? "(aktiv) " : ""}nicht kompatibel: ${p.name}`, detail: `${p.issue}. Das Paket wird als „inkompatibel“ angezeigt oder nicht geladen – neuere Version des Pakets holen.` });
  }
  // veraltet
  if (mytic && newestMytic && compareVersions(newestMytic, String(mytic.version).split("+")[0]) > 0) {
    add({ id: "mytic-old", level: "info", title: `Mytic-Mod veraltet (${mytic.version})`, detail: `Version ${newestMytic} liegt bereit und wird beim nächsten Start eingespielt.` });
  }
  if (cosmetics) {
    if (!cosmetics.local) add({ id: "cosmetics-missing", level: "info", title: "Cosmetics-Paket fehlt", detail: "Umhänge und Accessoires werden beim nächsten Start geladen (Internet nötig)." });
    else if (cosmetics.latest && compareVersions(cosmetics.latest, cosmetics.local) > 0) add({ id: "cosmetics-old", level: "warn", title: `Cosmetics-Paket veraltet (${cosmetics.local})`, detail: `Neu ist ${cosmetics.latest} – andere Spieler sehen neue Cosmetics sonst nicht. Kommt beim nächsten Start.` });
  }
  const incompatible = on.filter((m) => m.incompatible);
  if (incompatible.length) add({ id: "mods-incompatible", level: "info", title: "Mods ohne passende Version", detail: incompatible.map((m) => m.name).join(", ") });
  // Zeiten der Mod
  const t = live && live.timings;
  if (t) {
    if (t.gameStartToTitleMs > 90000) add({ id: "slow-start", level: "warn", title: `Langsamer Spielstart (${ms(t.gameStartToTitleMs)} bis zum Hauptmenü)`, detail: "Viele Mods, große Texturpakete oder wenig Arbeitsspeicher verlängern den Start. ModernFix hilft." });
    const joins = Array.isArray(t.joins) ? t.joins : [];
    const slow = joins.filter((j) => j.totalMs > 20000);
    if (slow.length) add({ id: "slow-join", level: "warn", title: `Langsamer Server-Join (bis ${ms(Math.max(...slow.map((j) => j.totalMs)))})`, detail: `${slow.length} von ${joins.length} Joins über 20 s. Verbinden ${ms(slow[slow.length - 1].connectMs)}, Welt laden ${ms(slow[slow.length - 1].loadingMs)}.` });
    const spikes = joins.filter((j) => Math.max(j.maxFrameMs || 0, j.afterJoinMaxFrameMs || 0) > 2000);
    if (spikes.length) add({ id: "join-spike", level: "warn", title: `Hänger beim Join (längstes Bild ${ms(Math.max(...spikes.map((j) => Math.max(j.maxFrameMs || 0, j.afterJoinMaxFrameMs || 0))))})`, detail: "Das Spiel stand beim Betreten der Welt sekundenlang. Mit /myticdiagnose während eines Joins messen, um die Mod zu finden." });
    const reloads = (Array.isArray(t.reloads) ? t.reloads : []).filter((r) => !r.initial && r.ms > 30000);
    if (reloads.length) add({ id: "slow-reload", level: "info", title: `Ressourcen neu laden dauert lange (${ms(Math.max(...reloads.map((r) => r.ms)))})`, detail: `Mit ${reloads[reloads.length - 1].packs} Paketen. Große Texturpakete (512x und mehr) verlangsamen das.` });
  }
  const order = { error: 0, warn: 1, info: 2 };
  return out.sort((a, b) => order[a.level] - order[b.level]);
}

// ---------------------------------------------------------------------------------------------- System aus Logs

/** Grafikkarte, Java, Fabric-Version aus latest.log bzw. Absturzbericht */
function parseLogInfo(text) {
  const t = String(text || "");
  const pick = (...res) => {
    for (const re of res) {
      const m = re.exec(t);
      if (m) return m[1].trim();
    }
    return null;
  };
  return {
    gpu: pick(/OpenGL Renderer: (.+)/, /GL_RENDERER[:=]\s*(.+)/, /Graphics card #0 name: (.+)/, /Renderer: '([^']+)'/, /GPU: (.+)/),
    glVersion: pick(/OpenGL Version: (.+)/, /GL_VERSION[:=]\s*(.+)/),
    java: pick(/Java Version: (.+)/, /Java version (\S+)/),
    fabric: pick(/with Fabric Loader (\S+)/),
    backend: pick(/Backend library: (.+)/),
  };
}

// ---------------------------------------------------------------------------------------------- Sammeln

async function readTail(file, bytes = MAX_LOG_BYTES) {
  try {
    const handle = await fsp.open(file, "r");
    try {
      const { size } = await handle.stat();
      const start = Math.max(0, size - bytes);
      const buffer = Buffer.alloc(size - start);
      await handle.read(buffer, 0, buffer.length, start);
      return (start > 0 ? `[… gekürzt, nur die letzten ${Math.round(bytes / 1048576)} MB …]\n` : "") + buffer.toString("utf8");
    } finally {
      await handle.close();
    }
  } catch {
    return null;
  }
}

/** Dateien eines Ordners (neueste zuerst), passend zum Muster */
async function newestFiles(dir, pattern, limit) {
  try {
    const names = (await fsp.readdir(dir)).filter((n) => pattern.test(n));
    const withTime = await Promise.all(names.map(async (name) => ({ name, file: path.join(dir, name), mtimeMs: (await fsp.stat(path.join(dir, name))).mtimeMs })));
    return withTime.sort((a, b) => b.mtimeMs - a.mtimeMs).slice(0, limit);
  } catch {
    return [];
  }
}

const readJson = async (file) => {
  try {
    return JSON.parse(await fsp.readFile(file, "utf8"));
  } catch {
    return null;
  }
};

const pad = (n) => String(n).padStart(2, "0");
function stamp(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}_${pad(date.getHours())}-${pad(date.getMinutes())}-${pad(date.getSeconds())}`;
}

/** Nur die für die Diagnose nützlichen Zeilen aus options.txt */
const OPTION_KEYS = ["renderDistance", "simulationDistance", "graphicsMode", "maxFps", "enableVsync", "guiScale", "fullscreen", "biomeBlendRadius", "particles", "entityShadows", "mipmapLevels", "prioritizeChunkUpdates", "resourcePacks", "incompatibleResourcePacks", "lang"];
function optionsSubset(text) {
  const out = {};
  for (const line of String(text || "").split(/\r?\n/)) {
    const i = line.indexOf(":");
    if (i > 0 && OPTION_KEYS.includes(line.slice(0, i))) out[line.slice(0, i)] = line.slice(i + 1);
  }
  return out;
}

/**
 * Alles sammeln und die ZIP schreiben.
 * opts: { outDir, now, redact: {userNames, homeDirs}, gameDir, modsDir, managedFiles, userState, launcherLogs: [file],
 *   configFiles: [{ name, file }], extraDirs (weitere Ordner mit Mytic-Profil-*.txt), system: {...}, settings: {memoryMb, jvmArgs, ...},
 *   profile: { name, mcVersion, loader }, lastLaunch, cosmetics, newestMytic, totalMb }
 * → { file, name, folder, problems, report, text }
 */
async function collect(opts) {
  const now = opts.now || new Date();
  const ctx = opts.redact || {};
  const R = (s) => redactText(s, ctx);
  const entries = [];
  const addText = (name, text) => {
    if (text != null) entries.push({ name, data: Buffer.from(R(text), "utf8") });
  };
  const gameDir = opts.gameDir;

  // Logs
  for (const file of opts.launcherLogs || []) addText(`logs/${path.basename(file)}`, await readTail(file));
  const latest = await readTail(path.join(gameDir, "logs", "latest.log"));
  addText("logs/latest.log", latest);
  const live = await readJson(path.join(gameDir, "logs", "mytic-diagnose.json"));
  if (live) addText("logs/mytic-diagnose.json", JSON.stringify(redactValue(live, ctx), null, 2));

  // Abstürze
  const crashReports = await newestFiles(path.join(gameDir, "crash-reports"), /^crash-.*\.txt$/i, 3);
  for (const c of crashReports) {
    const text = await readTail(c.file, 1024 * 1024);
    c.description = ((text || "").match(/Description: (.+)/) || [])[1];
    addText(`crash-reports/${c.name}`, text);
  }
  const hsErr = [];
  for (const dir of [gameDir, ...(opts.hsErrDirs || [])]) hsErr.push(...(await newestFiles(dir, /^hs_err_pid\d+\.log$/i, 2)));
  hsErr.sort((a, b) => b.mtimeMs - a.mtimeMs).splice(2);
  for (const h of hsErr) addText(`hs_err/${h.name}`, await readTail(h.file, 2 * 1024 * 1024));

  // Mods, Texturpakete, Optionen
  const mods = await listMods(opts.modsDir, { managedFiles: opts.managedFiles, userState: opts.userState });
  const tracked = Object.values((opts.userState && opts.userState.mods) || {});
  for (const m of mods) {
    const t = tracked.find((x) => x.file === m.file.replace(/\.disabled$/i, ""));
    if (t && t.incompatible) m.incompatible = true;
  }
  let optionsText = "";
  try {
    optionsText = await fsp.readFile(path.join(gameDir, "options.txt"), "utf8");
  } catch {
    // noch keine Optionen
  }
  const packs = await listPacks(path.join(gameDir, "resourcepacks"), optionsText);

  // Einstellungen (geschwärzt)
  for (const c of opts.configFiles || []) {
    const json = await readJson(c.file);
    if (json) addText(`config/${c.name}`, JSON.stringify(redactValue(json, ctx), null, 2));
  }

  // Berichte der Diagnose-Messung
  const profiles = [];
  for (const dir of [path.join(gameDir, "mytic", "diagnose"), ...(opts.extraDirs || [])]) profiles.push(...(await newestFiles(dir, /^Mytic-Profil-.*\.txt$/, 3)));
  profiles.sort((a, b) => b.mtimeMs - a.mtimeMs).splice(3);
  for (const p of profiles) addText(`profil/${p.name}`, await readTail(p.file, 1024 * 1024));

  // System
  const logInfo = { ...parseLogInfo(crashReports.length ? await readTail(crashReports[0].file, 256 * 1024) : ""), ...Object.fromEntries(Object.entries(parseLogInfo(latest)).filter(([, v]) => v)) };
  const settings = opts.settings || {};
  const memoryMb = Math.max(1024, Number(settings.memoryMb) || 4096);
  const system = {
    ...(opts.system || {}),
    gpu: (opts.system && opts.system.gpu) || logInfo.gpu || null,
    gpuFromLog: logInfo.gpu || null,
    glVersion: logInfo.glVersion || null,
    java: (live && live.system && live.system.java) || logInfo.java || null,
    fabricLoader: logInfo.fabric || null,
    myticMod: ((mods.find((m) => m.source === "mytic") || {}).version) || (live && live.mytic) || null,
    cosmetics: opts.cosmetics ? opts.cosmetics.local : null,
    mcVersion: opts.profile ? opts.profile.mcVersion : null,
    profile: opts.profile ? opts.profile.name : null,
    memoryMb,
    jvmArgs: R(opts.jvmArgs || settings.jvmArgs || ""),
    jvmArgsGame: live && live.system ? live.system.jvmArgs : null,
    options: optionsSubset(optionsText),
  };

  const problems = detectProblems({
    mods, packs, memoryMb, totalMb: opts.totalMb || 8192, live, crashReports, hsErr,
    lastLaunch: opts.lastLaunch, cosmetics: opts.cosmetics, newestMytic: opts.newestMytic,
  });
  const report = {
    created: now.toISOString(),
    system,
    lastLaunch: opts.lastLaunch || null,
    timings: live && live.timings ? live.timings : null,
    live: live ? { playSeconds: live.playSeconds, fpsAvg: live.fpsAvg, fps1Low: live.fps1Low, hitches: live.hitches, maxFrameMs: live.maxFrameMs, gcMaxPerSecondMs: live.gcMaxPerSecondMs, heapMaxPct: live.heapMaxPct, pingAvg: live.pingAvg } : null,
    mods: mods.map(({ provides, ...m }) => m),
    packs,
    crashReports: crashReports.map((c) => ({ name: c.name, at: new Date(c.mtimeMs).toISOString(), description: c.description || null })),
    hsErr: hsErr.map((h) => ({ name: h.name, at: new Date(h.mtimeMs).toISOString() })),
    profiles: profiles.map((p) => p.name),
    problems,
  };
  const text = summaryText(report);
  addText("ZUSAMMENFASSUNG.txt", text);
  addText("diagnose.json", JSON.stringify(redactValue(report, ctx), null, 2));

  await fsp.mkdir(opts.outDir, { recursive: true });
  const name = `Mytic-Diagnose-${stamp(now)}.zip`;
  const zip = new AdmZip();
  for (const e of entries) zip.addFile(e.name, e.data);
  const file = path.join(opts.outDir, name);
  await new Promise((resolve, reject) => zip.writeZip(file, (error) => (error ? reject(error) : resolve())));
  await prune(opts.outDir, KEEP_BUNDLES);
  return { file, name, folder: opts.outDir, problems, report, text: R(text), entries: entries.map((e) => e.name) };
}

/** Alte Pakete aufräumen (nur eigene Mytic-Diagnose-*.zip) */
async function prune(dir, keep) {
  const old = (await newestFiles(dir, /^Mytic-Diagnose-.*\.zip$/, 1000)).slice(keep);
  for (const f of old) await fsp.unlink(f.file).catch(() => {});
}

// ---------------------------------------------------------------------------------------------- Text-Zusammenfassung

const LEVEL = { error: "FEHLER", warn: "WARNUNG", info: "HINWEIS" };
function summaryText(report) {
  const s = report.system || {};
  const lines = [];
  lines.push(`Mytic Diagnose – ${report.created}`);
  lines.push("");
  lines.push("System");
  lines.push(`  OS: ${s.os || "?"} · CPU: ${s.cpu || "?"}${s.cpuCores ? ` (${s.cpuCores} Threads)` : ""} · RAM: ${s.totalMb ? `${(s.totalMb / 1024).toFixed(1)} GB` : "?"}`);
  lines.push(`  GPU: ${s.gpu || "?"}${s.gpuFromLog && s.gpuFromLog !== s.gpu ? ` (im Spiel: ${s.gpuFromLog})` : ""}`);
  lines.push(`  Java: ${s.java || "?"} · Launcher ${s.launcher || "?"} · Mytic-Mod ${s.myticMod || "?"} · Cosmetics ${s.cosmetics || "–"}`);
  lines.push(`  Minecraft ${s.mcVersion || "?"}${s.fabricLoader ? ` (Fabric ${s.fabricLoader})` : ""} · Profil „${s.profile || "?"}“ · RAM für Minecraft ${s.memoryMb} MB${s.jvmArgs ? ` · Java-Argumente: ${s.jvmArgs}` : ""}`);
  if (s.options && Object.keys(s.options).length) lines.push(`  Sichtweite ${s.options.renderDistance || "?"}, Simulation ${s.options.simulationDistance || "?"}, max. FPS ${s.options.maxFps || "?"}, VSync ${s.options.enableVsync || "?"}`);
  const l = report.lastLaunch;
  if (l) lines.push(`  Letzter Start: ${l.at ? new Date(l.at).toISOString() : "?"}, Vorbereitung ${l.prepareMs != null ? ms(l.prepareMs) : "?"}, Spielzeit ${l.durationMs != null ? ms(l.durationMs) : "läuft"}, Exit-Code ${l.exitCode ?? "–"}`);
  const t = report.timings;
  lines.push("");
  lines.push("Zeiten");
  if (t) {
    lines.push(`  Spielstart → Hauptmenü: ${t.gameStartToTitleMs > 0 ? ms(t.gameStartToTitleMs) : "?"}`);
    for (const j of (t.joins || []).slice(-5)) lines.push(`  Join ${j.server || j.kind}: gesamt ${ms(j.totalMs)} (Verbinden ${ms(j.connectMs)}, Welt laden ${ms(j.loadingMs)}), längstes Bild ${ms(j.maxFrameMs)}, danach ${ms(j.afterJoinMaxFrameMs || 0)}`);
    for (const r of (t.reloads || []).slice(-3)) lines.push(`  Ressourcen ${r.initial ? "beim Start" : "neu geladen"}: ${ms(r.ms)} mit ${r.packs} Paketen`);
  } else lines.push("  keine Messung der Mod (ältere Mytic-Mod oder noch nicht gespielt)");
  if (report.live && report.live.playSeconds) {
    const v = report.live;
    lines.push(`  Letzte Sitzung: ${Math.round(v.playSeconds / 60)} min, FPS Ø ${v.fpsAvg} (schlechteste 5 %: ${v.fps1Low}), Hänger ${v.hitches}, längstes Bild ${v.maxFrameMs} ms, Speicher max. ${v.heapMaxPct} %, Ping Ø ${v.pingAvg} ms`);
  }
  lines.push("");
  lines.push(`Erkannte Probleme (${report.problems.length})`);
  if (!report.problems.length) lines.push("  keine");
  for (const p of report.problems) lines.push(`  [${LEVEL[p.level]}] ${p.title} – ${p.detail}`);
  lines.push("");
  const on = report.mods.filter((m) => m.enabled);
  lines.push(`Mods (${on.length} aktiv, ${report.mods.length - on.length} aus)`);
  for (const m of report.mods) lines.push(`  ${m.enabled ? "+" : "-"} ${m.name} ${m.version} [${m.source}${m.id ? `, ${m.id}` : ""}] ${m.file}`);
  lines.push("");
  lines.push(`Texturpakete (${report.packs.length})`);
  for (const p of report.packs) lines.push(`  ${p.active ? "+" : "-"} ${p.name}${p.packFormat !== undefined ? ` pack_format ${JSON.stringify(p.packFormat)}` : ""}${p.minFormat !== undefined ? ` min ${JSON.stringify(p.minFormat)} max ${JSON.stringify(p.maxFormat)}` : ""}${p.issue ? ` ⚠ ${p.issue}` : ""}`);
  if (report.crashReports.length || report.hsErr.length) {
    lines.push("");
    lines.push("Abstürze");
    for (const c of report.crashReports) lines.push(`  ${c.name}${c.description ? ` – ${c.description}` : ""}`);
    for (const h of report.hsErr) lines.push(`  ${h.name}`);
  }
  if (report.profiles.length) lines.push("", `Diagnose-Messungen: ${report.profiles.join(", ")}`);
  return lines.join("\n");
}

// ---------------------------------------------------------------------------------------------- Launcher-Log

/** Launcher-Log in launcher-logs/launcher.log (bei 2 MB → launcher.old.log), gepuffert und geschwärzt beim Export */
class LauncherLog {
  constructor(dir, { maxBytes = 2 * 1024 * 1024 } = {}) {
    this.dir = dir;
    this.file = path.join(dir, "launcher.log");
    this.old = path.join(dir, "launcher.old.log");
    this.maxBytes = maxBytes;
    this.pending = [];
    this.timer = null;
  }

  append(line) {
    const d = new Date();
    this.pending.push(`[${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}] ${String(line).replace(/\r?\n/g, " ")}`);
    if (!this.timer) {
      this.timer = setTimeout(() => this.flush().catch(() => {}), 1000);
      if (this.timer.unref) this.timer.unref();
    }
  }

  async flush() {
    clearTimeout(this.timer);
    this.timer = null;
    if (!this.pending.length) return;
    const text = `${this.pending.splice(0).join("\n")}\n`;
    await fsp.mkdir(this.dir, { recursive: true });
    try {
      if ((await fsp.stat(this.file)).size > this.maxBytes) await fsp.rename(this.file, this.old);
    } catch {
      // neue Datei
    }
    await fsp.appendFile(this.file, text);
  }

  files() {
    return [this.old, this.file].filter((f) => fs.existsSync(f));
  }
}

module.exports = { redactText, redactValue, readZipEntry, modInfo, checkPackMeta, activePacks, listPacks, listMods, detectProblems, parseLogInfo, optionsSubset, collect, summaryText, LauncherLog, stamp };
