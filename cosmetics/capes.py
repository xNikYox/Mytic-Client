# 11 animierte Mytic-Umhänge (24 Bilder, 12-fache Auflösung). Aufruf: python3 capes.py AUSGABE [name ...]
import math, os, subprocess, sys
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
def ss(e0, e1, x): t = cl((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t)
def rnd(i): v = math.sin(i * 12.9898 + 78.233) * 43758.5453; return v - math.floor(v)
def rgba(c, a=1.0): return f"rgba({int(c[0])},{int(c[1])},{int(c[2])},{cl(a):.3f})"
def run(args): subprocess.run(["convert", *args], check=True)

def raster(fn, t, path):
    buf = bytearray()
    for y in range(FH):
        for x in range(FW):
            c = fn(x + 0.5, y + 0.5, t)
            buf += bytes((int(cl(c[0], 0, 255)), int(cl(c[1], 0, 255)), int(cl(c[2], 0, 255))))
    open(path + ".rgb", "wb").write(buf)
    run(["-size", f"{FW}x{FH}", "-depth", "8", f"rgb:{path}.rgb", path])

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
    run(["-size", f"{W}x{H}", "xc:none",
         front, "-geometry", f"+{K}+{K}", "-composite",
         "(", front, "-flop", "-modulate", "45,80", ")", "-geometry", f"+{2 * K + FW}+{K}", "-composite",
         side, "-geometry", f"+0+{K}", "-composite", side, "-geometry", f"+{K + FW}+{K}", "-composite",
         "(", "-size", f"{FW}x{K}", f"xc:{rgba(a)}", ")", "-geometry", f"+{K}+0", "-composite",
         "(", "-size", f"{FW}x{K}", f"xc:{rgba(b)}", ")", "-geometry", f"+{K + FW}+0", "-composite",
         "-depth", "8", f"PNG32:{OUT}/{design.name}_{f}.png"])

def stars(t, n, seed, color=(255, 255, 255), ymax=1.0, size=1.0, skip=None):
    d = []
    for i in range(n):
        x, y = rnd(seed + i) * FW, rnd(seed + i + 500) * FH * ymax
        if skip and skip(x, y): continue
        a = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(TAU * (2 * t + rnd(seed + i + 900))))
        r = (0.6 + 0.9 * rnd(seed + i + 1300)) * size
        d += ["-fill", rgba(color, a), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
    return d

def border_comets(t, colors, count=2, length=40, width=2.5):
    # Licht läuft um den Rand
    per = 2 * (FW + FH)
    d = ["-fill", "none", "-stroke-linecap", "round"] if False else []
    for c in range(count):
        for i in range(length):
            pos = ((t + c / count) * per - i * 1.2) % per
            if pos < FW: x, y = pos, 1.5
            elif pos < FW + FH: x, y = FW - 1.5, pos - FW
            elif pos < 2 * FW + FH: x, y = FW - (pos - FW - FH), FH - 1.5
            else: x, y = 1.5, FH - (pos - 2 * FW - FH)
            k = 1 - i / length
            col = lerp(colors[0], colors[1], i / length)
            r = width * (0.4 + 0.6 * k)
            d += ["-fill", rgba(col, k), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
    return d

class Design:
    glow_blur = 3
    def draws(self, t): return [], []
    def edge(self, t): return (180, 80, 255), (60, 20, 120)

# 1 ---------------------------------------------------------------- Mytic Mond
class Moon(Design):
    name = "mond"
    def bg(self, x, y, t):
        c = ramp([(0, hx('#3b1670')), (0.55, hx('#140828')), (1, hx('#070312'))], y / FH)
        band = math.exp(-((y / FH - ((t * 1.15) % 1.3 - 0.15)) * 5) ** 2)
        c = add(c, (60, 25, 120), 0.3 * band)
        # Mond (Sichel) mit weicher Kante und Schein
        d1 = math.hypot(x - 58, y - 74); d2 = math.hypot(x - 72, y - 62)
        cov = ss(31, 29.5, d1) * ss(26.5, 28, d2)
        pulse = 0.5 + 0.5 * math.sin(TAU * t)
        # Schein nur um die Sichel: im ausgesparten Teil sanft schwächer, ohne sichtbare Kante
        halo = math.exp(-max(0, d1 - 30) / (9 + 4 * pulse)) * (1 - cov) * (0.12 + 0.43 * ss(14, 34, d2))
        c = add(c, (150, 90, 255), halo * (0.6 + 0.4 * pulse))
        moon = lerp(hx('#e6dcff'), hx('#b9a3ff'), (y - 44) / 60)
        rim = ss(27.5, 29.5, d1) * cov
        moon = lerp(moon, hx('#7ff0ff'), rim)
        return lerp(c, moon, cov)
    def draws(self, t):
        glow = border_comets(t, [hx('#7ff0ff'), hx('#ff4fd8')], 2, 46, 3)
        sharp = stars(t, 30, 3, skip=lambda x, y: math.hypot(x - 58, y - 74) < 36 or 128 < y < 160) + border_comets(t, [(255, 255, 255), hx('#22e5ff')], 2, 18, 1.4)
        for i, (w, c) in enumerate(((44, '#ff2bd6'), (30, '#9b5cff'), (16, '#22e5ff'))):
            y = 134 + i * 9; x0 = 60 - w / 2
            shine = (t * 1.4 - i * 0.1) % 1.4
            sharp += ["-fill", c, "-draw", f"roundrectangle {x0},{y} {x0 + w},{y + 4} 2,2",
                      "-fill", rgba((255, 255, 255), 0.75), "-draw", f"rectangle {x0 + shine * w - 3:.1f},{y} {x0 + shine * w:.1f},{y + 4}"]
        glow += ["-fill", rgba(hx('#9b5cff'), 0.9), "-draw", "roundrectangle 34,132 86,156 4,4"]
        return sharp, glow
    def edge(self, t): return hx('#9b5cff'), hx('#2a0f55')

# 2 ---------------------------------------------------------------- Nordlicht
class Aurora(Design):
    name = "nordlicht"
    def bg(self, x, y, t):
        c = ramp([(0, hx('#02101f')), (0.7, hx('#06263a')), (1, hx('#0a3a4a'))], y / FH)
        tot = 0
        for k, (amp, wl, sp, off) in enumerate(((22, 34, 1, 0), (16, 23, -1, 1.7), (12, 47, 2, 3.1))):
            cx = 60 + amp * math.sin(y / wl + TAU * t * sp + off) + 8 * math.sin(y / 11 - TAU * t * sp)
            curtain = math.exp(-((x - cx) / (7 + 3 * k)) ** 2) * ss(170, 60, y) * ss(0, 30, y)
            tot += curtain * (0.9 - 0.2 * k)
            col = ramp([(0, hx('#7b5cff')), (0.35, hx('#29e0ff')), (0.75, hx('#3dff9a')), (1, hx('#3dff9a'))], y / 150)
            c = add(c, col, curtain * (0.9 - 0.2 * k))
        # Berge
        ridge = 160 + 10 * math.sin(x / 13) + 6 * math.sin(x / 5.3 + 1) + 4 * abs(math.sin(x / 7.7))
        if y > ridge: c = lerp(hx('#04121c'), hx('#0b2533'), (y - ridge) / 30)
        elif y > ridge - 1.5: c = lerp(c, hx('#d8f6ff'), 0.6)
        return c
    def draws(self, t): return stars(t, 30, 21, (220, 255, 255), 0.75), []
    def edge(self, t): return hx('#3dff9a'), hx('#1a4cff')

# 3 ---------------------------------------------------------------- Inferno
class Inferno(Design):
    name = "inferno"
    def bg(self, x, y, t):
        h = FH - y
        height = 95 + 26 * math.sin(x / 9 + TAU * t * 2) + 16 * math.sin(x / 4.1 - TAU * t * 3) + 10 * math.sin(x / 2.3 + TAU * t * 4)
        flick = 0.85 + 0.15 * math.sin(TAU * t * 5 + x / 7)
        v = cl((height - h) / height) * flick
        base = ramp([(0, hx('#120202')), (1, hx('#3a0806'))], y / FH)
        fire = ramp([(0, (0, 0, 0)), (0.25, hx('#7a0d05')), (0.5, hx('#e2360b')), (0.75, hx('#ff9a1c')), (1, hx('#fff1a8'))], v ** 0.8)
        return add(base, fire, 1.0)
    def draws(self, t):
        d, g = [], []
        for i in range(34):
            sp = 1 + int(rnd(i + 40) * 2)
            y = (FH + 10 - ((t * sp + rnd(i)) % 1) * (FH + 20))
            x = rnd(i + 80) * FW + 6 * math.sin(TAU * (t * 2 + rnd(i + 3)))
            r = 0.7 + 1.1 * rnd(i + 7)
            a = cl(y / FH * 1.4)
            d += ["-fill", rgba(hx('#ffd27a'), a), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
            g += ["-fill", rgba(hx('#ff6a00'), a), "-draw", f"circle {x:.1f},{y:.1f} {x + r * 2.5:.1f},{y:.1f}"]
        return d, g
    def edge(self, t): return hx('#ff7a1a'), hx('#5a0a04')

# 4 ---------------------------------------------------------------- Tiefsee
class Deep(Design):
    name = "tiefsee"
    def bg(self, x, y, t):
        c = ramp([(0, hx('#1d7fd6')), (0.35, hx('#0b3c7d')), (1, hx('#020a26'))], y / FH)
        ray = 0
        for k in range(4):
            u = (x + y * 0.35) / 22 + k * 1.7 + math.sin(TAU * t + k) * 0.6
            ray += max(0, math.sin(u)) ** 6 * (0.5 + 0.5 * math.sin(TAU * t + k * 2))
        c = add(c, (120, 200, 255), ray * 0.18 * ss(150, 0, y))
        caus = (math.sin(x / 5 + TAU * t * 2) + math.sin((x + y) / 6 - TAU * t) + math.sin(y / 4 + TAU * t)) / 3
        c = add(c, (160, 230, 255), max(0, caus) ** 3 * 0.35 * ss(40, 0, y))
        return c
    def draws(self, t):
        d, g = [], []
        for i in range(16):
            y = FH + 6 - ((t + rnd(i + 11)) % 1) * (FH + 12)
            x = rnd(i + 31) * (FW - 12) + 6 + 3 * math.sin(TAU * (t * 2 + rnd(i)))
            r = 1.2 + 2.2 * rnd(i + 51)
            d += ["-fill", rgba((180, 230, 255), 0.15), "-stroke", rgba((210, 245, 255), 0.7), "-strokewidth", "0.8",
                  "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}", "-stroke", "none",
                  "-fill", rgba((255, 255, 255), 0.8), "-draw", f"circle {x - r * 0.35:.1f},{y - r * 0.35:.1f} {x - r * 0.35 + r * 0.25:.1f},{y - r * 0.35:.1f}"]
        # Qualle: schwebt auf und ab, pulsiert
        jy = 96 + 10 * math.sin(TAU * t); jx = 60 + 4 * math.sin(TAU * t * 2)
        pulse = 1 + 0.08 * math.sin(TAU * t * 2)
        rw, rh = 20 * pulse, 15 / pulse
        g += ["-fill", rgba(hx('#ff5fd2'), 0.9), "-draw", f"ellipse {jx:.1f},{jy:.1f} {rw + 6:.1f},{rh + 6:.1f} 180,360"]
        d += ["-fill", rgba(hx('#ffb3ef'), 0.85), "-draw", f"ellipse {jx:.1f},{jy:.1f} {rw:.1f},{rh:.1f} 180,360",
              "-fill", rgba(hx('#ff6fd8'), 0.9), "-draw", f"ellipse {jx:.1f},{jy:.1f} {rw:.1f},{3:.1f} 0,180"]
        for k in range(6):
            x0 = jx - rw + 4 + k * (2 * rw - 8) / 5
            pts = " ".join(f"{x0 + 3 * math.sin(TAU * t * 2 + s / 6 + k):.1f},{jy + 2 + s * 2.4:.1f}" for s in range(16))
            d += ["-fill", "none", "-stroke", rgba(hx('#ffc2f2'), 0.55), "-strokewidth", "1.1", "-draw", f"polyline {pts}", "-stroke", "none"]
        return d, g
    def edge(self, t): return hx('#4fd3ff'), hx('#06204a')

# 5 ---------------------------------------------------------------- Code-Regen
class Code(Design):
    name = "coderegen"
    glow_blur = 2
    CW, CH = 8, 10
    def glyph(self, col, row, i):
        return [(gx, gy) for gx in range(3) for gy in range(5) if rnd(col * 131 + row * 17 + i * 7 + gx * 3 + gy * 11) > 0.5]
    def bg(self, x, y, t):
        return ramp([(0, hx('#000a03')), (1, hx('#001a08'))], y / FH)
    def draws(self, t):
        d, g = [], []
        cols = FW // self.CW; rows = FH // self.CH + 1
        for c in range(cols):
            speed = 1 + int(rnd(c + 5) * 2)
            head = ((t * speed + rnd(c + 9)) % 1) * (rows + 12)
            trail = 8 + int(rnd(c + 13) * 8)
            for r in range(rows):
                dist = head - r
                if dist < 0 or dist > trail: continue
                k = 1 - dist / trail
                i = int(t * N * 0.5 + rnd(c * 7 + r)) if rnd(c + r * 3) > 0.7 else 0
                col = (210, 255, 220) if dist < 1 else lerp((0, 90, 30), (40, 255, 110), k)
                for gx, gy in self.glyph(c, r, i):
                    x = c * self.CW + 1 + gx * 2; y = r * self.CH + gy * 2
                    d += ["-fill", rgba(col, 0.25 + 0.75 * k), "-draw", f"rectangle {x},{y} {x + 1.6:.1f},{y + 1.6:.1f}"]
                if dist < 1: g += ["-fill", rgba((80, 255, 140), 0.9), "-draw", f"rectangle {c * self.CW},{r * self.CH} {c * self.CW + 7},{r * self.CH + 9}"]
        return d, g
    def edge(self, t): return hx('#2bff7a'), hx('#003a12')

# 6 ---------------------------------------------------------------- Synthwave
class Synth(Design):
    name = "synthwave"
    HZ = 118
    def bg(self, x, y, t):
        if y < self.HZ:
            c = ramp([(0, hx('#12052e')), (0.6, hx('#5a1170')), (1, hx('#ff4f8b'))], y / self.HZ)
            d = math.hypot(x - 60, y - 96)
            if d < 36:
                sun = ramp([(0, hx('#ffe46b')), (1, hx('#ff3d9a'))], (y - 60) / 56)
                stripe = True
                if y > 92:
                    period = 8; off = (y - 92 + t * period) % period
                    gap = 1 + (y - 92) / 9
                    stripe = off > gap
                if stripe: c = lerp(c, sun, ss(36, 34.5, d))
            c = add(c, (255, 80, 160), math.exp(-max(0, d - 36) / 10) * 0.35)
            return c
        depth = (y - self.HZ) / (FH - self.HZ)
        c = lerp(hx('#1a0630'), hx('#08020f'), depth)
        z = 1 / max(0.04, depth)
        line_h = abs(((z * 1.6 - t * 1.6 * 2) % 1.6) - 0.8) < 0.08 + 0.05 * depth
        xv = (x - 60) / max(0.04, depth)
        line_v = abs(((xv / 26) % 1) - 0.5) > 0.47
        if line_h or line_v: c = lerp(c, hx('#ff3df0'), 0.55 + 0.45 * depth)
        c = add(c, (255, 60, 220), math.exp(-(y - self.HZ) / 4) * 0.8)
        return c
    def draws(self, t): return stars(t, 18, 61, (255, 220, 255), 0.4), []
    def edge(self, t): return hx('#ff3df0'), hx('#2a0a5a')

# 7 ---------------------------------------------------------------- Gewitter
class Storm(Design):
    name = "gewitter"
    def flash(self, t):
        return max(0, 1 - abs(t - 0.42) * 14) + 0.6 * max(0, 1 - abs(t - 0.52) * 18)
    def bg(self, x, y, t):
        n = (math.sin(x / 13 + TAU * t) + math.sin((x + y) / 17 - TAU * t) + math.sin(y / 9 + x / 21 + TAU * t * 2) * 0.6 + math.sin(x / 5.5 - y / 7 + TAU * t) * 0.3) / 2.9
        c = ramp([(0, hx('#0b0f1c')), (0.5, hx('#20283f')), (1, hx('#3c4766'))], 0.5 + 0.5 * n)
        c = lerp(c, hx('#070a12'), y / FH * 0.6)
        return add(c, (170, 190, 255), self.flash(t) * 0.55 * (1 - y / FH * 0.6))
    def draws(self, t):
        d, g = [], []
        for i in range(60):
            x = (rnd(i) * (FW + 40) - (t * 2 + rnd(i + 3)) % 1 * 40) % (FW + 20)
            y = ((t * 3 + rnd(i + 7)) % 1) * (FH + 20) - 10
            d += ["-stroke", rgba((170, 190, 230), 0.45), "-strokewidth", "0.9", "-draw", f"line {x:.1f},{y:.1f} {x - 3:.1f},{y + 9:.1f}"]
        fl = self.flash(t)
        if fl > 0.02:
            pts = [(70, 0)]
            for k in range(1, 12):
                pts.append((70 - k * 3.5 + 9 * (rnd(k + 300) - 0.5) * 2, k * 13))
            line = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
            g += ["-fill", "none", "-stroke", rgba((150, 180, 255), fl), "-strokewidth", "6", "-draw", f"polyline {line}"]
            d += ["-fill", "none", "-stroke", rgba((245, 248, 255), fl), "-strokewidth", "1.8", "-draw", f"polyline {line}",
                  "-strokewidth", "1", "-draw", f"polyline {pts[4][0]:.1f},{pts[4][1]:.1f} {pts[4][0] + 14:.1f},{pts[4][1] + 18:.1f} {pts[4][0] + 12:.1f},{pts[4][1] + 34:.1f}"]
        return d + ["-stroke", "none"], g
    def edge(self, t): return lerp(hx('#5a6aa0'), (230, 235, 255), self.flash(t)), hx('#111626')

# 8 ---------------------------------------------------------------- Kirschblüte
class Sakura(Design):
    name = "kirschbluete"
    def bg(self, x, y, t):
        c = ramp([(0, hx('#ffd6e7')), (0.6, hx('#f3a6c8')), (1, hx('#b77bc4'))], y / FH)
        for i in range(6):
            bx, by = rnd(i + 70) * FW, rnd(i + 90) * FH
            c = add(c, (255, 255, 255), 0.18 * math.exp(-(math.hypot(x - bx, y - by) / 12) ** 2) * (0.6 + 0.4 * math.sin(TAU * (t + rnd(i)))))
        return c
    def draws(self, t):
        d = []
        sway = 2 * math.sin(TAU * t)
        branch = [(0, 30), (25, 34 + sway * 0.3), (48, 46 + sway * 0.6), (70, 50 + sway), (96, 70 + sway)]
        pts = " ".join(f"{x:.1f},{y:.1f}" for x, y in branch)
        d += ["-fill", "none", "-stroke", hx_str('#3b1f24'), "-strokewidth", "4.5", "-draw", f"polyline {pts}",
              "-strokewidth", "2.2", "-draw", f"polyline 25,{34 + sway * 0.3:.1f} 30,{14 + sway * 0.3:.1f} 40,4",
              "-draw", f"polyline 48,{46 + sway * 0.6:.1f} 52,{70 + sway * 0.6:.1f} 46,90", "-stroke", "none"]
        for i in range(26):
            bx, by = branch[1 + int(rnd(i + 200) * 4)]
            x = bx + (rnd(i + 210) - 0.5) * 26; y = by + (rnd(i + 220) - 0.5) * 18
            r = 2.4 + 1.6 * rnd(i + 230)
            d += ["-fill", rgba(hx('#ffe9f2'), 0.95), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}",
                  "-fill", rgba(hx('#ff8fb8'), 0.9), "-draw", f"circle {x:.1f},{y:.1f} {x + r * 0.4:.1f},{y:.1f}"]
        for i in range(22):
            ph = (t + rnd(i + 400)) % 1
            x = (rnd(i + 410) * FW + ph * 40 + 6 * math.sin(TAU * (ph * 2 + rnd(i)))) % FW
            y = ph * (FH + 10) - 5
            ang = (ph * 720 + rnd(i) * 360) % 360
            rw = 2.6 * abs(math.cos(math.radians(ang))) + 0.6
            d += ["-fill", rgba(hx('#ffb3cf'), 0.95), "-draw", f"ellipse {x:.1f},{y:.1f} {rw:.1f},1.6 0,360"]
        return d, []
    def edge(self, t): return hx('#ff8fb8'), hx('#8a4a9a')
def hx_str(h): return h

# 9 ---------------------------------------------------------------- Eiskristall
class Ice(Design):
    name = "eiskristall"
    SHARDS = [[(60, 30), (78, 92), (60, 150), (42, 92)], [(30, 60), (44, 100), (30, 128), (18, 96)], [(92, 70), (104, 104), (92, 132), (80, 104)],
              [(60, 150), (70, 172), (60, 186), (50, 172)], [(40, 20), (48, 40), (40, 52), (32, 38)], [(84, 20), (92, 38), (84, 50), (76, 36)]]
    def bg(self, x, y, t):
        c = ramp([(0, hx('#0a1f3d')), (0.5, hx('#12406e')), (1, hx('#0a1a33'))], y / FH)
        return add(c, (120, 210, 255), 0.25 * math.exp(-((x - 60) / 40) ** 2 - ((y - 96) / 70) ** 2))
    def draws(self, t):
        d, g = [], []
        sweep = (t * 1.3 - 0.15) * (FH + 80) - 40
        for i, sh in enumerate(self.SHARDS):
            cy = sum(p[1] for p in sh) / 4
            k = math.exp(-((cy - sweep) / 22) ** 2)
            fill = lerp(hx('#5fc8ff'), (240, 252, 255), k * 0.8)
            pts = " ".join(f"{x},{y}" for x, y in sh)
            g += ["-fill", rgba((140, 220, 255), 0.5 + 0.5 * k), "-draw", f"polygon {pts}"]
            cx = sum(p[0] for p in sh) / 4
            d += ["-fill", rgba(fill, 0.55), "-stroke", rgba((230, 250, 255), 0.9), "-strokewidth", "1", "-draw", f"polygon {pts}",
                  "-stroke", rgba((255, 255, 255), 0.35 + 0.5 * k), "-draw", f"line {sh[0][0]},{sh[0][1]} {sh[2][0]},{sh[2][1]}",
                  "-draw", f"line {sh[1][0]},{sh[1][1]} {sh[3][0]},{sh[3][1]}", "-stroke", "none"]
        for i in range(8):
            x, y = rnd(i + 600) * FW, rnd(i + 610) * FH
            a = max(0, math.sin(TAU * (t * 2 + rnd(i + 620)))) ** 3
            if a > 0.05:
                r = 4 * a
                d += ["-stroke", rgba((255, 255, 255), a), "-strokewidth", "0.9", "-draw", f"line {x - r:.1f},{y:.1f} {x + r:.1f},{y:.1f}",
                      "-draw", f"line {x:.1f},{y - r:.1f} {x:.1f},{y + r:.1f}", "-stroke", "none"]
        for i in range(30):
            y = ((t + rnd(i + 700)) % 1) * (FH + 8) - 4
            x = rnd(i + 710) * FW + 4 * math.sin(TAU * (t * 2 + rnd(i)))
            r = 0.6 + 0.9 * rnd(i + 720)
            d += ["-fill", rgba((235, 248, 255), 0.85), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
        return d, g
    def edge(self, t): return hx('#a8e8ff'), hx('#1a4a80')

# 10 --------------------------------------------------------------- Galaxie
class Galaxy(Design):
    name = "galaxie"
    def bg(self, x, y, t):
        n1 = math.sin(x / 15 + y / 23 + TAU * t) + math.sin(y / 11 - x / 31) + math.sin((x - y) / 19 - TAU * t)
        n2 = math.sin(x / 9 - y / 27 - TAU * t) + math.sin(y / 17 + x / 13)
        c = ramp([(0, hx('#04020c')), (1, hx('#0c0624'))], y / FH)
        c = add(c, hx('#c02bd0'), cl(n1 / 3) ** 2 * 0.45)
        c = add(c, hx('#2b59ff'), cl(n2 / 2) ** 2 * 0.4)
        dx, dy = x - 60, (y - 92) * 1.25
        r = math.hypot(dx, dy) + 1e-3
        th = math.atan2(dy, dx)
        arm = 0.5 + 0.5 * math.cos(2 * th - math.log(r) * 3.2 + TAU * t)
        core = math.exp(-r / 9)
        disk = math.exp(-r / 34) * arm ** 3
        c = add(c, (255, 225, 255), core * 1.1)
        c = add(c, ramp([(0, hx('#ffd0ff')), (0.5, hx('#a46bff')), (1, hx('#3a6bff'))], r / 60), disk * 1.1)
        return c
    def draws(self, t): return stars(t, 40, 81, (255, 255, 255), 1.0, 0.9), []
    def edge(self, t): return hx('#b56bff'), hx('#1a1a6a')

# 11 --------------------------------------------------------------- Totenkopf
class Skull(Design):
    name = "totenkopf"
    glow_blur = 4
    GLITCH = {7: 5, 8: -4, 17: 6}

    def bg(self, x, y, t):
        c = ramp([(0, hx('#161616')), (0.5, hx('#070707')), (1, hx('#000000'))], y / FH)
        # Rauch steigt von unten auf
        rise = y / 11 + TAU * t * 2
        n = math.sin(x / 9 + rise) + math.sin(x / 17 - y / 9 + TAU * t) + math.sin((x + y) / 23 - TAU * t)
        smoke = cl(n / 3) ** 2 * ss(0.15, 1.0, y / FH)
        c = add(c, (170, 170, 170), smoke * 0.75)
        # Vignette
        v = math.hypot((x - 60) / 60, (y - 96) / 96)
        return lerp(c, (0, 0, 0), ss(0.75, 1.25, v))

    def draws(self, t):
        f = round(t * N)
        dx = self.GLITCH.get(f, 0)
        jaw = 4.0 * max(0.0, math.sin(TAU * t * 2)) ** 2
        beat = max(math.exp(-((t % 0.5) - 0.05) ** 2 / 0.002), 0.6 * math.exp(-((t % 0.5) - 0.17) ** 2 / 0.002))
        d, g = [], []
        white, bone, shade = '#f2f2f2', '#d9d9d9', '#9a9a9a'
        # gekreuzte Knochen hinter dem Schädel
        for ang in (35, -35):
            tr = f"translate {60 + dx},132 rotate {ang} "
            d += ["-fill", bone, "-draw", tr + "roundrectangle -46,-4.5 46,4.5 4,4",
                  "-draw", tr + "circle -46,-5 -40,-5", "-draw", tr + "circle -46,5 -40,5",
                  "-draw", tr + "circle 46,-5 52,-5", "-draw", tr + "circle 46,5 52,5"]
        x = lambda v: v + dx
        # Schädel: Kopf, Wangen, Unterkiefer
        d += ["-fill", '#bdbdbd', "-draw", f"ellipse {x(60)},70 31,31 0,360",
              "-draw", f"roundrectangle {x(36)},78 {x(84)},104 9,9",
              # Licht von links oben, Schatten an Schläfen und Wangen
              "-fill", white, "-draw", f"ellipse {x(57)},66 27,27 0,360",
              "-draw", f"roundrectangle {x(39)},80 {x(78)},100 8,8",
              "-fill", '#a8a8a8', "-draw", f"ellipse {x(47)},84 12.5,13 0,360", "-draw", f"ellipse {x(73)},84 12.5,13 0,360",
              "-fill", shade, "-draw", f"roundrectangle {x(44)},{104 + jaw:.1f} {x(76)},{118 + jaw:.1f} 6,6",
              "-fill", bone, "-draw", f"roundrectangle {x(44)},{103 + jaw:.1f} {x(76)},{116 + jaw:.1f} 6,6",
              # Augenhöhlen, Nase
              "-fill", '#050505', "-draw", f"ellipse {x(47)},82 9,10 0,360", "-draw", f"ellipse {x(73)},82 9,10 0,360",
              "-draw", f"polygon {x(60)},90 {x(54)},100 {x(66)},100",
              # Riss über dem rechten Auge
              "-fill", "none", "-stroke", '#202020', "-strokewidth", "1.4",
              "-draw", f"polyline {x(68)},42 {x(72)},52 {x(67)},58 {x(74)},66",
              # Zähne oben und unten
              "-strokewidth", "1.2"]
        for i in range(7):
            tx = x(46 + i * 4.7)
            d += ["-draw", f"line {tx:.1f},97 {tx:.1f},104", "-draw", f"line {tx:.1f},{104 + jaw:.1f} {tx:.1f},{110 + jaw:.1f}"]
        d += ["-draw", f"line {x(42)},104 {x(78)},104", "-stroke", "none"]
        # leuchtende Augen im Herzschlag
        for ex in (47, 73):
            d += ["-fill", rgba((255, 255, 255), 0.25 + 0.75 * beat), "-draw", f"circle {x(ex)},84 {x(ex) + 1.3 + beat * 1.4:.1f},84"]
            g += ["-fill", rgba((255, 255, 255), 0.25 + 0.75 * beat), "-draw", f"ellipse {x(ex)},82 {6 + 4 * beat:.1f},{7 + 4 * beat:.1f} 0,360"]
        # Scanlinie läuft von oben nach unten
        sy = (t * 1.25 % 1.25) * (FH + 30) - 15
        g += ["-fill", rgba((255, 255, 255), 0.9), "-draw", f"rectangle 0,{sy:.1f} {FW},{sy + 3:.1f}"]
        d += ["-fill", rgba((255, 255, 255), 0.55), "-draw", f"rectangle 0,{sy + 1:.1f} {FW},{sy + 1.8:.1f}"]
        # Glitch: versetzte Streifen
        if dx:
            for i in range(5):
                gy = rnd(f * 7 + i) * FH
                gh = 1 + rnd(f * 7 + i + 50) * 4
                gx = rnd(f * 7 + i + 80) * 40
                d += ["-fill", rgba((255, 255, 255), 0.5 + 0.4 * rnd(i + f)), "-draw", f"rectangle {gx:.1f},{gy:.1f} {gx + 30 + rnd(i) * 60:.1f},{gy + gh:.1f}"]
        # Licht läuft um den Rand
        g += border_comets(t, [(255, 255, 255), (120, 120, 120)], 2, 40, 3)
        d += border_comets(t, [(255, 255, 255), (170, 170, 170)], 2, 16, 1.3)
        return d, g

    def edge(self, t): return (225, 225, 225), (25, 25, 25)

DESIGNS = [Moon(), Aurora(), Inferno(), Deep(), Code(), Synth(), Storm(), Sakura(), Ice(), Galaxy(), Skull()]
for dsg in DESIGNS:
    if ONLY and dsg.name not in ONLY: continue
    for f in range(N):
        frame(dsg, f)
    print(dsg.name, flush=True)
