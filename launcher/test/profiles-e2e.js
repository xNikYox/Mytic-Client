// Profile in der echten Oberfläche: anlegen, Mod nur dort installieren, wechseln, Liste vergleichen.
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
  console.log("Start-Profil:", await js(`document.getElementById('profile-name').textContent`));
  await js(`document.getElementById('profile-button').click(); document.getElementById('profile-new').value='PvP'; document.getElementById('profile-form').requestSubmit()`);
  await wait(1200);
  console.log("Nach Erstellen aktiv:", await js(`document.getElementById('profile-name').textContent`), "| Profile:", await js(`[...document.querySelectorAll('#profile-list .prof b')].map(b=>b.textContent).join(', ')`));
  await shot("profiles");
  await js(`document.getElementById('profiles').hidden=true; document.querySelector('[data-page=browser]').click()`);
  await wait(3500);
  await js(`const i=document.getElementById('browser-search'); i.value='zoomify'; i.dispatchEvent(new Event('input'))`);
  await wait(3500);
  const first = await js(`document.querySelector('#browser-results .mr b').textContent`);
  await js(`document.querySelector('#browser-results .mr .mr-btn.install').click()`);
  for (let i = 0; i < 40; i++) { await wait(1000); const t = await js(`document.getElementById('toast').hidden ? '' : document.getElementById('toast').textContent`); if (t.includes('installiert')) { console.log("Meldung:", t); break; } }
  console.log("PvP – installiert:", await js(`document.getElementById('installed-count').textContent`), "(", first, ")");
  await js(`document.getElementById('profile-button').click()`);
  await wait(400);
  await js(`[...document.querySelectorAll('#profile-list .prof')].find(r=>r.querySelector('b').textContent==='Standard').click()`);
  await wait(1500);
  console.log("Standard – aktiv:", await js(`document.getElementById('profile-name').textContent`), "| installiert:", await js(`document.getElementById('installed-count').textContent`));
  await shot("profiles-browser");
  app.exit(0);
});
