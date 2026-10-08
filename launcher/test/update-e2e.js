// Update-Ablauf in der echten Oberfläche: Banner erscheint, Klick lädt die neue EXE neben die "alte".
const { app, BrowserWindow } = require("electron");
const fs = require("node:fs");
const path = require("node:path");
const out = process.env.SHOT_DIR;
app.commandLine.appendSwitch("ozone-platform", "headless");
require("../src/main.js");
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
app.whenReady().then(async () => {
  await wait(4000);
  const win = BrowserWindow.getAllWindows()[0];
  const js = (code) => win.webContents.executeJavaScript(code);
  console.log("Banner sichtbar:", !(await js(`document.getElementById('update-banner').hidden`)), "|", await js(`document.getElementById('update-title').textContent`));
  fs.writeFileSync(path.join(out, "update-banner.png"), (await win.capturePage()).toPNG());
  await js(`document.getElementById('update-install').click()`);
  for (let i = 0; i < 120; i++) {
    await wait(1000);
    const sub = await js(`document.getElementById('update-sub').textContent`);
    if (i === 3) fs.writeFileSync(path.join(out, "update-progress.png"), (await win.capturePage()).toPNG());
    const toast = await js(`document.getElementById('toast').hidden ? '' : document.getElementById('toast').textContent`);
    if (toast) { console.log("Meldung:", toast.slice(0, 160)); break; }
    if (/startet/.test(sub)) { console.log("Status:", sub); break; }
  }
  console.log("Dateien im Ordner:", fs.readdirSync(path.dirname(process.env.PORTABLE_EXECUTABLE_FILE)).join(", "));
  app.exit(0);
});
