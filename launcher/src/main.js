// Electron-Hauptprozess des Mytic Client Launchers.
const { app, BrowserWindow, ipcMain, shell, safeStorage, session } = require("electron");
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const os = require("node:os");
const crypto = require("node:crypto");
const { dirs: makeDirs } = require("./core/paths");
const { Installer, buildCommand, launch, MANAGED_MODS, MC_VERSION, defaultMemoryMb } = require("./core/minecraft");
const auth = require("./core/auth");

const DIRS = makeDirs();
/** Offline-Konten nur für Entwickler-Tests: im Quellcode-Start (npm start) oder mit MYTIC_DEV=1, nie in der veröffentlichten EXE. */
const OFFLINE_ALLOWED = process.env.MYTIC_DEV === "1" || (!app.isPackaged && process.env.MYTIC_RELEASE !== "1");
const SETTINGS_FILE = path.join(DIRS.base, "settings.json");
const ACCOUNTS_FILE = path.join(DIRS.base, "accounts.json");
const GAME_CONFIG = path.join(DIRS.game, "config", "myticclient.json");
const BUNDLED_MODS = app.isPackaged ? path.join(process.resourcesPath, "resources", "mods") : path.join(__dirname, "..", "resources", "mods");
const BUNDLED_CONFIG = app.isPackaged ? path.join(process.resourcesPath, "resources", "config.json") : path.join(__dirname, "..", "resources", "config.json");

/** In-Game-Module der Mytic-Mod (gleiche IDs und Standardwerte wie im Mod-Code). */
const GAME_MODULES = [
  { id: "fps", name: "FPS", description: "Bilder pro Sekunde", category: "HUD", default: true },
  { id: "cps", name: "CPS", description: "Klicks pro Sekunde (links | rechts)", category: "HUD", default: true },
  { id: "ping", name: "Ping", description: "Verbindung zum Server in ms", category: "HUD", default: true },
  { id: "keystrokes", name: "Keystrokes", description: "WASD, Maustasten und Leertaste", category: "HUD", default: true },
  { id: "armor", name: "Rüstung", description: "Rüstung und Haltbarkeit", category: "HUD", default: true },
  { id: "potions", name: "Tränke", description: "Aktive Effekte mit Restzeit", category: "HUD", default: true },
  { id: "coords", name: "Koordinaten", description: "X, Y und Z deiner Position", category: "HUD", default: true },
  { id: "direction", name: "Richtung", description: "Himmelsrichtung, in die du schaust", category: "HUD", default: true },
  { id: "clock", name: "Uhrzeit", description: "Echte Uhrzeit im HUD", category: "HUD", default: false },
  { id: "speed", name: "Tempo", description: "Geschwindigkeit in Blöcken pro Sekunde", category: "HUD", default: false },
  { id: "memory", name: "Arbeitsspeicher", description: "RAM-Verbrauch von Minecraft", category: "HUD", default: false },
  { id: "server", name: "Server-IP", description: "Adresse des aktuellen Servers", category: "HUD", default: false },
  { id: "biome", name: "Biom", description: "Biom, in dem du stehst", category: "HUD", default: false },
  { id: "daytime", name: "Spielzeit", description: "Tag und Uhrzeit in der Welt", category: "HUD", default: false },
  { id: "combo", name: "Combo", description: "Treffer hintereinander", category: "HUD", default: false },
  { id: "reach", name: "Reach", description: "Abstand deines letzten Treffers", category: "HUD", default: false },
  { id: "session", name: "Session", description: "Wie lange du schon spielst", category: "HUD", default: false },
  { id: "items", name: "Item-Zähler", description: "Pfeile, Perlen, Goldäpfel, Totems", category: "HUD", default: false },
  { id: "targethud", name: "Target-HUD", description: "Leben und Name deines Gegners", category: "HUD", default: false },
  { id: "watermark", name: "Wasserzeichen", description: "Mytic-Schriftzug im HUD", category: "HUD", default: false },
  { id: "zoom", name: "Zoom", description: "Mit C zoomen, Mausrad ändert die Stärke", category: "Mechanik", default: true },
  { id: "freelook", name: "Freelook", description: "Mit Alt umsehen (auf manchen Servern verboten)", category: "Mechanik", default: false },
  { id: "togglesprint", name: "Toggle Sprint", description: "Automatisch sprinten beim Vorwärtslaufen", category: "Mechanik", default: false },
  { id: "togglesneak", name: "Toggle Sneak", description: "Schleichen per Tastendruck an/aus", category: "Mechanik", default: false },
  { id: "crosshair", name: "Crosshair", description: "Eigenes Fadenkreuz in Form und Farbe", category: "Visuell", default: false },
  { id: "fullbright", name: "Fullbright", description: "Alles hell, auch in Höhlen und nachts", category: "Visuell", default: false },
  { id: "timechanger", name: "Time Changer", description: "Eigene Tageszeit, nur für dich", category: "Visuell", default: false },
  { id: "clearweather", name: "Klares Wetter", description: "Kein Regen und kein Gewitter", category: "Visuell", default: false },
  { id: "nohurtcam", name: "No Hurt Cam", description: "Kein Kamerawackeln bei Schaden", category: "Visuell", default: false },
  { id: "scoreboard", name: "Scoreboard", description: "Scoreboard verkleinern oder ausblenden", category: "Visuell", default: false },
  { id: "bossbar", name: "Bossbar ausblenden", description: "Blendet die Bossleiste aus", category: "Visuell", default: false },
  { id: "theme", name: "Design", description: "Akzentfarbe und Mytic-Hauptmenü", category: "Client", default: true },
];

let win;
let game = null;
let busy = false;

// ---------------------------------------------------------------------------------------------- Speicher

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return fallback;
  }
}

async function writeJson(file, data) {
  await fsp.mkdir(path.dirname(file), { recursive: true });
  await fsp.writeFile(file, JSON.stringify(data, null, 2));
}

function defaultSettings() {
  const bundled = readJson(BUNDLED_CONFIG, {});
  return {
    memoryMb: defaultMemoryMb(),
    width: 1280,
    height: 720,
    fullscreen: false,
    jvmArgs: "",
    server: "",
    afterLaunch: "minimize",
    mods: {},
    clientId: bundled.microsoftClientId || "",
    customClientId: "",
    recentServers: [],
    verifyNext: false,
  };
}

function settings() {
  const defaults = defaultSettings();
  const merged = { ...defaults, ...readJson(SETTINGS_FILE, {}) };
  // eingebaute Client-ID gilt für alle, außer der Spieler trägt eine eigene ein
  merged.clientId = merged.customClientId || defaults.clientId;
  return merged;
}

function protect(text) {
  if (!text) return "";
  return safeStorage.isEncryptionAvailable() ? `enc:${safeStorage.encryptString(text).toString("base64")}` : `raw:${text}`;
}

function unprotect(text) {
  if (!text) return "";
  if (text.startsWith("enc:")) return safeStorage.decryptString(Buffer.from(text.slice(4), "base64"));
  return text.replace(/^raw:/, "");
}

function accounts() {
  return readJson(ACCOUNTS_FILE, { selected: null, list: [] });
}

async function saveAccount(account, refreshToken) {
  const store = accounts();
  const entry = { ...account, accessToken: protect(account.accessToken), refreshToken: protect(refreshToken) };
  store.list = store.list.filter((a) => a.uuid !== account.uuid).concat(entry);
  store.selected = account.uuid;
  await writeJson(ACCOUNTS_FILE, store);
}

function publicAccounts() {
  const store = accounts();
  const list = store.list.filter((a) => OFFLINE_ALLOWED || a.type === "microsoft");
  const selected = list.some((a) => a.uuid === store.selected) ? store.selected : (list[0] ? list[0].uuid : null);
  return { selected, list: list.map(({ type, name, uuid }) => ({ type, name, uuid })) };
}

/** Holt das gewählte Konto mit gültigem Token (erneuert Microsoft-Tokens bei Bedarf). */
async function activeAccount() {
  const store = accounts();
  const entry = store.list.find((a) => a.uuid === store.selected);
  if (!entry) throw new Error("Bitte zuerst ein Konto hinzufügen.");
  if (entry.type !== "microsoft") {
    if (!OFFLINE_ALLOWED) throw new Error("Bitte melde dich mit deinem Microsoft-Konto an.");
    return entry;
  }
  if (entry.expiresAt && entry.expiresAt > Date.now() + 5 * 60 * 1000) return { ...entry, accessToken: unprotect(entry.accessToken) };
  const clientId = settings().clientId;
  if (!clientId) throw new Error("Für Microsoft-Konten fehlt die Client-ID (Einstellungen).");
  emit("status", "Anmeldung wird erneuert …");
  const tokens = await auth.refreshMicrosoft(clientId, unprotect(entry.refreshToken));
  const account = await auth.minecraftLogin(tokens.access_token);
  await saveAccount(account, tokens.refresh_token || unprotect(entry.refreshToken));
  return account;
}

function gameModules() {
  const config = readJson(GAME_CONFIG, {});
  const enabled = config.enabled || {};
  return GAME_MODULES.map((m) => ({ ...m, enabled: enabled[m.id] ?? m.default }));
}

// ---------------------------------------------------------------------------------------------- Fenster

function emit(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1180,
    height: 720,
    minWidth: 960,
    minHeight: 600,
    frame: false,
    backgroundColor: "#0d0b14",
    title: "Mytic Client",
    icon: path.join(__dirname, "..", "build", "icon.png"),
    show: false,
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  win.removeMenu();
  win.loadFile(path.join(__dirname, "renderer", "index.html"));
  win.once("ready-to-show", () => win.show());
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
}

/** Öffnet das Microsoft-Anmeldefenster und liefert den Autorisierungscode. */
function microsoftCode(clientId) {
  return new Promise((resolve, reject) => {
    const { verifier, challenge } = auth.pkce();
    const state = crypto.randomBytes(12).toString("hex");
    const authSession = session.fromPartition(`msauth-${Date.now()}`);
    const popup = new BrowserWindow({
      width: 520,
      height: 680,
      parent: win,
      modal: true,
      title: "Mit Microsoft anmelden",
      backgroundColor: "#ffffff",
      webPreferences: { session: authSession, contextIsolation: true, nodeIntegration: false, sandbox: true },
    });
    popup.removeMenu();
    let finished = false;
    const check = (event, url) => {
      if (!url.startsWith(auth.REDIRECT_URI)) return;
      if (event && event.preventDefault) event.preventDefault();
      finished = true;
      const params = new URL(url).searchParams;
      popup.close();
      if (params.get("error")) reject(new Error(params.get("error_description") || params.get("error")));
      else if (params.get("state") !== state) reject(new Error("Ungültige Antwort von Microsoft"));
      else resolve({ code: params.get("code"), verifier });
    };
    popup.webContents.on("will-redirect", check);
    popup.webContents.on("will-navigate", check);
    popup.webContents.on("did-navigate", (e, url) => check(null, url));
    popup.on("closed", () => {
      if (!finished) reject(new Error("Anmeldung abgebrochen"));
    });
    popup.loadURL(auth.authorizeUrl(clientId, challenge, state));
  });
}

// ---------------------------------------------------------------------------------------------- IPC

const PACKAGE_VERSION = require("../package.json").version;

ipcMain.handle("state", () => ({
  version: PACKAGE_VERSION,
  minecraft: MC_VERSION,
  settings: settings(),
  accounts: publicAccounts(),
  modules: gameModules(),
  managedMods: MANAGED_MODS.map(({ slug, name, description, required, default: on, requires }) => ({ slug, name, description, required: Boolean(required), default: Boolean(on), requires: requires || [] })),
  totalMemoryMb: Math.floor(os.totalmem() / 1024 / 1024),
  running: Boolean(game),
  offlineAllowed: OFFLINE_ALLOWED,
  gameDir: DIRS.game,
}));

ipcMain.handle("settings:set", async (e, patch) => {
  const { clientId, ...current } = settings();
  await writeJson(SETTINGS_FILE, { ...current, ...patch });
  return settings();
});

ipcMain.handle("module:set", async (e, id, on) => {
  const config = readJson(GAME_CONFIG, {});
  config.enabled = { ...(config.enabled || {}), [id]: Boolean(on) };
  await writeJson(GAME_CONFIG, config);
  return gameModules();
});

ipcMain.handle("account:microsoft", async () => {
  const clientId = settings().clientId;
  if (!clientId) throw new Error("Für den Microsoft-Login fehlt die Client-ID (Einstellungen).");
  const { code, verifier } = await microsoftCode(clientId);
  const tokens = await auth.exchangeCode(clientId, code, verifier);
  const account = await auth.minecraftLogin(tokens.access_token);
  await saveAccount(account, tokens.refresh_token);
  return publicAccounts();
});

ipcMain.handle("account:offline", async (e, name) => {
  if (!OFFLINE_ALLOWED) throw new Error("Offline-Konten sind nur in der Entwicklerversion verfügbar.");
  await saveAccount(auth.offlineAccount(String(name || "").trim()), "");
  return publicAccounts();
});

ipcMain.handle("account:select", async (e, uuid) => {
  const store = accounts();
  if (store.list.some((a) => a.uuid === uuid)) store.selected = uuid;
  await writeJson(ACCOUNTS_FILE, store);
  return publicAccounts();
});

ipcMain.handle("account:remove", async (e, uuid) => {
  const store = accounts();
  store.list = store.list.filter((a) => a.uuid !== uuid);
  if (store.selected === uuid) store.selected = store.list[0] ? store.list[0].uuid : null;
  await writeJson(ACCOUNTS_FILE, store);
  return publicAccounts();
});

ipcMain.handle("game:launch", async () => {
  if (busy || game) throw new Error("Das Spiel läuft bereits.");
  busy = true;
  try {
    const current = settings();
    const account = await activeAccount();
    const installer = new Installer(DIRS, {
      log: (line) => emit("log", line),
      progress: (p) => emit("progress", p),
    });
    const install = await installer.install({ enabledMods: current.mods, bundledModsDir: BUNDLED_MODS, verify: current.verifyNext });
    if (current.verifyNext) {
      const { clientId, ...stored } = current;
      await writeJson(SETTINGS_FILE, { ...stored, verifyNext: false });
    }
    const command = buildCommand(install, DIRS, account, current);
    emit("status", "Minecraft startet …");
    emit("log", `[Launcher] Starte Minecraft ${MC_VERSION} (Fabric) als ${account.name}`);
    game = launch(command, DIRS, {
      onLog: (line) => emit("log", line),
      onExit: (code) => {
        game = null;
        emit("game", { running: false, code });
        if (win && !win.isDestroyed()) {
          if (win.isMinimized()) win.restore();
          win.show();
        }
      },
    });
    emit("game", { running: true });
    if (current.afterLaunch === "minimize") win.minimize();
    if (current.afterLaunch === "hide") win.hide();
    return true;
  } finally {
    busy = false;
  }
});

ipcMain.handle("game:stop", () => {
  if (game) game.kill();
  return true;
});

ipcMain.handle("open:gameDir", async () => {
  await fsp.mkdir(DIRS.game, { recursive: true });
  return shell.openPath(DIRS.game);
});

ipcMain.handle("open:url", (e, url) => {
  if (/^https:\/\//.test(url)) shell.openExternal(url);
});

ipcMain.on("window", (e, action) => {
  if (!win) return;
  if (action === "minimize") win.minimize();
  if (action === "maximize") (win.isMaximized() ? win.unmaximize() : win.maximize());
  if (action === "close") win.close();
});

app.whenReady().then(createWindow);
app.on("window-all-closed", () => {
  if (!game) app.quit();
});
app.on("activate", () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
