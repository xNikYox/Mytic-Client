// Oberfläche des Mytic Client Launchers.
const api = window.mytic;
const $ = (id) => document.getElementById(id);
let state = null;
let running = false;
let modFilter = "Alle";
let logLevel = "all";
const logLines = [];

const avatar = (uuid) => `https://mc-heads.net/avatar/${uuid}/64`;
const PAGE_TITLES = { home: "Start", mods: "Mods", browser: "Mod-Browser", settings: "Einstellungen", console: "Konsole" };

// Einfache Linien-Icons pro Kategorie
const ICONS = {
  HUD: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M7 10h4M7 14h7"/></svg>',
  Mechanik: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1"/></svg>',
  Visuell: '<svg viewBox="0 0 24 24"><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/></svg>',
  Client: '<svg viewBox="0 0 24 24"><path d="M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6 6.6 19.5l1.2-6L3.3 9.3l6.1-.7z"/></svg>',
  Performance: '<svg viewBox="0 0 24 24"><path d="M13 3L5 14h6l-1 7 8-11h-6z"/></svg>',
};

// Eigene Icons pro Mod (Linien, 24×24)
const MOD_ICONS = {
  fps: '<path d="M4 15a8 8 0 1 1 16 0"/><path d="M12 15l4-5"/>',
  cps: '<rect x="7" y="3" width="10" height="18" rx="5"/><path d="M12 3v6"/>',
  ping: '<path d="M5 19v-3M10 19v-7M15 19v-10M20 19V5"/>',
  keystrokes: '<rect x="3" y="7" width="18" height="11" rx="2"/><path d="M7 11h1M11 11h1M15 11h1M8 15h8"/>',
  armor: '<path d="M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z"/>',
  potions: '<path d="M9 3h6M10 3v5l-5 9a3 3 0 0 0 2.6 4.5h8.8A3 3 0 0 0 19 17l-5-9V3"/><path d="M7.5 14h9"/>',
  coords: '<path d="M12 21s-6-6-6-11a6 6 0 0 1 12 0c0 5-6 11-6 11z"/><circle cx="12" cy="10" r="2"/>',
  direction: '<circle cx="12" cy="12" r="9"/><path d="M15 9l-2 5-5 2 2-5z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  speed: '<path d="M3 8h11a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h7"/>',
  memory: '<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4"/>',
  server: '<rect x="4" y="4" width="16" height="7" rx="2"/><rect x="4" y="13" width="16" height="7" rx="2"/><path d="M8 7.5h.01M8 16.5h.01"/>',
  biome: '<path d="M12 3l6 9h-4l4 6H6l4-6H6z"/><path d="M12 18v3"/>',
  daytime: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  combo: '<path d="M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-3 2-4 2-7 2 1 3 2 3 4"/>',
  reach: '<path d="M3 17l14-14 4 4L7 21z"/><path d="M8 12l2 2M11 9l2 2M14 6l2 2"/>',
  session: '<path d="M10 2h4M12 14l3-3"/><circle cx="12" cy="14" r="8"/>',
  items: '<path d="M3 7l9-4 9 4-9 4z"/><path d="M3 7v10l9 4 9-4V7M12 11v10"/>',
  targethud: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>',
  watermark: '<path d="M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6 6.6 19.5l1.2-6L3.3 9.3l6.1-.7z"/>',
  zoom: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5L21 21M10.5 7.5v6M7.5 10.5h6"/>',
  freelook: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
  togglesprint: '<path d="M13 4a2 2 0 1 0 0-.01M10 9l3-1 3 3 3 1M10 9l-2 5 4 2-1 5M8 14l-4 1"/>',
  togglesneak: '<path d="M6 9l6 6 6-6M6 4l6 6 6-6"/>',
  crosshair: '<circle cx="12" cy="12" r="7"/><path d="M12 2v6M12 16v6M2 12h6M16 12h6"/>',
  fullbright: '<path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.8.8 1 1.5 1 2.5h6c0-1 .2-1.7 1-2.5A6 6 0 0 0 12 3z"/>',
  timechanger: '<path d="M20 14.5A8 8 0 1 1 9.5 4 6.5 6.5 0 0 0 20 14.5z"/>',
  clearweather: '<path d="M7 18h10a4 4 0 0 0 0-8 6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7 18z"/>',
  nohurtcam: '<path d="M3 8h3l2-3h8l2 3h3v11H3z"/><circle cx="12" cy="13" r="3.5"/>',
  scoreboard: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/>',
  bossbar: '<rect x="3" y="9" width="18" height="6" rx="3"/><path d="M6 12h8"/>',
  theme: '<path d="M12 3a9 9 0 1 0 0 18c1.2 0 2-.8 2-2 0-.6-.3-1-.6-1.4-.3-.4-.5-.8-.5-1.3 0-1 .8-1.8 1.8-1.8H17a4 4 0 0 0 4-4c0-4-4-7.5-9-7.5z"/><circle cx="7.5" cy="11" r="1"/><circle cx="10" cy="7" r="1"/><circle cx="15" cy="7.5" r="1"/>',
};

function toast(message, isError = false) {
  const el = $("toast");
  el.textContent = message;
  el.classList.toggle("error", isError);
  el.hidden = false;
  el.style.animation = "none";
  void el.offsetWidth;
  el.style.animation = "";
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (el.hidden = true), isError ? 7000 : 3500);
}

function errorText(error) {
  return String(error && error.message ? error.message : error).replace(/^Error invoking remote method '[^']+': (Error: )?/, "");
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

// ---------------------------------------------------------------------------------------------- Hintergrund

(function stars() {
  const canvas = $("stars");
  const ctx = canvas.getContext("2d");
  let points = [];
  const resize = () => {
    canvas.width = innerWidth * devicePixelRatio;
    canvas.height = innerHeight * devicePixelRatio;
    points = Array.from({ length: 90 }, () => ({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      r: (Math.random() * 1.3 + 0.3) * devicePixelRatio,
      s: Math.random() * 0.15 + 0.03,
      p: Math.random() * Math.PI * 2,
      // Neon-Partikel: Cyan, Violett, Pink, ab und zu weiß
      c: ["34, 229, 255", "162, 89, 255", "255, 43, 214", "235, 225, 255"][Math.floor(Math.random() * 4)],
    }));
  };
  const draw = (t) => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (const p of points) {
      p.y -= p.s * devicePixelRatio;
      if (p.y < -4) p.y = canvas.height + 4;
      const a = 0.25 + 0.45 * (0.5 + 0.5 * Math.sin(t / 900 + p.p));
      ctx.fillStyle = `rgba(${p.c}, ${a})`;
      ctx.shadowColor = `rgba(${p.c}, ${a})`;
      ctx.shadowBlur = 6 * devicePixelRatio;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fill();
    }
    requestAnimationFrame(draw);
  };
  addEventListener("resize", resize);
  resize();
  requestAnimationFrame(draw);
})();

// Parallax der Startseiten-Illustration
$("page-home").addEventListener("mousemove", (e) => {
  const rect = $("hero-scene").getBoundingClientRect();
  const dx = (e.clientX - rect.left) / rect.width - 0.5;
  const dy = (e.clientY - rect.top) / rect.height - 0.5;
  document.querySelectorAll(".layer").forEach((layer) => {
    const depth = Number(layer.dataset.depth);
    layer.style.transform = `translate(${-dx * depth}px, ${-dy * depth * 0.6}px)`;
  });
});

// ---------------------------------------------------------------------------------------------- Navigation

function showPage(name) {
  document.querySelectorAll(".rail-item").forEach((b) => b.classList.toggle("active", b.dataset.page === name));
  document.querySelectorAll(".page").forEach((p) => p.classList.toggle("active", p.id === `page-${name}`));
  $("page-title").textContent = PAGE_TITLES[name];
  if (name === "console") renderLog(true);
  if (name === "browser" && !browserLoaded) loadBrowser(true);
}
document.querySelectorAll(".rail-item").forEach((b) => b.addEventListener("click", () => showPage(b.dataset.page)));
document.querySelectorAll("[data-goto]").forEach((b) => b.addEventListener("click", () => showPage(b.dataset.goto)));
document.querySelectorAll("[data-window]").forEach((b) => b.addEventListener("click", () => api.window(b.dataset.window)));

// ---------------------------------------------------------------------------------------------- Konten

function selectedAccount() {
  return state.accounts.list.find((a) => a.uuid === state.accounts.selected) || null;
}

function renderAccounts() {
  const account = selectedAccount();
  $("account-name").textContent = account ? account.name : "Anmelden";
  $("account-type").textContent = account ? (account.type === "microsoft" ? "Microsoft-Konto" : "Offline-Konto") : "Kein Konto";
  $("launch-name").textContent = account ? account.name : "kein Konto";
  $("account-avatar").hidden = !account;
  $("account-fallback").textContent = account ? account.name[0].toUpperCase() : "?";
  if (account) $("account-avatar").src = avatar(account.uuid);

  const list = $("account-list");
  list.replaceChildren();
  if (state.accounts.list.length === 0) list.append(el("div", "empty", "Noch kein Konto hinzugefügt."));
  for (const a of state.accounts.list) {
    const row = el("div", `acc${a.uuid === state.accounts.selected ? " selected" : ""}`);
    const img = el("img");
    img.src = avatar(a.uuid);
    img.alt = "";
    const who = el("div", "who");
    who.append(el("b", null, a.name), el("small", null, a.type === "microsoft" ? "Microsoft" : "Offline"));
    row.append(img, who);
    if (a.uuid !== state.accounts.selected) {
      const use = el("button", "use", "Verwenden");
      use.addEventListener("click", async () => {
        state.accounts = await api.selectAccount(a.uuid);
        renderAccounts();
      });
      row.append(use);
    }
    const remove = el("button", null, "Entfernen");
    remove.addEventListener("click", async () => {
      state.accounts = await api.removeAccount(a.uuid);
      renderAccounts();
    });
    row.append(remove);
    list.append(row);
  }
}

function accountError(message) {
  $("account-error").textContent = message || "";
  $("account-error").hidden = !message;
}

function openAccounts() {
  accountError("");
  $("profiles").hidden = true;
  $("accounts").hidden = false;
}

$("account-button").addEventListener("click", () => ($("accounts").hidden ? openAccounts() : ($("accounts").hidden = true)));
$("accounts").addEventListener("click", (e) => {
  if (e.target === $("accounts")) $("accounts").hidden = true;
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") $("accounts").hidden = true;
});

$("login-microsoft").addEventListener("click", async () => {
  accountError("");
  $("login-microsoft").disabled = true;
  try {
    state.accounts = await api.loginMicrosoft();
    renderAccounts();
    toast(`Angemeldet als ${selectedAccount().name}`);
    $("accounts").hidden = true;
  } catch (error) {
    accountError(errorText(error));
  } finally {
    $("login-microsoft").disabled = false;
  }
});

$("offline-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  accountError("");
  try {
    state.accounts = await api.addOffline($("offline-name").value);
    $("offline-name").value = "";
    renderAccounts();
    $("accounts").hidden = true;
  } catch (error) {
    accountError(errorText(error));
  }
});

// ---------------------------------------------------------------------------------------------- Mods

function switchControl(checked, disabled, label, onChange) {
  const wrap = el("label", "switch");
  const input = el("input");
  input.type = "checkbox";
  input.checked = checked;
  input.disabled = disabled;
  input.setAttribute("aria-label", label);
  input.addEventListener("change", () => onChange(input.checked));
  wrap.append(input, el("span"));
  return wrap;
}

let profileState = { active: null, list: [] };
let versions = [];
const activeProfile = () => profileState.list.find((p) => p.id === profileState.active) || { perf: {}, name: "Standard", color: "#9b5cff" };

const loaderOf = (version) => ((state.forgeVersions || []).includes(version) ? "forge" : "fabric");
const loaderName = (version) => (loaderOf(version) === "forge" ? "Forge" : "Fabric");
/** Performance-Mods, die zum Loader des aktiven Profils passen (Fabric oder Forge bei 1.8.9). */
const profileManagedMods = () => state.managedMods.filter((m) => m.loaders.includes(loaderOf(activeProfile().mcVersion)));

function perfEnabled(mod) {
  return mod.required || ((activeProfile().perf || {})[mod.slug] ?? mod.default);
}

function allMods() {
  const game = state.modules.map((m) => ({ key: `m:${m.id}`, name: m.name, description: m.description, category: m.category, on: m.enabled, locked: false, game: m }));
  const perf = profileManagedMods().map((m) => ({
    key: `p:${m.slug}`, name: m.name, description: m.required ? "Wird immer benötigt" : m.description, category: "Performance", on: perfEnabled(m), locked: m.required, perf: m,
  }));
  return [...game, ...perf];
}

function renderFilters() {
  const mods = allMods();
  const categories = ["Alle", "HUD", "Mechanik", "Visuell", "Client", "Performance"];
  $("filters").replaceChildren(...categories.map((c) => {
    const count = c === "Alle" ? mods.length : mods.filter((m) => m.category === c).length;
    const b = el("button", `filter${c === modFilter ? " active" : ""}`, c);
    b.append(el("small", null, String(count)));
    b.addEventListener("click", () => {
      modFilter = c;
      renderFilters();
      renderMods();
    });
    return b;
  }));
}

function renderMods() {
  const q = $("mod-search").value.trim().toLowerCase();
  const mods = allMods().filter((m) => (modFilter === "Alle" || m.category === modFilter) && (!q || `${m.name} ${m.description}`.toLowerCase().includes(q)));
  const grid = $("mod-grid");
  grid.replaceChildren();
  $("mods-profile").textContent = activeProfile().name;
  if (mods.length === 0) grid.append(el("p", "sub", "Keine Mods gefunden."));
  let lastCategory = null;
  for (const m of mods) {
    if (modFilter === "Alle" && m.category !== lastCategory) {
      grid.append(el("h4", "section-title", m.category));
      lastCategory = m.category;
    }
    const card = el("div", `mod${m.on ? " on" : ""}`);
    const icon = el("div", "icon");
    icon.innerHTML = m.game && MOD_ICONS[m.game.id] ? `<svg viewBox="0 0 24 24">${MOD_ICONS[m.game.id]}</svg>` : ICONS[m.category] || ICONS.HUD;
    const text = el("div", "text");
    text.append(el("b", null, m.name), el("small", null, m.description));
    card.append(icon, text, switchControl(m.on, m.locked, m.name, async (on) => {
      card.classList.toggle("on", on);
      if (m.game) state.modules = await api.setModule(m.game.id, on);
      else profileState = await api.setProfilePerf(m.perf.slug, on);
      renderSummary();
      renderFilters();
    }));
    grid.append(card);
  }
}
$("mod-search").addEventListener("input", renderMods);

function renderSummary() {
  const chips = $("perf-chips");
  chips.replaceChildren(...profileManagedMods().filter((m) => !m.required).map((m) => el("span", `pill${perfEnabled(m) ? " on" : ""}`, m.name)));
  $("ram-summary").textContent = `${(state.settings.memoryMb / 1024).toFixed(1)} GB`;
}

// ---------------------------------------------------------------------------------------------- Einstellungen

function renderSettings() {
  const s = state.settings;
  const max = Math.max(2048, Math.floor((state.totalMemoryMb * 0.75) / 512) * 512);
  $("memory").max = max;
  $("memory").value = Math.min(s.memoryMb, max);
  $("memory-out").value = `${(s.memoryMb / 1024).toFixed(1)} GB`;
  $("width").value = s.width;
  $("height").value = s.height;
  $("fullscreen").checked = s.fullscreen;
  $("after-launch").value = s.afterLaunch;
  $("jvm-args").value = s.jvmArgs;
  $("client-id").value = s.customClientId || "";
  $("server").value = s.server;
  $("game-dir").textContent = state.gameDir;
  $("mc-version").textContent = state.minecraft;
  $("rail-version").textContent = state.devBuild ? `v${state.version} DEV` : `v${state.version}`;
  if (state.devBuild) $("rail-version").classList.add("dev");
  renderRecentServers();
}

function renderRecentServers() {
  const recent = state.settings.recentServers || [];
  $("recent-servers").replaceChildren(...recent.map((address) => {
    const b = el("button", "pill", address);
    b.addEventListener("click", async () => {
      $("server").value = address;
      await save({ server: address });
    });
    return b;
  }));
}

async function save(patch) {
  state.settings = await api.setSettings(patch);
  renderSummary();
}

$("memory").addEventListener("input", () => ($("memory-out").value = `${($("memory").value / 1024).toFixed(1)} GB`));
$("memory").addEventListener("change", () => save({ memoryMb: Number($("memory").value) }));
$("width").addEventListener("change", () => save({ width: Number($("width").value) || 1280 }));
$("height").addEventListener("change", () => save({ height: Number($("height").value) || 720 }));
$("fullscreen").addEventListener("change", () => save({ fullscreen: $("fullscreen").checked }));
$("after-launch").addEventListener("change", () => save({ afterLaunch: $("after-launch").value }));
$("jvm-args").addEventListener("change", () => save({ jvmArgs: $("jvm-args").value.trim() }));
$("server").addEventListener("change", () => save({ server: $("server").value.trim() }));
$("client-id").addEventListener("change", () => save({ customClientId: $("client-id").value.trim() }));
$("open-dir").addEventListener("click", () => api.openGameDir());
$("verify").addEventListener("click", async () => {
  await save({ verifyNext: true });
  toast("Alle Dateien werden beim nächsten Start geprüft und repariert.");
});

// ---------------------------------------------------------------------------------------------- Start & Konsole

function setRunning(value) {
  running = value;
  $("launch").classList.toggle("running", value);
  $("launch").disabled = value;
  $("launch-label").textContent = value ? "LÄUFT" : "SPIELEN";
  $("launch-fill").style.width = "0";
  $("stop-game").disabled = !value;
}

function levelOf(line) {
  if (/\b(ERROR|FATAL)\]|Exception|^\s+at /.test(line)) return "error";
  if (/\bWARN\]/.test(line)) return "warn";
  if (line.startsWith("[Launcher]")) return "launcher";
  if (line.includes("[Mytic Client]")) return "mytic";
  return "";
}

function renderLog(force) {
  const log = $("log");
  if (!force && !$("page-console").classList.contains("active")) return;
  const atBottom = log.scrollTop + log.clientHeight >= log.scrollHeight - 40;
  const lines = logLines.filter((l) => logLevel === "all" || (logLevel === "warn" ? l.level === "warn" || l.level === "error" : l.level === "error"));
  log.replaceChildren(...lines.slice(-1500).map((l) => {
    const span = el("div", l.level || null, l.text);
    return span;
  }));
  if (atBottom || force) log.scrollTop = log.scrollHeight;
}

let logTimer = null;
function appendLog(line) {
  logLines.push({ text: line, level: levelOf(line) });
  if (logLines.length > 4000) logLines.splice(0, logLines.length - 4000);
  if (!logTimer) logTimer = setTimeout(() => { logTimer = null; renderLog(false); }, 120);
}

document.querySelectorAll("#log-filters .filter").forEach((b) => b.addEventListener("click", () => {
  logLevel = b.dataset.level;
  document.querySelectorAll("#log-filters .filter").forEach((x) => x.classList.toggle("active", x === b));
  renderLog(true);
}));

$("launch").addEventListener("click", async () => {
  if (!selectedAccount()) {
    openAccounts();
    return;
  }
  $("launch").disabled = true;
  $("status").textContent = "Wird vorbereitet …";
  try {
    const server = $("server").value.trim();
    const recent = [server, ...(state.settings.recentServers || []).filter((s) => s !== server)].filter(Boolean).slice(0, 4);
    await save({ server, recentServers: recent });
    renderRecentServers();
    await api.launch();
  } catch (error) {
    $("status").textContent = "Start fehlgeschlagen";
    $("launch-fill").style.width = "0";
    toast(errorText(error), true);
    $("launch").disabled = false;
  }
});

$("stop-game").addEventListener("click", () => api.stop());
$("copy-log").addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(logLines.map((l) => l.text).join("\n"));
    toast("Log kopiert");
  } catch {
    toast("Kopieren nicht möglich", true);
  }
});

api.onProgress(({ label, done, total }) => {
  $("status").textContent = total ? `${label} · ${done} / ${total}` : label;
  $("launch-fill").style.width = total ? `${Math.round((done / total) * 100)}%` : "6%";
});
api.onStatus((text) => ($("status").textContent = text));
api.onLog(appendLog);
api.onGame(({ running: isRunning, code }) => {
  setRunning(isRunning);
  if (isRunning) $("status").textContent = "Minecraft läuft – viel Spaß!";
  else $("status").textContent = code === 0 ? "Bereit" : `Beendet (Code ${code}) – Details in der Konsole`;
  if (!isRunning && code !== 0) toast("Minecraft wurde unerwartet beendet. Sieh dir die Konsole an.", true);
});

// ---------------------------------------------------------------------------------------------- Mod-Browser

const CATEGORIES = [["", "Alle"], ["optimization", "Optimierung"], ["utility", "Utility"], ["adventure", "Abenteuer"],
  ["technology", "Technik"], ["magic", "Magie"], ["decoration", "Dekoration"], ["storage", "Lager"], ["library", "Bibliothek"]];
let browserLoaded = false;
let browserTab = "discover";
let browserCategory = "";
let browserOffset = 0;
let browserTotal = 0;
let installedMods = [];
const busyMods = new Set();

const formatCount = (n) => n >= 1e6 ? `${n >= 1e7 ? Math.round(n / 1e6) : (n / 1e6).toFixed(1).replace(".", ",")} Mio` : n >= 1e3 ? `${Math.round(n / 1e3)} Tsd` : String(n);
const ICON_DL = '<svg viewBox="0 0 24 24"><path d="M12 4v11M7 10l5 5 5-5M5 20h14"/></svg>';
const ICON_HEART = '<svg viewBox="0 0 24 24"><path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/></svg>';

function renderCategories() {
  $("browser-categories").replaceChildren(...CATEGORIES.map(([id, label]) => {
    const b = el("button", `filter${id === browserCategory ? " active" : ""}`, label);
    b.addEventListener("click", () => {
      browserCategory = id;
      renderCategories();
      loadBrowser(true);
    });
    return b;
  }));
  $("browser-categories").hidden = browserTab !== "discover";
}

function modIcon(src, title) {
  if (src) {
    const img = el("img", "mr-icon");
    img.src = src;
    img.alt = "";
    img.loading = "lazy";
    img.addEventListener("error", () => img.replaceWith(modIcon("", title)));
    return img;
  }
  return el("div", "mr-icon empty", (title || "?")[0].toUpperCase());
}

function browserCard(mod, { installedEntry } = {}) {
  const card = el("article", "mr");
  const installed = installedEntry || installedMods.find((m) => m.id === mod.id);
  if (installed) card.classList.add("installed");
  if (installed && !installed.enabled) card.classList.add("disabled");
  const body = el("div", "mr-body");
  const title = el("div", "mr-title");
  title.append(el("b", null, mod.title));
  if (mod.author) title.append(el("small", null, `von ${mod.author}`));
  if (installed && installed.versionNumber) title.append(el("small", null, installed.versionNumber));
  body.append(title);
  if (mod.description != null) body.append(el("p", "mr-desc", mod.description));
  if (installed && installed.dependency) body.append(el("span", "mr-req", `Benötigt von: ${installed.requiredBy.join(", ")}`));
  if (installed && installed.incompatible) body.append(el("span", "mr-req warn", `Nicht verfügbar für ${activeProfile().mcVersion} – automatisch aus`));

  const foot = el("div", "mr-foot");
  const stats = el("div", "mr-stats");
  if (mod.downloads != null) {
    const d = el("span");
    d.innerHTML = ICON_DL;
    d.append(formatCount(mod.downloads));
    d.title = `${(mod.downloads || 0).toLocaleString("de-DE")} Downloads · ${(mod.follows || 0).toLocaleString("de-DE")} Follower`;
    stats.append(d);
  }
  foot.append(stats);

  if (mod.managed) {
    foot.append(el("button", "mr-btn done", "Enthalten"));
  } else if (installed) {
    if (!installed.dependency) {
      foot.append(switchControl(installed.enabled, false, `${mod.title} aktiv`, async (on) => {
        installedMods = await api.toggleMod(mod.id, on);
        card.classList.toggle("disabled", !on);
        if (running) toast("Gilt ab dem nächsten Start.");
      }));
    }
    const remove = el("button", "mr-btn remove", "Entfernen");
    remove.addEventListener("click", async () => {
      remove.disabled = true;
      try {
        installedMods = await api.removeMod(mod.id);
        toast(`${mod.title} entfernt`);
        updateInstalledCount();
        browserTab === "installed" ? renderInstalled() : card.replaceWith(browserCard(mod));
      } catch (error) {
        toast(errorText(error), true);
        remove.disabled = false;
      }
    });
    if (!installed.dependency) foot.append(remove);
  } else {
    const install = el("button", "mr-btn install", busyMods.has(mod.id) ? "Wird installiert …" : "Installieren");
    install.disabled = busyMods.has(mod.id);
    install.addEventListener("click", async () => {
      busyMods.add(mod.id);
      install.disabled = true;
      install.textContent = "Wird installiert …";
      try {
        const result = await api.installMod(mod.id);
        installedMods = result.list;
        const extra = result.installed.filter((t) => t !== mod.title);
        toast(extra.length ? `${mod.title} installiert (+ ${extra.join(", ")})` : `${mod.title} installiert`);
        updateInstalledCount();
        card.replaceWith(browserCard(mod));
      } catch (error) {
        toast(errorText(error), true);
        install.disabled = false;
        install.textContent = "Installieren";
      } finally {
        busyMods.delete(mod.id);
      }
    });
    foot.append(install);
  }
  body.append(foot);
  card.append(modIcon(mod.icon, mod.title), body);
  return card;
}

function updateInstalledCount() {
  $("installed-count").textContent = String(installedMods.filter((m) => !m.dependency).length);
}

async function loadBrowser(reset) {
  browserLoaded = true;
  if (browserTab === "installed") return renderInstalled();
  const results = $("browser-results");
  if (reset) {
    browserOffset = 0;
    results.replaceChildren(...Array.from({ length: 6 }, () => el("div", "skeleton")));
  }
  const query = $("browser-search").value.trim();
  const token = (loadBrowser.token = Symbol());
  try {
    const page = await api.searchMods({ query, sort: $("browser-sort").value, category: browserCategory, offset: browserOffset });
    if (token !== loadBrowser.token) return;
    if (reset) results.replaceChildren();
    browserTotal = page.total;
    browserOffset += page.hits.length;
    results.append(...page.hits.map((m) => browserCard(m)));
    if (reset && page.hits.length === 0) results.append(el("p", "sub", `Keine passenden Mods für ${activeProfile().mcVersion} gefunden.`));
    $("browser-more").hidden = browserOffset >= browserTotal;
    $("browser-info").textContent = `${formatCount(browserTotal)} Mods für Minecraft ${activeProfile().mcVersion} mit ${loaderName(activeProfile().mcVersion)} · Installiert ins Profil „${activeProfile().name}“`;
  } catch (error) {
    if (reset) results.replaceChildren(el("p", "sub", `Modrinth ist gerade nicht erreichbar: ${errorText(error)}`));
  }
}

function renderInstalled() {
  const results = $("browser-results");
  $("browser-more").hidden = true;
  results.replaceChildren();
  if (installedMods.length === 0) {
    $("browser-info").textContent = `Profil „${activeProfile().name}“`;
    results.append(el("p", "sub", "In diesem Profil sind noch keine eigenen Mods. Unter „Entdecken“ findest du Tausende Mods."));
    return;
  }
  $("browser-info").textContent = `Eigene Mods im Profil „${activeProfile().name}“ – werden bei jedem Spielstart automatisch aktualisiert.`;
  results.append(...installedMods.map((m) => browserCard({ id: m.id, title: m.title, icon: m.icon }, { installedEntry: m })));
}

let searchTimer = null;
$("browser-search").addEventListener("input", () => {
  clearTimeout(searchTimer);
  if (browserTab !== "discover") selectBrowserTab("discover");
  searchTimer = setTimeout(() => loadBrowser(true), 300);
});
$("browser-sort").addEventListener("change", () => loadBrowser(true));
$("browser-more").addEventListener("click", () => loadBrowser(false));

function selectBrowserTab(tab) {
  browserTab = tab;
  document.querySelectorAll("#browser-tabs .filter").forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
  $("browser-sort").hidden = tab !== "discover";
  renderCategories();
}
document.querySelectorAll("#browser-tabs .filter").forEach((b) => b.addEventListener("click", () => {
  selectBrowserTab(b.dataset.tab);
  loadBrowser(true);
}));

// ---------------------------------------------------------------------------------------------- Profile

const ICON_EDIT = '<svg viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16z"/></svg>';
const ICON_COPY = '<svg viewBox="0 0 24 24"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"/></svg>';
const ICON_TRASH = '<svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>';

function profileError(message) {
  $("profile-error").textContent = message || "";
  $("profile-error").hidden = !message;
}

function iconButton(svg, title, className) {
  const b = el("button", className || null);
  b.type = "button";
  b.title = title;
  b.setAttribute("aria-label", title);
  b.innerHTML = svg;
  return b;
}

function fillVersionSelect(select, value) {
  const list = versions.length ? versions : [value];
  select.replaceChildren(...list.map((v) => {
    const option = el("option", null, loaderOf(v) === "forge" ? `${v} · Forge` : v);
    option.value = v;
    return option;
  }));
  select.value = value;
}

async function applyProfiles(next, switched) {
  const before = activeProfile();
  profileState = next;
  const p = activeProfile();
  if (before && before.mcVersion && before.mcVersion !== p.mcVersion && before.id === p.id) switched = true;
  $("profile-name").textContent = p.name;
  $("mc-version").textContent = p.mcVersion;
  $("mc-loader").textContent = loaderName(p.mcVersion);
  $("version-profile").textContent = p.name;
  fillVersionSelect($("profile-version"), p.mcVersion);
  fillVersionSelect($("profile-new-version"), p.mcVersion);
  $("version-hint").hidden = p.mytic;
  $("version-hint").textContent = `Mytic-Ingame-Mods (HUD, Menü) gibt es für ${p.mcVersion} noch nicht – das Spiel startet mit ${loaderName(p.mcVersion)} und Performance-Mods. Weitere Versionen folgen.`;
  $("mytic-notice").hidden = p.mytic;
  $("mytic-notice").textContent = `Hinweis: Für Minecraft ${p.mcVersion} (Profil „${p.name}“) gibt es die Mytic-Ingame-Mods noch nicht. Sie folgen in den nächsten Updates.`;
  $("profile-dot").style.background = p.color;
  $("profile-dot").style.color = p.color;
  $("launch-profile").textContent = `Profil: ${p.name} · ${p.mcVersion}`;
  renderProfileList();
  if (switched) {
    installedMods = await api.listMods();
    updateInstalledCount();
    renderMods();
    renderFilters();
    renderSummary();
    if (browserLoaded) loadBrowser(true);
  }
}

function renderProfileList() {
  const list = $("profile-list");
  list.replaceChildren(...profileState.list.map((p) => {
    const row = el("div", `prof${p.id === profileState.active ? " active" : ""}`);
    const dot = el("span", "profile-dot");
    dot.style.background = p.color;
    dot.style.color = p.color;
    const who = el("div", "who");
    const title = el("b", null, p.name);
    who.append(title, el("small", null, p.modCount === 1 ? "1 eigener Mod" : `${p.modCount} eigene Mods`));
    const chip = el("span", "ver-chip", p.mcVersion);
    const actions = el("div", "prof-actions");
    const edit = iconButton(ICON_EDIT, "Umbenennen");
    const copy = iconButton(ICON_COPY, "Duplizieren");
    const del = iconButton(ICON_TRASH, "Löschen", "danger");
    actions.append(edit, copy);
    if (profileState.list.length > 1) actions.append(del);
    row.append(dot, who, chip, actions);

    row.addEventListener("click", async (e) => {
      if (e.target.closest(".prof-actions") || row.classList.contains("editing")) return;
      if (p.id === profileState.active) return;
      await applyProfiles(await api.selectProfile(p.id), true);
      toast(`Profil „${p.name}“ aktiv`);
    });
    edit.addEventListener("click", () => {
      row.classList.add("editing");
      const input = el("input");
      input.value = p.name;
      input.maxLength = 24;
      who.replaceChildren(input);
      input.focus();
      input.select();
      const finish = async (saveIt) => {
        if (!row.classList.contains("editing")) return;
        row.classList.remove("editing");
        try {
          if (saveIt && input.value.trim() && input.value.trim() !== p.name) await applyProfiles(await api.renameProfile(p.id, input.value), false);
          else renderProfileList();
        } catch (error) {
          profileError(errorText(error));
          renderProfileList();
        }
      };
      input.addEventListener("keydown", (ev) => {
        if (ev.key === "Enter") finish(true);
        if (ev.key === "Escape") { ev.stopPropagation(); finish(false); }
      });
      input.addEventListener("blur", () => finish(true));
    });
    copy.addEventListener("click", async () => {
      try {
        await applyProfiles(await api.createProfile(`${p.name} (Kopie)`.slice(0, 24), p.id, p.mcVersion), true);
        toast(`Profil „${p.name}“ dupliziert`);
      } catch (error) {
        profileError(errorText(error));
      }
    });
    del.addEventListener("click", async () => {
      if (!del.classList.contains("confirm")) {
        del.classList.add("confirm");
        del.textContent = "Sicher?";
        setTimeout(() => { if (del.isConnected) { del.classList.remove("confirm"); del.innerHTML = ICON_TRASH; } }, 3000);
        return;
      }
      try {
        await applyProfiles(await api.deleteProfile(p.id), true);
        toast(`Profil „${p.name}“ gelöscht`);
      } catch (error) {
        profileError(errorText(error));
      }
    });
    return row;
  }));
}

$("profile-button").addEventListener("click", () => {
  profileError("");
  $("accounts").hidden = true;
  $("profiles").hidden = !$("profiles").hidden;
});
// ---------------------------------------------------------------------------------------------- Import

const IMPORT_SOURCE_CLASS = { "Modrinth App": "modrinth", NoRiskClient: "norisk", "Lunar Client": "lunar" };
const LOADER_LABEL = { fabric: "Fabric", forge: "Forge", neoforge: "NeoForge", quilt: "Quilt", vanilla: "Vanilla", unknown: "" };

function importRow(item) {
  const row = el("div", `imp${item.target || item.chooseVersion ? "" : " off"}`);
  row.dataset.id = item.id;
  row.append(el("span", `imp-src ${IMPORT_SOURCE_CLASS[item.source] || ""}`, item.source));
  const body = el("div", "imp-body");
  const loader = LOADER_LABEL[item.loader] ?? item.loader;
  const facts = [item.mcVersion ? `Minecraft ${item.mcVersion}` : "Version unbekannt", loader, `${item.modCount} ${item.modCount === 1 ? "Mod" : "Mods"}`].filter(Boolean).join(" · ");
  body.append(el("b", null, item.name), el("small", null, facts));
  if (item.reason) body.append(el("small", "warn", ` – ${item.reason}`));
  else if (item.target !== item.mcVersion) body.append(el("small", null, ` → als ${item.target}`));
  row.append(body);
  const button = el("button", "mr-btn install", "Übernehmen");
  button.disabled = !item.target;
  // Version unbekannt oder nicht unterstützt: selbst wählen
  let chosen = null;
  if (item.chooseVersion) {
    const select = el("select", "imp-version");
    select.append(el("option", null, "Version wählen …"));
    for (const v of versions) {
      const option = el("option", null, loaderOf(v) === "forge" ? `${v} · Forge` : v);
      option.value = v;
      select.append(option);
    }
    select.firstChild.value = "";
    select.addEventListener("change", () => {
      chosen = select.value || null;
      button.disabled = !chosen;
    });
    body.append(el("br"), select);
  }
  button.addEventListener("click", async () => {
    button.disabled = true;
    button.textContent = "Importiere …";
    try {
      const result = await api.runImport(item.id, chosen);
      await applyProfiles(result.profiles, true);
      pickedImports = pickedImports.filter((p) => p.id !== item.id);
      $("import").hidden = true;
      toast(`Profil „${result.name}“ angelegt: ${result.copied} Mods übernommen, ${result.identified} davon bei Modrinth erkannt${result.skipped ? `, ${result.skipped} passten nicht` : ""}.`);
    } catch (error) {
      button.disabled = !item.target && !chosen;
      button.textContent = "Übernehmen";
      toast(errorText(error), true);
    }
  });
  row.append(button);
  return row;
}

/** Vom PC hochgeladene Einträge bleiben oben stehen, auch wenn die Suche danach fertig wird. */
let pickedImports = [];

async function scanImports() {
  const list = $("import-list");
  list.replaceChildren(...pickedImports.map(importRow), el("p", "sub", "Suche nach Profilen …"));
  try {
    const items = await api.scanImports();
    list.replaceChildren(...pickedImports.map(importRow), ...items.map(importRow));
    if (!items.length && !pickedImports.length) list.append(el("p", "sub", "Keine Profile gefunden. Installierte Launcher: Modrinth App, NoRiskClient oder Lunar Client – oder wähle eine .mrpack-Datei."));
  } catch (error) {
    list.replaceChildren(el("p", "error", errorText(error)));
  }
}

$("import-open").addEventListener("click", () => {
  $("profiles").hidden = true;
  $("import").hidden = false;
  scanImports();
});
$("import-rescan").addEventListener("click", scanImports);
/** Vom PC gewählte Profile oben in die Liste setzen. */
function showPicked(items) {
  const list = $("import-list");
  pickedImports = [...items, ...pickedImports.filter((p) => !items.some((i) => i.id === p.id))];
  list.querySelectorAll("p.sub:not(:last-child)").forEach((p) => p.remove());
  for (const item of [...items].reverse()) {
    const old = [...list.children].find((c) => c.dataset.id === item.id);
    if (old) old.remove();
    list.prepend(importRow(item));
  }
  if (!items.length) toast("In der Auswahl wurden keine Mods oder Profile gefunden.", true);
}
async function pick(kind) {
  try {
    const items = await api.pickImport(kind);
    if (items.length) showPicked(items);
  } catch (error) {
    toast(errorText(error), true);
  }
}
$("import-folder").addEventListener("click", () => pick("folder"));
$("import-file").addEventListener("click", () => pick("files"));
const drop = $("import-drop");
["dragenter", "dragover"].forEach((type) => drop.addEventListener(type, (e) => {
  e.preventDefault();
  drop.classList.add("over");
}));
["dragleave", "drop"].forEach((type) => drop.addEventListener(type, () => drop.classList.remove("over")));
drop.addEventListener("drop", async (e) => {
  e.preventDefault();
  const paths = [...e.dataTransfer.files].map((f) => api.pathForFile(f)).filter(Boolean);
  if (!paths.length) return;
  try {
    showPicked(await api.importPaths(paths));
  } catch (error) {
    toast(errorText(error), true);
  }
});
// Dateien außerhalb der Ablage nicht im Fenster öffnen
["dragover", "drop"].forEach((type) => document.addEventListener(type, (e) => e.preventDefault()));
$("import").addEventListener("click", (e) => {
  if (e.target === $("import")) $("import").hidden = true;
});

$("profiles").addEventListener("click", (e) => {
  if (e.target === $("profiles")) $("profiles").hidden = true;
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    $("profiles").hidden = true;
    $("import").hidden = true;
  }
});
$("profile-version").addEventListener("change", async () => {
  profileError("");
  try {
    const version = $("profile-version").value;
    await applyProfiles(await api.setProfileVersion(activeProfile().id, version), true);
    toast(`Profil „${activeProfile().name}“ nutzt jetzt Minecraft ${version}`);
  } catch (error) {
    profileError(errorText(error));
    fillVersionSelect($("profile-version"), activeProfile().mcVersion);
  }
});

$("profile-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  profileError("");
  try {
    const name = $("profile-new").value;
    await applyProfiles(await api.createProfile(name, null, $("profile-new-version").value), true);
    $("profile-new").value = "";
    toast(`Profil „${activeProfile().name}“ erstellt`);
  } catch (error) {
    profileError(errorText(error));
  }
});

// ---------------------------------------------------------------------------------------------- Updates

let updateInfo = null;
api.onUpdate((info) => {
  updateInfo = info;
  $("update-title").textContent = `Update auf v${info.version} verfügbar`;
  const mb = info.size ? ` · ${(info.size / 1048576).toFixed(0)} MB` : "";
  $("update-sub").textContent = info.canInstall ? `Ein Klick – deine Konten und Einstellungen bleiben erhalten${mb}` : `Neue Version auf GitHub herunterladen${mb}`;
  $("update-install").textContent = info.canInstall ? "Jetzt aktualisieren" : "Herunterladen";
  $("update-banner").hidden = false;
});
api.onUpdateProgress(({ done, total }) => {
  $("update-progress").hidden = false;
  $("update-bar").style.width = `${Math.round((done / total) * 100)}%`;
  $("update-sub").textContent = `Wird geladen … ${(done / 1048576).toFixed(0)} / ${(total / 1048576).toFixed(0)} MB`;
});
api.onUpdated(({ version }) => toast(`Mytic Client wurde auf v${version} aktualisiert.`));
$("update-close").addEventListener("click", () => ($("update-banner").hidden = true));
$("update-install").addEventListener("click", async () => {
  if (running) {
    toast("Bitte zuerst Minecraft beenden.", true);
    return;
  }
  $("update-install").disabled = true;
  try {
    const result = await api.installUpdate();
    if (result && result.restarting) {
      $("update-sub").textContent = "Neue Version startet …";
    } else {
      $("update-install").disabled = false;
    }
  } catch (error) {
    $("update-install").disabled = false;
    $("update-progress").hidden = true;
    toast(errorText(error), true);
  }
});

(async () => {
  state = await api.state();
  $("offline-area").hidden = !state.offlineAllowed;
  profileState = await api.listProfiles();
  versions = await api.listVersions().catch(() => []);
  renderAccounts();
  renderFilters();
  renderMods();
  renderSummary();
  renderSettings();
  renderCategories();
  installedMods = await api.listMods();
  updateInstalledCount();
  applyProfiles(profileState, false);
  setRunning(state.running);
})();
