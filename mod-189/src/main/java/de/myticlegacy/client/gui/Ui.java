package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Font;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;
import de.myticlegacy.client.compat.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/** Zeichen-Helfer für das Mytic-Design: abgerundete Flächen, Farben, weiche Übergänge. */
public final class Ui {
    public static final int PANEL = 0xEE0F0C17;
    public static final int SURFACE = 0xFF19152A;
    public static final int SURFACE_HOVER = 0xFF241E3A;
    public static final int LINE = 0xFF2C2545;
    public static final int TEXT = 0xFFF1EEF9;
    public static final int MUTED = 0xFF9A93B4;
    public static final int GREEN = 0xFF5BE38A;
    public static final int RED = 0xFFFF5C72;

    private static final Map<Object, Float> ANIMATIONS = new HashMap<Object, Float>();
    private static long lastFrame = System.nanoTime();
    private static float frameSeconds;

    private Ui() {
    }

    private static Font font;

    public static Font font() {
        Minecraft mc = Minecraft.getMinecraft();
        if (font == null || font.renderer != mc.fontRendererObj) font = mc.fontRendererObj == null ? null : new Font(mc.fontRendererObj);
        return font;
    }

    public static int accent() {
        return MyticClient.accent();
    }

    /** Einmal pro Bild aufrufen, damit Animationen unabhängig von den FPS gleich schnell sind. */
    public static void frame() {
        long now = System.nanoTime();
        frameSeconds = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
    }

    /** Weicher Übergang zu einem Zielwert (0..1), z. B. für Hover-Effekte. */
    public static float animate(Object key, boolean target, float speed) {
        float value = ANIMATIONS.getOrDefault(key, target ? 1f : 0f);
        float goal = target ? 1f : 0f;
        value += (goal - value) * Math.min(1f, frameSeconds * speed);
        if (Math.abs(goal - value) < 0.002f) value = goal;
        ANIMATIONS.put(key, value);
        return value;
    }

    public static int alpha(int color, float factor) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, factor)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Rechteck mit abgerundeten Ecken (Radius in GUI-Pixeln). */
    public static void rect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int dx = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x + dx, y + i, x + r, y + i + 1, color);
            g.fill(x + w - r, y + i, x + w - dx, y + i + 1, color);
            g.fill(x + dx, y + h - 1 - i, x + r, y + h - i, color);
            g.fill(x + w - r, y + h - 1 - i, x + w - dx, y + h - i, color);
        }
    }

    /** Abgerundeter Rahmen (1 Pixel), durch zwei Flächen übereinander. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int color, int inner) {
        rect(g, x, y, w, h, r, color);
        rect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), inner);
    }

    public static void text(GuiGraphics g, String text, int x, int y, int color, boolean shadow) {
        g.drawString(font(), text, x, y, color, shadow);
    }

    public static void centered(GuiGraphics g, String text, int cx, int y, int color, boolean shadow) {
        g.drawString(font(), text, cx - font().width(text) / 2, y, color, shadow);
    }

    public static void scaled(GuiGraphics g, String text, float x, float y, float scale, int color, boolean shadow) {
        Gfx.push(g);
        Gfx.translate(g, x, y);
        Gfx.scale(g, scale, scale);
        g.drawString(font(), text, 0, 0, color, shadow);
        Gfx.pop(g);
    }

    /** Mytic-Logo als Text: "MYTIC" in der Akzentfarbe, "CLIENT" weiß, kursiv wirkend durch Versatz-Schatten. */
    public static void logo(GuiGraphics g, float x, float y, float scale) {
        Gfx.push(g);
        Gfx.translate(g, x, y);
        Gfx.scale(g, scale, scale);
        int w = font().width("MYTIC ");
        g.drawString(font(), "MYTIC", 1, 1, withAlpha(accent(), 90), false);
        g.drawString(font(), "MYTIC", 0, 0, accent(), false);
        g.drawString(font(), "CLIENT", w, 0, TEXT, false);
        Gfx.pop(g);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Schalter wie bei Lunar: Pille mit Knopf, animiert. */
    public static void toggle(GuiGraphics g, Object key, int x, int y, boolean on) {
        float t = animate(key, on, 14f);
        rect(g, x, y, 20, 10, 5, mix(0xFF3A3352, accent(), t));
        int knob = x + 1 + Math.round(10 * t);
        rect(g, knob, y + 1, 8, 8, 4, 0xFFFFFFFF);
    }
}
