package de.myticlegacy.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Textur-Ausschnitt zeichnen (1.21 und 1.21.1). */
public final class Blit {
    private Blit() {
    }

    /** Ausschnitt (u, v, uw x vh) einer Textur der Größe texW x texH auf w x h zeichnen. */
    public static void region(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
        g.blit(texture, x, y, w, h, u, v, uw, vh, texW, texH);
    }

    /** Wie region(), aber eingefärbt (über die Shader-Farbe; 1.21/1.21.1 kennt keine Farbe beim Blit). */
    public static void tint(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH, int argb) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        g.blit(texture, x, y, w, h, u, v, uw, vh, texW, texH);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
