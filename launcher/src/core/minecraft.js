// Installation und Start von Minecraft 1.21.11 mit Fabric, Mytic-Client-Mod und Performance-Mods.
const fsp = require("node:fs/promises");
const path = require("node:path");
const os = require("node:os");
const { spawn } = require("node:child_process");
const AdmZip = require("adm-zip");
const { getJson, downloadAll, downloadFile, isValid } = require("./download");
const { ensureJava } = require("./java");

const MC_VERSION = "1.21.11";
const VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const FABRIC_META = "https://meta.fabricmc.net/v2";
const MODRINTH = "https://api.modrinth.com/v2";
const LAUNCHER_NAME = "MyticClient";
const LAUNCHER_VERSION = "2.4.0";

/** Mods, die der Launcher verwaltet. required = immer installiert, sonst über die Einstellungen schaltbar. */
const MANAGED_MODS = [
  { slug: "fabric-api", name: "Fabric API", required: true },
  { slug: "sodium", name: "Sodium", description: "Deutlich mehr FPS durch neue Render-Engine", default: true },
  { slug: "lithium", name: "Lithium", description: "Schnellere Spiellogik, weniger Ruckler", default: true },
  { slug: "ferrite-core", name: "FerriteCore", description: "Weniger Arbeitsspeicher", default: true },
  { slug: "immediatelyfast", name: "ImmediatelyFast", description: "Schnelleres Zeichnen von HUD, Text und Items", default: true },
  { slug: "entityculling", name: "EntityCulling", description: "Unsichtbare Entities werden nicht gezeichnet", default: true },
  { slug: "iris", name: "Iris Shaders", description: "Shader-Unterstützung (braucht Sodium)", default: false, requires: ["sodium"] },
];

// ---------------------------------------------------------------------------------------------- Regeln & Platzhalter

function osName(platform = process.platform) {
  return platform === "win32" ? "windows" : platform === "darwin" ? "osx" : "linux";
}

function rulesAllow(rules, { platform = process.platform, arch = process.arch, features = {} } = {}) {
  if (!rules || rules.length === 0) return true;
  let allowed = false;
  for (const rule of rules) {
    let matches = true;
    if (rule.os) {
      if (rule.os.name && rule.os.name !== osName(platform)) matches = false;
      if (rule.os.arch && rule.os.arch === "x86" && arch !== "ia32") matches = false;
    }
    if (rule.features) {
      for (const [key, value] of Object.entries(rule.features)) if (Boolean(features[key]) !== value) matches = false;
    }
    if (matches) allowed = rule.action === "allow";
  }
  return allowed;
}

function substitute(value, vars) {
  return value.replace(/\$\{([a-zA-Z0-9_]+)\}/g, (all, key) => (key in vars ? String(vars[key]) : all));
}

function collectArguments(list, vars, options) {
  const out = [];
  for (const entry of list || []) {
    if (typeof entry === "string") out.push(substitute(entry, vars));
    else if (rulesAllow(entry.rules, options)) {
      for (const v of [].concat(entry.value)) out.push(substitute(v, vars));
    }
  }
  return out;
}

function mavenPath(name) {
  const [group, artifact, version, classifier] = name.split(":");
  const file = `${artifact}-${version}${classifier ? `-${classifier}` : ""}.jar`;
  return path.join(...group.split("."), artifact, version, file);
}

function libraryKey(name) {
  const parts = name.split(":");
  return `${parts[0]}:${parts[1]}${parts[3] ? `:${parts[3]}` : ""}`;
}

// ---------------------------------------------------------------------------------------------- Installation

class Installer {
  constructor(dirs, { platform = process.platform, arch = process.arch, log = () => {}, progress = () => {} } = {}) {
    this.dirs = dirs;
    this.platform = platform;
    this.arch = arch;
    this.log = log;
    this.progress = progress;
  }

  step(label, done = 0, total = 0) {
    this.progress({ label, done, total });
  }

  async versionJson() {
    const file = path.join(this.dirs.versions, MC_VERSION, `${MC_VERSION}.json`);
    try {
      return JSON.parse(await fsp.readFile(file, "utf8"));
    } catch {
      const manifest = await getJson(VERSION_MANIFEST);
      const entry = manifest.versions.find((v) => v.id === MC_VERSION);
      if (!entry) throw new Error(`Minecraft ${MC_VERSION} nicht gefunden`);
      await downloadFile(entry.url, file, { sha1: entry.sha1 });
      return JSON.parse(await fsp.readFile(file, "utf8"));
    }
  }

  async fabricProfile() {
    const file = path.join(this.dirs.versions, `fabric-${MC_VERSION}`, "profile.json");
    try {
      const loaders = await getJson(`${FABRIC_META}/versions/loader/${MC_VERSION}`);
      const loader = (loaders.find((l) => l.loader.stable) || loaders[0]).loader.version;
      const profile = await getJson(`${FABRIC_META}/versions/loader/${MC_VERSION}/${loader}/profile/json`);
      await fsp.mkdir(path.dirname(file), { recursive: true });
      await fsp.writeFile(file, JSON.stringify(profile, null, 2));
      return profile;
    } catch (error) {
      // offline: letzte bekannte Version benutzen
      try {
        return JSON.parse(await fsp.readFile(file, "utf8"));
      } catch {
        throw error;
      }
    }
  }

  /** Bibliotheken von Minecraft + Fabric; Fabric-Versionen ersetzen gleichnamige von Minecraft (z. B. ASM). */
  libraries(version, fabric) {
    const opts = { platform: this.platform, arch: this.arch };
    const byKey = new Map();
    const natives = [];
    for (const lib of version.libraries) {
      if (!rulesAllow(lib.rules, opts)) continue;
      const artifact = lib.downloads && lib.downloads.artifact;
      if (artifact) {
        const entry = { name: lib.name, url: artifact.url, file: path.join(this.dirs.libraries, artifact.path), sha1: artifact.sha1, size: artifact.size };
        byKey.set(libraryKey(lib.name), entry);
        if (lib.name.includes(":natives-")) natives.push(entry);
      }
      if (lib.natives && lib.downloads && lib.downloads.classifiers) {
        const classifier = lib.natives[osName(this.platform)];
        const native = classifier && lib.downloads.classifiers[classifier.replace("${arch}", "64")];
        if (native) {
          const entry = { name: `${lib.name}:${classifier}`, url: native.url, file: path.join(this.dirs.libraries, native.path), sha1: native.sha1, size: native.size };
          byKey.set(libraryKey(entry.name), entry);
          natives.push(entry);
        }
      }
    }
    for (const lib of fabric.libraries) {
      const rel = mavenPath(lib.name);
      const base = (lib.url || "https://maven.fabricmc.net/").replace(/\/?$/, "/");
      byKey.set(libraryKey(lib.name), { name: lib.name, url: base + rel.split(path.sep).join("/"), file: path.join(this.dirs.libraries, rel), sha1: lib.sha1, size: lib.size });
    }
    return { libraries: [...byKey.values()], natives };
  }

  async assets(version, { verify = false } = {}) {
    const index = version.assetIndex;
    const indexFile = path.join(this.dirs.assets, "indexes", `${index.id}.json`);
    if (!(await isValid(indexFile, { sha1: index.sha1 }))) await downloadFile(index.url, indexFile, { sha1: index.sha1 });
    const objects = JSON.parse(await fsp.readFile(indexFile, "utf8")).objects;
    const items = Object.values(objects).map(({ hash, size }) => ({
      url: `https://resources.download.minecraft.net/${hash.slice(0, 2)}/${hash}`,
      file: path.join(this.dirs.assets, "objects", hash.slice(0, 2), hash),
      sha1: hash,
      size,
      checkHash: verify,
    }));
    await downloadAll(items, { concurrency: 24, onProgress: (d, t) => this.step("Spieldateien (Texturen, Sounds)", d, t) });
  }

  async extractNatives(natives, nativesDir) {
    await fsp.rm(nativesDir, { recursive: true, force: true });
    await fsp.mkdir(nativesDir, { recursive: true });
    for (const native of natives) {
      const zip = new AdmZip(native.file);
      for (const entry of zip.getEntries()) {
        if (entry.isDirectory || entry.entryName.startsWith("META-INF/")) continue;
        if (!/\.(dll|so|dylib|jnilib)$/.test(entry.entryName)) continue;
        await fsp.writeFile(path.join(nativesDir, path.basename(entry.entryName)), entry.getData());
      }
    }
  }

  async modrinthFile(slug) {
    const params = `loaders=${encodeURIComponent('["fabric"]')}&game_versions=${encodeURIComponent(`["${MC_VERSION}"]`)}`;
    const versions = await getJson(`${MODRINTH}/project/${slug}/version?${params}`);
    const version = versions.find((v) => v.version_type === "release") || versions[0];
    if (!version) return null;
    const file = version.files.find((f) => f.primary) || version.files[0];
    return { url: file.url, filename: file.filename, sha1: file.hashes.sha1, size: file.size, version: version.version_number };
  }

  /** Verwaltete Mods aktualisieren; selbst hinzugefügte Mods im mods-Ordner bleiben unangetastet. */
  async mods(enabled, bundledModsDir, modsDir = path.join(this.dirs.game, "mods")) {
    await fsp.mkdir(modsDir, { recursive: true });
    const stateFile = path.join(modsDir, ".mytic-managed.json");
    let previous = [];
    try {
      previous = JSON.parse(await fsp.readFile(stateFile, "utf8"));
    } catch {
      // erster Start
    }
    const isOn = (m) => m.required || (enabled[m.slug] !== undefined ? Boolean(enabled[m.slug]) : Boolean(m.default));
    const slugs = new Set();
    for (const mod of MANAGED_MODS.filter(isOn)) {
      slugs.add(mod.slug);
      for (const dep of mod.requires || []) slugs.add(dep);
    }
    const files = [];
    let done = 0;
    for (const slug of slugs) {
      this.step(`Mods: ${MANAGED_MODS.find((m) => m.slug === slug)?.name || slug}`, done++, slugs.size);
      try {
        const info = await this.modrinthFile(slug);
        if (!info) {
          this.log(`[Mods] ${slug}: keine Version für ${MC_VERSION}, übersprungen`);
          continue;
        }
        const target = path.join(modsDir, info.filename);
        if (!(await isValid(target, info))) await downloadFile(info.url, target, info);
        files.push({ slug, file: info.filename });
      } catch (error) {
        // ohne Internet: vorhandene Datei weiterverwenden
        const old = previous.find((f) => f.slug === slug);
        if (old) files.push(old);
        this.log(`[Mods] ${slug}: ${error.message}`);
      }
    }
    for (const name of await fsp.readdir(bundledModsDir)) {
      if (!name.endsWith(".jar")) continue;
      await fsp.copyFile(path.join(bundledModsDir, name), path.join(modsDir, name));
      files.push({ slug: "mytic", file: name });
    }
    const keep = new Set(files.map((f) => f.file));
    for (const old of previous) {
      if (old && old.file && !keep.has(old.file)) await fsp.rm(path.join(modsDir, old.file), { force: true });
    }
    await fsp.writeFile(stateFile, JSON.stringify(files, null, 2));
    return files.map((f) => f.file);
  }

  /** Alles herunterladen, was zum Start nötig ist. Gibt die Startdaten zurück. */
  async install({ enabledMods = {}, bundledModsDir, modsDir, verify = false, skipAssets = false } = {}) {
    this.step("Minecraft-Version laden");
    const version = await this.versionJson();
    this.step("Fabric laden");
    const fabric = await this.fabricProfile();

    this.step("Java vorbereiten");
    const java = await ensureJava(version.javaVersion.component, this.dirs.runtime, {
      platform: this.platform,
      arch: this.arch,
      onProgress: (d, t) => this.step("Java herunterladen", d, t),
    });

    const clientJar = path.join(this.dirs.versions, MC_VERSION, `${MC_VERSION}.jar`);
    const client = version.downloads.client;
    const { libraries, natives } = this.libraries(version, fabric);
    const items = [
      { url: client.url, file: clientJar, sha1: client.sha1, size: client.size },
      ...libraries.map((l) => ({ ...l, checkHash: verify || !l.size })),
    ];
    let logConfig = null;
    if (version.logging && version.logging.client) {
      const file = version.logging.client.file;
      logConfig = { file: path.join(this.dirs.assets, "log_configs", file.id), argument: version.logging.client.argument };
      items.push({ url: file.url, file: logConfig.file, sha1: file.sha1, size: file.size });
    }
    await downloadAll(items, { concurrency: 16, onProgress: (d, t) => this.step("Bibliotheken", d, t) });

    if (!skipAssets) await this.assets(version, { verify });
    const nativesDir = path.join(this.dirs.versions, MC_VERSION, "natives");
    this.step("Native Dateien entpacken");
    await this.extractNatives(natives, nativesDir);

    const mods = bundledModsDir ? await this.mods(enabledMods, bundledModsDir, modsDir) : [];
    this.step("Fertig", 1, 1);
    return { version, fabric, java, clientJar, libraries, nativesDir, logConfig, mods };
  }
}

// ---------------------------------------------------------------------------------------------- Start

/** Baut die komplette Java-Kommandozeile. */
function buildCommand(install, dirs, account, settings = {}, { platform = process.platform, arch = process.arch } = {}) {
  const { version, fabric, java, clientJar, libraries, nativesDir, logConfig } = install;
  const separator = platform === "win32" ? ";" : ":";
  const classpath = [...libraries.map((l) => l.file), clientJar].join(separator);
  const features = { has_custom_resolution: Boolean(settings.width && settings.height) };
  const vars = {
    auth_player_name: account.name,
    version_name: `fabric-${MC_VERSION}`,
    game_directory: dirs.game,
    assets_root: dirs.assets,
    assets_index_name: version.assetIndex.id,
    auth_uuid: account.uuid.replace(/-/g, ""),
    auth_access_token: account.accessToken || "0",
    clientid: account.clientId || "",
    auth_xuid: account.xuid || "",
    user_type: account.type === "microsoft" ? "msa" : "legacy",
    version_type: "release",
    natives_directory: nativesDir,
    launcher_name: LAUNCHER_NAME,
    launcher_version: LAUNCHER_VERSION,
    classpath,
    classpath_separator: separator,
    library_directory: dirs.libraries,
    resolution_width: settings.width || 1280,
    resolution_height: settings.height || 720,
  };
  const ruleOptions = { platform, arch, features };
  const memory = Math.max(1024, Number(settings.memoryMb) || 4096);
  const jvm = [
    `-Xms${Math.min(1024, memory)}M`,
    `-Xmx${memory}M`,
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=200",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+DisableExplicitGC",
    "-XX:G1NewSizePercent=30",
    "-XX:G1ReservePercent=20",
    ...collectArguments(version.arguments.jvm, vars, ruleOptions),
    ...collectArguments(fabric.arguments && fabric.arguments.jvm, vars, ruleOptions),
    // Mods des gewählten Profils (Fabric lädt alle JARs aus diesem Ordner zusätzlich)
    ...(settings.modsDir ? [`-Dfabric.addMods=${settings.modsDir}`] : []),
    ...(settings.jvmArgs ? settings.jvmArgs.split(/\s+/).filter(Boolean) : []),
  ];
  if (logConfig) jvm.push(substitute(logConfig.argument, { path: logConfig.file }));
  const game = [
    ...collectArguments(version.arguments.game, vars, ruleOptions),
    ...collectArguments(fabric.arguments && fabric.arguments.game, vars, ruleOptions),
  ];
  if (settings.fullscreen) game.push("--fullscreen");
  if (settings.server) game.push("--quickPlayMultiplayer", settings.server);
  return { java, args: [...jvm, fabric.mainClass, ...game] };
}

/** Minecraft schreibt seine Logs als log4j-XML; daraus werden lesbare Zeilen wie "[12:00:01 INFO] [Logger] Text". */
class Log4jParser {
  constructor(emit) {
    this.emit = emit;
    this.event = null;
  }

  push(line) {
    const start = line.match(/<log4j:Event logger="([^"]*)" timestamp="(\d+)" level="([A-Z]+)" thread="([^"]*)"/);
    if (start) {
      this.event = { logger: start[1], time: Number(start[2]), level: start[3], thread: start[4], text: [] };
      return;
    }
    if (!this.event) {
      if (line.trim()) this.emit(line);
      return;
    }
    if (line.includes("</log4j:Event>")) {
      const e = this.event;
      this.event = null;
      const time = new Date(e.time).toTimeString().slice(0, 8);
      const logger = e.logger.replace(/^net\.minecraft\.class_\d+$/, "Minecraft").split(".").pop();
      const text = e.text.join("\n").trim();
      text.split("\n").forEach((part, i) => this.emit(i === 0 ? `[${time} ${e.level}] [${logger}] ${part}` : `    ${part}`));
      return;
    }
    const cleaned = line.replace(/<log4j:(Message|Throwable)><!\[CDATA\[/, "").replace(/\]\]><\/log4j:(Message|Throwable)>/, "");
    this.event.text.push(cleaned.replace(/^\s{4}/, ""));
  }
}

function launch(command, dirs, { onLog = () => {}, onExit = () => {} } = {}) {
  const child = spawn(command.java, command.args, { cwd: dirs.game, windowsHide: false, env: { ...process.env, _JAVA_OPTIONS: undefined } });
  const forward = (stream) => {
    let buffer = "";
    const parser = new Log4jParser(onLog);
    stream.on("data", (chunk) => {
      buffer += chunk.toString();
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop();
      for (const line of lines) parser.push(line);
    });
  };
  forward(child.stdout);
  forward(child.stderr);
  child.on("exit", (code) => onExit(code));
  child.on("error", (error) => {
    onLog(`[Launcher] Start fehlgeschlagen: ${error.message}`);
    onExit(-1);
  });
  return child;
}

function defaultMemoryMb() {
  const total = os.totalmem() / 1024 / 1024;
  return Math.max(2048, Math.min(4096, Math.floor(total / 2 / 512) * 512));
}

module.exports = { Log4jParser, Installer, buildCommand, launch, rulesAllow, collectArguments, mavenPath, MANAGED_MODS, MC_VERSION, defaultMemoryMb };
