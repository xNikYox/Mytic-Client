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
    public static final int PANEL = 0xF20A0716;
    public static final int SURFACE = 0xFF130D24;
    public static final int SURFACE_HOVER = 0xFF1F1638;
    public static final int LINE = 0xFF2C2050;
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
        if (t > 0.01f) glow(g, x, y, 20, 10, 5, withAlpha(accent2(), Math.round(200 * t)), 3, 0.8f);
        rect(g, x, y, 20, 10, 5, 0xFF2A2240);
        if (t > 0.01f) {
            int w = Math.max(10, Math.round(20 * t));
            rect(g, x, y, w, 10, 5, alpha(accent(), t));
            hGradient(g, x + 3, y + 1, w - 6, 8, alpha(accent(), t), alpha(accent2(), t));
        }
        int knob = x + 1 + Math.round(10 * t);
        rect(g, knob, y + 1, 8, 8, 4, 0xFFFFFFFF);
    }
    // ------------------------------------------------------------------ Neon-Design

    public static final int CYAN = 0xFF22E5FF;
    public static final int PINK = 0xFFFF2BD6;

    /** Zweite Neonfarbe zur Akzentfarbe: Cyan, bei bläulichen Akzenten Pink. */
    public static int accent2() {
        int a = accent();
        int r = (a >> 16) & 0xFF, gr = (a >> 8) & 0xFF, b = a & 0xFF;
        return b > 180 && gr > 140 && r < 120 ? PINK : CYAN;
    }

    /** Weiches Leuchten um ein Rechteck (vor der Fläche zeichnen). */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int color, int size, float strength) {
        int base = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, strength)));
        for (int i = size; i >= 1; i--) {
            int a = Math.round(base * 0.55f * (1f - (float) (i - 1) / size) / size * 2f);
            if (a > 0) rect(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, withAlpha(color, Math.min(255, a)));
        }
    }

    /** Waagerechter Farbverlauf von links nach rechts. */
    public static void hGradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        int step = Math.max(1, w / 48);
        for (int i = 0; i < w; i += step) {
            g.fill(x + i, y, x + Math.min(w, i + step), y + h, mix(left, right, (float) i / Math.max(1, w - 1)));
        }
    }

    /** Neon-Linie: zweite Neonfarbe, Akzent, Pink – mit schwachem Schein darüber und darunter. */
    public static void neonLine(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        int c1 = alpha(accent2(), alpha), c2 = alpha(accent(), alpha), c3 = alpha(PINK, alpha);
        int half = w / 2;
        hGradient(g, x, y - 1, half, 1, withAlpha(c1, 50), withAlpha(c2, 50));
        hGradient(g, x + half, y - 1, w - half, 1, withAlpha(c2, 50), withAlpha(c3, 50));
        hGradient(g, x, y, half, h, c1, c2);
        hGradient(g, x + half, y, w - half, h, c2, c3);
        hGradient(g, x, y + h, half, 1, withAlpha(c1, 50), withAlpha(c2, 50));
        hGradient(g, x + half, y + h, w - half, 1, withAlpha(c2, 50), withAlpha(c3, 50));
    }

    /** Ecken-Markierungen wie bei einem HUD: oben links in c1, unten rechts in c2. */
    public static void corners(GuiGraphics g, int x, int y, int w, int h, int len, int c1, int c2) {
        g.fill(x, y, x + len, y + 1, c1);
        g.fill(x, y, x + 1, y + len, c1);
        g.fill(x + w - len, y + h - 1, x + w, y + h, c2);
        g.fill(x + w - 1, y + h - len, x + w, y + h, c2);
    }

    /** Leuchtende Schrift: weicher Schein in der Textfarbe hinter dem Text. */
    public static void glowText(GuiGraphics g, String text, int x, int y, int color, float strength) {
        int halo = withAlpha(color, Math.round(70 * strength * (((color >>> 24) & 0xFF) / 255f)));
        g.drawString(font(), text, x - 1, y, halo, false);
        g.drawString(font(), text, x + 1, y, halo, false);
        g.drawString(font(), text, x, y - 1, halo, false);
        g.drawString(font(), text, x, y + 1, halo, false);
        g.drawString(font(), text, x, y, color, false);
    }

    /** Panel im Neon-Stil: Leuchten, dunkle Fläche, Neon-Kante oben, HUD-Ecken. */
    public static void neonPanel(GuiGraphics g, int x, int y, int w, int h, int r, float alpha) {
        glow(g, x, y, w, h, r, withAlpha(accent(), Math.round(255 * alpha)), 8, 0.5f);
        rect(g, x, y, w, h, r, alpha(mix(0xFF0A0716, accent(), 0.35f), alpha));
        rect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), alpha(PANEL, alpha));
        neonLine(g, x + r + 4, y, w - 2 * r - 8, 1, alpha);
        corners(g, x + 3, y + 3, w - 6, h - 6, 8, alpha(accent2(), alpha), alpha(PINK, alpha));
    }
}
