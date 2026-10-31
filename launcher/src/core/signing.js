// Signierte Updates: Launcher, Mytic-Mods und Cosmetics werden nur übernommen, wenn sie mit dem eigenen
// Ed25519-Schlüssel signiert sind – egal ob sie von GitHub, Cloudflare oder der eigenen Download-Seite kommen.
// Signiert wird "mytic-update-v1\n<dateiname>\n<sha256>"; die Signatur (Base64) liegt
//   - auf GitHub als eigenes Asset "<dateiname>.sig" im selben Release,
//   - auf der Download-Seite im Feld "signature" von api/latest (sonst als "<url>.sig"),
//   - auf jedem anderen Host (z. B. Cloudflare R2/Pages) als "<url>.sig" oder im Feld "signature".
// Den privaten Schlüssel hat nur der Entwickler (scripts/sign.js), hier steht nur der öffentliche.
const crypto = require("node:crypto");
const { fetchRetry } = require("./download");

const PUBLIC_KEY = crypto.createPublicKey({
  key: Buffer.from("MCowBQYDK2VwAyEAHGFDAPK81z1MjOsnAOWqymxkeeGqdnQHVfpsNk8I+mQ=", "base64"),
  format: "der",
  type: "spki",
});

function message(name, sha256) {
  return Buffer.from(`mytic-update-v1\n${name}\n${String(sha256).toLowerCase()}`, "utf8");
}

/** Signatur für eine Datei erzeugen (nur mit privatem Schlüssel, siehe scripts/sign.js). */
function sign(name, sha256, privateKey) {
  return crypto.sign(null, message(name, sha256), privateKey).toString("base64");
}

/** Passt die Signatur zu Dateiname und SHA-256? key nur für Tests. */
function verify(name, sha256, signature, key = PUBLIC_KEY) {
  if (!name || !/^[0-9a-f]{64}$/i.test(String(sha256 || "")) || typeof signature !== "string") return false;
  const sig = Buffer.from(signature.trim(), "base64");
  if (sig.length !== 64) return false;
  try {
    return crypto.verify(null, message(name, sha256), key, sig);
  } catch {
    return false;
  }
}

/** Wirft, wenn die Signatur fehlt oder nicht passt. */
function assertSigned(name, sha256, signature, key) {
  if (!signature) throw new Error(`keine Signatur für ${name} – Update abgelehnt`);
  if (!verify(name, sha256, signature, key)) throw new Error(`Signatur von ${name} ungültig – Update abgelehnt`);
}

/** Signatur aus einer .sig-Datei laden (klein, Base64). */
async function fetchSignature(url) {
  if (!url) return null;
  const response = await fetchRetry(url);
  if (!response.ok) return null;
  const text = (await response.text()).trim();
  return text.length <= 200 ? text : null;
}

/** URL der Signatur neben einer Datei: <url>.sig (Query bleibt erhalten). */
function sigUrl(url) {
  const u = new URL(url);
  u.pathname += ".sig";
  return u.toString();
}

/** GitHub-Release: URL des Assets "<name>.sig" (oder null). */
function githubSigUrl(release, name) {
  const asset = (release.assets || []).find((a) => a.name === `${name}.sig`);
  return asset ? asset.browser_download_url : null;
}

module.exports = { sign, verify, assertSigned, fetchSignature, sigUrl, githubSigUrl, PUBLIC_KEY };
