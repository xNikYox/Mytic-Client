package de.myticlegacy.client.gui.ui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.UiFont;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Typografie des neuen Designs: Inter über den TTF-Font-Provider (ab 1.21; unter 1.8.9 Ersatz durch die
 * Pixelschrift in ganzen Vergrößerungen), immer auf echte Pixel ausgerichtet. Größen und Koordinaten in den
 * aktuellen Zeichen-Einheiten; y ist die Oberkante der Großbuchstaben. Die Minecraft-Pixelschrift bleibt kleinen
 * Akzenten vorbehalten (pixel()).
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Type {
    private Type() {
    }

    /** Textbreite in Einheiten */
    public static float width(GuiGraphics g, String text, float size, int weight) {
        float k = Draw.k(g);
        return UiFont.width(text, size * k, weight) / k;
    }

    /** Höhe der Großbuchstaben in Einheiten (zum senkrechten Zentrieren: y = mitte - capHeight/2) */
    public static float capHeight(GuiGraphics g, float size) {
        float k = Draw.k(g);
        return UiFont.capHeight(size * k) / k;
    }

    /** Text zeichnen; y = Oberkante der Großbuchstaben */
    public static void draw(GuiGraphics g, String text, float x, float y, float size, int weight, int color) {
        if (text == null || text.isEmpty()) return;
        float k = Draw.begin(g);
        Gfx.translate(g, Math.round(x * k), Math.round(y * k));
        UiFont.draw(g, text, size * k, weight, color);
        Draw.end(g);
    }

    /** Zentriert um cx */
    public static void centered(GuiGraphics g, String text, float cx, float y, float size, int weight, int color) {
        draw(g, text, cx - width(g, text, size, weight) / 2f, y, size, weight, color);
    }

    /** Rechtsbündig an x */
    public static void right(GuiGraphics g, String text, float x, float y, float size, int weight, int color) {
        draw(g, text, x - width(g, text, size, weight), y, size, weight, color);
    }

    /** In einer Box senkrecht mittig (cy = Mitte) */
    public static void middle(GuiGraphics g, String text, float x, float cy, float size, int weight, int color) {
        draw(g, text, x, cy - capHeight(g, size) / 2f, size, weight, color);
    }

    /** Auf maxWidth kürzen (mit „…“) */
    public static String ellipsize(GuiGraphics g, String text, float maxWidth, float size, int weight) {
        if (text == null) return "";
        if (width(g, text, size, weight) <= maxWidth) return text;
        String dots = "…";
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (width(g, text.substring(0, mid) + dots, size, weight) <= maxWidth) lo = mid;
            else hi = mid - 1;
        }
        return text.substring(0, lo).trim() + dots;
    }

    // ------------------------------------------------------------------ Pixelschrift-Akzente

    /** Ganzzahlige Vergrößerung der Pixelschrift, damit Großbuchstaben etwa capPx Einheiten hoch sind */
    public static int pixelScale(GuiGraphics g, float capUnits) {
        return Math.max(1, Math.round(capUnits * Draw.k(g) / 7f));
    }

    /** Breite eines Pixelschrift-Textes in Einheiten (Vergrößerung s in echten Pixeln, Laufweite in Pixeln) */
    public static float pixelWidth(GuiGraphics g, String text, int s, int trackingPx) {
        float k = Draw.k(g);
        int w = 0;
        for (int i = 0; i < text.length(); i++) w += de.myticlegacy.client.gui.Ui.font().width(String.valueOf(text.charAt(i))) * s + (i < text.length() - 1 ? trackingPx : 0);
        return w / k;
    }

    /** Pixelschrift (Minecraft-Schrift) scharf in s-facher Vergrößerung, optional mit Laufweite */
    public static void pixel(GuiGraphics g, String text, float x, float y, int s, int trackingPx, int color) {
        float k = Draw.begin(g);
        int X = Math.round(x * k), Y = Math.round(y * k);
        Gfx.translate(g, X, Y);
        Gfx.scale(g, s, s);
        if (trackingPx == 0) {
            g.drawString(de.myticlegacy.client.gui.Ui.font(), text, 0, 0, color, false);
        } else {
            float cx = 0;
            for (int i = 0; i < text.length(); i++) {
                String c = String.valueOf(text.charAt(i));
                Gfx.push(g);
                Gfx.translate(g, Math.round(cx * s) / (float) s, 0);
                g.drawString(de.myticlegacy.client.gui.Ui.font(), c, 0, 0, color, false);
                Gfx.pop(g);
                cx += de.myticlegacy.client.gui.Ui.font().width(c) + trackingPx / (float) s;
            }
        }
        Draw.end(g);
    }
}
