#!/usr/bin/env python3
"""
Baut das Cosmetics-Paket des Mytic Clients: build/mytic-cosmetics-<version>.jar

Das Paket ist eine reine Fabric-"Mod" ohne Code: Fabric lädt die Texturen daraus für jede Minecraft-Version.
1.8.9 (Forge) liest die Bilder direkt aus der Datei. Der Launcher lädt das Paket einmal herunter und gibt den Pfad
mit -Dmytic.cosmetics=… an das Spiel weiter. Benötigt: Python 3, ImageMagick (convert).
"""
import json, os, subprocess, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
VERSION = open(os.path.join(HERE, "VERSION")).read().strip()
BUILD = os.path.join(HERE, "build")
FRAMES = os.path.join(BUILD, "frames")

CAPES = [
    ("mond", "Mytic Mond"), ("nordlicht", "Nordlicht"), ("inferno", "Inferno"), ("tiefsee", "Tiefsee"),
    ("coderegen", "Code-Regen"), ("synthwave", "Synthwave"), ("gewitter", "Gewitter"),
    ("kirschbluete", "Kirschblüte"), ("eiskristall", "Eiskristall"), ("galaxie", "Galaxie"),
]

def main():
    os.makedirs(FRAMES, exist_ok=True)
    if "--skip-render" not in sys.argv:
        subprocess.run([sys.executable, os.path.join(HERE, "capes.py"), FRAMES], check=True)
    catalog = {"version": VERSION, "scale": 8, "capes": [{"id": i, "name": n, "frames": 24, "frameMs": 70} for i, n in CAPES]}
    mod = {"schemaVersion": 1, "id": "myticcosmetics", "version": VERSION, "name": "Mytic Cosmetics",
           "description": "Umhänge des Mytic Clients (nur Texturen)", "environment": "client"}
    # Gezeichnet in 12-facher, ausgeliefert in 8-facher Auflösung (512x256): scharf, aber halb so viel Grafikspeicher
    small = os.path.join(BUILD, "small")
    os.makedirs(small, exist_ok=True)
    for cid, _ in CAPES:
        for f in range(24):
            name = f"{cid}_{f}.png"
            subprocess.run(["convert", os.path.join(FRAMES, name), "-filter", "Lanczos", "-resize", "512x256!", "-depth", "8", f"PNG32:{os.path.join(small, name)}"], check=True)
    jar = os.path.join(BUILD, f"mytic-cosmetics-{VERSION}.jar")
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_STORED) as z:  # PNGs sind schon komprimiert
        z.writestr("fabric.mod.json", json.dumps(mod, indent=2))
        z.writestr("cosmetics.json", json.dumps(catalog, indent=2, ensure_ascii=False))
        for cid, _ in CAPES:
            for f in range(24):
                name = f"{cid}_{f}.png"
                z.write(os.path.join(small, name), f"assets/myticclient/textures/cape/{name}")
    print(f"{jar} ({os.path.getsize(jar) // 1024} KB)")

main()
