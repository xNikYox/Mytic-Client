"""Aura-Sprites „Winterzauber II“ in doppelter Auflösung (64x64 statt 32x32).

Format wie cosmetics/aurasprites.py: Textur je Aura <id>.png, je Sprite 128x64 (links vorne, rechts gespiegelt), nebeneinander;
<id>_icon.png = alle Sprites nebeneinander, je 32x32 (wie bisher). Die Mod rechnet die UVs relativ (1/sprites), höhere Auflösung braucht keinen Code.
Neu gegenüber aurasprites.sprite(): mehrere Ebenen je Sprite (Form + Farbfunktion mit Schattierung/Glanz), danach weicher Schein.
Aufruf: xmasauras2.py <ausgabeordner> (build.py: build/auras, Werte für den Katalog in meta2.json)
"""
import json, math, os, subprocess, sys

S = 64
TAU = 2 * math.pi


def cl(v, a=0.0, b=1.0): return a if v < a else b if v > b else v
def ss(e0, e1, x): t = cl((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t)
def hx(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
def mix(a, b, t): t = cl(t); return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def rnd(i): v = math.sin(i * 12.9898 + 78.233) * 43758.5453; return v - math.floor(v)


def seg(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    u = cl(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy + 1e-9))
    return math.hypot(px - ax - u * dx, py - ay - u * dy)


# ------------------------------------------------------------------ Formen (Abstandsfunktion, < 0 = innen; Pixel, Mitte = 0)
def circle(r): return lambda x, y: math.hypot(x, y) - r
def ellipse(rx, ry, rot=0.0):
    c, s = math.cos(rot), math.sin(rot)
    def f(x, y):
        u, v = x * c + y * s, -x * s + y * c
        return (math.hypot(u / rx, v / ry) - 1) * min(rx, ry)
    return f
def lines(segs, w): return lambda x, y: min(seg(x, y, *s) for s in segs) - w
def union(*fs): return lambda x, y: min(f(x, y) for f in fs)
def subtract(a, b): return lambda x, y: max(a(x, y), -b(x, y))
def shift(f, dx, dy): return lambda x, y: f(x - dx, y - dy)
def star(points, r1, r2, rot=0.0, sharp=3):
    def f(x, y):
        a = math.atan2(y, x) + rot
        k = (math.cos(a * points) + 1) / 2
        return math.hypot(x, y) - (r2 + (r1 - r2) * k ** sharp)
    return f


def leaf(cx, cy, length, width, rot):
    """Spitzes Blatt (Mistel): Ansatz bei (cx, cy), zeigt in Richtung rot."""
    c, s = math.cos(rot), math.sin(rot)
    def f(x, y):
        u, v = (x - cx) * c + (y - cy) * s, -(x - cx) * s + (y - cy) * c
        if u < 0 or u > length: return min(math.hypot(u, v), math.hypot(u - length, v)) + 0.01 if True else 99
        half = width * math.sin(math.pi * u / length) ** 0.75
        return abs(v) - half
    return f


def leaf_uv(cx, cy, rot):
    c, s = math.cos(rot), math.sin(rot)
    return lambda x, y: ((x - cx) * c + (y - cy) * s, -(x - cx) * s + (y - cy) * c)


# ------------------------------------------------------------------ Zeichnen
def sprite(layers, glow=None, glow_size=5.0, glow_amt=0.5, sparkle=None):
    """layers: [(form, farbe(x, y, d) -> rgb oder (rgb, deckkraft))], später gezeichnete liegen oben.
    glow: Farbe des weichen Scheins um die vereinigte Form."""
    px = []
    for j in range(S):
        row = []
        for i in range(S):
            x, y = i + 0.5 - S / 2, j + 0.5 - S / 2
            col, a, dmin = (0, 0, 0), 0.0, 99.0
            for shape, paint in layers:
                d = shape(x, y)
                dmin = min(dmin, d)
                cov = ss(0.6, -0.6, d)
                if cov <= 0: continue
                p = paint(x, y, d)
                if len(p) == 2 and isinstance(p[0], tuple): c, op = p
                else: c, op = p, 1.0
                cov *= op
                # "over"-Mischung
                na = cov + a * (1 - cov)
                col = mix(col, c, cov / na if na > 0 else 0) if a > 0 else c
                a = na
            if glow is not None and glow_size > 0:
                edge = ss(S / 2, S / 2 - 7, max(abs(x), abs(y)))
                g = math.exp(-max(0, dmin) / glow_size) * glow_amt * edge
                if g > a:
                    gc = hx(glow) if isinstance(glow, str) else glow
                    col = mix(gc, col, a / g)
                    a = g
            if sparkle:
                for sx, sy, r, sc in sparkle:
                    dx, dy = abs(x - sx), abs(y - sy)
                    k = max(0.0, 1 - (min(dx, dy) / 0.9)) * max(0.0, 1 - max(dx, dy) / r)
                    k = max(k, math.exp(-math.hypot(dx, dy) / 1.4) * 0.9)
                    if k > 0:
                        col = mix(col, hx(sc), k / max(a + k * (1 - a), 1e-6) * 1.0)
                        a = a + k * (1 - a)
            row.append((int(col[0]), int(col[1]), int(col[2]), int(cl(a) * 255)))
        px.append(row)
    return px


def shade_ball(base, dark, light, cx, cy, r, lx=-0.45, ly=-0.55):
    """Kugelschattierung: Licht von links oben, dunkler Rand, kleines Glanzlicht."""
    B, D, L = hx(base), hx(dark), hx(light)
    def f(x, y, d):
        nx, ny = (x - cx) / r, (y - cy) / r
        nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
        lam = cl(nx * lx + ny * ly + nz * 0.7)
        c = mix(D, B, ss(0.0, 0.55, lam))
        c = mix(c, L, ss(0.75, 1.05, lam))
        spec = math.exp(-((nx + 0.38) ** 2 + (ny + 0.42) ** 2) / 0.018)
        c = mix(c, (255, 255, 255), spec)
        return mix(c, D, ss(-2.0, 0, d) * 0.6)
    return f


def flat(c): C = hx(c); return lambda x, y, d: C


def ramp_y(top, bot, y0, y1, rim=None, rim_w=1.6):
    T, Bt = hx(top), hx(bot)
    R = hx(rim) if rim else None
    def f(x, y, d):
        c = mix(T, Bt, (y - y0) / (y1 - y0))
        if R: c = mix(c, R, ss(-rim_w, -0.3, d))
        return c
    return f


# ------------------------------------------------------------------ 1. Sternschnuppen (Kopf unten, Schweif nach oben, leicht schräg)
def _shoot_sprite(trail_shape, trail_paint, H, head_col, hx0, hy0, sparks):
    head = shift(star(4, 9.5, 2.2, 0, 3), hx0, hy0)
    core = lambda x, y: math.hypot(x - hx0, y - hy0) - 3.4
    return sprite([(trail_shape, trail_paint),
                   (head, lambda x, y, d: mix(H, (255, 255, 255), ss(-0.5, -3.5, d))),
                   (core, lambda x, y, d: (255, 255, 255))],
                  glow=head_col, glow_size=4.0, glow_amt=0.55, sparkle=sparks)


# ------------------------------------------------------------------ 2. Mistelzweig
def mistletoe(variant):
    leaves = [(0, -2, 22, 6.2, -2.35), (0, -2, 22, 6.2, -0.8)] if variant == 0 else [(0, -4, 20, 6, -2.6), (0, -4, 21, 6, -0.55), (0, -4, 17, 5, -1.6)]
    stem = lines([(0, -2, 0, 14), (0, 6, -6, 13)], 1.3)
    LD, LM, LL = hx('#0f3b2c'), hx('#2f7d4c'), hx('#7fc46a')
    layers = [(stem, flat('#4a6a2a'))]
    for cx, cy, ln, w, rot in leaves:
        uv = leaf_uv(cx, cy, rot)
        def paint(x, y, d, uv=uv, ln=ln):
            u, v = uv(x, y)
            # Licht auf der oberen Blatthälfte, Mittelrippe, dunkler Rand
            c = mix(LM, LL, ss(0, 5, -v) * 0.7 * (1 - u / ln))
            c = mix(c, LD, ss(1, 5, v) * 0.7)
            if abs(v) < 0.6 and 2 < u < ln - 3: c = mix(c, LL, 0.6)
            return mix(c, LD, ss(-1.6, -0.2, d))
        layers.append((leaf(cx, cy, ln, w, rot), paint))
    berries = [(-3, 4), (3.2, 3.6), (0, 7.4)] if variant == 0 else [(-3.5, 2), (3, 2.5), (-0.5, 6), (5, 7)]
    for bx, by in berries:
        layers.append((circle(3.3) if False else (lambda x, y, bx=bx, by=by: math.hypot(x - bx, y - by) - 3.2),
                       shade_ball('#f4f8ff', '#a9bfdf', '#ffffff', bx, by, 3.2)))
    if variant == 1:
        # rotes Schleifchen oben am Stiel
        bow = union(shift(ellipse(5.5, 3.4, 0.35), -5, 13), shift(ellipse(5.5, 3.4, -0.35), 5, 13),
                    lines([(-1, 15, -5, 23), (1, 15, 5, 22)], 1.4))
        layers.insert(0, (bow, lambda x, y, d: mix(mix(hx('#b3172c'), hx('#ff5a64'), ss(14, 10, y)), hx('#7a0f1e'), ss(-1.6, -0.2, d))))
        layers.append((lambda x, y: math.hypot(x, y - 13) - 2.2, flat('#d92a3f')))
    return sprite(layers, glow='#bfffd0', glow_size=3.0, glow_amt=0.4)


# ------------------------------------------------------------------ 3. Glöckchen
def bell_shape(s):
    def f(x, y):
        if y < -10 * s: return math.hypot(x, y + 10 * s) - 3.2 * s
        half = 4.8 * s + 6.2 * s * ((y + 10 * s) / (19 * s)) ** 2.6
        body = max(abs(x) - half, y - 9 * s, -y - 10.5 * s)
        lip = max(abs(x) - (half + 0.7 * s), abs(y - 8.6 * s) - 1.3 * s)
        return min(body, lip)
    return f


def golden_bell(swing, with_bow=True):
    s = 1.0
    c, sn = math.cos(swing), math.sin(swing)
    rot = lambda f: (lambda x, y: f(x * c + (y + 12) * sn, -x * sn + (y + 12) * c - 12))
    G = [hx(h) for h in ('#7a4a10', '#b8801c', '#e8b23a', '#ffd96a', '#fff3b8')]
    def gold(x, y, d):
        u = x * c + (y + 12) * sn
        k = cl(0.55 - u / 22)  # Licht von links
        i = k * 4
        col = mix(G[int(i)], G[min(4, int(i) + 1)], i - int(i))
        if abs(u + 5) < 1.2: col = mix(col, G[4], 0.8)  # Glanzlinie
        return mix(col, G[0], ss(-1.8, -0.2, d) * 0.8)
    body = rot(bell_shape(s))
    clap = rot(lambda x, y: math.hypot(x, y - 11.5) - 2.8)
    slot = rot(lambda x, y: max(abs(x) - 0.9, abs(y - 3) - 3.5))
    layers = [(clap, shade_ball('#b8801c', '#4a2614', '#ffd96a', -sn * 23.5, c * 23.5 - 12, 2.8)),
              (body, gold),
              (slot, flat('#4a2614'))]
    if with_bow:
        bow = rot(union(shift(ellipse(6, 3.6, 0.4), -6, -17), shift(ellipse(6, 3.6, -0.4), 6, -17), circle(0) if False else (lambda x, y: math.hypot(x, y + 17) - 2.6)))
        layers.append((bow, lambda x, y, d: mix(mix(hx('#b3172c'), hx('#ff5a64'), ss(-15, -21, y)), hx('#7a0f1e'), ss(-1.6, -0.2, d))))
    return sprite(layers, glow='#ffd96a', glow_size=4.0, glow_amt=0.45)


def note_sprite():
    head = union(shift(ellipse(6, 4.4, -0.4), -6, 13), shift(ellipse(6, 4.4, -0.4), 12, 9))
    stems = lines([(-1.2, 12, -1.2, -12), (16.8, 8, 16.8, -16)], 1.3)
    beam = lambda x, y: max(seg(x, y, -1.2, -12, 16.8, -16) - 2.4, 0) if True else 0
    shape = union(head, stems, beam)
    return sprite([(shape, ramp_y('#fff3b8', '#e8b23a', -16, 16, rim='#b8801c'))], glow='#ffd96a', glow_size=4, glow_amt=0.5,
                  sparkle=[(-14, -14, 5, '#ffffff'), (20, 16, 3.5, '#fff3b8')])


# ------------------------------------------------------------------ 4. Glitzerflocken
def flake(r, arm_w, col, rim, rot=0.0, plate=False):
    segs = []
    for k in range(6):
        a = k * math.pi / 3 + rot
        ex, ey = math.cos(a) * r, math.sin(a) * r
        segs.append((0, 0, ex, ey))
        for t, l in ((0.38, 0.28), (0.62, 0.34), (0.84, 0.2)):
            bx, by = ex * t, ey * t
            for side in (-1, 1):
                b = a + side * 1.0
                segs.append((bx, by, bx + math.cos(b) * r * l, by + math.sin(b) * r * l))
    shape = lines(segs, arm_w)
    if plate: shape = union(shape, star(6, r * 0.34, r * 0.22, rot))
    C, R = hx(col), hx(rim)
    def paint(x, y, d):
        k = math.hypot(x, y) / r
        c = mix((255, 255, 255), C, ss(0.1, 0.9, k))
        # Facetten-Glanz: Arme schimmern je nach Winkel
        ang = math.atan2(y, x) - rot
        c = mix(c, (255, 255, 255), 0.35 * max(0.0, math.cos(ang * 6 + 1.2)) * (1 - k))
        return mix(c, R, ss(-arm_w, -0.2, d) * 0.55)
    hole = lambda x, y: math.hypot(x, y) - 2.2 if plate else 99
    return sprite([(shape, paint)] + ([(hole, flat('#ffffff'))] if plate else []), glow=col, glow_size=3.5, glow_amt=0.45,
                  sparkle=[(r * 0.85 * math.cos(rot + 0.52), r * 0.85 * math.sin(rot + 0.52), 4.5, '#ffffff')])


def glint():
    shape = union(star(4, 22, 1.4, 0, 6), star(4, 10, 1.2, math.pi / 4, 6))
    return sprite([(shape, lambda x, y, d: mix((255, 255, 255), hx('#ffe9a0'), ss(0, 18, math.hypot(x, y))))],
                  glow='#ffd96a', glow_size=4.5, glow_amt=0.6)


# ------------------------------------------------------------------ 5. Polarlicht-Vorhang
def curtain(c_top, c_mid, c_bot, seed, curve):
    T, M, B = hx(c_top), hx(c_mid), hx(c_bot)
    def shape(x, y):
        cx = curve * math.sin((y + 32) / 64 * math.pi * 1.2 + seed)
        w = 11 + 3 * math.sin(y / 9 + seed * 2)
        return abs(x - cx) - w
    def paint(x, y, d):
        v = (y + 32) / 64
        c = mix(T, M, ss(0.0, 0.45, v)) if v < 0.5 else mix(M, B, ss(0.5, 1.0, v))
        cx = curve * math.sin((y + 32) / 64 * math.pi * 1.2 + seed)
        # senkrechte Strahlen
        rays = 0.5 + 0.5 * math.sin((x - cx) * 1.3 + seed * 5) * math.sin((x - cx) * 0.47 + seed)
        c = mix(c, (255, 255, 255), 0.25 * rays * ss(0.6, 1.0, v))
        a = ss(0.0, 0.28, v) * ss(1.0, 0.72, v) * (0.45 + 0.55 * rays) * ss(0, -8, d)
        # helle Unterkante (wie echtes Polarlicht)
        a = max(a, ss(0.62, 0.78, v) * ss(0.92, 0.8, v) * 0.95 * ss(0, -5, d))
        return c, a
    return sprite([(shape, paint)], glow=c_mid, glow_size=6, glow_amt=0.22)


# ------------------------------------------------------------------ Liste + Metadaten
META = [
    ("sternschnuppen", "Sternschnuppen", "Leuchtende Sternschnuppen mit funkelndem Schweif zischen schräg an der Figur vorbei nach unten.",
     "shoot", 3.5, 0.55, ["#ffffff", "#ffd96a", "#bfe4ff"], "neues Muster „shoot“ (AuraFx/AuraLayer, alle Versionen)"),
    ("mistelzauber", "Mistelzauber", "Kleine Mistelzweige mit Perlbeeren und roten Schleifchen schweben als Kranz über dem Kopf.",
     "halo", 4, 0.2, ["#2f7d4c", "#f4f8ff", "#d92a3f"], None),
    ("gloeckchen", "Glöckchenklang", "Goldene Glöckchen mit roter Schleife schwingen um die Figur, dazwischen klingen Noten.",
     "orbit", 3, 0.2, ["#ffd96a", "#e8b23a", "#d92a3f"], None),
    ("glitzerschnee", "Glitzerschnee", "Fein gezeichnete Eiskristalle in Silber, Eisblau und Gold wirbeln als Spirale hoch, dazwischen Glanzblitze.",
     "spiral", 7, 0.15, ["#f4f8ff", "#a9d8ff", "#ffd96a"], None),
    ("polarlichtband", "Polarlichtband", "Schimmernde Polarlicht-Vorhänge in Grün, Türkis und Violett ziehen als Band um die Figur.",
     "orbit", 9, 0.32, ["#3dff9a", "#2cc6a0", "#9b6bff"], None),
]


def build():
    return {
        "sternschnuppen": [_shoot(h, t, tilt) for h, t, tilt in (("#ffffff", "#bfe4ff", 0.38), ("#ffd96a", "#ffb040", 0.30), ("#bfe4ff", "#3fa8ff", 0.45))],
        "mistelzauber": [mistletoe(0), mistletoe(1)],
        "gloeckchen": [golden_bell(0.25), golden_bell(-0.3, with_bow=False), note_sprite()],
        "glitzerschnee": [flake(22, 1.35, '#a9d8ff', '#3fa8ff', 0.0, plate=True), flake(19, 1.2, '#f4f8ff', '#a9bfdf', 0.26),
                          flake(17, 1.2, '#ffd96a', '#b8801c', 0.12, plate=True), glint()],
        "polarlichtband": [curtain('#3fa8ff', '#3dff9a', '#2cc6a0', 0.3, 5), curtain('#9b6bff', '#2cc6a0', '#3dff9a', 1.7, -6),
                           curtain('#9b6bff', '#3fa8ff', '#3dff9a', 3.1, 4)],
    }


def _shoot(head_col, trail_col, tilt):
    hx0, hy0 = -math.sin(tilt) * 14, 20.0
    tx, ty = hx0 + math.sin(tilt) * 46, hy0 - math.cos(tilt) * 46
    T = hx(trail_col)
    def trail_shape(x, y):
        dx, dy = tx - hx0, ty - hy0
        u = cl(((x - hx0) * dx + (y - hy0) * dy) / (dx * dx + dy * dy))
        return math.hypot(x - hx0 - u * dx, y - hy0 - u * dy) - 4.6 * (1 - u) ** 1.2
    def trail_paint(x, y, d):
        dx, dy = tx - hx0, ty - hy0
        u = cl(((x - hx0) * dx + (y - hy0) * dy) / (dx * dx + dy * dy))
        streak = 0.5 + 0.5 * math.sin(u * 46 + (x - y) * 0.5)
        c = mix((255, 255, 255), T, ss(0.0, 0.45, u) + 0.12 * streak)
        return c, cl((1 - u) ** 1.5 * (0.7 + 0.3 * streak))
    sparks = []
    for i, l in enumerate((15, 26, 36)):
        off = (rnd(i * 3 + tilt * 10) - 0.5) * 10
        sparks.append((hx0 + math.sin(tilt) * l + off * math.cos(tilt), hy0 - math.cos(tilt) * l + off * math.sin(tilt), 3.2 - i * 0.6, '#ffffff'))
    return _shoot_sprite(trail_shape, trail_paint, hx(head_col), head_col, hx0, hy0, sparks)


def write(path, sprites, mirror=True):
    w = len(sprites) * S * (2 if mirror else 1)
    pam = path + ".pam"
    with open(pam, "wb") as fh:
        fh.write(f"P7\nWIDTH {w}\nHEIGHT {S}\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n".encode())
        for j in range(S):
            for sp in sprites:
                row = sp[j]
                fh.write(b"".join(bytes(c) for c in row))
                if mirror: fh.write(b"".join(bytes(c) for c in reversed(row)))
    subprocess.run(["convert", pam, "-depth", "8", f"PNG32:{path}"], check=True)
    os.remove(pam)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    auras = build()
    meta = []
    for aid, name, desc, pattern, rate, size, colors, ext in META:
        sp = auras[aid]
        write(os.path.join(out, f"{aid}.png"), sp)
        # Menü-Bild wie bisher: alle Sprites nebeneinander, je 32x32 (verkleinert)
        icon = os.path.join(out, f"{aid}_icon.png")
        write(icon, sp, mirror=False)
        subprocess.run(["convert", icon, "-filter", "Box", "-resize", f"{len(sp) * 32}x32!", "-depth", "8", f"PNG32:{icon}"], check=True)
        meta.append(dict(id=aid, name=name, desc=desc, pattern=pattern, rate=rate, size=size, colors=colors, sprites=len(sp), ext=ext))
        print("Aura", aid, len(sp), flush=True)
    json.dump(meta, open(os.path.join(out, "meta2.json"), "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
