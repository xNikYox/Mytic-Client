// Lädt die passende Java-Laufzeit direkt von Mojang (wie der offizielle Launcher). Spieler müssen nichts installieren.
const fsp = require("node:fs/promises");
const path = require("node:path");
const { getJson, downloadAll } = require("./download");

const RUNTIME_INDEX = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

function runtimePlatform(platform = process.platform, arch = process.arch) {
  if (platform === "win32") return arch === "arm64" ? "windows-arm64" : arch === "ia32" ? "windows-x86" : "windows-x64";
  if (platform === "darwin") return arch === "arm64" ? "mac-os-arm64" : "mac-os";
  return arch === "ia32" ? "linux-i386" : "linux";
}

function javaExecutable(home, platform = process.platform) {
  if (platform === "win32") return path.join(home, "bin", "javaw.exe");
  if (platform === "darwin") return path.join(home, "jre.bundle", "Contents", "Home", "bin", "java");
  return path.join(home, "bin", "java");
}

/** Installiert z. B. "java-runtime-delta" (Java 21) und gibt den Pfad zu java/javaw zurück. */
async function ensureJava(component, runtimeDir, { onProgress, platform = process.platform, arch = process.arch } = {}) {
  const key = runtimePlatform(platform, arch);
  const index = await getJson(RUNTIME_INDEX);
  const entry = index[key] && index[key][component] && index[key][component][0];
  if (!entry) throw new Error(`Keine Java-Laufzeit "${component}" für ${key} verfügbar`);
  const home = path.join(runtimeDir, component, key);
  const marker = path.join(home, ".version");
  const exe = javaExecutable(home, platform);
  try {
    if ((await fsp.readFile(marker, "utf8")) === entry.version.name) {
      await fsp.access(exe);
      return exe;
    }
  } catch {
    // noch nicht installiert
  }
  const manifest = await getJson(entry.manifest.url);
  const files = [];
  const links = [];
  for (const [rel, info] of Object.entries(manifest.files)) {
    const target = path.join(home, rel);
    if (info.type === "directory") await fsp.mkdir(target, { recursive: true });
    else if (info.type === "link") links.push([target, info.target]);
    else files.push({ url: info.downloads.raw.url, file: target, sha1: info.downloads.raw.sha1, size: info.downloads.raw.size, executable: info.executable });
  }
  await downloadAll(files, { concurrency: 12, onProgress });
  if (platform !== "win32") {
    for (const f of files) if (f.executable) await fsp.chmod(f.file, 0o755);
    for (const [target, linkTarget] of links) {
      await fsp.rm(target, { force: true });
      await fsp.symlink(linkTarget, target).catch(() => {});
    }
  }
  await fsp.writeFile(marker, entry.version.name);
  return exe;
}

module.exports = { ensureJava, runtimePlatform, javaExecutable };
