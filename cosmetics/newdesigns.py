# 6 neue Motive für Umhänge und Schilde (gemeinsamer Code; FW/FH und Hilfsfunktionen kommen aus capes.py bzw. shields.py).
# Aufruf über install(modul): legt die Klassen im jeweiligen Modul an.
import math


def install(m):
    TAU, hx, lerp, ramp, add, ss, rnd, rgba, cl = m.TAU, m.hx, m.lerp, m.ramp, m.add, m.ss, m.rnd, m.rgba, m.cl
    FW, FH, Design = m.FW, m.FH, m.Design
    sx, sy = FW / 120.0, FH / 192.0  # Maßstab gegenüber dem Umhang

    class Ocean(Design):
        name = "ozean"
        def bg(self, x, y, t):
            depth = y / FH
            c = ramp([(0, hx('#3fd0ff')), (0.35, hx('#0a6fb8')), (1, hx('#021a3a'))], depth)
            # Lichtstrahlen von oben
            ray = 0.5 + 0.5 * math.sin(x / (9 * sx) + y / (40 * sy) + TAU * t)
            c = add(c, (180, 240, 255), 0.18 * ray ** 6 * (1 - depth))
            # Kaustik
            k = math.sin(x / (6 * sx) + TAU * t * 2) * math.sin(y / (7 * sy) - TAU * t) + math.sin((x + y) / (9 * sx) + TAU * t)
            return add(c, (200, 250, 255), 0.12 * cl(k / 2) ** 3 * (1 - depth))
        def draws(self, t):
            d, g = [], []
            # Wellenkante oben
            pts = []
            for i in range(41):
                x = FW * i / 40
                pts.append(f"{x:.1f},{(10 + 4 * math.sin(TAU * (t * 2 + i / 10))) * sy:.1f}")
            d += ["-fill", rgba((230, 250, 255), 0.85), "-draw", "polygon 0,0 " + " ".join(pts) + f" {FW},0"]
            # Fisch-Schwarm zieht vorbei
            for i in range(7):
                ph = (t + i * 0.04) % 1
                x = -20 + ph * (FW + 40); y = (90 + 18 * math.sin(TAU * (ph + i * 0.15)) + (i % 3) * 8) * sy
                d += ["-fill", rgba(hx('#ffd23f'), 0.95), "-draw", f"ellipse {x:.1f},{y:.1f} {5 * sx:.1f},{2.6 * sx:.1f} 0,360",
                      "-draw", f"polygon {x - 5 * sx:.1f},{y:.1f} {x - 9 * sx:.1f},{y - 3 * sx:.1f} {x - 9 * sx:.1f},{y + 3 * sx:.1f}"]
            # Blasen steigen auf
            for i in range(18):
                ph = (t * 1.5 + rnd(i + 30)) % 1
                x = rnd(i + 40) * FW + 3 * math.sin(TAU * (ph * 2 + rnd(i)))
                y = FH * (1 - ph)
                r = (1 + 2.2 * rnd(i + 50)) * sx
                d += ["-fill", "none", "-stroke", rgba((220, 250, 255), 0.8), "-strokewidth", f"{0.8 * sx:.1f}",
                      "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}", "-stroke", "none"]
            # Tang am Boden wiegt sich
            for i in range(5):
                bx = (12 + i * 24) * sx
                pts = [f"{bx + 6 * sx * math.sin(TAU * t + k * 0.5 + i) * k / 6:.1f},{FH - k * 9 * sy:.1f}" for k in range(7)]
                d += ["-fill", "none", "-stroke", rgba(hx('#1fb36b'), 0.9), "-strokewidth", f"{3 * sx:.1f}", "-draw", "polyline " + " ".join(pts), "-stroke", "none"]
            return d, g
        def edge(self, t): return hx('#5fe0ff'), hx('#03264a')

    class Portal(Design):
        name = "netherportal"
        glow_blur = 5
        def bg(self, x, y, t):
            u, v = x / FW - 0.5, y / FH - 0.5
            a = math.atan2(v, u); r = math.hypot(u, v * FW / FH)
            swirl = math.sin(a * 3 + r * 22 - TAU * t * 2) + math.sin(x / (5 * sx) + y / (11 * sy) + TAU * t * 3)
            c = ramp([(0, hx('#1a0030')), (0.5, hx('#6a12c8')), (1, hx('#e070ff'))], 0.45 + swirl / 4)
            return add(c, (255, 200, 255), 0.6 * math.exp(-r * 9))
        def draws(self, t):
            d, g = [], []
            # Obsidian-Rahmen
            b = 9 * sx
            for rect in ((0, 0, FW, b), (0, FH - b, FW, FH), (0, 0, b, FH), (FW - b, 0, FW, FH)):
                d += ["-fill", hx_s('#120a1e'), "-draw", f"rectangle {rect[0]:.1f},{rect[1]:.1f} {rect[2]:.1f},{rect[3]:.1f}"]
            for i in range(14):
                x, y = rnd(i + 900) * FW, rnd(i + 910) * FH
                if b < x < FW - b and b < y < FH - b: continue
                d += ["-fill", rgba(hx('#5a2a9a'), 0.8), "-draw", f"rectangle {x:.1f},{y:.1f} {x + 3 * sx:.1f},{y + 2 * sx:.1f}"]
            # Partikel wirbeln heraus
            for i in range(24):
                ph = (t + rnd(i + 100)) % 1
                a = TAU * rnd(i + 110) + ph * 3
                rr = ph * FW * 0.6
                x, y = FW / 2 + math.cos(a) * rr, FH / 2 + math.sin(a) * rr * 1.4
                g += ["-fill", rgba(hx('#e0a0ff'), 1 - ph), "-draw", f"circle {x:.1f},{y:.1f} {x + 2.2 * sx:.1f},{y:.1f}"]
                d += ["-fill", rgba((255, 230, 255), (1 - ph) * 0.9), "-draw", f"rectangle {x:.1f},{y:.1f} {x + 1.6 * sx:.1f},{y + 1.6 * sx:.1f}"]
            return d, g
        def edge(self, t): return hx('#b45cff'), hx('#120a1e')

    class Fireworks(Design):
        name = "feuerwerk"
        glow_blur = 4
        COLS = ['#ff4f6d', '#ffd23f', '#3fd0ff', '#7dff6a', '#d66bff']
        def bg(self, x, y, t):
            c = ramp([(0, hx('#050a24')), (1, hx('#0c1640'))], y / FH)
            return add(c, (60, 40, 90), 0.25 * ss(0.6, 1.0, y / FH))
        def draws(self, t):
            d, g = m.stars(t, 25, 777, size=0.8 * sx) if 'stars' in dir(m) else [], []
            for k in range(3):
                ph = (t * 1.5 + k / 3) % 1
                cx = FW * (0.25 + 0.5 * rnd(k * 17 + int(t * 1.5 + k / 3) * 5))
                cy = FH * (0.2 + 0.35 * rnd(k * 19 + int(t * 1.5 + k / 3) * 7))
                col = hx(self.COLS[(k + int(t * 1.5 + k / 3)) % len(self.COLS)])
                if ph < 0.25:
                    # Rakete steigt
                    y = FH - (FH - cy) * ph / 0.25
                    g += ["-fill", rgba((255, 220, 160), 1), "-draw", f"circle {cx:.1f},{y:.1f} {cx + 2 * sx:.1f},{y:.1f}"]
                    d += ["-stroke", rgba((255, 200, 120), 0.6), "-strokewidth", f"{sx:.1f}", "-draw", f"line {cx:.1f},{y:.1f} {cx:.1f},{y + 14 * sy:.1f}", "-stroke", "none"]
                else:
                    e = (ph - 0.25) / 0.75
                    r = (8 + 34 * (1 - (1 - e) ** 2)) * sx
                    a = (1 - e) ** 1.2
                    for i in range(16):
                        ang = TAU * i / 16
                        x, y = cx + math.cos(ang) * r, cy + math.sin(ang) * r + e * e * 14 * sy
                        g += ["-fill", rgba(col, a), "-draw", f"circle {x:.1f},{y:.1f} {x + 2.6 * sx:.1f},{y:.1f}"]
                        d += ["-fill", rgba(lerp(col, (255, 255, 255), 0.5), a), "-draw", f"circle {x:.1f},{y:.1f} {x + 1.1 * sx:.1f},{y:.1f}"]
            # Stadtsilhouette unten
            pts = [f"0,{FH}"]
            for i in range(13):
                x0 = FW * i / 12; h = (12 + 26 * rnd(i + 600)) * sy
                pts += [f"{x0:.1f},{FH - h:.1f}", f"{x0 + FW / 12:.1f},{FH - h:.1f}"]
            pts.append(f"{FW},{FH}")
            d += ["-fill", hx_s('#02030a'), "-draw", "polygon " + " ".join(pts)]
            for i in range(14):
                x, y = rnd(i + 650) * FW, FH - rnd(i + 660) * 20 * sy
                if (int(t * 6) + i) % 4: d += ["-fill", rgba(hx('#ffd27a'), 0.9), "-draw", f"rectangle {x:.1f},{y:.1f} {x + 2 * sx:.1f},{y + 2 * sx:.1f}"]
            return d, g
        def edge(self, t): return hx('#ffd23f'), hx('#050a24')

    class Rainbow(Design):
        name = "regenbogen"
        def bg(self, x, y, t):
            hue = (y / FH * 0.9 + x / FW * 0.25 - t) % 1
            c = m_hsv(hue, 0.65, 1.0)
            shine = math.exp(-(((x - y * 0.6) / FW) - ((t * 1.3) % 1.6 - 0.5)) ** 2 * 50)
            return add(c, (255, 255, 255), 0.6 * shine)
        def draws(self, t):
            d = []
            for i in range(16):
                x, y = rnd(i + 70) * FW, rnd(i + 80) * FH
                a = max(0, math.sin(TAU * (t * 2 + rnd(i + 90)))) ** 3
                if a > 0.05:
                    r = 5 * a * sx
                    d += ["-stroke", rgba((255, 255, 255), a), "-strokewidth", f"{1.2 * sx:.1f}", "-draw", f"line {x - r:.1f},{y:.1f} {x + r:.1f},{y:.1f}",
                          "-draw", f"line {x:.1f},{y - r:.1f} {x:.1f},{y + r:.1f}", "-stroke", "none"]
            return d, []
        def edge(self, t): return m_hsv(t % 1, 0.6, 1.0), m_hsv((t + 0.5) % 1, 0.6, 0.5)

    class Hearts(Design):
        name = "herzen"
        glow_blur = 5
        def bg(self, x, y, t):
            return ramp([(0, hx('#ffb3cc')), (0.5, hx('#ff5c8a')), (1, hx('#a3124a'))], y / FH)
        def heart(self, cx, cy, s, col, a=1.0):
            pts = []
            for i in range(40):
                q = TAU * i / 40
                hxv = 16 * math.sin(q) ** 3
                hyv = -(13 * math.cos(q) - 5 * math.cos(2 * q) - 2 * math.cos(3 * q) - math.cos(4 * q))
                pts.append(f"{cx + hxv * s:.1f},{cy + hyv * s:.1f}")
            return ["-fill", rgba(col, a), "-draw", "polygon " + " ".join(pts)]
        def draws(self, t):
            d, g = [], []
            beat = 1 + 0.12 * max(math.exp(-((t % 0.5) - 0.05) ** 2 / 0.003), 0.7 * math.exp(-((t % 0.5) - 0.18) ** 2 / 0.003))
            for i in range(14):
                ph = (t + rnd(i + 200)) % 1
                x = rnd(i + 210) * FW + 6 * sx * math.sin(TAU * (ph * 2 + rnd(i)))
                y = FH * (1.05 - ph * 1.15)
                d += self.heart(x, y, (0.18 + 0.15 * rnd(i + 220)) * sx, (255, 235, 242), 0.55 * (1 - ph))
            cx, cy = FW / 2, FH * 0.45
            g += self.heart(cx, cy, 2.6 * beat * sx, (255, 60, 110), 0.9)
            d += self.heart(cx, cy, 2.2 * beat * sx, hx('#e8113f'))
            d += self.heart(cx - 9 * sx, cy - 10 * sx, 0.5 * beat * sx, (255, 200, 210), 0.8)
            return d, g
        def edge(self, t): return hx('#ff8fb0'), hx('#7a0a33')

    class Gold(Design):
        name = "gold"
        glow_blur = 3
        def bg(self, x, y, t):
            # gebürstetes Gold mit Rautenmuster
            n = math.sin(y * 1.7 + math.sin(x * 0.3) * 2) * 0.5 + 0.5
            c = ramp([(0, hx('#6a4100')), (0.5, hx('#d79a1e')), (1, hx('#ffe08a'))], 0.35 + 0.4 * (y / FH * -0.5 + 0.8) + 0.08 * n)
            dia = (abs((x / sx) % 20 - 10) + abs((y / sy) % 20 - 10))
            c = lerp(c, hx('#8a5a08'), 0.35 * ss(1.5, 0.0, abs(dia - 10)))
            shine = math.exp(-(((x + y * 0.5) / FW) - ((t * 1.25) % 1.8 - 0.3)) ** 2 * 40)
            return add(c, (255, 250, 220), 0.7 * shine)
        def draws(self, t):
            cx, cy = FW / 2, FH * 0.42
            d, g = [], []
            # Krone
            w, h = 40 * sx, 26 * sx
            pts = [(cx - w / 2, cy + h / 2), (cx - w / 2, cy - h / 2), (cx - w / 4, cy), (cx, cy - h / 2 - 4 * sx), (cx + w / 4, cy), (cx + w / 2, cy - h / 2), (cx + w / 2, cy + h / 2)]
            poly = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
            d += ["-fill", hx_s('#3a2400'), "-stroke", hx_s('#2a1a00'), "-strokewidth", f"{3 * sx:.1f}", "-draw", f"polygon {poly}",
                  "-fill", hx_s('#fff1b0'), "-stroke", "none", "-draw", f"polygon {poly}"]
            for k, (gx, col) in enumerate(((-w / 4, '#e8113f'), (0, '#3fd0ff'), (w / 4, '#2ee88a'))):
                tw = max(0, math.sin(TAU * (t + k / 3))) ** 6
                d += ["-fill", col, "-draw", f"circle {cx + gx:.1f},{cy + h / 4:.1f} {cx + gx + 3.5 * sx:.1f},{cy + h / 4:.1f}"]
                g += ["-fill", rgba((255, 255, 255), tw), "-draw", f"circle {cx + gx:.1f},{cy + h / 4:.1f} {cx + gx + 6 * sx:.1f},{cy + h / 4:.1f}"]
            for i in range(10):
                x, y = rnd(i + 330) * FW, rnd(i + 340) * FH
                a = max(0, math.sin(TAU * (t * 2 + rnd(i + 350)))) ** 4
                if a > 0.05:
                    r = 5 * a * sx
                    d += ["-stroke", rgba((255, 255, 240), a), "-strokewidth", f"{sx:.1f}", "-draw", f"line {x - r:.1f},{y:.1f} {x + r:.1f},{y:.1f}",
                          "-draw", f"line {x:.1f},{y - r:.1f} {x:.1f},{y + r:.1f}", "-stroke", "none"]
            return d, g
        def edge(self, t): return hx('#ffe08a'), hx('#6a4100')

    def m_hsv(h, s, v):
        i = int(h * 6) % 6; f = h * 6 - int(h * 6)
        p, q, w = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
        r, gg, b = [(v, w, p), (q, v, p), (p, v, w), (p, q, v), (w, p, v), (v, p, q)][i]
        return (r * 255, gg * 255, b * 255)

    def hx_s(h): return h

    return [Ocean(), Portal(), Fireworks(), Rainbow(), Hearts(), Gold()]


NAMES = {"ozean": "Ozean", "netherportal": "Netherportal", "feuerwerk": "Feuerwerk", "regenbogen": "Regenbogen", "herzen": "Herzen", "gold": "Königsgold"}
