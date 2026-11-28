// Diagnose-Paket: Schwärzen, Mod-/Paket-Erkennung, Probleme und das Sammeln in eine ZIP.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const AdmZip = require("adm-zip");
const d = require("../src/core/diagbundle");

// private Adressen zusammensetzen (nicht als Literal im Quelltext)
const ip = (...parts) => parts.join(".");
const PRIV_A = ip(10, 1, 2, 3);
const PRIV_B = ip(192, 168, 178, 20);
const PRIV_C = ip(172, 20, 0, 1);
const WIN_VER = ip(10, 0, 19045, 2364);
const ctx = { userNames: ["niklas"], homeDirs: ["C:\\Users\\niklas"] };

test("Benutzername in Pfaden wird zu %USER% (Windows, JSON-escaped, andere Benutzer, Linux/macOS)", () => {
  const r = (s) => d.redactText(s, ctx);
  assert.equal(r("C:\\Users\\niklas\\AppData\\Roaming\\.myticclient"), "C:\\Users\\%USER%\\AppData\\Roaming\\.myticclient");
  assert.equal(r("c:/users/Niklas/Desktop"), "c:/users/%USER%/Desktop");
  assert.equal(r('"C:\\\\Users\\\\niklas\\\\x"'), '"C:\\\\Users\\\\%USER%\\\\x"');
  assert.equal(r("D:\\Users\\Bob\\Spiele"), "D:\\Users\\%USER%\\Spiele");
  assert.equal(r("/home/alice/.myticclient"), "/home/%USER%/.myticclient");
  assert.equal(r("/Users/carol/Library"), "/Users/%USER%/Library");
  assert.equal(r("C:\\Users\\Public\\Desktop"), "C:\\Users\\Public\\Desktop");
  assert.equal(r("E:\\OneDrive\\niklas\\mods"), "E:\\OneDrive\\%USER%\\mods");
});

test("Tokens, E-Mails und private IPs werden geschwärzt, öffentliche Adressen und Versionen bleiben", () => {
  const jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlMTIz";
  const text = [
    `--username Steve --accessToken ${jwt} --uuid 1234`,
    `--accessToken abcdefghijkl --xuid 2535400000000000`,
    "token=geheim123 friendsToken: abcdEFGH",
    '"refreshToken":"M.R3_BAY.xyzxyzxyz"',
    "Authorization: Bearer abcdefghijklmnop",
    "mail: max.mustermann@example.de",
    `LAN ${PRIV_A} ${PRIV_B}:25565 ${PRIV_C} fe80::1c2b:3a4d%eth0`,
    "Server play.hypixel.net 8.8.8.8 1.21.10 Fabric 0.19.5",
    `Windows ${WIN_VER}`,
    `MYTIC_LAUNCHER_KEY=abcdef123456`,
  ].join("\n");
  const out = d.redactText(text, ctx);
  for (const secret of [jwt, "abcdefghijkl ", "2535400000000000", "geheim123", "abcdEFGH", "M.R3_BAY", "abcdefghijklmnop", "max.mustermann", PRIV_A, PRIV_B, PRIV_C, "fe80::", "abcdef123456"]) {
    assert.ok(!out.includes(secret), `noch sichtbar: ${secret}\n${out}`);
  }
  for (const keep of ["Steve", "--uuid 1234", "play.hypixel.net", "8.8.8.8", "1.21.10", "0.19.5", WIN_VER, "<privat-IP>:25565", "<E-Mail>"]) {
    assert.ok(out.includes(keep), `fehlt: ${keep}\n${out}`);
  }
});

test("JSON: Geheimnisse (wie Cloud-Sync), Sync-Daten und E-Mails raus, Schalter bleiben", () => {
  const v = d.redactValue({
    memoryMb: 4096, cloudSync: true, customClientId: "abc",
    friendsToken: "x", nested: { apiKey: "k", password: "p", sessionId: "s", list: [{ accessToken: "t", name: "ok" }] },
    syncState: { hash: "h" }, lastSyncBackup: "2026", email: "a@b.de", accounts: [{ name: "Steve" }],
    path: "C:\\Users\\niklas\\x", note: "IP " + PRIV_B,
  }, ctx);
  assert.equal(v.memoryMb, 4096);
  assert.equal(v.cloudSync, true);
  assert.equal(v.friendsToken, "<entfernt>");
  assert.deepEqual(v.nested, { apiKey: "<entfernt>", password: "<entfernt>", sessionId: "<entfernt>", list: [{ accessToken: "<entfernt>", name: "ok" }] });
  assert.equal(v.syncState, "<entfernt>");
  assert.equal(v.lastSyncBackup, "<entfernt>");
  assert.equal(v.email, "<entfernt>");
  assert.equal(v.accounts, "<entfernt>");
  assert.equal(v.path, "C:\\Users\\%USER%\\x");
  assert.equal(v.note, "IP <privat-IP>");
});

test("pack.mcmeta: min_format/max_format ab pack_format 65 Pflicht", () => {
  assert.equal(d.checkPackMeta({ pack: { pack_format: 34, description: "" } }).ok, true);
  assert.equal(d.checkPackMeta({ pack: { min_format: 65, max_format: [69, 0] } }).ok, true);
  assert.match(d.checkPackMeta({ pack: { pack_format: 69 } }).issue, /min_format\/max_format/);
  assert.match(d.checkPackMeta({ pack: { pack_format: 69, min_format: 65 } }).issue, /min_format ohne max_format/);
  assert.match(d.checkPackMeta({ pack: { description: "x" } }).issue, /weder pack_format/);
  assert.match(d.checkPackMeta({ pack: { min_format: 70, max_format: 65 } }).issue, /größer/);
  assert.equal(d.checkPackMeta({}).ok, false);
  assert.deepEqual(d.activePacks('resourcePacks:["vanilla","file/Faithful.zip"]\nlang:de_de'), ["vanilla", "file/Faithful.zip"]);
});

/** Jar mit fabric.mod.json bzw. mcmod.info */
function jar(file, entries, { store = false } = {}) {
  const zip = new AdmZip();
  for (const [name, content] of Object.entries(entries)) {
    zip.addFile(name, Buffer.from(typeof content === "string" ? content : JSON.stringify(content)));
    if (store) zip.getEntry(name).header.method = 0;
  }
  zip.addFile("big/filler.bin", Buffer.alloc(100000, 7));
  zip.writeZip(file);
}

test("ZIP-Eintrag lesen (gespeichert und komprimiert) und Mod-Infos aus Fabric/Forge-Jars", async () => {
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), "diag-zip-"));
  jar(path.join(dir, "a.jar"), { "fabric.mod.json": { id: "Sodium", name: "Sodium", version: "0.6.0", provides: ["indium"] } });
  jar(path.join(dir, "b.jar"), { "mcmod.info": [{ modid: "patcher", name: "PolyPatcher", version: "1.8.9" }] }, { store: true });
  jar(path.join(dir, "c.jar"), { "readme.txt": "nix" });
  assert.deepEqual(await d.modInfo(path.join(dir, "a.jar")), { id: "sodium", name: "Sodium", version: "0.6.0", loader: "fabric", provides: ["indium"] });
  assert.equal((await d.modInfo(path.join(dir, "b.jar"))).name, "PolyPatcher");
  assert.equal(await d.modInfo(path.join(dir, "c.jar")), null);
  assert.equal(String(await d.readZipEntry(path.join(dir, "c.jar"), "readme.txt")), "nix");
  assert.equal(await d.readZipEntry(path.join(dir, "c.jar"), "fehlt.txt"), null);
});

const mod = (o) => ({ file: `${o.id}.jar`, enabled: true, source: "eigene", name: o.id, version: "1.0", provides: [], ...o });
const ids = (list) => list.map((p) => p.id);

test("Probleme: rrls installiert (Warnung) und Hinweise der Mod aus mytic-diagnose.json", () => {
  const hint = { id: "rrls-stuck", level: "warn", title: "Ladebalken hing bei 100 % (rrls)", detail: "14 s" };
  const problems = d.detectProblems({ mods: [mod({ id: "rrls" })], memoryMb: 4096, totalMb: 16384, live: { hints: [hint, hint, { id: "x" }] } });
  const rrls = problems.find((p) => p.id === "heavy-rrls");
  assert.ok(rrls, `fehlt: heavy-rrls in ${ids(problems).join(", ")}`);
  assert.equal(rrls.level, "warn");
  assert.equal(rrls.title, "rrls installiert");
  assert.match(rrls.detail, /Remove Reloading Screen.*Ladebalken hängen.*zum Testen deaktivieren/);
  assert.deepEqual(ids(problems).filter((id) => id.startsWith("live-")), ["live-rrls-stuck"], "Hinweis einmal, unvollständige ignoriert");
  // ausgeschaltet → kein Hinweis
  assert.ok(!ids(d.detectProblems({ mods: [mod({ id: "rrls", enabled: false })], memoryMb: 4096, totalMb: 16384 })).includes("heavy-rrls"));
});

test("Probleme: doppelte Mods, InvSee neben Mytic, OptiFine+Sodium, RAM, Pakete, veraltet, Zeiten, Abstürze", () => {
  const problems = d.detectProblems({
    mods: [
      mod({ id: "myticclient", source: "mytic", version: "2.58.1", file: "mytic-client-2.58.1.jar" }),
      mod({ id: "invseesummary", file: "invseesummary-1.2.jar" }),
      mod({ id: "sodium", source: "integriert" }), mod({ id: "sodium", file: "sodium-old.jar" }),
      mod({ id: "optifabric" }), mod({ id: "xaerominimap" }), mod({ id: "essential", enabled: false }),
    ],
    packs: [{ name: "Alt.zip", active: true, issue: "pack_format 69 ohne min_format/max_format (Pflicht seit 1.21.9)" }, { name: "Ok.zip", active: true }],
    memoryMb: 1536, totalMb: 16384,
    live: { timings: { gameStartToTitleMs: 120000, joins: [{ server: "x", totalMs: 32000, connectMs: 2000, loadingMs: 30000, maxFrameMs: 4500 }], reloads: [{ ms: 45000, packs: 5, initial: false }] } },
    crashReports: [{ name: "crash-1.txt", mtimeMs: 2000, description: "Rendering overlay" }], hsErr: [{ name: "hs_err_pid42.log" }],
    lastLaunch: { at: 1000, exitCode: 1 },
    cosmetics: { local: "1.4.0", latest: "1.5.0" }, newestMytic: "2.59.0",
  });
  const got = ids(problems);
  for (const id of ["duplicate-sodium", "overlap-invseesummary", "overlap-xaerominimap", "heavy-optifabric", "ram-low", "pack-Alt.zip", "cosmetics-old", "mytic-old", "slow-start", "slow-join", "join-spike", "slow-reload", "crash", "hs-err"]) {
    assert.ok(got.includes(id), `fehlt: ${id} in ${got.join(", ")}`);
  }
  assert.ok(!got.includes("heavy-essential"), "ausgeschaltete Mods zählen nicht");
  assert.ok(!got.includes("exit-code"), "mit Absturzbericht kein zusätzlicher Exit-Code-Hinweis");
  assert.equal(problems.find((p) => p.id === "ram-low").level, "error");
  const levels = problems.map((p) => p.level);
  assert.deepEqual(levels, [...levels].sort((a, b) => ["error", "warn", "info"].indexOf(a) - ["error", "warn", "info"].indexOf(b)));
  // InvSee ohne Mytic: kein Doppel-Hinweis; alles gut → leer
  assert.ok(!ids(d.detectProblems({ mods: [mod({ id: "invseesummary" })], memoryMb: 4096, totalMb: 16384 })).includes("overlap-invseesummary"));
  assert.deepEqual(d.detectProblems({ mods: [mod({ id: "myticclient", source: "mytic" })], memoryMb: 4096, totalMb: 16384, cosmetics: { local: "1.5.0", latest: "1.5.0" } }), []);
  assert.ok(ids(d.detectProblems({ memoryMb: 4096, totalMb: 16384, lastLaunch: { at: 1, exitCode: -1073740791 } })).includes("exit-code"));
});

test("GPU/Java/Fabric aus Log und Absturzbericht", () => {
  const info = d.parseLogInfo("[Render thread/INFO]: OpenGL Renderer: NVIDIA GeForce RTX 3060/PCIe/SSE2\nLoading Minecraft 1.21.11 with Fabric Loader 0.19.5\n\tJava Version: 21.0.8, Microsoft");
  assert.equal(info.gpu, "NVIDIA GeForce RTX 3060/PCIe/SSE2");
  assert.equal(info.fabric, "0.19.5");
  assert.equal(info.java, "21.0.8, Microsoft");
  assert.equal(d.parseLogInfo("\tGraphics card #0 name: AMD Radeon RX 6600").gpu, "AMD Radeon RX 6600");
});

test("Sammeln: ZIP mit Logs, Abstürzen, Mods, Paketen, Einstellungen – alles geschwärzt, alte Pakete aufgeräumt", async () => {
  const root = await fsp.mkdtemp(path.join(os.tmpdir(), "diag-collect-"));
  const game = path.join(root, "game");
  const mods = path.join(root, "profiles", "standard", "mods");
  const out = path.join(root, "diagnose");
  const logs = path.join(root, "launcher-logs");
  for (const dir of [path.join(game, "logs"), path.join(game, "crash-reports"), path.join(game, "resourcepacks", "Ordner"), path.join(game, "config"), path.join(game, "mytic", "diagnose"), mods, out, logs]) {
    await fsp.mkdir(dir, { recursive: true });
  }
  const home = "C:\\Users\\niklas";
  await fsp.writeFile(path.join(game, "logs", "latest.log"), [
    `[12:00:00] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.19.5`,
    `[12:00:01] [main/INFO]: Game dir ${home}\\AppData\\Roaming\\.myticclient\\game`,
    `[12:00:02] [Render thread/INFO]: OpenGL Renderer: Intel(R) UHD Graphics 620`,
    `[12:00:03] [main/INFO]: Connecting to ${PRIV_B}, 25565 as niklas.test@example.com`,
    `[12:00:04] [main/INFO]: --accessToken eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlMTIz`,
  ].join("\n"));
  await fsp.writeFile(path.join(game, "logs", "mytic-diagnose.json"), JSON.stringify({
    version: 1, mytic: "2.59.0", playSeconds: 600, fpsAvg: 90, server: PRIV_A,
    timings: { gameStartToTitleMs: 41000, joins: [{ server: "play.example.net", kind: "server", connectMs: 1200, loadingMs: 3400, totalMs: 4600, maxFrameMs: 380, afterJoinMaxFrameMs: 120 }], reloads: [{ ms: 9000, packs: 3, initial: true }] },
    system: { java: "21.0.8", jvmArgs: ["-Xmx4096M"] },
  }));
  await fsp.writeFile(path.join(game, "crash-reports", "crash-2026-11-26_12.00.00-client.txt"), `Description: Rendering overlay\n\tJava Version: 21.0.8\nPath ${home}\\x`);
  await fsp.writeFile(path.join(game, "hs_err_pid4242.log"), `# A fatal error\n# Problematic frame: nvoglv64.dll\n${home}\\x`);
  await fsp.writeFile(path.join(game, "options.txt"), 'renderDistance:12\nsimulationDistance:8\nlastServer:geheim.example.net\nresourcePacks:["vanilla","file/Alt.zip","file/Ordner"]\n');
  jar(path.join(game, "resourcepacks", "Alt.zip"), { "pack.mcmeta": { pack: { pack_format: 69, description: "alt" } } });
  await fsp.writeFile(path.join(game, "resourcepacks", "Ordner", "pack.mcmeta"), JSON.stringify({ pack: { min_format: 65, max_format: 75, description: "ok" } }));
  await fsp.writeFile(path.join(game, "config", "myticclient.json"), JSON.stringify({ enabled: { fps: true }, friendsToken: "SECRET-XYZ", syncState: { hash: "abc" }, note: `${home}\\Bilder` }));
  await fsp.writeFile(path.join(game, "mytic", "diagnose", "Mytic-Profil-2026-11-26_12-00-00.txt"), "Anteil pro Mod\n 40.0 %  sodium");
  jar(path.join(mods, "mytic-client-2.58.1.jar"), { "fabric.mod.json": { id: "myticclient", name: "Mytic Client", version: "2.58.1" } });
  jar(path.join(mods, "invseesummary-1.2.0.jar"), { "fabric.mod.json": { id: "invseesummary", name: "InvSee Summary", version: "1.2.0" } });
  jar(path.join(mods, "sodium-0.6.jar"), { "fabric.mod.json": { id: "sodium", name: "Sodium", version: "0.6.0" } });
  jar(path.join(mods, "zoomify.jar.disabled"), { "fabric.mod.json": { id: "zoomify", name: "Zoomify", version: "2.0" } });
  await fsp.writeFile(path.join(logs, "launcher.log"), `[12:00:00] [Launcher] Start ${home}\\x token=abc12345\n`);
  for (let i = 0; i < 11; i++) await fsp.writeFile(path.join(out, `Mytic-Diagnose-2020-01-${String(i + 1).padStart(2, "0")}_00-00-00.zip`), "alt");
  await fsp.writeFile(path.join(out, "fremd.zip"), "bleibt");

  const result = await d.collect({
    outDir: out, now: new Date(2026, 10, 26, 13, 5, 9), redact: ctx, gameDir: game, modsDir: mods,
    managedFiles: ["sodium-0.6.jar"], userState: { mods: {} }, launcherLogs: [path.join(logs, "launcher.log")],
    configFiles: [{ name: "myticclient.json", file: path.join(game, "config", "myticclient.json") }, { name: "fehlt.json", file: path.join(root, "fehlt.json") }],
    system: { os: "Windows_NT 10", cpu: "Ryzen", cpuCores: 12, totalMb: 16384, launcher: "2.59.0" },
    settings: { memoryMb: 4096, jvmArgs: `-XX:ErrorFile=${home}\\err.log` }, profile: { name: "PvP", mcVersion: "1.21.11", loader: "fabric" },
    lastLaunch: { at: 0, prepareMs: 3500, exitCode: 0 }, cosmetics: { local: "1.5.0", latest: null }, newestMytic: "2.58.1", totalMb: 16384,
  });
  assert.equal(result.name, "Mytic-Diagnose-2026-11-26_13-05-09.zip");
  assert.ok(fs.existsSync(result.file));
  const zip = new AdmZip(result.file);
  const names = zip.getEntries().map((e) => e.entryName).sort();
  for (const n of ["ZUSAMMENFASSUNG.txt", "diagnose.json", "logs/launcher.log", "logs/latest.log", "logs/mytic-diagnose.json", "crash-reports/crash-2026-11-26_12.00.00-client.txt", "hs_err/hs_err_pid4242.log", "config/myticclient.json", "profil/Mytic-Profil-2026-11-26_12-00-00.txt"]) {
    assert.ok(names.includes(n), `fehlt im Paket: ${n} (${names.join(", ")})`);
  }
  // nichts Geheimes im Paket
  const all = zip.getEntries().map((e) => e.getData().toString("utf8")).join("\n");
  for (const secret of ["niklas", "SECRET-XYZ", "eyJhbGci", "abc12345", PRIV_A, PRIV_B, "example.com", "geheim.example.net", '"hash"']) {
    assert.ok(!all.includes(secret), `geheim im Paket: ${secret}`);
  }
  assert.ok(all.includes("%USER%"));
  // Inhalt
  const report = JSON.parse(zip.getEntry("diagnose.json").getData().toString("utf8"));
  assert.equal(report.system.gpu, "Intel(R) UHD Graphics 620");
  assert.equal(report.system.java, "21.0.8");
  assert.equal(report.system.fabricLoader, "0.19.5");
  assert.equal(report.system.myticMod, "2.58.1");
  assert.equal(report.system.options.renderDistance, "12");
  assert.equal(report.system.options.lastServer, undefined);
  assert.deepEqual(report.mods.map((m) => [m.id, m.source, m.enabled]), [["invseesummary", "eigene", true], ["myticclient", "mytic", true], ["sodium", "integriert", true], ["zoomify", "eigene", false]]);
  assert.deepEqual(report.packs.map((p) => [p.name, p.active, Boolean(p.issue)]), [["Alt.zip", true, true], ["Ordner", true, false]]);
  assert.equal(report.timings.joins[0].totalMs, 4600);
  const pids = result.problems.map((p) => p.id);
  assert.ok(pids.includes("overlap-invseesummary") && pids.includes("pack-Alt.zip") && pids.includes("crash") && pids.includes("hs-err"), pids.join(", "));
  assert.ok(!pids.includes("overlap-zoomify"), "ausgeschaltete Mod ist kein Doppel");
  // Text-Zusammenfassung
  assert.match(result.text, /Spielstart → Hauptmenü: 41 s/);
  assert.match(result.text, /Join play\.example\.net: gesamt 4,6 s/);
  assert.match(result.text, /\[WARNUNG\] InvSee Summary doppelt/);
  assert.ok(!result.text.includes("niklas"));
  // aufräumen: höchstens 10 eigene Pakete, fremde Dateien bleiben
  const left = (await fsp.readdir(out)).filter((n) => /^Mytic-Diagnose-/.test(n));
  assert.equal(left.length, 10);
  assert.ok(left.includes(result.name));
  assert.ok(fs.existsSync(path.join(out, "fremd.zip")));
});

test("Sammeln ohne Spielordner (noch nie gespielt) klappt trotzdem", async () => {
  const root = await fsp.mkdtemp(path.join(os.tmpdir(), "diag-empty-"));
  const result = await d.collect({ outDir: path.join(root, "out"), redact: ctx, gameDir: path.join(root, "game"), modsDir: path.join(root, "mods"), settings: {}, profile: { name: "Standard", mcVersion: "1.21.11" } });
  assert.ok(fs.existsSync(result.file));
  assert.match(result.text, /keine Messung der Mod/);
});

test("Launcher-Log: gepuffert schreiben und bei Überlänge rotieren", async () => {
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), "diag-log-"));
  const log = new d.LauncherLog(dir, { maxBytes: 50 });
  log.append("[Launcher] erste Zeile");
  log.append("zweite\nZeile");
  await log.flush();
  const text = await fsp.readFile(path.join(dir, "launcher.log"), "utf8");
  assert.match(text, /\] \[Launcher\] erste Zeile\n/);
  assert.match(text, /zweite Zeile/);
  log.append("dritte");
  await log.flush();
  assert.ok(fs.existsSync(path.join(dir, "launcher.old.log")));
  assert.deepEqual(log.files().map((f) => path.basename(f)), ["launcher.old.log", "launcher.log"]);
});
