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
