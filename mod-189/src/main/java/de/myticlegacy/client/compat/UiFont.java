package de.myticlegacy.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Schrift des neuen Mytic-Designs für 1.8.9: Minecraft 1.8.9 kennt keine TTF-Schriften, daher die Pixelschrift in
 * ganzzahliger Vergrößerung (bleibt scharf), passend zur gewünschten Größe. Gleiche Schnittstelle wie die
 * TTF-Fassung der neuen Versionen; alle Werte in echten Pixeln.
 */
public final class UiFont {
    private UiFont() {
    }

    public static boolean ttf() {
        return false;
    }

    /** Vergrößerung der Pixelschrift: Großbuchstaben (7 Pixel) etwa so hoch wie bei Inter (0,727 em) */
    private static int scale(float px) {
        return Math.max(1, Math.round(px * 0.727f / 7f));
    }

    public static int size(float px) {
        return Math.round(scale(px) * 7 / 0.727f);
    }

    public static int width(String s, float px, int weight) {
        return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s) * scale(px);
    }

    public static int capHeight(float px) {
        return 7 * scale(px);
    }

    /** Zeichnet so, dass die Oberkante der Großbuchstaben bei y = 0 liegt. */
    public static void draw(GuiGraphics g, String s, float px, int weight, int color) {
        if ((color >>> 24) < 8) return;
        int k = scale(px);
        GlStateManager.pushMatrix();
        GlStateManager.scale(k, k, 1);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Minecraft.getMinecraft().fontRendererObj.drawString(s, 0, 0, color, false);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }
}
