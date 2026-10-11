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
}
