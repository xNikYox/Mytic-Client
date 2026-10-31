// Tests für signierte Updates (eigenes Testschlüsselpaar, nicht der echte Schlüssel).
const test = require("node:test");
const assert = require("node:assert");
const crypto = require("node:crypto");
const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const signing = require("../src/core/signing");
const updater = require("../src/core/updater");
const { CosmeticsUpdater } = require("../src/core/cosmetics");

const { publicKey, privateKey } = crypto.generateKeyPairSync("ed25519");
const sha = (data) => crypto.createHash("sha256").update(data).digest("hex");

test("Signatur: passt nur zu Name, Hash und Schlüssel", () => {
  const hash = sha("x");
  const sig = signing.sign("a.jar", hash, privateKey);
  assert.ok(signing.verify("a.jar", hash, sig, publicKey));
  assert.ok(!signing.verify("b.jar", hash, sig, publicKey), "anderer Dateiname");
  assert.ok(!signing.verify("a.jar", sha("y"), sig, publicKey), "anderer Inhalt");
  assert.ok(!signing.verify("a.jar", hash, sig), "echter Schlüssel lehnt Testsignatur ab");
  assert.ok(!signing.verify("a.jar", hash, "kaputt", publicKey));
  assert.ok(!signing.verify("a.jar", hash, null, publicKey));
  assert.throws(() => signing.assertSigned("a.jar", hash, null, publicKey), /keine Signatur/);
});

test("Signatur: URLs für .sig (GitHub-Asset und daneben)", () => {
  assert.strictEqual(signing.sigUrl("https://x.test/files/a.jar?x=1"), "https://x.test/files/a.jar.sig?x=1");
  const release = { assets: [{ name: "a.jar", browser_download_url: "u1" }, { name: "a.jar.sig", browser_download_url: "u2" }] };
  assert.strictEqual(signing.githubSigUrl(release, "a.jar"), "u2");
  assert.strictEqual(signing.githubSigUrl(release, "b.jar"), null);
});

/** Kleiner Server: liefert Dateien aus routes { pfad: Buffer|string }. */
async function serve(routes) {
  const server = http.createServer((req, res) => {
    const body = routes[req.url];
    if (body === undefined) { res.writeHead(404); return res.end(); }
    res.end(body);
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  return { server, base: `http://127.0.0.1:${server.address().port}` };
}

test("Launcher-Update: ohne gültige Signatur abgelehnt, mit Signatur (.sig daneben) geladen", async () => {
  const exe = Buffer.from("neue exe");
  const name = "MyticClient-2.6.0.exe";
  const good = signing.sign(name, sha(exe), privateKey);
  const { server, base } = await serve({ "/f.exe": exe, "/f.exe.sig": good, "/g.exe": exe });
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-sig-"));
  try {
    const update = { name, url: `${base}/g.exe`, size: exe.length, sigUrl: `${base}/g.exe.sig` };
    await assert.rejects(updater.downloadUpdate(update, dir, () => {}, { publicKey }), /keine Signatur/);
    assert.ok(!fs.existsSync(path.join(dir, name)));
    const bad = { name, url: `${base}/f.exe`, signature: signing.sign("MyticClient-2.5.0.exe", sha(exe), privateKey) };
    await assert.rejects(updater.downloadUpdate(bad, dir, () => {}, { publicKey }), /ungültig/);
    const ok = { name, url: `${base}/f.exe`, size: exe.length, sigUrl: `${base}/f.exe.sig` };
    const file = await updater.downloadUpdate(ok, dir, () => {}, { publicKey });
    assert.deepStrictEqual(fs.readFileSync(file), exe);
    assert.strictEqual(ok.sha256, sha(exe));
  } finally {
    server.closeAllConnections();
    server.close();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("Download-Seite: Signatur aus api/latest, Version muss zum Dateinamen passen", async () => {
  const exe = Buffer.from("dev exe");
  const json = (v, n) => JSON.stringify({ version: v, name: n, size: exe.length, sha256: sha(exe), url: "download/x", signature: "s" });
  const { server, base } = await serve({
    "/api/latest/ok": json("2.6.0", "MyticClient-Dev-2.6.0.exe"),
    "/api/latest/alt": json("2.9.0", "MyticClient-Dev-2.6.0.exe"),
  });
  try {
    const u = await updater.checkSiteUpdate("2.5.0", `${base}/`, "ok");
    assert.strictEqual(u.signature, "s");
    assert.strictEqual(await updater.checkSiteUpdate("2.5.0", `${base}/`, "alt"), null);
  } finally {
    server.closeAllConnections();
    server.close();
  }
});

test("Cosmetics: unsigniertes Paket wird nicht übernommen", async () => {
  const jar = Buffer.from("pack");
  const name = "mytic-cosmetics-1.2.0.jar";
  const info = (signature) => JSON.stringify({ name, url: "files/p.jar", size: jar.length, sha256: sha(jar), signature });
  let current = info(null);
  const server = http.createServer((req, res) => res.end(req.url.startsWith("/api/") ? current : jar));
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-cos-"));
  try {
    const cos = new CosmeticsUpdater({ dir, source: { type: "site", base }, publicKey });
    // ohne Feld "signature" wird <url>.sig versucht – der Server liefert dort das Jar, also ungültig
    assert.strictEqual(await cos.prepare(), null);
    current = info(signing.sign(name, sha(jar), privateKey));
    assert.strictEqual(path.basename(await cos.prepare()), name);
  } finally {
    server.closeAllConnections();
    server.close();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
