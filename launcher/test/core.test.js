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

const profiles = require("../src/core/profiles");

test("Profile: Umzug, Anlegen, Duplizieren, Löschen", async () => {
  const base = await fsp.mkdtemp(path.join(os.tmpdir(), "mytic-prof-"));
  const game = path.join(base, "game");
  await fsp.mkdir(path.join(game, "mods"), { recursive: true });
  await fsp.writeFile(path.join(game, "mods", "sodium.jar"), "x");
  let s = await profiles.migrate({ mods: { iris: true } }, base, game);
  assert.equal(s.profiles.length, 1);
  assert.deepEqual(s.profiles[0].perf, { iris: true });
  await fsp.access(path.join(profiles.modsDir(base, "standard"), "sodium.jar"));
  assert.deepEqual(await fsp.readdir(path.join(game, "mods")), []);
  s = await profiles.create(s, base, "  PvP  ", "standard");
  const pvp = profiles.active(s);
  assert.equal(pvp.name, "PvP");
  await fsp.access(path.join(profiles.modsDir(base, pvp.id), "sodium.jar"));
  s = profiles.rename(s, pvp.id, "Bedwars");
  assert.equal(profiles.active(s).name, "Bedwars");
  s = await profiles.remove(s, base, pvp.id);
  assert.equal(s.activeProfile, "standard");
  await assert.rejects(profiles.remove(s, base, "standard"));
  await assert.rejects(profiles.create(s, base, "   "));
  await fsp.rm(base, { recursive: true });
});

const http = require("node:http");
const { checkSiteUpdate } = require("../src/core/updater");

test("Update über die Download-Seite (Entwicklerversion)", async () => {
  const server = http.createServer((req, res) => {
    if (req.url === "/proxy/8765/api/latest/mytic-client-dev") {
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ version: "2.6.0", name: "MyticClient-Dev-2.6.0.exe", size: 5, sha256: "abc", url: "download/mytic-client-dev" }));
    } else { res.statusCode = 404; res.end(); }
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const base = `http://127.0.0.1:${server.address().port}/proxy/8765/`;
  const update = await checkSiteUpdate("2.5.0", base, "mytic-client-dev");
  assert.equal(update.version, "2.6.0");
  assert.equal(update.url, `${base}download/mytic-client-dev`);
  assert.equal(await checkSiteUpdate("2.6.0", base, "mytic-client-dev"), null);
  server.close();
});

test("1.8.9: Forge-Start mit eigenem Spielordner und Profil-Mods", async () => {
  const fs = require("node:fs");
  const os = require("node:os");
  const path = require("node:path");
  const { loaderFor, gameDirFor, buildCommand } = require("../src/core/minecraft");
  assert.equal(loaderFor("1.8.9"), "forge");
  assert.equal(loaderFor("1.21.11"), "fabric");
  const base = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-189-"));
  const dirs = { base, game: path.join(base, "game"), assets: path.join(base, "assets") };
  assert.equal(gameDirFor(dirs, "1.8.9"), path.join(base, "game-1.8.9"));
  assert.equal(gameDirFor(dirs, "1.21.11"), dirs.game);
  const modsDir = path.join(base, "profiles", "p1", "mods");
  fs.mkdirSync(modsDir, { recursive: true });
  fs.writeFileSync(path.join(modsDir, "a.jar"), "");
  fs.writeFileSync(path.join(modsDir, "b.jar.disabled"), "");
  const install = {
    mcVersion: "1.8.9", loader: "forge", gameDir: gameDirFor(dirs, "1.8.9"), java: "java", clientJar: "client.jar",
    libraries: [{ file: "lw.jar" }], nativesDir: "natives", logConfig: null, version: { assetIndex: { id: "1.8" } },
    fabric: { mainClass: "net.minecraft.launchwrapper.Launch", minecraftArguments: "--username ${auth_player_name} --gameDir ${game_directory} --assetIndex ${assets_index_name} --userProperties ${user_properties} --tweakClass x.FMLTweaker" },
  };
  const cmd = buildCommand(install, dirs, offlineAccount("Steve"), { modsDir, server: "play.example.net" }, { platform: "linux" });
  const args = cmd.args;
  assert.equal(cmd.cwd, install.gameDir);
  assert.equal(args[args.indexOf("--gameDir") + 1], install.gameDir);
  assert.equal(args[args.indexOf("--userProperties") + 1], "{}");
  assert.equal(args[args.indexOf("--mods") + 1], ["..", "profiles", "p1", "mods", "a.jar"].join(path.sep));
  assert.deepEqual(args.slice(args.indexOf("--server"), args.indexOf("--server") + 4), ["--server", "play.example.net", "--port", "25565"]);
  assert.ok(args.includes("net.minecraft.launchwrapper.Launch"));
  assert.ok(args.some((a) => a.startsWith("-Dmytic.config=")));
  fs.rmSync(base, { recursive: true, force: true });
});

test("Import: Modrinth App, NoRiskClient, Lunar und .mrpack werden erkannt", () => {
  const fs = require("node:fs");
  const os = require("node:os");
  const path = require("node:path");
  const AdmZip = require("adm-zip");
  const { DatabaseSync } = require("node:sqlite");
  const importer = require("../src/core/importer");
  const { loaderFor } = require("../src/core/minecraft");
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-import-test-"));
  const jar = (file, meta) => {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const zip = new AdmZip();
    zip.addFile(meta, Buffer.from("{}"));
    zip.writeZip(file);
  };
  const paths = {
    modrinth: path.join(home, "ModrinthApp"),
    noriskData: path.join(home, "norisk"),
    noriskMeta: path.join(home, "norisk", "meta"),
    lunar: path.join(home, ".lunarclient"),
  };
  // Modrinth App (neues Schema)
  fs.mkdirSync(paths.modrinth, { recursive: true });
  let db = new DatabaseSync(path.join(paths.modrinth, "app.db"));
  db.exec(`CREATE TABLE instances (id TEXT, path TEXT, name TEXT, applied_content_set_id TEXT);
           CREATE TABLE instance_content_sets (id TEXT, game_version TEXT, loader TEXT);
           INSERT INTO instances VALUES ('a', 'pvp', 'PvP Pack', 'c1'), ('b', 'old', 'Alt', 'c2');
           INSERT INTO instance_content_sets VALUES ('c1', '1.21.11', 'fabric'), ('c2', '1.20.1', 'forge');`);
  db.close();
  jar(path.join(paths.modrinth, "profiles", "pvp", "mods", "a.jar"), "fabric.mod.json");
  jar(path.join(paths.modrinth, "profiles", "pvp", "mods", "b.jar"), "fabric.mod.json");
  // NoRiskClient
  fs.mkdirSync(paths.noriskMeta, { recursive: true });
  db = new DatabaseSync(path.join(paths.noriskMeta, "app.db"));
  db.exec(`CREATE TABLE profiles (id TEXT, name TEXT, path TEXT, game_version TEXT, loader TEXT, is_standard_version INTEGER);
           CREATE TABLE profile_mods (profile_id TEXT, source TEXT, enabled INTEGER);
           INSERT INTO profiles VALUES ('n1', 'Survival', 'survival', '1.21.10', 'fabric', 0), ('n2', 'NRC 1.21', 'std', '1.21.4', 'fabric', 1);`);
  db.prepare("INSERT INTO profile_mods VALUES (?, ?, 1)").run("n1", JSON.stringify({ type: "modrinth", project_id: "P", version_id: "V", file_name: "sodium.jar", download_url: "https://cdn.modrinth.com/data/P/versions/V/sodium.jar" }));
  db.prepare("INSERT INTO profile_mods VALUES (?, ?, 1)").run("n1", JSON.stringify({ type: "url", url: "https://evil.example/x.jar" }));
  db.close();
  jar(path.join(paths.noriskData, "profiles", "survival", "mods", "local.jar"), "fabric.mod.json");
  // Lunar Client
  jar(path.join(paths.lunar, "profiles", "lunar", "1.21", "mods", "fabric-1.21.11", "l.jar"), "fabric.mod.json");
  jar(path.join(paths.lunar, "profiles", "lunar", "1.8", "mods", "f.jar"), "mcmod.info");

  const available = ["1.21.11", "1.21.10", "1.21.4", "1.8.9"];
  const { list } = importer.scan({ paths, available, loaderFor });
  const by = (name) => list.find((e) => e.name === name);
  assert.equal(by("PvP Pack").target, "1.21.11");
  assert.equal(by("PvP Pack").modCount, 2);
  assert.equal(by("Alt").target, null);
  assert.equal(by("Survival").target, "1.21.10");
  assert.equal(by("Survival").modCount, 2, "lokale Jar + Modrinth-Download, unsichere URL ignoriert");
  assert.equal(by("NRC 1.21"), undefined, "Standard-Profile von NoRisk werden nicht angeboten");
  assert.equal(by("Lunar 1.21.11").target, "1.21.11");
  assert.equal(by("Lunar 1.8").target, "1.8.9", "Lunars 1.8-Ordner ist 1.8.9 (Forge)");
  assert.equal(importer.resolveVersion("1.21", ["1.21.11", "1.21.9"]), "1.21.11");
  assert.equal(importer.resolveVersion("1.21", ["1.21.11", "1.21"]), "1.21");

  const pack = new AdmZip();
  pack.addFile("modrinth.index.json", Buffer.from(JSON.stringify({
    formatVersion: 1, game: "minecraft", name: "Mein Pack",
    dependencies: { minecraft: "1.21.11", "fabric-loader": "0.19.5" },
    files: [
      { path: "mods/x.jar", downloads: ["https://cdn.modrinth.com/data/a/x.jar"], hashes: { sha1: "0" }, fileSize: 1 },
      { path: "mods/server.jar", env: { client: "unsupported", server: "required" }, downloads: ["https://cdn.modrinth.com/s.jar"], hashes: {} },
      { path: "config/a.json", downloads: ["https://cdn.modrinth.com/c"], hashes: {} },
    ],
  })));
  pack.addFile("overrides/mods/extra.jar", Buffer.from("x"));
  const packFile = path.join(home, "pack.mrpack");
  pack.writeZip(packFile);
  const entry = importer.readMrpack(packFile);
  assert.equal(entry.downloads.length, 1);
  assert.deepEqual(entry.packJars, ["overrides/mods/extra.jar"]);
  const ev = importer.evaluate(entry, available, loaderFor);
  assert.equal(ev.target, "1.21.11");
  assert.equal(ev.modCount, 2);
  fs.rmSync(home, { recursive: true, force: true });
});

test("Import vom PC: Ordner, ZIP und einzelne Jars", () => {
  const fs = require("node:fs");
  const os = require("node:os");
  const path = require("node:path");
  const AdmZip = require("adm-zip");
  const importer = require("../src/core/importer");
  const { loaderFor } = require("../src/core/minecraft");
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-pc-import-"));
  const fabricJar = (file, mc) => {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const zip = new AdmZip();
    zip.addFile("fabric.mod.json", Buffer.from(JSON.stringify({ id: "x", depends: { minecraft: mc } })));
    zip.writeZip(file);
  };
  const available = ["1.21.11", "1.21.10", "1.21.4", "1.8.9"];
  // CurseForge-Instanz
  const cf = path.join(root, "CF Pack");
  fs.mkdirSync(cf, { recursive: true });
  fs.writeFileSync(path.join(cf, "minecraftinstance.json"), JSON.stringify({ name: "Mein CF", gameVersion: "1.21.4", baseModLoader: { name: "fabric-0.16.9-1.21.4" } }));
  fabricJar(path.join(cf, "mods", "a.jar"), "~1.21.4");
  // Prism/MultiMC-Instanz
  const prism = path.join(root, "Prism");
  fs.mkdirSync(prism, { recursive: true });
  fs.writeFileSync(path.join(prism, "mmc-pack.json"), JSON.stringify({ components: [{ uid: "net.minecraft", version: "1.21.10" }, { uid: "net.fabricmc.fabric-loader", version: "0.17" }] }));
  fabricJar(path.join(prism, ".minecraft", "mods", "b.jar"), ">=1.21.10");
  // Ordner ohne Metadaten: Version aus den Mods
  const plain = path.join(root, "Meine Mods");
  fabricJar(path.join(plain, "c.jar"), "1.21.11");
  fabricJar(path.join(plain, "d.jar"), ["1.21.11"]);
  // Lunar-Unterordner mit Version im Namen
  const lunar = path.join(root, "fabric-1.21.4");
  fabricJar(path.join(lunar, "e.jar"), "*");
  // ZIP-Export mit mods-Ordner und einzelne Jar
  const zip = new AdmZip();
  zip.addFile("export/mods/f.jar", Buffer.from("x"));
  zip.addFile("export/config/a.txt", Buffer.from("x"));
  const zipFile = path.join(root, "export.zip");
  zip.writeZip(zipFile);
  const single = path.join(root, "single.jar");
  fabricJar(single, "1.21.11");

  const items = importer.readPaths([cf, prism, plain, lunar, zipFile, single]).map((e) => importer.evaluate(e, available, loaderFor));
  const by = (name) => items.find((i) => i.name === name);
  assert.equal(by("Mein CF").target, "1.21.4");
  assert.equal(by("Prism").target, "1.21.10");
  assert.equal(by("Prism").modCount, 1);
  assert.equal(by("Meine Mods").target, "1.21.11");
  assert.equal(by("Meine Mods").modCount, 2);
  assert.equal(by("fabric-1.21.4").target, "1.21.4");
  assert.equal(by("export").modCount, 1);
  assert.equal(by("export").target, null, "ZIP ohne Version: Spieler wählt sie");
  assert.equal(by("export").chooseVersion, true);
  assert.equal(by("single").target, "1.21.11");
  // selbst gewählte Version
  const entry = importer.readPaths([zipFile])[0];
  assert.equal(importer.evaluate(entry, available, loaderFor, "1.21.10").target, "1.21.10");
  fs.rmSync(root, { recursive: true, force: true });
});

test("Updates wie bei Lunar: Modus, bereitliegendes Update, Umzug", async () => {
  const fs = require("node:fs");
  const os = require("node:os");
  const path = require("node:path");
  const updater = require("../src/core/updater");
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-upd-"));
  // Modus
  const installDir = path.join(root, "Programs", "mytic-client");
  fs.mkdirSync(installDir, { recursive: true });
  fs.writeFileSync(path.join(installDir, "Uninstall Mytic Client.exe"), "");
  assert.equal(updater.installMode({ packaged: true, portableFile: "", execPath: path.join(installDir, "Mytic Client.exe") }), "installed");
  assert.equal(updater.installMode({ packaged: true, portableFile: "C:/x/MyticClient-2.17.0.exe", execPath: "C:/tmp/x.exe" }), "portable");
  assert.equal(updater.installMode({ packaged: true, portableFile: "", execPath: path.join(root, "zip", "Mytic Client.exe") }), "zip");
  assert.equal(updater.installMode({ packaged: false, portableFile: "", execPath: "x" }), "zip");
  assert.ok(updater.SETUP_PATTERN.test("MyticClient-Setup-2.18.0.exe"));
  assert.ok(!updater.ASSET_PATTERN.test("MyticClient-Setup-2.18.0.exe"));
  // bereitliegendes Update
  const dir = path.join(root, "updates");
  fs.mkdirSync(dir);
  const file = path.join(dir, "MyticClient-Setup-2.18.0.exe");
  fs.writeFileSync(file, "setup");
  const sha256 = await updater.sha256File(file);
  await updater.writeReady(dir, { version: "2.18.0", file, sha256, kind: "setup" });
  assert.equal((await updater.readReady(dir, "2.17.0")).version, "2.18.0");
  assert.equal(await updater.readReady(dir, "2.18.0"), null, "schon installiert → aufräumen");
  assert.equal(fs.existsSync(dir), false);
  // manipulierte Datei wird nicht eingespielt
  fs.mkdirSync(dir);
  fs.writeFileSync(file, "setup");
  await updater.writeReady(dir, { version: "2.18.0", file, sha256, kind: "setup" });
  fs.writeFileSync(file, "boese");
  assert.equal(await updater.readReady(dir, "2.17.0"), null);
  // Umzug: gleiche Version erlaubt
  fs.mkdirSync(dir);
  fs.writeFileSync(file, "setup");
  await updater.writeReady(dir, { version: "2.18.0", file, sha256, kind: "setup", migrate: true });
  assert.equal((await updater.readReady(dir, "2.18.0")).migrate, true);
  // alte portable EXE nach dem Umzug löschen – nur passende Namen
  const old = path.join(root, "Downloads", "MyticClient-2.17.0.exe");
  fs.mkdirSync(path.dirname(old), { recursive: true });
  fs.writeFileSync(old, "");
  const other = path.join(root, "Downloads", "wichtig.exe");
  fs.writeFileSync(other, "");
  assert.equal(await updater.removeMigratedPortable(other, null, { tries: 1 }), false);
  assert.equal(await updater.removeMigratedPortable(old, path.join(installDir, "Mytic Client.exe"), { tries: 1 }), true);
  assert.equal(fs.existsSync(old), false);
  assert.equal(fs.existsSync(other), true);
  fs.rmSync(root, { recursive: true, force: true });
});

test("Mod-Updates wie bei Lunar: einzelne Jar laden, sonst mitgelieferte Mod", async () => {
  const fs = require("node:fs");
  const os = require("node:os");
  const path = require("node:path");
  const http = require("node:http");
  const crypto = require("node:crypto");
  const AdmZip = require("adm-zip");
  const { ModUpdater, jarVersion } = require("../src/core/modupdates");
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "mytic-modupd-"));
  const bundled = path.join(root, "bundled");
  fs.mkdirSync(path.join(bundled, "1.21.4"), { recursive: true });
  fs.writeFileSync(path.join(bundled, "1.21.4", "mytic-client-2.5.0+1.21.4.jar"), "alt");
  fs.mkdirSync(path.join(bundled, "1.21.11"), { recursive: true });
  fs.writeFileSync(path.join(bundled, "1.21.11", "mytic-client-2.5.0.jar"), "alt");
  assert.equal(jarVersion("mytic-client-2.5.0.jar", "1.21.11"), "2.5.0");
  assert.equal(jarVersion("mytic-client-2.5.0+1.21.4.jar", "1.21.4"), "2.5.0");
  assert.equal(jarVersion("mytic-client-2.5.0+1.21.4.jar", "1.21.5"), null);

  // Download-Seite (Entwicklerversion): ZIP mit allen Versionen
  const zip = new AdmZip();
  zip.addFile("1.21.4/mytic-client-2.6.0+1.21.4.jar", Buffer.from("neu-1.21.4"));
  zip.addFile("1.21.11/mytic-client-2.6.0+1.21.11.jar", Buffer.from("neu-1.21.11"));
  const zipData = zip.toBuffer();
  const sha = crypto.createHash("sha256").update(zipData).digest("hex");
  const server = http.createServer((req, res) => {
    if (req.url === "/api/latest/mytic-mods") {
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ version: "2.6.0", name: "MyticMods-2.6.0.zip", size: zipData.length, sha256: sha, url: "files/MyticMods-2.6.0.zip" }));
    } else if (req.url === "/files/MyticMods-2.6.0.zip") res.end(zipData);
    else res.writeHead(404).end();
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const site = new ModUpdater({ cacheDir: path.join(root, "cache"), bundledDir: bundled, source: { type: "site", base } });
  const dir = await site.prepare("1.21.4");
  assert.equal(dir, path.join(root, "cache"));
  assert.equal(fs.readFileSync(path.join(root, "cache", "1.21.4", "mytic-client-2.6.0+1.21.4.jar"), "utf8"), "neu-1.21.4");
  assert.equal(await site.prepare("1.21.11"), path.join(root, "cache"));
  server.close();

  // ohne Netz: Cache bleibt nutzbar, für andere Versionen die mitgelieferte Mod
  const offline = new ModUpdater({ cacheDir: path.join(root, "cache"), bundledDir: bundled, source: { type: "site", base: "http://127.0.0.1:1/" } });
  assert.equal(await offline.prepare("1.21.4"), path.join(root, "cache"));
  const fresh = new ModUpdater({ cacheDir: path.join(root, "leer"), bundledDir: bundled, source: { type: "site", base: "http://127.0.0.1:1/" } });
  assert.equal(await fresh.prepare("1.21.4"), bundled);

  // GitHub: Release "mods-x.y.z" mit einzelnen Jars, Prüfsumme aus "digest"
  const jar = Buffer.from("github-jar");
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    if (String(url).includes("/releases/tags/mods-latest")) return new Response("{}", { status: 404 });
    if (String(url).includes("/releases?")) {
      return new Response(JSON.stringify([
        { tag_name: "v2.18.0", assets: [] },
        { tag_name: "mods-2.6.0", draft: false, assets: [] },
        { tag_name: "mods-2.7.0", draft: false, assets: [
          { name: "mytic-client-2.7.0+1.21.4.jar", size: jar.length, browser_download_url: "https://example.test/a.jar", digest: `sha256:${crypto.createHash("sha256").update(jar).digest("hex")}` },
          { name: "mytic-client-2.7.0+1.21.5.jar", size: 1, browser_download_url: "https://example.test/b.jar", digest: "sha256:00" },
        ] },
      ]), { status: 200 });
    }
    if (String(url) === "https://example.test/a.jar") return new Response(jar, { status: 200 });
    return new Response("", { status: 404 });
  };
  try {
    const gh = new ModUpdater({ cacheDir: path.join(root, "gh"), bundledDir: bundled, source: { type: "github", repo: "x/y" } });
    assert.equal(await gh.prepare("1.21.4"), path.join(root, "gh"));
    assert.deepEqual(fs.readdirSync(path.join(root, "gh", "1.21.4")), ["mytic-client-2.7.0+1.21.4.jar"]);
    // falsche Prüfsumme → mitgelieferte Mod
    const bad = new ModUpdater({ cacheDir: path.join(root, "gh2"), bundledDir: bundled, source: { type: "github", repo: "x/y" } });
    fs.mkdirSync(path.join(bundled, "1.21.5"));
    fs.writeFileSync(path.join(bundled, "1.21.5", "mytic-client-2.5.0+1.21.5.jar"), "alt");
    assert.equal(await bad.prepare("1.21.5"), bundled);
  } finally {
    globalThis.fetch = realFetch;
  }
  fs.rmSync(root, { recursive: true, force: true });
});

test("Integrierte Mods: immer die neueste vorhandene Version", () => {
  const { newestVersion } = require("../src/core/minecraft");
  const v = (id, type, date) => ({ id, version_type: type, date_published: date });
  // neuere Beta schlägt ältere Release
  assert.equal(newestVersion([v("r", "release", "2026-08-28"), v("b", "beta", "2026-09-20")]).id, "b");
  // unabhängig von der Reihenfolge der API
  assert.equal(newestVersion([v("alt", "release", "2026-01-01"), v("neu", "release", "2026-09-01")]).id, "neu");
  // Alpha nur, wenn es nichts anderes gibt
  assert.equal(newestVersion([v("a", "alpha", "2026-10-01"), v("r", "release", "2026-09-01")]).id, "r");
  assert.equal(newestVersion([v("a", "alpha", "2026-10-01")]).id, "a");
  assert.equal(newestVersion([]), null);
});
