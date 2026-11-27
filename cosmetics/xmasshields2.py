"""Schilde „Winterzauber II“ (Schneekugel, Lebkuchenhaus, Türkranz) im Format von cosmetics/shields.py:
24 Bilder, gezeichnet 12-fach im 64x64-Format des Minecraft-Schilds (Vorderseite 12x22 bei (1,1), Rückseite bei (14,1),
Ränder, Griff bei (26,0)), ausgeliefert 6-fach = 384x384. Detail: Schattierungs-Rampen, 2x2-Bayer-Rasterung in
Verläufen (wirkt nach dem Verkleinern wie Pixel-Dithering), Glanzlichter und eine weich gezeichnete Leucht-Ebene.
Aufruf: xmasshields2.py <ausgabeordner> [id ...] (build.py: build/shields-xmas2, wird unverändert eingepackt)
"""
import json, math, os, shutil, subprocess, sys
from multiprocessing import Pool

OUT = sys.argv[1]; ONLY = sys.argv[2:]
N = 24; K = 12; FW, FH = 12 * K, 22 * K; W = H = 64 * K
TMP = os.path.join(OUT, "tmp")
TAU = 2 * math.pi
SANS = "DejaVu-Sans-Bold"


def cl(v, a=0.0, b=1.0): return a if v < a else b if v > b else v
def lerp(a, b, t): t = cl(t); return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def hx(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
def add(a, b, k=1.0): return tuple(min(255, a[i] + b[i] * k) for i in range(3))
def mul(a, k): return tuple(a[i] * k for i in range(3))
def ss(e0, e1, x): t = cl((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t)
def rnd(i): v = math.sin(i * 12.9898 + 78.233) * 43758.5453; return v - math.floor(v)
def rgba(c, a=1.0): return f"rgba({int(c[0])},{int(c[1])},{int(c[2])},{cl(a):.3f})"
def run(args): subprocess.run(["convert", *args], check=True)
def h2(x, y): return rnd(int(x) * 73.1 + int(y) * 157.7)

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
def dramp(cols, v, x, y):
    """Rampe aus festen Tönen mit geordnetem Dithering (2x2-Blöcke → nach Verkleinern 1 Pixel)."""
    v = cl(v) * (len(cols) - 1)
    i = int(v); f = v - i
    if i >= len(cols) - 1: return cols[-1]
    th = (BAYER[(int(y) // 2) % 4][(int(x) // 2) % 4] + 0.5) / 16
    return cols[i + 1] if f > th else cols[i]

SNOW = [hx(h) for h in ('#a9bfdf', '#d6e4f7', '#f4f8ff')]
FIR = [hx(h) for h in ('#0a2a20', '#0f3b2c', '#1d5a3c', '#2f7d4c', '#4fa35e')]
RED = [hx(h) for h in ('#4a0812', '#7a0f1e', '#b3172c', '#d92a3f', '#ff5a64')]
GOLD = [hx(h) for h in ('#7a4a10', '#b8801c', '#e8b23a', '#ffd96a', '#fff3b8')]
WOOD = [hx(h) for h in ('#2e170c', '#4a2614', '#7a4422', '#a5632c', '#c98a4e')]
CANDLE = [hx(h) for h in ('#ff9a2a', '#ffc24a', '#ffe8a0')]
NIGHT = [hx(h) for h in ('#070a1f', '#0d1636', '#16244f', '#22366a')]


def raster(fn, t, path):
    buf = bytearray()
    for y in range(FH):
        for x in range(FW):
            c = fn(x + 0.5, y + 0.5, t)
            buf += bytes((int(cl(c[0], 0, 255)), int(cl(c[1], 0, 255)), int(cl(c[2], 0, 255))))
    open(path + ".rgb", "wb").write(buf)
    run(["-size", f"{FW}x{FH}", "-depth", "8", f"rgb:{path}.rgb", path]); os.remove(path + ".rgb")


def twinkle(x, y, a, r=4, col=(255, 255, 255)):
    return ["-stroke", rgba(col, a), "-strokewidth", "1", "-draw", f"line {x - r:.1f},{y:.1f} {x + r:.1f},{y:.1f}",
            "-draw", f"line {x:.1f},{y - r:.1f} {x:.1f},{y + r:.1f}", "-stroke", "none"]


def falling_snow(t, n, seed, clip=None, ymin=0, ymax=FH, rmin=0.7, rmax=1.7, alpha=0.9):
    d = []
    for i in range(n):
        sp = 1 + int(rnd(seed + i) * 2)
        y = ymin + ((t * sp + rnd(seed + i + 40)) % 1) * (ymax - ymin + 8) - 4
        x = rnd(seed + i + 80) * FW + 4 * math.sin(TAU * (t * sp + rnd(seed + i + 3)))
        if clip and not clip(x, y): continue
        r = rmin + (rmax - rmin) * rnd(seed + i + 7)
        d += ["-fill", rgba((246, 250, 255), alpha * (0.6 + 0.4 * rnd(seed + i + 9))), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
    return d


class Design:
    glow_blur = 4
    def draws(self, t): return [], []
    def edge(self, t): return GOLD[2], hx('#3a1a0a')


# =================================================================== 1. Schneekugel
GX, GY, GR = 72, 116, 60  # Glaskugel
class SnowGlobe(Design):
    name = "schneekugel"
    HOUSES = [(36, 152, 20, 15, "#b3402e"), (96, 150, 20, 13, "#3a6aa8"), (110, 160, 14, 10, "#a5632c")]

    def ground(self, x): return 148 + 6 * math.sin((x - 20) / 18) + 3 * math.sin(x / 6.5)

    def bg(self, x, y, t):
        # Hintergrund: dunkles Nachtblau mit Rauten-Muster (Geschenkpapier-Anmutung, ruhig)
        c = dramp(NIGHT[:3], 0.25 + 0.5 * y / FH, x, y)
        if (int((x + y) / 12) + int((x - y) / 12)) % 2 == 0: c = add(c, (10, 14, 30))
        # Leiser Schein hinter der Kugel
        c = add(c, (90, 130, 220), 0.35 * math.exp(-max(0, math.hypot(x - GX, y - GY) - GR) / 16))
        # Sockel: Holz mit Goldband, Trapez
        if 170 <= y <= 222:
            k = (y - 170) / 52
            half = 44 + 12 * k
            if abs(x - GX) <= half:
                lx = (x - GX) / half
                if y < 180:  # Goldring oben
                    c = dramp(GOLD, 0.75 - 0.5 * abs(lx + 0.3) + 0.2 * (1 - (y - 170) / 10), x, y)
                    if abs(lx + 0.45) < 0.06: c = GOLD[4]
                elif y > 212:  # Goldfuß
                    c = dramp(GOLD, 0.6 - 0.45 * abs(lx + 0.3), x, y)
                    if y > 220: c = GOLD[0]
                else:
                    g = 0.55 - 0.45 * abs(lx + 0.25) + 0.12 * math.sin(x / 2.3 + math.sin(y / 9) * 3)
                    c = dramp(WOOD[1:], g, x, y)
                    # Messing-Schild "MYTIC"
                    if abs(x - GX) < 24 and 188 <= y <= 204:
                        c = dramp(GOLD, 0.65 - 0.4 * (y - 188) / 16, x, y)
                        if abs(x - GX) > 22.5 or y < 189.5 or y > 202.5: c = GOLD[0]
                if abs(lx) > 0.96: c = mul(c, 0.6)
        # Glaskugel (über dem Sockel)
        d = math.hypot(x - GX, y - GY)
        if d <= GR:
            c = self.inside(x, y, t, d)
            # Glas: Rand dunkler + bläulich, Fresnel
            fr = ss(GR - 10, GR, d)
            c = lerp(c, hx('#9fc8ff'), 0.35 * fr)
            if d > GR - 1.6: c = hx('#e8f4ff')
            # Glanzbogen oben links
            ang = math.atan2(y - GY, x - GX)
            if GR - 11 < d < GR - 5 and -2.7 < ang < -1.6: c = lerp(c, (255, 255, 255), 0.75 * ss(-2.7, -2.4, ang) * ss(-1.6, -1.9, ang))
            # kleines Glanzlicht
            if math.hypot(x - (GX - 26), y - (GY - 30)) < 5: c = lerp(c, (255, 255, 255), 0.9)
            if math.hypot(x - (GX - 17), y - (GY - 38)) < 2.4: c = lerp(c, (255, 255, 255), 0.8)
            # Spiegelung rechts unten (schwach)
            if GR - 14 < d < GR - 8 and 0.3 < ang < 1.0: c = lerp(c, hx('#cfe6ff'), 0.3)
        return c

    def inside(self, x, y, t, d):
        # Himmel: Verlauf, wenige Sterne
        c = dramp([hx('#14204a'), hx('#24407a'), hx('#3a64a8'), hx('#6a94d0')], (y - (GY - GR)) / (GR * 1.5), x, y)
        if h2(x / 2, y / 2) > 0.992 and y < 130: c = (230, 240, 255)
        # Ferne Hügel
        if y > 136 + 5 * math.sin(x / 11): c = dramp(SNOW, 0.3 + 0.2 * math.sin(x / 7), x, y)
        # Tanne in der Mitte (gestufte Etagen mit Schnee)
        tx, top, bot = 70, 78, 150
        if top <= y <= bot + 4:
            k = (y - top) / (bot - top)
            step = (k * 5) % 1
            half = 30 * (0.2 + 0.8 * step) * (0.25 + 0.75 * k)
            if abs(x - tx) <= half and y <= bot:
                shade = 0.65 - 0.5 * (x - tx) / max(half, 1) * 0.6 - 0.25 * step
                c = dramp(FIR, shade, x, y)
                if step < 0.18: c = dramp(SNOW, 0.6 + 0.4 * (1 - step / 0.18), x, y)
                # Lichter am Baum
                for i in range(14):
                    lx, ly = tx + (rnd(i + 3) - 0.5) * 50 * (0.3 + 0.7 * rnd(i + 5)), top + 10 + rnd(i + 7) * 60
                    if math.hypot(x - lx, y - ly) < 1.8 and abs(x - tx) < half - 1:
                        on = 0.5 + 0.5 * math.sin(TAU * (t * 2 + i * 0.37))
                        c = lerp(hx(['#ff5a64', '#ffd96a', '#7fd0ff', '#9bff8a'][i % 4]), (255, 255, 255), 0.3 * on)
            if abs(x - tx) < 4 and bot < y <= bot + 4: c = WOOD[2]
        # Stern auf der Tanne
        if math.hypot(x - tx, y - (top - 3)) < 4.5 - 1.5 * abs(math.sin(2.5 * math.atan2(y - top + 3, x - tx))): c = GOLD[4]
        # Häuschen
        for i, (hxp, hyp, hw, hh, col) in enumerate(self.HOUSES):
            if hxp - hw / 2 <= x <= hxp + hw / 2 and hyp - hh <= y <= hyp:
                c = dramp([mul(hx(col), 0.6), hx(col), add(hx(col), (40, 30, 20))], 0.7 - 0.6 * (x - hxp + hw / 2) / hw, x, y)
                # Fenster
                wx, wy = hxp - hw / 4, hyp - hh * 0.55
                if abs(x - wx) < 2.6 and abs(y - wy) < 2.6:
                    fl = 0.85 + 0.15 * math.sin(TAU * (t * 3) + i * 2)
                    c = lerp(CANDLE[0], CANDLE[2], fl * (1 - math.hypot(x - wx, y - wy) / 4))
                    if abs(x - wx) < 0.5 or abs(y - wy) < 0.5: c = mul(hx(col), 0.5)
                if abs(x - (hxp + hw / 4)) < 2.2 and y > hyp - 6: c = WOOD[1]
            roof = hyp - hh - (hw / 2 + 2 - abs(x - hxp)) * 0.7
            if abs(x - hxp) <= hw / 2 + 2 and roof <= y < hyp - hh + 0.5:
                c = dramp(SNOW, 0.95 - 0.6 * (y - roof) / 8, x, y)
        # Vordergrund-Schnee
        gl = self.ground(x)
        if y > gl: c = dramp(SNOW, 0.95 - (y - gl) / 30, x, y)
        # warmer Fensterschein
        for hxp, hyp, hw, hh, _ in self.HOUSES:
            c = add(c, (255, 170, 70), 0.18 * math.exp(-math.hypot(x - hxp + hw / 4, y - hyp + hh * 0.55) / 7))
        return c

    def draws(self, t):
        d, g = [], []
        # Wirbelnder Schnee in der Kugel: kreist um die Mitte, nahtlos (ganzzahlige Umläufe)
        for i in range(70):
            r0 = 8 + rnd(i + 11) * (GR - 14)
            turns = 1 if i % 3 else 2
            a = TAU * (rnd(i + 21) + turns * t * (1 if i % 2 else -1) * 0.999)
            x = GX + math.cos(a) * r0 * 0.95
            y = GY + math.sin(a) * r0 * 0.75 + 6 * math.sin(TAU * (t + rnd(i)))
            if math.hypot(x - GX, y - GY) > GR - 4: continue
            r = 0.7 + 1.2 * rnd(i + 31)
            d += ["-fill", rgba((250, 252, 255), 0.6 + 0.4 * rnd(i + 41)), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
            # Glitzer: einzelne Flocken blitzen golden auf
            fl = max(0.0, math.sin(TAU * (t * 2 + rnd(i + 51)))) ** 10
            if i % 5 == 0 and fl > 0.1:
                d += twinkle(x, y, fl, 3.5, (255, 240, 190))
                g += ["-fill", rgba(GOLD[3], fl), "-draw", f"circle {x:.1f},{y:.1f} {x + 4:.1f},{y:.1f}"]
        # Schein um Lichter / Fenster / Stern
        g += ["-fill", rgba(GOLD[3], 0.85), "-draw", f"circle 70,75 79,75"]
        for hxp, hyp, hw, hh, _ in self.HOUSES:
            g += ["-fill", rgba(CANDLE[1], 0.7), "-draw", f"circle {hxp - hw / 4},{hyp - hh * 0.55} {hxp - hw / 4 + 5},{hyp - hh * 0.55}"]
        d += ["-font", SANS, "-pointsize", "10", "-fill", rgba(WOOD[0]), "-gravity", "North", "-annotate", "+0+190", "MYTIC", "-gravity", "NorthWest"]
        # Sternfunkeln außen
        for i in range(8):
            x, y = 8 + rnd(i + 300) * (FW - 16), 8 + rnd(i + 320) * (FH - 16)
            if math.hypot(x - GX, y - GY) < GR + 6 or 165 < y < 226: continue
            a = max(0, math.sin(TAU * (t * 2 + rnd(i + 340)))) ** 6
            if a > 0.05: d += twinkle(x, y, a, 4)
        d += ["-fill", "none", "-stroke", rgba(GOLD[2], 0.9), "-strokewidth", "3", "-draw", f"rectangle 4,4 {FW - 4},{FH - 4}",
              "-stroke", rgba(GOLD[0], 0.9), "-strokewidth", "1", "-draw", f"rectangle 7,7 {FW - 7},{FH - 7}", "-stroke", "none"]
        return d, g

    def edge(self, t): return GOLD[2], hx('#16244f')


# =================================================================== 2. Lebkuchenhaus
HX0, HX1, HY0, HY1 = 26, 118, 132, 214  # Wände
RIDGE = 70  # First (y)
GINGER = [hx(h) for h in ('#4a2614', '#6e3a18', '#8e5224', '#a5632c', '#c98a4e')]
ICING = [hx(h) for h in ('#c9d4e6', '#eef3fb', '#ffffff')]

class GingerHouse(Design):
    name = "lebkuchenhaus"

    def roof_y(self, x): return RIDGE + abs(x - 72) * 0.95  # Dachkante (Unterseite der Schneeschicht)

    def bg(self, x, y, t):
        c = dramp(NIGHT, 0.15 + 0.85 * y / 200, x, y)
        if h2(x / 2, y / 2) > 0.994 and y < 120: c = (220, 230, 255)
        # Mond
        dm = math.hypot(x - 116, y - 30)
        c = add(c, (140, 160, 255), 0.25 * math.exp(-max(0, dm - 10) / 14))
        if dm < 10: c = dramp(SNOW, 0.9 - 0.3 * (x - 106) / 20 - (0.6 if math.hypot(x - 121, y - 27) < 8 else 0), x, y)
        # Boden
        gy = 214 + 3 * math.sin(x / 13)
        if y > gy: c = dramp(SNOW, 0.95 - (y - gy) / 40, x, y)
        # Schornstein (hinter dem Dach)
        if 90 <= x <= 104 and 58 <= y <= 100:
            c = dramp(GINGER[1:], 0.75 - 0.5 * (x - 90) / 14, x, y)
            if (int(y) - 58) % 8 < 1 or ((int(y) - 58) // 8 % 2 and abs(x - 97) < 0.6): c = ICING[1]
            if y < 63: c = dramp(SNOW, 0.95 - (y - 58) / 12, x, y)
        # Wände: Lebkuchen mit Poren + Rautenmuster aus Zuckerguss
        if HX0 <= x <= HX1 and self.roof_y(x) <= y <= HY1:
            n = h2(x / 2, y / 2)
            c = dramp(GINGER, 0.72 - 0.35 * (x - HX0) / (HX1 - HX0) + (0.12 if n > 0.85 else -0.08 if n < 0.12 else 0), x, y)
            if n > 0.97: c = GINGER[0]  # Poren
            u, v = (x - 72) + (y - 150), (x - 72) - (y - 150)
            if y > 120 and (abs(u % 22 - 11) < 0.9 or abs(v % 22 - 11) < 0.9): c = ICING[1]
            # Fenster links/rechts (warm, flackernd), Tür Mitte
            for wx in (44, 100):
                if abs(x - wx) <= 10 and 148 <= y <= 170:
                    fl = 0.8 + 0.2 * math.sin(TAU * (t * 3 + wx * 0.01)) * math.sin(TAU * (t * 5 + 0.3))
                    k = 1 - math.hypot(x - wx, y - 162) / 16
                    c = lerp(CANDLE[0], CANDLE[2], cl(k * fl + 0.15))
                    if abs(x - wx) < 0.9 or abs(y - 159) < 0.9: c = ICING[2]
                    if abs(x - wx) > 8.6 or y < 149.5 or y > 168.6: c = ICING[1]
                if abs(x - wx) <= 12 and 170 < y <= 174:  # Fensterbank mit Schnee
                    c = dramp(SNOW, 0.9 - (y - 170) / 6, x, y)
            if abs(x - 72) <= 11 and 176 <= y <= HY1:
                top = 176 + (11 - math.sqrt(max(0, 121 - (x - 72) ** 2)))
                if y >= top:
                    c = dramp(RED[1:4], 0.7 - 0.4 * (x - 61) / 22, x, y)
                    if abs(x - 72) < 0.8: c = RED[0]
                    if math.hypot(x - 77, y - 196) < 1.6: c = GOLD[3]
                    if abs(math.hypot(x - 72, y - 187) - 11) < 0.9 or abs(x - 72) > 10.2: c = ICING[1]
        # Dach: Schnee-Schicht mit Zuckerguss-Schuppen, Kante mit Tropfen
        ry = self.roof_y(x)
        if 14 <= x <= 130 and ry - 14 <= y <= ry:
            k = (ry - y) / 14
            c = dramp(ICING, 0.5 + 0.5 * k - 0.25 * (x - 72) / 60, x, y)
            # Schuppen (Plätzchen-Ziegel)
            sx, sy = x - 72, y - RIDGE
            if (int(sy / 6) + int(sx / 9)) % 2 == 0 and k < 0.6: c = lerp(c, GINGER[3], 0.4)
        # Tropfen unter der Dachkante
        if 14 <= x <= 130 and ry < y < ry + 4 + 6 * (rnd(int(x / 5)) > 0.55) * ss(0, 1, math.sin((x % 5) / 5 * math.pi)):
            c = dramp(ICING, 0.7, x, y)
        # Zuckerguss-Linie unten an den Wänden
        if HX0 - 2 <= x <= HX1 + 2 and HY1 - 3 <= y <= HY1: c = ICING[2] if (int(x) // 4) % 2 else ICING[1]
        return c

    def draws(self, t):
        d, g = [], []
        # Bonbons auf dem Dach (Gummidrops) entlang der Kante
        cols = ['#d92a3f', '#4fa35e', '#ffd96a', '#3fa8ff', '#ff5a64']
        for i, x in enumerate(range(22, 126, 13)):
            y = self.roof_y(x) - 6
            col = hx(cols[i % len(cols)])
            d += ["-fill", rgba(mul(col, 0.6)), "-draw", f"ellipse {x},{y + 1} 4.4,4 0,360",
                  "-fill", rgba(col), "-draw", f"ellipse {x},{y} 4,3.6 0,360",
                  "-fill", rgba((255, 255, 255), 0.8), "-draw", f"circle {x - 1.4},{y - 1.4} {x - 0.4},{y - 1.4}"]
        # Bonbon-Knöpfe an der Wand (rot-weiß gedreht, drehen sich)
        for i, (bx, by) in enumerate(((36, 136 + 0), (108, 136), (52, 190), (92, 190))):
            if by < self.roof_y(bx) + 5: by = self.roof_y(bx) + 8
            d += ["-fill", rgba(ICING[2]), "-draw", f"circle {bx},{by} {bx + 4.5},{by}"]
            for k in range(3):
                a0 = (TAU * (t + k / 3)) * 180 / math.pi
                d += ["-fill", rgba(RED[3]), "-draw", f"path 'M {bx},{by} L {bx + 4.5 * math.cos(math.radians(a0)):.1f},{by + 4.5 * math.sin(math.radians(a0)):.1f} A 4.5,4.5 0 0,1 {bx + 4.5 * math.cos(math.radians(a0 + 60)):.1f},{by + 4.5 * math.sin(math.radians(a0 + 60)):.1f} Z'"]
        # Zuckerstange neben der Tür
        for side in (-1, 1):
            x0 = 72 + side * 18
            d += ["-stroke", rgba(ICING[2]), "-strokewidth", "3.4", "-fill", "none", "-draw", f"line {x0},{HY1} {x0},188",
                  "-draw", f"arc {x0 - 0 if side < 0 else x0 - 8},{184} {x0 + 8 if side < 0 else x0},{192} 180,360", "-stroke", "none"]
            for yy in range(190, HY1, 5):
                d += ["-stroke", rgba(RED[3]), "-strokewidth", "1.6", "-draw", f"line {x0 - 1.6},{yy + 1.5} {x0 + 1.6},{yy - 1}", "-stroke", "none"]
        # Rauch aus dem Schornstein (steigt nahtlos)
        for i in range(6):
            k = (t + i / 6) % 1
            x = 97 + 6 * math.sin(TAU * k + i) + 10 * k
            y = 55 - 46 * k
            r = 3 + 6 * k
            d += ["-fill", rgba((210, 220, 240), 0.55 * (1 - k)), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
        # Leuchten der Fenster
        for wx in (44, 100):
            fl = 0.8 + 0.2 * math.sin(TAU * (t * 3 + wx * 0.01))
            g += ["-fill", rgba(CANDLE[1], 0.75 * fl), "-draw", f"rectangle {wx - 12},146 {wx + 12},172"]
        g += ["-fill", rgba((190, 200, 255), 0.4), "-draw", "circle 116,30 130,30"]
        d += falling_snow(t, 26, 7, ymax=FH)
        d += ["-fill", "none", "-stroke", rgba(ICING[1], 0.95), "-strokewidth", "3", "-draw", f"roundrectangle 4,4 {FW - 4},{FH - 4} 6,6",
              "-stroke", rgba(GINGER[2], 0.9), "-strokewidth", "1", "-draw", f"roundrectangle 7,7 {FW - 7},{FH - 7} 5,5", "-stroke", "none"]
        # Zuckerperlen auf dem Rahmen
        for i in range(0, 2 * (FW + FH), 18):
            p = i
            if p < FW: x, y = p, 5.5
            elif p < FW + FH: x, y = FW - 5.5, p - FW
            elif p < 2 * FW + FH: x, y = FW - (p - FW - FH), FH - 5.5
            else: x, y = 5.5, FH - (p - 2 * FW - FH)
            col = hx(cols[(i // 18) % len(cols)])
            d += ["-fill", rgba(col), "-draw", f"circle {x:.1f},{y:.1f} {x + 2:.1f},{y:.1f}"]
        return d, g

    def edge(self, t): return ICING[1], GINGER[0]


# =================================================================== 3. Türkranz
WX, WY, RO, RI = 72, 118, 52, 24  # Kranz: Mitte, außen, innen
class DoorWreath(Design):
    name = "tuerkranz"
    LIGHTS = [(WX + math.cos(a) * r, WY + math.sin(a) * r) for a, r in
              [(TAU * i / 16 + 0.2, (RO + RI) / 2 + (6 if i % 2 else -6)) for i in range(16)] if not (0.9 < (a % TAU) < 2.25)]
    CONES = [(-0.4, 44), (2.6, 42), (3.6, 30), (4.9, 44), (5.8, 32)]
    BERRIES = [(a * 0.73 + 0.3, 30 + (a * 7) % 18) for a in range(9)]

    def bg(self, x, y, t):
        # Tür: senkrechte Bretter mit Maserung, Fugen, Nägeln
        plank = int((x - 2) // 24)
        px = (x - 2) % 24
        grain = 0.5 + 0.18 * math.sin(y / 5.5 + plank * 2 + 1.5 * math.sin(px / 4 + y / 30)) + 0.08 * math.sin(px * 1.7 + plank)
        knot = math.hypot((px - 12) * 1.4, y - (60 + plank * 37) % 230)
        if knot < 5: grain -= 0.25 * (1 - knot / 5)
        c = dramp(WOOD, grain * 0.9 - 0.1 * (plank % 2) + 0.12, x, y)
        if px < 1.6: c = WOOD[0]
        if px > 22.5: c = mul(dramp(WOOD, grain * 0.8, x, y), 0.8)
        # Querriegel oben/unten
        for ry in (26, 236):
            if abs(y - ry) < 7:
                c = dramp(WOOD, 0.75 - (y - ry + 7) / 20, x, y)
                if abs(y - ry) > 6: c = WOOD[0]
                for nx in range(14, FW, 24):
                    if math.hypot(x - nx, y - ry) < 2.2: c = lerp(hx('#3a3a44'), hx('#a0a0b0'), 1 - math.hypot(x - nx + 0.8, y - ry + 0.8) / 2.2)
        # warmer Schein von der Lichterkette
        c = add(c, (255, 170, 70), 0.12 * math.exp(-abs(math.hypot(x - WX, y - WY) - (RO + RI) / 2) / 20))
        # Schatten des Kranzes auf der Tür
        dd = math.hypot(x - WX - 3, y - WY - 4)
        if RI - 2 < dd < RO + 2: c = mul(c, 0.72)
        # Kranz: Tannenzweige als kleine Nadelbüschel
        d = math.hypot(x - WX, y - WY)
        a = math.atan2(y - WY, x - WX)
        wob = 3 * math.sin(a * 13) + 2 * math.sin(a * 29 + 1)
        if RI - wob * 0.5 <= d <= RO + wob:
            # Nadeln: Streifen schräg zur Kranzrichtung
            n = math.sin(a * 60 + d * 0.9) * math.sin(a * 23 - d * 0.6 + 2)
            light = 0.55 - 0.35 * math.sin(a + 0.8) * 0.8 + 0.25 * n + 0.15 * (1 - abs(d - (RO + RI) / 2) / ((RO - RI) / 2))
            c = dramp(FIR, light, x, y)
            if n > 0.85: c = FIR[4]
            # Schneehauch oben
            if a < -0.6 and a > -2.6 and n > 0.5 and d > (RO + RI) / 2: c = lerp(c, SNOW[2], 0.7)
            # Tannenzapfen
            for ca, cr in self.CONES:
                cx, cy = WX + math.cos(ca) * cr, WY + math.sin(ca) * cr
                u, v = (x - cx), (y - cy)
                if (u / 5) ** 2 + (v / 7.5) ** 2 < 1:
                    sc = (int((v + 8) / 2.5) + (int((u + 6) / 3) if int((v + 8) / 2.5) % 2 else int((u + 7.5) / 3))) % 2
                    c = dramp(WOOD, 0.55 - 0.3 * (u / 5) - 0.25 * sc + 0.1 * (-v / 7.5), x, y)
            # rote Beeren-Büschel
            for ba, br in self.BERRIES:
                bx, by = WX + math.cos(ba) * br, WY + math.sin(ba) * br
                for k in range(3):
                    ex, ey = bx + (k - 1) * 3.2, by + (k % 2) * 2.5
                    q = math.hypot(x - ex, y - ey)
                    if q < 2.2: c = dramp(RED[1:], 0.9 - q / 2.2 * 0.8 - (x - ex + y - ey) * 0.1, x, y)
        return c

    def bells(self, t):
        sw = 0.28 * math.sin(TAU * t)
        return [(WX - 27, 184, sw), (WX + 27, 186, -sw * 0.8)]

    def draws(self, t):
        d, g = [], []
        # Lichter: warmweiß, eins nach dem anderen hell (Lauflicht)
        n = len(self.LIGHTS)
        for i, (lx, ly) in enumerate(self.LIGHTS):
            ph = (t * 2 - i / n) % 1
            on = 0.35 + 0.65 * math.exp(-((ph - 0.5) ** 2) / 0.02) + 0.15 * math.sin(TAU * (t * 4 + i * 0.3))
            col = lerp(CANDLE[0], CANDLE[2], on)
            d += ["-fill", rgba((40, 30, 20)), "-draw", f"rectangle {lx - 1},{ly - 3.4} {lx + 1},{ly - 1.6}",
                  "-fill", rgba(col), "-draw", f"ellipse {lx:.1f},{ly:.1f} 1.8,2.4 0,360",
                  "-fill", rgba((255, 255, 255), on * 0.9), "-draw", f"circle {lx - 0.5:.1f},{ly - 0.6:.1f} {lx + 0.3:.1f},{ly - 0.6:.1f}"]
            g += ["-fill", rgba(CANDLE[1], cl(on)), "-draw", f"circle {lx:.1f},{ly:.1f} {lx + 5 + 3 * on:.1f},{ly:.1f}"]
        # Glöckchen unter der Schleife (schwingen gegeneinander)
        for bx, by, sw in self.bells(t):
            ca, sa = math.cos(sw), math.sin(sw)
            def P(u, v): return f"{bx + u * ca - v * sa:.1f},{by + u * sa + v * ca:.1f}"
            d += ["-stroke", rgba(GOLD[1]), "-strokewidth", "1", "-draw", f"line {bx},{by - 16} {P(0, -9)}", "-stroke", "none",
                  "-fill", rgba(GOLD[0]), "-draw", f"polygon {P(-3.5, -9)} {P(3.5, -9)} {P(8.5, 4)} {P(-8.5, 4)}",
                  "-fill", rgba(GOLD[2]), "-draw", f"polygon {P(-3, -9)} {P(2, -9)} {P(6, 3)} {P(-7.5, 3)}",
                  "-fill", rgba(GOLD[3]), "-draw", f"polygon {P(-2.5, -8)} {P(-0.5, -8)} {P(-3, 2)} {P(-6, 2)}",
                  "-fill", rgba(GOLD[4]), "-draw", f"line {P(-2.2, -6)} {P(-4.2, 0)}",
                  "-fill", rgba(GOLD[1]), "-draw", f"ellipse {P(0, 4)} 9,1.8 {math.degrees(sw):.1f},{math.degrees(sw) + 360:.1f}",
                  "-fill", rgba(WOOD[0]), "-draw", f"circle {P(math.sin(TAU * t) * 1.5, 6)} {P(math.sin(TAU * t) * 1.5 + 2.2, 6)}"]
            g += ["-fill", rgba(GOLD[3], 0.5), "-draw", f"circle {bx},{by} {bx + 8},{by}"]
        # Schleife (Samt): zwei Schlaufen, Knoten, zwei Bänder mit Schwalbenschwanz
        bx, by = WX, WY + RO - 10
        R0, R1, R2, R3, R4 = (rgba(c) for c in RED)
        sway = 1.5 * math.sin(TAU * t)
        for s in (-1, 1):
            # Band
            e = s * (14 + sway * s)
            d += ["-fill", R0, "-draw", f"polygon {bx + s * 2},{by + 2} {bx + e},{by + 44} {bx + e + s * 4},{by + 40} {bx + e + s * 8},{by + 46} {bx + s * 9},{by + 2}",
                  "-fill", R2, "-draw", f"polygon {bx + s * 2},{by + 2} {bx + e - s * 1},{by + 40} {bx + e + s * 3},{by + 37} {bx + s * 7},{by + 2}",
                  "-fill", R3, "-draw", f"polygon {bx + s * 3},{by + 4} {bx + e - s * 0},{by + 30} {bx + e + s * 1.5},{by + 29} {bx + s * 5},{by + 4}"]
            # Schlaufe
            d += ["-fill", R0, "-draw", f"ellipse {bx + s * 15},{by - 4} 16,11 0,360",
                  "-fill", R2, "-draw", f"ellipse {bx + s * 14},{by - 5} 14,9 0,360",
                  "-fill", R1, "-draw", f"ellipse {bx + s * 10},{by - 3} 6,4 0,360",
                  "-fill", R3, "-draw", f"ellipse {bx + s * 17},{by - 9} 8,3 {-15 * s},{360 - 15 * s}",
                  "-fill", R4, "-draw", f"ellipse {bx + s * 19},{by - 10} 4,1.3 {-15 * s},{360 - 15 * s}"]
        d += ["-fill", R0, "-draw", f"roundrectangle {bx - 6},{by - 9} {bx + 6},{by + 5} 3,3",
              "-fill", R2, "-draw", f"roundrectangle {bx - 5},{by - 8} {bx + 5},{by + 3} 3,3",
              "-fill", R4, "-draw", f"line {bx - 2},{by - 6} {bx - 2},{by + 1}",
              # Goldkante der Schleife
              "-fill", "none", "-stroke", rgba(GOLD[2], 0.8), "-strokewidth", "0.8", "-draw", f"ellipse {bx - 15},{by - 4} 16,11 0,360",
              "-draw", f"ellipse {bx + 15},{by - 4} 16,11 0,360", "-stroke", "none"]
        # Samt-Glanz wandert
        gx = bx - 30 + 60 * ((t * 1.0) % 1)
        g += ["-fill", rgba((255, 140, 150), 0.35), "-draw", f"ellipse {gx:.1f},{by - 6} 5,8 0,360"]
        # Türklopfer-Ring (Messing) – über dem Kranz
        d += ["-fill", "none", "-stroke", rgba(GOLD[1]), "-strokewidth", "2.5", "-draw", f"circle {WX},{WY} {WX + 9},{WY}",
              "-stroke", rgba(GOLD[3]), "-strokewidth", "1", "-draw", f"arc {WX - 9},{WY - 9} {WX + 9},{WY + 9} 190,280", "-stroke", "none",
              "-fill", rgba(GOLD[2]), "-draw", f"circle {WX},{WY - 10} {WX + 3},{WY - 10}"]
        d += ["-fill", "none", "-stroke", rgba(GOLD[2], 0.9), "-strokewidth", "3", "-draw", f"rectangle 4,4 {FW - 4},{FH - 4}",
              "-stroke", rgba(FIR[2], 0.9), "-strokewidth", "1", "-draw", f"rectangle 7,7 {FW - 7},{FH - 7}", "-stroke", "none"]
        return d, g

    def edge(self, t): return GOLD[2], WOOD[0]


DESIGNS = [SnowGlobe(), GingerHouse(), DoorWreath()]
META = {"schneekugel": ("Schneekugel", "Glaskugel auf Holzsockel mit Messingschild: verschneites Dorf, Tanne mit Lichtern, wirbelnder Glitzerschnee, Glasglanz."),
        "lebkuchenhaus": ("Lebkuchenhaus", "Lebkuchenhaus mit Zuckerguss-Rauten, Gummidrops, drehenden Bonbons, flackernden Fenstern und Rauch aus dem Schornstein."),
        "tuerkranz": ("Türkranz", "Holztür mit dichtem Tannenkranz, Zapfen und Beeren, Samtschleife, schwingenden Goldglöckchen und Lauflicht.")}


def shield_frame(job):
    di, f = job
    design = DESIGNS[di]
    t = f / N; name = design.name
    base = f"{TMP}/{name}_bg{f}.png"
    raster(design.bg, t, base)
    sharp, glow = design.draws(t)
    gpath, spath = f"{TMP}/{name}_gl{f}.png", f"{TMP}/{name}_sh{f}.png"
    run(["-size", f"{FW}x{FH}", "xc:none", *glow, "-blur", f"0x{design.glow_blur}", gpath])
    run(["-size", f"{FW}x{FH}", "xc:none", *sharp, spath])
    front = f"{TMP}/{name}_front{f}.png"
    run([base, gpath, "-compose", "screen", "-composite", spath, "-compose", "over", "-composite", front])
    a, b = design.edge(t)
    big = f"{TMP}/{name}_{f}_12x.png"
    run(["-size", f"{W}x{H}", "xc:none",
         front, "-geometry", f"+{K}+{K}", "-composite",
         "(", front, "-modulate", "45,75", ")", "-geometry", f"+{14 * K}+{K}", "-composite",
         "(", "-size", f"{K}x{FH}", f"gradient:{rgba(a)}-{rgba(b)}", ")", "-geometry", f"+0+{K}", "-composite",
         "(", "-size", f"{K}x{FH}", f"gradient:{rgba(a)}-{rgba(b)}", ")", "-geometry", f"+{13 * K}+{K}", "-composite",
         "(", "-size", f"{FW}x{K}", f"xc:{rgba(a)}", ")", "-geometry", f"+{K}+0", "-composite",
         "(", "-size", f"{FW}x{K}", f"xc:{rgba(b)}", ")", "-geometry", f"+{13 * K}+0", "-composite",
         "(", "-size", f"{16 * K}x{12 * K}", f"gradient:{rgba((70, 62, 58))}-{rgba((34, 30, 28))}", ")", "-geometry", f"+{26 * K}+0", "-composite",
         "-depth", "8", f"PNG32:{big}"])
    run([big, "-filter", "Lanczos", "-resize", "384x384!", "-depth", "8", f"PNG32:{OUT}/{name}_{f}.png"])


if __name__ == "__main__":
    os.makedirs(TMP, exist_ok=True)
    jobs = [(i, f) for i, dsg in enumerate(DESIGNS) if not ONLY or dsg.name in ONLY for f in range(N)]
    with Pool(16) as p: p.map(shield_frame, jobs)
    shutil.rmtree(TMP, ignore_errors=True)
    meta = [dict(id=d.name, name=META[d.name][0], desc=META[d.name][1], frames=N, frameMs=70) for d in DESIGNS]
    json.dump(meta, open(os.path.join(OUT, "meta2.json"), "w"), ensure_ascii=False, indent=1)
    print("Schilde fertig:", [d.name for d in DESIGNS if not ONLY or d.name in ONLY])
