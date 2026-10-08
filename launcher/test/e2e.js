// Ende-zu-Ende in der echten Oberfläche: Offline-Konto anlegen, SPIELEN klicken, Fortschritt und Spielstart beobachten.
const { app, BrowserWindow } = require("electron");
const path = require("node:path");
const fs = require("node:fs");
const out = process.env.SHOT_DIR;
app.commandLine.appendSwitch("ozone-platform", "headless");
require("../src/main.js");
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
app.whenReady().then(async () => {
  await wait(2000);
  const win = BrowserWindow.getAllWindows()[0];
  const js = (code) => win.webContents.executeJavaScript(code);
  const shot = async (name) => fs.writeFileSync(path.join(out, `${name}.png`), (await win.capturePage()).toPNG());
  await shot("start");
  await js(`document.getElementById('account-button').click(); document.getElementById('offline-name').value='MyticTester'; document.getElementById('offline-form').requestSubmit();`);
  await wait(800);
  console.log("Konto:", await js(`document.getElementById('account-name').textContent + ' / ' + document.getElementById('account-type').textContent`));
  await js(`document.getElementById('launch').click()`);
  const seen = new Set();
  for (let i = 0; i < 240; i++) {
    await wait(500);
    const status = await js(`document.getElementById('status').textContent`);
    if (!seen.has(status.replace(/\d+ \/ \d+/, ""))) { seen.add(status.replace(/\d+ \/ \d+/, "")); console.log("Status:", status); }
    if (i === 6) await shot("launching");
    if (/beendet|fehlgeschlagen|läuft/.test(status) && i > 2) { if (/beendet|fehlgeschlagen/.test(status)) break; }
  }
  await wait(500);
  await js(`document.querySelector('[data-page=console]').click()`);
  await wait(400);
  await shot("console");
  const log = await js(`document.getElementById('log').innerText`);
  console.log("Log-Zeilen:", log.split("\n").length, "| Mytic geladen:", /Mytic Client [0-9.]+ geladen/.test(log), "| GLFW (kein Bildschirm):", /Failed to initialize GLFW/.test(log));
  app.exit(0);
});
