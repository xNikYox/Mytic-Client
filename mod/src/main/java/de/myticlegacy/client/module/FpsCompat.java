package de.myticlegacy.client.module;

import net.minecraft.client.Options;

/** FPS-Boost, versionsabhängiger Teil (1.21.11 und 26.x): deckendes Laub, keine verbesserte Transparenz. */
final class FpsCompat {
    private FpsCompat() {
    }

    static void graphics(Options o, boolean fast) {
        FpsBoostModule.option("cutoutLeaves", o.cutoutLeaves(), fast, false);
        FpsBoostModule.option("improvedTransparency", o.improvedTransparency(), fast, false);
    }
}
