"""Pixel-Flügel (Stil: gestufte Federreihen mit goldener Vorderkante), 10 Farben, mit animierter Leucht-Ebene.

Ein Flügel wird auf einem 48x40-Raster gezeichnet (Ansatz am Körper rechts, Sichel-Spitze oben), die Textur enthält ihn auf
beiden Seiten (96x40) und wird ohne Glätten auf 768x320 vergrößert (Modell 24x20 Pixel je Flügel, 16-fach).
Ausgabe je Flügel: <id>.png, <id>_fx_<f>.png (16 Bilder, Helligkeit = Deckkraft), <id>_icon.png und <id>_icon_fx_<f>.png
für das Cosmetics-Menü. Aufruf: pixelwings.py <ausgabeordner>
"""
import math, os, random, subprocess, sys

W, H = 48, 40  # breiter als hoch, wie die Vorlage (Modell 24x20 Pixel)
FRAMES = 16


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# Sichel-Kante: außen dunkel → innen hell (4 Töne)
GOLD = ("#d4500e", "#f28a22", "#ffc75a", "#ffeaa8")
SILVER = ("#6a7282", "#a9b3c4", "#dfe6f0", "#ffffff")
ROSEGOLD = ("#c04a3a", "#ec8a72", "#ffc4ae", "#fff0e6")

# Feder-Hauptfarbe, Schatten, Zweitfarbe (Linie unten an jeder Feder), zwei Querstreifen, Kante, Glanzfarbe, Extras.
# rows: eigene Hauptfarben je Federreihe (von oben nach unten), sonst überall main.
PALETTES = {
    "rabe": dict(name="Rabe", main="#2b1424", shade="#1d0b17", second="#45263a", band1="#9a6f7d", band2="#6e4655", edge=GOLD, glint="#e8d0ff", sparkles=5),
    "taube": dict(name="Taube", main="#fff7e8", shade="#f2e6d0", second="#c9e5ea", band1="#e8c89e", band2="#e3a670", edge=GOLD, glint="#ffffff", sparkles=5),
    "elster": dict(name="Elster", main="#f4f4f4", shade="#d8d8de", second="#9ea3ad", band1="#3a3640", band2="#1a171e", edge=SILVER, glint="#ffffff", sparkles=5,
                   rows=["#f4f4f4", "#1c1820", "#f4f4f4", "#1c1820", "#f4f4f4", "#1c1820", "#f4f4f4"]),
    "phoenix": dict(name="Phönix", main="#ff8c2a", shade="#e65a12", second="#ffd34c", band1="#c8300e", band2="#8a1a06", edge=GOLD, glint="#fff2a8", sparkles=4, embers=True,
                    rows=["#ffd34c", "#ffb238", "#ff8c2a", "#f86c1e", "#ec5016", "#d83a10", "#c02a0c"]),
    "saphir": dict(name="Saphir", main="#3a7ae6", shade="#2456bc", second="#9ccaff", band1="#173c90", band2="#0c2462", edge=SILVER, glint="#d8eeff", sparkles=6),
    "amethyst": dict(name="Amethyst", main="#9454d8", shade="#7034b0", second="#d6b0ff", band1="#4c1e7c", band2="#2e0f52", edge=GOLD, glint="#f4e0ff", sparkles=6),
    "kirsche": dict(name="Kirschblüte", main="#ffc8de", shade="#f4a4c6", second="#ffffff", band1="#e2729f", band2="#c24c7e", edge=ROSEGOLD, glint="#ffffff", sparkles=6),
    "sonne": dict(name="Sonnengold", main="#ffda4c", shade="#f0b822", second="#fff4aa", band1="#d88a10", band2="#b06a00", edge=("#e09a20", "#ffd060", "#fff2b8", "#ffffff"), glint="#ffffff", sparkles=8),
    "polarlicht": dict(name="Polarlicht", main="#2cc6a0", shade="#1c9a80", second="#b4fff0", band1="#1c5c90", band2="#283a8c", edge=SILVER, glint="#d0fff4", sparkles=5, aurora=True,
                       rows=["#a0f8cc", "#62e8b4", "#2cc6a0", "#22a8b0", "#2486c4", "#3666d8", "#4a52d6"]),
    "sternennacht": dict(name="Sternennacht", main="#1c1c4e", shade="#121236", second="#2e2e7c", band1="#4c4cac", band2="#6c6cd4", edge=SILVER, glint="#b0d8ff", sparkles=18),
}

ROWS = 7
ROW_H = 4.6
ANGLE = math.radians(15)  # Federn steigen nach außen leicht an
DIR = (-math.cos(ANGLE), -math.sin(ANGLE))  # Richtung zur Federspitze (nach außen-oben)
NRM = (-math.sin(ANGLE), math.cos(ANGLE))  # quer dazu, nach unten


def crescent(t):
    """Sichel-Kante: von der Spitze oben (t=0) bis unten rechts am Körper (t=1), quadratische Kurve."""
    p0, c, p1 = (29.0, 0.5), (31.0, 26.0), (47.5, 30.0)
    x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0]
    y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]
    return x, y


CRESCENT = [crescent(i / 299) for i in range(300)]


def crescent_x(y):
    """x der Sichel-Mittellinie auf Höhe y (None unterhalb)."""
    best = None
    for cx, cy in CRESCENT:
        if best is None or abs(cy - y) < abs(best[1] - y):
            best = (cx, cy)
    return best[0] if abs(best[1] - y) < 1.0 else None


def mix(c1, c2, f):
    return tuple(int(round(a + (b - a) * f)) for a, b in zip(c1, c2))


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1]


# Linke Außenkante des Flügels (die Federspitzen liegen gestuft darauf): von oben-außen nach unten-innen
TIP_TOP, TIP_BOTTOM = (1.0, 2.0), (24.0, 39.0)
V0 = dot((46.0, 10.0), NRM)  # Oberkante der obersten Federreihe


def classify(x, y):
    """('edge', t, seite) | ('feather', reihe, quer 0..1, abstand zur Kante) | None"""
    px, py = x + 0.5, y + 0.5
    # Sichel: dick in der Mitte, spitz an den Enden
    best = None
    for i, (cx, cy) in enumerate(CRESCENT):
        d = (px - cx) ** 2 + (py - cy) ** 2
        if best is None or d < best[0]:
            best = (d, i)
    d, i = math.sqrt(best[0]), best[1]
    t = i / (len(CRESCENT) - 1)
    thick = 0.35 + 2.3 * math.sin(math.pi * min(1.0, t * 1.04)) ** 0.7
    if d <= thick:
        j = min(i + 1, len(CRESCENT) - 1)
        tx, ty = CRESCENT[j][0] - CRESCENT[max(i - 1, 0)][0], CRESCENT[j][1] - CRESCENT[max(i - 1, 0)][1]
        side = (tx * (py - CRESCENT[i][1]) - ty * (px - CRESCENT[i][0])) / (math.hypot(tx, ty) + 1e-6)
        return ("edge", t, max(-1.0, min(1.0, -side / thick)))
    # Federreihen
    v = dot((px, py), NRM) - V0
    if v < 0:
        return None
    row = int(v / ROW_H)
    if row >= ROWS:
        return None
    fv = v / ROW_H - row
    cx = crescent_x(py)
    if cx is not None and px > cx:
        return None
    if cx is None and py < 15:
        return None
    # Federspitze: Schnittpunkt der Reihenmitte mit der Außenkante, Spitze schräg abgeschnitten
    k = (row + 0.5) / ROWS
    bx, by = TIP_TOP[0] + (TIP_BOTTOM[0] - TIP_TOP[0]) * k, TIP_TOP[1] + (TIP_BOTTOM[1] - TIP_TOP[1]) * k
    if dot((px - bx, py - by), DIR) > (fv - 0.5) * 3.0:
        return None
    # unten rechts (unter der Sichel) enden die Reihen gestuft
    if py > 38.5 + (47.5 - px) * 0.1:
        return None
    off = (cx if cx is not None else 47.5) - px
    return ("feather", row, fv, off)


def render_base(pal):
    """Grundbild 40x48 (Ansatz rechts): RGBA je Pixel und die Einordnung je Pixel für die Animation."""
    img = [[None] * W for _ in range(H)]
    info = [[None] * W for _ in range(H)]
    main, shade, second = hexrgb(pal["main"]), hexrgb(pal["shade"]), hexrgb(pal["second"])
    band1, band2 = hexrgb(pal["band1"]), hexrgb(pal["band2"])
    edge = [hexrgb(c) for c in pal["edge"]]
    rows = [hexrgb(c) for c in pal["rows"]] if "rows" in pal else None
    for y in range(H):
        for x in range(W):
            c = classify(x, y)
            if c is None:
                continue
            info[y][x] = c
            if c[0] == "edge":
                side = c[2]
                col = edge[0] if side < -0.35 else edge[1] if side < 0.15 else edge[2] if side < 0.6 else edge[3]
                img[y][x] = col + (255,)
                continue
            _, row, fv, off = c
            base = rows[row] if rows else main
            col = base
            if fv > 0.74:
                col = second if not rows else mix(base, second, 0.6)  # Linie unten an jeder Feder
            elif fv < 0.2:
                col = mix(base, (255, 255, 255), 0.18)
            elif fv > 0.55:
                col = mix(base, shade if not rows else mix(base, (0, 0, 0), 0.25), 0.6)
            # zwei schräge Querstreifen (Grenzen der Deckfedern), etwa parallel zur Kante
            if 8.0 <= off <= 9.8 and fv <= 0.74:
                col = band1
            elif 18.0 <= off <= 19.5 and row >= 1 and fv <= 0.74:
                col = band2
            img[y][x] = col + (255,)
    return img, info


def sparkle_points(info, n, seed):
    rnd = random.Random(seed)
    cells = [(x, y) for y in range(H) for x in range(W) if info[y][x] is not None and info[y][x][0] != "edge"]
    rnd.shuffle(cells)
    return [(x, y, rnd.random()) for x, y in cells[:n]]


def render_fx(pal, info, f, sparkles):
    """Leucht-Ebene eines Bildes (RGB, Helligkeit = Deckkraft): Glanzstreifen quer über den Flügel, Glanz an der Sichel,
    Funkeln; je nach Farbe Glut oder Polarlicht-Schimmer."""
    p = f / FRAMES
    glint = hexrgb(pal["glint"])
    out = [[(0, 0, 0)] * W for _ in range(H)]

    def add(x, y, col, a):
        if 0 <= x < W and 0 <= y < H and a > 0:
            o = out[y][x]
            out[y][x] = tuple(min(255, int(o[i] + col[i] * a)) for i in range(3))

    # Glanzstreifen: breit + schmal, wandern von innen nach außen (quer zur Federrichtung, parallel zur Sichel)
    m = (30.0, -18.0)
    ml = math.hypot(*m)
    m = (m[0] / ml, m[1] / ml)
    centre = 44.0 - p * 68.0
    for y in range(H):
        for x in range(W):
            inf = info[y][x]
            if inf is None:
                continue
            w = dot((x + 0.5, y + 0.5), m)
            a = 0.0
            if abs(w - centre) < 3.2:
                a = 0.32
            elif abs(w - (centre + 6.5)) < 0.9:
                a = 0.26
            if a:
                add(x, y, glint, a)
            if inf[0] == "edge":
                g = math.exp(-((inf[1] - (1.15 - p * 1.3)) ** 2) / 0.008)
                add(x, y, glint, g * 0.9 * (0.6 + 0.4 * (inf[2] + 1) / 2))
                continue
            _, row, fv, off = inf
            if pal.get("embers") and off > 14:
                flick = max(0.0, math.sin(2 * math.pi * (p * 2 + row * 0.37 + off * 0.05)))
                add(x, y, (255, 110, 20), flick ** 2 * 0.5)
            if pal.get("aurora"):
                band = math.sin(2 * math.pi * (p + (x - y * 0.5) / 30.0))
                add(x, y, hsv((p + row / 10.0) % 1.0 * 0.45 + 0.4, 0.7, 1.0), max(0.0, band) ** 2 * 0.35)
    for sx, sy, ph in sparkles:
        a = max(0.0, math.sin(2 * math.pi * (p + ph))) ** 8
        if a < 0.05:
            continue
        add(sx, sy, (255, 255, 255), a)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            add(sx + dx, sy + dy, glint, a * 0.5)
    return out


def hsv(h, s, v):
    i = int(h * 6) % 6
    f = h * 6 - int(h * 6)
    p, q, t = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
    r, g, b = [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)][i]
    return int(r * 255), int(g * 255), int(b * 255)


def write_pam(path, rows, icon=False):
    """rows: Liste von Zeilen mit RGBA-Tupeln (40 breit, Ansatz rechts) → Textur 80x48 (beide Seiten)."""
    with open(path, "wb") as fh:
        fh.write(f"P7\nWIDTH {W * 2}\nHEIGHT {H}\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n".encode())
        data = bytearray()
        for row in rows:
            # im Modell liegt der Körper-Ansatz der linken Texturhälfte links: daher links gespiegelt
            # fürs Menü-Bild andersherum, damit es aussieht wie im Spiel (Kante innen)
            full = list(row) + list(reversed(row)) if icon else list(reversed(row)) + list(row)
            for c in full:
                data += bytes(c)
        fh.write(data)


def convert(src, dst, scale, canvas=None):
    pad = ["-background", "none", "-gravity", "center", "-extent", canvas] if canvas else []
    subprocess.run(["convert", src, "-filter", "point", "-resize", f"{scale * 100}%", *pad, "-depth", "8", f"PNG32:{dst}"], check=True)


def main():
    out = sys.argv[1]
    tmp = os.path.join(out, "tmp-pixel")
    os.makedirs(tmp, exist_ok=True)
    for wid, pal in PALETTES.items():
        img, info = render_base(pal)
        rows = [[c if c is not None else (0, 0, 0, 0) for c in row] for row in img]
        pam = os.path.join(tmp, f"{wid}.pam")
        write_pam(pam, rows)
        convert(pam, os.path.join(out, f"{wid}.png"), 8)
        write_pam(pam, rows, icon=True)
        convert(pam, os.path.join(out, f"{wid}_icon.png"), 2, "240x144")
        sp = sparkle_points(info, pal["sparkles"], wid)
        for f in range(FRAMES):
            fx = render_fx(pal, info, f, sp)
            # Deckkraft = Helligkeit
            rows = [[c + (max(c),) for c in row] for row in fx]
            pam = os.path.join(tmp, f"{wid}_fx_{f}.pam")
            write_pam(pam, rows)
            convert(pam, os.path.join(out, f"{wid}_fx_{f}.png"), 4)
            write_pam(pam, rows, icon=True)
            convert(pam, os.path.join(out, f"{wid}_icon_fx_{f}.png"), 2, "240x144")
        print("Pixel-Flügel", wid)


if __name__ == "__main__":
    main()
