package de.myticlegacy.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

/**
 * Wird die Welt gerade betreten („Joining world“/„Loading terrain“)? Dann soll nichts Zusätzliches auf dem
 * Spiel-Thread laufen (Minimap-Scan, Clip-Aufnahme), damit Minecraft die Chunks schnell fertig baut.
 * Diese Fassung gilt ab 1.21.9 (ein Ladebildschirm für alles); 1.21 bis 1.21.8 haben eine eigene LevelLoading.java.
 */
public final class LevelLoading {
    private LevelLoading() {
    }

    public static boolean active(Minecraft mc) {
        return mc.level == null || mc.player == null || Compat.screen(mc) instanceof LevelLoadingScreen;
    }
}
