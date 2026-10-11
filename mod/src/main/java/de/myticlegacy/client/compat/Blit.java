package de.myticlegacy.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Textur-Ausschnitt zeichnen. Diese Fassung gilt ab 1.21.6 (RenderPipeline); ältere haben eine eigene Blit.java. */
public final class Blit {
    private Blit() {
    }

    /** Ausschnitt (u, v, uw x vh) einer Textur der Größe texW x texH auf w x h zeichnen. */
    public static void region(GuiGraphics g, Identifier texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, uw, vh, texW, texH);
    }
}
