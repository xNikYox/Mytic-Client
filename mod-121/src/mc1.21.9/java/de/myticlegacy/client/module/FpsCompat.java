package de.myticlegacy.client.module;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import java.util.Map;

/** FPS-Boost, versionsabhängiger Teil (1.21–1.21.10): Grafik auf „Schnell“. */
final class FpsCompat {
    private FpsCompat() {
    }

    static void graphics(Options o, boolean fast) {
        FpsBoostModule.option("graphics", o.graphicsMode(), fast, "FAST");
    }

    /** Grafik-Optionen der Leistungsprofile */
    static void perfGraphics(Options o, Map<String, OptionInstance<?>> out) {
        out.put("graphics", o.graphicsMode());
    }

    /** Wert je Stufe (0 schnell, 1 ausgewogen, 2 schön); „Fabelhaft“ wird nicht gesetzt (verträgt sich nicht mit Sodium/Shadern) */
    static String perfGraphicsValue(String key, int level) {
        return key.equals("graphics") ? (level > 0 ? "FANCY" : "FAST") : null;
    }
}
