package de.myticlegacy.client.module;

import net.minecraft.client.renderer.texture.DynamicTexture;

/** PvP-Optik (1.21 – 1.21.1): Treffer-Farbe in die Overlay-Textur malen (NativeImage hier mit ABGR) */
public final class PvpCompat {
    public static DynamicTexture overlay;

    private PvpCompat() {
    }

    public static boolean recolorHurt(int argb) {
        if (overlay == null || overlay.getPixels() == null) return false;
        int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) overlay.getPixels().setPixelRGBA(x, y, abgr);
        overlay.upload();
        return true;
    }
}
