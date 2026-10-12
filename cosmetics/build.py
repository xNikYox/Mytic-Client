#!/usr/bin/env python3
"""
Baut das Cosmetics-Paket des Mytic Clients: build/mytic-cosmetics-<version>.jar

Das Paket ist eine reine Fabric-"Mod" ohne Code: Fabric lädt die Texturen daraus für jede Minecraft-Version.
1.8.9 (Forge) liest die Bilder direkt aus der Datei. Der Launcher lädt das Paket einmal herunter und gibt den Pfad
mit -Dmytic.cosmetics=… an das Spiel weiter. Benötigt: Python 3, ImageMagick (convert) und einen Minecraft-Client ab 1.21.2 (für die Form der Elytra;
automatisch aus dem Gradle-Cache oder über MC_CLIENT_JAR).
"""
import glob, json, os, subprocess, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
VERSION = open(os.path.join(HERE, "VERSION")).read().strip()
BUILD = os.path.join(HERE, "build")
FRAMES = os.path.join(BUILD, "frames")

CAPES = [
    ("mond", "Mytic Mond"), ("nordlicht", "Nordlicht"), ("inferno", "Inferno"), ("tiefsee", "Tiefsee"),
    ("coderegen", "Code-Regen"), ("synthwave", "Synthwave"), ("gewitter", "Gewitter"),
    ("kirschbluete", "Kirschblüte"), ("eiskristall", "Eiskristall"), ("galaxie", "Galaxie"), ("totenkopf", "Totenkopf"),
    ("ozean", "Ozean"), ("netherportal", "Netherportal"), ("feuerwerk", "Feuerwerk"), ("regenbogen", "Regenbogen"), ("herzen", "Herzen"), ("gold", "Königsgold"),
]

WINGS = [("engel", "Engel"), ("inferno", "Inferno"), ("eis", "Eis"), ("neon", "Mytic Neon"), ("smaragd", "Smaragd")]
# Pixel-Flügel aus pixelwings.py (bringen ihre Menü-Bilder selbst mit)
PIXEL_WINGS = [("rabe", "Rabe"), ("taube", "Taube"), ("elster", "Elster"), ("phoenix", "Phönix"), ("saphir", "Saphir"),
               ("amethyst", "Amethyst"), ("kirsche", "Kirschblüte"), ("sonne", "Sonnengold"), ("polarlicht", "Polarlicht"),
               ("sternennacht", "Sternennacht")]

# Schilde aus shields.py (24 Bilder, gezeichnet 12-fach, ausgeliefert 6-fach = 384x384 im 64x64-Format des Minecraft-Schilds)
SHIELDS = [("void", "Void"), ("death", "Death"), ("mytic", "Mytic Neon"), ("drache", "Drachenschuppen"), ("galaxie", "Galaxie"),
           ("lava", "Lava"), ("frost", "Frost"), ("gewitter", "Gewitter"), ("kristall", "Kristall"), ("totenkopf", "Totenkopf"),
           ("code", "Code-Regen"), ("synthwave", "Synthwave"), ("ozean", "Ozean"), ("netherportal", "Netherportal"),
           ("feuerwerk", "Feuerwerk"), ("regenbogen", "Regenbogen"), ("herzen", "Herzen"), ("gold", "Königsgold")]
SHIELD_FRAMES = os.path.join(BUILD, "shields")


def main():
    os.makedirs(FRAMES, exist_ok=True)
    if "--skip-render" not in sys.argv:
        subprocess.run([sys.executable, os.path.join(HERE, "capes.py"), FRAMES], check=True)
        subprocess.run([sys.executable, os.path.join(HERE, "shields.py"), SHIELD_FRAMES], check=True)
    catalog = {"version": VERSION, "scale": 8, "capes": [{"id": i, "name": n, "frames": 24, "frameMs": 70} for i, n in CAPES],
               "wings": [{"id": i, "name": n, "fxFrames": 16, "fxMs": 90} for i, n in WINGS]
                        + [{"id": i, "name": n, "fxFrames": 16, "fxMs": 90, "w": 24, "h": 20, "feathers": True} for i, n in PIXEL_WINGS],
               "shields": [{"id": i, "name": n, "frames": 24, "frameMs": 70} for i, n in SHIELDS]}
    subprocess.run([sys.executable, os.path.join(HERE, "wings.py"), os.path.join(BUILD, "wings")], check=True)
    subprocess.run([sys.executable, os.path.join(HERE, "pixelwings.py"), os.path.join(BUILD, "wings")], check=True)
    # kleine, weich verkleinerte Bilder für die Vorschau im Cosmetics-Menü (große Texturen flimmern dort)
    wdir = os.path.join(BUILD, "wings")
    for wid, _ in WINGS:
        names = [(f"{wid}.png", f"{wid}_icon.png")] + [(f"{wid}_fx_{f}.png", f"{wid}_icon_fx_{f}.png") for f in range(16)]
        for src, dst in names:
            subprocess.run(["convert", os.path.join(wdir, src), "-filter", "Lanczos", "-resize", "240x144!", "-depth", "8",
                            f"PNG32:{os.path.join(wdir, dst)}"], check=True)
    mod = {"schemaVersion": 1, "id": "myticcosmetics", "version": VERSION, "name": "Mytic Cosmetics",
           "description": "Cosmetics des Mytic Clients (nur Texturen)", "environment": "client"}
    # Maske aus der Form der Original-Elytra (Bereich 22,0–46,22), auf 12-fache Größe ohne Glätten vergrößert,
    # dazu eine weiche Abdunklung zum Rand hin
    global MASK, MASK_SHADE
    MASK = os.path.join(BUILD, "elytra_mask.png")
    MASK_SHADE = os.path.join(BUILD, "elytra_shade.png")
    # Form der Elytra aus dem Minecraft-Client lesen (nicht im Repository, die Textur gehört Mojang)
    jars = [os.environ["MC_CLIENT_JAR"]] if os.environ.get("MC_CLIENT_JAR") else sorted(glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/1.21*/minecraft-client.jar")))
    if not jars:
        raise SystemExit("Minecraft-Client nicht gefunden – MC_CLIENT_JAR setzen (z. B. aus ~/.gradle/caches/fabric-loom/<version>/)")
    shape = os.path.join(BUILD, "elytra_vanilla.png")
    entry = "assets/minecraft/textures/entity/equipment/wings/elytra.png"
    for jar in reversed(jars):
        with zipfile.ZipFile(jar) as mc:
            if entry in mc.namelist():
                with open(shape, "wb") as out:
                    out.write(mc.read(entry))
                break
    else:
        raise SystemExit("Elytra-Textur in keinem Minecraft-Client gefunden (ab 1.21.2 nötig)")
    subprocess.run(["convert", shape, "-crop", "24x22+22+0", "+repage",
        "-alpha", "extract", "-filter", "point", "-resize", "1200%",
        "(", "-size", f"{24*12}x{22*12}", "xc:white", ")", "+swap", "-alpha", "off", "-compose", "CopyOpacity", "-composite", f"PNG32:{MASK}"], check=True)
    subprocess.run(["convert", MASK, "-alpha", "extract", "-blur", "0x10", "-level", "0%,70%",
        "+level", "62%,100%", "-alpha", "off", f"PNG24:{MASK_SHADE}"], check=True)

    # Elytra: Minecraft nimmt für die Flügel die Umhang-Textur, Bereich (22,0)–(46,22) der 64x32-Vorlage.
    # Dort das Motiv der Außenseite (auf 10x20 gestreckt) und die Randfarben eintragen.
    K = 12
    for cid, _ in CAPES:
        for f in range(24):
            src = os.path.join(FRAMES, f"{cid}_{f}.png")
            subprocess.run(["convert", src,
                "(", "+clone", "-crop", f"{10*K}x{16*K}+{K}+{K}", "+repage", "-resize", f"{10*K}x{20*K}!", ")", "-geometry", f"+{24*K}+{2*K}", "-composite",
                "(", "+clone", "-crop", f"{10*K}x{16*K}+{K}+{K}", "+repage", "-resize", f"{10*K}x{20*K}!", ")", "-geometry", f"+{36*K}+{2*K}", "-composite",
                "(", "+clone", "-crop", f"{K}x{16*K}+0+{K}", "+repage", "-resize", f"{2*K}x{20*K}!", ")", "-geometry", f"+{22*K}+{2*K}", "-composite",
                "(", "+clone", "-crop", f"{K}x{16*K}+0+{K}", "+repage", "-resize", f"{2*K}x{20*K}!", ")", "-geometry", f"+{34*K}+{2*K}", "-composite",
                "(", "+clone", "-crop", f"{10*K}x{K}+{K}+0", "+repage", "-resize", f"{10*K}x{2*K}!", ")", "-geometry", f"+{24*K}+0", "-composite",
                "(", "+clone", "-crop", f"{10*K}x{K}+{K}+0", "+repage", "-resize", f"{10*K}x{2*K}!", ")", "-geometry", f"+{34*K}+0", "-composite",
                "-depth", "8", f"PNG32:{src}"], check=True)
            # Form der Original-Elytra übernehmen: Design nur innerhalb der Flügel, Ränder leicht abgedunkelt
            region = os.path.join(BUILD, "elytra_region.png")
            subprocess.run(["convert", src, "-crop", f"{24*K}x{22*K}+{22*K}+0", "+repage",
                MASK_SHADE, "-compose", "Multiply", "-composite",
                MASK, "-compose", "DstIn", "-composite", f"PNG32:{region}"], check=True)
            subprocess.run(["convert", src, "-region", f"{24*K}x{22*K}+{22*K}+0", "-alpha", "transparent", "+region",
                region, "-geometry", f"+{22*K}+0", "-compose", "Over", "-composite", "-depth", "8", f"PNG32:{src}"], check=True)

    # Gezeichnet in 12-facher, ausgeliefert in 8-facher Auflösung (512x256): scharf, aber halb so viel Grafikspeicher
    small = os.path.join(BUILD, "small")
    os.makedirs(small, exist_ok=True)
    for cid, _ in CAPES:
        for f in range(24):
            name = f"{cid}_{f}.png"
            subprocess.run(["convert", os.path.join(FRAMES, name), "-filter", "Lanczos", "-resize", "512x256!", "-depth", "8", f"PNG32:{os.path.join(small, name)}"], check=True)
    shield_small = os.path.join(BUILD, "shields-small")
    os.makedirs(shield_small, exist_ok=True)
    for sid, _ in SHIELDS:
        for f in range(24):
            name = f"{sid}_{f}.png"
            subprocess.run(["convert", os.path.join(SHIELD_FRAMES, name), "-filter", "Lanczos", "-resize", "384x384!", "-depth", "8",
                            f"PNG32:{os.path.join(shield_small, name)}"], check=True)
    jar = os.path.join(BUILD, f"mytic-cosmetics-{VERSION}.jar")
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_STORED) as z:  # PNGs sind schon komprimiert
        z.writestr("fabric.mod.json", json.dumps(mod, indent=2))
        z.writestr("cosmetics.json", json.dumps(catalog, indent=2, ensure_ascii=False))
        for cid, _ in CAPES:
            for f in range(24):
                name = f"{cid}_{f}.png"
                z.write(os.path.join(small, name), f"assets/myticclient/textures/cape/{name}")
        for sid, _ in SHIELDS:
            for f in range(24):
                name = f"{sid}_{f}.png"
                z.write(os.path.join(shield_small, name), f"assets/myticclient/textures/shield/{name}")
        for wid, _ in PIXEL_WINGS:
            z.write(os.path.join(BUILD, "wings", f"{wid}_feather.png"), f"assets/myticclient/textures/wings/{wid}_feather.png")
        for wid, _ in WINGS + PIXEL_WINGS:
            z.write(os.path.join(BUILD, "wings", f"{wid}.png"), f"assets/myticclient/textures/wings/{wid}.png")
            z.write(os.path.join(BUILD, "wings", f"{wid}_icon.png"), f"assets/myticclient/textures/wings/{wid}_icon.png")
            for f in range(16):
                for name in (f"{wid}_fx_{f}.png", f"{wid}_icon_fx_{f}.png"):
                    z.write(os.path.join(BUILD, "wings", name), f"assets/myticclient/textures/wings/{name}")
    print(f"{jar} ({os.path.getsize(jar) // 1024} KB)")

main()
