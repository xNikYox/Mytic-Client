// Skins pro Konto: Vorschau (vorne/hinten), Hochladen, Skin anderer Spieler, Bibliothek, Umhänge.
// Nutzt api, $, el, toast, errorText, state und avatar aus app.js.

/** Teile eines Skins (64×64, bei alten 64×32 werden die linken Gliedmaßen gespiegelt) – Ziel in Modell-Pixeln (16×32) */
function skinParts(slim, legacy, back) {
  const a = slim ? 3 : 4;
  // [Quelle x, y, Breite, Höhe, Ziel x, y, gespiegelt]
  if (!back) {
    return [
      [8, 8, 8, 8, 4, 0], [20, 20, 8, 12, 4, 8],
      [44, 20, a, 12, 4 - a, 8], // rechter Arm (links im Bild)
      legacy ? [44, 20, a, 12, 12, 8, true] : [36, 52, a, 12, 12, 8],
      [4, 20, 4, 12, 4, 20],
      legacy ? [4, 20, 4, 12, 8, 20, true] : [20, 52, 4, 12, 8, 20],
      // zweite Ebene
      [40, 8, 8, 8, 4, 0], ...(legacy ? [] : [[20, 36, 8, 12, 4, 8], [44, 36, a, 12, 4 - a, 8], [52, 52, a, 12, 12, 8], [4, 36, 4, 12, 4, 20], [4, 52, 4, 12, 8, 20]]),
    ];
  }
  return [
    [24, 8, 8, 8, 4, 0], [32, 20, 8, 12, 4, 8],
    [48 + a, 20, a, 12, 12, 8], // rechter Arm von hinten (rechts im Bild)
    legacy ? [48 + a, 20, a, 12, 4 - a, 8, true] : [40 + a, 52, a, 12, 4 - a, 8],
    [12, 20, 4, 12, 8, 20],
    legacy ? [12, 20, 4, 12, 4, 20, true] : [28, 52, 4, 12, 4, 20],
    [56, 8, 8, 8, 4, 0], ...(legacy ? [] : [[32, 36, 8, 12, 4, 8], [52 + a, 36, a, 12, 12, 8], [56 + a, 52, a, 12, 4 - a, 8], [12, 36, 4, 12, 8, 20], [12, 52, 4, 12, 4, 20]]),
  ];
}

/** Bild laden (data:-URL) */
function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

/** Figur zeichnen; ohne Bild der Standard-Umriss */
async function drawSkin(canvas, src, slim, back) {
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.imageSmoothingEnabled = false;
  const k = canvas.width / 16;
  if (!src) {
    ctx.fillStyle = "rgba(255,255,255,.08)";
    for (const [, , w, h, x, y] of skinParts(slim, true, back).slice(0, 6)) ctx.fillRect(x * k, y * k, w * k, h * k);
    return;
  }
  let img;
  try {
    img = await loadImage(src);
  } catch {
    return;
  }
  const legacy = img.height === 32;
  for (const [sx, sy, w, h, x, y, mirror] of skinParts(slim, legacy, back)) {
    if (mirror) {
      ctx.save();
      ctx.translate((x + w) * k, y * k);
      ctx.scale(-1, 1);
      ctx.drawImage(img, sx, sy, w, h, 0, 0, w * k, h * k);
      ctx.restore();
    } else {
      ctx.drawImage(img, sx, sy, w, h, x * k, y * k, w * k, h * k);
    }
  }
}

/** Umhang-Vorschau: Außenseite (1,1 / 10×16 in der 64×32-Textur) */
async function drawCape(canvas, src) {
  const ctx = canvas.getContext("2d");
  ctx.imageSmoothingEnabled = false;
  try {
    const img = await loadImage(src);
    const scale = img.width / 64;
    ctx.drawImage(img, 1 * scale, 1 * scale, 10 * scale, 16 * scale, 0, 0, canvas.width, canvas.height);
  } catch {
    // Bild fehlt
  }
}

const skinsView = { uuid: null, profile: null, pending: null, model: "classic", library: [], busy: false, loadId: 0 };

function skinAccounts() {
  return state.accounts.list.filter((a) => a.type === "microsoft");
}

function setSkinBusy(busy) {
  skinsView.busy = busy;
  for (const id of ["skin-apply", "skin-reset", "skin-keep", "skin-file"]) $(id).disabled = busy || (id === "skin-apply" && !skinsView.pending) || (!skinsView.profile && id !== "skin-file");
  document.querySelectorAll(".skins-cape").forEach((b) => (b.disabled = busy));
}

async function loadSkins() {
  const list = skinAccounts();
  $("skins-notice").hidden = list.length > 0;
  if (!list.length) {
    $("skins-notice").textContent = "Skins lassen sich nur bei Microsoft-Konten ändern – füge oben rechts ein Konto hinzu.";
    $("skins-accounts").replaceChildren();
    $("skins-caption").textContent = "";
    drawSkin($("skin-front"), null, false, false);
    drawSkin($("skin-back"), null, false, true);
  }
  if (!skinsView.uuid || !list.some((a) => a.uuid === skinsView.uuid)) {
    const sel = selectedAccount();
    skinsView.uuid = sel && sel.type === "microsoft" ? sel.uuid : list[0] ? list[0].uuid : null;
  }
  renderSkinAccounts();
  loadSkinLibrary();
  if (skinsView.uuid) await loadSkinProfile();
}

function renderSkinAccounts() {
  const box = $("skins-accounts");
  box.replaceChildren();
  const list = skinAccounts();
  if (list.length < 2) return;
  for (const a of list) {
    const b = el("button", `filter${a.uuid === skinsView.uuid ? " active" : ""}`);
    const img = el("img");
    img.src = avatar(a.uuid);
    img.alt = "";
    b.append(img, a.name);
    b.addEventListener("click", () => {
      if (a.uuid === skinsView.uuid) return;
      skinsView.uuid = a.uuid;
      skinsView.pending = null;
      renderSkinAccounts();
      loadSkinProfile();
    });
    box.append(b);
  }
}

async function loadSkinProfile() {
  const id = ++skinsView.loadId;
  skinsView.profile = null;
  $("skins-caption").textContent = "Wird geladen …";
  setSkinBusy(true);
  try {
    const profile = await api.skinProfile(skinsView.uuid);
    if (id !== skinsView.loadId) return;
    skinsView.profile = profile;
    if (!skinsView.pending) skinsView.model = profile.skin ? profile.skin.variant : "classic";
  } catch (error) {
    if (id !== skinsView.loadId) return;
    $("skins-caption").textContent = errorText(error);
  } finally {
    if (id === skinsView.loadId) setSkinBusy(false);
  }
  renderSkinStage();
  renderCapes();
}

function renderSkinStage() {
  const p = skinsView.profile, pending = skinsView.pending;
  const src = pending ? pending.image : p && p.skin ? p.skin.image : null;
  const slim = skinsView.model === "slim";
  drawSkin($("skin-front"), src, slim, false);
  drawSkin($("skin-back"), src, slim, true);
  $("skins-stage").classList.toggle("pending", Boolean(pending));
  document.querySelectorAll("#skin-model .filter").forEach((b) => b.classList.toggle("active", b.dataset.model === skinsView.model));
  $("skin-cancel").hidden = !pending;
  const caption = $("skins-caption");
  caption.replaceChildren();
  if (pending) caption.append(el("b", null, pending.name), el("small", null, " · Vorschau – noch nicht übernommen"));
  else if (p) caption.append(el("b", null, p.name), el("small", null, p.skin ? ` · ${p.skin.variant === "slim" ? "Slim" : "Classic"}` : " · Standard-Skin"));
  setSkinBusy(skinsView.busy);
}

function renderCapes() {
  const box = $("skins-capes");
  box.replaceChildren();
  const p = skinsView.profile;
  if (!p) return;
  if (!p.capes.length) {
    box.append(el("span", "sub", "Dieses Konto hat keine offiziellen Umhänge."));
    return;
  }
  const none = el("button", `skins-cape${p.capes.some((c) => c.active) ? "" : " active"}`);
  none.append(el("div", "none", "–"), el("span", null, "Keiner"));
  none.addEventListener("click", () => setCape(null));
  box.append(none);
  for (const c of p.capes) {
    const b = el("button", `skins-cape${c.active ? " active" : ""}`);
    b.title = c.name;
    const canvas = el("canvas");
    canvas.width = 30;
    canvas.height = 48;
    if (c.image) drawCape(canvas, c.image);
    b.append(canvas, el("span", null, c.name));
    b.addEventListener("click", () => !c.active && setCape(c.id));
    box.append(b);
  }
}

async function setCape(id) {
  setSkinBusy(true);
  try {
    skinsView.profile = await api.skinCape(skinsView.uuid, id);
    toast(id ? "Umhang angelegt." : "Umhang abgelegt.");
  } catch (error) {
    toast(errorText(error), true);
  } finally {
    setSkinBusy(false);
    renderCapes();
  }
}

/** Neue Quelle als Vorschau zeigen (noch nichts hochladen) */
async function previewSkin(source) {
  try {
    const preview = await api.skinPreview(source);
    skinsView.pending = { source, image: preview.image, name: preview.name };
    if (preview.variant) skinsView.model = preview.variant;
    renderSkinStage();
  } catch (error) {
    toast(errorText(error), true);
  }
}

async function loadSkinLibrary() {
  try {
    skinsView.library = await api.skinLibrary();
  } catch {
    skinsView.library = [];
  }
  renderSkinLibrary();
}

function renderSkinLibrary() {
  const box = $("skins-library");
  box.replaceChildren();
  $("skins-lib-empty").hidden = skinsView.library.length > 0;
  $("skins-lib-count").textContent = skinsView.library.length ? String(skinsView.library.length) : "";
  for (const s of skinsView.library) {
    const item = el("div", "skins-lib-item");
    if (skinsView.pending && skinsView.pending.source.kind === "library" && skinsView.pending.source.id === s.id) item.classList.add("selected");
    item.title = `${s.name} (${s.variant === "slim" ? "Slim" : "Classic"}) – Klick zeigt die Vorschau`;
    const canvas = el("canvas");
    canvas.width = 64;
    canvas.height = 128;
    drawSkin(canvas, s.image, s.variant === "slim", false);
    const del = el("button", "del", "✕");
    del.title = "Aus der Bibliothek löschen";
    del.addEventListener("click", async (event) => {
      event.stopPropagation();
      skinsView.library = await api.skinRemove(s.id);
      renderSkinLibrary();
    });
    item.append(canvas, el("span", null, s.name), del);
    item.addEventListener("click", async () => {
      await previewSkin({ kind: "library", id: s.id });
      renderSkinLibrary();
    });
    box.append(item);
  }
}

$("skin-file").addEventListener("click", async () => {
  const file = await api.skinPick();
  if (file) previewSkin({ kind: "file", path: file });
});

$("skin-player").addEventListener("submit", (event) => {
  event.preventDefault();
  const name = $("skin-player-name").value.trim();
  if (name) previewSkin({ kind: "player", name });
});

document.querySelectorAll("#skin-model .filter").forEach((b) => b.addEventListener("click", () => {
  skinsView.model = b.dataset.model;
  renderSkinStage();
}));

$("skin-cancel").addEventListener("click", () => {
  skinsView.pending = null;
  skinsView.model = skinsView.profile && skinsView.profile.skin ? skinsView.profile.skin.variant : "classic";
  renderSkinStage();
  renderSkinLibrary();
});

$("skin-apply").addEventListener("click", async () => {
  if (!skinsView.pending) return;
  setSkinBusy(true);
  $("skin-apply").textContent = "Wird übernommen …";
  try {
    skinsView.profile = await api.skinApply(skinsView.uuid, skinsView.pending.source, skinsView.model);
    skinsView.pending = null;
    toast(`Neuer Skin für ${skinsView.profile.name} – sichtbar nach dem nächsten Server-Beitritt.`);
    loadSkinLibrary();
  } catch (error) {
    toast(errorText(error), true);
  } finally {
    $("skin-apply").textContent = "Skin übernehmen";
    setSkinBusy(false);
    renderSkinStage();
  }
});

$("skin-reset").addEventListener("click", async () => {
  if (!confirm("Wirklich den Standard-Skin (Steve/Alex) verwenden? Tipp: vorher „Aktuellen Skin merken“.")) return;
  setSkinBusy(true);
  try {
    skinsView.profile = await api.skinReset(skinsView.uuid);
    skinsView.pending = null;
    toast("Standard-Skin wird verwendet.");
  } catch (error) {
    toast(errorText(error), true);
  } finally {
    setSkinBusy(false);
    renderSkinStage();
  }
});

$("skin-keep").addEventListener("click", async () => {
  try {
    skinsView.library = await api.skinKeep(skinsView.uuid);
    renderSkinLibrary();
    toast("Skin in der Bibliothek gespeichert.");
  } catch (error) {
    toast(errorText(error), true);
  }
});

// PNG auf die Vorschau ziehen
const skinStage = $("skins-stage");
skinStage.addEventListener("dragover", (event) => {
  event.preventDefault();
  skinStage.classList.add("dragging");
});
skinStage.addEventListener("dragleave", () => skinStage.classList.remove("dragging"));
skinStage.addEventListener("drop", (event) => {
  event.preventDefault();
  skinStage.classList.remove("dragging");
  const file = event.dataTransfer.files[0];
  if (file) previewSkin({ kind: "file", path: api.pathForFile(file) });
});
