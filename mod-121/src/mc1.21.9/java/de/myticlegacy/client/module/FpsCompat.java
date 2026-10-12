package de.myticlegacy.client.module;

import net.minecraft.client.Options;

/** FPS-Boost, versionsabhängiger Teil (1.21–1.21.10): Grafik auf „Schnell“. */
final class FpsCompat {
    private FpsCompat() {
    }

    static void graphics(Options o, boolean fast) {
        FpsBoostModule.option("graphics", o.graphicsMode(), fast, "FAST");
    }
}
