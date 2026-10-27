package de.myticlegacy.client.module;

import net.minecraft.client.renderer.texture.DynamicTexture;

/** PvP-Optik, versionsabhängiger Teil (1.21.2+): Treffer-Farbe in die Overlay-Textur malen */
public final class PvpCompat {
    /** Wird vom OverlayTexture-Mixin gesetzt */
    public static DynamicTexture overlay;

    private PvpCompat() {
    }

    /** obere Hälfte der Overlay-Textur (Treffer-Rot) neu einfärben; ARGB */
    public static boolean recolorHurt(int argb) {
        if (overlay == null || overlay.getPixels() == null) return false;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) overlay.getPixels().setPixel(x, y, argb);
        overlay.upload();
        return true;
    }
}
