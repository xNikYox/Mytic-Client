// Ordner des Mytic Client. Alles liegt getrennt vom normalen .minecraft-Ordner.
const os = require("node:os");
const path = require("node:path");

function baseDir() {
  if (process.env.MYTIC_HOME) return process.env.MYTIC_HOME;
  if (process.platform === "win32") return path.join(process.env.APPDATA || path.join(os.homedir(), "AppData", "Roaming"), ".myticclient");
  if (process.platform === "darwin") return path.join(os.homedir(), "Library", "Application Support", "myticclient");
  return path.join(os.homedir(), ".myticclient");
}

function dirs(base = baseDir()) {
  return {
    base,
    game: path.join(base, "game"),
    versions: path.join(base, "versions"),
    libraries: path.join(base, "libraries"),
    assets: path.join(base, "assets"),
    runtime: path.join(base, "runtime"),
    logs: path.join(base, "launcher-logs"),
  };
}

module.exports = { baseDir, dirs };
