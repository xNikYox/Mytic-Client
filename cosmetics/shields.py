# 12 animierte Mytic-Schilde (24 Bilder, 12-fache Auflösung). Aufruf: python3 shields.py AUSGABE [name ...]
# Die Textur hat das Format des Minecraft-Schilds (64x64): Vorderseite 12x22 bei (1,1), Rückseite bei (14,1),
# Ränder dazwischen, Griff bei (26,0).
import math, os, subprocess, sys
OUT = sys.argv[1]; ONLY = sys.argv[2:]
N = 24; K = 12; FW, FH = 12 * K, 22 * K; W = H = 64 * K
TMP = os.path.join(OUT, "tmp"); os.makedirs(TMP, exist_ok=True)
TAU = 2 * math.pi
SERIF = "DejaVu-Serif-Bold"
SANS = "DejaVu-Sans-Bold"
MONO = "DejaVu-Sans-Mono-Bold"

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
def run(args): subprocess.run(["convert", *args], check=True)
def noise(x, y, s=1.0):
    """Billiges Wolken-Rauschen aus Sinus-Wellen (0..1)."""
    n = (math.sin(x / 7.0 * s + y / 13.0 * s) + math.sin(y / 5.3 * s - x / 11.0 * s) + math.sin((x + y) / 9.7 * s)
         + 0.5 * math.sin(x / 2.3 * s + 1.7) * math.sin(y / 2.9 * s + 0.3))
    return cl(0.5 + n / 7.0)

def raster(fn, t, path):
    buf = bytearray()
    for y in range(FH):
        for x in range(FW):
            c = fn(x + 0.5, y + 0.5, t)
            buf += bytes((int(cl(c[0], 0, 255)), int(cl(c[1], 0, 255)), int(cl(c[2], 0, 255))))
    open(path + ".rgb", "wb").write(buf)
    run(["-size", f"{FW}x{FH}", "-depth", "8", f"rgb:{path}.rgb", path])

def stars(t, n, seed, color=(255, 255, 255), size=1.0, skip=None):
    d = []
    for i in range(n):
        x, y = rnd(seed + i) * FW, rnd(seed + i + 500) * FH
        if skip and skip(x, y): continue
        a = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(TAU * (2 * t + rnd(seed + i + 900))))
        r = (0.6 + 1.0 * rnd(seed + i + 1300)) * size
        d += ["-fill", rgba(color, a), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
    return d

def frame_rect(color, a=1.0, inset=5, width=2.0):
    return ["-fill", "none", "-stroke", rgba(color, a), "-strokewidth", f"{width}", "-draw",
            f"rectangle {inset},{inset} {FW - inset},{FH - inset}", "-stroke", "none"]

def border_comets(t, colors, count=2, length=50, width=2.5):
    per = 2 * (FW + FH)
    d = []
    for c in range(count):
        for i in range(length):
            pos = ((t + c / count) * per - i * 1.3) % per
            if pos < FW: x, y = pos, 1.5
            elif pos < FW + FH: x, y = FW - 1.5, pos - FW
            elif pos < 2 * FW + FH: x, y = FW - (pos - FW - FH), FH - 1.5
            else: x, y = 1.5, FH - (pos - 2 * FW - FH)
            k = 1 - i / length
            r = width * (0.4 + 0.6 * k)
            d += ["-fill", rgba(lerp(colors[0], colors[1], i / length), k), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
    return d

def ctext(s, y, size, color, font=SERIF, a=1.0):
    """Waagerecht zentrierter Text (jeder Buchstabe einzeln, damit schmale wie I mittig stehen)."""
    return ["-font", font, "-pointsize", f"{size}", "-fill", rgba(color, a), "-gravity", "North",
            "-annotate", f"+0+{y:.0f}", s, "-gravity", "NorthWest"]

def bolt(seed, x0, y0, x1, y1, steps=9, jitter=14):
    """Zackiger Blitz von (x0,y0) nach (x1,y1) mit Seitenast."""
    pts = []
    for i in range(steps + 1):
        k = i / steps
        j = 0 if i in (0, steps) else (rnd(seed + i * 7) - 0.5) * 2 * jitter
        pts.append((x0 + (x1 - x0) * k + j, y0 + (y1 - y0) * k + (rnd(seed + i * 11) - 0.5) * 4))
    b = int(2 + rnd(seed + 99) * (steps - 4))
    bx, by = pts[b]
    branch = [(bx, by), (bx + (rnd(seed + 5) - 0.5) * 50, by + 18), (bx + (rnd(seed + 6) - 0.5) * 70, by + 36)]
    return pts, branch

def text(s, x, y, size, color, font=SERIF, a=1.0):
    return ["-font", font, "-pointsize", f"{size}", "-fill", rgba(color, a), "-gravity", "NorthWest",
            "-annotate", f"+{x:.0f}+{y:.0f}", s, "-gravity", "NorthWest"]

class Design:
    glow_blur = 4
    def draws(self, t): return [], []
    def edge(self, t): return (60, 60, 70), (20, 20, 26)

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
    a, b = design.edge(t)
    # Schild-Textur: Vorderseite, dunkle Rückseite, Ränder, Griff
    run(["-size", f"{W}x{H}", "xc:none",
         front, "-geometry", f"+{K}+{K}", "-composite",
         "(", front, "-modulate", "45,75", ")", "-geometry", f"+{14 * K}+{K}", "-composite",
         "(", "-size", f"{K}x{FH}", f"gradient:{rgba(a)}-{rgba(b)}", ")", "-geometry", f"+0+{K}", "-composite",
         "(", "-size", f"{K}x{FH}", f"gradient:{rgba(a)}-{rgba(b)}", ")", "-geometry", f"+{13 * K}+{K}", "-composite",
         "(", "-size", f"{FW}x{K}", f"xc:{rgba(a)}", ")", "-geometry", f"+{K}+0", "-composite",
         "(", "-size", f"{FW}x{K}", f"xc:{rgba(b)}", ")", "-geometry", f"+{13 * K}+0", "-composite",
         "(", "-size", f"{16 * K}x{12 * K}", f"gradient:{rgba((70, 62, 58))}-{rgba((34, 30, 28))}", ")", "-geometry", f"+{26 * K}+0", "-composite",
         "-depth", "8", f"PNG32:{OUT}/{design.name}_{f}.png"])

# 1 ---------------------------------------------------------------- Void (Stil der Vorlage)
class Void(Design):
    name = "void"
    def bg(self, x, y, t):
        n = noise(x, y, 0.9)
        c = lerp(hx('#0d0d10'), hx('#2a2a30'), n ** 1.6)
        c = add(c, (255, 255, 255), 0.05 * rnd(int(x) * 131 + int(y) * 17))
        # Schein hinter der Schrift
        flick = 0.85 + 0.15 * math.sin(TAU * t * 3)
        return add(c, hx('#7a2cff'), 0.22 * flick * math.exp(-((x - FW / 2) / 34) ** 2))
    def draws(self, t):
        flick = 0.75 + 0.25 * math.sin(TAU * t) + (-.45 if round(t * N) in (5, 6, 15) else 0)
        flick = cl(flick)
        d = frame_rect((120, 120, 132), 0.9, 6, 2) + frame_rect((60, 60, 70), 0.9, 10, 1)
        g = []
        for i, ch in enumerate("VOID"):
            y = 30 + i * 54
            g += ctext(ch, y, 52, hx('#c040ff'), a=flick)
            d += ctext(ch, y, 52, lerp(hx('#f6d8ff'), hx('#ffffff'), flick), a=0.55 + 0.45 * flick)
        # wandernder Lichtstreif über die Buchstaben
        sy = (t * 1.3 % 1.3) * (FH + 40) - 20
        g += ["-fill", rgba(hx('#e090ff'), 0.6), "-draw", f"rectangle 30,{sy:.1f} {FW - 30},{sy + 6:.1f}"]
        return d, g
    def edge(self, t): return (78, 78, 88), (24, 24, 30)

# 1b --------------------------------------------------------------- Death (wie Void, mit roten Blitzen)
class Death(Design):
    name = "death"
    glow_blur = 4
    def bg(self, x, y, t):
        n = noise(x + 31, y + 17, 0.9)
        c = lerp(hx('#0c0909'), hx('#2c2626'), n ** 1.6)
        c = add(c, (255, 255, 255), 0.05 * rnd(int(x) * 97 + int(y) * 29))
        return add(c, hx('#b00010'), 0.22 * (0.8 + 0.2 * math.sin(TAU * t * 3)) * math.exp(-((x - FW / 2) / 34) ** 2))
    def draws(self, t):
        f = round(t * N) % N
        flick = cl(0.75 + 0.25 * math.sin(TAU * t) + (-.4 if f in (4, 13, 20) else 0))
        d = frame_rect((132, 116, 116), 0.9, 6, 2) + frame_rect((70, 56, 56), 0.9, 10, 1)
        g = []
        # rote Blitze zucken durch den Stein (neue Form alle zwei Bilder, manchmal zwei gleichzeitig)
        for k in range(2):
            on = rnd(f // 2 * 13 + k * 101) > (0.35 if k == 0 else 0.7)
            if not on:
                continue
            seed = f // 2 * 37 + k * 211
            x0 = 20 + rnd(seed) * (FW - 40)
            x1 = 20 + rnd(seed + 1) * (FW - 40)
            main, branch = bolt(seed, x0, 12, x1, FH - 12, 11, 16)
            for pts, w in ((main, 1.0), (branch, 0.6)):
                line = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
                g += ["-fill", "none", "-stroke", rgba(hx('#ff1020'), 0.95), "-strokewidth", f"{9 * w:.1f}", "-draw", f"polyline {line}", "-stroke", "none"]
                d += ["-fill", "none", "-stroke", rgba(hx('#ff8a8a'), 0.95), "-strokewidth", f"{2.2 * w:.1f}", "-draw", f"polyline {line}", "-stroke", "none"]
        for i, ch in enumerate("DEATH"):
            y = 18 + i * 47
            g += ["-stroke", rgba(hx('#ff1a2a'), flick), "-strokewidth", "5"] + ctext(ch, y, 44, hx('#ff1a2a'), a=flick) + ["-stroke", "none"]
            d += ctext(ch, y, 44, lerp(hx('#ffd6d6'), hx('#ffffff'), flick), a=0.6 + 0.4 * flick)
        sy = (t * 1.3 % 1.3) * (FH + 40) - 20
        g += ["-fill", rgba(hx('#ff6060'), 0.55), "-draw", f"rectangle 30,{sy:.1f} {FW - 30},{sy + 6:.1f}"]
        return d, g
    def edge(self, t): return (96, 72, 72), (30, 20, 20)

# 2 ---------------------------------------------------------------- Mytic Neon
class Neon(Design):
    name = "mytic"
    def bg(self, x, y, t):
        c = ramp([(0, hx('#140a2e')), (0.6, hx('#090418')), (1, hx('#050210'))], y / FH)
        r = math.hypot(x - FW / 2, y - FH * 0.45)
        return add(c, hx('#5a1aa0'), 0.35 * math.exp(-r / 60))
    def draws(self, t):
        d, g = [], []
        cx, cy = FW / 2, FH * 0.45
        pulse = 0.6 + 0.4 * math.sin(TAU * t)
        # Ring mit umlaufendem Licht
        for i in range(36):
            a = TAU * i / 36
            k = 0.5 + 0.5 * math.cos(a - TAU * t * 2)
            x, y = cx + 52 * math.cos(a), cy + 52 * math.sin(a)
            g += ["-fill", rgba(lerp(hx('#22e5ff'), hx('#ff2bd6'), i / 36), 0.3 + 0.7 * k), "-draw", f"circle {x:.1f},{y:.1f} {x + 4:.1f},{y:.1f}"]
            d += ["-fill", rgba((255, 255, 255), 0.25 + 0.75 * k ** 4), "-draw", f"circle {x:.1f},{y:.1f} {x + 1.4:.1f},{y:.1f}"]
        m = f"polyline {cx - 26},{cy + 26} {cx - 26},{cy - 26} {cx},{cy + 6} {cx + 26},{cy - 26} {cx + 26},{cy + 26}"
        g += ["-fill", "none", "-stroke", rgba(hx('#ff2bd6'), pulse), "-strokewidth", "12", "-draw", m, "-stroke", "none"]
        d += ["-fill", "none", "-stroke", rgba(hx('#ffd6f6'), 0.7 + 0.3 * pulse), "-strokewidth", "4", "-draw", m, "-stroke", "none"]
        d += text("MYTIC", FW / 2 - 46, FH - 70, 24, hx('#22e5ff'), SANS, 0.6 + 0.4 * pulse)
        g += text("MYTIC", FW / 2 - 46, FH - 70, 24, hx('#22e5ff'), SANS, pulse)
        g += border_comets(t, [hx('#22e5ff'), hx('#ff2bd6')], 2, 50, 3)
        return d, g
    def edge(self, t): return hx('#9b5cff'), hx('#2a0f55')

# 3 ---------------------------------------------------------------- Drachenschuppen
class Dragon(Design):
    name = "drache"
    def bg(self, x, y, t):
        # versetzte Schuppenreihen, jede Schuppe mit Licht oben und dunklem Rand unten
        row = int(y / 14); ox = 9 if row % 2 else 0
        sx = (x + ox) % 18 - 9; sy = y - row * 14
        d = math.hypot(sx, sy - 2) / 11
        base = ramp([(0, hx('#ff5a3a')), (0.6, hx('#9a1408')), (1, hx('#3a0602'))], d)
        base = lerp(base, hx('#1a0301'), ss(0.85, 1.0, d))
        wave = math.exp(-(((y - x * 0.5) / FH) - ((t * 1.4) % 1.4 - 0.2)) ** 2 * 60)
        return add(base, hx('#ffd27a'), 0.55 * wave * (1 - d))
    def draws(self, t):
        # Drachenauge in der Mitte, blinzelt
        cx, cy = FW / 2, FH * 0.5
        blink = cl(abs(math.sin(TAU * t * 1.0)) * 6 - 4.8)
        h = 22 * (1 - blink)
        d = ["-fill", hx_s('#ffcc33'), "-draw", f"ellipse {cx},{cy} 30,{max(1, h):.1f} 0,360",
             "-fill", hx_s('#ff8a00'), "-draw", f"ellipse {cx},{cy} 22,{max(1, h * 0.8):.1f} 0,360",
             "-fill", hx_s('#120000'), "-draw", f"ellipse {cx},{cy} 4,{max(1, h * 0.9):.1f} 0,360"]
        g = ["-fill", rgba(hx('#ffb000'), 0.9), "-draw", f"ellipse {cx},{cy} 36,{max(2, h + 6):.1f} 0,360"]
        return d, g
    def edge(self, t): return hx('#c9921f'), hx('#4a1a06')

def hx_s(h): return h

# 4 ---------------------------------------------------------------- Galaxie
class Galaxy(Design):
    name = "galaxie"
    def bg(self, x, y, t):
        n1 = math.sin(x / 15 + y / 23 + TAU * t) + math.sin(y / 11 - x / 31) + math.sin((x - y) / 19 - TAU * t)
        c = ramp([(0, hx('#04020c')), (1, hx('#0c0624'))], y / FH)
        c = add(c, hx('#c02bd0'), cl(n1 / 3) ** 2 * 0.45)
        dx, dy = x - FW / 2, (y - FH / 2) * 1.1
        r = math.hypot(dx, dy) + 1e-3
        th = math.atan2(dy, dx)
        arm = 0.5 + 0.5 * math.cos(2 * th - math.log(r) * 3.2 + TAU * t)
        c = add(c, (255, 225, 255), math.exp(-r / 10) * 1.1)
        return add(c, ramp([(0, hx('#ffd0ff')), (0.5, hx('#a46bff')), (1, hx('#3a6bff'))], r / 70), math.exp(-r / 40) * arm ** 3 * 1.1)
    def draws(self, t): return stars(t, 55, 81), []
    def edge(self, t): return hx('#b56bff'), hx('#1a1a6a')

# 5 ---------------------------------------------------------------- Lava
class Lava(Design):
    name = "lava"
    PTS = [(rnd(i) * FW, rnd(i + 40) * FH) for i in range(22)]
    def bg(self, x, y, t):
        d1 = d2 = 1e9
        for px, py in self.PTS:
            d = math.hypot(x - px, y - py)
            if d < d1: d1, d2 = d, d1
            elif d < d2: d2 = d
        crack = ss(5.0, 0.0, d2 - d1)
        flow = 0.6 + 0.4 * math.sin(y / 14 - TAU * t * 2 + x / 30)
        rock = lerp(hx('#0b0806'), hx('#2a1d16'), noise(x, y, 1.3))
        heat = ramp([(0, hx('#7a0d00')), (0.5, hx('#ff5a00')), (1, hx('#ffe28a'))], crack * flow)
        return lerp(rock, heat, crack)
    def draws(self, t):
        g = []
        for i in range(10):
            ph = (t + rnd(i + 300)) % 1
            x = rnd(i + 310) * FW; y = FH - ph * FH * 1.1
            g += ["-fill", rgba(hx('#ffb030'), 1 - ph), "-draw", f"circle {x:.1f},{y:.1f} {x + 2.4:.1f},{y:.1f}"]
        return [], g
    def edge(self, t): return hx('#ff7a1a'), hx('#2a0a00')

# 6 ---------------------------------------------------------------- Frost
class Frost(Design):
    name = "frost"
    def bg(self, x, y, t):
        c = ramp([(0, hx('#cfefff')), (0.4, hx('#5fb4ec')), (1, hx('#0c3a72'))], y / FH)
        return add(c, (255, 255, 255), 0.15 * noise(x, y, 2.0))
    def draws(self, t):
        cx, cy = FW / 2, FH * 0.45
        rot = TAU * t / 6
        d, g = [], []
        for k in range(6):
            a = rot + TAU * k / 6
            ex, ey = cx + 50 * math.cos(a), cy + 50 * math.sin(a)
            line = f"line {cx:.1f},{cy:.1f} {ex:.1f},{ey:.1f}"
            g += ["-stroke", rgba((200, 240, 255), 0.9), "-strokewidth", "7", "-draw", line]
            d += ["-stroke", "white", "-strokewidth", "3", "-draw", line]
            for s in (0.45, 0.72):
                bx, by = cx + 50 * s * math.cos(a), cy + 50 * s * math.sin(a)
                for side in (-1, 1):
                    b = a + side * 0.75
                    d += ["-strokewidth", "2", "-draw", f"line {bx:.1f},{by:.1f} {bx + 14 * math.cos(b):.1f},{by + 14 * math.sin(b):.1f}"]
        d += ["-stroke", "none", "-fill", "white", "-draw", f"circle {cx},{cy} {cx + 6},{cy}"]
        for i in range(10):
            x, y = rnd(i + 600) * FW, rnd(i + 610) * FH
            a = max(0, math.sin(TAU * (t * 2 + rnd(i + 620)))) ** 3
            if a > 0.05:
                r = 5 * a
                d += ["-stroke", rgba((255, 255, 255), a), "-strokewidth", "1", "-draw", f"line {x - r:.1f},{y:.1f} {x + r:.1f},{y:.1f}",
                      "-draw", f"line {x:.1f},{y - r:.1f} {x:.1f},{y + r:.1f}", "-stroke", "none"]
        return d, g
    def edge(self, t): return hx('#e8f8ff'), hx('#1a4a80')

# 7 ---------------------------------------------------------------- Gewitter
class Storm(Design):
    name = "gewitter"
    def flash(self, t): return max(0.0, 1 - abs((t % 0.5) - 0.12) * 14)
    def bg(self, x, y, t):
        n = noise(x + t * 40, y, 0.8)
        c = lerp(hx('#0a0d16'), hx('#3a4258'), n ** 2 * ss(1.0, 0.0, y / FH * 1.4))
        return add(c, (190, 200, 255), 0.45 * self.flash(t))
    def draws(self, t):
        f = self.flash(t)
        d, g = [], []
        if f > 0.05:
            pts = [(FW * 0.55, 0), (FW * 0.4, 70), (FW * 0.6, 90), (FW * 0.35, 170), (FW * 0.55, 185), (FW * 0.45, FH)]
            line = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
            g += ["-fill", "none", "-stroke", rgba((170, 190, 255), f), "-strokewidth", "10", "-draw", f"polyline {line}"]
            d += ["-fill", "none", "-stroke", rgba((250, 250, 255), f), "-strokewidth", "3", "-draw", f"polyline {line}", "-stroke", "none"]
        for i in range(40):
            ph = (t * 3 + rnd(i + 700)) % 1
            x = rnd(i + 710) * FW; y = ph * (FH + 20) - 10
            d += ["-stroke", rgba((170, 190, 230), 0.5), "-strokewidth", "1", "-draw", f"line {x:.1f},{y:.1f} {x - 3:.1f},{y + 9:.1f}", "-stroke", "none"]
        return d, g
    def edge(self, t): return lerp(hx('#5a6aa0'), (230, 235, 255), self.flash(t)), hx('#111626')

# 8 ---------------------------------------------------------------- Kristall
class Crystal(Design):
    name = "kristall"
    SHARDS = [[(72, 40), (96, 120), (72, 220), (48, 120)], [(36, 90), (54, 140), (36, 200), (20, 140)], [(108, 90), (124, 140), (108, 200), (90, 140)],
              [(72, 220), (84, 245), (72, 258), (60, 245)], [(40, 30), (50, 55), (40, 70), (30, 52)], [(104, 30), (114, 52), (104, 68), (94, 50)]]
    def bg(self, x, y, t):
        c = ramp([(0, hx('#1a0830')), (0.5, hx('#3a1466')), (1, hx('#12041f'))], y / FH)
        return add(c, hx('#b06bff'), 0.25 * math.exp(-((x - FW / 2) / 50) ** 2 - ((y - FH / 2) / 90) ** 2))
    def draws(self, t):
        d, g = [], []
        sweep = (t * 1.3 - 0.15) * (FH + 80) - 40
        for sh in self.SHARDS:
            cy = sum(p[1] for p in sh) / 4
            k = math.exp(-((cy - sweep) / 26) ** 2)
            pts = " ".join(f"{x},{y}" for x, y in sh)
            g += ["-fill", rgba(hx('#c58cff'), 0.4 + 0.6 * k), "-draw", f"polygon {pts}"]
            d += ["-fill", rgba(lerp(hx('#9b4dff'), (250, 235, 255), k * 0.8), 0.75), "-stroke", rgba((240, 220, 255), 0.9), "-strokewidth", "1.2",
                  "-draw", f"polygon {pts}", "-stroke", rgba((255, 255, 255), 0.3 + 0.6 * k),
                  "-draw", f"line {sh[0][0]},{sh[0][1]} {sh[2][0]},{sh[2][1]}", "-stroke", "none"]
        return d + stars(t, 12, 444, (255, 230, 255), 1.2), g
    def edge(self, t): return hx('#d0a0ff'), hx('#2a0a4a')

# 9 ---------------------------------------------------------------- Totenkopf
class Skull(Design):
    name = "totenkopf"
    def bg(self, x, y, t):
        n = math.sin(x / 9 + y / 11 + TAU * t * 2) + math.sin(x / 17 - y / 9 + TAU * t) + math.sin((x + y) / 23 - TAU * t)
        c = ramp([(0, hx('#161616')), (1, hx('#000000'))], y / FH)
        c = add(c, (170, 170, 170), cl(n / 3) ** 2 * ss(0.2, 1.0, y / FH) * 0.7)
        v = math.hypot((x - FW / 2) / (FW / 2), (y - FH / 2) / (FH / 2))
        return lerp(c, (0, 0, 0), ss(0.75, 1.25, v))
    def draws(self, t):
        cx, cy = FW / 2, FH * 0.42
        jaw = 4.0 * max(0.0, math.sin(TAU * t * 2)) ** 2
        beat = max(math.exp(-((t % 0.5) - 0.05) ** 2 / 0.002), 0.6 * math.exp(-((t % 0.5) - 0.17) ** 2 / 0.002))
        X = lambda v: cx - 60 + v
        Y = lambda v: cy - 80 + v
        d = []
        for ang in (35, -35):
            tr = f"translate {cx},{Y(132)} rotate {ang} "
            d += ["-fill", "#d9d9d9", "-draw", tr + "roundrectangle -48,-4.5 48,4.5 4,4", "-draw", tr + "circle -48,-5 -42,-5",
                  "-draw", tr + "circle -48,5 -42,5", "-draw", tr + "circle 48,-5 54,-5", "-draw", tr + "circle 48,5 54,5"]
        d += ["-fill", "#bdbdbd", "-draw", f"ellipse {X(60)},{Y(70)} 31,31 0,360", "-draw", f"roundrectangle {X(36)},{Y(78)} {X(84)},{Y(104)} 9,9",
              "-fill", "#f2f2f2", "-draw", f"ellipse {X(57)},{Y(66)} 27,27 0,360", "-draw", f"roundrectangle {X(39)},{Y(80)} {X(78)},{Y(100)} 8,8",
              "-fill", "#a8a8a8", "-draw", f"ellipse {X(47)},{Y(84)} 12.5,13 0,360", "-draw", f"ellipse {X(73)},{Y(84)} 12.5,13 0,360",
              "-fill", "#d9d9d9", "-draw", f"roundrectangle {X(44)},{Y(103) + jaw:.1f} {X(76)},{Y(116) + jaw:.1f} 6,6",
              "-fill", "#050505", "-draw", f"ellipse {X(47)},{Y(82)} 9,10 0,360", "-draw", f"ellipse {X(73)},{Y(82)} 9,10 0,360",
              "-draw", f"polygon {X(60)},{Y(90)} {X(54)},{Y(100)} {X(66)},{Y(100)}",
              "-fill", "none", "-stroke", "#202020", "-strokewidth", "1.3"]
        for i in range(7):
            tx = X(46 + i * 4.7)
            d += ["-draw", f"line {tx:.1f},{Y(97)} {tx:.1f},{Y(104)}", "-draw", f"line {tx:.1f},{Y(104) + jaw:.1f} {tx:.1f},{Y(110) + jaw:.1f}"]
        d += ["-stroke", "none"]
        g = []
        for ex in (47, 73):
            d += ["-fill", rgba((255, 255, 255), 0.25 + 0.75 * beat), "-draw", f"circle {X(ex)},{Y(84)} {X(ex) + 1.3 + beat * 1.4:.1f},{Y(84)}"]
            g += ["-fill", rgba((255, 255, 255), 0.25 + 0.75 * beat), "-draw", f"ellipse {X(ex)},{Y(82)} {6 + 4 * beat:.1f},{7 + 4 * beat:.1f} 0,360"]
        sy = (t * 1.25 % 1.25) * (FH + 30) - 15
        g += ["-fill", rgba((255, 255, 255), 0.9), "-draw", f"rectangle 0,{sy:.1f} {FW},{sy + 3:.1f}"]
        g += border_comets(t, [(255, 255, 255), (120, 120, 120)], 2, 50, 3)
        return d, g
    def edge(self, t): return (225, 225, 225), (25, 25, 25)

# 10 --------------------------------------------------------------- Code-Regen
class Code(Design):
    name = "code"
    def bg(self, x, y, t): return ramp([(0, hx('#001408')), (1, hx('#000502'))], y / FH)
    def draws(self, t):
        d, g = [], []
        cols = 9
        for c in range(cols):
            x = 6 + c * (FW - 12) / (cols - 1) - 6
            speed = 0.7 + rnd(c + 50) * 0.8
            head = ((t * speed + rnd(c)) % 1) * (FH + 60) - 20
            for k in range(14):
                y = head - k * 15
                if y < -12 or y > FH: continue
                ch = "01MYTICXZ"[int(rnd(c * 31 + k + int(t * N)) * 9)]
                col = (220, 255, 230) if k == 0 else lerp(hx('#3dff7a'), hx('#0a4a1a'), k / 14)
                d += text(ch, x, y, 13, col, MONO, 1 - k / 15)
                if k == 0: g += text(ch, x, y, 13, hx('#7affb0'), MONO)
        return d, g
    def edge(self, t): return hx('#3dff7a'), hx('#002a0e')

# 11 --------------------------------------------------------------- Synthwave
class Synth(Design):
    name = "synthwave"
    def bg(self, x, y, t):
        hor = FH * 0.55
        if y < hor:
            c = ramp([(0, hx('#12002a')), (0.7, hx('#5a0a6a')), (1, hx('#ff3d7f'))], y / hor)
            r = math.hypot(x - FW / 2, y - hor * 0.72)
            if r < 40:
                band = (y - (hor * 0.72 - 40)) / 80
                stripe = band > 0.5 and math.sin((y + t * 30) * 0.9) > 0.4 - band * 0.6
                if not stripe: c = ramp([(0, hx('#ffe14a')), (1, hx('#ff3d7f'))], band)
            return c
        depth = (y - hor) / (FH - hor)
        c = lerp(hx('#1a0030'), hx('#05000c'), depth)
        z = 1 / max(0.02, depth)
        gx = ((x - FW / 2) * depth * 0 + (x - FW / 2) / max(0.15, depth)) % 40
        gy = (z * 12 + t * 40) % 40
        line = ss(2.5, 0, min(gx, 40 - gx) * depth * 1.5) + ss(2.5, 0, min(gy, 40 - gy) / z * 6)
        return add(c, hx('#ff2bd6'), cl(line) * (0.4 + 0.6 * depth))
    def draws(self, t):
        return stars(t, 20, 555, size=0.9, skip=lambda x, y: y > FH * 0.5), border_comets(t, [hx('#22e5ff'), hx('#ff2bd6')], 2, 40, 2.5)
    def edge(self, t): return hx('#ff2bd6'), hx('#1a0030')

DESIGNS = [Void(), Death(), Neon(), Dragon(), Galaxy(), Lava(), Frost(), Storm(), Crystal(), Skull(), Code(), Synth()]
import newdesigns
DESIGNS += newdesigns.install(sys.modules[__name__])

NAMES = {"void": "Void", "death": "Death", "mytic": "Mytic Neon", "drache": "Drachenschuppen", "galaxie": "Galaxie", "lava": "Lava", "frost": "Frost",
         "gewitter": "Gewitter", "kristall": "Kristall", "totenkopf": "Totenkopf", "code": "Code-Regen", "synthwave": "Synthwave"}

if __name__ == "__main__":
    for dsg in DESIGNS:
        if ONLY and dsg.name not in ONLY: continue
        for f in range(N):
            frame(dsg, f)
        print(dsg.name, flush=True)
