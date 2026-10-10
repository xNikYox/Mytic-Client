package de.myticlegacy.client.gui;

import de.myticlegacy.client.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Farbwähler (Farbpalette): Feld für Sättigung und Helligkeit, Regler für Farbton und Deckkraft,
 * dazu die bisherigen Farben als Schnellauswahl. Öffnet sich unter der Farb-Einstellung im Mod-Menü.
 */
final class ColorPicker {
    static final int HEIGHT = 70;
    private static final int SV_W = 110;
    private static final int SV_H = 60;
    private static final int BAR_W = 10;
    private static final int GAP = 6;
    private static final int SW = 10;

    private ColorSetting setting;
    private float hue;
    private float sat;
    private float val;
    private int alpha = 255;
    private String dragging;
    private int ox;
    private int oy;

    ColorSetting setting() {
        return setting;
    }

    void open(ColorSetting s) {
        setting = s;
        load(s.get());
    }

    void close() {
        setting = null;
        dragging = null;
    }

    private void load(int argb) {
        alpha = (argb >>> 24) & 0xFF;
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        val = max;
        sat = max == 0 ? 0 : d / max;
        if (d == 0) return; // Grau: Farbton beibehalten
        float h;
        if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        hue = ((h / 6f) % 1f + 1f) % 1f;
    }

    /** HSV (0..1) → RGB mit voller Deckkraft. */
    static int hsv(float h, float s, float v) {
        float hh = (h % 1f + 1f) % 1f * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    int current() {
        return (alpha << 24) | (hsv(hue, sat, val) & 0xFFFFFF);
    }

    void draw(GuiGraphics g, int x, int y) {
        ox = x;
        oy = y;
        // Sättigung (waagerecht) × Helligkeit (senkrecht)
        for (int i = 0; i < SV_W; i += 2) g.fillGradient(x + i, y, x + i + 2, y + SV_H, hsv(hue, i / (float) (SV_W - 1), 1f), 0xFF000000);
        g.renderOutline(x - 1, y - 1, SV_W + 2, SV_H + 2, 0x40FFFFFF);
        int sx = x + Math.round(sat * (SV_W - 1));
        int sy = y + Math.round((1 - val) * (SV_H - 1));
        Ui.rect(g, sx - 3, sy - 3, 7, 7, 3, 0xFFFFFFFF);
        Ui.rect(g, sx - 2, sy - 2, 5, 5, 2, hsv(hue, sat, val));
        // Farbton
        int hx = x + SV_W + GAP;
        for (int k = 0; k < 6; k++) g.fillGradient(hx, y + k * SV_H / 6, hx + BAR_W, y + (k + 1) * SV_H / 6, hsv(k / 6f, 1, 1), hsv((k + 1) / 6f, 1, 1));
        int hy = y + Math.round(hue * (SV_H - 1));
        g.fill(hx - 1, hy - 1, hx + BAR_W + 1, hy + 2, 0xFFFFFFFF);
        // Deckkraft (oben deckend, unten durchsichtig) über einem Schachbrett
        int ax = hx + BAR_W + GAP;
        for (int cy = 0; cy < SV_H; cy += 4) {
            for (int cx = 0; cx < BAR_W; cx += 5) {
                g.fill(ax + cx, y + cy, ax + Math.min(BAR_W, cx + 5), y + Math.min(SV_H, cy + 4), ((cx / 5 + cy / 4) % 2 == 0) ? 0xFF7A7A7A : 0xFFBDBDBD);
            }
        }
        int rgb = hsv(hue, sat, val) & 0xFFFFFF;
        g.fillGradient(ax, y, ax + BAR_W, y + SV_H, 0xFF000000 | rgb, rgb);
        int ay = y + Math.round((1 - alpha / 255f) * (SV_H - 1));
        g.fill(ax - 1, ay - 1, ax + BAR_W + 1, ay + 2, 0xFFFFFFFF);
        // Vorschau, Hex-Wert, Schnellauswahl
        int px = ax + BAR_W + GAP + 4;
        Ui.rect(g, px, y, 26, 16, 3, 0xFFFFFFFF);
        Ui.rect(g, px + 1, y + 1, 24, 14, 2, current());
        String hex = String.format("#%06X", rgb) + (alpha < 255 ? "  " + Math.round(alpha / 2.55f) + " %" : "");
        Ui.text(g, hex, px + 32, y + 4, Ui.TEXT, false);
        for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
            int bx = px + (i % 6) * (SW + 3);
            int by = y + 24 + (i / 6) * (SW + 3);
            int c = ColorSetting.PALETTE[i];
            if (c == current()) Ui.rect(g, bx - 1, by - 1, SW + 2, SW + 2, 3, 0xFFFFFFFF);
            Ui.rect(g, bx, by, SW, SW, 2, c == 0xFF000000 ? 0xFF101010 : c);
        }
    }

    /** Klick in den Farbwähler (true = verarbeitet). */
    boolean click(double mx, double my) {
        if (setting == null) return false;
        int hx = ox + SV_W + GAP;
        int ax = hx + BAR_W + GAP;
        int px = ax + BAR_W + GAP + 4;
        if (Ui.inside(mx, my, ox, oy, SV_W, SV_H)) dragging = "sv";
        else if (Ui.inside(mx, my, hx - 2, oy, BAR_W + 4, SV_H)) dragging = "hue";
        else if (Ui.inside(mx, my, ax - 2, oy, BAR_W + 4, SV_H)) dragging = "alpha";
        if (dragging != null) {
            update(mx, my);
            return true;
        }
        for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
            int bx = px + (i % 6) * (SW + 3);
            int by = oy + 24 + (i / 6) * (SW + 3);
            if (Ui.inside(mx, my, bx, by, SW, SW)) {
                load(ColorSetting.PALETTE[i]);
                setting.set(current());
                return true;
            }
        }
        return Ui.inside(mx, my, ox, oy, px + 6 * (SW + 3) - ox, HEIGHT);
    }

    boolean drag(double mx, double my) {
        if (dragging == null || setting == null) return false;
        update(mx, my);
        return true;
    }

    boolean release() {
        boolean was = dragging != null;
        dragging = null;
        return was;
    }

    private static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    private void update(double mx, double my) {
        if ("sv".equals(dragging)) {
            sat = clamp((mx - ox) / (SV_W - 1));
            val = 1 - clamp((my - oy) / (SV_H - 1));
        } else if ("hue".equals(dragging)) {
            hue = clamp((my - oy) / (SV_H - 1));
        } else if ("alpha".equals(dragging)) {
            alpha = Math.round((1 - clamp((my - oy) / (SV_H - 1))) * 255);
        }
        setting.set(current());
    }
}
