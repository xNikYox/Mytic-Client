// Auto-Update über GitHub-Releases: neue Version finden, EXE mit Prüfsumme laden, alte EXE aufräumen.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { fetchRetry, USER_AGENT } = require("./download");

const REPO = "xNikYox/Mytic-Client";
const ASSET_PATTERN = /^MyticClient-(?:Dev-)?(\d+\.\d+\.\d+)\.exe$/i;
/** Installer (NSIS, Ein-Klick, pro Benutzer) – wie bei Lunar. */
const SETUP_PATTERN = /^MyticClient-Setup-(\d+\.\d+\.\d+)\.exe$/i;

/**
 * Wie läuft der Launcher? "installed" (Installer), "portable" (portable EXE) oder "zip" (entpackt/Entwicklung).
 * Der Installer legt neben der EXE "Uninstall Mytic Client.exe" ab.
 */
function installMode({ packaged, portableFile, execPath }) {
  if (!packaged) return "zip";
  if (portableFile) return "portable";
  if (fs.existsSync(path.join(path.dirname(execPath), "Uninstall Mytic Client.exe"))) return "installed";
  return "zip";
}

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

/**
 * Neueste Version auf GitHub, oder null, wenn die aktuelle schon die neueste ist.
 * kind: "portable" (EXE) oder "setup" (Installer). allowSame: auch die gleiche Version liefern (Umzug zum Installer).
 */
async function checkForUpdate(currentVersion, repo = REPO, { kind = "portable", allowSame = false } = {}) {
  const response = await fetchRetry(`https://api.github.com/repos/${repo}/releases/latest`, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!response.ok) throw new Error(`Update-Prüfung fehlgeschlagen (HTTP ${response.status})`);
  const release = await response.json();
  const version = String(release.tag_name || "").replace(/^v/, "");
  const diff = compareVersions(version, currentVersion);
  if (!/^\d+\.\d+\.\d+$/.test(version) || diff < 0 || (diff === 0 && !allowSame)) return null;
  const pattern = kind === "setup" ? SETUP_PATTERN : ASSET_PATTERN;
  const asset = (release.assets || []).find((a) => pattern.test(a.name));
  if (!asset) return null;
  const digest = typeof asset.digest === "string" && asset.digest.startsWith("sha256:") ? asset.digest.slice(7) : null;
  return {
    version,
    kind,
    name: asset.name,
    url: asset.browser_download_url,
    size: asset.size,
    sha256: digest,
    notes: String(release.body || "").slice(0, 4000),
    page: release.html_url,
  };
}

/**
 * Neueste Version von der eigenen Download-Seite (für die Entwicklerversion).
 * base: z. B. "http://server/proxy/8765/", project: "mytic-client-dev".
 */
async function checkSiteUpdate(currentVersion, base, project) {
  const root = base.endsWith("/") ? base : `${base}/`;
  const response = await fetchRetry(`${root}api/latest/${project}`, { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(`Update-Prüfung fehlgeschlagen (HTTP ${response.status})`);
  const info = await response.json();
  if (!/^\d+\.\d+\.\d+$/.test(info.version || "") || compareVersions(info.version, currentVersion) <= 0) return null;
  if (!ASSET_PATTERN.test(info.name || "")) return null;
  return {
    version: info.version,
    kind: "portable",
    name: info.name,
    url: new URL(info.url, root).toString(),
    size: info.size,
    sha256: info.sha256 || null,
    notes: "",
    page: root,
  };
}

/** Lädt die neue EXE neben die alte (über eine .part-Datei) und prüft Größe und SHA-256. */
async function downloadUpdate(update, targetDir, onProgress = () => {}) {
  const target = path.join(targetDir, update.name);
  const part = `${target}.part`;
  await fsp.mkdir(targetDir, { recursive: true });
  const response = await fetchRetry(update.url, { headers: { "User-Agent": USER_AGENT } });
  if (!response.ok || !response.body) throw new Error(`Download fehlgeschlagen (HTTP ${response.status})`);
  const hash = crypto.createHash("sha256");
  const out = fs.createWriteStream(part);
  // Schreibfehler (z. B. voller Datenträger) als normalen Fehler melden statt den Launcher abstürzen zu lassen
  let writeError = null;
  out.on("error", (error) => {
    writeError = error;
  });
  let received = 0;
  const reader = response.body.getReader();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (writeError) throw new Error(`Update konnte nicht gespeichert werden: ${writeError.message}`);
      hash.update(value);
      received += value.length;
      if (!out.write(value)) await new Promise((r) => out.once("drain", r));
      onProgress(received, update.size);
    }
  } finally {
    await new Promise((r) => out.end(r));
  }
  if (writeError) {
    await fsp.rm(part, { force: true });
    throw new Error(`Update konnte nicht gespeichert werden: ${writeError.message}`);
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

// ---------------------------------------------------------------------------------------------- Bereitliegende Updates

/** Merkt sich ein fertig geladenes Update; es wird beim nächsten Start eingespielt (wie bei Lunar). */
async function writeReady(dir, info) {
  await fsp.mkdir(dir, { recursive: true });
  await fsp.writeFile(path.join(dir, "ready.json"), JSON.stringify(info, null, 2));
}

/** Bereitliegendes Update, wenn es neuer ist (bzw. ein Umzug) und die Datei unverändert ist. Sonst aufräumen. */
async function readReady(dir, currentVersion) {
  let info;
  try {
    info = JSON.parse(await fsp.readFile(path.join(dir, "ready.json"), "utf8"));
  } catch {
    return null;
  }
  const valid = info && info.file && path.dirname(path.resolve(info.file)) === path.resolve(dir)
    && (compareVersions(info.version, currentVersion) > 0 || (info.migrate && compareVersions(info.version, currentVersion) >= 0))
    && fs.existsSync(info.file)
    && (!info.sha256 || (await sha256File(info.file)) === String(info.sha256).toLowerCase());
  if (!valid) {
    await clearReady(dir);
    return null;
  }
  return info;
}

async function clearReady(dir) {
  await fsp.rm(dir, { recursive: true, force: true }).catch(() => {});
}

function sha256File(file) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash("sha256");
    fs.createReadStream(file).on("data", (d) => hash.update(d)).on("error", reject).on("end", () => resolve(hash.digest("hex")));
  });
}

/** Nach dem Umzug zum Installer: alte portable EXE löschen (nur MyticClient-x.y.z.exe, nie die laufende). */
async function removeMigratedPortable(oldFile, currentFile, options) {
  if (!oldFile || !ASSET_PATTERN.test(path.basename(oldFile))) return false;
  if (currentFile && path.resolve(oldFile) === path.resolve(currentFile)) return false;
  const { tries = 30, delayMs = 1000 } = options || {};
  for (let i = 0; i < tries; i++) {
    try {
      await fsp.rm(path.resolve(oldFile));
      return true;
    } catch (error) {
      if (error.code === "ENOENT") return true;
      await new Promise((r) => setTimeout(r, delayMs));
    }
  }
  return false;
}

module.exports = {
  compareVersions, checkForUpdate, checkSiteUpdate, downloadUpdate, removeOldVersion, installMode,
  writeReady, readReady, clearReady, sha256File, removeMigratedPortable, REPO, ASSET_PATTERN, SETUP_PATTERN,
};
