package de.myticlegacy.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Fassung bis 1.21.3: GuiGraphics nutzt noch einen 3D-PoseStack. */
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
        // bis 1.21.3 ignoriert enableScissor Verschiebung und Skalierung: Ecken selbst umrechnen
        Matrix4f m = g.pose().last().pose();
        Vector3f a = m.transformPosition(new Vector3f(x1, y1, 0));
        Vector3f b = m.transformPosition(new Vector3f(x2, y2, 0));
        g.enableScissor(Math.round(a.x()), Math.round(a.y()), Math.round(b.x()), Math.round(b.y()));
    }

    public static void noScissor(GuiGraphics g) {
        g.disableScissor();
    }
}
