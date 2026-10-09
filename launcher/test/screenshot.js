// Rendert die Oberfläche ohne Bildschirm (Chromium "headless"-Plattform) und speichert Screenshots.
const { app, BrowserWindow } = require("electron");
const path = require("node:path");
const fs = require("node:fs");
const out = process.env.SHOT_DIR;
app.commandLine.appendSwitch("ozone-platform", "headless");
require("../src/main.js");
app.whenReady().then(async () => {
  await new Promise((r) => setTimeout(r, 2500));
  const win = BrowserWindow.getAllWindows()[0];
  const shot = async (name, js) => {
    if (js) await win.webContents.executeJavaScript(js);
    await new Promise((r) => setTimeout(r, 900));
    fs.writeFileSync(path.join(out, `${name}.png`), (await win.webContents.capturePage()).toPNG());
  };
  await shot("start");
  await shot("mods", "document.querySelector('[data-page=mods]').click()");
  await shot("settings", "document.querySelector('[data-page=settings]').click()");
  await shot("console", "document.querySelector('[data-page=console]').click()");
  await shot("browser", "document.querySelector('[data-page=browser]').click()");
  await new Promise((r) => setTimeout(r, 4000));
  await shot("browser");
  await shot("accounts", "document.querySelector('[data-page=home]').click(); document.getElementById('account-button').click()");
  app.exit(0);
});
