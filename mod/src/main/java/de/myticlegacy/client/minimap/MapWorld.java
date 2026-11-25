package de.myticlegacy.client.minimap;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Wo die Karte gerade ist: Server (Adresse) bzw. Einzelspieler-Welt (Ordnername) und Dimension. Karten-Daten und
 * Wegpunkte liegen getrennt je Server/Welt unter .minecraft/mytic/minimap/.
 */
public final class MapWorld {
    private MapWorld() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("mytic").resolve("minimap");
    }

    private static String clean(String s) {
        String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (out.length() > 80) out = out.substring(0, 80);
        return out.isEmpty() ? "_" : out;
    }

    /** z. B. "mp_play.example.net" oder "sp_neue_welt"; null ohne Welt */
    public static String server(Minecraft mc) {
        if (mc.level == null) return null;
        try {
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                Path p = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                Path name = p.getFileName();
                return "sp_" + clean(name == null ? "welt" : name.toString());
            }
        } catch (RuntimeException ignored) {
            // Welt-Pfad nicht lesbar: unten als unbekannt behandeln
        }
        if (mc.getCurrentServer() != null) return "mp_" + clean(mc.getCurrentServer().ip);
        return "unbekannt";
    }

    /** Lesbarer Name der Dimension (minecraft:the_nether → "the_nether") */
    public static String dimension(ClientLevel level) {
        return level.dimension().identifier().toString();
    }

    public static String dimensionFolder(String dim) {
        return clean(dim.replace(':', '_'));
    }

    /** Kurzname für Anzeigen */
    public static String dimensionLabel(String dim) {
        switch (dim) {
            case "minecraft:overworld":
                return "Oberwelt";
            case "minecraft:the_nether":
                return "Nether";
            case "minecraft:the_end":
                return "End";
            default:
                int i = dim.indexOf(':');
                return i >= 0 ? dim.substring(i + 1) : dim;
        }
    }

    /** Server-Adresse (für die Sperrliste), leer im Einzelspieler */
    public static String address(Minecraft mc) {
        return mc.getCurrentServer() != null && !mc.hasSingleplayerServer() ? mc.getCurrentServer().ip.toLowerCase(Locale.ROOT) : "";
    }
}
