// Skins und Umhänge eines Microsoft-Kontos über die offiziellen Minecraft-Dienste, dazu eine lokale Skin-Bibliothek.
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");

const API = "https://api.minecraftservices.com/minecraft/profile";
const MAX_PNG = 256 * 1024;

/** Nur Texturen von Mojang laden (die API liefert teils http:// – wird zu https) */
function textureUrl(url) {
  try {
    const u = new URL(String(url));
    if (u.hostname !== "textures.minecraft.net") return null;
    u.protocol = "https:";
    return u.toString();
  } catch {
    return null;
  }
}

/** Prüft eine Skin-Datei: PNG, 64×64 oder 64×32 (alte Skins) – gibt { width, height } zurück */
function checkSkinPng(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 33 || buffer.length > MAX_PNG) throw new Error("Skin muss eine PNG-Datei unter 256 KB sein");
  if (buffer.readUInt32BE(0) !== 0x89504e47 || buffer.toString("ascii", 12, 16) !== "IHDR") throw new Error("Keine gültige PNG-Datei");
  const width = buffer.readUInt32BE(16), height = buffer.readUInt32BE(20);
  if (width !== 64 || (height !== 64 && height !== 32)) throw new Error(`Skins müssen 64×64 (oder 64×32) Pixel groß sein – diese Datei hat ${width}×${height}`);
  return { width, height };
}

/** Fehlertext der Minecraft-Dienste verständlich machen */
async function apiError(response) {
  let detail = "";
  try {
    const body = await response.json();
    detail = body.errorMessage || body.error || "";
  } catch {
    // keine JSON-Antwort
  }
  if (response.status === 401) return new Error("Anmeldung abgelaufen – Konto im Launcher neu hinzufügen");
  if (response.status === 429) return new Error("Zu viele Änderungen – Mojang erlaubt nur wenige pro Minute, kurz warten");
  if (response.status === 404) return new Error("Dieses Konto besitzt kein Minecraft");
  return new Error(`Minecraft-Dienste: ${detail || `Fehler ${response.status}`}`);
}

function createSkins({ fetch = globalThis.fetch, libraryDir }) {
  const auth = (token) => ({ Authorization: `Bearer ${token}` });

  async function call(token, method, url, body, headers = {}) {
    const response = await fetch(url, { method, headers: { ...auth(token), ...headers }, body });
    if (!response.ok) throw await apiError(response);
    return response.status === 204 ? null : response.json();
  }

  /** PNG einer Mojang-Textur als data:-URL (die Oberfläche lädt keine fremden Bilder) */
  async function texture(url) {
    const safe = textureUrl(url);
    if (!safe) return null;
    const response = await fetch(safe);
    if (!response.ok) return null;
    const buffer = Buffer.from(await response.arrayBuffer());
    if (buffer.length > MAX_PNG) return null;
    return `data:image/png;base64,${buffer.toString("base64")}`;
  }

  /** Profil mit aktivem Skin und allen Umhängen (Bilder als data:-URL) */
  async function profile(token) {
    const p = await call(token, "GET", API);
    const active = (p.skins || []).find((s) => s.state === "ACTIVE") || null;
    const capes = await Promise.all((p.capes || []).map(async (c) => ({ id: c.id, name: c.alias || "Umhang", active: c.state === "ACTIVE", image: await texture(c.url) })));
    return {
      name: p.name,
      uuid: p.id,
      skin: active ? { image: await texture(active.url), variant: String(active.variant || "CLASSIC").toLowerCase() === "slim" ? "slim" : "classic", url: textureUrl(active.url) } : null,
      capes,
    };
  }

  /** Skin hochladen (PNG-Daten) */
  async function upload(token, png, variant) {
    checkSkinPng(png);
    const form = new FormData();
    form.append("variant", variant === "slim" ? "slim" : "classic");
    form.append("file", new Blob([png], { type: "image/png" }), "skin.png");
    await call(token, "POST", `${API}/skins`, form);
  }

  /** Standard-Skin (Steve/Alex) */
  async function reset(token) {
    await call(token, "DELETE", `${API}/skins/active`);
  }

  /** Umhang tragen (id) oder ablegen (null) */
  async function cape(token, id) {
    if (id) await call(token, "PUT", `${API}/capes/active`, JSON.stringify({ capeId: String(id) }), { "Content-Type": "application/json" });
    else await call(token, "DELETE", `${API}/capes/active`);
  }

  /** Skin eines anderen Spielers holen (für „Von Spieler übernehmen“): PNG + Modell */
  async function ofPlayer(name) {
    const clean = String(name || "").trim();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(clean)) throw new Error("Ungültiger Spielername");
    const r = await fetch(`https://api.mojang.com/users/profiles/minecraft/${clean}`);
    if (r.status === 404 || r.status === 204) throw new Error(`Spieler „${clean}“ gibt es nicht`);
    if (!r.ok) throw new Error(`Mojang: Fehler ${r.status}`);
    const { id, name: realName } = await r.json();
    const s = await fetch(`https://sessionserver.mojang.com/session/minecraft/profile/${id}`);
    if (!s.ok) throw new Error(`Mojang: Fehler ${s.status}`);
    const prop = ((await s.json()).properties || []).find((p) => p.name === "textures");
    const textures = prop ? JSON.parse(Buffer.from(prop.value, "base64").toString("utf8")).textures : {};
    const skin = textures && textures.SKIN;
    const url = skin && textureUrl(skin.url);
    if (!url) throw new Error(`${realName} hat den Standard-Skin`);
    const png = Buffer.from(await (await fetch(url)).arrayBuffer());
    checkSkinPng(png);
    return { name: realName, png, variant: skin.metadata && skin.metadata.model === "slim" ? "slim" : "classic" };
  }

  // ------------------------------------------------------------------------------------------ Bibliothek

  const indexFile = () => path.join(libraryDir, "library.json");

  function readIndex() {
    try {
      const list = JSON.parse(fs.readFileSync(indexFile(), "utf8"));
      return Array.isArray(list) ? list : [];
    } catch {
      return [];
    }
  }

  async function writeIndex(list) {
    await fsp.mkdir(libraryDir, { recursive: true });
    await fsp.writeFile(indexFile(), JSON.stringify(list, null, 2));
  }

  /** Skin in die Bibliothek legen (gleiches Bild nur einmal; Name/Modell werden aktualisiert) */
  async function save(png, name, variant) {
    checkSkinPng(png);
    const id = crypto.createHash("sha1").update(png).digest("hex").slice(0, 16);
    await fsp.mkdir(libraryDir, { recursive: true });
    await fsp.writeFile(path.join(libraryDir, `${id}.png`), png);
    const list = readIndex().filter((e) => e.id !== id);
    list.unshift({ id, name: String(name || "Skin").slice(0, 32), variant: variant === "slim" ? "slim" : "classic", added: Date.now() });
    await writeIndex(list.slice(0, 60));
    return id;
  }

  async function library() {
    const out = [];
    for (const e of readIndex()) {
      try {
        const png = await fsp.readFile(path.join(libraryDir, `${e.id}.png`));
        out.push({ ...e, image: `data:image/png;base64,${png.toString("base64")}` });
      } catch {
        // Datei fehlt
      }
    }
    return out;
  }

  async function libraryPng(id) {
    if (!/^[0-9a-f]{16}$/.test(String(id))) throw new Error("Unbekannter Skin");
    const entry = readIndex().find((e) => e.id === id);
    if (!entry) throw new Error("Unbekannter Skin");
    return { png: await fsp.readFile(path.join(libraryDir, `${id}.png`)), entry };
  }

  async function remove(id) {
    if (!/^[0-9a-f]{16}$/.test(String(id))) return;
    await writeIndex(readIndex().filter((e) => e.id !== id));
    await fsp.rm(path.join(libraryDir, `${id}.png`), { force: true });
  }

  async function rename(id, name) {
    const list = readIndex();
    const entry = list.find((e) => e.id === id);
    if (entry) entry.name = String(name || "Skin").slice(0, 32);
    await writeIndex(list);
  }

  return { profile, upload, reset, cape, ofPlayer, save, library, libraryPng, remove, rename };
}

module.exports = { createSkins, checkSkinPng, textureUrl };
