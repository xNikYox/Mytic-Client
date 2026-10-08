const test = require("node:test");
const assert = require("node:assert");
const { rulesAllow, collectArguments, mavenPath, Log4jParser } = require("../src/core/minecraft");
const { offlineUuid, offlineAccount } = require("../src/core/auth");
const { runtimePlatform } = require("../src/core/java");

test("Regeln: Betriebssystem und Features", () => {
  assert.equal(rulesAllow([{ action: "allow", os: { name: "windows" } }], { platform: "win32" }), true);
  assert.equal(rulesAllow([{ action: "allow", os: { name: "windows" } }], { platform: "linux" }), false);
  assert.equal(rulesAllow([{ action: "allow" }, { action: "disallow", os: { name: "osx" } }], { platform: "darwin" }), false);
  assert.equal(rulesAllow([{ action: "allow", features: { is_demo_user: true } }], {}), false);
  assert.equal(rulesAllow([{ action: "allow", features: { has_custom_resolution: true } }], { features: { has_custom_resolution: true } }), true);
});

test("Argumente mit Platzhaltern und Regeln", () => {
  const args = collectArguments(["--username", "${auth_player_name}", { rules: [{ action: "allow", features: { is_demo_user: true } }], value: "--demo" }], { auth_player_name: "Steve" }, {});
  assert.deepEqual(args, ["--username", "Steve"]);
});

test("Maven-Pfad", () => {
  assert.equal(mavenPath("net.fabricmc:fabric-loader:0.19.5").split(/[\\/]/).join("/"), "net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar");
});

test("Offline-UUID wie bei Minecraft-Servern", () => {
  // bekannter Wert: OfflinePlayer:Notch
  assert.equal(offlineUuid("Notch"), "b50ad385-829d-3141-a216-7e7d7539ba7f");
  assert.throws(() => offlineAccount("a b"));
});

test("Java-Plattform", () => {
  assert.equal(runtimePlatform("win32", "x64"), "windows-x64");
  assert.equal(runtimePlatform("darwin", "arm64"), "mac-os-arm64");
  assert.equal(runtimePlatform("linux", "x64"), "linux");
});

test("log4j-XML wird lesbar", () => {
  const out = [];
  const p = new Log4jParser((l) => out.push(l));
  [
    '  <log4j:Event logger="Mytic Client" timestamp="1791473131385" level="INFO" thread="Render thread">',
    "    <log4j:Message><![CDATA[Mytic Client geladen: 13 Module]]></log4j:Message>",
    "  </log4j:Event>",
    "plain line",
  ].forEach((l) => p.push(l));
  assert.match(out[0], /^\[\d\d:\d\d:\d\d INFO\] \[Mytic Client\] Mytic Client geladen: 13 Module$/);
  assert.equal(out[1], "plain line");
});

const fsp = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const { compareVersions, removeOldVersion } = require("../src/core/updater");

test("Versionen vergleichen", () => {
  assert.ok(compareVersions("2.1.0", "2.0.9") > 0);
  assert.ok(compareVersions("2.0.10", "2.0.9") > 0);
  assert.equal(compareVersions("v2.0.1", "2.0.1"), 0);
  assert.ok(compareVersions("1.9.9", "2.0.0") < 0);
});

test("Alte EXE wird nur sicher gelöscht", async () => {
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), "mytic-upd-"));
  const old = path.join(dir, "MyticClient-2.0.1.exe");
  const current = path.join(dir, "MyticClient-2.1.0.exe");
  const other = path.join(dir, "wichtig.exe");
  for (const f of [old, current, other]) await fsp.writeFile(f, "x");
  assert.equal(await removeOldVersion(other, current, { tries: 1 }), false);
  assert.equal(await removeOldVersion(current, current, { tries: 1 }), false);
  assert.equal(await removeOldVersion(path.join(os.tmpdir(), "MyticClient-1.0.0.exe"), current, { tries: 1 }), false);
  assert.equal(await removeOldVersion(old, current, { tries: 1 }), true);
  await assert.rejects(fsp.access(old));
  await fsp.access(other);
  await fsp.access(current);
  await fsp.rm(dir, { recursive: true });
});
