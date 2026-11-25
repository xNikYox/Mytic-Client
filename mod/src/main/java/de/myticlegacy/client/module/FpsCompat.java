package de.myticlegacy.client.module;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import java.util.Map;

/** FPS-Boost, versionsabhängiger Teil (1.21.11 und 26.x): deckendes Laub, keine verbesserte Transparenz. */
final class FpsCompat {
    private FpsCompat() {
    }

    static void graphics(Options o, boolean fast) {
        FpsBoostModule.option("cutoutLeaves", o.cutoutLeaves(), fast, false);
        FpsBoostModule.option("improvedTransparency", o.improvedTransparency(), fast, false);
    }

    /** Grafik-Optionen der Leistungsprofile */
    static void perfGraphics(Options o, Map<String, OptionInstance<?>> out) {
        out.put("cutoutLeaves", o.cutoutLeaves());
        out.put("improvedTransparency", o.improvedTransparency());
    }

    /** Wert je Stufe (0 schnell, 1 ausgewogen, 2 schön); verbesserte Transparenz bleibt aus (verträgt sich nicht mit Sodium/Shadern) */
    static String perfGraphicsValue(String key, int level) {
        if (key.equals("cutoutLeaves")) return String.valueOf(level > 0);
        if (key.equals("improvedTransparency")) return "false";
        return null;
    }
}
