// Mytic-Mod beim Spielstart aktualisieren – wie bei Lunar: nur die eine Jar für die gewählte Version laden,
// ohne neuen Launcher. Normale Version: eigenes GitHub-Release "mods-x.y.z". Entwicklerversion: Download-Seite.
// Ohne Internet (oder bei Fehlern) wird die mitgelieferte Mod verwendet.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const AdmZip = require("adm-zip");
const { fetchRetry, getJson } = require("./download");
const { compareVersions } = require("./updater");

/** mytic-client-2.5.0.jar (1.21.11) bzw. mytic-client-2.5.0+1.21.4.jar */
const MOD_PATTERN = /^mytic-client-(\d+\.\d+\.\d+)(?:\+([\w.-]+))?\.jar$/i;
const DEFAULT_MC = "1.21.11";

/** Version einer Mytic-Jar für diese Minecraft-Version (sonst null). */
function jarVersion(name, mcVersion) {
  const m = MOD_PATTERN.exec(name);
  if (!m) return null;
  return (m[2] || DEFAULT_MC) === mcVersion ? m[1] : null;
}

/** Version der Mytic-Mod in <dir>/<mc>/ (mitgeliefert oder aktualisiert). */
function versionIn(dir, mcVersion) {
  try {
    for (const name of fs.readdirSync(path.join(dir, mcVersion))) {
      const v = jarVersion(name, mcVersion);
      if (v) return v;
    }
  } catch {
    // Ordner fehlt
  }
  return null;
}

function withTimeout(promise, ms) {
  return Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(new Error("Zeitüberschreitung")), ms))]);
}

async function download(url, sha256, expectedSize) {
  const response = await fetchRetry(url);
  if (!response.ok) throw new Error(`HTTP ${response.status} bei ${url}`);
  const data = Buffer.from(await response.arrayBuffer());
  if (expectedSize && data.length !== expectedSize) throw new Error("Download unvollständig");
  if (!sha256) throw new Error("keine Prüfsumme – Mod-Update übersprungen");
  if (crypto.createHash("sha256").update(data).digest("hex") !== String(sha256).toLowerCase()) throw new Error("Prüfsumme stimmt nicht");
  return data;
}

class ModUpdater {
  /**
   * cacheDir: wo aktualisierte Mods liegen (<cache>/<mc>/<jar>); bundledDir: mitgelieferte Mods.
   * source: { type: "github", repo } oder { type: "site", base }.
   */
  constructor({ cacheDir, bundledDir, source, log = () => {} }) {
    this.cacheDir = cacheDir;
    this.bundledDir = bundledDir;
    this.source = source;
    this.log = log;
    this.latestInfo = null;
    this.latestAt = 0;
  }

  /** Neueste veröffentlichte Mod-Version (10 Minuten zwischengespeichert). */
  async latest() {
    if (this.latestInfo && Date.now() - this.latestAt < 10 * 60 * 1000) return this.latestInfo;
    let info = null;
    if (this.source.type === "github") {
      const headers = { Accept: "application/vnd.github+json" };
      const files = (release) => (release.assets || []).map((a) => ({
        name: a.name,
        url: a.browser_download_url,
        size: a.size,
        sha256: typeof a.digest === "string" && a.digest.startsWith("sha256:") ? a.digest.slice(7) : null,
      }));
      // 1. festes Release "mods-latest" (immer das aktuelle Paket, eine Abfrage)
      try {
        const release = await getJson(`https://api.github.com/repos/${this.source.repo}/releases/tags/mods-latest`, { headers });
        const list = files(release);
        const versions = list.map((f) => MOD_PATTERN.exec(f.name)).filter(Boolean).map((m) => m[1]);
        if (versions.length) info = { version: versions.sort(compareVersions).pop(), files: list };
      } catch {
        // noch nicht vorhanden: Liste durchsuchen
      }
      // 2. sonst die höchste Version unter den Releases "mods-x.y.z"
      if (!info) {
        const releases = await getJson(`https://api.github.com/repos/${this.source.repo}/releases?per_page=100`, { headers });
        const release = releases
          .filter((r) => /^mods-\d+\.\d+\.\d+$/.test(r.tag_name) && !r.draft)
          .sort((x, y) => compareVersions(x.tag_name.slice(5), y.tag_name.slice(5)))
          .pop();
        if (release) info = { version: release.tag_name.slice(5), files: files(release) };
      }
    } else if (this.source.type === "site" && this.source.base) {
      const root = this.source.base.endsWith("/") ? this.source.base : `${this.source.base}/`;
      const data = await getJson(`${root}api/latest/mytic-mods`);
      if (/^\d+\.\d+\.\d+$/.test(data.version || "")) {
        info = { version: data.version, zip: { url: new URL(data.url, root).toString(), size: data.size, sha256: data.sha256 } };
      }
    }
    this.latestInfo = info;
    this.latestAt = Date.now();
    return info;
  }

  /** Installiert die neue Jar für eine Minecraft-Version in den Cache (alte Jars dieser Version werden ersetzt). */
  async install(info, mcVersion) {
    let name;
    let data;
    if (info.files) {
      const file = info.files.find((f) => jarVersion(f.name, mcVersion) === info.version);
      if (!file) return false;
      name = file.name;
      data = await download(file.url, file.sha256, file.size);
    } else if (info.zip) {
      const zip = new AdmZip(await download(info.zip.url, info.zip.sha256, info.zip.size));
      const entry = zip.getEntries().find((e) => e.entryName.startsWith(`${mcVersion}/`) && jarVersion(path.basename(e.entryName), mcVersion) === info.version);
      if (!entry) return false;
      name = path.basename(entry.entryName);
      data = entry.getData();
    } else {
      return false;
    }
    const dir = path.join(this.cacheDir, mcVersion);
    await fsp.mkdir(dir, { recursive: true });
    await fsp.writeFile(path.join(dir, `${name}.part`), data);
    for (const old of await fsp.readdir(dir)) {
      if (old.endsWith(".jar")) await fsp.rm(path.join(dir, old), { force: true });
    }
    await fsp.rename(path.join(dir, `${name}.part`), path.join(dir, name));
    return true;
  }

  /**
   * Beim Spielstart: neue Mytic-Mod laden, falls vorhanden. Gibt den Ordner zurück, aus dem die Mod kommt
   * (Cache, wenn dort eine neuere liegt, sonst die mitgelieferten Mods).
   */
  async prepare(mcVersion) {
    const bundled = versionIn(this.bundledDir, mcVersion);
    try {
      const info = await withTimeout(this.latest(), 8000);
      const have = [bundled, versionIn(this.cacheDir, mcVersion)].filter(Boolean).sort(compareVersions).pop();
      if (info && (!have || compareVersions(info.version, have) > 0)) {
        if (await withTimeout(this.install(info, mcVersion), 30000)) this.log(`[Mods] Mytic-Mod ${info.version} für ${mcVersion} geladen`);
      }
    } catch (error) {
      this.log(`[Mods] Mod-Update übersprungen: ${error.message}`);
    }
    const cached = versionIn(this.cacheDir, mcVersion);
    return cached && (!bundled || compareVersions(cached, bundled) > 0) ? this.cacheDir : this.bundledDir;
  }

  /** Gibt es eine Mytic-Mod für diese Version (mitgeliefert oder schon aktualisiert)? */
  has(mcVersion) {
    return Boolean(versionIn(this.bundledDir, mcVersion) || versionIn(this.cacheDir, mcVersion));
  }
}

module.exports = { ModUpdater, jarVersion, versionIn, MOD_PATTERN };
