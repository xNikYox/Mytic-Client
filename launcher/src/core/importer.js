// Profile aus anderen Launchern übernehmen: Modrinth App, NoRiskClient (NRC), Lunar Client und Modrinth-Modpacks (.mrpack).
// Übernommen werden Minecraft-Version und Mods. Die Mods werden über ihre Prüfsumme bei Modrinth wiedererkannt,
// damit der Mod-Browser sie anzeigt und beim Spielstart passend zur Version aktualisiert.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const AdmZip = require("adm-zip");
const { getJson, downloadFile, sha1File } = require("./download");
const profiles = require("./profiles");

const API = "https://api.modrinth.com/v2";
/** Erlaubte Download-Quellen (wie im .mrpack-Format von Modrinth festgelegt). */
const ALLOWED_HOSTS = ["https://cdn.modrinth.com/", "https://github.com/", "https://raw.githubusercontent.com/", "https://gitlab.com/"];
const allowed = (url) => typeof url === "string" && ALLOWED_HOSTS.some((h) => url.startsWith(h));

function dataHome(platform, home) {
  if (platform === "win32") return process.env.APPDATA || path.join(home, "AppData", "Roaming");
  if (platform === "darwin") return path.join(home, "Library", "Application Support");
  return process.env.XDG_DATA_HOME || path.join(home, ".local", "share");
}

/** Wo die anderen Launcher ihre Daten ablegen. */
function sourcePaths({ platform = process.platform, home = os.homedir() } = {}) {
  const data = dataHome(platform, home);
  const norisk = platform === "win32" ? path.join(data, "norisk", "NoRiskClientV3")
    : platform === "darwin" ? path.join(data, "gg.norisk.NoRiskClientV3") : path.join(data, "noriskclientv3");
  return {
    modrinth: path.join(data, "ModrinthApp"),
    noriskData: platform === "win32" ? path.join(norisk, "data") : norisk,
    noriskMeta: path.join(norisk, "meta"),
    lunar: path.join(home, ".lunarclient"),
  };
}

/** Liest eine SQLite-Datenbank über eine Kopie (der andere Launcher kann sie gerade offen haben). */
function withDb(file, fn) {
  if (!fs.existsSync(file)) return null;
  let sqlite;
  try {
    sqlite = require("node:sqlite");
  } catch {
    return null;
  }
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-import-"));
  try {
    for (const suffix of ["", "-wal", "-shm"]) {
      if (fs.existsSync(file + suffix)) fs.copyFileSync(file + suffix, path.join(tmp, `app.db${suffix}`));
    }
    const db = new sqlite.DatabaseSync(path.join(tmp, "app.db"));
    try {
      return fn(db);
    } finally {
      db.close();
    }
  } catch {
    return null;
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

/** Erste Abfrage, die funktioniert (die Launcher ändern ihr Datenbank-Schema gelegentlich). */
function firstQuery(db, queries) {
  for (const sql of queries) {
    try {
      return db.prepare(sql).all();
    } catch {
      // nächste Variante
    }
  }
  return null;
}

function jarsIn(dir) {
  try {
    return fs.readdirSync(dir).filter((f) => f.toLowerCase().endsWith(".jar")).map((f) => path.join(dir, f));
  } catch {
    return [];
  }
}

function subdirs(dir) {
  try {
    return fs.readdirSync(dir, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name);
  } catch {
    return [];
  }
}

/** Loader eines Mods anhand der Metadaten in der Jar. */
function loaderOfJar(file) {
  try {
    const zip = new AdmZip(file);
    if (zip.getEntry("fabric.mod.json")) return "fabric";
    if (zip.getEntry("quilt.mod.json")) return "quilt";
    if (zip.getEntry("META-INF/neoforge.mods.toml")) return "neoforge";
    if (zip.getEntry("META-INF/mods.toml") || zip.getEntry("mcmod.info")) return "forge";
  } catch {
    // keine lesbare Jar
  }
  return "unknown";
}

function normLoader(value) {
  const v = String(value || "").toLowerCase().replace(/[^a-z]/g, "");
  if (!v || v === "vanilla" || v === "none") return "vanilla";
  if (v.includes("neoforge")) return "neoforge";
  if (v.includes("fabric")) return "fabric";
  if (v.includes("quilt")) return "quilt";
  if (v.includes("forge")) return "forge";
  return v;
}

/** Häufigster Loader der Mods (für Quellen ohne Angabe). */
function guessLoader(jars) {
  const count = {};
  for (const jar of jars) {
    const l = loaderOfJar(jar);
    if (l !== "unknown") count[l] = (count[l] || 0) + 1;
  }
  return Object.entries(count).sort((a, b) => b[1] - a[1])[0]?.[0] || "unknown";
}

// ---------------------------------------------------------------------------------------------- Quellen

function scanModrinth(paths) {
  const base = paths.modrinth;
  let rows = withDb(path.join(base, "app.db"), (db) => firstQuery(db, [
    // neues Schema (Instanzen mit Inhalts-Sets)
    `SELECT i.path AS path, i.name AS name, c.game_version AS game_version, c.loader AS loader
       FROM instances i LEFT JOIN instance_content_sets c ON c.id = i.applied_content_set_id`,
    // älteres Schema
    "SELECT path, name, game_version, mod_loader AS loader FROM profiles",
  ]));
  if (!rows) rows = subdirs(path.join(base, "profiles")).map((name) => ({ path: name, name, game_version: null, loader: null }));
  return rows.map((row) => {
    const jars = jarsIn(path.join(base, "profiles", row.path, "mods"));
    return {
      id: `modrinth:${row.path}`,
      source: "Modrinth App",
      name: row.name || row.path,
      mcVersion: row.game_version || null,
      loader: row.loader ? normLoader(row.loader) : guessLoader(jars),
      jars,
      downloads: [],
    };
  });
}

/** Mod-Quelle aus der NoRisk-Datenbank (serde-JSON, verschiedene Schreibweisen) als Download. */
function noriskDownload(sourceJson) {
  let source;
  try {
    source = JSON.parse(sourceJson);
  } catch {
    return null;
  }
  const inner = source && (source.Modrinth || source.modrinth || (String(source.type || "").toLowerCase() === "modrinth" ? source : null));
  if (!inner || !allowed(inner.download_url) || !inner.file_name) return null;
  return { url: inner.download_url, filename: path.basename(inner.file_name), sha1: inner.file_hash_sha1 || undefined };
}

function scanNoRisk(paths) {
  const data = withDb(path.join(paths.noriskMeta, "app.db"), (db) => ({
    profiles: firstQuery(db, ["SELECT id, name, path, game_version, loader, is_standard_version FROM profiles", "SELECT id, name, path, game_version, loader FROM profiles"]) || [],
    mods: firstQuery(db, ["SELECT profile_id, source, enabled FROM profile_mods"]) || [],
  }));
  if (!data) return [];
  return data.profiles.filter((row) => !row.is_standard_version).map((row) => {
    const dir = [path.join(paths.noriskData, "profiles", row.path || ""), path.join(paths.noriskData, "profiles", row.name || "")].find((d) => fs.existsSync(d));
    const jars = dir ? jarsIn(path.join(dir, "mods")) : [];
    const names = new Set(jars.map((j) => path.basename(j)));
    const downloads = [];
    for (const mod of data.mods.filter((m) => m.profile_id === row.id && m.enabled !== 0)) {
      const d = noriskDownload(mod.source);
      if (d && !names.has(d.filename)) {
        names.add(d.filename);
        downloads.push(d);
      }
    }
    return {
      id: `norisk:${row.id}`,
      source: "NoRiskClient",
      name: row.name || row.path,
      mcVersion: row.game_version || null,
      loader: normLoader(row.loader),
      jars,
      downloads,
    };
  });
}

/** Lunar Client: eigene Mods liegen in .lunarclient/profiles/<art>/<version>/mods (ggf. mit Unterordnern je Version). */
function scanLunar(paths) {
  const root = path.join(paths.lunar, "profiles");
  const entries = [];
  for (const type of subdirs(root)) {
    for (const version of subdirs(path.join(root, type))) {
      const mods = path.join(root, type, version, "mods");
      const groups = [{ key: "", dir: mods, version }];
      for (const sub of subdirs(mods)) {
        const match = sub.match(/(\d+\.\d+(?:\.\d+)?)/);
        groups.push({ key: sub, dir: path.join(mods, sub), version: match ? match[1] : version });
      }
      for (const group of groups) {
        const jars = jarsIn(group.dir);
        if (!jars.length) continue;
        entries.push({
          id: `lunar:${type}/${version}/${group.key}`,
          source: "Lunar Client",
          name: `Lunar ${group.version}`,
          mcVersion: group.version,
          loader: guessLoader(jars),
          jars,
          downloads: [],
        });
      }
    }
  }
  return entries;
}

/** Modrinth-Modpack (.mrpack): Mods aus dem Index plus mitgelieferte Jars aus overrides/mods. */
function readMrpack(file) {
  const zip = new AdmZip(file);
  const index = JSON.parse(zip.readAsText("modrinth.index.json"));
  const deps = index.dependencies || {};
  const loader = deps["fabric-loader"] ? "fabric" : deps["quilt-loader"] ? "quilt" : deps.neoforge ? "neoforge" : deps.forge ? "forge" : "vanilla";
  const downloads = (index.files || [])
    .filter((f) => /^mods\/[^/]+\.jar$/i.test(f.path || "") && !(f.env && f.env.client === "unsupported"))
    .map((f) => ({ url: (f.downloads || []).find(allowed), filename: path.basename(f.path), sha1: f.hashes && f.hashes.sha1, size: f.fileSize }))
    .filter((d) => d.url);
  const packJars = zip.getEntries()
    .filter((e) => /^(client-)?overrides\/mods\/[^/]+\.jar$/i.test(e.entryName))
    .map((e) => e.entryName);
  return {
    id: `file:${file}`,
    source: "Modpack-Datei",
    name: index.name || path.basename(file, ".mrpack"),
    mcVersion: deps.minecraft || null,
    loader,
    jars: [],
    downloads,
    packFile: file,
    packJars,
  };
}

/** Minecraft-Version aus den Metadaten der Mods (fabric.mod.json bzw. mcmod.info), die häufigste gewinnt. */
function guessVersion(jars) {
  const count = {};
  for (const jar of jars.slice(0, 60)) {
    let found = null;
    try {
      const zip = new AdmZip(jar);
      const fabric = zip.getEntry("fabric.mod.json");
      if (fabric) {
        const meta = JSON.parse(zip.readAsText(fabric).replace(/^﻿/, ""));
        const dep = meta.depends && meta.depends.minecraft;
        const text = Array.isArray(dep) ? dep.join(" ") : String(dep || "");
        const m = text.match(/(\d+\.\d+(?:\.\d+)?)/);
        if (m) found = m[1];
      }
      const info = !found && zip.getEntry("mcmod.info");
      if (info) {
        const m = zip.readAsText(info).match(/"mcversion"\s*:\s*"(\d+\.\d+(?:\.\d+)?)/);
        if (m) found = m[1];
      }
    } catch {
      // keine lesbare Jar
    }
    if (found) count[found] = (count[found] || 0) + 1;
  }
  return Object.entries(count).sort((a, b) => b[1] - a[1])[0]?.[0] || null;
}

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8").replace(/^﻿/, ""));
  } catch {
    return null;
  }
}

/**
 * Beliebiger Profil-Ordner vom PC: Modrinth/NRC/Lunar-Profil, CurseForge-Instanz, Prism/MultiMC-Instanz,
 * .minecraft-Ordner oder einfach ein Ordner mit Mods.
 */
function readFolder(dir) {
  let name = path.basename(dir);
  let mcVersion = null;
  let loader = null;
  const cf = readJson(path.join(dir, "minecraftinstance.json"));
  if (cf) {
    name = cf.name || name;
    mcVersion = cf.gameVersion || null;
    loader = cf.baseModLoader && cf.baseModLoader.name ? normLoader(cf.baseModLoader.name.split("-")[0]) : null;
  }
  const mmc = readJson(path.join(dir, "mmc-pack.json"));
  if (mmc && Array.isArray(mmc.components)) {
    for (const c of mmc.components) {
      if (c.uid === "net.minecraft") mcVersion = c.version || mcVersion;
      if (/fabric-loader/.test(c.uid)) loader = "fabric";
      if (/minecraftforge/.test(c.uid)) loader = "forge";
      if (/neoforge/.test(c.uid)) loader = "neoforge";
      if (/quilt/.test(c.uid)) loader = "quilt";
    }
  }
  const theseus = readJson(path.join(dir, "profile.json"));
  if (theseus && theseus.metadata) {
    name = theseus.metadata.name || name;
    mcVersion = theseus.metadata.game_version || mcVersion;
    loader = theseus.metadata.loader ? normLoader(theseus.metadata.loader) : loader;
  }
  const modsDir = [path.join(dir, "mods"), path.join(dir, ".minecraft", "mods"), path.join(dir, "minecraft", "mods"), dir]
    .find((d) => jarsIn(d).length) || path.join(dir, "mods");
  const jars = jarsIn(modsDir);
  // Lunar-Unterordner wie "fabric-1.21.4" verraten die Version
  if (!mcVersion) {
    const m = path.basename(modsDir).match(/(\d+\.\d+(?:\.\d+)?)/) || name.match(/(\d+\.\d+(?:\.\d+)?)/);
    mcVersion = m ? m[1] : guessVersion(jars);
  }
  return { id: `dir:${dir}`, source: "Ordner", name, mcVersion, loader: loader || guessLoader(jars), jars, downloads: [] };
}

/** Datei vom PC: .mrpack, .zip (Modpack-Export mit mods-Ordner) oder einzelne .jar-Mods. */
function readFiles(files) {
  const entries = [];
  const jars = files.filter((f) => f.toLowerCase().endsWith(".jar"));
  if (jars.length) {
    entries.push({ id: `jars:${jars.join("|")}`, source: "Mod-Dateien", name: jars.length === 1 ? path.basename(jars[0], ".jar") : `${jars.length} Mods`, mcVersion: guessVersion(jars), loader: guessLoader(jars), jars, downloads: [] });
  }
  for (const file of files.filter((f) => !f.toLowerCase().endsWith(".jar"))) {
    const zip = new AdmZip(file);
    if (zip.getEntry("modrinth.index.json")) {
      entries.push(readMrpack(file));
      continue;
    }
    const packJars = zip.getEntries().filter((e) => /(^|\/)mods\/[^/]+\.jar$/i.test(e.entryName)).map((e) => e.entryName);
    const cf = zip.getEntry("manifest.json") ? JSON.parse(zip.readAsText("manifest.json")) : null;
    const mc = cf && cf.minecraft;
    const loaderId = mc && mc.modLoaders && mc.modLoaders[0] && mc.modLoaders[0].id;
    entries.push({
      id: `zip:${file}`,
      source: cf ? "CurseForge-Export" : "ZIP-Datei",
      name: (cf && cf.name) || path.basename(file).replace(/\.zip$/i, ""),
      mcVersion: (mc && mc.version) || null,
      loader: loaderId ? normLoader(loaderId.split("-")[0]) : "unknown",
      jars: [],
      downloads: [],
      packFile: file,
      packJars,
    });
  }
  return entries;
}

/** Ordner oder Dateien vom PC (Dialog oder Drag & Drop). */
function readPaths(paths) {
  const dirs = paths.filter((p) => {
    try {
      return fs.statSync(p).isDirectory();
    } catch {
      return false;
    }
  });
  const files = paths.filter((p) => !dirs.includes(p) && fs.existsSync(p));
  return [...dirs.map(readFolder), ...readFiles(files)];
}

// ---------------------------------------------------------------------------------------------- Bewertung

/** Passende Mytic-Version: exakt, oder bei "1.21" die neueste 1.21.x. */
function resolveVersion(version, available) {
  if (!version) return null;
  if (available.includes(version)) return version;
  if (/^\d+\.\d+$/.test(version)) {
    const same = available.filter((v) => v === version || v.startsWith(`${version}.`));
    if (same.length) return same.sort((a, b) => b.localeCompare(a, undefined, { numeric: true }))[0];
  }
  return null;
}

const LOADER_NAMES = { fabric: "Fabric", forge: "Forge", neoforge: "NeoForge", quilt: "Quilt" };

function evaluate(entry, available, loaderFor, chosenVersion = null) {
  const modCount = entry.jars.length + entry.downloads.length + (entry.packJars ? entry.packJars.length : 0);
  const base = { id: entry.id, source: entry.source, name: entry.name, mcVersion: entry.mcVersion, loader: entry.loader, modCount };
  const target = chosenVersion && available.includes(chosenVersion) ? chosenVersion : resolveVersion(entry.mcVersion, available);
  // Version unbekannt oder nicht unterstützt: der Spieler kann selbst eine wählen
  if (!target) return { ...base, target: null, chooseVersion: true, reason: entry.mcVersion ? `Minecraft ${entry.mcVersion} wird nicht unterstützt – Version wählen` : "Minecraft-Version unbekannt – bitte wählen" };
  const need = loaderFor(target);
  if (modCount > 0 && !["unknown", "vanilla", need].includes(entry.loader)) {
    return { ...base, target: null, reason: `${LOADER_NAMES[entry.loader] || entry.loader}-Mods laufen hier nicht (${target} nutzt ${LOADER_NAMES[need]})` };
  }
  return { ...base, target, reason: null };
}

/** Alle gefundenen Profile anderer Launcher. */
function scan({ paths = sourcePaths(), available = [], loaderFor }) {
  const found = [];
  for (const fn of [scanModrinth, scanNoRisk, scanLunar]) {
    try {
      found.push(...fn(paths));
    } catch {
      // Quelle nicht lesbar: überspringen
    }
  }
  return { entries: found, list: found.map((e) => evaluate(e, available, loaderFor)) };
}

// ---------------------------------------------------------------------------------------------- Import

/** Mods über ihre SHA-1-Prüfsumme bei Modrinth erkennen und im Mod-Browser eintragen. */
async function identify(modsDir, browser, log) {
  const files = jarsIn(modsDir);
  const result = { identified: 0, managed: 0 };
  if (!files.length) return result;
  const byHash = {};
  for (const file of files) byHash[await sha1File(file)] = file;
  let versions;
  try {
    versions = await getJson(`${API}/version_files`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ hashes: Object.keys(byHash), algorithm: "sha1" }),
    });
  } catch (error) {
    log(`[Import] Mods konnten nicht bei Modrinth erkannt werden: ${error.message}`);
    return result;
  }
  const ids = [...new Set(Object.values(versions).map((v) => v.project_id))];
  if (!ids.length) return result;
  const projects = {};
  for (const p of await getJson(`${API}/projects?ids=${encodeURIComponent(JSON.stringify(ids))}`)) projects[p.id] = p;
  const state = await browser.state();
  for (const [hash, version] of Object.entries(versions)) {
    const file = byHash[hash];
    const project = projects[version.project_id];
    if (!file || !project) continue;
    // Fabric API, Sodium usw. bringt der Launcher selbst mit
    if (await browser.isManaged(project)) {
      await fsp.rm(file, { force: true });
      result.managed++;
      continue;
    }
    if (state.mods[project.id]) continue;
    state.mods[project.id] = {
      id: project.id,
      slug: project.slug,
      title: project.title,
      icon: project.icon_url || "",
      enabled: true,
      dependency: false,
      requiredBy: [],
      file: path.basename(file),
      versionId: version.id,
      versionNumber: version.version_number,
    };
    result.identified++;
  }
  await browser.save(state);
  return result;
}

/**
 * Legt ein neues Mytic-Profil an und übernimmt die Mods.
 * ctx: { settings, base, loaderFor, browserFor(profile), log }
 */
async function importEntry(entry, evaluation, ctx) {
  const { base, loaderFor, log = () => {} } = ctx;
  if (!evaluation.target) throw new Error(evaluation.reason || "Dieses Profil lässt sich nicht übernehmen.");
  const settings = await profiles.create(ctx.settings, base, evaluation.name, null, evaluation.target);
  const profile = settings.profiles[settings.profiles.length - 1];
  const modsDir = profiles.modsDir(base, profile.id);
  const need = loaderFor(evaluation.target);
  let copied = 0;
  let skipped = 0;
  for (const jar of entry.jars) {
    const loader = loaderOfJar(jar);
    if (loader !== "unknown" && loader !== need) {
      skipped++;
      continue;
    }
    await fsp.copyFile(jar, path.join(modsDir, path.basename(jar)));
    copied++;
  }
  for (const d of entry.downloads) {
    const target = path.join(modsDir, d.filename);
    if (fs.existsSync(target) || !allowed(d.url)) continue;
    try {
      await downloadFile(d.url, target, { sha1: d.sha1, size: d.size });
      copied++;
    } catch (error) {
      log(`[Import] ${d.filename}: ${error.message}`);
      skipped++;
    }
  }
  if (entry.packFile && entry.packJars && entry.packJars.length) {
    const zip = new AdmZip(entry.packFile);
    for (const name of entry.packJars) {
      const data = zip.readFile(name);
      if (!data) continue;
      await fsp.writeFile(path.join(modsDir, path.basename(name)), data);
      copied++;
    }
  }
  const { identified, managed } = await identify(modsDir, ctx.browserFor(profile), log);
  copied -= managed;
  log(`[Import] ${evaluation.source} „${evaluation.name}“ → Profil „${profile.name}“ (${evaluation.target}): ${copied} Mods, ${identified} bei Modrinth erkannt${skipped ? `, ${skipped} übersprungen` : ""}`);
  return { settings, profile, copied, identified, skipped };
}

module.exports = { sourcePaths, scan, readMrpack, readFolder, readFiles, readPaths, guessVersion, evaluate, resolveVersion, importEntry, loaderOfJar, normLoader, noriskDownload };
