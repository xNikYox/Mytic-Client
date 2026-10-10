// Verpackt die gebauten Mytic-Mods für die Mod-Updates des Launchers (wie bei Lunar):
//   dist/mods/jars/mytic-client-<v>+<mc>.jar  → als GitHub-Release "mods-<v>" hochladen
//   dist/mods/MyticMods-<v>.zip               → für die Download-Seite (Entwicklerversion)
// Aufruf: node scripts/pack-mods.js   (vorher ../build-mods.sh)
const fs = require("node:fs");
const path = require("node:path");
const AdmZip = require("adm-zip");
const { jarVersion } = require("../src/core/modupdates");

const root = path.join(__dirname, "..");
const source = path.join(root, "resources", "mods");
const version = /^version = "(.+)"/m.exec(fs.readFileSync(path.join(root, "..", "mod", "build.gradle.kts"), "utf8"))[1];
const out = path.join(root, "dist", "mods");
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(path.join(out, "jars"), { recursive: true });

const zip = new AdmZip();
let count = 0;
for (const mc of fs.readdirSync(source).sort()) {
  const jar = fs.readdirSync(path.join(source, mc)).find((f) => jarVersion(f, mc));
  if (!jar) continue;
  if (jarVersion(jar, mc) !== version) throw new Error(`${mc}: ${jar} passt nicht zu Version ${version} – erst build-mods.sh ausführen`);
  const name = `mytic-client-${version}+${mc}.jar`;
  const data = fs.readFileSync(path.join(source, mc, jar));
  fs.writeFileSync(path.join(out, "jars", name), data);
  zip.addFile(`${mc}/${name}`, data);
  count++;
}
zip.writeZip(path.join(out, `MyticMods-${version}.zip`));
console.log(`Mytic-Mods ${version}: ${count} Versionen → dist/mods/`);
