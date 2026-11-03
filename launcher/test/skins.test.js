// Skins: PNG-Prüfung, nur Mojang-Texturen, Aufrufe an die Minecraft-Dienste (nachgebaut), Bibliothek.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { createSkins, checkSkinPng, textureUrl } = require("../src/core/skins");

function png(width, height, extra = 0) {
  const b = Buffer.alloc(33 + extra);
  b.writeUInt32BE(0x89504e47, 0);
  b.write("\r\n\x1a\n", 4, "binary");
  b.writeUInt32BE(13, 8);
  b.write("IHDR", 12, "ascii");
  b.writeUInt32BE(width, 16);
  b.writeUInt32BE(height, 20);
  return b;
}

test("Skin-PNG: 64×64 und 64×32 erlaubt, sonst verständlicher Fehler", () => {
  assert.deepEqual(checkSkinPng(png(64, 64)), { width: 64, height: 64 });
  assert.deepEqual(checkSkinPng(png(64, 32)), { width: 64, height: 32 });
  assert.throws(() => checkSkinPng(png(128, 128)), /64×64/);
  assert.throws(() => checkSkinPng(Buffer.from("not a png at all, definitely not a png")), /PNG/);
  assert.throws(() => checkSkinPng(png(64, 64, 300 * 1024)), /256 KB/);
});

test("Texturen nur von textures.minecraft.net, immer https", () => {
  assert.equal(textureUrl("http://textures.minecraft.net/texture/abc"), "https://textures.minecraft.net/texture/abc");
  assert.equal(textureUrl("https://evil.example/texture/abc"), null);
  assert.equal(textureUrl("javascript:alert(1)"), null);
});

function fakeFetch(routes) {
  const calls = [];
  const fetch = async (url, options = {}) => {
    calls.push({ url, method: options.method || "GET", headers: options.headers || {}, body: options.body });
    const route = routes[`${options.method || "GET"} ${url}`] || routes[url];
    if (!route) return { ok: false, status: 404, json: async () => ({}) };
    const { status = 200, json = {}, bytes } = route;
    return { ok: status < 400, status, json: async () => json, arrayBuffer: async () => bytes || new ArrayBuffer(0) };
  };
  return { fetch, calls };
}

test("Profil: aktiver Skin und Umhänge als Bild, Token im Kopf", async () => {
  const tex = png(64, 64);
  const { fetch, calls } = fakeFetch({
    "GET https://api.minecraftservices.com/minecraft/profile": { json: { id: "u1", name: "Alpha",
      skins: [{ state: "INACTIVE", url: "http://textures.minecraft.net/texture/old" }, { state: "ACTIVE", url: "http://textures.minecraft.net/texture/new", variant: "SLIM" }],
      capes: [{ id: "c1", state: "ACTIVE", alias: "Migrator", url: "http://textures.minecraft.net/texture/cape" }] } },
    "https://textures.minecraft.net/texture/new": { bytes: tex },
    "https://textures.minecraft.net/texture/cape": { bytes: tex },
  });
  const skins = createSkins({ fetch, libraryDir: fs.mkdtempSync(path.join(os.tmpdir(), "skins-")) });
  const p = await skins.profile("TOKEN");
  assert.equal(p.name, "Alpha");
  assert.equal(p.skin.variant, "slim");
  assert.match(p.skin.image, /^data:image\/png;base64,/);
  assert.deepEqual(p.capes.map((c) => [c.id, c.name, c.active]), [["c1", "Migrator", true]]);
  assert.equal(calls[0].headers.Authorization, "Bearer TOKEN");
});

test("Hochladen, Zurücksetzen, Umhang; 429 wird verständlich", async () => {
  const { fetch, calls } = fakeFetch({
    "POST https://api.minecraftservices.com/minecraft/profile/skins": { json: {} },
    "DELETE https://api.minecraftservices.com/minecraft/profile/skins/active": { json: {} },
    "PUT https://api.minecraftservices.com/minecraft/profile/capes/active": { status: 429, json: {} },
  });
  const skins = createSkins({ fetch, libraryDir: os.tmpdir() });
  await skins.upload("T", png(64, 64), "slim");
  const form = calls[0].body;
  assert.equal(form.get("variant"), "slim");
  assert.equal(form.get("file").type, "image/png");
  await assert.rejects(skins.upload("T", png(32, 32), "classic"), /64×64/);
  await skins.reset("T");
  assert.equal(calls.at(-1).method, "DELETE");
  await assert.rejects(skins.cape("T", "c1"), /Zu viele Änderungen/);
});

test("Bibliothek: gleiches Bild nur einmal, löschen, Umbenennen, ungültige ids", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "skins-"));
  const skins = createSkins({ fetch: async () => ({}), libraryDir: dir });
  const a = await skins.save(png(64, 64), "Erster", "classic");
  const again = await skins.save(png(64, 64), "Neu benannt", "slim");
  assert.equal(a, again);
  let list = await skins.library();
  assert.equal(list.length, 1);
  assert.equal(list[0].name, "Neu benannt");
  assert.equal(list[0].variant, "slim");
  await skins.rename(a, "Dritter");
  assert.equal((await skins.library())[0].name, "Dritter");
  await assert.rejects(skins.libraryPng("../../etc/passwd"), /Unbekannt/);
  await skins.remove(a);
  list = await skins.library();
  assert.equal(list.length, 0);
  assert.ok(!fs.existsSync(path.join(dir, `${a}.png`)));
});
