package de.myticlegacy.client.module;

import net.minecraft.client.renderer.texture.DynamicTexture;

/** PvP-Optik (1.21 – 1.21.1): Treffer-Farbe in die Overlay-Textur malen (NativeImage hier mit ABGR) */
public final class PvpCompat {
    public static DynamicTexture overlay;

    private PvpCompat() {
    }

    /** split = untere Zeilen (5–7) bleiben Minecraft-Rot für Nicht-Spieler (Treffer-Farbe „Nur Spieler“) */
    public static boolean recolorHurt(int argb, boolean split) {
        if (overlay == null || overlay.getPixels() == null) return false;
        for (int y = 0; y < 8; y++) {
            int c = split && y >= 5 ? 0xB2FF0000 : argb;
            int abgr = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
            for (int x = 0; x < 16; x++) overlay.getPixels().setPixelRGBA(x, y, abgr);
        }
        overlay.upload();
        return true;
    }
}
