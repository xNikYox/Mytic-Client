// Kopf (Gesicht 8×8 plus Hut-Ebene) aus einem Minecraft-Skin lesen. Ergebnis: 64 Pixel als Hex-Text "AARRGGBB…",
// damit Launcher und Mod den Kopf ohne Textur-Laden selbst zeichnen können. Läuft in Node 22 und in Cloudflare Workers.

async function inflate(bytes) {
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a);
  const pb = Math.abs(p - b);
  const pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  return pb <= pc ? b : c;
}

/** Einfacher PNG-Leser für Skins (8 Bit, RGBA/RGB/Palette, ohne Interlacing). Gibt { width, height, rgba } zurück. */
async function decodePng(bytes) {
  const data = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  const view = new DataView(data.buffer, data.byteOffset, data.byteLength);
  if (view.getUint32(0) !== 0x89504e47) throw new Error("kein PNG");
  let pos = 8;
  let width = 0;
  let height = 0;
  let colorType = 0;
  let palette = null;
  let alpha = null;
  const idat = [];
  while (pos < data.length) {
    const length = view.getUint32(pos);
    const type = String.fromCharCode(...data.subarray(pos + 4, pos + 8));
    const chunk = data.subarray(pos + 8, pos + 8 + length);
    if (type === "IHDR") {
      width = view.getUint32(pos + 8);
      height = view.getUint32(pos + 12);
      if (chunk[8] !== 8 || chunk[12] !== 0) throw new Error("PNG-Format nicht unterstützt");
      colorType = chunk[9];
    } else if (type === "PLTE") palette = chunk;
    else if (type === "tRNS") alpha = chunk;
    else if (type === "IDAT") idat.push(chunk);
    else if (type === "IEND") break;
    pos += 12 + length;
  }
  const channels = { 6: 4, 2: 3, 3: 1, 4: 2, 0: 1 }[colorType];
  if (!channels || width > 1024 || height > 1024) throw new Error("PNG-Format nicht unterstützt");
  const joined = new Uint8Array(idat.reduce((n, c) => n + c.length, 0));
  let offset = 0;
  for (const c of idat) {
    joined.set(c, offset);
    offset += c.length;
  }
  const raw = await inflate(joined);
  const stride = width * channels;
  const pixels = new Uint8Array(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= channels ? pixels[y * stride + x - channels] : 0;
      const b = y > 0 ? pixels[(y - 1) * stride + x] : 0;
      const c = x >= channels && y > 0 ? pixels[(y - 1) * stride + x - channels] : 0;
      let v = line[x];
      if (filter === 1) v += a;
      else if (filter === 2) v += b;
      else if (filter === 3) v += (a + b) >> 1;
      else if (filter === 4) v += paeth(a, b, c);
      pixels[y * stride + x] = v & 255;
    }
  }
  const rgba = new Uint8Array(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    const s = i * channels;
    let r;
    let g;
    let b;
    let a = 255;
    if (colorType === 6) [r, g, b, a] = pixels.subarray(s, s + 4);
    else if (colorType === 2) [r, g, b] = pixels.subarray(s, s + 3);
    else if (colorType === 3) {
      const idx = pixels[s];
      [r, g, b] = palette ? palette.subarray(idx * 3, idx * 3 + 3) : [0, 0, 0];
      if (alpha && idx < alpha.length) a = alpha[idx];
    } else if (colorType === 4) {
      r = g = b = pixels[s];
      a = pixels[s + 1];
    } else r = g = b = pixels[s];
    rgba.set([r, g, b, a], i * 4);
  }
  return { width, height, rgba };
}

/** Gesicht (8,8) mit Hut-Ebene (40,8) zu 64 Pixeln "AARRGGBB" zusammensetzen. */
function headFromImage({ width, height, rgba }) {
  const at = (x, y) => {
    const i = (y * width + x) * 4;
    return [rgba[i], rgba[i + 1], rgba[i + 2], rgba[i + 3]];
  };
  // alte 64×32-Skins: Hut-Ebene ohne durchsichtige Pixel gilt als leer (wie in Minecraft)
  let hat = true;
  if (height === 32) {
    hat = false;
    for (let y = 0; y < 16 && !hat; y++) for (let x = 32; x < 64; x++) if (at(x, y)[3] < 128) hat = true;
  }
  let out = "";
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      let [r, g, b] = at(8 + x, 8 + y);
      const [hr, hg, hb, ha] = at(40 + x, 8 + y);
      if (hat && ha > 0) {
        const t = ha / 255;
        r = Math.round(hr * t + r * (1 - t));
        g = Math.round(hg * t + g * (1 - t));
        b = Math.round(hb * t + b * (1 - t));
      }
      out += [255, r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("");
    }
  }
  return out.toUpperCase();
}

async function headFromPng(bytes) {
  return headFromImage(await decodePng(bytes));
}

export { decodePng, headFromImage, headFromPng };
