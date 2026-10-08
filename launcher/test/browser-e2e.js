// Mod-Browser in der echten Oberfläche: Seite öffnen, suchen, installieren, "Installiert" ansehen.
const { app, BrowserWindow } = require("electron");
const fs = require("node:fs");
const path = require("node:path");
const out = process.env.SHOT_DIR;
app.commandLine.appendSwitch("ozone-platform", "headless");
require("../src/main.js");
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
app.whenReady().then(async () => {
  await wait(3000);
  const win = BrowserWindow.getAllWindows()[0];
  const js = (code) => win.webContents.executeJavaScript(code);
  const shot = async (name) => fs.writeFileSync(path.join(out, `${name}.png`), (await win.capturePage()).toPNG());
  await js(`document.querySelector('[data-page=browser]').click()`);
  await wait(4000);
  console.log("Karten:", await js(`document.querySelectorAll('#browser-results .mr').length`), "|", await js(`document.getElementById('browser-info').textContent`));
  await shot("browser");
  await js(`const i=document.getElementById('browser-search'); i.value='appleskin'; i.dispatchEvent(new Event('input'))`);
  await wait(3500);
  console.log("Erster Treffer:", await js(`document.querySelector('#browser-results .mr b').textContent`));
  await js(`document.querySelector('#browser-results .mr .mr-btn.install').click()`);
  for (let i = 0; i < 30; i++) {
    await wait(1000);
    const t = await js(`document.getElementById('toast').hidden ? '' : document.getElementById('toast').textContent`);
    if (t) { console.log("Meldung:", t); break; }
  }
  await shot("browser-installed-card");
  await js(`document.querySelector('[data-tab=installed]').click()`);
  await wait(800);
  console.log("Installiert-Tab:", await js(`[...document.querySelectorAll('#browser-results .mr b')].map(b=>b.textContent).join(', ')`), "| Zähler:", await js(`document.getElementById('installed-count').textContent`));
  await shot("browser-installed");
  app.exit(0);
});
