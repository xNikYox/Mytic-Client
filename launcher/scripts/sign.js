// Signiert Release-Dateien für die Auto-Updates: legt neben jede Datei "<datei>.sig" (Base64, Ed25519).
// Aufruf: node scripts/sign.js <datei|ordner> [...]   (Ordner: alle .exe/.jar/.zip darin, z. B. "dist")
// Die .sig-Dateien gehören mit ins GitHub-Release (bzw. neben die Datei auf Cloudflare/der Download-Seite).
// Privater Schlüssel: MYTIC_SIGNING_KEY (Pfad) oder ~/.config/mytic-signing/update-key.pem – nie ins Repository!
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const crypto = require("node:crypto");
const signing = require("../src/core/signing");

const KEY_FILE = process.env.MYTIC_SIGNING_KEY || path.join(os.homedir(), ".config", "mytic-signing", "update-key.pem");

function loadKey() {
  if (!fs.existsSync(KEY_FILE)) throw new Error(`Signaturschlüssel fehlt: ${KEY_FILE}`);
  const key = crypto.createPrivateKey(fs.readFileSync(KEY_FILE));
  // passt der Schlüssel zum öffentlichen Schlüssel im Launcher?
  const pub = crypto.createPublicKey(key).export({ type: "spki", format: "der" });
  if (!pub.equals(signing.PUBLIC_KEY.export({ type: "spki", format: "der" }))) {
    throw new Error("Signaturschlüssel passt nicht zum öffentlichen Schlüssel in src/core/signing.js");
  }
  return key;
}

/** Signiert die Dateien und gibt die Pfade der .sig-Dateien zurück. */
function signFiles(files, key = loadKey()) {
  return files.map((file) => {
    const sha256 = crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
    const sig = `${file}.sig`;
    fs.writeFileSync(sig, `${signing.sign(path.basename(file), sha256, key)}\n`);
    return sig;
  });
}

module.exports = { signFiles, loadKey, KEY_FILE };

if (require.main === module) {
  // --release: nur EXE und Zip der aktuellen Version aus dist/ (nach npm run dist:win)
  const version = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "package.json"), "utf8")).version;
  const args = process.argv.slice(2).flatMap((arg) => (arg === "--release"
    ? [`MyticClient-${version}.exe`, `MyticClient-${version}-win.zip`].map((f) => path.join(__dirname, "..", "dist", f)).filter((f) => fs.existsSync(f))
    : [arg]));
  const files = args.flatMap((arg) => (fs.statSync(arg).isDirectory()
    ? fs.readdirSync(arg).filter((f) => /\.(exe|jar|zip)$/i.test(f)).map((f) => path.join(arg, f))
    : [arg]));
  if (!files.length) {
    console.error("Aufruf: node scripts/sign.js <datei> [<datei> ...]");
    process.exit(1);
  }
  try {
    for (const sig of signFiles(files)) console.log(`signiert: ${sig}`);
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
