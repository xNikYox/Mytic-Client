"""Pixel-Flügel „Winterzauber II“ in doppelter Raster-Auflösung (96x80 je Flügel statt 48x40), gleiche Dateiformate wie
cosmetics/pixelwings.py – die Mod braucht dafür keine Änderung (UVs sind normiert, Datei bleibt 768x320 bzw. 384x160):

  <id>.png              Grundbild beider Seiten 192x80, ohne Glätten x4 = 768x320 (wie heute 96x40 x8)
  <id>_fx_<f>.png       16 Bilder Leucht-Ebene 192x80 x2 = 384x160 (Helligkeit = Deckkraft, wie heute 96x40 x4)
  <id>_icon.png         Menü-Bild 240x144 (Raster 1:1), dazu <id>_icon_fx_<f>.png
  <id>_feather.png      fallende Feder (wie heute)

Die Silhouette kommt unverändert aus pixelwings.classify() (fein abgetastet). Grundbild ist „cutout“ (Deckkraft nur 0 oder
1) – „durchscheinend“ wird über Farben/Dithering und die Leucht-Ebene gelöst.
Aufruf: xmaswings2.py <ausgabeordner> (build.py: build/wings)
"""
import json, math, os, random, sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pixelwings as pw

W, H = pw.W * 2, pw.H * 2  # 96 x 80
FRAMES = 16
hx = pw.hexrgb
mix = pw.mix
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def rnd(i):
    v = math.sin(i * 12.9898 + 78.233) * 43758.5453
    return v - math.floor(v)


def ramp_d(cols, t, x, y):
    """Ton t (0 = dunkel .. 1 = hell) auf eine Farbreihe, Zwischenstufen per 4x4-Bayer-Dithering."""
    t = max(0.0, min(0.9999, t)) * (len(cols) - 1)
    i = int(t)
    f = t - i
    if f > (BAYER[y % 4][x % 4] + 0.5) / 16:
        i += 1
    return cols[min(i, len(cols) - 1)]


# ------------------------------------------------------------------ Einordnung je Feinpixel
def analyse():
    """info[Y][X]: dict mit Art (edge/scale/feather) und Koordinaten, oder None."""
    info = [[None] * W for _ in range(H)]
    for Y in range(H):
        for X in range(W):
            x, y = (X + 0.5) / 2 - 0.5, (Y + 0.5) / 2 - 0.5
            c = pw.classify(x, y)
            if c is None:
                continue
            px, py = x + 0.5, y + 0.5
            u = pw.dot((px, py), pw.DIR)  # entlang der Federn (nach außen größer)
            v = pw.dot((px, py), pw.NRM)  # quer
            if c[0] == "edge":
                info[Y][X] = dict(kind="edge", t=c[1], side=c[2], u=u, v=v)
                continue
            _, row, fv, off = c
            k = (row + 0.5) / pw.ROWS
            bx = pw.TIP_TOP[0] + (pw.TIP_BOTTOM[0] - pw.TIP_TOP[0]) * k
            by = pw.TIP_TOP[1] + (pw.TIP_BOTTOM[1] - pw.TIP_TOP[1]) * k
            tip = (fv - 0.5) * 3.0 - pw.dot((px - bx, py - by), pw.DIR)  # Abstand zur Federspitze (>= 0)
            sub = 0 if fv < 0.5 else 1
            fs = fv * 2 - sub  # quer innerhalb der Einzelfeder 0..1
            kind = "scale" if off < 6.5 else "feather"
            info[Y][X] = dict(kind=kind, row=row, fv=fv, off=off, tip=tip, sub=sub, fs=fs, u=u, v=v,
                              fid=row * 2 + sub, zone=0 if off < 6.5 else 1 if off < 15 else 2)
    return info


def scale_cell(i, cell=1.6):
    """Schuppen (kleine Deckfedern): versetzte Halbkreise. Liefert (Abstand zum Rand 0..1, oben 0..1, Zellnummer)."""
    v = i["v"] / cell
    r = math.floor(v)
    u = i["u"] / cell + (0.5 if r % 2 else 0.0)
    c = math.floor(u)
    fu, fvv = u - c - 0.5, v - r  # fu -0.5..0.5, fvv 0 (oben) .. 1 (unten)
    d = math.hypot(fu * 1.25, fvv - 0.15)
    return min(1.0, d / 0.95), fvv, int(c * 31 + r * 7)


def feather_tone(i):
    """Grundschattierung einer Einzelfeder: oben hell, unten dunkler, Spitze etwas heller."""
    t = 0.8 - 0.55 * i["fs"]
    t += 0.12 * math.exp(-i["tip"] / 1.2)
    return t


def part(i):
    """Teil einer Feder quer zur Reihe: hl (Glanzkante oben), up (obere Fahne), shaft (Kiel), low (untere Fahne), out (Schatten unten)
    und ob hier eine Armschwinge endet (schräge Trennlinie zwischen zwei kurzen Federn)."""
    fv = i["fv"]
    if fv > 0.86: return "out"
    if fv < 0.1: return "hl"
    if 0.42 <= fv < 0.53: return "shaft"
    return "up" if fv < 0.42 else "low"


def split_line(i):
    """Armschwingen (Zone 1) bestehen aus zwei kurzen Federn je Reihe: Trennlinie bei off ~ 11 (schräg)."""
    return i["zone"] == 1 and abs(i["off"] - (10.8 + i["fv"] * 1.6)) < 0.3


def barb(i):
    """feine Fahnen-Äste: schräg vom Kiel nach außen, alle 3 Feinpixel"""
    q = i["u"] * 2 + abs(i["fv"] - 0.47) * 9
    return (int(math.floor(q)) % 3) == 0


# ------------------------------------------------------------------ Design 1: Engelsflügel Gold
GOLD = [hx(c) for c in ("#5a340a", "#7a4a10", "#b8801c", "#e8b23a", "#ffd96a", "#fff3b8")]
IVORY = [hx(c) for c in ("#8a7250", "#b39a70", "#d8c6a0", "#efe4cc", "#fbf5e6", "#ffffff")]
GOLD_EDGE = [hx(c) for c in ("#7a4a10", "#b8801c", "#e8b23a", "#ffd96a", "#fff3b8")]


def engel(i, X, Y):
    if i["kind"] == "edge":
        s = (i["side"] + 1) / 2
        t = 0.1 + 0.85 * s
        if (int(math.floor(i["u"] * 1.4 + i["v"] * 1.4)) % 3) == 0:
            t -= 0.22  # Kordel-Rillen
        return ramp_d(GOLD_EDGE, t, X, Y)
    if i["kind"] == "scale":
        d, top, cid = scale_cell(i, 1.5)
        if d > 0.84:
            return GOLD[1]
        t = 0.9 - 0.5 * top - 0.2 * d + 0.08 * (rnd(cid) - 0.5)
        if d < 0.3 and top < 0.35:
            t = 1.0
        return ramp_d(GOLD[1:], t, X, Y)
    p, tip = part(i), i["tip"]
    if split_line(i): return IVORY[1]
    if p == "out": return GOLD[1] if i["zone"] == 1 else IVORY[0]
    if p == "shaft": return GOLD[3] if tip > 1.0 else GOLD[4]
    if p == "hl": return IVORY[5] if i["zone"] == 2 else IVORY[4]
    t = 0.72 if p == "up" else 0.45
    t += 0.18 * math.exp(-tip / 1.3)
    if barb(i): t -= 0.1
    if tip < 1.3:
        return ramp_d(GOLD[2:], t + 0.15, X, Y)  # vergoldete Spitze
    if i["zone"] == 1:
        return ramp_d([IVORY[1], mix(IVORY[2], GOLD[3], 0.35), mix(IVORY[3], GOLD[4], 0.3), IVORY[4]], t, X, Y)
    return ramp_d(IVORY[1:], t, X, Y)


def engel_fx(info, f, out):
    p = f / FRAMES
    glint = hx("#fff3b8")
    centre = 44.0 - p * 68.0
    m = (30.0 / 34.99, -18.0 / 34.99)
    for Y in range(H):
        for X in range(W):
            i = info[Y][X]
            if not i:
                continue
            px, py = (X + 0.5) / 2, (Y + 0.5) / 2
            w = px * m[0] + py * m[1]
            a = 0.3 * math.exp(-((w - centre) / 3.0) ** 2) + 0.2 * math.exp(-((w - centre - 7) / 0.9) ** 2)
            if i["kind"] == "edge":
                a += 0.9 * math.exp(-((i["t"] - (1.15 - p * 1.3)) ** 2) / 0.006)
            elif i["kind"] == "scale":
                d, top, cid = scale_cell(i, 1.5)
                if d < 0.3 and top < 0.35:
                    a += 0.7 * max(0.0, math.sin(2 * math.pi * (p + rnd(cid)))) ** 6
            elif part(i) == "shaft":
                a += 0.12
            add(out, X, Y, glint, a)
    sparkles(info, out, p, 7, "engel", glint)


# ------------------------------------------------------------------ Design 2: Eiskristall
ICE = [hx(c) for c in ("#16335e", "#2a5a9a", "#5a96d6", "#9fd0f2", "#e2f2ff", "#ffffff")]
SNOW = [hx(c) for c in ("#a9bfdf", "#d6e4f7", "#f4f8ff", "#ffffff")]
DEEP = hx("#0c1f40")


def eis(i, X, Y):
    if i["kind"] == "edge":
        s = (i["side"] + 1) / 2
        z = abs(((i["u"] * 0.9) % 2) - 1)  # facettiert
        return ramp_d(SNOW, 0.15 + 0.6 * s + 0.25 * z, X, Y)
    if i["kind"] == "scale":
        a = i["u"] / 1.7 + i["v"] / 1.7
        b = i["u"] / 1.7 - i["v"] / 1.7
        fa, fb = a - math.floor(a), b - math.floor(b)
        cid = int(math.floor(a) * 13 + math.floor(b) * 7)
        if min(fa, 1 - fa, fb, 1 - fb) < 0.13:
            return SNOW[2]
        t = 0.5 + 0.3 * (fa - fb) + 0.2 * (rnd(cid) - 0.5)
        c = ramp_d(ICE[1:], t, X, Y)
        return mix(c, DEEP, 0.3) if (X + Y) % 2 == 0 and fa > 0.4 and fb > 0.4 else c
    p, tip = part(i), i["tip"]
    if split_line(i) or p == "hl" or tip < 0.5: return SNOW[3]
    if p == "out": return ICE[4] if (X // 2 + Y) % 2 else SNOW[1]  # helle Bruchkante
    if p == "shaft": return ICE[5] if (X + Y) % 3 else ICE[4]  # Frost-Ader
    # Seitenäste der Ader
    if barb(i) and abs(i["fv"] - 0.47) < 0.25 and (int(i["u"] * 2) % 6) < 2:
        return ICE[3]
    core = 0.5 - abs(i["fv"] - (0.26 if p == "up" else 0.7)) * 3  # Mitte der Fahne = dunkler Kern
    t = (0.75 if p == "up" else 0.5) - 0.35 * max(0.0, core) + 0.2 * math.exp(-tip / 1.6)
    if i["zone"] == 2: t -= 0.05
    c = ramp_d(ICE, t, X, Y)
    if (X + Y) % 2 == 0 and core > 0.15:
        c = mix(c, DEEP, 0.35)  # Durchblick-Dither
    return c


def eis_fx(info, f, out):
    p = f / FRAMES
    cold = hx("#bfe8ff")
    centre = 50.0 - p * 76.0
    m = (30.0 / 34.99, -18.0 / 34.99)
    for Y in range(H):
        for X in range(W):
            i = info[Y][X]
            if not i:
                continue
            px, py = (X + 0.5) / 2, (Y + 0.5) / 2
            w = px * m[0] + py * m[1]
            a = 0.32 * math.exp(-((w - centre) / 2.2) ** 2)
            if i["kind"] == "feather" and part(i) in ("shaft", "hl"):
                a += 0.14
            add(out, X, Y, cold, a)
    sparkles(info, out, p, 34, "eis", hx("#ffffff"), seq=True)


# ------------------------------------------------------------------ Design 3: Lametta
SILV = [hx(c) for c in ("#3c4250", "#6a7282", "#a9b3c4", "#dfe6f0", "#ffffff")]
GLD = [hx(c) for c in ("#7a4a10", "#b8801c", "#e8b23a", "#ffd96a", "#fff3b8")]
RED = [hx(c) for c in ("#4a0812", "#7a0f1e", "#b3172c", "#d92a3f", "#f2667a")]


def strand_of(i):
    return int(math.floor(i["v"] * 2.0))  # ein Faden je Feinpixel quer


def lametta(i, X, Y):
    if i["kind"] == "edge":
        s = (i["side"] + 1) / 2
        if s < 0.18: return GLD[3] if (X + Y) % 2 else GLD[2]  # Goldpaspel innen
        fold = 0.5 + 0.5 * math.sin(i["t"] * 38)  # rotes Samtband mit Falten
        return ramp_d(RED, 0.15 + 0.45 * s + 0.3 * fold, X, Y)
    if i["kind"] == "scale":
        if i["off"] < 1.0:
            return GLD[3] if (X + Y) % 2 else GLD[2]  # Goldborte
    s = strand_of(i)
    if i["kind"] == "feather" and i["tip"] < rnd(s * 3 + 1) * 2.4:
        return None  # zerzauste Enden
    cols = GLD if i.get("row") == 3 and i["kind"] == "feather" else SILV
    base = 0.62 if s % 2 == 0 else 0.3  # vordere Fäden hell, hintere dunkel
    base += 0.15 * (rnd(s * 11) - 0.5)
    seg = int(math.floor(i["u"] * 2 / 3 + rnd(s) * 3))  # Folienknicke alle ~3 Pixel
    k = rnd(s * 101 + seg)
    t = base + (0.35 if k > 0.8 else -0.15 if k < 0.2 else 0)
    if i["kind"] == "feather" and part(i) == "out": t -= 0.2
    return ramp_d(cols, t, X, Y)


def lametta_fx(info, f, out):
    p = f / FRAMES
    for Y in range(H):
        for X in range(W):
            i = info[Y][X]
            if not i or i["kind"] == "edge":
                continue
            if i["kind"] == "scale" and i["off"] < 1.0:
                continue
            s = strand_of(i)
            L = 16.0
            pos = ((p * 2 + rnd(s * 7)) % 1) * L
            d = ((-i["u"] - pos) % L)
            a = 0.9 * math.exp(-(d / 1.0) ** 2) * (0.45 + 0.55 * (s % 2 == 0))
            col = hx("#fff3b8") if i.get("row") == 3 else hx("#ffffff")
            add(out, X, Y, col, a)
    sparkles(info, out, p, 10, "lametta", hx("#ffffff"))


# ------------------------------------------------------------------ gemeinsame Helfer
def add(out, x, y, col, a):
    if 0 <= x < W and 0 <= y < H and a > 0.004:
        o = out[y][x]
        out[y][x] = tuple(min(255, int(o[k] + col[k] * min(1.0, a))) for k in range(3))


def sparkles(info, out, p, n, seed, col, seq=False):
    r = random.Random(seed)
    cells = [(X, Y) for Y in range(H) for X in range(W) if info[Y][X] and info[Y][X]["kind"] != "edge"]
    r.shuffle(cells)
    for k, (X, Y) in enumerate(cells[:n]):
        ph = k / n if seq else r.random()
        a = max(0.0, math.sin(2 * math.pi * (p + ph))) ** (14 if seq else 8)
        if a < 0.05:
            continue
        add(out, X, Y, (255, 255, 255), a)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            add(out, X + dx, Y + dy, col, a * 0.6)
        if a > 0.5:
            for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
                add(out, X + dx, Y + dy, col, a * 0.25)


DESIGNS = {
    "engelsgold": dict(name="Engelsflügel Gold", paint=engel, fx=engel_fx, feather=("#f7efdc", "#e8b23a", "#b8801c"),
                       desc="Elfenbeinfarbene Schwungfedern mit goldenem Kiel und vergoldeten Spitzen, fein geschuppte Deckfedern "
                            "an der Goldkordel; ein warmer Schimmer wandert über den Flügel, Schuppen glänzen einzeln auf."),
    "eiskristall": dict(name="Eiskristall", paint=eis, fx=eis_fx, feather=("#9fd0f2", "#ffffff", "#5a96d6"),
                        desc="Federn als facettierte Eisscherben: helle Kanten, dunkler geditherter Kern (wirkt durchscheinend), "
                             "Frost-Adern mit Seitenästen; Glitzer funkelt der Reihe nach, ein kalter Lichtstreif zieht durch."),
    "lametta": dict(name="Lametta", paint=lametta, fx=lametta_fx, feather=("#dfe6f0", "#ffffff", "#e8b23a"),
                    desc="Federn aus einzelnen Lametta-Fäden (Silber, eine Goldreihe) mit zerzausten Enden, rote Samtschleife mit "
                         "Goldpaspel an der Kante; Glanz läuft Faden für Faden nach außen."),
}


def write_pam(path, rows, icon=False):
    with open(path, "wb") as fh:
        fh.write(f"P7\nWIDTH {W * 2}\nHEIGHT {H}\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n".encode())
        data = bytearray()
        for row in rows:
            full = list(row) + list(reversed(row)) if icon else list(reversed(row)) + list(row)
            for c in full:
                data += bytes(c)
        fh.write(data)


def main():
    out = sys.argv[1]
    only = sys.argv[2:]
    tmp = os.path.join(out, "tmp-hd")
    os.makedirs(tmp, exist_ok=True)
    info = analyse()
    meta = []
    for wid, d in DESIGNS.items():
        meta.append(dict(id=wid, name=d["name"], desc=d["desc"], fxFrames=FRAMES, fxMs=90, w=24, h=20, ext=None,
                         note="Raster 96x80 je Flügel (2x), gleiche Dateien wie pixelwings.py"))
        if only and wid not in only:
            continue
        rows = []
        for Y in range(H):
            row = []
            for X in range(W):
                i = info[Y][X]
                c = d["paint"](i, X, Y) if i else None
                row.append(tuple(int(v) for v in c) + (255,) if c else (0, 0, 0, 0))
            rows.append(row)
        pam = os.path.join(tmp, f"{wid}.pam")
        write_pam(pam, rows)
        pw.convert(pam, os.path.join(out, f"{wid}.png"), 4)
        write_pam(pam, rows, icon=True)
        pw.convert(pam, os.path.join(out, f"{wid}_icon.png"), 1, "240x144")
        # Feder (8x8 wie heute, eigene Farben)
        m, s, b = (hx(c) for c in d["feather"])
        shape = ["..M.", ".MM.", ".MSM", "MMSM", "MBSM", "MSMM", ".S..", ".S.."]
        cols = {"M": m, "S": s, "B": b}
        fpam = os.path.join(tmp, f"{wid}_feather.pam")
        with open(fpam, "wb") as fh:
            fh.write(b"P7\nWIDTH 8\nHEIGHT 8\nDEPTH 4\nMAXVAL 255\nTUPLTYPE RGB_ALPHA\nENDHDR\n")
            for line in shape:
                half = [cols[ch] + (255,) if ch in cols else (0, 0, 0, 0) for ch in line]
                for c in half + list(reversed(half)):
                    fh.write(bytes(c))
        pw.convert(fpam, os.path.join(out, f"{wid}_feather.png"), 4)
        for f in range(FRAMES):
            fx = [[(0, 0, 0)] * W for _ in range(H)]
            d["fx"](info, f, fx)
            frows = [[c + (max(c),) for c in row] for row in fx]
            pam = os.path.join(tmp, f"{wid}_fx_{f}.pam")
            write_pam(pam, frows)
            pw.convert(pam, os.path.join(out, f"{wid}_fx_{f}.png"), 2)
            write_pam(pam, frows, icon=True)
            pw.convert(pam, os.path.join(out, f"{wid}_icon_fx_{f}.png"), 1, "240x144")
        print("HD-Flügel", wid, flush=True)
    json.dump(meta, open(os.path.join(out, "meta2.json"), "w"), ensure_ascii=False, indent=1)
    for fn in os.listdir(tmp):
        os.remove(os.path.join(tmp, fn))
    os.rmdir(tmp)


if __name__ == "__main__":
    main()
