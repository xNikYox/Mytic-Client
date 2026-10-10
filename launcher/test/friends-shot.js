// Screenshot der Freunde-Seite (Friends-Server über MYTIC_FRIENDS_SERVER, Ordner über SHOT_DIR).
const { app, BrowserWindow } = require("electron");
const path = require("node:path");
const fs = require("node:fs");
const out = process.env.SHOT_DIR;
app.commandLine.appendSwitch("ozone-platform", "headless");
require("../src/main.js");
app.whenReady().then(async () => {
  await new Promise((r) => setTimeout(r, 3000));
  const win = BrowserWindow.getAllWindows()[0];
  const shot = async (name, js) => {
    if (js) await win.webContents.executeJavaScript(js);
    await new Promise((r) => setTimeout(r, 1500));
    fs.writeFileSync(path.join(out, `${name}.png`), (await win.webContents.capturePage()).toPNG());
  };
  await shot("friends-start");
  await shot("friends", "document.querySelector('[data-page=friends]').click()");
  await shot("friends-add", "document.getElementById('friend-name').value = 'Dinnerbone'; document.getElementById('friend-send').click()");
  await shot("friends-accept", "document.querySelector('#friends-incoming .friend-actions button').click()");
  console.log(await win.webContents.executeJavaScript("document.getElementById('friends-list').innerText"));
  app.exit(0);
});
