// Auto-Update über GitHub-Releases: neue Version finden, EXE mit Prüfsumme laden, alte EXE aufräumen.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { fetchRetry, USER_AGENT } = require("./download");

const REPO = "xNikYox/Mytic-Client";
const ASSET_PATTERN = /^MyticClient-(\d+\.\d+\.\d+)\.exe$/i;

/** Vergleicht Versionen wie "2.0.10" und "2.1.0". Ergebnis > 0, wenn a neuer ist. */
function compareVersions(a, b) {
  const pa = String(a).replace(/^v/, "").split(".").map(Number);
  const pb = String(b).replace(/^v/, "").split(".").map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const diff = (pa[i] || 0) - (pb[i] || 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

/** Neueste Version auf GitHub, oder null, wenn die aktuelle schon die neueste ist. */
async function checkForUpdate(currentVersion, repo = REPO) {
  const response = await fetchRetry(`https://api.github.com/repos/${repo}/releases/latest`, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!response.ok) throw new Error(`Update-Prüfung fehlgeschlagen (HTTP ${response.status})`);
  const release = await response.json();
  const version = String(release.tag_name || "").replace(/^v/, "");
  if (!/^\d+\.\d+\.\d+$/.test(version) || compareVersions(version, currentVersion) <= 0) return null;
  const asset = (release.assets || []).find((a) => ASSET_PATTERN.test(a.name));
  if (!asset) return null;
  const digest = typeof asset.digest === "string" && asset.digest.startsWith("sha256:") ? asset.digest.slice(7) : null;
  return {
    version,
    name: asset.name,
    url: asset.browser_download_url,
    size: asset.size,
    sha256: digest,
    notes: String(release.body || "").slice(0, 4000),
    page: release.html_url,
  };
}

/** Lädt die neue EXE neben die alte (über eine .part-Datei) und prüft Größe und SHA-256. */
async function downloadUpdate(update, targetDir, onProgress = () => {}) {
  const target = path.join(targetDir, update.name);
  const part = `${target}.part`;
  const response = await fetchRetry(update.url, { headers: { "User-Agent": USER_AGENT } });
  if (!response.ok || !response.body) throw new Error(`Download fehlgeschlagen (HTTP ${response.status})`);
  const hash = crypto.createHash("sha256");
  const out = fs.createWriteStream(part);
  let received = 0;
  const reader = response.body.getReader();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      hash.update(value);
      received += value.length;
      if (!out.write(value)) await new Promise((r) => out.once("drain", r));
      onProgress(received, update.size);
    }
  } finally {
    await new Promise((r) => out.end(r));
  }
  if (update.size && received !== update.size) {
    await fsp.rm(part, { force: true });
    throw new Error("Download unvollständig, bitte erneut versuchen.");
  }
  if (update.sha256 && hash.digest("hex") !== update.sha256.toLowerCase()) {
    await fsp.rm(part, { force: true });
    throw new Error("Prüfsumme stimmt nicht, Update abgebrochen.");
  }
  await fsp.rm(target, { force: true });
  await fsp.rename(part, target);
  return target;
}

/**
 * Löscht die alte EXE nach einem Update. Nur Dateien nach dem Muster MyticClient-x.y.z.exe im selben Ordner wie die
 * neue Version, nie die gerade laufende. Wartet, bis die alte Version beendet ist (Datei wieder freigegeben).
 */
async function removeOldVersion(oldFile, currentFile, { tries = 30, delayMs = 1000 } = {}) {
  if (!oldFile || !currentFile) return false;
  const oldPath = path.resolve(oldFile);
  if (oldPath === path.resolve(currentFile)) return false;
  if (path.dirname(oldPath) !== path.dirname(path.resolve(currentFile))) return false;
  if (!ASSET_PATTERN.test(path.basename(oldPath))) return false;
  for (let i = 0; i < tries; i++) {
    try {
      await fsp.rm(oldPath);
      return true;
    } catch (error) {
      if (error.code === "ENOENT") return true;
      await new Promise((r) => setTimeout(r, delayMs));
    }
  }
  return false;
}

module.exports = { compareVersions, checkForUpdate, downloadUpdate, removeOldVersion, REPO, ASSET_PATTERN };
