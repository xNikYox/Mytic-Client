# Weihnachts-Umhänge „Winterzauber II“ (Nordlichtwald, Kaminabend, Weihnachtsmarkt, Schlittenflug, Adventskranz, Schneemann-Strick):
# 24 Bilder, gezeichnet 12-fach wie capes.py. Unterschied zu Set 1: ausgeliefert wird die volle Zeichen-Auflösung
# (768x384, im Katalog "scale": 12 je Umhang – build.py verkleinert diese Umhänge nicht). frame() wie in capes.py.
# Mehr Details: geordnetes Dithering (Bayer 4x4 auf 3er-Zellen = 1/4 Modell-Pixel) zwischen festen Palettenstufen,
# Randlichter, Glanzpunkte, weiche Leucht-Ebene, mehrere Tiefen-Ebenen.
# Aufruf: python3 xmascapes2.py AUSGABE [name ...] (build.py ruft es mit build/frames auf)
import json, math, os, shutil, subprocess, sys
from multiprocessing import Pool

OUT = sys.argv[1]; ONLY = sys.argv[2:]
N = 24; K = 12; FW, FH = 10 * K, 16 * K; W, H = 64 * K, 32 * K
TMP = os.path.join(OUT, "tmp"); os.makedirs(TMP, exist_ok=True)
TAU = 2 * math.pi

def cl(v, a=0.0, b=1.0): return a if v < a else b if v > b else v
def lerp(a, b, t): t = cl(t); return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def hx(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
def ramp(stops, v):
    v = cl(v)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if v <= p1: return lerp(c0, c1, (v - p0) / max(1e-6, p1 - p0))
    return stops[-1][1]
def add(a, b, k=1.0): return tuple(min(255, a[i] + b[i] * k) for i in range(3))
def mul(a, k): return tuple(a[i] * k for i in range(3))
def ss(e0, e1, x): t = cl((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t)
def rnd(i): v = math.sin(i * 12.9898 + 78.233) * 43758.5453; return v - math.floor(v)
def rgba(c, a=1.0): return f"rgba({int(c[0])},{int(c[1])},{int(c[2])},{cl(a):.3f})"
def run(args): subprocess.run(["convert", *args], check=True)

# ------------------------------------------------------------------ Dithering
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
def bay(x, y, cell=3): return (BAYER[int(y // cell) % 4][int(x // cell) % 4] + 0.5) / 16
def dpal(pal, v, x, y, cell=3):
    """Wert 0..1 auf feste Palettenstufen, Übergänge geordnet gedithert (Pixel-Art statt weicher Verlauf)."""
    v = cl(v) * (len(pal) - 1)
    i = int(v)
    if i >= len(pal) - 1: return pal[-1]
    return pal[i + 1] if v - i > bay(x, y, cell) else pal[i]
def P(*hs): return [hx(h) for h in hs]

NIGHT = P('#060a1e', '#0b1230', '#111b42', '#16234f', '#1d2d5f', '#24386f')
SNOW = P('#7f95bd', '#a9bfdf', '#c3d4ee', '#d6e4f7', '#e8f0fc', '#f4f8ff')
FIR = P('#0a2a20', '#0f3b2c', '#1d5a3c', '#2f7d4c', '#4fa35e')
RED = P('#4a0812', '#7a0f1e', '#b3172c', '#d92a3f', '#ff5a64')
GOLD = P('#7a4a10', '#b8801c', '#e8b23a', '#ffd96a', '#fff3b8')
WOOD = P('#2e170c', '#4a2614', '#7a4422', '#a5632c', '#c98a4e')
CANDLE = P('#ff9a2a', '#ffc24a', '#ffe8a0')

def raster(fn, t, path):
    buf = bytearray()
    for y in range(FH):
        for x in range(FW):
            c = fn(x + 0.5, y + 0.5, t)
            buf += bytes((int(cl(c[0], 0, 255)), int(cl(c[1], 0, 255)), int(cl(c[2], 0, 255))))
    open(path + ".rgb", "wb").write(buf)
    run(["-size", f"{FW}x{FH}", "-depth", "8", f"rgb:{path}.rgb", path])
    os.remove(path + ".rgb")

def frame(design, f):
    t = f / N
    base = f"{TMP}/{design.name}_bg{f}.png"
    raster(design.bg, t, base)
    layers = [base]
    sharp, glow = design.draws(t)
    if glow:
        g = f"{TMP}/{design.name}_gl{f}.png"
        run(["-size", f"{FW}x{FH}", "xc:none", *glow, "-blur", f"0x{design.glow_blur}", g])
        layers += [g, "-compose", "screen", "-composite"]
    if sharp:
        s = f"{TMP}/{design.name}_sh{f}.png"
        run(["-size", f"{FW}x{FH}", "xc:none", *sharp, s])
        layers += [s, "-compose", "over", "-composite"]
    front = f"{TMP}/{design.name}_front{f}.png"
    run([layers[0], *layers[1:], front])
    side = f"{TMP}/{design.name}_side{f}.png"
    a, b = design.edge(t)
    run(["-size", f"{K}x{FH}", f"gradient:{rgba(a)}-{rgba(b)}", side])
    # volle Auflösung ausliefern (768x384, "scale": 12)
    run(["-size", f"{W}x{H}", "xc:none",
         front, "-geometry", f"+{K}+{K}", "-composite",
         "(", front, "-flop", "-modulate", "45,80", ")", "-geometry", f"+{2 * K + FW}+{K}", "-composite",
         side, "-geometry", f"+0+{K}", "-composite", side, "-geometry", f"+{K + FW}+{K}", "-composite",
         "(", "-size", f"{FW}x{K}", f"xc:{rgba(a)}", ")", "-geometry", f"+{K}+0", "-composite",
         "(", "-size", f"{FW}x{K}", f"xc:{rgba(b)}", ")", "-geometry", f"+{K + FW}+0", "-composite",
         "-depth", "8", f"PNG32:{OUT}/{design.name}_{f}.png"])
    for p in (base, front, side, f"{TMP}/{design.name}_gl{f}.png", f"{TMP}/{design.name}_sh{f}.png"):
        if os.path.exists(p): os.remove(p)

# ------------------------------------------------------------------ gemeinsame Zeichen-Helfer
def snow(t, n, seed, color=(245, 250, 255), smin=0.6, smax=1.6, speed=(1, 2), sway=4, alpha=0.9, ymax=FH, ymin=0):
    """Fallende Flocken, nahtlos (jede Flocke fällt in 24 Bildern genau 1- oder 2-mal durch)."""
    d = []
    for i in range(n):
        sp = speed[0] + int(rnd(seed + i + 40) * (speed[1] - speed[0] + 1))
        y = ymin + ((t * sp + rnd(seed + i)) % 1) * (ymax - ymin + 8) - 4
        x = rnd(seed + i + 80) * FW + sway * math.sin(TAU * (t * sp + rnd(seed + i + 3)))
        r = smin + (smax - smin) * rnd(seed + i + 7)
        d += ["-fill", rgba(color, alpha * (0.6 + 0.4 * rnd(seed + i + 9))), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
    return d

def twinkle(x, y, a, r=4, col=(255, 255, 255), w=0.9):
    return ["-stroke", rgba(col, a), "-strokewidth", f"{w}", "-draw", f"line {x - r:.1f},{y:.1f} {x + r:.1f},{y:.1f}",
            "-draw", f"line {x:.1f},{y - r:.1f} {x:.1f},{y + r:.1f}", "-stroke", "none"]

def stars(t, n, seed, ymax, skip=None, col=(255, 255, 255)):
    """Funkelnde Sterne: kleiner Punkt immer, Kreuz-Glanz nur auf dem Höhepunkt (nahtlos: ganzzahlige Frequenzen)."""
    d = []
    for i in range(n):
        x, y = 3 + rnd(seed + i) * (FW - 6), 3 + rnd(seed + i + 50) * ymax
        if skip and skip(x, y): continue
        ph = rnd(seed + i + 90); fq = 1 + int(rnd(seed + i + 70) * 2)
        a = 0.5 + 0.5 * math.sin(TAU * (t * fq + ph))
        r = 0.6 + 0.5 * rnd(seed + i + 30)
        d += ["-fill", rgba(col, 0.45 + 0.55 * a), "-draw", f"rectangle {x - r:.1f},{y - r:.1f} {x + r:.1f},{y + r:.1f}"]
        if a > 0.8 and rnd(seed + i + 11) < 0.5: d += twinkle(x, y, (a - 0.8) * 4, 2.5 + 2 * rnd(seed + i), col, 0.7)
    return d

def tree(x, y, tx, base, w, h, tiers=4):
    """Gestufte Tanne: None außerhalb, sonst (k = 0 oben..1 unten, step = 0 Astoberkante..1 Astunterkante, seite -1..1)."""
    top = base - h
    if not (top <= y <= base): return None
    k = (y - top) / h
    step = (k * tiers) % 1
    half = w / 2 * (0.28 + 0.72 * step) * (0.3 + 0.7 * k)
    if abs(x - tx) > half: return None
    return k, step, (x - tx) / max(half, 0.5)

class Design:
    glow_blur = 3
    def draws(self, t): return [], []
    def edge(self, t): return (180, 80, 255), (60, 20, 120)

# ==================================================================== 1. Nordlichtwald
AUR = P('#1a6b4a', '#2bd17e', '#5dffa0', '#b8ffd8')
class NorthWood(Design):
    name = "nordlichtwald"
    FAR = [(x, 118 + 4 * math.sin(x * 0.7), 9 + 5 * rnd(x), 18 + 12 * rnd(x + 3)) for x in range(-4, 126, 7)]
    MID = [(6, 150, 20, 52), (30, 146, 16, 44), (52, 152, 14, 34), (84, 145, 18, 48), (106, 150, 20, 56), (70, 156, 10, 26)]
    NEAR = [(-2, 196, 34, 92), (124, 194, 36, 100)]
    @staticmethod
    def ground(x): return 160 + 5 * math.sin(x / 21 + 1.3) + 2.5 * math.sin(x / 8.1)
    def aurora(self, x, y, t):
        """Vorhänge: helle, scharfe Unterkante, Strahlen nach oben, Farbe grün → türkis → violett."""
        col, tot = (0, 0, 0), 0.0
        for j, (yb0, amp, wl, ph, fq) in enumerate([(78, 10, 19, 0.0, 1), (58, 8, 13, 2.1, 2), (96, 6, 25, 4.0, 1)]):
            yb = yb0 + amp * math.sin(x / wl + TAU * t * fq + ph) + 3 * math.sin(x / 5.3 - TAU * t * 2 + j)
            if y > yb + 2: continue
            h = (yb - y) / (46 - 8 * j)
            rays = 0.55 + 0.45 * math.sin(x * 0.85 + 2.2 * math.sin(x / 9 + TAU * t + j) + TAU * t * 2 * (1 if j % 2 else -1))
            rays *= 0.7 + 0.3 * math.sin(x * 0.31 - TAU * t + j * 1.7)
            inten = (ss(-0.05, 0.06, h) * math.exp(-max(h, 0) * 1.7)) * rays * (0.95 - 0.2 * j)
            c = ramp([(0, hx('#7dffb2')), (0.35, hx('#2ee6b0')), (0.75, hx('#2bb3d8')), (1.2, hx('#8a5cff')), (2, hx('#5a2a9a'))], h)
            col = add(col, c, inten); tot += inten
        return col, tot
    def bg(self, x, y, t):
        c = dpal(NIGHT, 0.95 - y / 170 * 0.9 + 0.08 * math.sin(x / 30), x, y)
        a, tot = self.aurora(x, y, t)
        c = add(c, a, 0.85)
        # ferne Tannen (bläulich, vom Polarlicht angestrahlt)
        for tx, base, w, h in self.FAR:
            r = tree(x, y, tx, base, w, h, 3)
            if r:
                k, step, side = r
                c = dpal(P('#0e1b36', '#14284a', '#1d3a5c', '#3a5a7c'), 0.25 + 0.3 * (1 - k) - 0.2 * side + (0.5 if step < 0.18 else 0), x, y, 2)
        if y > 118 + 4 * math.sin(x * 0.7) - 0: c = lerp(c, dpal(P('#3a5480', '#56709c', '#7b94bf'), 0.5 + 0.3 * math.sin(x / 13), x, y), ss(117, 121, y))
        # mittlere Tannen
        for tx, base, w, h in self.MID:
            r = tree(x, y, tx, base, w, h, 4)
            if r:
                k, step, side = r
                v = 0.55 - 0.35 * side - 0.25 * k
                c = dpal(FIR, v, x, y, 2)
                if step < 0.24 and side < 0.75: c = dpal(SNOW, 0.85 - side * 0.3 - step * 1.5, x, y, 2)
                c = add(c, (60, 255, 170), 0.08 * (1 - k) * max(0, -side))  # Polarlicht-Reflex auf der linken Seite
        # Schneeboden
        gy = self.ground(x)
        if y > gy:
            v = 0.95 - (y - gy) / 45 + 0.1 * math.sin(x / 9 + y / 5)
            c = dpal(SNOW, v, x, y)
            c = add(c, (60, 255, 170), 0.10 * math.exp(-(y - gy) / 12))
            # Schneewehen-Schatten
            if (y - gy) % 13 < 1.5 and rnd(int(x // 6) + int(y // 13) * 31) < 0.5: c = mul(c, 0.88)
        # nahe Tannen (fast schwarz, Schneekanten)
        for tx, base, w, h in self.NEAR:
            r = tree(x, y, tx, base, w, h, 5)
            if r:
                k, step, side = r
                c = dpal(P('#04140e', '#082219', '#0f3b2c'), 0.6 - 0.4 * abs(side), x, y, 2)
                if step < 0.15: c = dpal(SNOW, 0.7 - step * 2, x, y, 2)
        return c
    def draws(self, t):
        d, g = [], []
        d += stars(t, 34, 700, 95)
        # Schneefunkeln am Boden
        for i in range(14):
            x = rnd(i + 900) * FW; y = self.ground(x) + 3 + rnd(i + 950) * 25
            a = max(0, math.sin(TAU * (t * 2 + rnd(i + 990)))) ** 6
            if a > 0.05: d += twinkle(x, y, a, 3, (230, 255, 245), 0.8)
        # Leuchten der Vorhang-Unterkante
        for x in range(0, FW, 4):
            yb = 78 + 10 * math.sin(x / 19 + TAU * t) + 3 * math.sin(x / 5.3 - TAU * t * 2)
            g += ["-fill", rgba(hx('#5dffa0'), 0.35), "-draw", f"rectangle {x},{yb - 6:.1f} {x + 4},{yb + 1:.1f}"]
        return d, g
    def edge(self, t): return lerp(hx('#5dffa0'), hx('#8a5cff'), 0.5 + 0.5 * math.sin(TAU * t)), hx('#0b1230')

# ==================================================================== 2. Kaminabend
BRICK = P('#3a1410', '#5a2218', '#7a3424', '#94442c', '#b05a38')
class Fireside(Design):
    name = "kaminabend"
    MANTEL = 54
    OPEN = (26, 116, 94, 184)  # Feueröffnung
    STOCK = [(24, RED, hx('#f4f8ff')), (60, FIR, hx('#f4f8ff')), (96, P('#a9bfdf', '#d6e4f7', '#f4f8ff', '#ffffff'), hx('#d92a3f'))]
    def flick(self, t): return 0.86 + 0.08 * math.sin(TAU * t * 4) + 0.06 * math.sin(TAU * t * 7 + 1)
    def fire(self, x, y, t):
        x0, y0, x1, y1 = self.OPEN
        if not (x0 < x < x1 and y0 < y < y1 - 10): return 0
        best = 0
        for i in range(6):
            cx = 36 + i * 9.6 + 2.5 * math.sin(TAU * (t * 2 + i * 0.3))
            hgt = 34 + 14 * rnd(i + 5) + 8 * math.sin(TAU * (t * (2 + i % 2) + rnd(i)))
            yb = y1 - 12
            v = (yb - y) / hgt
            if v < 0 or v > 1: continue
            wob = 3 * math.sin(y / 5 + TAU * t * 3 + i) * v
            wdt = 7.5 * (1 - v) ** 0.8 + 0.5
            dd = 1 - abs(x - cx - wob) / wdt
            if dd > 0: best = max(best, dd * (1 - v * 0.6))
        # Flackern im Inneren
        best *= 0.8 + 0.2 * math.sin(x * 0.7 + y * 0.5 - TAU * t * 4)
        return best
    def bg(self, x, y, t):
        fl = self.flick(t)
        fx, fy = 60, 160
        light = fl * math.exp(-math.hypot(x - fx, (y - fy) * 1.2) / 55)
        # Tapete über dem Sims: dunkles Weinrot mit goldenen Rauten
        if y < self.MANTEL:
            c = dpal(P('#1e0a10', '#2c0e18', '#3e1422', '#55202e'), 0.3 + 0.6 * light + 0.15 * (y / self.MANTEL), x, y)
            gx, gy = (x + 6) % 16 - 8, (y + (8 if int((x + 6) // 16) % 2 else 0)) % 16 - 8
            if abs(abs(gx) + abs(gy) - 5) < 0.7: c = lerp(c, hx('#b8801c'), 0.35 + 0.4 * light)
            return c
        # Sims aus Holz
        if y < self.MANTEL + 10:
            v = 0.55 + 0.3 * (1 - (y - self.MANTEL) / 10) + 0.1 * math.sin(x * 0.4 + y * 2.1) + 0.3 * light
            c = dpal(WOOD, v, x, y, 2)
            if y < self.MANTEL + 1.5: c = hx('#e0a060')
            if y > self.MANTEL + 8.5: c = hx('#2e170c')
            return c
        # Ziegel
        row = int((y - self.MANTEL - 10) // 7)
        bx = (x + (6 if row % 2 else 0)) // 13
        lx, ly = (x + (6 if row % 2 else 0)) % 13, (y - self.MANTEL - 10) % 7
        seed = int(bx * 17 + row * 131)
        v = 0.3 + 0.35 * rnd(seed) + 0.9 * light
        c = dpal(BRICK, v, x, y, 2)
        if ly < 1.2 or lx < 1.2: c = dpal(P('#2a201c', '#4a3c34', '#6a5a4c'), 0.3 + light, x, y, 2)  # Fugen
        elif ly < 2.2: c = add(c, (40, 20, 10), 0.6)  # Oberkante heller
        elif ly > 5.8 or lx > 11.8: c = mul(c, 0.82)
        x0, y0, x1, y1 = self.OPEN
        # Öffnung: Bogen oben
        arch = y0 + 10 - math.sqrt(max(0, 34 ** 2 - (x - 60) ** 2)) * 0.3
        if x0 < x < x1 and arch < y < y1:
            c = dpal(P('#060302', '#120806', '#24100a', '#3a1a0e'), 0.15 + 1.2 * light * ss(y0, y1, y), x, y, 2)
            # Holzscheite
            for lx0, ly0, lx1, ly1 in ((34, 176, 70, 170), (52, 170, 88, 177), (40, 180, 84, 180)):
                dseg = abs((ly1 - ly0) * x - (lx1 - lx0) * y + lx1 * ly0 - ly1 * lx0) / math.hypot(lx1 - lx0, ly1 - ly0)
                if dseg < 3.4 and min(lx0, lx1) - 1 < x < max(lx0, lx1) + 1:
                    c = dpal(WOOD, 0.2 + 0.25 * (3.4 - dseg) / 3.4, x, y, 2)
                    if dseg < 1.2 and int(x) % 5 < 2: c = lerp(hx('#ff6a1a'), hx('#ffc24a'), fl - 0.8)  # Glut-Risse
            f = self.fire(x, y, t)
            if f > 0:
                c = ramp([(0, hx('#7a1a06')), (0.25, hx('#d8400e')), (0.5, hx('#ff9a2a')), (0.75, hx('#ffd96a')), (1, hx('#fff8e0'))],
                         min(1, f * 1.3) if bay(x, y, 2) < 0.9 else f)
            # Rand des Bogens
            if y < arch + 2: c = hx('#2a201c')
        if y > y1 - 2 and x0 - 6 < x < x1 + 6: c = dpal(P('#2a201c', '#4a3c34', '#6a5a4c', '#8a786a'), 0.4 + light, x, y, 2)  # Herdplatte
        return c
    def stocking(self, cx, pal, cuff, t, sway):
        """Strumpf als Polygon-Teile (gezeichnet, damit Strickmuster und Rand scharf bleiben)."""
        d = []
        top = self.MANTEL + 10
        ang = sway
        def P2(px, py):
            dx, dy = px - cx, py - top
            return cx + dx * math.cos(ang) - dy * math.sin(ang), top + dx * math.sin(ang) + dy * math.cos(ang)
        def poly(pts, col, a=1.0):
            return ["-fill", rgba(col, a), "-draw", "polygon " + " ".join(f"{p[0]:.1f},{p[1]:.1f}" for p in (P2(*q) for q in pts))]
        # Bein + Fuß
        leg = [(cx - 7, top + 6), (cx + 7, top + 6), (cx + 7, top + 30), (cx + 13, top + 36), (cx + 14, top + 41), (cx + 9, top + 45),
               (cx - 1, top + 44), (cx - 7, top + 38)]
        d += poly(leg, pal[1])
        d += poly([(cx - 5, top + 6), (cx + 5, top + 6), (cx + 5, top + 31), (cx - 5, top + 36)], pal[2])
        # Strick-Querstreifen + kleine Muster
        for k, yy in enumerate(range(top + 12, top + 40, 6)):
            col = pal[3] if k % 2 == 0 else cuff
            d += poly([(cx - 7, yy), (cx + 7, yy), (cx + 7, yy + 1.6), (cx - 7, yy + 1.6)], col, 0.85)
        for yy in range(top + 8, top + 40, 3):
            for xx in range(int(cx - 6), int(cx + 6), 3):
                px, py = P2(xx + 1, yy + 1)
                d += ["-fill", rgba((0, 0, 0), 0.12), "-draw", f"point {px:.0f},{py:.0f}"]
        # Ferse/Spitze
        d += poly([(cx + 7, top + 36), (cx + 14, top + 40), (cx + 9, top + 45), (cx + 4, top + 44)], cuff, 0.9)
        # Fellrand
        for i in range(9):
            px, py = P2(cx - 8 + i * 2, top + 1 + 2.5 * rnd(i + cx))
            d += ["-fill", rgba(lerp(cuff, (255, 255, 255), 0.3 * rnd(i))), "-draw", f"circle {px:.1f},{py + 2:.1f} {px + 2.6:.1f},{py + 2:.1f}"]
        d += poly([(cx - 8, top), (cx + 8, top), (cx + 8, top + 7), (cx - 8, top + 7)], cuff)
        d += poly([(cx - 8, top + 6), (cx + 8, top + 6), (cx + 8, top + 7.5), (cx - 8, top + 7.5)], mul(cuff, 0.7))
        # Aufhänger
        px, py = P2(cx, top)
        d += ["-fill", "none", "-stroke", rgba(hx('#e8b23a')), "-strokewidth", "1.2", "-draw", f"line {cx},{top - 3} {px:.1f},{py:.1f}", "-stroke", "none"]
        return d
    def draws(self, t):
        d, g = [], []
        fl = self.flick(t)
        # Feuerschein
        g += ["-fill", rgba(hx('#ff8a2a'), 0.55 * fl), "-draw", "ellipse 60,160 34,24 0,360"]
        g += ["-fill", rgba(hx('#ffd96a'), 0.4 * fl), "-draw", "ellipse 60,170 18,12 0,360"]
        # Funken steigen
        for i in range(16):
            sp = 1 + int(rnd(i + 300) * 2)
            p = (t * sp + rnd(i + 310)) % 1
            x = 38 + rnd(i + 320) * 44 + 4 * math.sin(TAU * (p * 2 + rnd(i)))
            y = 172 - p * 70
            if y < self.OPEN[1] + 6: continue
            a = (1 - p) * 0.9
            d += ["-fill", rgba(lerp(hx('#ffd96a'), hx('#ff6a1a'), p), a), "-draw", f"rectangle {x:.1f},{y:.1f} {x + 1.2:.1f},{y + 1.2:.1f}"]
            g += ["-fill", rgba(hx('#ff9a2a'), a * 0.6), "-draw", f"circle {x:.1f},{y:.1f} {x + 2.5:.1f},{y:.1f}"]
        # Girlande auf dem Sims
        pts = [(x, self.MANTEL - 2 + 5 * abs(math.sin(x / 40 * math.pi))) for x in range(-2, 124, 2)]
        for i, (x, y) in enumerate(pts):
            for k in range(3):
                ox, oy = 3 * (rnd(i * 3 + k) - 0.5), 3 * (rnd(i * 3 + k + 50) - 0.5)
                d += ["-fill", rgba(FIR[1 + (i + k) % 4]), "-draw", f"circle {x + ox:.1f},{y + oy:.1f} {x + ox + 2.2:.1f},{y + oy:.1f}"]
        for i in range(12):
            x = 5 + i * 10; y = self.MANTEL - 2 + 5 * abs(math.sin(x / 40 * math.pi)) + 2
            col = [hx('#ff5a64'), hx('#ffd96a'), hx('#7fd0ff'), hx('#7dffb2')][i % 4]
            on = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(TAU * (t * 2 - i / 6)))
            d += ["-fill", rgba(mul(col, 0.55 + 0.45 * on)), "-draw", f"ellipse {x},{y + 1} 1.6,2.2 0,360",
                  "-fill", rgba((255, 255, 255), 0.7 * on), "-draw", f"point {x - 0.5:.1f},{y:.1f}"]
            g += ["-fill", rgba(col, 0.8 * on), "-draw", f"circle {x},{y + 1} {x + 5},{y + 1}"]
        # Kerzen und Uhr auf dem Sims
        for cx in (10, 110):
            d += ["-fill", rgba(hx('#f4ecd8')), "-draw", f"rectangle {cx - 2},{self.MANTEL - 16} {cx + 2},{self.MANTEL}",
                  "-fill", rgba(hx('#c8bca0')), "-draw", f"rectangle {cx + 1},{self.MANTEL - 16} {cx + 2},{self.MANTEL}"]
            fy = self.MANTEL - 19 + 0.6 * math.sin(TAU * t * 5 + cx)
            d += ["-fill", rgba(hx('#ffc24a')), "-draw", f"ellipse {cx},{fy:.1f} 1.3,3 0,360", "-fill", rgba(hx('#fff3b8')), "-draw", f"ellipse {cx},{fy + 1:.1f} 0.6,1.4 0,360"]
            g += ["-fill", rgba(hx('#ffc24a'), 0.7), "-draw", f"circle {cx},{fy:.1f} {cx + 7},{fy:.1f}"]
        # Strümpfe (leichtes Pendeln in der Wärme)
        for i, (cx, pal, cuff) in enumerate(self.STOCK):
            d += self.stocking(cx, pal, cuff, t, 0.03 * math.sin(TAU * t + i * 2))
        return d, g
    def edge(self, t): return lerp(hx('#ff9a2a'), hx('#ffd96a'), self.flick(t) - 0.8), hx('#3a1410')

# ==================================================================== 3. Weihnachtsmarkt
class Market(Design):
    name = "weihnachtsmarkt"
    BOOTHS = [(3, 57, 118, RED), (63, 117, 122, FIR)]  # x0, x1, Dachunterkante, Markisenfarbe
    def church(self, x, y):
        # Kirchturm mit Spitze rechts, Schiff, Dächer der Stadt
        if 84 <= x <= 98 and 58 <= y: return True
        if abs(x - 91) < (y - 24) * 0.22 and 24 <= y <= 58: return True
        if 70 <= x <= 120 and y >= 82 - 0 and y >= 74 + abs(x - 102) * 0.35: return True
        sky = 98 + 6 * math.sin(x / 7) + (8 if int(x // 11) % 3 == 0 else 0)
        return y > sky
    def bg(self, x, y, t):
        c = dpal(NIGHT, 0.85 - y / 150 + 0.1, x, y)
        c = add(c, (255, 150, 60), 0.12 * ss(60, 130, y))  # Lichtglocke der Stadt
        if self.church(x, y):
            c = dpal(P('#0a0f24', '#121a36', '#1a2446'), 0.4 + 0.3 * ss(60, 120, y), x, y, 2)
            # Kirchenfenster
            if 88 <= x <= 94 and 64 <= y <= 74 and not (abs(x - 91) < 0.6): c = lerp(hx('#ffc24a'), hx('#ff9a2a'), (y - 64) / 10)
            if (abs(x - 91) < 3.5 and abs(y - 52) < 3.5) and math.hypot(x - 91, y - 52) < 3.3: c = hx('#ffe8a0')  # Uhr
            for wx in (22, 37, 75, 109, 116):
                if abs(x - wx) < 1.6 and 104 < y < 108 and rnd(wx) < 0.8: c = hx('#ffc24a')
            # Schnee auf Dächern
            if not self.church(x, y - 2.5) and y > 30: c = dpal(SNOW, 0.6, x, y, 2)
        # Kopfsteinpflaster mit Schnee
        if y > 172:
            bxx, byy = (x + (4 if int(y // 5) % 2 else 0)) % 8, y % 5
            c = dpal(P('#2a2a3a', '#3e3e52', '#5a5a70', '#a9bfdf', '#d6e4f7'), 0.35 + 0.15 * rnd(int(x // 8) * 7 + int(y // 5)) + 0.25 * ss(185, 172, y), x, y, 2)
            if bxx < 1 or byy < 1: c = mul(c, 0.7)
            c = add(c, (255, 170, 80), 0.25 * math.exp(-(y - 172) / 10))
        # Buden
        for bi, (x0, x1, ry, aw) in enumerate(self.BOOTHS):
            cx = (x0 + x1) / 2
            roof_top = ry - (x1 - x0) / 2 * 0.55
            ry_here = ry - ((x1 - x0) / 2 - abs(x - cx)) * 0.55
            if x0 - 3 <= x <= x1 + 3 and ry_here - 4 <= y <= ry + 1 and y >= roof_top - 4:
                # Dach: Schnee oben, Holzkante
                if y < ry_here + 0:
                    sv = 0.55 + 0.4 * (1 - (ry_here - y) / 4) + 0.1 * math.sin(x * 0.8)
                    c = dpal(SNOW, sv, x, y, 2)
                    # Eiszapfen/Schneekante unregelmäßig
                else:
                    c = dpal(WOOD, 0.45, x, y, 2)
            # Schneehaube mit Wülsten
            if x0 - 3 <= x <= x1 + 3 and ry_here - 7 <= y < ry_here - 4 + 1.5 * math.sin(x * 0.9 + bi):
                c = dpal(SNOW, 0.95 - 0.15 * (x - x0) / (x1 - x0), x, y, 2)
            if x0 <= x <= x1 and ry + 1 < y <= 172:
                # Wände aus Brettern
                plank = int((x - x0) // 5)
                c = dpal(WOOD, 0.35 + 0.2 * rnd(plank + bi * 50) + 0.08 * math.sin(y * 1.3 + plank), x, y, 2)
                if (x - x0) % 5 < 0.8: c = WOOD[0]
                # Markise
                if ry + 1 < y < ry + 11:
                    stripe = int((x - x0) // 6) % 2
                    c = dpal(aw if stripe == 0 else SNOW, 0.75 - 0.4 * (y - ry - 1) / 10, x, y, 2)
                    if y > ry + 8 and (x - x0) % 6 > 3 and y > ry + 9: pass
                    # Zacken unten
                    if y > ry + 8 + 2 * abs(((x - x0) % 6) - 3) / 3: c = dpal(WOOD, 0.1, x, y, 2)
                # Verkaufsfenster mit warmem Licht und Waren
                if x0 + 4 < x < x1 - 4 and ry + 12 < y < ry + 38:
                    c = dpal(P('#7a3a10', '#b8601c', '#e89a3a', '#ffc24a', '#ffe8a0'), 0.95 - (y - ry - 12) / 30 + 0.1 * math.sin(TAU * t * 2 + x / 7) * 0.3, x, y, 2)
                    # Regal-Linie und Waren (Lebkuchenherzen, Kugeln, Tassen)
                    if abs(y - (ry + 25)) < 0.8: c = WOOD[1]
                    for k in range(6):
                        gx = x0 + 9 + k * 8
                        gy = ry + 21 if k % 2 else ry + 33
                        dd = math.hypot(x - gx, y - gy)
                        if dd < 2.8:
                            col = [hx('#a5632c'), hx('#d92a3f'), hx('#e8b23a'), hx('#2f7d4c'), hx('#f4f8ff'), hx('#ff5a64')][(k + bi * 3) % 6]
                            c = lerp(col, (255, 255, 255), 0.5) if dd < 1 and x < gx else mul(col, 1 - 0.3 * dd / 2.8)
                    # Lebkuchenherzen hängen oben
                    for k in range(4):
                        hx0, hy0 = x0 + 10 + k * 12, ry + 15
                        u, v = (x - hx0) / 3, (y - hy0) / 3
                        if (u * u + v * v - 1) ** 3 - u * u * v ** 3 * 0 < 0 and abs(u) < 1.2 and -1 < v < 1.3 and abs(u) < 1.25 - max(0, v) * 0.9:
                            c = WOOD[3] if (abs(abs(u) - 0.6) > 0.25) else hx('#f4f8ff')
                # Theke
                if ry + 38 <= y < ry + 42: c = dpal(WOOD, 0.75 - (y - ry - 38) / 6, x, y, 2)
                if ry + 37 <= y < ry + 38.5: c = dpal(SNOW, 0.8, x, y, 2)
        # Christbaum in der Mitte hinten
        r = tree(x, y, 60, 120, 22, 52, 4)
        if r and not (3 <= x <= 57 or 63 <= x <= 117) or (r and y < 103):
            k, step, side = r
            c = dpal(FIR, 0.6 - 0.35 * side - 0.2 * k, x, y, 2)
            if step < 0.2: c = dpal(SNOW, 0.75 - side * 0.2, x, y, 2)
        return c
    LIGHTS = None
    def lights(self):
        """Zickzack-Lichterkette quer über den Markt (Punkte auf zwei durchhängenden Bögen)."""
        pts = []
        for seg, (ax, ay, bx, by, sag) in enumerate([(-2, 66, 40, 80, 9), (40, 80, 80, 64, 8), (80, 64, 122, 78, 9)]):
            for i in range(7):
                u = (i + 0.5) / 7
                pts.append((ax + (bx - ax) * u, ay + (by - ay) * u + sag * math.sin(math.pi * u)))
        return pts
    def draws(self, t):
        d, g = [], []
        d += stars(t, 18, 1300, 50, skip=lambda x, y: self.church(x, y))
        # Kabel
        for ax, ay, bx, by, sag in [(-2, 66, 40, 80, 9), (40, 80, 80, 64, 8), (80, 64, 122, 78, 9)]:
            pts = " ".join(f"{ax + (bx - ax) * u / 12:.1f},{ay + (by - ay) * u / 12 + sag * math.sin(math.pi * u / 12):.1f}" for u in range(13))
            d += ["-fill", "none", "-stroke", rgba(hx('#0a0a14')), "-strokewidth", "0.8", "-draw", f"polyline {pts}", "-stroke", "none"]
        cols = [hx('#ff5a64'), hx('#ffd96a'), hx('#7fd0ff'), hx('#7dffb2'), hx('#ffa0e0')]
        for i, (x, y) in enumerate(self.lights()):
            col = cols[i % 5]
            # Lauflicht: 3 Gruppen nacheinander + leichtes Glimmen
            on = 1.0 if (i % 3) == int(t * 6) % 3 else 0.35
            d += ["-fill", rgba(mul(col, 0.5 + 0.5 * on)), "-draw", f"ellipse {x:.1f},{y + 1.5:.1f} 1.4,2 0,360",
                  "-fill", rgba((255, 255, 255), on * 0.8), "-draw", f"point {x - 0.4:.1f},{y + 0.8:.1f}"]
            g += ["-fill", rgba(col, on * 0.9), "-draw", f"circle {x:.1f},{y + 1.5:.1f} {x + 4 + 2 * on:.1f},{y + 1.5:.1f}"]
        # Christbaum-Lichter + Stern
        for i in range(10):
            k = 0.2 + 0.75 * rnd(i + 400)
            x = 60 + (rnd(i + 410) - 0.5) * 22 * (0.3 + 0.7 * k) * 0.8
            y = 68 + 52 * k
            if y > 103 and (3 <= x <= 57 or 63 <= x <= 117): continue
            a = 0.5 + 0.5 * math.sin(TAU * (t * 2 + rnd(i + 420)))
            col = cols[i % 5]
            d += ["-fill", rgba(mul(col, 0.6 + 0.4 * a)), "-draw", f"circle {x:.1f},{y:.1f} {x + 1:.1f},{y:.1f}"]
            g += ["-fill", rgba(col, a), "-draw", f"circle {x:.1f},{y:.1f} {x + 3:.1f},{y:.1f}"]
        pulse = 0.8 + 0.2 * math.sin(TAU * t * 2)
        d += ["-fill", rgba(hx('#ffd96a')), "-stroke", rgba(hx('#fff3b8')), "-strokewidth", "0.6", "-draw",
              "polygon " + " ".join(f"{60 + (4 if j % 2 == 0 else 1.7) * math.sin(j * math.pi / 5):.1f},{66 - (4 if j % 2 == 0 else 1.7) * math.cos(j * math.pi / 5):.1f}" for j in range(10)), "-stroke", "none"]
        g += ["-fill", rgba(hx('#ffd96a'), pulse), "-draw", "circle 60,66 72,66"]
        # Fensterschein der Buden
        for x0, x1, ry, _ in self.BOOTHS:
            g += ["-fill", rgba(hx('#ffb347'), 0.45), "-draw", f"rectangle {x0 + 4},{ry + 12} {x1 - 4},{ry + 40}"]
            # Glühwein-Tassen auf der Theke mit Dampf
            for k, mx in enumerate((x0 + 12, x1 - 14)):
                my = ry + 34
                d += ["-fill", rgba(hx('#b3172c')), "-draw", f"rectangle {mx - 2.5},{my - 3} {mx + 2.5},{my + 2}",
                      "-fill", rgba(hx('#f4f8ff')), "-draw", f"rectangle {mx - 2.5},{my - 1} {mx + 2.5},{my}",
                      "-fill", "none", "-stroke", rgba(hx('#b3172c')), "-strokewidth", "1", "-draw", f"arc {mx + 1.5},{my - 2} {mx + 5},{my + 1} -90,90", "-stroke", "none"]
                for s in range(3):
                    p = (t + s / 3 + k * 0.17) % 1
                    sx = mx + 2.2 * math.sin(TAU * (p * 1.5 + s * 0.3))
                    sy = my - 5 - p * 22
                    d += ["-fill", rgba((240, 240, 250), 0.35 * (1 - p) * ss(0, 0.15, p)), "-draw", f"circle {sx:.1f},{sy:.1f} {sx + 1.4 + 2 * p:.1f},{sy:.1f}"]
        d += snow(t, 46, 1500, smin=0.5, smax=1.3, speed=(1, 2), sway=4, alpha=0.75)
        return d, g
    def edge(self, t): return hx('#ffc24a'), hx('#16234f')

# ==================================================================== 4. Schlittenflug
MOON = P('#8a8470', '#b0a88e', '#cfc6a8', '#e8dfc0', '#fff6dc', '#fffbee')
class SleighRide(Design):
    name = "schlittenflug"
    MX, MY, MR = 60, 70, 36
    CRATERS = [(-12, -10, 7), (10, 8, 9), (-4, 18, 5), (16, -14, 4.5), (-20, 10, 4), (4, -22, 3.5), (22, 4, 3), (-14, -24, 3)]
    MARIA = [(-8, -4, 14), (12, 12, 11), (-16, 16, 8)]
    def roof(self, x):
        # Häuserreihe unten: Satteldächer
        hs = [(-6, 30, 168), (24, 26, 174), (50, 34, 164), (84, 26, 172), (110, 26, 166)]
        best = 999
        for hx0, w, top in hs:
            if hx0 <= x <= hx0 + w:
                best = min(best, top + abs(x - (hx0 + w / 2)) * 0.6)
        return best
    def bg(self, x, y, t):
        dd = math.hypot(x - self.MX, y - self.MY)
        c = dpal(NIGHT, 0.95 - y / 180 + 0.4 * math.exp(-max(0, dd - self.MR) / 26), x, y)
        if dd < self.MR:
            lx, ly = x - self.MX, y - self.MY
            nz = math.sqrt(max(0, 1 - (dd / self.MR) ** 2))
            v = 0.35 + 0.55 * nz + 0.12 * (-lx - ly) / self.MR
            for mx, my, mr in self.MARIA:
                v -= 0.22 * math.exp(-(math.hypot(lx - mx, ly - my) / mr) ** 2)
            for cx, cy, cr in self.CRATERS:
                cd = math.hypot(lx - cx, ly - cy)
                if cd < cr:
                    # innen dunkel, Schatten oben links, Licht am Rand unten rechts
                    v -= 0.18
                    v += 0.25 * ((lx - cx) + (ly - cy)) / cr * ss(cr * 0.4, cr, cd)
                elif cd < cr + 1.3:
                    v += 0.12  # Kraterwall
            v += 0.05 * (rnd(int(x // 2) * 13 + int(y // 2) * 7) - 0.5)
            c = dpal(MOON, v, x, y, 2)
            if dd > self.MR - 1.5: c = lerp(c, MOON[-1], 0.5)
        # Häuser
        r = self.roof(x)
        if y > r:
            if y < r + 4: c = dpal(SNOW, 0.95 - (y - r) / 5, x, y, 2)
            else:
                c = dpal(P('#0a0f24', '#121a36', '#1a2446', '#24304f'), 0.3 + 0.1 * math.sin(x / 3), x, y, 2)
                # Fenster
                wx, wy = x % 12, (y - r - 8) % 14
                if 3 <= wx <= 8 and 0 <= wy <= 7 and y > r + 8 and rnd(int(x // 12) * 5 + int((y - r) // 14)) < 0.6:
                    c = dpal(P('#b8601c', '#ffc24a', '#ffe8a0'), 0.4 + 0.5 * (1 - wy / 7), x, y, 2)
                    if abs(wx - 5.5) < 0.6 or abs(wy - 3.5) < 0.6: c = hx('#24304f')
        # Schornsteine
        for cx0, top in ((8, 156), (60, 150), (94, 160)):
            if cx0 <= x <= cx0 + 6 and top <= y <= self.roof(x) + 2:
                c = dpal(BRICK, 0.4 + 0.2 * ((y - top) % 4 < 1), x, y, 2)
                if y < top + 2.5: c = dpal(SNOW, 0.9, x, y, 2)
        return c
    def team(self, t):
        """Position der Gespann-Spitze (links): rechts herein, links hinaus; leichter Bogen über den Mond."""
        x = FW + 14 - t * (FW + 200)
        return x, 64 + 14 * math.sin(math.pi * t) * -1 + 22 * t
    def draws(self, t):
        d, g = [], []
        d += stars(t, 30, 2000, 140, skip=lambda x, y: math.hypot(x - self.MX, y - self.MY) < self.MR + 3)
        g += ["-fill", rgba(hx('#fff6dc'), 0.35), "-draw", f"circle {self.MX},{self.MY} {self.MX + self.MR + 6},{self.MY}"]
        f = int(round(t * N))
        x0, y0 = self.team(t)
        sil = rgba(hx('#070a18'))
        rim = rgba(hx('#fff6dc'), 0.85)
        # Glitzerspur hinter dem Schlitten
        sx, sy = x0 + 96, y0 + 8
        for i in range(26):
            u = i / 25
            px = sx + 6 + u * 90 + 3 * rnd(i + f)
            py = sy + 3 + 10 * u * u - 6 * u + 3 * (rnd(i + 70 + f) - 0.5)
            a = (1 - u) * (0.5 + 0.5 * rnd(i * 3 + f))
            col = [hx('#ffd96a'), hx('#fff3b8'), hx('#ffffff')][i % 3]
            d += ["-fill", rgba(col, a), "-draw", f"rectangle {px:.1f},{py:.1f} {px + 1:.1f},{py + 1:.1f}"]
            if i % 5 == f % 5: d += twinkle(px, py, a, 2.5, col, 0.7)
            g += ["-fill", rgba(hx('#ffd96a'), a * 0.6), "-draw", f"circle {px:.1f},{py:.1f} {px + 2.5:.1f},{py:.1f}"]
        # Rentiere (4, in Zweierreihen versetzt), galoppierend
        for k in range(4):
            rx, ry = x0 + k * 18, y0 + 3 * math.sin(TAU * t * 6 + k) + 2 * k
            ph = (f + k * 2) % 4
            leg = [(-3, 3, -5, 9), (-1, 3, 1, 9), (5, 3, 3, 9), (7, 3, 9, 9)]
            gal = [(-6, 6), (2, 8), (-1, 7), (5, 5)][ph]
            d += ["-fill", sil,
                  "-draw", f"ellipse {rx + 2},{ry} 6,3 0,360",  # Körper
                  "-draw", f"polygon {rx - 4},{ry - 1} {rx - 8},{ry - 7} {rx - 6},{ry - 8} {rx - 2},{ry - 2}",  # Hals
                  "-draw", f"ellipse {rx - 8},{ry - 8} 2.6,1.6 -20,360",  # Kopf
                  "-stroke", sil, "-strokewidth", "1.1",
                  "-draw", f"line {rx - 4},{ry + 1} {rx - 4 + gal[0] * 0.5:.1f},{ry + 6}",
                  "-draw", f"line {rx - 1},{ry + 2} {rx - 1 - gal[0] * 0.4:.1f},{ry + 7}",
                  "-draw", f"line {rx + 6},{ry + 1} {rx + 6 + gal[1] * 0.4:.1f},{ry + 6}",
                  "-draw", f"line {rx + 8},{ry} {rx + 8 + gal[1] * 0.6:.1f},{ry + 5}",
                  # Geweih
                  "-draw", f"line {rx - 8},{ry - 9} {rx - 7},{ry - 14}", "-draw", f"line {rx - 7},{ry - 12} {rx - 5},{ry - 14}",
                  "-draw", f"line {rx - 9},{ry - 9} {rx - 11},{ry - 13}", "-stroke", "none",
                  # Mondlicht-Rand oben
                  "-fill", "none", "-stroke", rim, "-strokewidth", "0.5", "-draw", f"arc {rx - 4},{ry - 3} {rx + 8},{ry + 3} 200,330", "-stroke", "none"]
            if k == 0:  # Rudolphs Nase
                d += ["-fill", rgba(hx('#ff3b4e')), "-draw", f"circle {rx - 10.5},{ry - 8} {rx - 9.6},{ry - 8}"]
                g += ["-fill", rgba(hx('#ff3b4e'), 0.9), "-draw", f"circle {rx - 10.5},{ry - 8} {rx - 6},{ry - 8}"]
        # Zügel
        d += ["-fill", "none", "-stroke", rgba(hx('#e8b23a'), 0.8), "-strokewidth", "0.5", "-draw",
              f"line {x0 - 4},{y0 - 1} {x0 + 86},{y0 + 3}", "-stroke", "none"]
        # Schlitten mit Weihnachtsmann und Sack
        bx, by = x0 + 84, y0 + 8
        d += ["-fill", sil,
              "-draw", f"polygon {bx},{by - 6} {bx + 18},{by - 8} {bx + 20},{by - 1} {bx + 16},{by + 3} {bx + 2},{by + 3} {bx - 2},{by - 2}",
              "-draw", f"circle {bx + 8},{by - 13} {bx + 12},{by - 13}",  # Sack
              "-draw", f"ellipse {bx + 2},{by - 10} 3,4 0,360", "-draw", f"circle {bx + 1},{by - 15} {bx + 3},{by - 15}",  # Weihnachtsmann
              "-draw", f"polygon {bx - 1},{by - 17} {bx + 3},{by - 17} {bx - 3},{by - 20}",  # Mütze
              "-fill", "none", "-stroke", sil, "-strokewidth", "1.2", "-draw", f"arc {bx - 6},{by + 2} {bx + 22},{by + 7} 0,180",
              "-draw", f"line {bx - 5},{by + 4} {bx - 6},{by + 1}", "-stroke", "none",
              "-fill", "none", "-stroke", rim, "-strokewidth", "0.5", "-draw", f"line {bx},{by - 6} {bx + 18},{by - 8}", "-stroke", "none",
              "-fill", rgba(hx('#d92a3f'), 0.9), "-draw", f"rectangle {bx + 3},{by - 5} {bx + 15},{by - 4}"]
        # Rauch aus den Schornsteinen
        for i, (cx0, top) in enumerate(((8, 156), (60, 150), (94, 160))):
            for s in range(5):
                p = (t + s / 5 + i * 0.13) % 1
                px = cx0 + 3 + 6 * p + 2 * math.sin(TAU * (p * 2 + i))
                py = top - 2 - p * 34
                d += ["-fill", rgba((190, 200, 225), 0.4 * (1 - p) * ss(0, 0.1, p)), "-draw", f"circle {px:.1f},{py:.1f} {px + 1.5 + 3 * p:.1f},{py:.1f}"]
        return d, g
    def edge(self, t): return hx('#fff6dc'), hx('#0b1230')

# ==================================================================== 5. Adventskranz
class Advent(Design):
    name = "adventskranz"
    CX, CY, RX, RY = 60, 134, 46, 21
    def angles(self): return [math.radians(a) for a in (208, 332, 62, 118)]  # hinten links, hinten rechts, vorne rechts, vorne links
    def cpos(self, a): return self.CX + self.RX * math.cos(a), self.CY + self.RY * math.sin(a)
    CH = [42, 38, 32, 36]  # Kerzenhöhen (unterschiedlich weit abgebrannt)
    def flame(self, i, t):
        fl = 0.85 + 0.1 * math.sin(TAU * (t * (3 + i % 2) + i * 0.37)) + 0.05 * math.sin(TAU * (t * 7 + i))
        return fl, 1.3 * math.sin(TAU * (t * 2 + i * 0.25))
    def wreath(self, x, y):
        """Kranz als Ellipsenring; Rückgabe (Tiefe -1 hinten..1 vorne, Querposition 0..1) oder None."""
        u, v = (x - self.CX) / self.RX, (y - self.CY) / self.RY
        r = math.hypot(u, v * 0.9)
        th = 0.36
        if abs(r - 1) > th + 0.06 * math.sin(math.atan2(v, u) * 23): return None
        return v, (r - 1 + th) / (2 * th)
    def candle(self, i, x, y):
        cx, cy = self.cpos(self.angles()[i])
        hgt = self.CH[i]
        if abs(x - cx) <= 5 and cy - hgt <= y <= cy:
            return (x - cx) / 5, (y - (cy - hgt)) / hgt
        return None
    def bg(self, x, y, t):
        c = dpal(P('#0c0604', '#160a06', '#22100a', '#30180e', '#40200f'), 0.25 + 0.5 * math.exp(-math.hypot(x - 60, y - 90) / 70), x, y)
        # warmes Licht der Kerzen (flackernd)
        light = 0
        for i, a in enumerate(self.angles()):
            cx, cy = self.cpos(a)
            fl, _ = self.flame(i, t)
            light += fl * math.exp(-math.hypot(x - cx, y - (cy - self.CH[i] - 6)) / 30)
        c = add(c, (255, 150, 60), 0.18 * light)
        # Tisch / Tischdecke
        if y > 150:
            c = dpal(RED, 0.2 + 0.25 * light + 0.1 * math.sin(x / 6), x, y, 2)
            if int((x + y * 0.0) // 10) % 2 == 0 and int(y // 10) % 2 == 0: c = mul(c, 0.9)
        def wreath_px(depth_front):
            nonlocal c
            w = self.wreath(x, y)
            if w is None: return
            v, q = w
            if (v > 0) != depth_front: return
            # Nadeln: kurze schräge Striche, oben heller, innen/außen dunkler
            ang = math.atan2((y - self.CY) / self.RY, (x - self.CX) / self.RX)
            needle = 0.5 + 0.5 * math.sin(ang * 90 + q * 9 + rnd(int(ang * 40)) * 6)
            sh = 0.35 + 0.45 * (1 - abs(q - 0.4) * 1.6) + 0.25 * needle - 0.15 * max(0, v) + 0.25 * light
            c = dpal(FIR, sh, x, y, 2)
            # Beeren, Zapfen
            k = int((ang + math.pi) / TAU * 28)
            ca = -math.pi + (k + 0.5) / 28 * TAU
            px, py = self.CX + self.RX * math.cos(ca) * (1 + 0.12 * (rnd(k) - 0.5)), self.CY + self.RY * math.sin(ca) - 2
            dd = math.hypot(x - px, (y - py) * 1.2)
            kind = k % 4
            if kind == 1 and dd < 2.4:
                c = RED[3] if dd > 0.9 or x > px else RED[4]
                if x < px - 0.5 and y < py - 0.5: c = hx('#ffd0d4')
            if kind == 3 and dd < 4.2:
                sc = (int((x - px) // 2) + int((y - py) // 2)) % 2
                c = dpal(WOOD, 0.45 + 0.25 * sc - 0.2 * dd / 4, x, y, 1)
            if kind == 2 and dd < 3:  # goldene Kugel
                c = dpal(GOLD, 0.8 - dd / 4 + (0.3 if x < px and y < py else 0), x, y, 1)
            # rotes Band schräg um den Kranz
            if (ang * 8 / TAU * 4 + q * 2) % 4 < 0.7: c = dpal(RED, 0.5 + 0.3 * (1 - q) + 0.2 * light, x, y, 2)
        def candle_px(i):
            nonlocal c
            r = self.candle(i, x, y)
            if r is None: return
            u, v = r
            cyl = math.sqrt(max(0, 1 - u * u))
            pal = P('#4a0812', '#7a0f1e', '#b3172c', '#d92a3f', '#ff5a64', '#ff9a9a')
            fl, _ = self.flame(i, t)
            sh = 0.12 + 0.52 * cyl - 0.22 * u + 0.12 * (1 - v) * fl
            c = dpal(pal, sh, x, y, 1)
            # Glanzlinie
            if -0.55 < u < -0.3: c = lerp(c, hx('#ffb0b0'), 0.35)
            # Wachsrand oben und Tropfen
            if v < 0.06: c = hx('#ff8a8a')
            for dx, dl in ((-3, 0.25), (1, 0.15), (3.5, 0.35)):
                cx, cy = self.cpos(self.angles()[i])
                if abs(x - (cx + dx)) < 0.9 and v < dl * (0.6 + 0.4 * rnd(i * 5 + int(dx))) and v > 0.03:
                    c = lerp(hx('#e83a4c'), hx('#ffb0b0'), 0.4 - u * 0.3)
        # Reihenfolge: hinterer Kranz, hintere Kerzen, vorderer Kranz, vordere Kerzen
        wreath_px(False)
        candle_px(0); candle_px(1)
        wreath_px(True)
        candle_px(3); candle_px(2)
        return c
    def draws(self, t):
        d, g = [], []
        # Bokeh im Hintergrund (driftet, nahtlos)
        for i in range(12):
            x = (rnd(i + 600) * FW + t * FW * (1 if i % 2 else -1) * 0.0) % FW
            y = 10 + rnd(i + 610) * 80
            r = 4 + 7 * rnd(i + 620)
            a = 0.18 + 0.12 * math.sin(TAU * (t + rnd(i + 630)))
            col = [hx('#ffc24a'), hx('#ff9a2a'), hx('#d92a3f'), hx('#ffd96a')][i % 4]
            g += ["-fill", rgba(col, a), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
            d += ["-fill", "none", "-stroke", rgba(col, a * 0.5), "-strokewidth", "0.6", "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}", "-stroke", "none"]
        # Schleifen vorne
        bx, by = self.CX, self.CY + self.RY + 2
        for s in (-1, 1):
            d += ["-fill", rgba(RED[1]), "-draw", f"polygon {bx},{by} {bx + s * 13},{by - 7} {bx + s * 14},{by + 5}",
                  "-fill", rgba(RED[2]), "-draw", f"polygon {bx},{by} {bx + s * 11},{by - 5} {bx + s * 11},{by + 3}",
                  "-fill", rgba(RED[4], 0.7), "-draw", f"polygon {bx},{by} {bx + s * 9},{by - 5} {bx + s * 8},{by - 3}",
                  "-fill", rgba(RED[2]), "-draw", f"polygon {bx},{by} {bx + s * 6},{by + 14} {bx + s * 3},{by + 12} {bx + s * 1},{by + 15}"]
        d += ["-fill", rgba(RED[1]), "-draw", f"circle {bx},{by} {bx + 3},{by}", "-fill", rgba(RED[3]), "-draw", f"circle {bx - 0.5},{by - 0.5} {bx + 1.5},{by}"]
        # Flammen: Docht, äußere Flamme, Kern, Leuchten
        for i, a in enumerate(self.angles()):
            cx, cy = self.cpos(a)
            top = cy - self.CH[i]
            fl, sway = self.flame(i, t)
            h = 9 * fl
            fx = cx + sway * 0.6
            d += ["-stroke", rgba(hx('#1a0a04')), "-strokewidth", "0.8", "-draw", f"line {cx},{top} {cx + sway * 0.2:.1f},{top - 2.5}", "-stroke", "none"]
            d += ["-fill", rgba(CANDLE[0], 0.9), "-draw", f"polygon {cx - 2.6:.1f},{top - 3} {cx + 2.6:.1f},{top - 3} {fx + 0.6:.1f},{top - 3 - h:.1f} {fx - 0.6:.1f},{top - 3 - h:.1f}",
                  "-draw", f"ellipse {cx},{top - 3.5} 2.7,2.4 0,360",
                  "-fill", rgba(CANDLE[1]), "-draw", f"ellipse {(cx + fx) / 2:.1f},{top - 3 - h * 0.35:.1f} 1.8,{h * 0.35:.1f} 0,360",
                  "-fill", rgba(CANDLE[2]), "-draw", f"ellipse {(cx + fx) / 2:.1f},{top - 3 - h * 0.25:.1f} 0.9,{h * 0.22:.1f} 0,360",
                  "-fill", rgba(hx('#5a8aff'), 0.6), "-draw", f"ellipse {cx},{top - 2.6} 1.2,0.8 0,360"]
            g += ["-fill", rgba(hx('#ffb347'), 0.85 * fl), "-draw", f"circle {fx:.1f},{top - 6:.1f} {fx + 14 * fl:.1f},{top - 6:.1f}",
                  "-fill", rgba(hx('#fff3b8'), 0.8), "-draw", f"circle {fx:.1f},{top - 5:.1f} {fx + 5:.1f},{top - 5:.1f}"]
        return d, g
    glow_blur = 4
    def edge(self, t): return hx('#ffc24a'), hx('#7a0f1e')

# ==================================================================== 6. Schneemann-Strick
NAVY, CREAM, KRED, KGRN, BLACK, ORANGE = '#16234f', '#f4f8ff', '#b3172c', '#2f7d4c', '#141420', '#ff8a2a'
def _chart():
    """Strickmuster 20 x 32 Maschen (je 6x6 Pixel = 1/2 Modell-Pixel)."""
    W2, H2 = 20, 32
    g = [[NAVY] * W2 for _ in range(H2)]
    def put(x, y, c):
        if 0 <= x < W2 and 0 <= y < H2: g[y][x] = c
    for x in range(W2):
        for y in (0, 1, 30, 31): g[y][x] = KRED
        for y in range(2, 7): g[y][x] = KRED
        for y in range(25, 30): g[y][x] = CREAM
    # Zickzack weiß auf rot
    for x in range(W2):
        z = abs((x % 4) - 2)
        put(x, 3 + z, CREAM)
        put(x, 2 + (2 - z), KRED)
    # Schneeflocken-Band (Reihen 7..12) auf Navy
    for cx in (2, 9, 16):
        for dx, dy in [(0, 0), (-1, -1), (1, -1), (-1, 1), (1, 1), (0, -2), (0, 2), (-2, 0), (2, 0)]:
            put(cx + dx, 9 + dy, CREAM)
    for x in range(W2):
        if x % 7 in (5, 6): put(x, 9, KRED)
    # Grenze
    for x in range(W2): put(x, 12, CREAM if x % 2 else NAVY)
    # Tannen-Band auf Creme (Reihen 25..29)
    for cx in (1, 6, 11, 16):
        for dy, half in ((0, 0), (1, 1), (2, 1), (3, 2)):
            for dx in range(-half, half + 1): put(cx + 1 + dx, 25 + dy, KGRN)
        put(cx + 1, 29, '#7a4422')
    # Rippen unten/oben: abwechselnd
    return g
CHART = _chart()
SNOWMAN = {}
def _snowman():
    m = {}
    def disc(cx, cy, r, c):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.3: m[(x, y)] = c
    disc(10, 21, 3.3, CREAM)
    disc(10, 16.5, 2.3, CREAM)
    for x in range(8, 13): m[(x, 14)] = BLACK
    for x in range(9, 12): m[(x, 13)] = BLACK
    m[(9, 16)] = BLACK; m[(11, 16)] = BLACK; m[(10, 17)] = ORANGE; m[(11, 17)] = ORANGE
    for x in range(8, 13): m[(x, 18)] = KRED
    m[(12, 19)] = KRED; m[(12, 20)] = KRED
    m[(10, 20)] = BLACK; m[(10, 22)] = BLACK
    m[(6, 19)] = '#7a4422'; m[(5, 18)] = '#7a4422'; m[(14, 19)] = '#7a4422'; m[(15, 18)] = '#7a4422'  # Ast-Arme
    for x in range(4, 17): m.setdefault((x, 24), CREAM)  # Schneeboden
    for x in (5, 6, 14, 15, 16): m.setdefault((x, 23), CREAM)
    return m
SNOWMAN = _snowman()
class Knit(Design):
    name = "schneemannstrick"
    CELL = 6
    def cell(self, cx, cy, t):
        if 13 <= cy <= 24:
            if (cx, cy) in SNOWMAN: return SNOWMAN[(cx, cy)], False
            # fallende Strick-Flocken im Hintergrund
            shift = int(t * 12)
            if rnd(cx * 31 + ((cy - 13 - shift) % 12) * 7 + 5) < 0.09 and cy < 24: return CREAM, True
            return NAVY, False
        c = CHART[cy][cx]
        return c, (c == CREAM and 7 <= cy <= 11)
    def bg(self, x, y, t):
        C = self.CELL
        cx, cy = int(x // C), int(y // C)
        col, sparkle = self.cell(min(cx, 19), min(cy, 31), t)
        base = hx(col)
        u, v = x - cx * C, y - cy * C
        # Masche = zwei schräge Halbmaschen (V); dazwischen und am Rand dunkler
        if cy in (0, 1, 30, 31):  # Bündchen: senkrechte Rippen
            sh = 0.75 + 0.35 * math.cos((u - 3) / 3 * math.pi / 2) * (1 if cx % 2 else 0.6)
        else:
            def leg(ax, ay, bx, by):
                dx, dy = bx - ax, by - ay
                k = cl(((u - ax) * dx + (v - ay) * dy) / (dx * dx + dy * dy))
                return math.hypot(u - ax - k * dx, v - ay - k * dy), k
            d1, k1 = leg(0.9, -0.6, 2.75, 5.6)
            d2, k2 = leg(5.1, -0.6, 3.25, 5.6)
            dmin, k = (d1, k1) if d1 < d2 else (d2, k2)
            sh = 1.18 - 0.55 * (dmin / 1.55) ** 2 - 0.15 * k if dmin < 1.55 else 0.55
            if d1 < d2 and dmin < 1.55: sh += 0.06  # linke Halbmasche etwas heller (Licht von links)
        # Wollfasern
        sh += 0.05 * (rnd(int(x) * 7 + int(y) * 131) - 0.5)
        c = mul(base, cl(sh, 0.35, 1.3))
        if sh > 1.05: c = lerp(c, (255, 255, 255), (sh - 1.05) * 0.6)
        # wandernder Glanz (schräg, weich)
        w = (x * 0.6 + y) - (t * 1.0 * (FH + FW) - 40)
        c = add(c, (255, 245, 230), 0.10 * math.exp(-(w / 18) ** 2))
        # Funkeln einzelner weißer Maschen
        if sparkle:
            a = max(0, math.sin(TAU * (t * 2 + rnd(cx * 13 + cy * 7)))) ** 8
            c = add(c, (200, 230, 255), 0.45 * a)
        return c
    def draws(self, t):
        d, g = [], []
        for i in range(6):
            x = 6 + rnd(i + 800) * (FW - 12); y = 40 + rnd(i + 810) * 30
            a = max(0, math.sin(TAU * (t * 2 + rnd(i + 820)))) ** 10
            if a > 0.05: d += twinkle(x, y, a * 0.8, 3, (220, 240, 255), 0.7)
        return d, g
    def edge(self, t): return hx('#d92a3f'), hx('#7a0f1e')

DESIGNS = [NorthWood(), Fireside(), Market(), SleighRide(), Advent(), Knit()]
META = {
    "nordlichtwald": ("Nordlichtwald", 70, "Wogende Polarlicht-Vorhänge in Grün, Türkis und Violett über einem verschneiten Tannenwald in drei Tiefen, mit funkelnden Sternen und Schneeglitzern."),
    "kaminabend": ("Kaminabend", 70, "Knisterndes Kaminfeuer mit aufsteigenden Funken, drei gestrickten Strümpfen am Sims, blinkender Girlande und warmem Lichtschein auf den Ziegeln."),
    "weihnachtsmarkt": ("Weihnachtsmarkt", 70, "Holzbuden mit Schneedächern und warm leuchtenden Waren, Lauflicht-Lichterkette, dampfender Glühwein, Christbaum und Kirchturm im Schneefall."),
    "schlittenflug": ("Schlittenflug", 90, "Der Weihnachtsmann fliegt mit Rentier-Gespann und Glitzerspur vor einem großen, detailreichen Vollmond über verschneite Dächer."),
    "adventskranz": ("Adventskranz", 70, "Tannenkranz mit Schleife, Beeren, Zapfen und goldenen Kugeln; vier rote Kerzen mit einzeln flackernden Flammen vor warmem Bokeh."),
    "schneemannstrick": ("Schneemann-Strick", 70, "Weihnachtspullover-Optik aus echten V-Maschen: Norweger-Bänder, Tannen, Schneeflocken und ein gestrickter Schneemann mit fallendem Strick-Schnee."),
}

def job(arg):
    di, f = arg
    frame(DESIGNS[di], f)

if __name__ == "__main__":
    jobs = [(i, f) for i, dsg in enumerate(DESIGNS) if not ONLY or dsg.name in ONLY for f in range(N)]
    with Pool(16) as p: p.map(job, jobs)
    shutil.rmtree(TMP, ignore_errors=True)
    meta = [dict(id=d.name, name=META[d.name][0], desc=META[d.name][2], frames=N, frameMs=META[d.name][1], scale=K) for d in DESIGNS]
    json.dump(meta, open(os.path.join(OUT, "meta2.json"), "w"), ensure_ascii=False, indent=1)
    print("Umhänge fertig:", [d.name for d in DESIGNS if not ONLY or d.name in ONLY])
