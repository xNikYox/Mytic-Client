// Echter Test: installiert alles in einen Testordner und startet das Spiel (ohne Bildschirm bricht es beim Fenster ab).
const path = require("node:path");
const { dirs } = require("../src/core/paths");
const { Installer, buildCommand, launch } = require("../src/core/minecraft");
const { offlineAccount } = require("../src/core/auth");

(async () => {
  const d = dirs(process.argv[2]);
  let last = "";
  const version = (process.argv.find((a) => a.startsWith("--version=")) || "").slice(10) || undefined;
  const installer = new Installer(d, {
    version,
    log: (l) => console.log(l),
    progress: ({ label, done, total }) => {
      const line = `${label}${total ? ` ${done}/${total}` : ""}`;
      if (label !== last || done === total) console.log(line);
      last = label;
    },
  });
  const t = Date.now();
  const install = await installer.install({ bundledModsDir: path.join(__dirname, "..", "resources", "mods"), skipAssets: process.argv.includes("--no-assets") });
  console.log(`Installiert in ${((Date.now() - t) / 1000).toFixed(0)} s – Mods: ${install.mods.join(", ")}`);
  const command = buildCommand(install, d, offlineAccount("MyticTester"), { memoryMb: 2048 });
  console.log("Java:", command.java);
  console.log("Main:", command.args[command.args.indexOf("-cp") + 2]);
  launch(command, d, { onLog: (l) => console.log("[Spiel]", l), onExit: (c) => console.log("Spiel beendet, Code", c) });
})().catch((e) => { console.error("FEHLER", e); process.exit(1); });
