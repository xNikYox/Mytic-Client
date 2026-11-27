"""Accessoires „Kopf & Hals“ – Winterzauber II.

Gleiches Format wie cosmetics/accessories.py (Box-Modelle, Minecraft-Box-Layout, 2 Texel je Modell-Pixel im Katalog),
aber die PNG-Datei hat die DOPPELTE Auflösung (HD = 2 → 4 Bildpunkte je Modell-Pixel). Minecraft rechnet die UVs über die
im Katalog angegebene Texturgröße (texture: [w, h]) – ein größeres Bild wird dadurch einfach feiner abgetastet
(1.21+: LayerDefinition mit texW/texH; 1.8.9: ModelBase.textureWidth/Height + DynamicTexture). Keine Mod-Änderung nötig.

Bemalt wird jeder Bildpunkt über seine 3D-Position auf der Box (Muster laufen so über Boxgrenzen weiter) – mit
Strickmaschen, Fell-Rändern (Cutout), Metallglanz, Kanten-Licht und Dithering.
Boxen ohne Spiegelung; Größen immer Vielfache von 0,5 (dann gibt es in 1.8.9 keine Zugabe-Probleme).
Zusatzfeld je Box (Mods ab 2.60, ältere ignorieren es): "blink": Gruppe 0..2 → leuchtende Box blinkt der Reihe nach.

Aufruf: xmasacc2.py <ausgabeordner> (build.py: build/acc, Katalog-Werte in accessories2.json)
"""
import json, math, os, subprocess, sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import accessories as base  # render_icon, faces_of

XOUT = sys.argv[1] if len(sys.argv) > 1 else "acc"
TS, HD = 2, 2


def cl(v, a=0.0, b=1.0): return a if v < a else b if v > b else v
def lerp(a, b, t): t = cl(t); return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def hx(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
def mul(c, k): return tuple(cl(c[i] * k, 0, 255) for i in range(3))
def add(a, b, k=1.0): return tuple(min(255, a[i] + b[i] * k) for i in range(3))
def rnd(i): v = math.sin(i * 12.9898 + 78.233) * 43758.5453; return v - math.floor(v)
def h3(x, y, z, s=0): return rnd(x * 157.31 + y * 311.7 + z * 74.93 + s * 19.19)
def ramp(stops, v):
    v = cl(v)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if v <= p1: return lerp(c0, c1, (v - p0) / max(1e-6, p1 - p0))
    return stops[-1][1]
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
def dith(i, j): return (BAYER[j % 4][i % 4] + 0.5) / 16

# Palette Winterzauber II
RED = [hx('#5a0a16'), hx('#7a0f1e'), hx('#b3172c'), hx('#d92a3f'), hx('#ff5a64')]
GRN = [hx('#0a2a1f'), hx('#0f3b2c'), hx('#1d5a3c'), hx('#2f7d4c'), hx('#4fa35e'), hx('#7cc779')]
SNOW = [hx('#8fa6c8'), hx('#a9bfdf'), hx('#d6e4f7'), hx('#f4f8ff'), hx('#ffffff')]
GOLD = [hx('#5a340a'), hx('#7a4a10'), hx('#b8801c'), hx('#e8b23a'), hx('#ffd96a'), hx('#fff3b8')]
SILV = [hx('#3a404c'), hx('#6a7282'), hx('#a9b3c4'), hx('#dfe6f0'), hx('#ffffff')]
CLEAR = (0, 0, 0, 0)


def B(name, paint, frm, size, **kw):
    return dict(name=name, paint=paint, frm=frm, size=size, **kw)


# ------------------------------------------------------------------ Modelle (Modell-Pixel: y nach oben, Füße 0, Hals 24, Kopf 24..32, Gesicht +z)
ACC = {}

ACC["elfenmuetze"] = dict(name="Elfenmütze", slot="kopf", boxes=[
    B("krempe", "elfband", [-4.5, 30, -4.5], [9, 1.5, 9]),
    B("zacken_v", "zigzag", [-4.5, 29, 4], [9, 1, 0.5]),
    B("zacken_h", "zigzag", [-4.5, 29, -4.5], [9, 1, 0.5]),
    B("zacken_l", "zigzag", [4, 29, -4], [0.5, 1, 8]),
    B("zacken_r", "zigzag", [-4.5, 29, -4], [0.5, 1, 8]),
    B("stufe1", "felt", [-4.25, 31.5, -4.25], [8.5, 2, 8.5]),
    B("stufe2", "felt", [-3.5, 33.5, -3.75], [7, 2, 7]),
    B("stufe3", "felt", [-2.75, 35.5, -3.5], [5.5, 1.5, 5.5]),
    B("stufe4", "felt", [-2, 37, -4], [4, 1.5, 4.5]),
    B("zipfel1", "felt", [-1.5, 38, -6], [3, 1.5, 3]),
    B("zipfel2", "felt", [-1.25, 37.5, -7.5], [2.5, 1.5, 2]),
    B("zipfel3", "felt", [-1, 36.5, -8.5], [2, 1.5, 1.5]),
    B("gloeckchen", "bell", [-1, 34.5, -9.5], [2, 2, 2]),
    B("kloeppel", "darkgold", [-0.5, 34, -9], [1, 0.5, 1]),
])

ACC["strickmuetze"] = dict(name="Strickmütze mit Bommel", slot="kopf", boxes=[
    B("umschlag", "rib", [-4.75, 29.5, -4.75], [9.5, 2.5, 9.5]),
    B("koerper", "knitcap", [-4.5, 32, -4.5], [9, 2.5, 9]),
    B("oben", "knitcap", [-4, 34.5, -4], [8, 1.5, 8]),
    B("deckel", "knitcap", [-3, 36, -3], [6, 0.5, 6]),
    B("bommel", "pom", [-2, 36.5, -2], [4, 3.5, 4]),
    B("bommel_x", "pom", [-2.5, 37, -1.5], [5, 2.5, 3]),
    B("bommel_z", "pom", [-1.5, 37, -2.5], [3, 2.5, 5]),
    B("bommel_o", "pom", [-1.5, 40, -1.5], [3, 0.5, 3]),
])

def muff(sx):
    x = (lambda a, w: a) if sx > 0 else (lambda a, w: -a - w)
    tag = "l" if sx > 0 else "r"
    return [B("muschel_" + tag, "plush", [x(4.25, 1.75), 25.5, -2], [1.75, 4, 4]),
            B("muschel2_" + tag, "plush", [x(4.25, 1.75), 26, -2.5], [1.75, 3, 5]),
            B("muschel3_" + tag, "plush", [x(4.25, 1.75), 25, -1.5], [1.75, 5, 3]),
            B("kissen_" + tag, "plush", [x(6, 0.5), 26, -1.5], [0.5, 3, 3]),
            B("buegel_" + tag, "band", [x(4, 0.5), 29, -0.75], [0.5, 3, 1.5])]
ACC["ohrenschuetzer"] = dict(name="Ohrenschützer", slot="kopf", boxes=[
    B("buegel", "band", [-4.5, 32, -0.75], [9, 0.5, 1.5])] + muff(1) + muff(-1))

def halo_ring():
    """Ring auf dem 0,5-Raster (Boxen haben keine Drehung): Läufe entlang x je z-Zeile zu Boxen zusammenfassen."""
    out, r0, r1, y = [], 3.0, 4.5, 35
    for k in range(-9, 9):
        z = k * 0.5
        run_start = None
        for i in range(-9, 10):
            x = i * 0.5
            inside = i < 9 and r0 <= math.hypot(x + 0.25, z + 0.25) <= r1
            if inside and run_start is None: run_start = x
            if not inside and run_start is not None:
                out.append(B(f"ring{len(out)}", "halo", [run_start, y, z], [x - run_start, 1, 0.5], emissive=True))
                run_start = None
    return out
ACC["heiligenschein"] = dict(name="Heiligenschein", slot="kopf", boxes=halo_ring())

ACC["mistelreif"] = dict(name="Mistelzweig-Haarreif", slot="kopf", boxes=[
    B("reif", "ribbon", [-4.5, 32, -0.5], [9, 0.5, 1]),
    B("reif_l", "ribbon", [4, 28, -0.5], [0.5, 4, 1]),
    B("reif_r", "ribbon", [-4.5, 28, -0.5], [0.5, 4, 1]),
    B("feder1", "wire", [-0.25, 32.5, -0.25], [0.5, 2.5, 0.5]),
    B("feder2", "wire", [0, 35, -0.25], [0.5, 2.5, 0.5]),
    B("schleife", "velvet", [-0.5, 37.5, -0.5], [1.5, 1, 1]),
    B("schlaufe_l", "velvet", [1, 37.5, -0.25], [1.5, 1.5, 0.5]),
    B("schlaufe_r", "velvet", [-2, 37.5, -0.25], [1.5, 1.5, 0.5]),
    B("blatt_l", "leaf", [-4.5, 38, -0.25], [4.5, 2, 0.5], axis="x", flip=True),
    B("blatt_r", "leaf", [0.5, 38, -0.25], [4.5, 2, 0.5], axis="x"),
    B("blatt_v", "leaf", [-0.25, 38.5, 0.5], [0.5, 1.5, 3.5], axis="z"),
    B("blatt_h", "leaf", [-0.25, 38.5, -4], [0.5, 1.5, 3.5], axis="z", flip=True),
    B("beere1", "berry", [-0.5, 36.5, 0.25], [1, 1, 1]),
    B("beere2", "berry", [0.25, 36, -0.75], [1, 1, 1]),
    B("beere3", "berry", [-1, 36, -0.5], [1, 1, 1]),
])

ACC["lamettaboa"] = dict(name="Lametta-Boa", slot="hals", boxes=[
    B("vorne", "tinsel", [-4.75, 21.5, 2], [9.5, 3, 1.25]),
    B("hinten", "tinsel", [-4.75, 21.5, -3.25], [9.5, 3, 1.25]),
    B("links", "tinsel", [4, 21.5, -2], [1.25, 3, 4]),
    B("rechts", "tinsel", [-5.25, 21.5, -2], [1.25, 3, 4]),
    B("ende1", "tinsel", [1, 15, 2.5], [2, 6.5, 1.25]),
    B("ende2", "tinsel", [-3, 16.5, 2.5], [2, 5, 1.25]),
    B("flaum_v", "tinselshell", [-5.25, 21, 3.25], [10.5, 4, 0.5]),
    B("flaum_l", "tinselshell", [5.25, 21, -3.25], [0.5, 4, 6.5]),
    B("flaum_r", "tinselshell", [-5.75, 21, -3.25], [0.5, 4, 6.5]),
    B("flaum_e1", "tinselshell", [0.5, 14.5, 3.75], [3, 7, 0.5]),
    B("flaum_e2", "tinselshell", [-3.5, 16, 3.75], [3, 5.5, 0.5]),
])

BULBS = [("#ff3b4e", [-4.25, 21.5, 2.75]), ("#ffd23f", [-2, 21.5, 2.75]), ("#3fb6ff", [-0.5, 21.5, 3.5]), ("#5bff7a", [3.5, 21.5, 2.75]),
         ("#ff7ad9", [4.75, 21.5, 0.5]), ("#ffd23f", [4.75, 21.5, -2]), ("#3fb6ff", [-5.25, 21.5, 0.5]), ("#ff3b4e", [-5.25, 21.5, -2]),
         ("#5bff7a", [1.5, 17.5, 3.5]), ("#ff7ad9", [2, 15, 3.5]), ("#ffd23f", [1.25, 12.5, 3.5]), ("#3fb6ff", [-1, 3.5 + 13, 3])]
ACC["lichterschal"] = dict(name="Lichterketten-Schal", slot="hals", boxes=[
    B("ring", "knitscarf", [-4.75, 21, -2.75], [9.5, 3, 5.5]),
    B("knoten", "knitscarf", [0.5, 19.5, 2.25], [3, 2.5, 1.25]),
    B("ende1", "knitscarf", [1, 12.5, 2.5], [2.5, 7, 1], fringe=True),
    B("ende2", "knitscarf", [-1.5, 14.5, 2.25], [2.5, 5.5, 0.75], fringe=True),
] + [B(f"lampe{i}", "bulb", p, [1, 1, 0.5], color=c, emissive=True, blink=i % 3) for i, (c, p) in enumerate(BULBS)])


# ------------------------------------------------------------------ Bemalung je Bildpunkt
def knit_cell(a, y):
    """Strickmasche: Zelle 1x1 Modell-Pixel; (cx, cy) Zelle, Helligkeit einer V-Masche."""
    cx, cy = math.floor(a), math.floor(y)
    fa, fy = a - cx, y - cy
    # zwei schräge Halbmaschen: links "\" und rechts "/" – Mitte und Zeilenrand dunkler
    half = fa if fa < 0.5 else 1 - fa  # 0 am Rand, 0.5 in der Mitte
    lobe = 1 - abs((1 - fy) - (half * 2)) * 0.9  # V-Form
    k = 0.62 + 0.5 * cl(lobe)
    if half > 0.4: k *= 0.72  # Rinne in der Mitte
    if fy < 0.2: k *= 0.7  # Zeilenfuge
    return cx, cy, k


def along(p, n):
    """Koordinate quer über die Fläche (für Maschen/Strähnen): x vorne/hinten, z an den Seiten."""
    x, y, z = p
    if n[0] != 0: return z * (-n[0])
    if n[2] != 0: return x * n[2]
    return x


def bevel(c, ctx, light=0.12, dark=0.16):
    """Kanten-Licht: oben/links heller, unten/rechts dunkler (wie gefaste Kanten bei Minecraft-Modellen in HD)."""
    s, t, wm, hm = ctx["s"], ctx["t"], ctx["wm"], ctx["hm"]
    e_top, e_bot = t * hm, (1 - t) * hm
    e_l, e_r = s * wm, (1 - s) * wm
    if min(e_top, e_l) < 0.25: c = add(c, (255, 255, 255), light)
    elif min(e_bot, e_r) < 0.25: c = mul(c, 1 - dark)
    return c


def paint(kind, ctx):
    p, n, i, j, box = ctx["p"], ctx["n"], ctx["i"], ctx["j"], ctx["box"]
    x, y, z = p
    r = h3(round(x * 4), round(y * 4), round(z * 4), ctx["seed"])
    d = dith(i, j)
    up = n[1] > 0
    if kind == "felt":  # Filz grün mit roten Schrägstreifen und goldener Steppnaht
        a = along(p, n)
        c = ramp([(0, GRN[2]), (0.5, GRN[3]), (1, GRN[4])], 0.35 + 0.35 * r + (0.15 if up else 0) + (y - 31) * 0.03)
        stripe = ((a + y * 1.0 + (z if up else 0)) % 3.0)
        if stripe < 0.6: c = lerp(RED[2], RED[3], r) if stripe > 0.12 else RED[1]
        if (y % 2.0) < 0.25 and not up and (i + j) % 2 == 0: c = lerp(c, GOLD[4], 0.6)  # Steppnaht
        if r > 0.93: c = mul(c, 0.86)
        return bevel(c, ctx, 0.1, 0.18) + (255,)
    if kind == "elfband":
        c = ramp([(0, RED[1]), (0.6, RED[2]), (1, RED[3])], 0.4 + 0.4 * r)
        t = ctx["t"]
        if 0.18 < t < 0.32 or 0.68 < t < 0.82: c = lerp(GOLD[3], GOLD[4], r)
        if (0.32 <= t <= 0.68) and int((along(p, n) + 20) * 2) % 2 == 0 and not up and 0.42 < t < 0.58: c = GOLD[5]
        return bevel(c, ctx) + (255,)
    if kind == "zigzag":
        a = along(p, n) if n[1] == 0 else x
        k = (a * 1.0) % 1.0
        depth = 1 - abs(k - 0.5) * 2  # 0 am Rand, 1 in der Mitte → Spitze nach unten
        if ctx["t"] > 0.25 + 0.75 * depth and n[1] == 0: return CLEAR
        cid = int(math.floor(a)) % 2
        c = (RED if cid else GRN)[2 if r < 0.5 else 3]
        if ctx["t"] < 0.2: c = mul(c, 0.8)
        return c + (255,)
    if kind in ("bell", "darkgold"):
        ny = n[1]
        t = 0.55 + 0.3 * ny + 0.2 * (n[0] + n[2]) * 0.5 + (r - 0.5) * 0.12 + (d - 0.5) * 0.15
        c = ramp([(0, GOLD[1]), (0.35, GOLD[2]), (0.65, GOLD[3]), (0.85, GOLD[4]), (1, GOLD[5])], t)
        if kind == "darkgold": c = mul(c, 0.55)
        if kind == "bell" and n[1] == 0 and 0.55 < ctx["t"] < 0.7: c = GOLD[1]  # Schlitz
        if kind == "bell" and ctx["s"] < 0.3 and ctx["t"] < 0.4 and n[1] == 0: c = GOLD[5]  # Glanz
        return c + (255,)
    if kind == "rib":  # Bündchen: Rippen senkrecht, rot mit weißen Ringeln
        a = along(p, n) if n[1] == 0 else math.atan2(z, x) * 4
        rib = (a * 2) % 1.0
        k = 0.7 + 0.45 * math.sin(rib * math.pi)
        band = int((y - 29.5) * 2)
        c = RED[3] if band in (1, 3) else SNOW[3]
        if up: c = SNOW[2]
        c = mul(c, k * (0.95 + 0.1 * r))
        return c + (255,)
    if kind in ("knitcap", "knitscarf"):
        if n[1] != 0:  # Oberseiten: Maschen im Kreis → einfach Kacheln nach x/z
            cx, cy, k = knit_cell(x + 10, z + 10)
        else:
            cx, cy, k = knit_cell(along(p, n) + 10, y * 1.0)
        if kind == "knitcap":
            row = cy - 32 if n[1] == 0 else 9
            # Norweger-Band (Periode 6 Maschen): Kreuz-Schneeflocken und Punkte auf Rot
            motif = {0: "W.....", 1: "..W...", 2: ".WWW..", 3: "..W..W"}.get(row, "......")
            col = SNOW[3] if motif[cx % 6] == "W" else RED[2]
        else:
            stripe = (cy if n[1] == 0 else cx) % 6
            col = [GRN[3], GRN[3], RED[2], SNOW[3], RED[2], GRN[3]][stripe]
            if box.get("fringe") and n[1] == 0 and ctx["t"] > 0.86:
                return CLEAR if (i // 2) % 2 else SNOW[3] + (255,)
            # Draht der Lichterkette
            if box["name"] == "ring" and n[1] == 0 and abs(y - (22.6 + 0.25 * math.sin(along(p, n) * 1.3))) < 0.14: return hx('#123a22') + (255,)
            if box["name"] == "ende1" and n[2] > 0 and abs(x - (1.75 + 0.3 * math.sin(y * 1.1))) < 0.14: return hx('#123a22') + (255,)
        c = mul(col, k * (0.94 + 0.12 * r))
        return bevel(c, ctx, 0.06, 0.14) + (255,)
    if kind == "pom":  # Bommel: Wolle, Ränder ausgefranst (Cutout)
        e = min(ctx["s"] * ctx["wm"], (1 - ctx["s"]) * ctx["wm"], ctx["t"] * ctx["hm"], (1 - ctx["t"]) * ctx["hm"])
        if e < 0.3 and r < 0.45: return CLEAR
        c = ramp([(0, SNOW[1]), (0.5, SNOW[3]), (1, SNOW[4])], 0.25 + 0.6 * r + 0.2 * n[1])
        if r < 0.12: c = SNOW[0]
        return c + (255,)
    if kind == "plush":  # Plüsch-Ohrenwärmer: cremeweiß, Fell in Büscheln, ausgefranste Kanten
        e = min(ctx["s"] * ctx["wm"], (1 - ctx["s"]) * ctx["wm"], ctx["t"] * ctx["hm"], (1 - ctx["t"]) * ctx["hm"])
        if e < 0.26 and r < 0.4: return CLEAR
        tuft = h3(math.floor(x * 2), math.floor(y * 2), math.floor(z * 2), 7)
        c = ramp([(0, hx('#c9b9a8')), (0.4, hx('#efe4d6')), (0.8, hx('#fbf6ee')), (1, hx('#ffffff'))], 0.3 * tuft + 0.5 * r + 0.15 * (n[1] + 0.5))
        if (i + j * 3) % 7 == 0: c = mul(c, 0.88)
        return c + (255,)
    if kind == "band":  # roter Lackbügel mit Glanzlinie
        c = ramp([(0, RED[1]), (0.5, RED[2]), (1, RED[3])], 0.5 + 0.3 * n[1] + 0.1 * r)
        if n[1] == 0 and 0.2 < (ctx["s"] if ctx["wm"] < ctx["hm"] else ctx["t"]) < 0.35: c = lerp(c, RED[4], 0.8)
        if up and 0.3 < ctx["t"] < 0.45: c = lerp(c, (255, 220, 225), 0.6)
        return c + (255,)
    if kind == "halo":  # leuchtet (volle Helligkeit): Gold mit hellem Innenrand und wanderndem Glanz
        rad = math.hypot(x + 0.25, z + 0.25)
        t = cl((rad - 3.0) / 1.5)
        c = ramp([(0, GOLD[5]), (0.35, GOLD[4]), (0.75, GOLD[3]), (1, GOLD[2])], t + (d - 0.5) * 0.25)
        if n[1] < 0: c = mul(c, 0.8)
        ang = math.atan2(z, x)
        if (ang % (math.pi / 3)) < 0.12 and up: c = (255, 255, 235)
        return c + (255,)
    if kind == "ribbon":  # Haarreif: goldenes Band mit grünem Mittelstreifen
        t = ctx["t"] if ctx["hm"] < ctx["wm"] else ctx["s"]
        c = GRN[3] if 0.3 < t < 0.7 else GOLD[3]
        c = mul(c, 0.9 + 0.2 * r)
        return c + (255,)
    if kind == "wire":
        c = lerp(GOLD[2], GOLD[4], 0.5 + 0.5 * math.sin(y * 9))  # Spirale
        return c + (255,)
    if kind == "velvet":
        c = ramp([(0, RED[0]), (0.4, RED[1]), (0.8, RED[2]), (1, RED[3])], 0.45 + 0.35 * n[1] + 0.2 * r + (d - 0.5) * 0.2)
        if ctx["t"] < 0.2 and n[1] == 0: c = lerp(c, RED[4], 0.5)
        return c + (255,)
    if kind == "leaf":  # flaches Blatt (Cutout-Form), längs entlang box["axis"]
        ax = box["axis"]
        lo = box["frm"][0 if ax == "x" else 2]; ln = box["size"][0 if ax == "x" else 2]
        u = ((x if ax == "x" else z) - lo) / ln
        if box.get("flip"): u = 1 - u
        v = (y - box["frm"][1]) / box["size"][1] * 2 - 1
        half = 0.95 * math.sin(math.pi * cl(u) ** 0.8) * (1 - 0.15 * u)
        if n[1] != 0 or abs(v) > half or u < 0.05: return CLEAR
        c = ramp([(0, GRN[2]), (0.5, GRN[3]), (1, GRN[5])], 0.35 + 0.35 * (1 - abs(v)) + 0.25 * u * 0.5 + (r - 0.5) * 0.2)
        if abs(v) < 0.12: c = lerp(GRN[5], (230, 255, 200), 0.3)  # Mittelrippe
        if abs(v) > half - 0.2: c = mul(c, 0.78)
        return c + (255,)
    if kind == "berry":
        cx, cy, cz = (box["frm"][k] + box["size"][k] / 2 for k in range(3))
        dx, dy = (x - cx) * (n[2] or 1), y - cy
        c = ramp([(0, hx('#b9c4b0')), (0.5, hx('#eef2e6')), (1, hx('#ffffff'))], 0.6 + 0.4 * n[1] - 0.25 * abs(dx) - 0.2 * max(0, -dy) + (d - 0.5) * 0.2)
        if dx < -0.15 and dy > 0.1 and n[1] == 0: c = (255, 255, 255)
        return c + (255,)
    if kind in ("tinsel", "tinselshell"):  # Lametta: Strähnen senkrecht, silbern mit Gold/Rot, glitzernd
        a = along(p, n) if n[1] == 0 else x + z
        strand = math.floor(a * 4 + 100)
        sr = rnd(strand * 3.1 + ctx["seed"])
        col = SILV if sr < 0.62 else GOLD if sr < 0.85 else [RED[1], RED[1], RED[2], RED[3], RED[4]]
        glint = (y * 2.3 + sr * 7) % 1.0
        k = 0.25 + 0.7 * abs(math.sin(math.pi * glint)) + (0.3 if r > 0.9 else 0)
        c = ramp([(0, col[1]), (0.4, col[2]), (0.75, col[3]), (1, col[4])], k + (d - 0.5) * 0.18)
        if kind == "tinselshell":  # lose Strähnen davor: ganze Fäden, unterschiedlich lang
            top = box["frm"][1] + box["size"][1]
            if n[1] != 0 or sr > 0.5 or (top - y) > box["size"][1] * (0.45 + 0.55 * rnd(strand * 1.7 + 3)): return CLEAR
        else:
            e = min(ctx["s"] * ctx["wm"], (1 - ctx["s"]) * ctx["wm"], ctx["t"] * ctx["hm"], (1 - ctx["t"]) * ctx["hm"])
            if (e < 0.25 and r < 0.35) or (n[1] < 0 and r < 0.5): return CLEAR
        return c + (255,)
    if kind == "bulb":  # Glas-Lämpchen (leuchtet), Fassung oben dunkelgrün
        col = hx(box["color"])
        if ctx["t"] < 0.3 and n[1] == 0 or up: return hx('#1d4a2c') + (255,)
        c = lerp(col, (255, 255, 255), 0.15 + (0.45 if (ctx["s"] < 0.5 and ctx["t"] < 0.55) else 0))
        return c + (255,)
    raise ValueError(kind)


def build():
    os.makedirs(XOUT, exist_ok=True)
    meta = {}
    for aid, acc in ACC.items():
        # Platzieren wie cosmetics/accessories.py (Texel-Einheiten, TS=2)
        placed, x0, y0, rowh, tw = [], 0, 0, 0, 64
        while any(2 * (round(b["size"][0] * TS) + round(b["size"][2] * TS)) > tw for b in acc["boxes"]): tw *= 2
        for b in acc["boxes"]:
            w, h, d = (max(1, round(s * TS)) for s in b["size"])
            bw, bh = 2 * (w + d), h + d
            if x0 + bw > tw: x0, y0, rowh = 0, y0 + rowh, 0
            placed.append((b, x0, y0, w, h, d)); x0 += bw; rowh = max(rowh, bh)
        th = 1
        while th < y0 + rowh: th *= 2
        W, H = tw * HD, th * HD
        img = [[CLEAR] * W for _ in range(H)]
        boxes = []
        for bi, (b, u0, v0, w, h, d) in enumerate(placed):
            jb = {"from": b["frm"], "size": b["size"], "uv": [u0, v0], "tex": [w, h, d], "mirror": False}
            for corners, (fu, fv, fw, fh), nrm in base.faces_of(jb, (tw, th)):
                if fw == 0 or fh == 0: continue
                c0, c1, c3 = corners[0], corners[1], corners[3]
                wm = math.dist(c0, c1); hm = math.dist(c0, c3)
                FW, FH = fw * HD, fh * HD
                for jj in range(FH):
                    for ii in range(FW):
                        s, t = (ii + 0.5) / FW, (jj + 0.5) / FH
                        p = tuple(c0[k] + (c1[k] - c0[k]) * s + (c3[k] - c0[k]) * t for k in range(3))
                        ctx = dict(p=p, n=nrm, s=s, t=t, wm=wm, hm=hm, i=ii, j=jj, box=b, seed=bi + 1)
                        col = paint(b["paint"], ctx)
                        img[fv * HD + jj][fu * HD + ii] = tuple(int(cl(v, 0, 255)) for v in col)
            entry = {"name": b["name"], "from": b["frm"], "size": b["size"], "uv": [u0, v0], "tex": [w, h, d],
                     "mirror": False, "emissive": b.get("emissive", False)}
            if "blink" in b: entry["blink"] = b["blink"]
            boxes.append(entry)
        pam = os.path.join(XOUT, f"{aid}.pam")
        with open(pam, "wb") as fh_:
            fh_.write(f"P7\nWIDTH {W}\nHEIGHT {H}\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n".encode())
            for row in img: fh_.write(b"".join(bytes(c) for c in row))
        base.run([pam, "-depth", "8", f"PNG32:{XOUT}/{aid}.png"]); os.remove(pam)
        # Menü-Bild 128x128 wie heute (render_icon kennt nur ganze Texel → Boxen in Bildpunkt-Einheiten übergeben)
        icon_boxes = [dict(bx, uv=[bx["uv"][0] * HD, bx["uv"][1] * HD], tex=[v * HD for v in bx["tex"]]) for bx in boxes]
        base.render_icon(icon_boxes, img, acc["slot"], f"{XOUT}/{aid}_icon.png")
        meta[aid] = {"name": acc["name"], "slot": acc["slot"], "texture": [tw, th], "png": [W, H], "texelsPerPixel": TS, "hd": HD, "boxes": boxes}
        print("Accessoire", aid, f"Katalog {tw}x{th}, PNG {W}x{H},", len(boxes), "Boxen", flush=True)
    json.dump(meta, open(os.path.join(XOUT, "accessories2.json"), "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    build()
