# 5 Flügel-Designs für den Mytic Client. Aufruf: python3 wings.py AUSGABE
# Form: wing-mask.png (rechter Flügel, Ansatz links, Spitze rechts oben, darunter ein kleiner Flügel).
# Federn werden einzeln aus dem Umriss aufgebaut (featherfind.py): Schwungfedern zwischen den Mulden, darüber
# Deckfedern am Ansatz. Farben und Muster sind eigene Designs. Jede Textur enthält Vorder- und Rückseite.
import math, os, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import featherfind
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
TMP = os.path.join(OUT, "tmp"); os.makedirs(TMP, exist_ok=True)
MASK = os.path.join(HERE, "wing-mask.png")
W, H, PARTS = featherfind.analyse(MASK)

def run(args): subprocess.run(["convert", *args], check=True)
def rnd(i):
    v = math.sin(i * 12.9898 + 78.233) * 43758.5453
    return v - math.floor(v)
def hexrgb(h): return [int(h[i:i+2], 16) for i in (1, 3, 5)]
def mix(a, b, t):
    a, b = hexrgb(a), hexrgb(b); t = max(0, min(1, t))
    return "#" + "".join(f"{round(a[i] + (b[i]-a[i]) * t):02x}" for i in range(3))
def rgba(h, a=1.0):
    r, g, b = hexrgb(h); return f"rgba({r},{g},{b},{a:.3f})"

def poly(points):
    return "polygon " + " ".join(f"{x:.1f},{y:.1f}" for x, y in points)

def leading_side(part):
    """Funktion: > 0 für Punkte vor der Linie Ansatz → oberste Spitze (Vorderkante des großen Flügels)."""
    fx, fy = part["focus"]
    tx, ty = min(part["pts"], key=lambda p: p[1])
    ref = (tx - fx) * (0 - fy) - (ty - fy) * (0 - fx)   # Vorzeichen für die Ecke oben links
    sign = 1 if ref > 0 else -1
    return lambda x, y: sign * ((tx - fx) * (y - fy) - (ty - fy) * (x - fx)) if part["upper"] else -1

def feathers(part, pal, seed):
    """Schwungfedern: Umriss zwischen zwei Mulden, innen zum Ansatz hin zugespitzt."""
    pts, tips, valleys = part["pts"], part["tips"], part["valleys"]
    fx, fy = part["focus"]
    lead = leading_side(part)
    n = len(pts)
    args = []
    if not valleys: return args
    for k, ti in enumerate(tips):
        # Mulden links und rechts der Spitze auf dem Umriss
        before = max([v for v in valleys if v < ti] or [valleys[-1] - n])
        after = min([v for v in valleys if v > ti] or [valleys[0] + n])
        if after - before > n * 0.4: continue
        if lead(*pts[ti]) > 0: continue   # Dellen an der Vorderkante sind keine Federn
        outline = [pts[i % n] for i in range(before, after + 1)]
        tx, ty = pts[ti]
        d = math.hypot(tx - fx, ty - fy)
        length = min(d * 0.55, 130)
        bx, by = tx + (fx - tx) / d * length, ty + (fy - ty) / d * length
        shade = 0.25 + 0.5 * rnd(seed + k)
        body = mix(pal["dark"], pal["light"], shade)
        args += ["-fill", rgba(body), "-stroke", rgba(pal["line"], 0.9), "-strokewidth", "2.2", "-draw", poly(outline + [(bx, by)])]
        # heller Kiel von der Basis zur Spitze, Lichtkante an einer Seite
        args += ["-fill", "none", "-stroke", rgba(pal["shaft"], 0.75), "-strokewidth", "2", "-draw", f"line {bx:.1f},{by:.1f} {tx:.1f},{ty:.1f}"]
        lx, ly = outline[len(outline) // 4]
        args += ["-stroke", rgba(pal["light"], 0.5), "-strokewidth", "1.4", "-draw", f"line {bx:.1f},{by:.1f} {lx:.1f},{ly:.1f}", "-stroke", "none"]
    return args

def coverts(part, pal, seed, reach):
    """Deckfedern: kleine Schuppen am Ansatz, von außen nach innen gezeichnet (dachziegelartig)."""
    comp, fx, fy = part["comp"], part["focus"][0], part["focus"][1]
    tipdist = sum(math.hypot(part["pts"][i][0] - fx, part["pts"][i][1] - fy) for i in part["tips"]) / max(1, len(part["tips"]))
    scales = []
    lead = leading_side(part)
    step = 12
    for y in range(0, H, step):
        for x in range(0, W, step):
            jx, jy = x + (rnd(seed + x * 7 + y) - 0.5) * 6, y + (rnd(seed + x + y * 5) - 0.5) * 6
            if (int(jy) * W + int(jx)) not in comp: continue
            dist = math.hypot(jx - fx, jy - fy)
            if dist > tipdist * reach and lead(jx, jy) <= -8: continue
            scales.append((dist, jx, jy))
    args = []
    for i, (dist, x, y) in enumerate(sorted(scales, reverse=True)):
        a = math.degrees(math.atan2(y - fy, x - fx))
        f = min(1.0, dist / (tipdist * reach))     # 0 am Ansatz … 1 außen
        length = 8 + 22 * f ** 1.4                 # außen länglicher – Übergang zu den Schwungfedern
        width = 7.5 + 3 * f
        c = mix(pal["cdark"], pal["clight"], 0.25 + 0.65 * rnd(seed + i * 3) * (1 - 0.4 * f))
        args += ["-fill", rgba(c), "-stroke", rgba(pal["line"], 0.85), "-strokewidth", "1.6",
                 "-draw", f"translate {x:.1f},{y:.1f} rotate {a:.1f} ellipse {length*0.45:.1f},0 {length:.1f},{width:.1f} 0,360"]
        if f > 0.45:
            args += ["-stroke", rgba(pal["shaft"], 0.55), "-strokewidth", "1.2", "-draw", f"translate {x:.1f},{y:.1f} rotate {a:.1f} line 0,0 {length*1.3:.1f},0"]
    return args

def sparkles(color, n, seed, rmax=2.6):
    args = ["-stroke", "none", "-fill", rgba(color, 0.95)]
    for i in range(n):
        x, y = rnd(seed + i) * W, rnd(seed + i + 50) * H
        r = 0.8 + rnd(seed + i + 90) * rmax
        args += ["-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
        if r > 2.4:
            args += ["-stroke", rgba(color, 0.7), "-strokewidth", "1", "-draw", f"line {x-3*r:.1f},{y:.1f} {x+3*r:.1f},{y:.1f}",
                     "-draw", f"line {x:.1f},{y-3*r:.1f} {x:.1f},{y+3*r:.1f}", "-stroke", "none"]
    return args

def design(name, pal, seed):
    base = f"{TMP}/{name}_base.png"
    run(["-size", f"{W}x{H}", f"gradient:{pal['grad'][0]}-{pal['grad'][1]}", base])
    draw = []
    for part in sorted(PARTS, key=lambda p: p["upper"]):  # kleiner Flügel zuerst
        draw += feathers(part, pal, seed + (0 if part["upper"] else 500))
        draw += coverts(part, pal, seed + (100 if part["upper"] else 700), 0.80 if part["upper"] else 0.50)
    run([base, *draw, base])
    # feine Faserstruktur in Federrichtung
    run(["-size", f"{W}x{H}", "xc:gray", "+noise", "Random", "-colorspace", "gray", "-motion-blur", "0x14+-55", "-level", "35%,65%",
         "+level", "80%,100%", base, "+swap", "-compose", "multiply", "-composite", base])
    # Schattierung zum Rand und Glanz an der Oberkante
    run([MASK, "-blur", "0x16", "-level", "0%,55%", "+level", f"{int(pal['shade']*100)}%,100%", f"{TMP}/{name}_shade.png"])
    run([base, f"{TMP}/{name}_shade.png", "-compose", "multiply", "-composite", base])
    run([MASK, "-morphology", "EdgeIn", "Disk:2.5", "-fill", pal["edge"], "-opaque", "white", "-transparent", "black", f"PNG32:{TMP}/{name}_edge.png"])
    run([base, f"{TMP}/{name}_edge.png", "-compose", "over", "-composite", base])
    out = f"{TMP}/{name}_wing.png"
    run([base, MASK, "-alpha", "off", "-compose", "CopyOpacity", "-composite", f"PNG32:{out}"])
    if pal.get("glow"):
        run(["(", MASK, "-blur", "0x6", "-fill", pal["glow"], "-colorize", "100", ")", "(", MASK, "-blur", "0x6", ")", "-alpha", "off", "-compose", "CopyOpacity", "-composite",
             out, "-compose", "over", "-composite", f"PNG32:{out}"])
    run(["-size", f"{2*W}x{H}", "xc:none", out, "-geometry", "+0+0", "-composite", "(", out, "-flop", ")", "-geometry", f"+{W}+0", "-composite",
         "-depth", "8", f"PNG32:{OUT}/{name}.png"])

PALETTES = {
    "engel":   dict(grad=("#fffdf6", "#dfe6f2"), dark="#d9e0ec", light="#ffffff", shaft="#ffffff", line="#8e9ab4", cdark="#e3e8f2", clight="#ffffff",
                    edge="#ffcf5a", glow="#fff1c0", shade=0.72, shimmer="#fff4c8", sparkle="#fff6d8", sparkles=36),
    "inferno": dict(grad=("#ffcf3a", "#b3150c"), dark="#c3220d", light="#ffb23a", shaft="#fff1a8", line="#5a0904", cdark="#e2420f", clight="#ffd65a",
                    edge="#ffe08a", glow="#ff5a00", shade=0.55, sparkle="#fff1a8", sparkles=45, shimmer="#ffb040"),
    "eis":     dict(grad=("#e8fbff", "#4fa6e6"), dark="#6fb8ec", light="#e9fbff", shaft="#ffffff", line="#2c6ea8", cdark="#a6dcfa", clight="#f2fdff",
                    edge="#ffffff", glow="#7fe7ff", shade=0.66, sparkle="#ffffff", sparkles=55, shimmer="#bff4ff"),
    "neon":    dict(grad=("#2a0f55", "#07030f"), dark="#120726", light="#3a1670", shaft="#22e5ff", line="#ff2bd6", cdark="#1a0b38", clight="#4a2090",
                    edge="#ff2bd6", glow="#a259ff", shade=0.62, sparkle="#22e5ff", sparkles=40, shimmer="#22e5ff"),
    "smaragd": dict(grad=("#3ee6a0", "#06402c"), dark="#0a5a42", light="#3ed49a", shaft="#ffd36b", line="#032a1d", cdark="#138a64", clight="#7af0c2",
                    edge="#ffd36b", glow="#3ee6a0", shade=0.56, shimmer="#9affd8", sparkle="#ffe7a0", sparkles=30),
}
FX_FRAMES = 16

def effects(name, pal, seed):
    """Leucht-Ebene (wird im Spiel aufaddiert, schwarz = kein Effekt): wanderndes Lichtband, funkelnde Punkte,
    pulsierender Rand. 16 Bilder, fast nur Schwarz – dadurch sehr klein."""
    up = next(p for p in PARTS if p["upper"])
    rx, ry = up["focus"]
    tx, ty = min(up["pts"], key=lambda p: p[1])
    dx, dy = tx - rx, ty - ry
    length = math.hypot(dx, dy)
    nx, ny = -dy / length, dx / length          # senkrecht zur Richtung Ansatz → Spitze
    edge = f"{TMP}/{name}_fxedge.png"
    run([MASK, "-morphology", "EdgeIn", "Disk:3", "-blur", "0x2", edge])
    for f in range(FX_FRAMES):
        t = f / FX_FRAMES
        u = -0.25 + 1.5 * t
        cx, cy = rx + dx * u, ry + dy * u
        band = ["-stroke", pal["shimmer"], "-strokewidth", "38", "-draw",
                f"line {cx - nx * 500:.1f},{cy - ny * 500:.1f} {cx + nx * 500:.1f},{cy + ny * 500:.1f}"]
        frame = f"{TMP}/{name}_fx{f}.png"
        run(["-size", f"{W}x{H}", "xc:black", *band, "-blur", "0x14", "-evaluate", "multiply", str(pal.get("shimmerStrength", 0.75)), frame])
        # funkelnde Punkte (jeder mit eigener Phase)
        dots = ["-stroke", "none"]
        for i in range(pal.get("sparkles", 40)):
            x, y = rnd(seed + 900 + i) * W, rnd(seed + 950 + i) * H
            a = max(0.0, math.sin(2 * math.pi * (2 * t + rnd(seed + 990 + i)))) ** 3
            if a < 0.05: continue
            r = 1.2 + 2.2 * rnd(seed + 1990 + i)
            dots += ["-fill", rgba(pal["sparkle"], a), "-draw", f"circle {x:.1f},{y:.1f} {x + r:.1f},{y:.1f}"]
            if r > 2.4:
                dots += ["-stroke", rgba(pal["sparkle"], a * 0.8), "-strokewidth", "1", "-draw", f"line {x-4*r:.1f},{y:.1f} {x+4*r:.1f},{y:.1f}",
                         "-draw", f"line {x:.1f},{y-4*r:.1f} {x:.1f},{y+4*r:.1f}", "-stroke", "none"]
        pulse = 0.25 + 0.35 * (0.5 + 0.5 * math.sin(2 * math.pi * t))
        run([frame, *dots, "(", edge, "-fill", pal["edge"], "-tint", "100", "-evaluate", "multiply", f"{pulse:.2f}", ")", "-compose", "screen", "-composite",
             MASK, "-compose", "multiply", "-composite", frame])
        run(["-size", f"{2*W}x{H}", "xc:black", frame, "-geometry", "+0+0", "-composite", "(", frame, "-flop", ")", "-geometry", f"+{W}+0", "-composite",
             f"{TMP}/{name}_fxpair.png"])
        # Transparenz = Helligkeit (schwarz = unsichtbar), Farbe aufgehellt – wird im Spiel mit voller Helligkeit darübergelegt
        run([f"{TMP}/{name}_fxpair.png", "-normalize", "(", f"{TMP}/{name}_fxpair.png", "-colorspace", "gray", "-level", "0%,70%", ")",
             "-alpha", "off", "-compose", "CopyOpacity", "-composite", "-resize", "50%", "-depth", "8", f"PNG32:{OUT}/{name}_fx_{f}.png"])

for i, (name, pal) in enumerate(PALETTES.items()):
    design(name, pal, i * 1000 + 7)
    effects(name, pal, i * 1000 + 7)
print("fertig")
