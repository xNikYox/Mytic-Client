// Veröffentlicht auf GitHub – immer mit Signaturen, damit die Launcher der Spieler die Updates annehmen.
//   node scripts/release.js launcher [notizen.md]   Release "v<version>": portable EXE + Zip aus dist/ (vorher npm run dist:win)
//                                       – den Installer baut und signiert danach GitHub Actions (installer.yml)
//   node scripts/release.js mods        Release "mods-latest": Jars aus dist/mods/jars (vorher pack-mods.js)
//   node scripts/release.js cosmetics   Release "cosmetics-latest": ../cosmetics/build/mytic-cosmetics-<v>.jar
// Fehlende .sig werden mit dem lokalen Schlüssel erzeugt; jede Signatur wird vor dem Hochladen geprüft.
// Benötigt die GitHub-CLI (gh), angemeldet mit Schreibrechten auf das Repository.
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { execFileSync } = require("node:child_process");
const signing = require("../src/core/signing");
const { signFiles } = require("./sign");
const { REPO } = require("../src/core/updater");

const root = path.join(__dirname, "..");
const version = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8")).version;

function gh(args, options = {}) {
  return execFileSync("gh", [...args, "--repo", REPO], { encoding: "utf8", stdio: ["ignore", "pipe", "inherit"], ...options });
}

/** Signiert Dateien ohne .sig und prüft alle Signaturen mit dem öffentlichen Schlüssel aus dem Launcher. */
function signed(files) {
  if (!files.length) throw new Error("keine Dateien gefunden");
  const missing = files.filter((f) => !fs.existsSync(`${f}.sig`));
  if (missing.length) signFiles(missing);
  for (const file of files) {
    const sha256 = crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
    if (!signing.verify(path.basename(file), sha256, fs.readFileSync(`${file}.sig`, "utf8"))) {
      throw new Error(`Signatur passt nicht: ${file} – neu signieren mit: node scripts/sign.js ${file}`);
    }
  }
  return files.flatMap((f) => [f, `${f}.sig`]);
}

function releaseExists(tag) {
  try {
    gh(["release", "view", tag, "--json", "tagName"]);
    return true;
  } catch {
    return false;
  }
}

/** Ersetzt alle Assets eines festen Releases (mods-latest, cosmetics-latest), damit keine alten Dateien liegen bleiben. */
function replaceAssets(tag, files, title) {
  if (!releaseExists(tag)) gh(["release", "create", tag, "--title", title, "--notes", title, "--latest=false"]);
  const old = JSON.parse(gh(["release", "view", tag, "--json", "assets"])).assets.map((a) => a.name);
  const names = new Set(files.map((f) => path.basename(f)));
  gh(["release", "upload", tag, ...files, "--clobber"], { stdio: "inherit" });
  for (const name of old.filter((n) => !names.has(n))) gh(["release", "delete-asset", tag, name, "--yes"]);
}

/** Version aus der Karte „Neu in X.Y“ der Startseite (oder null). */
function newsVersion(html) {
  const m = /<h3>Neu in (\d+\.\d+)<\/h3>/.exec(html);
  return m ? m[1] : null;
}

module.exports = { newsVersion };

const what = require.main === module ? process.argv[2] : null;
try {
  if (what === null) {
    // nur als Modul geladen (Tests)
  } else if (what === "launcher") {
    // Die Spieler sehen „Neu in …“ auf der Startseite – muss bei jedem Release zur Version passen
    const news = newsVersion(fs.readFileSync(path.join(root, "src", "renderer", "index.html"), "utf8"));
    const minor = version.split(".").slice(0, 2).join(".");
    if (news !== minor) throw new Error(`Startseite zeigt „Neu in ${news}“, Version ist ${version} – erst die Karte in src/renderer/index.html aktualisieren`);
    const dist = path.join(root, "dist");
    const files = signed([`MyticClient-${version}.exe`, `MyticClient-${version}-win.zip`].map((f) => path.join(dist, f)).filter((f) => fs.existsSync(f)));
    const tag = `v${version}`;
    if (releaseExists(tag)) gh(["release", "upload", tag, ...files, "--clobber"], { stdio: "inherit" });
    else {
      // Release-Notizen: optional als Datei (node scripts/release.js launcher notizen.md), sonst von GitHub erzeugt
      const notes = process.argv[3] ? ["--notes-file", process.argv[3]] : ["--generate-notes"];
      gh(["release", "create", tag, ...files, "--title", `Mytic Client ${version}`, ...notes], { stdio: "inherit" });
    }
    console.log(`${tag} veröffentlicht – der Installer folgt über GitHub Actions (signiert mit Secret MYTIC_UPDATE_KEY).`);
  } else if (what === "mods") {
    const dir = path.join(root, "dist", "mods", "jars");
    const files = signed(fs.readdirSync(dir).filter((f) => f.endsWith(".jar")).map((f) => path.join(dir, f)));
    replaceAssets("mods-latest", files, "Mytic-Mods (aktuell)");
    console.log(`mods-latest: ${files.length / 2} Jars mit Signatur veröffentlicht.`);
  } else if (what === "cosmetics") {
    const dir = path.join(root, "..", "cosmetics", "build");
    const { PACK_PATTERN } = require("../src/core/cosmetics");
    const { compareVersions } = require("../src/core/updater");
    const newest = fs.readdirSync(dir).filter((f) => PACK_PATTERN.test(f))
      .sort((a, b) => compareVersions(PACK_PATTERN.exec(a)[1], PACK_PATTERN.exec(b)[1])).pop();
    const files = signed(newest ? [path.join(dir, newest)] : []);
    replaceAssets("cosmetics-latest", files, "Mytic Cosmetics (aktuell)");
    console.log("cosmetics-latest mit Signatur veröffentlicht.");
  } else {
    console.error("Aufruf: node scripts/release.js launcher|mods|cosmetics");
    process.exit(1);
  }
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
