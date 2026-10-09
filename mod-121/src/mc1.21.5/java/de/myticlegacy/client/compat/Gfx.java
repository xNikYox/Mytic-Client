package de.myticlegacy.client.compat;

import net.minecraft.client.gui.GuiGraphics;

/** Fassung bis 1.21.5: GuiGraphics nutzt noch einen 3D-PoseStack. */
public final class Gfx {
    private Gfx() {
    }

    public static void push(GuiGraphics g) {
        g.pose().pushPose();
    }

    public static void pop(GuiGraphics g) {
        g.pose().popPose();
    }

    public static void translate(GuiGraphics g, float x, float y) {
        g.pose().translate(x, y, 0);
    }

    public static void scale(GuiGraphics g, float x, float y) {
        g.pose().scale(x, y, 1);
    }

    /** Zeichenbereich begrenzen (Koordinaten im aktuellen, ggf. verkleinerten Zeichenraum). */
    public static void scissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public static void noScissor(GuiGraphics g) {
        g.disableScissor();
    }
}
