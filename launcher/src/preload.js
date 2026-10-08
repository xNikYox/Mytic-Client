// Sichere Brücke zwischen Oberfläche und Hauptprozess.
const { contextBridge, ipcRenderer } = require("electron");

const on = (channel) => (callback) => {
  const listener = (event, payload) => callback(payload);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
};

contextBridge.exposeInMainWorld("mytic", {
  state: () => ipcRenderer.invoke("state"),
  setSettings: (patch) => ipcRenderer.invoke("settings:set", patch),
  setModule: (id, on) => ipcRenderer.invoke("module:set", id, on),
  loginMicrosoft: () => ipcRenderer.invoke("account:microsoft"),
  addOffline: (name) => ipcRenderer.invoke("account:offline", name),
  selectAccount: (uuid) => ipcRenderer.invoke("account:select", uuid),
  removeAccount: (uuid) => ipcRenderer.invoke("account:remove", uuid),
  launch: () => ipcRenderer.invoke("game:launch"),
  stop: () => ipcRenderer.invoke("game:stop"),
  openGameDir: () => ipcRenderer.invoke("open:gameDir"),
  openUrl: (url) => ipcRenderer.invoke("open:url", url),
  window: (action) => ipcRenderer.send("window", action),
  installUpdate: () => ipcRenderer.invoke("update:install"),
  searchMods: (options) => ipcRenderer.invoke("browser:search", options),
  listMods: () => ipcRenderer.invoke("browser:list"),
  installMod: (id) => ipcRenderer.invoke("browser:install", id),
  removeMod: (id) => ipcRenderer.invoke("browser:remove", id),
  toggleMod: (id, on) => ipcRenderer.invoke("browser:toggle", id, on),
  onBrowserStep: on("browser-step"),
  onUpdate: on("update"),
  onUpdateProgress: on("update-progress"),
  onUpdated: on("updated"),
  onProgress: on("progress"),
  onLog: on("log"),
  onStatus: on("status"),
  onGame: on("game"),
});
