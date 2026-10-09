package de.myticlegacy.client.compat;

import net.minecraft.client.gui.GuiGraphics;

/** Verschieben/Skalieren beim Zeichnen. Ab 1.21.6 2D-Matrix; ältere Versionen haben eine eigene Gfx.java. */
public final class Gfx {
    private Gfx() {
    }

    public static void push(GuiGraphics g) {
        g.pose().pushMatrix();
    }

    public static void pop(GuiGraphics g) {
        g.pose().popMatrix();
    }

    public static void translate(GuiGraphics g, float x, float y) {
        g.pose().translate(x, y);
    }

    public static void scale(GuiGraphics g, float x, float y) {
        g.pose().scale(x, y);
    }

    /** Zeichenbereich begrenzen (Koordinaten im aktuellen, ggf. verkleinerten Zeichenraum). */
    public static void scissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public static void noScissor(GuiGraphics g) {
        g.disableScissor();
    }
}
