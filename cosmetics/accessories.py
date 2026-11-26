"""Accessoires „Kopf & Hals“ (Winterzauber 2026): Box-Modelle mit Pixel-Textur (2 Texel je Modell-Pixel, Minecraft-Box-Layout).

Koordinaten in Modell-Pixeln wie im Vorschau-Viewer: y nach oben, Füße = 0, Hals = 24, Gesicht zeigt nach +z, +x = linke Seite
des Spielers. Die Mod rechnet das selbst ins Minecraft-Modell um (Kopf bzw. Oberkörper, Drehpunkt am Hals).
Ausgabe: <id>.png (Textur), <id>_icon.png (Vorschau fürs Menü, 3/4-Ansicht auf grauem Kopf) und accessories.json.
Aufruf: accessories.py <ausgabeordner>
"""
import json, math, os, subprocess, sys

XOUT = sys.argv[1] if len(sys.argv) > 1 else "acc"


def cl(v, a=0.0, b=1.0): return a if v < a else b if v > b else v
def lerp(a, b, t): t = cl(t); return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def hx(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
def add(a, b, k=1.0): return tuple(min(255, a[i] + b[i] * k) for i in range(3))
def rnd(i): v = math.sin(i * 12.9898 + 78.233) * 43758.5453; return v - math.floor(v)
def run(args): subprocess.run(["convert", *args], check=True)


TS = 2  # Texel je Modell-Pixel
def mirror(b):
    x, y, z = b["from"]; w = b["size"][0]
    return dict(b, name=b["name"] + "_r", **{"from": [-x - w, y, z]}, mirror=True)

FUR, RED, BROWN, LBROWN, NOSE, SCARF = "fur", "red", "brown", "lbrown", "nose", "scarf"
ACCESSORIES = {
    "weihnachtsmuetze": dict(name="Weihnachtsmütze", slot="kopf", boxes=[
        dict(name="rand", paint=FUR, **{"from": [-4.75, 30, -4.75]}, size=[9.5, 2.5, 9.5]),
        dict(name="kegel1", paint=RED, **{"from": [-4, 32.5, -4]}, size=[8, 3, 8]),
        dict(name="kegel2", paint=RED, **{"from": [-3, 35.5, -3.5]}, size=[6, 2.5, 6]),
        dict(name="kegel3", paint=RED, **{"from": [-1.75, 38, -4]}, size=[3.5, 2, 3.5]),
        dict(name="spitze", paint=RED, **{"from": [-1.25, 38.5, -6.5]}, size=[2.5, 1.5, 3]),
        dict(name="bommel", paint=FUR, **{"from": [-1.5, 36.5, -9]}, size=[3, 3, 3]),
    ]),
    "rentiergeweih": dict(name="Rentiergeweih", slot="kopf", boxes=[]),
    "rentiergeweih_nase": dict(name="Rentiergeweih + rote Nase", slot="kopf", boxes=[]),
    "schal": dict(name="Winterschal", slot="hals", boxes=[
        dict(name="ring", paint=SCARF, **{"from": [-4.6, 21.5, -2.6]}, size=[9.2, 2.5, 5.2]),
        dict(name="knoten", paint=SCARF, **{"from": [0.5, 19.5, 2.4]}, size=[3, 2.5, 1]),
        dict(name="ende", paint=SCARF, **{"from": [1, 13.5, 2.5]}, size=[2.5, 6.5, 1], fringe=True),
    ]),
}
ANTLER = [
    dict(name="stamm", paint=BROWN, **{"from": [2, 32, -0.5]}, size=[1, 3, 1]),
    dict(name="ast", paint=BROWN, **{"from": [2.5, 34.5, -0.5]}, size=[3, 1, 1]),
    dict(name="aussen", paint=BROWN, **{"from": [5, 35, -0.5]}, size=[1, 4, 1]),
    dict(name="innen", paint=BROWN, **{"from": [2.5, 35.5, -0.5]}, size=[1, 2.5, 1]),
    dict(name="zacke", paint=BROWN, **{"from": [5.5, 37, -0.5]}, size=[1.5, 1, 1]),
    dict(name="zacke2", paint=BROWN, **{"from": [4, 38.5, -0.5]}, size=[1, 1, 1]),
    dict(name="ohr", paint=LBROWN, **{"from": [4, 29, -0.5]}, size=[2, 1, 1]),
]
ACCESSORIES["rentiergeweih"]["boxes"] = ANTLER + [mirror(b) for b in ANTLER]
ACCESSORIES["rentiergeweih_nase"]["boxes"] = ANTLER + [mirror(b) for b in ANTLER] + [
    dict(name="nase", paint=NOSE, **{"from": [-1, 26.25, 4]}, size=[2, 1.5, 1], emissive=True)]

def paint(kind, face, u, v, fw, fh, box, seed):
    """Farbe eines Texels (u, v im Face, 0..fw-1)."""
    n = rnd(seed * 977 + u * 31 + v * 17)
    if kind == FUR:
        c = lerp(hx('#e9eef5'), hx('#ffffff'), n)
        if n < 0.18: c = hx('#cfd6e2')
    elif kind == RED:
        c = lerp(hx('#b3132b'), hx('#d92a3f'), n)
        if face in ("north", "east", "west", "south") and v >= fh - 1: c = lerp(c, (0, 0, 0), 0.25)
        if face == "up": c = add(c, (40, 10, 10))
    elif kind == BROWN:
        c = lerp(hx('#6b4423'), hx('#8a5a32'), n)
        if face == "up": c = hx('#a87448')
    elif kind == LBROWN:
        c = lerp(hx('#9a6a40'), hx('#b0804f'), n)
        if face == "south" and 0 < u < fw - 1: c = hx('#e8a0a8')
    elif kind == NOSE:
        c = lerp(hx('#e01a2a'), hx('#ff3b4e'), n)
        if u == 1 and v == 0: c = hx('#ffd0d4')
    elif kind == SCARF:
        # Streifen quer: rot / weiß / grün; Fransen am Ende
        stripe = (v // 2) % 4 if face in ("north", "south", "east", "west") else (u // 2) % 4
        c = [hx('#d61f35'), hx('#fbf7f2'), hx('#d61f35'), hx('#2e9e4f')][stripe]
        c = lerp(c, (0, 0, 0), 0.12 * n)
        if box.get("fringe") and face in ("north", "south", "east", "west") and v >= fh - 2:
            return (0, 0, 0, 0) if u % 2 else hx('#fbf7f2') + (255,)
    return tuple(int(x) for x in c) + (255,)

def build_accessories():
    """Alle Boxen eines Accessoires in eine Textur (Minecraft-Box-Layout), Ausgabe: PNG + JSON für den Viewer/Mod."""
    os.makedirs(XOUT, exist_ok=True)
    meta = {}
    for aid, acc in ACCESSORIES.items():
        placed, x0, y0, rowh, tw = [], 0, 0, 0, 64
        while any(2 * (round(b["size"][0] * TS) + round(b["size"][2] * TS)) > tw for b in acc["boxes"]): tw *= 2
        for b in acc["boxes"]:
            w, h, d = (max(1, round(s * TS)) for s in b["size"])
            bw, bh = 2 * (w + d), h + d
            if x0 + bw > tw: x0, y0, rowh = 0, y0 + rowh, 0
            placed.append((b, x0, y0, w, h, d)); x0 += bw; rowh = max(rowh, bh)
        th = 1
        while th < y0 + rowh: th *= 2
        img = [[(0, 0, 0, 0)] * tw for _ in range(th)]
        boxes = []
        for i, (b, u0, v0, w, h, d) in enumerate(placed):
            faces = {"up": (u0 + d, v0, w, d), "down": (u0 + d + w, v0, w, d), "east": (u0, v0 + d, d, h),
                     "north": (u0 + d, v0 + d, w, h), "west": (u0 + d + w, v0 + d, d, h), "south": (u0 + 2 * d + w, v0 + d, w, h)}
            # Namen wie im Minecraft-Box-Layout: "north" = Vorderseite der Box; im Viewer zeigt sie nach +z
            for face, (fu, fv, fw, fh) in faces.items():
                for v in range(fh):
                    for u in range(fw):
                        img[fv + v][fu + u] = paint(b["paint"], face, u, v, fw, fh, b, i + 1)
            boxes.append({"name": b["name"], "from": b["from"], "size": b["size"], "uv": [u0, v0], "tex": [w, h, d],
                          "mirror": b.get("mirror", False), "emissive": b.get("emissive", False)})
        pam = os.path.join(XOUT, f"{aid}.pam")
        with open(pam, "wb") as fh:
            fh.write(f"P7\nWIDTH {tw}\nHEIGHT {th}\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n".encode())
            for row in img: fh.write(b"".join(bytes(c) for c in row))
        run([pam, "-depth", "8", f"PNG32:{XOUT}/{aid}.png"]); os.remove(pam)
        render_icon(boxes, img, acc["slot"], f"{XOUT}/{aid}_icon.png")
        meta[aid] = {"name": acc["name"], "slot": acc["slot"], "texture": [tw, th], "texelsPerPixel": TS, "boxes": boxes}
        print("Accessoire", aid, f"{tw}x{th}", len(boxes), "Boxen", flush=True)
    json.dump(meta, open(os.path.join(XOUT, "accessories.json"), "w"), ensure_ascii=False, indent=1)

# ------------------------------------------------------------------ Menü-Vorschau: 3/4-Ansicht (Software-Rasterizer)
def faces_of(box, ts):
    """Flächen einer Box wie im Viewer: (4 Ecken oben-links, oben-rechts, unten-rechts, unten-links von außen gesehen, Texel-Rechteck, Normale)."""
    (x0, y0, z0), (sx, sy, sz) = box["from"], box["size"]
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    u0, v0 = box["uv"]; w, h, d = box["tex"]
    R, F, Lr, B = (u0, v0 + d, d, h), (u0 + d, v0 + d, w, h), (u0 + d + w, v0 + d, d, h), (u0 + 2 * d + w, v0 + d, w, h)
    T, Bo = (u0 + d, v0, w, d), (u0 + d + w, v0, w, d)
    m = box.get("mirror", False)
    return [
        ([(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)], Lr if m else R, (-1, 0, 0)),
        ([(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], F, (0, 0, 1)),
        ([(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], R if m else Lr, (1, 0, 0)),
        ([(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)], B, (0, 0, -1)),
        ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], T, (0, 1, 0)),
        ([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], Bo, (0, -1, 0)),
    ]


def render_icon(boxes, texture, slot, path, size=128, ss=4):
    yaw, pitch = math.radians(-32), math.radians(22)
    cy, sy_, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    def rot(p):
        x, y, z = p
        x, z = x * cy + z * sy_, -x * sy_ + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z
    light = {(0, 1, 0): 1.0, (0, 0, 1): 0.9, (1, 0, 0): 0.78, (-1, 0, 0): 0.78, (0, 0, -1): 0.7, (0, -1, 0): 0.6}
    # grauer Kopf (und beim Hals der Oberkörper) als Bezug
    grey = [dict(**{"from": [-4, 24, -4]}, size=[8, 8, 8], uv=[0, 0], tex=[8, 8, 8], grey=True)]
    if slot == "hals": grey.append(dict(**{"from": [-4, 12, -2]}, size=[8, 12, 4], uv=[0, 0], tex=[8, 12, 4], grey=True))
    tw, th = len(texture[0]), len(texture)
    quads = []  # (tiefe, 2D-Ecken, farbe)
    for b in grey + boxes:
        for corners, (fu, fv, fw, fh), n in faces_of(b, (tw, th)):
            if fw == 0 or fh == 0: continue
            if rot(n)[2] <= 1e-6: continue  # Rückseite
            c0, c1, c3 = corners[0], corners[1], corners[3]
            shade = light[n]
            for j in range(fh):
                for i in range(fw):
                    if b.get("grey"):
                        col = (92, 88, 104, 255) if (i + j) % 2 else (100, 96, 112, 255)
                    else:
                        u = fu + (fw - 1 - i if b.get("mirror") else i)
                        col = texture[fv + j][u]
                    if col[3] == 0: continue
                    pts = []
                    for s, t in ((i / fw, j / fh), ((i + 1) / fw, j / fh), ((i + 1) / fw, (j + 1) / fh), (i / fw, (j + 1) / fh)):
                        p = tuple(c0[k] + (c1[k] - c0[k]) * s + (c3[k] - c0[k]) * t for k in range(3))
                        pts.append(rot(p))
                    depth = sum(q[2] for q in pts) / 4
                    em = b.get("emissive", False)
                    rgb = tuple(int(min(255, col[k] * (1.0 if em else shade))) for k in range(3))
                    quads.append((depth, [(q[0], q[1]) for q in pts], rgb))
    xs = [p[0] for q in quads for p in q[1]]; ys = [p[1] for q in quads for p in q[1]]
    S = size * ss
    span = max(max(xs) - min(xs), max(ys) - min(ys)) * 1.08
    cx, cyy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    k = S / span
    img = [[(0, 0, 0, 0)] * S for _ in range(S)]
    quads.sort(key=lambda q: q[0])
    for _, pts, rgb in quads:
        P = [((x - cx) * k + S / 2, S / 2 - (y - cyy) * k) for x, y in pts]
        minx, maxx = max(0, int(min(p[0] for p in P))), min(S - 1, int(max(p[0] for p in P)) + 1)
        miny, maxy = max(0, int(min(p[1] for p in P))), min(S - 1, int(max(p[1] for p in P)) + 1)
        area = sum(P[a][0] * P[(a + 1) % 4][1] - P[(a + 1) % 4][0] * P[a][1] for a in range(4))
        sgn = 1 if area > 0 else -1
        for yy in range(miny, maxy + 1):
            for xx in range(minx, maxx + 1):
                px, py = xx + 0.5, yy + 0.5
                if all(sgn * ((P[(a + 1) % 4][0] - P[a][0]) * (py - P[a][1]) - (P[(a + 1) % 4][1] - P[a][1]) * (px - P[a][0])) >= -1e-9 for a in range(4)):
                    img[yy][xx] = rgb + (255,)
    pam = path + ".pam"
    with open(pam, "wb") as fh:
        fh.write(f"P7\nWIDTH {S}\nHEIGHT {S}\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n".encode())
        for row in img: fh.write(b"".join(bytes(c) for c in row))
    run([pam, "-filter", "Box", "-resize", f"{size}x{size}", "-depth", "8", f"PNG32:{path}"]); os.remove(pam)


if __name__ == "__main__":
    build_accessories()
