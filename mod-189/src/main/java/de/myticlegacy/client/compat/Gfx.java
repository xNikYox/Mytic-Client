package de.myticlegacy.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Verschieben/Skalieren beim Zeichnen (OpenGL-Matrix). Die Verschiebung wird zusätzlich mitgerechnet, damit
 * scissor() die Koordinaten in Fensterpixel umrechnen kann.
 */
public final class Gfx {
    private static final Deque<float[]> STACK = new ArrayDeque<float[]>();
    /** tx, ty, sx, sy relativ zur Minecraft-GUI. */
    private static float[] current = {0, 0, 1, 1};

    private Gfx() {
    }

    public static void push(GuiGraphics g) {
        GlStateManager.pushMatrix();
        STACK.push(current.clone());
    }

    public static void pop(GuiGraphics g) {
        GlStateManager.popMatrix();
        current = STACK.isEmpty() ? new float[]{0, 0, 1, 1} : STACK.pop();
    }

    public static void translate(GuiGraphics g, float x, float y) {
        GlStateManager.translate(x, y, 0);
        current[0] += x * current[2];
        current[1] += y * current[3];
    }

    public static void scale(GuiGraphics g, float x, float y) {
        GlStateManager.scale(x, y, 1);
        current[2] *= x;
        current[3] *= y;
    }

    /** Zu Beginn jedes Bildes, falls ein Fehler den Stapel durcheinandergebracht hat. */
    public static void reset() {
        STACK.clear();
        current = new float[]{0, 0, 1, 1};
    }

    /** Zeichenbereich begrenzen (Koordinaten im aktuellen, ggf. verkleinerten Zeichenraum). */
    public static void scissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        Minecraft mc = Minecraft.getMinecraft();
        int gui = new ScaledResolution(mc).getScaleFactor();
        int px1 = Math.round((current[0] + x1 * current[2]) * gui);
        int py1 = Math.round((current[1] + y1 * current[3]) * gui);
        int px2 = Math.round((current[0] + x2 * current[2]) * gui);
        int py2 = Math.round((current[1] + y2 * current[3]) * gui);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(px1, mc.displayHeight - py2, Math.max(0, px2 - px1), Math.max(0, py2 - py1));
    }

    public static void noScissor(GuiGraphics g) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
