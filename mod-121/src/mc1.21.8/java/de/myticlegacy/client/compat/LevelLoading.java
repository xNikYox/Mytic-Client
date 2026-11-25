package de.myticlegacy.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;

/**
 * Wird die Welt gerade betreten („Loading terrain“)? Dann soll nichts Zusätzliches auf dem Spiel-Thread laufen
 * (Minimap-Scan, Clip-Aufnahme). Diese Fassung gilt für 1.21 bis 1.21.8 (eigener Bildschirm beim Server-Beitritt).
 */
public final class LevelLoading {
    private LevelLoading() {
    }

    public static boolean active(Minecraft mc) {
        var screen = Compat.screen(mc);
        return mc.level == null || mc.player == null || screen instanceof ReceivingLevelScreen || screen instanceof LevelLoadingScreen;
    }
}
