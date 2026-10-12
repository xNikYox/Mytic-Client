"""Pixel-Flügel (Stil: gestufte Federreihen mit goldener Vorderkante), 10 Farben, mit animierter Leucht-Ebene.

Ein Flügel wird auf einem 40x48-Raster gezeichnet (Ansatz am Körper rechts, Spitze oben), die Textur enthält ihn links und
gespiegelt rechts (80x48) und wird ohne Glätten auf 640x384 vergrößert – wie die anderen Flügel (40x24 Modell-Pixel, 16-fach).
Ausgabe je Flügel: <id>.png, <id>_fx_<f>.png (16 Bilder, Helligkeit = Deckkraft), <id>_icon.png und <id>_icon_fx_<f>.png
für das Cosmetics-Menü. Aufruf: pixelwings.py <ausgabeordner>
"""
import math, os, random, subprocess, sys

W, H = 40, 48
FRAMES = 16


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


GOLD = ("#9a4a10", "#e8901e", "#ffd76a")
SILVER = ("#5c6372", "#aeb7c6", "#ffffff")
ROSEGOLD = ("#9a4430", "#e8967a", "#ffe0cc")

# Federreihen von hinten (lange Schwungfedern) nach vorne (Deckfedern), Umriss, Kante, Glanzfarbe, Extras
PALETTES = {
    "rabe": dict(name="Rabe", tiers=["#151019", "#231b2c", "#33283f", "#45375a"], outline="#07050a", edge=GOLD, glint="#c8b4ff", sparkles=6),
    "taube": dict(name="Taube", tiers=["#a6c3dc", "#eef4fa", "#ead8ba", "#ffffff"], outline="#7d8fa8", edge=GOLD, glint="#ffffff", sparkles=6),
    "elster": dict(name="Elster", tiers=["#141118", "#f2f2f2", "#26212c", "#ffffff"], outline="#060508", edge=SILVER, glint="#ffffff", sparkles=6),
    "phoenix": dict(name="Phönix", tiers=["#7a1204", "#cc360c", "#f2801e", "#ffc23a"], outline="#3e0801", edge=GOLD, glint="#fff0a0", sparkles=4, embers=True),
    "saphir": dict(name="Saphir", tiers=["#0a1c56", "#143a98", "#2c6ade", "#7cb0ff"], outline="#040c2c", edge=SILVER, glint="#d4eaff", sparkles=7),
    "amethyst": dict(name="Amethyst", tiers=["#280d46", "#4a1b7c", "#7838c0", "#b888f0"], outline="#140524", edge=GOLD, glint="#f0d6ff", sparkles=7),
    "kirsche": dict(name="Kirschblüte", tiers=["#86264f", "#cc4c86", "#ee8ab6", "#ffd2e6"], outline="#46102c", edge=ROSEGOLD, glint="#ffffff", sparkles=6),
    "sonne": dict(name="Sonnengold", tiers=["#825200", "#c48800", "#efc030", "#fff19a"], outline="#442a00", edge=("#b0761a", "#fff0b0", "#ffffff"), glint="#ffffff", sparkles=8),
    "polarlicht": dict(name="Polarlicht", tiers=["#18286a", "#1c7690", "#2cc6a0", "#a0f8cc"], outline="#091030", edge=SILVER, glint="#c8fff0", sparkles=5, aurora=True),
    "sternennacht": dict(name="Sternennacht", tiers=["#09091f", "#161644", "#26266a", "#40409e"], outline="#03030c", edge=SILVER, glint="#a8d4ff", sparkles=18),
}


def edge_point(t):
    """Vorderkante: von der Spitze oben (t=0) bis zum Ansatz unten (t=1), leicht nach außen gebogen."""
    return 34.0 - 4.0 * t + 1.5 * math.sin(math.pi * t), 5.0 + 37.0 * t


def shape(t):
    """Länge der Federn entlang der Kante: oben am längsten (Flügel ragt nach oben-außen), zum Ansatz hin deutlich kürzer."""
    if t < 0.15:
        return 0.6 + 0.4 * t / 0.15
    return 1.0 - 0.62 * ((t - 0.15) / 0.85) ** 1.1


def feathers():
    """Alle Federn: (Reihe, t am Ansatz, Start, Ende, Breite). Reihe 0 = hinten/längste; je Reihe unten zuerst."""
    out = []
    lengths = (28.0, 20.0, 12.5, 6.5)
    counts = (7, 7, 8, 8)
    for k in range(4):
        for j in range(counts[k]):
            t = 0.1 + 0.88 * (j + 0.5 * (k % 2)) / counts[k]
            sx, sy = edge_point(t)
            ang = math.radians(-6 + 58 * t)
            length = lengths[k] * shape(t)
            ex, ey = sx - math.cos(ang) * length, sy + math.sin(ang) * length
            out.append((k, t, (sx, sy), (ex, ey), 2.3 - 0.15 * k))
    out.sort(key=lambda f: (f[0], -f[1]))
    return out


def seg_dist(px, py, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    u = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / l2)) if l2 else 0.0
    cx, cy = ax + u * dx, ay + u * dy
    return math.hypot(px - cx, py - cy), u


def mix(c1, c2, f):
    return tuple(int(round(a + (b - a) * f)) for a, b in zip(c1, c2))


def render_base(pal):
    """Grundbild 40x48: RGBA je Pixel; dazu je Pixel (Feder-Index, Anteil entlang der Feder) für die Animation."""
    img = [[None] * W for _ in range(H)]
    info = [[None] * W for _ in range(H)]
    tiers = [hexrgb(c) for c in pal["tiers"]]
    outline = hexrgb(pal["outline"])
    fl = feathers()
    for idx, (k, t, a, b, w) in enumerate(fl):
        col = tiers[k]
        dark = mix(col, outline, 0.45)
        light = mix(col, (255, 255, 255), 0.35)
        for y in range(H):
            for x in range(W):
                d, u = seg_dist(x + 0.5, y + 0.5, a, b)
                r = w * (1.0 - 0.35 * u ** 2)
                if d <= r:
                    # Lichtkante oben an der Feder, Schatten unten, Spitze etwas dunkler
                    side = (y + 0.5) - (a[1] + u * (b[1] - a[1]))
                    c = light if side < -r * 0.35 else dark if side > r * 0.45 else col
                    if u > 0.85:
                        c = mix(c, dark, 0.5)
                    img[y][x] = c + (255,)
                    info[y][x] = (idx, u, k, t)
                elif d <= r + 1.0:
                    img[y][x] = outline + (255,)
                    info[y][x] = (idx, u, k, t)
    # Vorderkante (Knochen) mit Verlauf und Glanzlinie
    e0, e1, e2 = (hexrgb(c) for c in pal["edge"])
    for y in range(H):
        for x in range(W):
            best = None
            for s in range(200):
                t = s / 199
                ex, ey = edge_point(t)
                d = math.hypot(x + 0.5 - ex, y + 0.5 - ey)
                if best is None or d < best[0]:
                    best = (d, t, x + 0.5 - ex)
            d, t, dx = best
            thick = 1.2 + 1.4 * math.sin(math.pi * min(1.0, t * 1.3))
            if d <= thick:
                c = e2 if dx < -thick * 0.3 else e1 if dx < thick * 0.4 else e0
                img[y][x] = c + (255,)
                info[y][x] = ("edge", t, -1, t)
            elif d <= thick + 1.0 and img[y][x] is None:
                img[y][x] = outline + (255,)
    return img, info


def sparkle_points(info, n, seed):
    rnd = random.Random(seed)
    cells = [(x, y) for y in range(H) for x in range(W) if info[y][x] is not None and info[y][x][0] != "edge"]
    rnd.shuffle(cells)
    return [(x, y, rnd.random()) for x, y in cells[:n]]


def render_fx(pal, info, f, sparkles):
    """Leucht-Ebene eines Bildes (RGB, Helligkeit = Deckkraft)."""
    p = f / FRAMES
    glint = hexrgb(pal["glint"])
    out = [[(0, 0, 0)] * W for _ in range(H)]

    def add(x, y, col, a):
        if 0 <= x < W and 0 <= y < H and a > 0:
            o = out[y][x]
            out[y][x] = tuple(min(255, int(o[i] + col[i] * a)) for i in range(3))

    for y in range(H):
        for x in range(W):
            inf = info[y][x]
            if inf is None:
                continue
            if inf[0] == "edge":
                # Glanz läuft die Kante hinauf
                t = inf[1]
                g = math.exp(-((t - (1.25 - p * 1.5)) ** 2) / 0.006)
                add(x, y, glint, g * 0.95)
                continue
            idx, u, k, t = inf
            # Lichtwelle über die Federspitzen nach außen
            wave = math.sin(2 * math.pi * (p - u * 0.6 - t * 0.35))
            if u > 0.45:
                add(x, y, glint, max(0.0, wave) ** 6 * 0.55 * (u - 0.45) / 0.55)
            if pal.get("embers") and u > 0.7:
                flick = 0.5 + 0.5 * math.sin(2 * math.pi * (p * 2 + idx * 0.37))
                add(x, y, (255, 120, 30), flick * 0.45)
            if pal.get("aurora"):
                band = math.sin(2 * math.pi * (p + (x + y) / 40.0))
                hue = (p + y / 60.0) % 1.0
                col = hsv(hue * 0.5 + 0.35, 0.7, 1.0)
                add(x, y, col, max(0.0, band) ** 2 * 0.35)
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


def convert(src, dst, scale):
    subprocess.run(["convert", src, "-filter", "point", "-resize", f"{scale * 100}%", "-depth", "8", f"PNG32:{dst}"], check=True)


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
        convert(pam, os.path.join(out, f"{wid}_icon.png"), 3)
        sp = sparkle_points(info, pal["sparkles"], wid)
        for f in range(FRAMES):
            fx = render_fx(pal, info, f, sp)
            # Deckkraft = Helligkeit
            rows = [[c + (max(c),) for c in row] for row in fx]
            pam = os.path.join(tmp, f"{wid}_fx_{f}.pam")
            write_pam(pam, rows)
            convert(pam, os.path.join(out, f"{wid}_fx_{f}.png"), 4)
            write_pam(pam, rows, icon=True)
            convert(pam, os.path.join(out, f"{wid}_icon_fx_{f}.png"), 3)
        print("Pixel-Flügel", wid)


if __name__ == "__main__":
    main()
