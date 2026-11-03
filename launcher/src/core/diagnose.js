// Lag- und Absturz-Diagnose: wertet latest.log und die Live-Messung der Mod (logs/mytic-diagnose.json) aus und
// schlägt Lösungen vor – wo möglich mit einem Klick (Arbeitsspeicher, Sichtweite, Module, Mods).

/** Zahl auf 512 MB runden */
const round512 = (mb) => Math.max(1024, Math.round(mb / 512) * 512);

/** Fehlerzeilen nach Quelle zählen: "[12:00:00] [Render thread/ERROR] (Quelle) Text" */
function errorSources(log) {
  const counts = new Map();
  for (const line of log.split(/\r?\n/)) {
    const m = /\/(?:ERROR|FATAL)\]\s*(?:\(([^)]+)\))?/.exec(line);
    if (!m) continue;
    const source = (m[1] || "unbekannt").trim();
    counts.set(source, (counts.get(source) || 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1]);
}

/**
 * input: { log, live, memoryMb, totalMb, running, krypton, iris, fpsBoost, clips, userMods: [{ id, title, enabled }] }
 * Ergebnis: { summary, findings: [{ id, level: "error"|"warn"|"info", title, detail, fix?: { kind, label, ... } }] }
 */
function analyze(input) {
  const { log = "", live = null, memoryMb = 4096, totalMb = 8192, krypton = false, iris = false, fpsBoost = false, clips = false, userMods = [] } = input;
  const findings = [];
  const add = (f) => findings.push(f);
  const recommended = round512(Math.min(Math.max(memoryMb + 2048, 4096), totalMb * 0.6));

  // ---------------------------------------------------------------- Arbeitsspeicher
  const oom = /java\.lang\.OutOfMemoryError/.test(log);
  const heapFull = live && live.heapMaxPct >= 90 && (live.gcBusySeconds || 0) >= 5;
  if ((oom || heapFull) && recommended > memoryMb) {
    add({ id: "memory-low", level: oom ? "error" : "warn", title: "Zu wenig Arbeitsspeicher für Minecraft",
      detail: oom ? "Minecraft ist der Speicher ausgegangen (OutOfMemoryError)." : `Der Speicher war bis zu ${live.heapMaxPct} % voll, die Speicherbereinigung hat das Spiel immer wieder angehalten.`,
      fix: { kind: "memory", memoryMb: recommended, label: `Auf ${(recommended / 1024).toFixed(1).replace(".0", "")} GB erhöhen` } });
  } else if (memoryMb > totalMb * 0.7) {
    const lower = round512(totalMb * 0.5);
    add({ id: "memory-high", level: "warn", title: "Zu viel Arbeitsspeicher zugewiesen",
      detail: `Minecraft bekommt ${(memoryMb / 1024).toFixed(1)} von ${(totalMb / 1024).toFixed(1)} GB – für Windows und andere Programme bleibt zu wenig, der PC lagert aus und ruckelt.`,
      fix: { kind: "memory", memoryMb: lower, label: `Auf ${(lower / 1024).toFixed(1).replace(".0", "")} GB senken` } });
  } else if (memoryMb < 3072 && userMods.length > 15 && recommended > memoryMb) {
    add({ id: "memory-mods", level: "info", title: "Viele Mods, wenig Arbeitsspeicher",
      detail: `${userMods.length} eigene Mods mit ${memoryMb} MB – mehr Speicher verhindert Ruckler durch die Speicherbereinigung.`,
      fix: { kind: "memory", memoryMb: recommended, label: `Auf ${(recommended / 1024).toFixed(1).replace(".0", "")} GB erhöhen` } });
  }

  if (live) {
    // ---------------------------------------------------------------- Speicherbereinigung
    if (!heapFull && (live.gcMaxPerSecondMs || 0) >= 200) {
      add({ id: "gc-pauses", level: "warn", title: "Lange Pausen durch die Speicherbereinigung",
        detail: `Bis zu ${live.gcMaxPerSecondMs} ms pro Sekunde stand das Spiel still. Das fühlt sich wie Lag an, obwohl Internet und Server gut sind.`,
        fix: recommended > memoryMb ? { kind: "memory", memoryMb: recommended, label: `Speicher auf ${(recommended / 1024).toFixed(1).replace(".0", "")} GB` } : undefined });
    }

    // ---------------------------------------------------------------- FPS und Ruckler
    const hours = Math.max(live.playSeconds / 3600, 1 / 12);
    if (live.playSeconds >= 60 && live.fpsAvg > 0 && live.fpsAvg < 40) {
      const rd = live.renderDistance || 0;
      add({ id: "fps-low", level: "warn", title: `Niedrige FPS (im Schnitt ${live.fpsAvg})`,
        detail: rd > 10 ? `Die Sichtweite steht auf ${rd} Chunks – das kostet am meisten.` : iris ? "Shader (Iris) kosten viel Leistung." : "FPS-Boost dreht teure Grafik herunter.",
        fix: rd > 10 ? { kind: "renderDistance", value: 10, label: "Sichtweite auf 10" } : iris ? { kind: "perfMod", slug: "iris", on: false, label: "Shader ausschalten" }
          : !fpsBoost ? { kind: "module", id: "fpsboost", on: true, label: "FPS-Boost einschalten" } : undefined });
    }
    if (live.playSeconds >= 60 && (live.hitches / hours > 30 || (live.fpsAvg >= 60 && live.fps1Low > 0 && live.fps1Low < live.fpsAvg / 3))) {
      const cause = (live.gcMaxPerSecondMs || 0) >= 100 ? "Wahrscheinlich die Speicherbereinigung (siehe oben)." : clips ? "Clips nehmen im Hintergrund auf – das kann ruckeln." : "Häufig: Chunks laden, viele Entities oder einzelne Mods.";
      add({ id: "stutter", level: "warn", title: "Ruckler im Spiel",
        detail: `${live.hitches} Hänger über 250 ms, längster ${live.maxFrameMs} ms. ${cause}`,
        fix: clips && (live.gcMaxPerSecondMs || 0) < 100 ? { kind: "module", id: "clips", on: false, label: "Clips ausschalten" } : undefined });
    }

    // ---------------------------------------------------------------- Ping
    if ((live.pingAvg || 0) >= 150 || (live.playSeconds > 60 && live.highPingSeconds > live.playSeconds * 0.25)) {
      add({ id: "ping", level: "warn", title: `Hoher Ping (im Schnitt ${live.pingAvg} ms, bis ${live.pingMax} ms)`,
        detail: krypton ? "Krypton ist eingeschaltet – die Mod ist für Server gedacht und kann auf dem Client den Ping erhöhen." : `Server: ${live.server || "unbekannt"}. Meist liegt es am Internet (WLAN, Downloads nebenher) oder am Server-Standort.`,
        fix: krypton ? { kind: "perfMod", slug: "krypton", on: false, label: "Krypton ausschalten" } : undefined });
    }
  } else if (krypton) {
    add({ id: "krypton", level: "info", title: "Krypton ist eingeschaltet",
      detail: "Die Netzwerk-Mod ist für Server gedacht und kann auf dem Client den Ping erhöhen.",
      fix: { kind: "perfMod", slug: "krypton", on: false, label: "Krypton ausschalten" } });
  }

  // ---------------------------------------------------------------- aus dem Log
  if (/libx264/.test(log) && clips) {
    add({ id: "clips-cpu", level: "info", title: "Clips werden mit dem Prozessor kodiert",
      detail: "Es wurde keine Grafikkarten-Kodierung gefunden – das belastet die CPU und kann den Ping und die FPS drücken.",
      fix: { kind: "module", id: "clips", on: false, label: "Clips ausschalten" } });
  }
  const behind = [...log.matchAll(/Can't keep up!.*?Running (\d+)ms or (\d+) ticks behind/g)];
  if (behind.length >= 3) {
    add({ id: "server-behind", level: "info", title: "Einzelspieler-Welt kommt nicht hinterher",
      detail: `${behind.length}× „Can't keep up“ – die Welt selbst ist zu aufwendig (viele Mobs, Redstone, Farmen). Simulationsdistanz senken hilft.` });
  }
  const disconnects = (log.match(/Timed out|Connection reset|Disconnected from server|ReadTimeoutException/g) || []).length;
  if (disconnects >= 2) {
    add({ id: "network", level: "warn", title: "Verbindungsabbrüche",
      detail: `${disconnects} Abbrüche im letzten Spiel – meist WLAN, Router oder Internet. Ein LAN-Kabel hilft am zuverlässigsten.` });
  }
  if (/Mixin apply failed|MixinApplyError|Mixin transformation of .* failed/.test(log)) {
    add({ id: "mixin", level: "error", title: "Eine Mod passt nicht zu dieser Minecraft-Version",
      detail: "Beim Laden ist eine Mod gescheitert (Mixin-Fehler). Im Absturz-Fenster bietet der Auto-Fix eine Lösung an." });
  }
  const sources = errorSources(log).filter(([, n]) => n >= 20);
  for (const [source, n] of sources.slice(0, 3)) {
    const mod = userMods.find((m) => m.enabled && (source.toLowerCase().includes(String(m.title).toLowerCase()) || source.toLowerCase().includes(String(m.slug || m.id).toLowerCase())));
    add({ id: `errors-${source}`, level: "info", title: `${n} Fehlermeldungen von „${source}“`,
      detail: mod ? `Das ist die Mod „${mod.title}“. Viele Fehler pro Sitzung kosten Leistung.` : "Viele Fehlermeldungen pro Sitzung kosten Leistung – ist es eine eigene Mod, testweise ausschalten.",
      fix: mod ? { kind: "userMod", id: mod.id, on: false, label: `„${mod.title}“ ausschalten` } : undefined });
  }

  const order = { error: 0, warn: 1, info: 2 };
  findings.sort((a, b) => order[a.level] - order[b.level]);
  return {
    summary: live ? { playMinutes: Math.round(live.playSeconds / 60), fpsAvg: live.fpsAvg, fps1Low: live.fps1Low, pingAvg: live.pingAvg, hitches: live.hitches, heapMaxPct: live.heapMaxPct, gcMaxMs: live.gcMaxPerSecondMs, updatedAt: live.updatedAt } : null,
    findings,
  };
}

/** options.txt: Sichtweite setzen (nur bei geschlossenem Spiel) */
function setRenderDistance(text, value) {
  const line = `renderDistance:${Math.max(2, Math.min(32, Number(value) || 10))}`;
  return /^renderDistance:\d+/m.test(text) ? text.replace(/^renderDistance:\d+/m, line) : `${text.replace(/\s*$/, "")}\n${line}\n`;
}

module.exports = { analyze, errorSources, setRenderDistance };
