package de.myticlegacy.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Textur-Ausschnitt zeichnen (1.21.2 bis 1.21.5, RenderType). */
public final class Blit {
    private Blit() {
    }

    /** Ausschnitt (u, v, uw x vh) einer Textur der Größe texW x texH auf w x h zeichnen. */
    public static void region(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
        g.blit(RenderType::guiTextured, texture, x, y, u, v, w, h, uw, vh, texW, texH);
    }
}
