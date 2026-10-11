# Findet in wing-mask.png die Flügelteile, ihren Umriss und darauf Federspitzen (Zacken) und Mulden.
import math, subprocess
from collections import deque

def load(path):
    data = subprocess.run(["convert", path, "-colorspace", "gray", "-compress", "none", "pgm:-"], capture_output=True, text=True).stdout.split()
    w, h, mx = int(data[1]), int(data[2]), int(data[3])
    return w, h, [int(v) > mx // 2 for v in data[4:]]

def components(w, h, px):
    seen = [False] * (w * h); comps = []
    for i in range(w * h):
        if px[i] and not seen[i]:
            q = deque([i]); seen[i] = True; pts = []
            while q:
                j = q.popleft(); pts.append(j); x, y = j % w, j // w
                for nx, ny in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
                    if 0 <= nx < w and 0 <= ny < h and px[ny*w+nx] and not seen[ny*w+nx]:
                        seen[ny*w+nx] = True; q.append(ny*w+nx)
            if len(pts) > 200: comps.append(set(pts))
    return sorted(comps, key=len, reverse=True)

def contour(w, h, comp):
    """Moore-Nachbarschaft (im Uhrzeigersinn): Umriss als geordnete Punktliste."""
    inside = lambda q: 0 <= q[0] < w and 0 <= q[1] < h and (q[1] * w + q[0]) in comp
    dirs = [(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,-1),(1,-1)]
    start = min(comp, key=lambda j: (j // w, j % w)); start = (start % w, start // w)
    p, back = start, (start[0] - 1, start[1])
    pts = [p]
    for _ in range(50000):
        idx = dirs.index((back[0] - p[0], back[1] - p[1]))
        nxt = None
        for k in range(1, 9):
            d = dirs[(idx + k) % 8]
            c = (p[0] + d[0], p[1] + d[1])
            if inside(c):
                prev = dirs[(idx + k - 1) % 8]
                back = (p[0] + prev[0], p[1] + prev[1])
                nxt = c
                break
        if nxt is None or (nxt == start and len(pts) > 2): break
        p = nxt
        pts.append(p)
    return pts

def features(pts, cx, cy, smooth=18, window=7, min_prot=2.0):
    n = len(pts); out_tips, out_valleys = [], []
    sm = []
    for i in range(n):
        xs = [pts[(i + k) % n][0] for k in range(-smooth, smooth + 1)]
        ys = [pts[(i + k) % n][1] for k in range(-smooth, smooth + 1)]
        sm.append((sum(xs) / len(xs), sum(ys) / len(ys)))
    prot = [math.hypot(p[0]-cx, p[1]-cy) - math.hypot(s[0]-cx, s[1]-cy) for p, s in zip(pts, sm)]
    for i in range(n):
        win = [prot[(i + k) % n] for k in range(-window, window + 1)]
        if prot[i] == max(win) and prot[i] > min_prot and (not out_tips or i - out_tips[-1] > window): out_tips.append(i)
        if prot[i] == min(win) and prot[i] < -min_prot and (not out_valleys or i - out_valleys[-1] > window): out_valleys.append(i)
    return out_tips, out_valleys

def analyse(path):
    w, h, px = load(path)
    parts = []
    for comp in components(w, h, px)[:2]:
        xs = [j % w for j in comp]; ys = [j // w for j in comp]
        box = (min(xs), min(ys), max(xs), max(ys))
        cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
        pts = contour(w, h, comp)
        tips, valleys = features(pts, cx, cy)
        upper = box[1] < h * 0.3
        focus = (box[0] + 0.05 * (box[2]-box[0]), box[3] - 0.08 * (box[3]-box[1])) if upper else (box[0] - 0.02 * (box[2]-box[0]), box[1] + 0.15 * (box[3]-box[1]))
        parts.append({"upper": upper, "comp": comp, "pts": pts, "tips": tips, "valleys": valleys, "box": box, "focus": focus, "centroid": (cx, cy)})
    return w, h, parts

if __name__ == "__main__":
    w, h, parts = analyse("wing-mask.png")
    args = []
    for p in parts:
        for i in p["tips"]:
            x, y = p["pts"][i]; args += ["-fill", "yellow", "-draw", f"circle {x},{y} {x+3},{y}"]
        for i in p["valleys"]:
            x, y = p["pts"][i]; args += ["-fill", "red", "-draw", f"circle {x},{y} {x+2},{y}"]
        print("oben" if p["upper"] else "unten", "Umriss", len(p["pts"]), "Spitzen", len(p["tips"]), "Mulden", len(p["valleys"]))
    subprocess.run(["convert", "wing-mask.png", "-fill", "#444", "-opaque", "white", *args, "/tmp/claude-1000/-workspace-tickguard/808ca650-c5bb-45de-a264-bfbbff44aadd/scratchpad/tips.png"], check=True)
