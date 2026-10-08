// Profile: jedes Profil hat einen eigenen Mods-Ordner und eine eigene Auswahl an Performance-Mods.
// Welten, Optionen, Server-Liste, Texturpakete und Mod-Configs teilen sich alle Profile (gemeinsamer Spielordner).
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");

const COLORS = ["#9b5cff", "#3ee6d0", "#ff6fb5", "#ffcf5a", "#4da3ff", "#5be38a", "#ff9f43", "#ff5c72"];

function profileDir(base, id) {
  return path.join(base, "profiles", id);
}

function modsDir(base, id) {
  return path.join(profileDir(base, id), "mods");
}

function cleanName(name) {
  const value = String(name || "").replace(/\s+/g, " ").trim().slice(0, 24);
  if (!value) throw new Error("Bitte einen Namen eingeben.");
  return value;
}

function newId(existing) {
  let id;
  do id = crypto.randomBytes(4).toString("hex");
  while (existing.some((p) => p.id === id));
  return id;
}

/** Legt beim ersten Start das Profil "Standard" an und zieht die bisherigen Mods dorthin um. */
async function migrate(settings, base, gameDir) {
  if (Array.isArray(settings.profiles) && settings.profiles.length) return settings;
  const standard = { id: "standard", name: "Standard", color: COLORS[0], perf: settings.mods || {}, created: Date.now() };
  const oldMods = path.join(gameDir, "mods");
  const target = modsDir(base, standard.id);
  await fsp.mkdir(target, { recursive: true });
  try {
    for (const name of await fsp.readdir(oldMods)) {
      await fsp.rename(path.join(oldMods, name), path.join(target, name)).catch(() => {});
    }
  } catch {
    // noch keine Mods vorhanden
  }
  return { ...settings, profiles: [standard], activeProfile: standard.id };
}

async function countMods(base, id) {
  try {
    const state = JSON.parse(await fsp.readFile(path.join(modsDir(base, id), ".mytic-user.json"), "utf8"));
    return Object.values(state.mods || {}).filter((m) => !m.dependency).length;
  } catch {
    return 0;
  }
}

async function create(settings, base, name, copyFrom) {
  const profiles = settings.profiles;
  const id = newId(profiles);
  const source = copyFrom ? profiles.find((p) => p.id === copyFrom) : null;
  const profile = {
    id,
    name: cleanName(name),
    color: COLORS[profiles.length % COLORS.length],
    perf: source ? { ...source.perf } : {},
    created: Date.now(),
  };
  const target = modsDir(base, id);
  await fsp.mkdir(target, { recursive: true });
  if (source) await fsp.cp(modsDir(base, source.id), target, { recursive: true, force: true }).catch(() => {});
  return { ...settings, profiles: [...profiles, profile], activeProfile: id };
}

function rename(settings, id, name) {
  return { ...settings, profiles: settings.profiles.map((p) => (p.id === id ? { ...p, name: cleanName(name) } : p)) };
}

async function remove(settings, base, id) {
  if (settings.profiles.length <= 1) throw new Error("Das letzte Profil kann nicht gelöscht werden.");
  const profiles = settings.profiles.filter((p) => p.id !== id);
  await fsp.rm(profileDir(base, id), { recursive: true, force: true });
  return { ...settings, profiles, activeProfile: settings.activeProfile === id ? profiles[0].id : settings.activeProfile };
}

function active(settings) {
  return settings.profiles.find((p) => p.id === settings.activeProfile) || settings.profiles[0];
}

module.exports = { migrate, create, rename, remove, active, countMods, modsDir, profileDir, COLORS };
