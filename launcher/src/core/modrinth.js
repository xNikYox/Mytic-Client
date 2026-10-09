// Mod-Browser: Mods von Modrinth suchen, installieren (mit Abhängigkeiten), an/aus, entfernen, aktuell halten.
const fsp = require("node:fs/promises");
const path = require("node:path");
const { getJson, downloadFile, isValid } = require("./download");

const API = "https://api.modrinth.com/v2";
const STATE_FILE = ".mytic-user.json";

class ModBrowser {
  /**
   * modsDir: Mods-Ordner des Spiels; gameVersion: z. B. "1.21.11";
   * managed: Slugs, die der Launcher selbst verwaltet (Fabric API, Sodium …) – die werden nie doppelt installiert.
   */
  constructor(modsDir, gameVersion, managed = []) {
    this.modsDir = modsDir;
    this.gameVersion = gameVersion;
    this.managedSlugs = new Set(managed);
    this.managedIds = null;
  }

  // ------------------------------------------------------------------------------------------ Zustand

  async state() {
    try {
      const data = JSON.parse(await fsp.readFile(path.join(this.modsDir, STATE_FILE), "utf8"));
      return data && data.mods ? data : { mods: {} };
    } catch {
      return { mods: {} };
    }
  }

  async save(state) {
    await fsp.mkdir(this.modsDir, { recursive: true });
    await fsp.writeFile(path.join(this.modsDir, STATE_FILE), JSON.stringify(state, null, 2));
  }

  async managedProjectIds() {
    if (this.managedIds) return this.managedIds;
    const ids = new Set();
    try {
      const projects = await getJson(`${API}/projects?ids=${encodeURIComponent(JSON.stringify([...this.managedSlugs]))}`);
      for (const p of projects) ids.add(p.id);
    } catch {
      // ohne Internet: nur Slugs vergleichen
    }
    this.managedIds = ids;
    return ids;
  }

  async isManaged(project) {
    if (this.managedSlugs.has(project.slug)) return true;
    return (await this.managedProjectIds()).has(project.id || project.project_id);
  }

  // ------------------------------------------------------------------------------------------ Suche

  async search({ query = "", sort = "relevance", category = "", offset = 0, limit = 24 } = {}) {
    const facets = [["project_type:mod"], ["categories:fabric"], [`versions:${this.gameVersion}`]];
    if (category) facets.push([`categories:${category}`]);
    const params = new URLSearchParams({ query, index: sort, offset: String(offset), limit: String(limit), facets: JSON.stringify(facets) });
    const result = await getJson(`${API}/search?${params}`);
    const state = await this.state();
    return {
      total: result.total_hits,
      hits: result.hits.map((h) => ({
        id: h.project_id,
        slug: h.slug,
        title: h.title,
        description: h.description,
        author: h.author,
        icon: h.icon_url || "",
        downloads: h.downloads,
        follows: h.follows,
        categories: h.display_categories || h.categories || [],
        installed: Boolean(state.mods[h.project_id] && !state.mods[h.project_id].dependency),
        dependency: Boolean(state.mods[h.project_id] && state.mods[h.project_id].dependency),
        managed: this.managedSlugs.has(h.slug),
      })),
    };
  }

  // ------------------------------------------------------------------------------------------ Installation

  async latestVersion(projectId) {
    const params = `loaders=${encodeURIComponent('["fabric"]')}&game_versions=${encodeURIComponent(`["${this.gameVersion}"]`)}`;
    const versions = await getJson(`${API}/project/${projectId}/version?${params}`);
    const version = versions.find((v) => v.version_type === "release") || versions[0];
    if (!version) return null;
    const file = version.files.find((f) => f.primary) || version.files[0];
    const url = new URL(file.url);
    if (url.hostname !== "cdn.modrinth.com") throw new Error(`Unerwartete Download-Adresse: ${url.hostname}`);
    return { version, file };
  }

  async writeVersion(entry, version, file) {
    const target = path.join(this.modsDir, file.filename + (entry.enabled === false ? ".disabled" : ""));
    if (!(await isValid(target, { sha1: file.hashes.sha1, size: file.size }))) {
      await downloadFile(file.url, target, { sha1: file.hashes.sha1, size: file.size });
    }
    if (entry.file && entry.file !== file.filename) {
      await fsp.rm(path.join(this.modsDir, entry.file), { force: true });
      await fsp.rm(path.join(this.modsDir, `${entry.file}.disabled`), { force: true });
    }
    entry.file = file.filename;
    entry.versionId = version.id;
    entry.versionNumber = version.version_number;
  }

  /** Installiert ein Projekt samt Pflicht-Abhängigkeiten. Gibt die Namen der neu installierten Mods zurück. */
  async install(projectId, onStep = () => {}) {
    const state = await this.state();
    const installed = [];
    const visit = async (id, requiredBy) => {
      const project = await getJson(`${API}/project/${id}`);
      if (await this.isManaged(project)) return;
      const existing = state.mods[project.id];
      if (existing) {
        if (requiredBy && !existing.requiredBy.includes(requiredBy)) existing.requiredBy.push(requiredBy);
        if (!requiredBy) existing.dependency = false;
        return;
      }
      onStep(project.title);
      const latest = await this.latestVersion(project.id);
      if (!latest) throw new Error(`${project.title} gibt es nicht für Minecraft ${this.gameVersion} (Fabric).`);
      const entry = {
        id: project.id,
        slug: project.slug,
        title: project.title,
        icon: project.icon_url || "",
        enabled: true,
        dependency: Boolean(requiredBy),
        requiredBy: requiredBy ? [requiredBy] : [],
      };
      await this.writeVersion(entry, latest.version, latest.file);
      state.mods[project.id] = entry;
      installed.push(project.title);
      for (const dep of latest.version.dependencies || []) {
        if (dep.dependency_type === "required" && dep.project_id) await visit(dep.project_id, project.id);
      }
    };
    await visit(projectId, null);
    await this.save(state);
    return installed;
  }

  /** Entfernt einen Mod und Abhängigkeiten, die sonst niemand mehr braucht. */
  async remove(projectId) {
    const state = await this.state();
    const drop = async (id) => {
      const entry = state.mods[id];
      if (!entry) return;
      await fsp.rm(path.join(this.modsDir, entry.file), { force: true });
      await fsp.rm(path.join(this.modsDir, `${entry.file}.disabled`), { force: true });
      delete state.mods[id];
      for (const other of Object.values(state.mods)) {
        other.requiredBy = other.requiredBy.filter((r) => r !== id);
        if (other.dependency && other.requiredBy.length === 0) await drop(other.id);
      }
    };
    await drop(projectId);
    await this.save(state);
  }

  /** An/aus durch Umbenennen in .jar.disabled (wie bei anderen Launchern). */
  async setEnabled(projectId, enabled) {
    const state = await this.state();
    const entry = state.mods[projectId];
    if (!entry) return;
    const on = path.join(this.modsDir, entry.file);
    const off = `${on}.disabled`;
    if (enabled) await fsp.rename(off, on).catch(() => {});
    else await fsp.rename(on, off).catch(() => {});
    entry.enabled = enabled;
    await this.save(state);
  }

  async list() {
    const state = await this.state();
    return Object.values(state.mods)
      .map(({ id, slug, title, icon, enabled, dependency, requiredBy, versionNumber, incompatible }) => ({
        id, slug, title, icon, enabled, dependency, versionNumber, incompatible: Boolean(incompatible),
        requiredBy: requiredBy.map((r) => (state.mods[r] ? state.mods[r].title : r)),
      }))
      .sort((a, b) => Number(a.dependency) - Number(b.dependency) || a.title.localeCompare(b.title));
  }

  /** Beim Spielstart: alle installierten Mods auf die neueste passende Version bringen. */
  async updateAll(log = () => {}) {
    const state = await this.state();
    for (const entry of Object.values(state.mods)) {
      try {
        const latest = await this.latestVersion(entry.id);
        if (!latest) {
          // für diese Minecraft-Version nicht verfügbar: automatisch ausschalten (und später wieder einschalten)
          if (!entry.incompatible) {
            if (entry.enabled !== false) {
              await fsp.rename(path.join(this.modsDir, entry.file), path.join(this.modsDir, `${entry.file}.disabled`)).catch(() => {});
              entry.autoDisabled = true;
            }
            entry.enabled = false;
            entry.incompatible = true;
            log(`[Mods] ${entry.title} gibt es nicht für ${this.gameVersion} – vorübergehend ausgeschaltet`);
          }
          continue;
        }
        if (latest.version.id !== entry.versionId) {
          await this.writeVersion(entry, latest.version, latest.file);
          log(`[Mods] ${entry.title}: ${entry.versionNumber}`);
        }
        if (entry.incompatible) {
          entry.incompatible = false;
          if (entry.autoDisabled) {
            await fsp.rename(path.join(this.modsDir, `${entry.file}.disabled`), path.join(this.modsDir, entry.file)).catch(() => {});
            entry.enabled = true;
            entry.autoDisabled = false;
          }
        }
      } catch (error) {
        log(`[Mods] ${entry.title}: ${error.message}`);
      }
    }
    await this.save(state);
  }
}

module.exports = { ModBrowser };
