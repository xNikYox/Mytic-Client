// Diagnose: typische Fälle aus Log und Live-Messung → Befunde mit passender Ein-Klick-Lösung.
const test = require("node:test");
const assert = require("node:assert/strict");
const { analyze, errorSources, setRenderDistance } = require("../src/core/diagnose");

const live = (o) => ({ playSeconds: 1800, fpsAvg: 120, fps1Low: 80, hitches: 2, maxFrameMs: 300, gcMaxPerSecondMs: 20, gcBusySeconds: 0, heapMaxPct: 60, pingAvg: 40, pingMax: 80, highPingSeconds: 0, renderDistance: 8, server: "play.example.net", ...o });
const ids = (r) => r.findings.map((f) => f.id);

test("alles gut → keine Befunde", () => {
  assert.deepEqual(ids(analyze({ live: live(), memoryMb: 4096, totalMb: 16384 })), []);
});

test("Speicher voll / OutOfMemory → Speicher erhöhen (höchstens 60 % RAM)", () => {
  const r = analyze({ live: live({ heapMaxPct: 97, gcBusySeconds: 12 }), memoryMb: 2048, totalMb: 8192 });
  const f = r.findings.find((x) => x.id === "memory-low");
  assert.equal(f.fix.kind, "memory");
  assert.equal(f.fix.memoryMb, 4096); // min(max(2048+2048, 4096), 8192*0.6=4915) → 4096
  assert.equal(analyze({ log: "java.lang.OutOfMemoryError: Java heap space", memoryMb: 2048, totalMb: 16384 }).findings[0].level, "error");
});

test("zu viel RAM zugewiesen → senken", () => {
  const f = analyze({ live: live(), memoryMb: 12288, totalMb: 16384 }).findings.find((x) => x.id === "memory-high");
  assert.equal(f.fix.memoryMb, 8192);
});

test("hoher Ping mit Krypton → Krypton aus; ohne Krypton nur Hinweis", () => {
  const withK = analyze({ live: live({ pingAvg: 220, pingMax: 400 }), memoryMb: 4096, totalMb: 16384, krypton: true }).findings.find((x) => x.id === "ping");
  assert.deepEqual(withK.fix, { kind: "perfMod", slug: "krypton", on: false, label: "Krypton ausschalten" });
  const without = analyze({ live: live({ pingAvg: 220 }), memoryMb: 4096, totalMb: 16384 }).findings.find((x) => x.id === "ping");
  assert.equal(without.fix, undefined);
});

test("niedrige FPS: hohe Sichtweite → Sichtweite 10, sonst FPS-Boost", () => {
  assert.equal(analyze({ live: live({ fpsAvg: 25, renderDistance: 24 }), memoryMb: 4096, totalMb: 16384 }).findings.find((x) => x.id === "fps-low").fix.kind, "renderDistance");
  assert.deepEqual(analyze({ live: live({ fpsAvg: 25 }), memoryMb: 4096, totalMb: 16384 }).findings.find((x) => x.id === "fps-low").fix, { kind: "module", id: "fpsboost", on: true, label: "FPS-Boost einschalten" });
});

test("Ruckler und GC-Pausen werden erkannt", () => {
  const r = analyze({ live: live({ hitches: 90, gcMaxPerSecondMs: 400 }), memoryMb: 4096, totalMb: 16384 });
  assert.ok(ids(r).includes("gc-pauses") && ids(r).includes("stutter"));
});

test("Log: Verbindungsabbrüche, Can't keep up, Fehler einer eigenen Mod → ausschalten", () => {
  const lines = [];
  for (let i = 0; i < 25; i++) lines.push("[12:00:00] [Render thread/ERROR] (Fancy Menu) Something broke");
  lines.push("Timed out", "Connection reset", ...Array(3).fill("[Server thread/WARN]: Can't keep up! Is the server overloaded? Running 2500ms or 50 ticks behind"));
  const r = analyze({ log: lines.join("\n"), memoryMb: 4096, totalMb: 16384, userMods: [{ id: "abc", slug: "fancymenu", title: "Fancy Menu", enabled: true }] });
  assert.ok(ids(r).includes("network") && ids(r).includes("server-behind"));
  assert.deepEqual(r.findings.find((f) => f.id === "errors-Fancy Menu").fix, { kind: "userMod", id: "abc", on: false, label: "„Fancy Menu“ ausschalten" });
  assert.deepEqual(errorSources("[x] [Render thread/ERROR] (A) a\n[x] [main/FATAL] (B) b\n[x] [main/ERROR] (A) c")[0], ["A", 2]);
});

test("Sortierung: Fehler vor Warnungen vor Hinweisen", () => {
  const r = analyze({ log: "java.lang.OutOfMemoryError\nTimed out\nTimed out", live: live({ pingAvg: 300 }), memoryMb: 2048, totalMb: 16384, krypton: true });
  const levels = r.findings.map((f) => f.level);
  assert.deepEqual(levels, [...levels].sort((a, b) => ["error", "warn", "info"].indexOf(a) - ["error", "warn", "info"].indexOf(b)));
});

test("options.txt: Sichtweite ersetzen oder anhängen", () => {
  assert.equal(setRenderDistance("fov:0\nrenderDistance:24\nlang:de_de\n", 10), "fov:0\nrenderDistance:10\nlang:de_de\n");
  assert.equal(setRenderDistance("fov:0\n", 10), "fov:0\nrenderDistance:10\n");
});
