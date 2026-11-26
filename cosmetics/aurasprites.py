"""Eigene Partikel-Sprites für die Mytic-Auren (keine Minecraft-Partikel).

Jede Aura hat bis zu 4 Sprites (32x32, weich leuchtend). Textur je Aura: <id>.png, je Sprite 64x32 (links die Vorderseite,
rechts gespiegelt die Rückseite), nebeneinander. Dazu <id>_icon.png (erstes Sprite, 32x32) für das Menü.
Aufruf: aurasprites.py <ausgabeordner>
"""
import math, os, subprocess, sys

S = 32


def cl(v, a=0.0, b=1.0): return a if v < a else b if v > b else v
def ss(e0, e1, x): t = cl((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t)
def hx(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
def mix(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def seg(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    u = cl(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy + 1e-9))
    return math.hypot(px - ax - u * dx, py - ay - u * dy)


# ------------------------------------------------------------------ Formen als Abstandsfunktion (< 0 = innen), Einheit: Pixel
def circle(r):
    return lambda x, y: math.hypot(x, y) - r


def ring(r, w):
    return lambda x, y: abs(math.hypot(x, y) - r) - w


def star(points, r1, r2, rot=0.0):
    def f(x, y):
        a = math.atan2(y, x) + rot
        r = math.hypot(x, y)
        k = (math.cos(a * points) + 1) / 2  # 1 an den Spitzen
        edge = r2 + (r1 - r2) * k ** 3
        return r - edge
    return f


def heart(s):
    def f(x, y):
        x, y = x / s, -y / s + 0.25
        v = (x * x + y * y - 1) ** 3 - x * x * y ** 3
        return v * s * 2.2
    return f


def ellipse(rx, ry, rot=0.0):
    c, sn = math.cos(rot), math.sin(rot)
    def f(x, y):
        u, v = x * c + y * sn, -x * sn + y * c
        return (math.hypot(u / rx, v / ry) - 1) * min(rx, ry)
    return f


def lines(segs, w):
    return lambda x, y: min(seg(x, y, *s) for s in segs) - w


def union(*fs): return lambda x, y: min(f(x, y) for f in fs)
def subtract(a, b): return lambda x, y: max(a(x, y), -b(x, y))
def shift(f, dx, dy): return lambda x, y: f(x - dx, y - dy)


def snowflake(r):
    segs = []
    for k in range(6):
        a = k * math.pi / 3
        ex, ey = math.cos(a) * r, math.sin(a) * r
        segs.append((0, 0, ex, ey))
        for t, l in ((0.55, 0.35), (0.8, 0.22)):
            bx, by = ex * t, ey * t
            for side in (-1, 1):
                b = a + side * 0.8
                segs.append((bx, by, bx + math.cos(b) * r * l, by + math.sin(b) * r * l))
    return lines(segs, 1.0)


def wisp(h):
    """Flamme / Irrlicht: runder Bauch unten, nach oben spitz zulaufend."""
    r = h * 0.3
    cy = h * 0.25
    def f(x, y):
        if y >= cy:
            return math.hypot(x, y - cy) - r
        t = (cy - y) / (h * 0.85)  # 0 am Bauch, 1 an der Spitze
        w = r * (1 - t) ** 1.4 + 0.3 * math.sin(t * 3) * r * 0.2
        return abs(x + math.sin(t * 2.5) * r * 0.25) - w if t < 1 else 99
    return f


def crescent(r):
    return subtract(circle(r), shift(circle(r * 0.85), r * 0.45, -r * 0.2))


def note(s):
    head = shift(ellipse(s * 0.38, s * 0.28, -0.4), -s * 0.15, s * 0.45)
    stem = lines([(s * 0.18, s * 0.42, s * 0.18, -s * 0.6)], s * 0.08)
    flag = lines([(s * 0.18, -s * 0.6, s * 0.5, -s * 0.3)], s * 0.08)
    return union(head, stem, flag)


def diamond(w, h):
    return lambda x, y: (abs(x) / w + abs(y) / h - 1) * min(w, h) * 0.7


def bolt(s):
    pts = [(-0.15, -1.0), (0.25, -0.15), (-0.1, -0.05), (0.2, 1.0)]
    return lines([(pts[i][0] * s, pts[i][1] * s, pts[i + 1][0] * s, pts[i + 1][1] * s) for i in range(3)], s * 0.1)


def leaf(s, rot):
    return union(ellipse(s, s * 0.5, rot), lines([(-math.cos(rot) * s * 1.3, -math.sin(rot) * s * 1.3, math.cos(rot) * s, math.sin(rot) * s)], 0.6))


def butterfly(s):
    return union(shift(ellipse(s * 0.5, s * 0.38, 0.5), -s * 0.45, -s * 0.25), shift(ellipse(s * 0.5, s * 0.38, -0.5), s * 0.45, -s * 0.25),
                 shift(ellipse(s * 0.35, s * 0.28, -0.4), -s * 0.32, s * 0.35), shift(ellipse(s * 0.35, s * 0.28, 0.4), s * 0.32, s * 0.35),
                 ellipse(s * 0.1, s * 0.55))


def feather(s):
    """Feder: schmale, leicht gebogene Fahne mit Kiel."""
    def f(x, y):
        x2 = x - math.sin((y / s) * 1.2) * s * 0.12
        t = cl((y + s) / (2 * s))
        w = s * 0.5 * math.sin(math.pi * cl(t * 1.05)) ** 0.6
        vane = abs(x2) - w if -s <= y <= s * 0.85 else 99
        quill = seg(x, y, 0, s * 0.6, 0, s * 1.2) - 0.6
        return min(vane, quill)
    return f


def rune(seed):
    """Einfache Rune aus 3–4 Strichen (je Seed anders)."""
    import random
    rnd = random.Random(seed)
    pts = [(-7, -10), (7, -10), (-7, 0), (7, 0), (-7, 10), (7, 10), (0, -12), (0, 12)]
    segs = [(0, -12, 0, 12)]
    for _ in range(3):
        a, b = rnd.sample(pts, 2)
        segs.append((a[0], a[1], b[0], b[1]))
    return lines(segs, 1.3)


# ------------------------------------------------------------------ Zeichnen
def sprite(shape, fill, rim=None, glow=None, glow_size=4.0, core=None):
    """RGBA-Pixel 32x32: Form gefüllt (innen zur Mitte heller = core), Rand (rim), weicher Schein (glow)."""
    px = []
    fill = hx(fill)
    rim = hx(rim) if rim else None
    glow = hx(glow) if glow else fill
    core = hx(core) if core else None
    for j in range(S):
        row = []
        for i in range(S):
            x, y = i + 0.5 - S / 2, j + 0.5 - S / 2
            d = shape(x, y)
            inside = ss(0.7, -0.7, d)
            col = fill
            if core:
                col = mix(fill, core, cl(1 - math.hypot(x, y) / 9))
            if rim:
                col = mix(col, rim, ss(-2.2, -0.6, d))
            g = math.exp(-max(0, d) / glow_size) * 0.55 if glow_size > 0 else 0
            # Schein zum Rand des Sprites hin ausblenden (sonst sieht man das Quadrat)
            edge = ss(S / 2, S / 2 - 4, max(abs(x), abs(y)))
            a = max(inside, g * edge)
            c = mix(glow, col, inside / a if a > 0 else 0)
            row.append((int(c[0]), int(c[1]), int(c[2]), int(cl(a) * 255)))
        px.append(row)
    return px


# ------------------------------------------------------------------ Winterzauber 2026: neue Formen

def gingerman(s):
    return union(shift(circle(3.2 * s), 0, -6 * s), lines([(0, -3 * s, 0, 3 * s)], 3 * s), lines([(-6 * s, -1.5 * s, 6 * s, -1.5 * s)], 1.6 * s),
             lines([(-1 * s, 3 * s, -4 * s, 8.5 * s), (1 * s, 3 * s, 4 * s, 8.5 * s)], 1.7 * s))

def candycane(s):
    hook = lambda x, y: max(abs(math.hypot(x - 2.5 * s, y + 5 * s) - 2.5 * s) - 1.3 * s, y + 5 * s)
    return shift(union(lines([(0, -5 * s, 0, 8 * s), (5 * s, -5 * s, 5 * s, -2.5 * s)], 1.3 * s), hook), -2.5 * s, 0)

def bauble(r):
    return union(circle(r), shift(lambda x, y: max(abs(x) - r * 0.3, abs(y) - r * 0.18), 0, -r * 1.05))

def bell(s):
    def f(x, y):
        # Glocke: oben schmal, unten breit, mit Rand
        if y < -6 * s: return math.hypot(x, y + 6 * s) - 1.8 * s
        half = 2.8 * s + 3.6 * s * ((y + 6 * s) / (12 * s)) ** 1.6
        return max(abs(x) - half, y - 5 * s, -y - 6 * s)
    return union(f, shift(circle(1.4 * s), 0, 6.4 * s))

def treeshape(s):
    def tri(top, bot, half):
        return lambda x, y: max(abs(x) - half * (y - top) / (bot - top) if bot > top else 99, top - y, y - bot)
    return union(tri(-10 * s, -2 * s, 5 * s), tri(-6 * s, 3 * s, 7 * s), tri(-2 * s, 8 * s, 9 * s), lambda x, y: max(abs(x) - 1.3 * s, abs(y - 9.5 * s) - 1.5 * s))

def stripes(sprite_px, col, period=5, width=2.2):
    """Rote Schrägstreifen auf eine Form legen (Zuckerstange)."""
    out = []
    for j, row in enumerate(sprite_px):
        r2 = []
        for i, p in enumerate(row):
            if p[3] > 200 and ((i + j) % period) < width:
                r2.append(col + (p[3],))
            else:
                r2.append(p)
        out.append(r2)
    return out


AURAS = {
    "schwarzweiss": [sprite(circle(4.5), "#0b0b0b", rim="#ffffff", glow="#ffffff", glow_size=2.5),
                     sprite(star(4, 9, 2.2), "#ffffff", glow="#ffffff", glow_size=3),
                     sprite(ring(6, 1.2), "#ffffff", glow="#888888", glow_size=2),
                     sprite(circle(3), "#ffffff", rim="#0b0b0b", glow="#000000", glow_size=3)],
    "sternenstaub": [sprite(star(4, 11, 2), "#ffffff", glow="#c8b8ff", core="#ffffff"),
                     sprite(star(4, 7, 1.6, 0.78), "#e6dcff", glow="#9b8cff"),
                     sprite(circle(2.5), "#ffffff", glow="#c8b8ff", glow_size=5)],
    "seelenlicht": [sprite(wisp(22), "#4fe8ff", glow="#1fb3c4", core="#e0ffff"),
                    sprite(wisp(16), "#7ff0ff", glow="#1fb3c4", core="#ffffff"),
                    sprite(circle(3), "#bff8ff", glow="#4fe8ff", glow_size=5)],
    "kirschbluete": [sprite(union(*[shift(ellipse(5, 3, a), math.cos(a) * 5, math.sin(a) * 5) for a in [k * 2 * math.pi / 5 for k in range(5)]]), "#ffc2d9", rim="#ff8fb8", glow="#ff8fb8", glow_size=1.5, core="#fff0f6"),
                     sprite(ellipse(7, 4, 0.6), "#ffb3cf", rim="#ff7fae", glow="#ff8fb8", glow_size=1.2),
                     sprite(ellipse(5, 3, -0.4), "#ffd6e7", rim="#ff8fb8", glow="#ff8fb8", glow_size=1.2)],
    "frost": [sprite(snowflake(12), "#ffffff", glow="#9fdcff", glow_size=3),
              sprite(snowflake(8), "#e8f8ff", glow="#9fdcff", glow_size=2.5),
              sprite(circle(2.5), "#ffffff", glow="#bfe8ff", glow_size=4)],
    "herzschlag": [sprite(heart(8), "#ff4f7a", glow="#ff4f7a", glow_size=3, core="#ff9fbe"),
                   sprite(heart(6), "#ff8fb0", glow="#ff4f7a", glow_size=2.5),
                   sprite(circle(2), "#ffd6e2", glow="#ff4f7a", glow_size=4)],
    "runen": [sprite(rune(1), "#d8b8ff", glow="#8a3cff", glow_size=4), sprite(rune(2), "#d8b8ff", glow="#8a3cff", glow_size=4),
              sprite(rune(3), "#d8b8ff", glow="#8a3cff", glow_size=4), sprite(rune(4), "#d8b8ff", glow="#8a3cff", glow_size=4)],
    "galaxie": [sprite(circle(4), "#b56bff", glow="#7a3cff", glow_size=6, core="#ffe0ff"),
                sprite(circle(3), "#5a8bff", glow="#2b59ff", glow_size=5, core="#e0f0ff"),
                sprite(star(4, 8, 1.5), "#ffffff", glow="#d0b0ff")],
    "glut": [sprite(circle(3), "#ffcf4a", glow="#ff5a00", glow_size=5, core="#fff6c0"),
             sprite(wisp(14), "#ff7a1a", glow="#ff3a00", core="#ffe08a"),
             sprite(circle(2), "#ff9a3a", glow="#ff5a00", glow_size=3)],
    "gluehwuermchen": [sprite(circle(2.5), "#f4ff9a", glow="#b8ff3a", glow_size=6, core="#ffffff"),
                       sprite(circle(1.8), "#fff28a", glow="#d8ff5a", glow_size=5)],
    "kristall": [sprite(diamond(6, 11), "#7ff0ff", rim="#e0ffff", glow="#3fd0ff", glow_size=3),
                 sprite(diamond(4, 7), "#b8a0ff", rim="#f0e8ff", glow="#8a6bff", glow_size=3),
                 sprite(star(4, 6, 1.2), "#ffffff", glow="#9fe8ff")],
    "mond": [sprite(crescent(10), "#fff2c0", glow="#ffd27a", glow_size=3),
             sprite(star(5, 6, 2.6, -math.pi / 2), "#fff8d8", glow="#ffd27a", glow_size=2.5),
             sprite(circle(1.8), "#ffffff", glow="#fff2c0", glow_size=4)],
    "melodie": [sprite(note(12), "#7dff6a", glow="#3a9a2a", glow_size=2), sprite(note(12), "#ffd23f", glow="#9a7a10", glow_size=2),
                sprite(note(12), "#3fd0ff", glow="#1a6a9a", glow_size=2), sprite(note(12), "#ff6aa8", glow="#9a2a5a", glow_size=2)],
    "blasen": [sprite(ring(8, 1.0), "#cff4ff", glow="#7fd8ff", glow_size=2), sprite(ring(5, 0.9), "#e8fbff", glow="#7fd8ff", glow_size=2),
               sprite(union(ring(6, 0.9), shift(circle(1.6), -2.5, -2.5)), "#ffffff", glow="#7fd8ff", glow_size=2)],
    "blitz": [sprite(bolt(12), "#e8f8ff", glow="#3fa0ff", glow_size=4), sprite(bolt(9), "#bfe0ff", glow="#3fa0ff", glow_size=3),
              sprite(star(4, 6, 1), "#ffffff", glow="#7fc0ff")],
    "herbst": [sprite(leaf(9, 0.6), "#ff8a2a", rim="#c84a10", glow="#ff6a1a", glow_size=1.2),
               sprite(leaf(8, -0.4), "#e8401a", rim="#a82a0a", glow="#ff4a1a", glow_size=1.2),
               sprite(leaf(7, 1.2), "#ffc23a", rim="#c88a10", glow="#ffb000", glow_size=1.2)],
    "schmetterlinge": [sprite(butterfly(11), "#3fb0ff", rim="#d0f0ff", glow="#3f80ff", glow_size=2),
                       sprite(butterfly(9), "#9b6bff", rim="#ece0ff", glow="#7a3cff", glow_size=2),
                       sprite(circle(1.6), "#d0f0ff", glow="#3fb0ff", glow_size=4)],
    "goldstaub": [sprite(star(4, 11, 1.8), "#fff1a0", glow="#ffb000", core="#ffffff"),
                  sprite(star(8, 7, 2.5, 0.2), "#ffd23f", glow="#ffb000"),
                  sprite(circle(2), "#fff1a0", glow="#ffb000", glow_size=4)],
    "schatten": [sprite(circle(7), "#1a0a2a", glow="#6a2aaa", glow_size=4), sprite(circle(5), "#2a0a3a", glow="#8a3cff", glow_size=4),
                 sprite(circle(2.5), "#c080ff", glow="#8a3cff", glow_size=4)],
    "federn": [sprite(feather(11), "#ffffff", rim="#aab4cc", glow="#e8eef8", glow_size=1.5),
               sprite(feather(9), "#f0f4ff", rim="#aab4cc", glow="#e8eef8", glow_size=1.5)],
    "regenbogen": [sprite(circle(3.5), c, glow=c, glow_size=5, core="#ffffff") for c in ("#ff4f6d", "#ffd23f", "#4fff8a", "#4fa8ff")],
    # Winterzauber 2026
    # Orbit: Plätzchen kreisen um die Figur
    "plaetzchen": [sprite(gingerman(1.25), "#b8702f", rim="#fbf8f4", glow="#ffb060", glow_size=1.5),
                   sprite(star(5, 11, 5, 0.3), "#d8a04c", rim="#fbf8f4", glow="#ffd27a", glow_size=1.5),
                   stripes(sprite(candycane(1.15), "#fbf7f2", glow="#ff9aa6", glow_size=1.5), (214, 31, 53)),
                   sprite(bell(1.1), "#ffd23f", rim="#b8860b", glow="#ffe48a", glow_size=2)],
    # Halo: Christbaumkugeln schweben im Kreis über dem Kopf
    "christbaumkugeln": [sprite(bauble(7.5), c2, rim=r2, glow=c2, glow_size=2.5, core="#ffffff") for c2, r2 in
                         (("#e8283a", "#7a0a14"), ("#ffcf3a", "#8a6a10"), ("#2fae4e", "#0f4a20"), ("#3f8bff", "#122e7a"))],
    # Fall: großes Schneegestöber mit 4 verschiedenen Flocken
    "schneegestoeber": [sprite(snowflake(11), "#ffffff", glow="#bfe4ff", glow_size=3),
                        sprite(union(snowflake(10), ring(4, 0.8)), "#f4fbff", glow="#9fd0ff", glow_size=2.5),
                        sprite(star(6, 8, 2.5), "#ffffff", glow="#cfe8ff", glow_size=2.5),
                        sprite(circle(2), "#ffffff", glow="#e0f0ff", glow_size=3)],
    # Rise: kleine Tannenbäumchen und Goldsterne steigen auf
    "tannenduft": [sprite(treeshape(1.2), "#2fae4e", rim="#145c2c", glow="#7aff9a", glow_size=2),
                   sprite(star(5, 10, 4), "#ffd23f", glow="#ffb000", glow_size=3, core="#ffffff"),
                   sprite(circle(2.4), "#ff3b4e", glow="#ff3b4e", glow_size=4, core="#ffffff")],
}


def write(path, sprites, mirror=True):
    w = len(sprites) * S * (2 if mirror else 1)
    pam = path + ".pam"
    with open(pam, "wb") as fh:
        fh.write(f"P7\nWIDTH {w}\nHEIGHT {S}\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n".encode())
        for j in range(S):
            for sp in sprites:
                row = sp[j]
                fh.write(b"".join(bytes(c) for c in row))
                if mirror:
                    fh.write(b"".join(bytes(c) for c in reversed(row)))
    subprocess.run(["convert", pam, "-depth", "8", f"PNG32:{path}"], check=True)
    os.remove(pam)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for aid, sprites in AURAS.items():
        write(os.path.join(out, f"{aid}.png"), sprites)
        write(os.path.join(out, f"{aid}_icon.png"), sprites, mirror=False)
        print("Aura", aid, len(sprites))


if __name__ == "__main__":
    main()
