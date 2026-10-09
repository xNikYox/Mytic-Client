// Baut die Entwicklerversion (Offline-Konten aktiv, Updates über die eigene Download-Seite) als
// dist/MyticClient-Dev-<version>.exe. Nur für eigene Tests – nicht öffentlich verteilen.
// Die Download-Seite kann mit MYTIC_UPDATE_SITE überschrieben werden.
const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

// Adresse der eigenen Download-Seite: aus MYTIC_UPDATE_SITE oder der lokalen Datei scripts/.dev-update-site (nicht im Repository)
const siteFile = path.join(__dirname, ".dev-update-site");
const UPDATE_SITE = (process.env.MYTIC_UPDATE_SITE || (fs.existsSync(siteFile) ? fs.readFileSync(siteFile, "utf8") : "")).trim();
if (!UPDATE_SITE) console.warn("Hinweis: keine Download-Seite für Updates angegeben – die Entwicklerversion aktualisiert sich dann nicht selbst.");

const config = path.join(__dirname, "..", "resources", "config.json");
const original = fs.readFileSync(config, "utf8");
try {
  fs.writeFileSync(config, JSON.stringify({ ...JSON.parse(original), devBuild: true, updateSite: UPDATE_SITE }, null, 2));
  execFileSync("npx", ["electron-builder", "--win", "portable", "--x64",
    "-c.portable.artifactName=MyticClient-Dev-${version}.exe"], { stdio: "inherit", env: { ...process.env, LC_ALL: "C.UTF-8", LANG: "C.UTF-8" } });
} finally {
  fs.writeFileSync(config, original);
}
