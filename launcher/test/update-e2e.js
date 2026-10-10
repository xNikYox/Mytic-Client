// Update-Ablauf wie bei Lunar in der echten Oberfläche: neue Version wird still im Hintergrund geladen,
// dann erscheint "Update bereit", ein Klick spielt sie ein (portable: neue EXE neben die alte, dann Neustart).
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
  let title = "";
  for (let i = 0; i < 180 && !/bereit/.test(title); i++) {
    await wait(1000);
    title = await js(`document.getElementById('update-banner').hidden ? '' : document.getElementById('update-title').textContent`);
  }
  console.log("Hinweis:", title, "|", await js(`document.getElementById('update-sub').textContent`));
  fs.writeFileSync(path.join(out, "update-banner.png"), (await win.capturePage()).toPNG());
  const readyFile = path.join(process.env.MYTIC_HOME, "updates", "ready.json");
  console.log("Bereit:", fs.existsSync(readyFile) ? JSON.parse(fs.readFileSync(readyFile, "utf8")).version : "nein");
  await js(`document.getElementById('update-install').click()`);
  await wait(3000);
  console.log("Meldung:", await js(`document.getElementById('toast').hidden ? '' : document.getElementById('toast').textContent`));
  console.log("Dateien im Ordner:", fs.readdirSync(path.dirname(process.env.PORTABLE_EXECUTABLE_FILE)).join(", "));
  app.exit(0);
});
