// Auto-Fix: erkennt Fehlermeldungen des Fabric Loaders (und Absturzberichte) und behebt sie per Klick.
// - Mod für eine andere Minecraft-Version / Versionskonflikt → passende Version von Modrinth, sonst deaktivieren
// - fehlende Abhängigkeit → bei Modrinth nachsehen, was die Mod braucht, und passend installieren
// - doppelte Mods → nur die neueste behalten
// - Absturz mit "Suspected Mods" → diese Mods deaktivieren
// Integrierte Mods des Launchers (Sodium, Fabric API …) werden nie angefasst – die sind immer passend.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const AdmZip = require("adm-zip");
const { getJson, sha1File } = require("./download");

const API = "https://api.modrinth.com/v2";
/** Kennungen, die keine Mods aus dem Mods-Ordner sind. */
const SYSTEM_IDS = new Set(["minecraft", "fabricloader", "java", "fabric", "mixinextras"]);
/** Bekannte Abweichungen zwischen Mod-ID und Modrinth-Projekt. */
const ID_TO_SLUG = {
  yet_another_config_lib_v3: "yacl",
  "cloth-config2": "cloth-config",
  architectury: "architectury-api",
  owo: "owo-lib",
  ferritecore: "ferrite-core",
  "fabric-api": "fabric-api",
};

// ---------------------------------------------------------------------------------------------- Erkennen

/** Liest die Fehlermeldung des Fabric Loaders (und ggf. Absturzbericht) und beschreibt die Probleme. */
function parse(text) {
  const problems = [];
  const seen = new Set();
  const add = (p) => {
    const key = `${p.kind}:${p.id}`;
    if (!seen.has(key)) {
      seen.add(key);
      problems.push(p);
    }
  };
  const lines = String(text || "").split(/\r?\n/).map((l) => l.trim().replace(/^- /, ""));
  for (const line of lines) {
    let m;
    // Mod 'A' (a) 1.0 requires version X of mod 'B' (b), which is missing!
    if ((m = /^Mod '(.+?)' \(([\w.-]+)\) (\S+) requires .*? of (?:mod '(.+?)' \()?([\w.-]+)\)?, which is missing!/.exec(line))) {
      add({ kind: "missing", id: m[5], name: m[4] || m[5], by: m[2], byName: m[1], text: line });
      continue;
    }
    // Mod 'A' (a) 1.0 requires version 1.21.4 of minecraft, but only the wrong version is present: 1.21.11!
    if ((m = /^Mod '(.+?)' \(([\w.-]+)\) (\S+) requires .*? of (?:mod '.+?' \()?(minecraft|java|fabricloader)\)?, but only the wrong version is present/.exec(line))) {
      add({ kind: "wrong-game", id: m[2], name: m[1], version: m[3], text: line });
      continue;
    }
    // Mod 'A' (a) requires any 0.6.x version of mod 'B' (b), but only the wrong version is present
    if ((m = /^Mod '(.+?)' \(([\w.-]+)\) (\S+) requires .*? of (?:mod '(.+?)' \()?([\w.-]+)\)?, but only the wrong version is present/.exec(line))) {
      add({ kind: "conflict", id: m[2], name: m[1], version: m[3], other: m[5], otherName: m[4] || m[5], text: line });
      continue;
    }
    // Mod 'B' (b) is incompatible with ... of mod 'A' (a), yet a conflicting version is present
    if ((m = /^Mod '(.+?)' \(([\w.-]+)\) (\S+) is incompatible with .*? of (?:mod '(.+?)' \()?([\w.-]+)\)?, yet a conflicting version is present/.exec(line))) {
      add({ kind: "conflict", id: m[5], name: m[4] || m[5], other: m[2], otherName: m[1], text: line });
      continue;
    }
    // doppelte Mods
    if ((m = /Mod ID '([\w.-]+)' is (?:provided by|present in) multiple|Duplicate mod(?:s)?.*?'?([\w.-]+)'?/i.exec(line)) && /duplicate|multiple/i.test(line)) {
      const id = m[1] || m[2];
      if (id) add({ kind: "duplicate", id, name: id, text: line });
      continue;
    }
    // Absturzbericht: "Suspected Mods: Iris (iris), Sodium (sodium)"
    if ((m = /^Suspected Mods?: (.+)$/i.exec(line)) && !/none|unknown/i.test(m[1])) {
      for (const part of m[1].matchAll(/([^,(]+?)\s*\(([\w.-]+)\)/g)) add({ kind: "suspect", id: part[2], name: part[1].trim(), text: line });
    }
  }
  return problems.filter((p) => !SYSTEM_IDS.has(p.id));
}

// ---------------------------------------------------------------------------------------------- Mods-Ordner

/** Mod-IDs der Jars im Mods-Ordner (fabric.mod.json) → Datei, Name, Version. */
function indexMods(modsDir) {
  const index = {};
  let files = [];
  try {
    files = fs.readdirSync(modsDir).filter((f) => f.endsWith(".jar"));
  } catch {
    return index;
  }
  for (const file of files) {
    try {
      const zip = new AdmZip(path.join(modsDir, file));
      const entry = zip.getEntry("fabric.mod.json");
      if (!entry) continue;
      const meta = JSON.parse(zip.readAsText(entry).replace(/^﻿/, ""));
      if (!meta.id) continue;
      (index[meta.id] = index[meta.id] || []).push({ file, name: meta.name || meta.id, version: meta.version || "" });
    } catch {
      // keine lesbare Mod
    }
  }
  return index;
}

/** Dateien, die der Launcher selbst verwaltet (integrierte Mods, Mytic-Mod) – stehen in .mytic-managed.json. */
function managedFiles(modsDir) {
  try {
    return new Set(JSON.parse(fs.readFileSync(path.join(modsDir, ".mytic-managed.json"), "utf8")).map((f) => f.file));
  } catch {
    return new Set();
  }
}

/** Plan für die Oberfläche: was der Auto-Fix tun wird. Integrierte Mods (nach Datei) werden nicht angefasst. */
function plan(problems, modsDir) {
  const all = indexMods(modsDir);
  const managed = managedFiles(modsDir);
  const user = {};
  for (const [id, list] of Object.entries(all)) {
    const own = list.filter((e) => !managed.has(e.file));
    if (own.length) user[id] = own;
  }
  const steps = [];
  const done = new Set();
  const step = (s) => {
    const key = `${s.action}:${s.id}`;
    if (!done.has(key)) {
      done.add(key);
      steps.push(s);
    }
  };
  for (const p of problems) {
    if (p.kind === "missing") {
      step({ action: "install", id: p.id, name: p.name, for: p.by, forName: p.byName, label: `${p.name} installieren (wird von ${p.byName} gebraucht)` });
    } else if (p.kind === "wrong-game" || p.kind === "conflict" || p.kind === "suspect") {
      // Konflikt mit einer integrierten Mod: die eigene Mod ist schuld
      let id = p.id;
      let name = p.name;
      if (!user[id] && p.other && user[p.other]) {
        id = p.other;
        name = p.otherName;
      }
      if (!user[id]) continue;
      // gleiche Mod zusätzlich integriert (z. B. eigene alte Iris neben der integrierten): eigene Datei entfernen
      if (all[id].length > user[id].length) {
        step({ action: "dedupe", id, name, files: user[id].map((e) => e.file), keepNone: true, label: `${name}: eigene Datei deaktivieren – der Launcher bringt die passende Version schon mit` });
        continue;
      }
      const action = p.kind === "suspect" ? "disable" : "update";
      step({ action, id, name, files: user[id].map((e) => e.file), label: action === "disable" ? `${name} deaktivieren (wahrscheinlich Absturzursache)` : `${name} auf die passende Version bringen (sonst deaktivieren)` });
    } else if (p.kind === "duplicate" && all[p.id] && all[p.id].length > 1) {
      const own = user[p.id] || [];
      const keepNone = own.length < all[p.id].length;
      step({ action: "dedupe", id: p.id, name: all[p.id][0].name, files: (keepNone ? own : all[p.id]).map((e) => e.file), keepNone, label: `Doppelte Datei von ${all[p.id][0].name} entfernen` });
    }
  }
  return steps;
}

// ---------------------------------------------------------------------------------------------- Beheben

async function identify(file) {
  const sha1 = await sha1File(file);
  try {
    return await getJson(`${API}/version_file/${sha1}?algorithm=sha1`);
  } catch {
    return null;
  }
}

/** Modrinth-Projekt zu einer Mod-ID finden (Projekt-Slug ist meist die Mod-ID). */
async function projectForId(id) {
  const candidates = [ID_TO_SLUG[id], id, id.replace(/_/g, "-"), id.replace(/[-_]/g, "")].filter(Boolean);
  for (const slug of [...new Set(candidates)]) {
    try {
      return await getJson(`${API}/project/${encodeURIComponent(slug)}`);
    } catch {
      // nächster Versuch
    }
  }
  return null;
}

async function disableFile(modsDir, file) {
  const from = path.join(modsDir, file);
  await fsp.rename(from, `${from}.disabled`).catch(() => {});
}

/**
 * Führt den Plan aus. ctx: { modsDir, browser (ModBrowser des Profils), log }.
 * Gibt eine Liste mit Ergebnissen (Text) zurück.
 */
async function apply(steps, { modsDir, browser, log = () => {} }) {
  const results = [];
  const state = await browser.state();
  const trackedByFile = {};
  for (const entry of Object.values(state.mods)) trackedByFile[entry.file] = entry;

  for (const s of steps) {
    try {
      if (s.action === "install") {
        // Was braucht die Mod laut Modrinth? (genauer als die Mod-ID)
        const requiring = indexMods(modsDir)[s.for];
        let installed = [];
        if (requiring && requiring[0]) {
          const version = await identify(path.join(modsDir, requiring[0].file));
          const deps = (version && version.dependencies || []).filter((d) => d.dependency_type === "required" && d.project_id);
          for (const dep of deps) installed.push(...(await browser.install(dep.project_id)));
        }
        if (!installed.length) {
          const project = await projectForId(s.id);
          if (!project) throw new Error(`${s.name} nicht bei Modrinth gefunden`);
          installed = await browser.install(project.id);
        }
        if (!installed.length) continue; // schon durch einen vorherigen Schritt installiert
        results.push(`✓ Installiert: ${installed.join(", ")}`);
      } else if (s.action === "update") {
        let fixed = false;
        for (const file of s.files) {
          const tracked = trackedByFile[file];
          const projectId = tracked ? tracked.id : (await identify(path.join(modsDir, file)))?.project_id;
          const latest = projectId ? await browser.latestVersion(projectId) : null;
          if (latest) {
            if (tracked) {
              await browser.writeVersion(tracked, latest.version, latest.file);
            } else {
              // bisher unbekannte Jar: neue Version laden und im Mod-Browser eintragen (wird künftig aktualisiert)
              const project = await getJson(`${API}/project/${projectId}`);
              const entry = { id: project.id, slug: project.slug, title: project.title, icon: project.icon_url || "", enabled: true, dependency: false, requiredBy: [], file };
              await browser.writeVersion(entry, latest.version, latest.file);
              const fresh = await browser.state();
              fresh.mods[project.id] = entry;
              await browser.save(fresh);
            }
            results.push(`✓ ${s.name} auf ${latest.version.version_number} aktualisiert`);
            fixed = true;
          } else {
            if (tracked) await browser.setEnabled(tracked.id, false);
            else await disableFile(modsDir, file);
            results.push(`✓ ${s.name} deaktiviert – keine passende Version für diese Minecraft-Version`);
            fixed = true;
          }
        }
        if (!fixed) results.push(`– ${s.name}: nichts zu tun`);
      } else if (s.action === "disable") {
        for (const file of s.files) {
          const tracked = trackedByFile[file];
          if (tracked) await browser.setEnabled(tracked.id, false);
          else await disableFile(modsDir, file);
        }
        results.push(`✓ ${s.name} deaktiviert`);
      } else if (s.action === "dedupe") {
        // keepNone: die integrierte Version bleibt, alle eigenen Dateien gehen; sonst die neueste behalten
        const files = s.files
          .map((f) => ({ f, t: fs.statSync(path.join(modsDir, f)).mtimeMs }))
          .sort((x, y) => y.t - x.t);
        const drop = s.keepNone ? files : files.slice(1);
        for (const { f } of drop) {
          const tracked = trackedByFile[f];
          if (tracked) await browser.setEnabled(tracked.id, false);
          else await disableFile(modsDir, f);
        }
        results.push(`✓ ${s.name}: ${drop.length} doppelte Datei(en) deaktiviert`);
      }
    } catch (error) {
      results.push(`✗ ${s.name}: ${error.message}`);
    }
    log(`[Auto-Fix] ${results[results.length - 1]}`);
  }
  return results;
}

module.exports = { parse, plan, apply, indexMods, projectForId, managedFiles };
