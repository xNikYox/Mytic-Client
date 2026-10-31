// Cosmetics-Paket (Umhänge) – wird wie bei Lunar einmal heruntergeladen und beim Spielstart aktualisiert.
// Normale Version: GitHub-Release "cosmetics-latest"; Entwicklerversion: Download-Seite (Projekt mytic-cosmetics).
// Fabric lädt das Paket über -Dfabric.addMods, 1.8.9 liest es über -Dmytic.cosmetics.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { fetchRetry, getJson } = require("./download");
const { compareVersions } = require("./updater");
const signing = require("./signing");

const PACK_PATTERN = /^mytic-cosmetics-(\d+\.\d+\.\d+)\.jar$/;

function withTimeout(promise, ms) {
  return Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(new Error("Zeitüberschreitung")), ms))]);
}

class CosmeticsUpdater {
  /** dir: Ablage für das Paket; source: { type: "github", repo } oder { type: "site", base }. */
  constructor({ dir, source, log = () => {}, publicKey }) {
    this.publicKey = publicKey; // nur für Tests, sonst der eingebaute Schlüssel
    this.dir = dir;
    this.source = source;
    this.log = log;
  }

  /** Vorhandenes Paket (neueste Version im Ordner) oder null. */
  local() {
    try {
      const files = fs.readdirSync(this.dir).filter((f) => PACK_PATTERN.test(f));
      files.sort((a, b) => compareVersions(PACK_PATTERN.exec(a)[1], PACK_PATTERN.exec(b)[1]));
      const name = files.pop();
      return name ? { version: PACK_PATTERN.exec(name)[1], file: path.join(this.dir, name) } : null;
    } catch {
      return null;
    }
  }

  async latest() {
    if (this.source.type === "github") {
      const release = await getJson(`https://api.github.com/repos/${this.source.repo}/releases/tags/cosmetics-latest`, { headers: { Accept: "application/vnd.github+json" } });
      const asset = (release.assets || []).find((a) => PACK_PATTERN.test(a.name));
      if (!asset) return null;
      const sha256 = typeof asset.digest === "string" && asset.digest.startsWith("sha256:") ? asset.digest.slice(7) : null;
      return { version: PACK_PATTERN.exec(asset.name)[1], name: asset.name, url: asset.browser_download_url, size: asset.size, sha256,
        sigUrl: signing.githubSigUrl(release, asset.name) };
    }
    if (this.source.type === "site" && this.source.base) {
      const root = this.source.base.endsWith("/") ? this.source.base : `${this.source.base}/`;
      const data = await getJson(`${root}api/latest/mytic-cosmetics`);
      if (!PACK_PATTERN.test(data.name || "")) return null;
      const url = new URL(data.url, root).toString();
      return { version: PACK_PATTERN.exec(data.name)[1], name: data.name, url, size: data.size, sha256: data.sha256,
        signature: typeof data.signature === "string" ? data.signature : null, sigUrl: signing.sigUrl(url) };
    }
    return null;
  }

  async download(info) {
    const response = await fetchRetry(info.url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = Buffer.from(await response.arrayBuffer());
    if (info.size && data.length !== info.size) throw new Error("Download unvollständig");
    const digest = crypto.createHash("sha256").update(data).digest("hex");
    if (!info.sha256 || digest !== String(info.sha256).toLowerCase()) throw new Error("Prüfsumme stimmt nicht");
    signing.assertSigned(info.name, digest, info.signature || (await signing.fetchSignature(info.sigUrl)), this.publicKey);
    await fsp.mkdir(this.dir, { recursive: true });
    const target = path.join(this.dir, info.name);
    await fsp.writeFile(`${target}.part`, data);
    await fsp.rename(`${target}.part`, target);
    // ältere Pakete entfernen
    for (const old of await fsp.readdir(this.dir)) {
      if (PACK_PATTERN.test(old) && old !== info.name) await fsp.rm(path.join(this.dir, old), { force: true });
    }
    return target;
  }

  /** Beim Spielstart: Paket aktualisieren, falls möglich. Gibt den Pfad zum Paket zurück (oder null). */
  async prepare() {
    const have = this.local();
    try {
      const info = await withTimeout(this.latest(), 8000);
      if (info && (!have || compareVersions(info.version, have.version) > 0)) {
        const file = await withTimeout(this.download(info), 60000);
        this.log(`[Cosmetics] Paket ${info.version} geladen`);
        return file;
      }
    } catch (error) {
      this.log(`[Cosmetics] Update übersprungen: ${error.message}`);
    }
    return have ? have.file : null;
  }
}

module.exports = { CosmeticsUpdater, PACK_PATTERN };
