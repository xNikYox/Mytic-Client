// Mod-Scanner: nachgebaute Mods mit typischen Merkmalen von Malware – und harmlose, die nicht auffallen dürfen.
const test = require("node:test");
const assert = require("node:assert/strict");
const AdmZip = require("adm-zip");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { scanZip, verdict, scanFolder } = require("../src/core/modscan");

/** Jar mit „Klassen“, deren Inhalt die gegebenen Texte enthält (wie Konstanten in echten .class-Dateien) */
function jar(classes, nested = {}) {
  const z = new AdmZip();
  for (const [name, text] of Object.entries(classes)) z.addFile(`${name}.class`, Buffer.concat([Buffer.from([0xca, 0xfe, 0xba, 0xbe]), Buffer.from(text, "latin1")]));
  for (const [name, buf] of Object.entries(nested)) z.addFile(`META-INF/jars/${name}`, buf);
  z.addFile("fabric.mod.json", Buffer.from('{"id":"test"}'));
  return z.toBuffer();
}
const check = (buf) => verdict(scanZip(buf, "test.jar"));

test("harmlose Mod: nichts gefunden", () => {
  const buf = jar({ "com/example/Sodium": "net/minecraft/client/Minecraft render chunk https://github.com/CaffeineMC/sodium", "com/example/Http": "java/net/URL https://api.modrinth.com/v2" });
  assert.equal(check(buf), "ok");
});

test("Fractureiser wird als Malware erkannt – auch in verschachtelter Jar", () => {
  const inner = jar({ "dev/neko/nekoclient/Client": "dev/neko/nekoclient connect 85.217.144.130" });
  const outer = jar({ "com/mod/Main": "harmless" }, { "lib.jar": inner });
  const hits = scanZip(outer, "mod.jar");
  assert.equal(verdict(hits), "malware");
  assert.match(hits[0].where, /mod\.jar › lib\.jar/);
});

test("Discord-Token lesen + Webhook = Malware, nur Webhook = verdächtig", () => {
  const grabber = jar({ "a/B": "AppData discord\\Local Storage\\leveldb dQw4w9WgXcQ: https://discord.com/api/webhooks/123/abc" });
  assert.equal(check(grabber), "malware");
  assert.equal(check(jar({ "a/B": "https://discord.com/api/webhooks/123/abc" })), "verdaechtig");
});

test("Minecraft-Sitzung lesen + Telegram = Malware; nur Sitzung lesen = Hinweis", () => {
  const thief = jar({ "a/B": "net/minecraft/client/User getAccessToken", "a/C": "https://api.telegram.org/bot123/sendMessage" });
  assert.equal(check(thief), "malware");
  assert.equal(check(jar({ "a/B": "net/minecraft/client/User getAccessToken" })), "hinweis");
});

test("Browser-Passwörter, Autostart, PowerShell", () => {
  assert.equal(check(jar({ "a/B": "Google\\Chrome\\User Data\\Default\\Login Data" })), "verdaechtig");
  assert.equal(check(jar({ "a/B": "Google\\Chrome\\User Data\\Default\\Login Data", "a/C": "Software\\Microsoft\\Windows\\CurrentVersion\\Run" })), "malware");
  assert.equal(check(jar({ "a/B": "java/lang/ProcessBuilder powershell -enc" })), "hinweis");
});

test("Code von GitHub/Modrinth nachladen ist kein Treffer, von fremder Seite schon (Hinweis)", () => {
  assert.equal(check(jar({ "a/B": "java/net/URLClassLoader https://raw.githubusercontent.com/x/y" })), "ok");
  assert.equal(check(jar({ "a/B": "java/net/URLClassLoader https://evil.example/payload.jar" })), "hinweis");
});

test("Ordner: Sortierung nach Gefahr, Zwischenspeicher, übersprungene Dateien", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "modscan-"));
  fs.writeFileSync(path.join(dir, "gut.jar"), jar({ "a/B": "harmless" }));
  fs.writeFileSync(path.join(dir, "boese.jar.disabled"), jar({ "a/B": "files-8ie.pages.dev" }));
  fs.writeFileSync(path.join(dir, "sodium.jar"), jar({ "a/B": "skyrage.de" }));
  const { results, cache } = await scanFolder(dir, { skip: (n) => n === "sodium.jar" });
  assert.deepEqual(results.map((r) => [r.name, r.verdict, r.disabled]), [["boese.jar.disabled", "malware", true], ["gut.jar", "ok", false]]);
  assert.equal(Object.keys(cache).length, 2);
});
