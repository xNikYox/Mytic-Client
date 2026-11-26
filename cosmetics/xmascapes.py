# Weihnachts-Umhänge „Winterzauber 2026“ (Schneegestöber, Lebkuchenmann, Zuckerstange, Tannenzauber) für capes.py:
# gleiches Format und gleiche Hilfsfunktionen wie dort; gezeichnet wird mit frame() aus capes.py.
import math

N = 24; K = 12; FW, FH = 10 * K, 16 * K; W, H = 64 * K, 32 * K
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
def ss(e0, e1, x): t = cl((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t)
def rnd(i): v = math.sin(i * 12.9898 + 78.233) * 43758.5453; return v - math.floor(v)
def rgba(c, a=1.0): return f"rgba({int(c[0])},{int(c[1])},{int(c[2])},{cl(a):.3f})"

def snow(t, n, seed, color=(245, 250, 255), smin=0.6, smax=1.6, speed=(1, 2), sway=4, alpha=0.9, ymax=FH):
    """Fallende Flocken, nahtlos (jede Flocke fällt in 24 Bildern genau 1- oder 2-mal durch)."""
    d = []
    for i in range(n):
        sp = speed[0] + int(rnd(seed + i + 40) * (speed[1] - speed[0] + 1))
        y = ((t * sp + rnd(seed + i)) % 1) * (ymax + 8) - 4
        x = rnd(seed + i + 80) * FW + sway * math.sin(TAU * (t * sp + rnd(seed + i + 3)))
        r = smin + (smax - smin) * rnd(seed + i + 7)
        d += ["-fill", rgba(color, alpha * (0.6 + 0.4 * rnd(seed + i + 9))), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
    return d

def twinkle(x, y, a, r=4, col=(255, 255, 255)):
    return ["-stroke", rgba(col, a), "-strokewidth", "0.9", "-draw", f"line {x - r:.1f},{y:.1f} {x + r:.1f},{y:.1f}",
            "-draw", f"line {x:.1f},{y - r:.1f} {x:.1f},{y + r:.1f}", "-stroke", "none"]

class Design:
    glow_blur = 3
    def draws(self, t): return [], []
    def edge(self, t): return (180, 80, 255), (60, 20, 120)

# ---------------------------------------------------------------- Schneegestöber: Winternacht mit Tannen, Hütte und Schneefall
class SnowNight(Design):
    name = "schneegestoeber"
    @staticmethod
    def hill(x): return 150 + 8 * math.sin(x / 19 + 0.6) + 4 * math.sin(x / 7.3)
    TREES = [(14, 150, 15, 46), (34, 156, 11, 34), (96, 148, 16, 50), (110, 158, 10, 30), (78, 160, 8, 24)]
    def bg(self, x, y, t):
        c = ramp([(0, hx('#060b24')), (0.45, hx('#13265a')), (0.8, hx('#2a3f7a')), (1, hx('#3b5590'))], y / FH)
        # Mond mit Hof
        d = math.hypot(x - 84, y - 34)
        c = add(c, (120, 150, 255), 0.35 * math.exp(-max(0, d - 10) / 18))
        c = lerp(c, hx('#fff6dc'), ss(11, 9.5, d))
        # Tannen (gestufte Dreiecke)
        for tx, ty, tw, th in self.TREES:
            if ty - th <= y <= ty + 2:
                k = (y - (ty - th)) / th
                step = (k * 4) % 1
                half = tw / 2 * (0.25 + 0.75 * step) * (0.4 + 0.6 * k)
                if abs(x - tx) <= half:
                    c = lerp(hx('#0d2a28'), hx('#173f37'), 0.5 + 0.5 * math.sin(x / 2))
                    # Schnee auf den Ästen
                    if step < 0.22 and abs(x - tx) <= half: c = lerp(c, hx('#e6f0ff'), 0.85)
        # Hütte
        if 46 <= x <= 72 and 138 <= y <= 158:
            c = hx('#3a2216') if (int(y) // 4) % 2 else hx('#4a2c1c')
            if 54 <= x <= 63 and 144 <= y <= 152:
                flick = 0.85 + 0.15 * math.sin(TAU * t * 3 + x / 3)
                c = lerp(hx('#ffb347'), hx('#ffe39a'), flick)
                if abs(x - 58.5) < 0.8 or abs(y - 148) < 0.8: c = hx('#3a2216')
        roof = 138 - (13 - abs(x - 59)) * 0.85
        if 44 <= x <= 74 and roof <= y < 139: c = lerp(hx('#e8f0ff'), hx('#b8c8e8'), (y - roof) / 12)
        # Hügel mit Schnee
        hl = self.hill(x)
        if y > hl: c = lerp(hx('#dfe9ff'), hx('#9fb2dc'), (y - hl) / 40)
        # warmer Schein des Fensters
        c = add(c, (255, 170, 60), 0.28 * math.exp(-math.hypot(x - 58.5, y - 150) / 14))
        return c
    def draws(self, t):
        d, g = [], []
        g += ["-fill", rgba(hx('#ffcf6a'), 0.8), "-draw", "rectangle 52,142 65,154"]
        # Rauch aus dem Schornstein
        for i in range(6):
            p = (t + i / 6) % 1
            x, y = 67 + 6 * p + 2 * math.sin(TAU * (p * 2 + t)), 126 - p * 50
            d += ["-fill", rgba((200, 210, 235), 0.35 * (1 - p)), "-draw", f"circle {x:.1f},{y:.1f} {x + 2 + 4 * p:.1f},{y:.1f}"]
        d += ["-fill", rgba(hx('#2a1810')), "-draw", "rectangle 64,124 68,134", "-fill", rgba(hx('#e8f0ff')), "-draw", "rectangle 63,123 69,125"]
        d += snow(t, 34, 1000, smin=0.5, smax=1.0, speed=(1, 1), sway=3, alpha=0.7)
        d += snow(t, 22, 2000, smin=1.1, smax=1.9, speed=(2, 2), sway=5, alpha=0.95)
        for i in range(5):
            x, y = 6 + rnd(i + 70) * 108, 6 + rnd(i + 90) * 60
            a = max(0, math.sin(TAU * (t + rnd(i + 99)))) ** 4
            if a > 0.05: d += twinkle(x, y, a, 3)
        return d, g
    def edge(self, t): return hx('#dfe9ff'), hx('#13265a')

# ---------------------------------------------------------------- Lebkuchen: Lebkuchenmann mit Zuckerguss, Glitzer, Glanz
class Gingerbread(Design):
    name = "lebkuchen"
    def bg(self, x, y, t):
        n = 0.5 + 0.25 * math.sin(x * 1.7 + y * 0.9) * math.sin(y * 1.3 - x * 0.4) + 0.25 * rnd(int(x) * 31 + int(y) * 7)
        c = lerp(hx('#7a3f17'), hx('#9a5a26'), n)
        # Glanz-Streifen wandert schräg
        band = math.exp(-(((x + y * 0.6) - ((t * 1.25) % 1.25 * 320 - 60)) / 18) ** 2)
        c = add(c, (255, 220, 170), 0.18 * band)
        return c
    def draws(self, t):
        d, g = [], []
        icing = (255, 252, 246)
        # Zuckerguss-Wellen am Rand
        for x in range(0, FW + 1, 10):
            d += ["-fill", "none", "-stroke", rgba(icing), "-strokewidth", "2.2", "-draw", f"arc {x - 5},4 {x + 5},14 0,180",
                  "-draw", f"arc {x - 5},{FH - 14} {x + 5},{FH - 4} 180,360", "-stroke", "none"]
        for y in range(12, FH - 10, 10):
            d += ["-fill", rgba(icing), "-draw", f"circle 6,{y} 7.4,{y}", "-draw", f"circle {FW - 6},{y} {FW - 4.6},{y}"]
        # Lebkuchenmann (leicht wippend)
        bob = 2 * math.sin(TAU * t)
        cx, cy = 60, 96 + bob
        body = hx('#b8702f'); dark = hx('#6a3412')
        g += ["-fill", rgba(hx('#ffb060'), 0.55), "-draw", f"circle {cx},{cy - 38} {cx + 24},{cy - 38}", "-draw", f"ellipse {cx},{cy + 6} 34,44 0,360"]
        d += ["-fill", rgba(dark), "-draw", f"circle {cx},{cy - 38} {cx + 19},{cy - 38}",
              "-draw", f"roundrectangle {cx - 18},{cy - 22} {cx + 18},{cy + 26} 8,8",
              "-draw", f"roundrectangle {cx - 40},{cy - 18} {cx + 40},{cy - 2} 8,8",
              "-draw", f"polygon {cx - 18},{cy + 16} {cx - 2},{cy + 16} {cx - 10},{cy + 62} {cx - 32},{cy + 58}",
              "-draw", f"polygon {cx + 18},{cy + 16} {cx + 2},{cy + 16} {cx + 10},{cy + 62} {cx + 32},{cy + 58}"]
        d += ["-fill", rgba(body), "-draw", f"circle {cx},{cy - 38} {cx + 17},{cy - 38}",
              "-draw", f"roundrectangle {cx - 16},{cy - 20} {cx + 16},{cy + 24} 7,7",
              "-draw", f"roundrectangle {cx - 38},{cy - 16} {cx + 38},{cy - 4} 6,6",
              "-draw", f"polygon {cx - 16},{cy + 16} {cx - 3},{cy + 16} {cx - 11},{cy + 59} {cx - 29},{cy + 56}",
              "-draw", f"polygon {cx + 16},{cy + 16} {cx + 3},{cy + 16} {cx + 11},{cy + 59} {cx + 29},{cy + 56}"]
        # Gesicht + Zuckerguss
        blink = 0.6 if round(t * N) in (9, 10) else 2.2
        d += ["-fill", rgba((40, 20, 10)), "-draw", f"ellipse {cx - 6},{cy - 42} 2.2,{blink} 0,360", "-draw", f"ellipse {cx + 6},{cy - 42} 2.2,{blink} 0,360",
              "-fill", "none", "-stroke", rgba(icing), "-strokewidth", "2", "-draw", f"arc {cx - 8},{cy - 40} {cx + 8},{cy - 28} 20,160",
              "-draw", f"polyline {cx - 36},{cy - 13} {cx - 33},{cy - 7} {cx - 30},{cy - 13} {cx - 27},{cy - 7}",
              "-draw", f"polyline {cx + 36},{cy - 13} {cx + 33},{cy - 7} {cx + 30},{cy - 13} {cx + 27},{cy - 7}",
              "-draw", f"polyline {cx - 26},{cy + 50} {cx - 22},{cy + 54} {cx - 18},{cy + 50} {cx - 14},{cy + 54}",
              "-draw", f"polyline {cx + 26},{cy + 50} {cx + 22},{cy + 54} {cx + 18},{cy + 50} {cx + 14},{cy + 54}", "-stroke", "none"]
        # Knöpfe: rot, grün, rot (Zuckerperlen) + Fliege
        for i, col in enumerate(('#e8283a', '#2fae4e', '#e8283a')):
            y = cy - 6 + i * 11
            d += ["-fill", rgba(hx(col)), "-draw", f"circle {cx},{y} {cx + 3.4},{y}", "-fill", rgba((255, 255, 255), 0.7), "-draw", f"circle {cx - 1},{y - 1} {cx},{y - 1}"]
        d += ["-fill", rgba(hx('#2fae4e')), "-draw", f"polygon {cx},{cy - 18} {cx - 9},{cy - 23} {cx - 9},{cy - 13}", "-draw", f"polygon {cx},{cy - 18} {cx + 9},{cy - 23} {cx + 9},{cy - 13}",
              "-fill", rgba(hx('#1f7a36')), "-draw", f"circle {cx},{cy - 18} {cx + 2.4},{cy - 18}"]
        # Zuckerglitzer
        for i in range(10):
            x, y = 14 + rnd(i + 300) * 92, 18 + rnd(i + 320) * 156
            a = max(0, math.sin(TAU * (t * 2 + rnd(i + 340)))) ** 6
            if a > 0.05: d += twinkle(x, y, a, 3.5, (255, 246, 220))
        return d, g
    def edge(self, t): return hx('#fff6ea'), hx('#5a2c0e')

# ---------------------------------------------------------------- Zuckerstange: wandernde Streifen, Glanz, Minz-Funkeln, Schleife
class CandyCane(Design):
    name = "zuckerstange"
    glow_blur = 4
    def bg(self, x, y, t):
        period = 34.0
        u = ((x + y * 0.75) + t * period) % period / period  # genau eine Periode in 24 Bildern → nahtlos
        red = u < 0.42
        thin = 0.5 < u < 0.58
        c = hx('#d61f35') if red else hx('#2e9e4f') if thin else hx('#fbf7f2')
        if red and (u < 0.05 or u > 0.37): c = lerp(c, hx('#a5121f'), 0.6)
        # zylindrische Schattierung + Glanzlicht
        shade = 0.72 + 0.28 * math.sin(math.pi * x / FW)
        c = tuple(v * shade for v in c)
        c = add(c, (255, 255, 255), 0.45 * math.exp(-((x - 34) / 6) ** 2))
        return c
    def draws(self, t):
        d, g = [], []
        # Schleife oben
        rb = hx('#1f8a43'); rl = hx('#4cd27a')
        d += ["-fill", rgba(rb), "-draw", "rectangle 0,10 120,22",
              "-draw", "polygon 60,16 32,2 28,30", "-draw", "polygon 60,16 88,2 92,30",
              "-draw", "polygon 56,20 44,52 52,50 58,58 60,22", "-draw", "polygon 64,20 76,52 68,50 62,58 60,22",
              "-fill", rgba(rl, 0.8), "-draw", "polygon 60,16 36,6 34,14", "-draw", "polygon 60,16 84,6 86,14",
              "-fill", rgba(hx('#145c2c')), "-draw", "circle 60,16 66,16", "-fill", rgba(rl), "-draw", "circle 59,14 61,14"]
        g += ["-fill", rgba((140, 255, 180), 0.35), "-draw", "circle 60,16 70,16"]
        for i in range(9):
            x, y = 8 + rnd(i + 500) * 104, 40 + rnd(i + 520) * 145
            a = max(0, math.sin(TAU * (t + rnd(i + 540)))) ** 6
            if a > 0.05:
                d += twinkle(x, y, a, 4.5)
                g += ["-fill", rgba((200, 255, 230), a * 0.8), "-draw", f"circle {x:.1f},{y:.1f} {x + 3:.1f},{y:.1f}"]
        return d, g
    def edge(self, t): return hx('#fbf7f2'), hx('#a5121f')

# ---------------------------------------------------------------- Tannenzauber: Christbaum mit blinkender Lichterkette und Stern
class XmasTree(Design):
    name = "tannenzauber"
    glow_blur = 4
    TIERS = ((34, 92, 30), (62, 132, 42), (96, 170, 54))
    @classmethod
    def width(cls, y):
        return max([half * (y - top) / (bot - top) for top, bot, half in cls.TIERS if top <= y <= bot] or [0])
    LIGHTS = []
    for row, y0 in enumerate((58, 84, 112, 126, 150, 164)):
        for k in range(3 + row):
            LIGHTS.append((y0, k, 3 + row))
    def bg(self, x, y, t):
        c = ramp([(0, hx('#1a0612')), (0.6, hx('#330b1e')), (1, hx('#4a0f22'))], y / FH)
        c = add(c, (255, 200, 90), 0.22 * math.exp(-math.hypot(x - 60, y - 34) / 26) * (0.8 + 0.2 * math.sin(TAU * t * 2)))
        # Baum: drei gestufte Dreiecke
        for top, bot, half in self.TIERS:
            if top <= y <= bot:
                w = half * (y - top) / (bot - top)
                if abs(x - 60) <= w:
                    k = (abs(x - 60) / max(1, w))
                    c = lerp(hx('#1f6b3a'), hx('#0b331c'), k * 0.8 + 0.2 * ((y - top) / (bot - top)))
                    # Nadel-Textur
                    if (int(x + y * 0.5) % 5 == 0): c = lerp(c, hx('#2f8a4c'), 0.5)
        if 170 <= y <= 184 and abs(x - 60) <= 7: c = hx('#5a3012')
        # Geschenke
        for gx, gw, gh, col in ((22, 22, 16, '#2a7fd6'), (92, 20, 14, '#e8283a'), (40, 12, 10, '#ffcf3a')):
            if gx - gw / 2 <= x <= gx + gw / 2 and FH - 6 - gh <= y <= FH - 6:
                c = hx(col)
                if abs(x - gx) < 1.5 or abs(y - (FH - 6 - gh / 2)) < 1.5: c = hx('#fff4c8')
        return c
    def draws(self, t):
        d, g = [], []
        cols = [hx('#ff3b4e'), hx('#ffd23f'), hx('#3fb6ff'), hx('#5bff7a'), hx('#ff7ad9')]
        # Girlande + Lichter
        for i, (y0, k, n) in enumerate(self.LIGHTS):
            half = self.width(y0 + 3) * 0.9
            x = 60 - half + (k + 0.5) * 2 * half / n
            y = y0 + 4 * math.sin(math.pi * (k + 0.5) / n) + (x - 60) * 0.12
            col = cols[i % len(cols)]
            on = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(TAU * (t * 2 + i * 0.37)))
            g += ["-fill", rgba(col, on), "-draw", f"circle {x:.1f},{y:.1f} {x + 5:.1f},{y:.1f}"]
            d += ["-fill", rgba(lerp(col, (255, 255, 255), on * 0.45)), "-draw", f"circle {x:.1f},{y:.1f} {x + 2.2:.1f},{y:.1f}"]
        for y0 in (58, 84, 112, 126, 150, 164):
            half = self.width(y0 + 3) * 0.9
            pts = " ".join(f"{60 - half + s * half / 10:.1f},{y0 + 4 * math.sin(math.pi * s / 20) + (-half + s * half / 10) * 0.12 + 1.5:.1f}" for s in range(21))
            d += ["-fill", "none", "-stroke", rgba(hx('#e8c25a'), 0.7), "-strokewidth", "0.8", "-draw", f"polyline {pts}", "-stroke", "none"]
        # Stern (pulsierend, dreht Strahlen)
        sx, sy = 60, 30
        pts = []
        for j in range(10):
            a = -math.pi / 2 + j * math.pi / 5
            r = 13 if j % 2 == 0 else 5.5
            pts.append(f"{sx + r * math.cos(a):.1f},{sy + r * math.sin(a):.1f}")
        pulse = 0.7 + 0.3 * math.sin(TAU * t * 2)
        g += ["-fill", rgba(hx('#ffd23f'), pulse), "-draw", f"circle {sx},{sy} {sx + 20},{sy}"]
        for j in range(8):
            a = TAU * (j / 8 + t / 8)
            g += ["-stroke", rgba(hx('#fff1a0'), 0.6 * pulse), "-strokewidth", "1.6", "-draw", f"line {sx},{sy} {sx + 30 * math.cos(a):.1f},{sy + 30 * math.sin(a):.1f}", "-stroke", "none"]
        d += ["-fill", rgba(hx('#ffd23f')), "-stroke", rgba(hx('#fff6c0')), "-strokewidth", "1", "-draw", "polygon " + " ".join(pts), "-stroke", "none"]
        d += snow(t, 18, 4000, smin=0.5, smax=1.2, speed=(1, 1), sway=3, alpha=0.6)
        return d, g
    def edge(self, t): return hx('#ffd23f'), hx('#1f6b3a')

DESIGNS = [SnowNight(), Gingerbread(), CandyCane(), XmasTree()]
