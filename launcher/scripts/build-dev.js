// Baut die Entwicklerversion (Offline-Konten aktiv, kein Auto-Update) als dist/MyticClient-Dev-<version>.exe.
// Nur für eigene Tests – nicht öffentlich verteilen.
const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

const config = path.join(__dirname, "..", "resources", "config.json");
const original = fs.readFileSync(config, "utf8");
try {
  fs.writeFileSync(config, JSON.stringify({ ...JSON.parse(original), devBuild: true }, null, 2));
  execFileSync("npx", ["electron-builder", "--win", "portable", "--x64",
    "-c.portable.artifactName=MyticClient-Dev-${version}.exe"], { stdio: "inherit", env: { ...process.env, LC_ALL: "C.UTF-8", LANG: "C.UTF-8" } });
} finally {
  fs.writeFileSync(config, original);
}
