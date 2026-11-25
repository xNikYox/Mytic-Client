package de.myticlegacy.client.gui.ui;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Tex;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Grundformen des neuen Designs, immer auf echte Bildschirm-Pixel gerundet (gestochen scharf bei jeder
 * GUI-Größe): abgerundete Flächen und Rahmen mit geglätteten Ecken (vorgezeichnete Kreis-/Ring-Texturen statt
 * Treppen – Weiterentwicklung der „glatten Rundungen“ aus 2.53.3), Akzent-Verlauf, Glas, weiche Schatten,
 * Köpfe. Koordinaten in den aktuellen Zeichen-Einheiten (float), Radien ebenso.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Draw {
    private Draw() {
    }

    /** Echte Pixel je Zeichen-Einheit an dieser Stelle */
    public static float k(GuiGraphics g) {
        return Gfx.scaleX(g) * (float) Compat.guiScale();
    }

    /** In den Pixel-Raum wechseln (1 Einheit = 1 Pixel); danach unbedingt end() */
    public static float begin(GuiGraphics g) {
        float k = k(g);
        Gfx.push(g);
        Gfx.scale(g, 1f / k, 1f / k);
        return k;
    }

    public static void end(GuiGraphics g) {
        Gfx.pop(g);
    }

    public static int alpha(int color, float f) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, f)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Farbe des Akzent-Verlaufs (#9B5CFF → #C25EF0 → #EC5FBF) an Stelle t */
    public static int accent(float t) {
        return t < 0.5f ? mix(Tokens.ACC_1, Tokens.ACC_MID, t * 2) : mix(Tokens.ACC_MID, Tokens.ACC_2, t * 2 - 1);
    }

    /** Heller (f > 0) bzw. dunkler, Alpha bleibt */
    public static int brighten(int color, float f) {
        return f >= 0 ? mix(color, color | 0x00FFFFFF, f) : mix(color, color & 0xFF000000, -f);
    }

    // ------------------------------------------------------------------ Pixel-Raum (int, schon gerundet)

    private static int clampR(int r, int w, int h) {
        return Math.max(0, Math.min(r, Math.min(w, h) / 2));
    }

    /** Abgerundete Fläche in Pixeln */
    public static void pxRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = clampR(r, w, h);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        Bake.Baked d = Bake.disc(r);
        if (d == null) return;
        corners(g, d, x, y, w, h, r, color, color);
    }

    private static void corners(GuiGraphics g, Bake.Baked d, int x, int y, int w, int h, int r, int left, int right) {
        Tex.drawTinted(g, d.tex, x, y, r, r, 0, 0, r, r, d.w, d.h, left);
        Tex.drawTinted(g, d.tex, x + w - r, y, r, r, r, 0, r, r, d.w, d.h, right);
        Tex.drawTinted(g, d.tex, x, y + h - r, r, r, 0, r, r, r, d.w, d.h, left);
        Tex.drawTinted(g, d.tex, x + w - r, y + h - r, r, r, r, r, r, r, d.w, d.h, right);
    }

    /** Rahmen (Stärke t Pixel) in Pixeln, innen frei */
    public static void pxRing(GuiGraphics g, int x, int y, int w, int h, int r, int t, int color) {
        if (w <= 0 || h <= 0 || t <= 0 || (color >>> 24) == 0) return;
        r = clampR(r, w, h);
        if (r < t) {
            g.fill(x, y, x + w, y + t, color);
            g.fill(x, y + h - t, x + w, y + h, color);
            g.fill(x, y + t, x + t, y + h - t, color);
            g.fill(x + w - t, y + t, x + w, y + h - t, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + t, color);
        g.fill(x + r, y + h - t, x + w - r, y + h, color);
        g.fill(x, y + r, x + t, y + h - r, color);
        g.fill(x + w - t, y + r, x + w, y + h - r, color);
        Bake.Baked d = Bake.ring(r, t);
        if (d != null) corners(g, d, x, y, w, h, r, color, color);
    }

    /** Waagerechter Verlauf links → rechts mit abgerundeten Ecken (Akzent: accent(t)) */
    public static void pxGradient(GuiGraphics g, int x, int y, int w, int h, int r, int left, int right, boolean accent3) {
        if (w <= 0 || h <= 0) return;
        r = clampR(r, w, h);
        int body = w - 2 * r;
        int steps = Math.max(1, Math.min(64, body / 3));
        for (int i = 0; i < steps; i++) {
            int x0 = x + r + body * i / steps, x1 = x + r + body * (i + 1) / steps;
            float t = ((x0 + x1) / 2f - x) / Math.max(1f, w);
            int c = accent3 ? alpha(accent(t), ((left >>> 24) & 255) / 255f) : mix(left, right, t);
            g.fill(x0, y, x1, y + h, c);
        }
        if (r > 0) {
            int cl = accent3 ? alpha(accent(r / 2f / w), ((left >>> 24) & 255) / 255f) : left;
            int cr = accent3 ? alpha(accent(1 - r / 2f / w), ((left >>> 24) & 255) / 255f) : right;
            g.fill(x, y + r, x + r, y + h - r, cl);
            g.fill(x + w - r, y + r, x + w, y + h - r, cr);
            Bake.Baked d = Bake.disc(r);
            if (d != null) corners(g, d, x, y, w, h, r, cl, cr);
        }
    }

    /** Weicher Schatten in Pixeln (blur = CSS-Blur-Radius, spread negativ = kleiner) */
    public static void pxShadow(GuiGraphics g, int x, int y, int w, int h, int r, int blur, int spread, int color) {
        int sw = w + 2 * spread, sh = h + 2 * spread;
        if (sw <= 0 || sh <= 0 || blur <= 0 || (color >>> 24) == 0) return;
        Bake.Baked s = Bake.shadow(sw, sh, Math.max(0, r + spread), blur);
        if (s == null) return;
        Tex.drawTinted(g, s.tex, x - spread - s.ox, y - spread - s.oy, s.w, s.h, 0, 0, s.w, s.h, s.w, s.h, color);
    }

    /** Textur 1:1 in Pixeln eingefärbt zeichnen */
    public static void pxBaked(GuiGraphics g, Bake.Baked b, int x, int y, int color) {
        if (b == null) return;
        Tex.drawTinted(g, b.tex, x - b.ox, y - b.oy, b.w, b.h, 0, 0, b.w, b.h, b.w, b.h, color);
    }

    // ------------------------------------------------------------------ Einheiten-Raum (float)

    private static int[] px(float k, float x, float y, float w, float h) {
        int X = Math.round(x * k), Y = Math.round(y * k);
        return new int[]{X, Y, Math.round((x + w) * k) - X, Math.round((y + h) * k) - Y};
    }

    /** Abgerundete Fläche */
    public static void rect(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
        float k = begin(g);
        int[] p = px(k, x, y, w, h);
        pxRect(g, p[0], p[1], p[2], p[3], Math.round(r * k), color);
        end(g);
    }

    /** 1-px-Rahmen (Stärke in echten Pixeln) */
    public static void ring(GuiGraphics g, float x, float y, float w, float h, float r, int thicknessPx, int color) {
        float k = begin(g);
        int[] p = px(k, x, y, w, h);
        pxRing(g, p[0], p[1], p[2], p[3], Math.round(r * k), thicknessPx, color);
        end(g);
    }

    /** Fläche im Akzent-Verlauf (alpha 0..1) */
    public static void accentRect(GuiGraphics g, float x, float y, float w, float h, float r, float alpha) {
        float k = begin(g);
        int[] p = px(k, x, y, w, h);
        pxGradient(g, p[0], p[1], p[2], p[3], Math.round(r * k), alpha(0xFFFFFFFF, alpha), 0, true);
        end(g);
    }

    /** Waagerechter Verlauf zwischen zwei Farben */
    public static void gradient(GuiGraphics g, float x, float y, float w, float h, float r, int left, int right) {
        float k = begin(g);
        int[] p = px(k, x, y, w, h);
        pxGradient(g, p[0], p[1], p[2], p[3], Math.round(r * k), left, right, false);
        end(g);
    }

    /** Weicher Schatten */
    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float r, float blur, float spread, int color) {
        float k = begin(g);
        int[] p = px(k, x, y, w, h);
        pxShadow(g, p[0], p[1], p[2], p[3], Math.round(r * k), Math.max(1, Math.round(blur * k)), Math.round(spread * k), color);
        end(g);
    }

    /**
     * Glas-Fläche: durchscheinende dunkle Füllung, 1-px-Rahmen, feine Lichtkante oben. Echte Weichzeichnung
     * gibt es nur, wo der Bildschirm sie ohnehin hat (Minecraft-Menü-Blur ab 1.21); sonst opaque = true nehmen.
     */
    public static void glass(GuiGraphics g, float x, float y, float w, float h, float r, int fill, int line) {
        float k = begin(g);
        int[] p = px(k, x, y, w, h);
        int R = Math.round(r * k);
        pxRect(g, p[0] + 1, p[1] + 1, p[2] - 2, p[3] - 2, Math.max(0, R - 1), fill);
        pxRing(g, p[0], p[1], p[2], p[3], R, 1, line);
        int rr = clampR(R, p[2], p[3]);
        g.fill(p[0] + rr, p[1] + 1, p[0] + p[2] - rr, p[1] + 2, Tokens.HIGHLIGHT);
        end(g);
    }

    /** Kreis (z. B. Status-Punkt) */
    public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
        float k = begin(g);
        int d = Math.max(1, Math.round(radius * 2 * k));
        int X = Math.round(cx * k - d / 2f), Y = Math.round(cy * k - d / 2f);
        pxRect(g, X, Y, d, d, d / 2, color);
        end(g);
    }

    /** Spielerkopf, abgerundet (radius = size/2 → rund); ohne Hautdaten ein Buchstabe auf Akzent */
    public static void head(GuiGraphics g, int[] pixels, String name, float x, float y, float size, float radius) {
        float k = begin(g);
        int X = Math.round(x * k), Y = Math.round(y * k), S = Math.max(1, Math.round(size * k)), R = Math.round(radius * k);
        Bake.Baked b = Bake.head(pixels, S, R);
        if (b != null) {
            Tex.drawTinted(g, b.tex, X, Y, S, S, 0, 0, S, S, b.w, b.h, 0xFFFFFFFF);
        } else {
            pxGradient(g, X, Y, S, S, R, 0xFFFFFFFF, 0, true);
            if (name != null && !name.isEmpty()) {
                String c = name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                float fs = S * 0.5f;
                int tw = de.myticlegacy.client.compat.UiFont.width(c, fs, Tokens.SEMIBOLD);
                Gfx.push(g);
                Gfx.translate(g, X + (S - tw) / 2, Y + (S - de.myticlegacy.client.compat.UiFont.capHeight(fs)) / 2);
                de.myticlegacy.client.compat.UiFont.draw(g, c, fs, Tokens.SEMIBOLD, 0xFFFFFFFF);
                Gfx.pop(g);
            }
        }
        end(g);
    }

    /** Senkrechter Verlauf über eine Fläche (ohne Rundung) */
    public static void vgradient(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
        float k = begin(g);
        int[] p = px(k, x, y, w, h);
        g.fillGradient(p[0], p[1], p[0] + p[2], p[1] + p[3], top, bottom);
        end(g);
    }
}
