package de.myticlegacy.client.gui.ui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Tex;
import net.minecraft.client.gui.GuiGraphics;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * „Mytic“-Hintergrund des Titelbildschirms (wie titlebg.js der Vorschau): Pixel-Abenddämmerung mit schwebender
 * Insel und Turm, Burg-Silhouette mit hellen Fenstern, Hügel mit Stufen-Tannen, Vordergrund. Die Ebenen werden
 * einmal je Auflösung in echten Pixeln ohne Glättung gezeichnet (im Hintergrund-Thread, Nearest-Neighbor) und
 * pro Bild nur auf ganze Pixel verschoben: langsame Drift (70–150 s hin und zurück) plus leichte Maus-Parallaxe.
 * Live dazu: funkelnde Sterne, Pixel-Wolken, aufsteigende Funken in Lila/Pink, pulsierendes Horizont-Leuchten,
 * Vignette. Ohne Animationen steht alles still.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class TitleBackground {
    private static final int W = 1920, H = 1080, OVER = 160;
    private static final float[] DEPTH = {0.3f, 0.45f, 0.7f, 1f};
    private static final float[] PERIOD = {150, 120, 90, 70};

    /** Fertige Ebenen (PNG) aus dem Hintergrund-Thread */
    static final class Layers {
        String key;
        byte[][] png;
        int[] top, w, h;
        int up; // Vergrößerung beim Zeichnen (große Auflösungen werden halb so groß gezeichnet)
        float s, dx0, wd;
    }

    private static volatile Layers ready;
    private static volatile boolean rendering;
    private static String wanted;
    private static Object[] textures;
    private static Layers uploaded;
    private static float mx, my;
    private static final long START = System.currentTimeMillis();

    private TitleBackground() {
    }

    // ------------------------------------------------------------------ Zufall wie in der Vorschau (mulberry32)

    private static final class Rng {
        int a;

        Rng(int seed) {
            a = seed;
        }

        float next() {
            a += 0x6D2B79F5;
            int t = (a ^ (a >>> 15)) * (1 | a);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return (float) (((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0);
        }
    }

    // ------------------------------------------------------------------ Ebenen zeichnen (ganzzahlig, ohne Glättung)

    private static final class Painter {
        final int[] px;
        final int w, h;
        final float k;

        Painter(int w, int h, float k) {
            this.w = w;
            this.h = h;
            this.k = k;
            px = new int[w * h];
        }

        void rect(float x, float y, float rw, float rh, int argb) {
            int x0 = Math.round(x * k), y0 = Math.round(y * k), x1 = Math.round((x + rw) * k), y1 = Math.round((y + rh) * k);
            x0 = Math.max(0, x0);
            y0 = Math.max(0, y0);
            x1 = Math.min(w, x1);
            y1 = Math.min(h, y1);
            int a = argb >>> 24;
            for (int y2 = y0; y2 < y1; y2++) {
                int row = y2 * w;
                for (int x2 = x0; x2 < x1; x2++) px[row + x2] = a == 255 ? argb : over(px[row + x2], argb);
            }
        }

        static int over(int dst, int src) {
            int sa = src >>> 24, da = dst >>> 24;
            if (sa == 0) return dst;
            int oa = sa + da * (255 - sa) / 255;
            if (oa == 0) return 0;
            int r = (((src >> 16) & 255) * sa + ((dst >> 16) & 255) * da * (255 - sa) / 255) / oa;
            int g = (((src >> 8) & 255) * sa + ((dst >> 8) & 255) * da * (255 - sa) / 255) / oa;
            int b = ((src & 255) * sa + (dst & 255) * da * (255 - sa) / 255) / oa;
            return (oa << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private static void ridge(Painter p, Rng r, float lw, float base, float amp, float f, float ph, int step, int color) {
        for (int x = 0; x < lw; x += step) {
            double v = base - amp * (Math.sin(x * f + ph) * .62 + Math.sin(x * f * 2.7 + ph * 1.9) * .28 + (r.next() - .5) * .18);
            float y = Math.round(v / step) * step;
            p.rect(x, y, step, H - y, color);
        }
    }

    private static void castle(Painter p, float x, float y, int c, int win) {
        p.rect(x - 70, y - 60, 140, 60, c);
        for (int i = -70; i < 70; i += 20) p.rect(x + i, y - 70, 10, 10, c);
        p.rect(x - 95, y - 120, 36, 120, c);
        p.rect(x - 95, y - 130, 10, 10, c);
        p.rect(x - 69, y - 130, 10, 10, c);
        p.rect(x + 60, y - 105, 32, 105, c);
        p.rect(x + 60, y - 115, 10, 10, c);
        p.rect(x + 82, y - 115, 10, 10, c);
        p.rect(x - 18, y - 175, 36, 175, c);
        p.rect(x - 24, y - 185, 48, 12, c);
        for (int i = -24; i < 24; i += 12) p.rect(x + i, y - 195, 6, 10, c);
        p.rect(x - 2, y - 220, 4, 35, c);
        p.rect(x + 2, y - 220, 16, 8, c);
        float[][] windows = {{x - 6, y - 150}, {x - 6, y - 118}, {x - 84, y - 92}, {x + 70, y - 72}, {x + 28, y - 36}, {x - 46, y - 36}};
        for (float[] wnd : windows) p.rect(wnd[0], wnd[1], 8, 12, win);
    }

    private static void island(Painter p, float x, float y, int c, int top, int win) {
        int b = 12;
        p.rect(x - 96, y - b, 192, b, top);
        p.rect(x - 96, y, 192, b * 2, c);
        int[] widths = {156, 132, 108, 84, 60, 36, 12};
        for (int i = 0; i < widths.length; i++) p.rect(x - widths[i] / 2f + (i % 2 == 1 ? b / 2f : 0), y + b * 2 + i * b, widths[i], b, c);
        p.rect(x - 48, y - 72, 24, 60, c);
        p.rect(x - 54, y - 84, 36, 12, c);
        p.rect(x - 54, y - 96, 12, 12, c);
        p.rect(x - 30, y - 96, 12, 12, c);
        p.rect(x + 18, y - 48, 48, 36, c);
        p.rect(x + 30, y - 60, 24, 12, c);
        p.rect(x + 36, y - 12, 12, 12, c);
        p.rect(x - 40, y - 60, 8, 12, win);
    }

    private static int rgba(int r, int g, int b, float a) {
        return (Math.round(a * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    /** Eine Ebene als Bild (k = Pixel je Design-Pixel, lw = Ebenenbreite in Design-Pixeln, dx0 = Versatz bei Breitbild) */
    private static int[] layer(int i, Painter p, float lw, float dx0) {
        Rng r = new Rng(42 + i * 7);
        switch (i) {
            case 0:
                island(p, 520 + OVER + dx0, 300, 0xFF29254D, 0xFF2D2954, rgba(255, 160, 210, .6f));
                break;
            case 1:
                ridge(p, r, lw, 640, 120, .0032f, 1.1f, 20, 0xFF272347);
                castle(p, 1350 + OVER + dx0, 560, 0xFF272347, rgba(255, 175, 210, .45f));
                ridge(p, r, lw, 720, 60, .006f, 4.2f, 20, 0xFF221F40);
                break;
            case 2: {
                ridge(p, r, lw, 800, 70, .0045f, 2.3f, 24, 0xFF1A1834);
                int n = Math.max(9, (int) ((lw - 120) / 260) + 1);
                for (int t = 0; t < n; t++) {
                    float x = Math.round((120 + t * 260 + r.next() * 80) / 24) * 24f, y = Math.round((760 - r.next() * 40) / 24) * 24f;
                    int c = 0xFF181631;
                    p.rect(x, y - 24, 72, 24, c);
                    p.rect(x + 12, y - 48, 48, 24, c);
                    p.rect(x + 24, y - 72, 24, 24, c);
                    p.rect(x + 30, y - 84, 12, 12, c);
                    p.rect(x + 30, y, 12, 24, c);
                }
                break;
            }
            default:
                for (int x = 0; x < lw; x += 32) {
                    float t = x / lw, kk = Math.max(0, Math.abs(t - .5f) * 2 - .25f);
                    float y = Math.round((1010 - kk * 300 - (float) Math.sin(x * .01) * 16) / 32) * 32f;
                    p.rect(x, y, 32, H - y, 0xFF0E0C1F);
                }
        }
        return p.px;
    }

    static Layers render(String key, int sw, int sh) {
        float s = sh / (float) H;
        int up = s >= 1.95f ? (int) Math.floor(s + 0.05f) : 1;
        float k = s / up;
        float wd = Math.max(sw / s, 1f); // sichtbare Breite in Design-Pixeln
        float dx0 = (wd - W) / 2f;
        float lw = wd + 2 * OVER;
        int pw = (int) Math.ceil(lw * k), ph = (int) Math.ceil(H * k);
        Layers out = new Layers();
        out.key = key;
        out.png = new byte[4][];
        out.top = new int[4];
        out.w = new int[4];
        out.h = new int[4];
        out.up = up;
        out.s = s;
        out.dx0 = dx0;
        out.wd = wd;
        for (int i = 0; i < 4; i++) {
            Painter p = new Painter(pw, ph, k);
            int[] px = layer(i, p, lw, dx0);
            // leere Zeilen oben und unten abschneiden (spart Speicher)
            int top = 0, bottom = ph;
            while (top < ph && emptyRow(px, pw, top)) top++;
            while (bottom > top && emptyRow(px, pw, bottom - 1)) bottom--;
            if (bottom <= top) {
                top = 0;
                bottom = 1;
            }
            BufferedImage img = new BufferedImage(pw, bottom - top, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, pw, bottom - top, px, top * pw, pw);
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(img, "png", bytes);
                out.png[i] = bytes.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            out.top[i] = top;
            out.w[i] = pw;
            out.h[i] = bottom - top;
        }
        return out;
    }

    private static boolean emptyRow(int[] px, int w, int y) {
        int row = y * w;
        for (int x = 0; x < w; x++) if ((px[row + x] >>> 24) != 0) return false;
        return true;
    }

    private static synchronized void request(final String key, final int sw, final int sh) {
        if (rendering) return;
        rendering = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    ready = render(key, sw, sh);
                } catch (Throwable e) {
                    MyticClient.LOG.warn("Mytic-Hintergrund nicht gezeichnet: " + e);
                } finally {
                    rendering = false;
                }
            }
        }, "Mytic Titel-Hintergrund");
        t.setDaemon(true);
        t.start();
    }

    /** Texturen freigeben (Titelbildschirm verlassen); die PNG-Ebenen bleiben, beim nächsten Mal nur neu hochladen */
    public static void release() {
        if (textures != null) for (Object t : textures) if (t != null) Tex.release(t);
        textures = null;
        uploaded = null;
    }

    // ------------------------------------------------------------------ Zeichnen

    /**
     * Ganzer Hintergrund in Pixeln (vorher Draw.begin): sw × sh Bildschirm-Pixel, Maus in Pixeln.
     * animate = false: alles steht still (Einstellung „Animationen im Menü“ aus).
     */
    public static void draw(GuiGraphics g, int sw, int sh, double mouseX, double mouseY, boolean animate) {
        float t = animate ? (System.currentTimeMillis() - START) : 0;
        String key = sw + "x" + sh;
        Layers r = ready;
        // neue Größe: Ebenen im Hintergrund neu zeichnen (währenddessen bleiben die alten bzw. nur der Himmel)
        if ((r == null || !key.equals(r.key)) && !rendering && !key.equals(wanted)) {
            wanted = key;
            request(key, sw, sh);
        }
        if (r != null && r != uploaded && key.equals(r.key)) {
            release();
            textures = new Object[4];
            for (int i = 0; i < 4; i++) textures[i] = Tex.upload("ui/titlebg" + i + "_" + key, r.png[i]);
            uploaded = r;
        }

        float s = sh / (float) H;
        // Himmel
        int[] stops = {0xFF0B0B20, 0xFF141433, 0xFF252047, 0xFF3B2C58, 0xFF523B62, 0xFF261D40, 0xFF110E24};
        float[] at = {0, .30f, .55f, .70f, .78f, .88f, 1};
        for (int i = 0; i < stops.length - 1; i++) {
            int y0 = Math.round(at[i] * sh), y1 = Math.round(at[i + 1] * sh);
            if (y1 > y0) g.fillGradient(0, y0, sw, y1, stops[i], stops[i + 1]);
        }
        // Maus-Parallaxe weich nachführen
        float tx = animate ? (float) Math.max(-.5, Math.min(.5, mouseX / Math.max(1, sw) - .5)) : 0;
        float ty = animate ? (float) Math.max(-.5, Math.min(.5, mouseY / Math.max(1, sh) - .5)) : 0;
        mx += (tx - mx) * .08f;
        my += (ty - my) * .08f;
        if (!animate) mx = my = 0;

        // Horizont-Leuchten (pulsiert in 11 s)
        float pulse = animate ? Motion.easeInOut((float) (0.5 - 0.5 * Math.cos(t / 11000.0 * 2 * Math.PI))) : 0.5f;
        Bake.Baked glow = glowTexture(sw, sh);
        if (glow != null) {
            int gx = Math.round(sw * .1f), gy = Math.round(sh * .52f), gw = Math.round(sw * .8f), gh = Math.round(sh * .34f);
            Tex.drawTinted(g, glow.tex, gx, gy, gw, gh, 0, 0, glow.w, glow.h, glow.w, glow.h, Draw.alpha(0xFFFFFFFF, .55f + .45f * pulse));
        }
        float wd = sw / s;
        // Sterne (Tiefe .15)
        int[] so = offset(.15f, 0, t, s);
        Rng rs = new Rng(9);
        for (int i = 0; i < 70; i++) {
            int size = rs.next() < .15f ? 3 : 2;
            float x = rs.next() * wd, y = rs.next() * 560, o = .25f + rs.next() * .55f, dur = (3 + rs.next() * 5) * 1000, delay = rs.next() * 8 * 1000;
            float ph = ((t + delay) / dur) % 2f;
            float op = animate ? o + (.05f - o) * Motion.easeInOut(ph < 1 ? ph : 2 - ph) : o;
            int X = Math.round(x * s) + so[0], Y = Math.round(y * s) + so[1], S = Math.max(1, Math.round(size * s));
            g.fill(X, Y, X + S, Y + S, Draw.alpha(0xFFFFFFFF, op));
        }
        // Wolken (Tiefe .25, ziehen in 4–6 Minuten durch)
        int[] co = offset(.25f, 0, t, s);
        float[][] clouds = {{230, 240, 0, 260}, {370, 310, -120, 180}, {160, 380, -260, 320}};
        for (float[] c : clouds) {
            float frac = (((t / 1000f - c[2]) / c[1]) % 1f + 1f) % 1f;
            float x = -400 + (wd + 680) * frac;
            int X = Math.round(x * s) + co[0], Y = Math.round(c[0] * s) + co[1], CW = Math.round(c[3] * s), CH = Math.max(1, Math.round(12 * s));
            g.fill(X, Y, X + CW, Y + CH, 0x12D7C8F5);
            g.fill(X + Math.round(36 * s), Y - CH, X + CW - Math.round(72 * s), Y, 0x12D7C8F5);
        }
        // Ebenen: Insel, ferne Hügel + Burg (mit Dunst), mittlere Hügel
        Layers u = uploaded;
        if (u != null && textures != null) {
            for (int i = 0; i < 3; i++) {
                drawLayer(g, u, i, t, s, animate);
                if (i == 1) { // Dunst über den fernen Hügeln
                    int fy = Math.round(sh * .66f), fh = Math.round(sh * .08f);
                    g.fillGradient(0, fy, sw, fy + fh / 2, 0x008C64A0, 0x1A8C64A0);
                    g.fillGradient(0, fy + fh / 2, sw, fy + fh, 0x1A8C64A0, 0x008C64A0);
                }
            }
        }
        // Funken (Lila/Pink, steigen in 16–34 s auf)
        Rng rp = new Rng(9);
        for (int i = 0; i < 70 * 6; i++) rp.next(); // gleiche Reihenfolge wie in der Vorschau: erst die Sterne
        for (int i = 0; i < 26; i++) {
            boolean pink = rp.next() < .45f;
            int size = rp.next() < .3f ? 4 : 2;
            float x = rp.next() * wd, y = 670 + rp.next() * 430, sx = (rp.next() - .5f) * 80, rise = -260 - rp.next() * 320;
            float dur = (16 + rp.next() * 18) * 1000, delay = rp.next() * 30 * 1000;
            float p = ((t + delay) / dur) % 1f;
            float op = p < .15f ? p / .15f * .7f : p < .85f ? .7f - (p - .15f) / .7f * .25f : .45f * (1 - (p - .85f) / .15f);
            float ox = p < .5f ? -.5f * sx * (p / .5f) : -.5f * sx + 1.5f * sx * ((p - .5f) / .5f);
            float oy = rise * p;
            int c = pink ? 0xF09AD2 : 0xB39AE8;
            int X = Math.round((x + ox) * s), Y = Math.round((y + oy) * s), S = Math.max(1, Math.round(size * s));
            int halo = Math.max(1, Math.round(3 * s));
            g.fill(X - halo, Y - halo, X + S + halo, Y + S + halo, ((Math.round(op * 0.18f * 255)) << 24) | c);
            g.fill(X, Y, X + S, Y + S, ((Math.round(op * 255)) << 24) | c);
        }
        if (u != null && textures != null) drawLayer(g, u, 3, t, s, animate);
        // Vignette
        Bake.Baked vig = vignetteTexture(sw, sh);
        if (vig != null) Tex.drawTinted(g, vig.tex, 0, 0, vig.w * 4, vig.h * 4, 0, 0, vig.w, vig.h, vig.w, vig.h, 0xFFFFFFFF);
    }

    private static int[] offset(float d, float period, float t, float s) {
        float drift = period > 0 ? (float) Math.sin(t / 1000.0 * 2 * Math.PI / period + d * 3) * 130 * d : 0;
        return new int[]{Math.round((drift - mx * d * 36) * s), Math.round(-my * d * 16 * s)};
    }

    private static void drawLayer(GuiGraphics g, Layers u, int i, float t, float s, boolean animate) {
        int[] o = offset(DEPTH[i], animate ? PERIOD[i] : 0, t, s);
        int x = Math.round(-OVER * s) + o[0];
        int y = u.top[i] * u.up + o[1];
        Tex.drawTinted(g, textures[i], x, y, u.w[i] * u.up, u.h[i] * u.up, 0, 0, u.w[i], u.h[i], u.w[i], u.h[i], 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ weiche Verläufe (Viertel-Auflösung, ungefiltert reicht)

    private static Bake.Baked glowTexture(int sw, int sh) {
        final int w = Math.max(8, Math.round(sw * .8f / 4)), h = Math.max(8, Math.round(sh * .34f / 4));
        return Bake.painted("tbglow" + w + "x" + h, new java.util.function.Supplier<BufferedImage>() {
            public BufferedImage get() {
                // radial-gradient(ellipse 50% 45%, rgba(240,150,200,.22), rgba(170,120,230,.08) 55%, transparent 75%)
                BufferedImage img = Bake.image(w, h);
                float rx = w * .5f, ry = h * .45f;
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        float dx = (x + .5f - w / 2f) / rx, dy = (y + .5f - h / 2f) / ry;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        int c;
                        if (d < .55f) c = Draw.mix(0x38F096C8, 0x14AA78E6, d / .55f);
                        else if (d < .75f) c = Draw.mix(0x14AA78E6, 0x00AA78E6, (d - .55f) / .2f);
                        else continue;
                        img.setRGB(x, y, c);
                    }
                return img;
            }
        });
    }

    private static Bake.Baked vignetteTexture(int sw, int sh) {
        final int w = (sw + 3) / 4, h = (sh + 3) / 4;
        return Bake.painted("tbvig" + w + "x" + h, new java.util.function.Supplier<BufferedImage>() {
            public BufferedImage get() {
                // radial-gradient(ellipse 72% 68% at 50% 48%, transparent 48%, rgba(4,3,12,.66)) + oben/unten dunkler
                BufferedImage img = Bake.image(w, h);
                float rx = w * .72f, ry = h * .68f;
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        float dx = (x + .5f - w * .5f) / rx, dy = (y + .5f - h * .48f) / ry;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        float a = d <= .48f ? 0 : Math.min(1, (d - .48f) / .52f) * .66f;
                        float fy = (y + .5f) / h;
                        float lin = fy < .18f ? .3f * (1 - fy / .18f) : fy > .82f ? .4f * ((fy - .82f) / .18f) : 0;
                        a = 1 - (1 - a) * (1 - lin);
                        int v = Math.round(a * 255);
                        if (v > 0) img.setRGB(x, y, (v << 24) | 0x04030C);
                    }
                return img;
            }
        });
    }

    /**
     * Abdunkelung + Vignette über dem Minecraft-Panorama (radial: Mitte 20 %, Rand 78 % Dunkel-Violett).
     */
    public static void panoramaShade(GuiGraphics g, int sw, int sh) {
        final int w = (sw + 3) / 4, h = (sh + 3) / 4;
        Bake.Baked b = Bake.painted("panovig" + w + "x" + h, new java.util.function.Supplier<BufferedImage>() {
            public BufferedImage get() {
                BufferedImage img = Bake.image(w, h);
                float rx = w * .70f, ry = h * .66f;
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        float dx = (x + .5f - w * .5f) / rx, dy = (y + .5f - h * .5f) / ry;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        float a = d <= .3f ? .2f : .2f + Math.min(1, (d - .3f) / .7f) * .58f;
                        img.setRGB(x, y, (Math.round(a * 255) << 24) | 0x06040E);
                    }
                return img;
            }
        });
        if (b != null) Tex.drawTinted(g, b.tex, 0, 0, b.w * 4, b.h * 4, 0, 0, b.w, b.h, b.w, b.h, 0xFFFFFFFF);
    }

    /** „Einfarbig dunkel“: Verlauf #100D1F → #0A0814 mit leichtem Lila-Schein oben */
    public static void solid(GuiGraphics g, int sw, int sh) {
        g.fillGradient(0, 0, sw, sh, 0xFF100D1F, 0xFF0A0814);
        final int w = (sw + 3) / 4, h = (sh + 3) / 4;
        Bake.Baked b = Bake.painted("solidglow" + w + "x" + h, new java.util.function.Supplier<BufferedImage>() {
            public BufferedImage get() {
                // radial-gradient(ellipse 60% 55% at 50% 40%, rgba(155,92,255,.10), transparent 70%)
                BufferedImage img = Bake.image(w, h);
                float rx = w * .6f, ry = h * .55f;
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        float dx = (x + .5f - w * .5f) / rx, dy = (y + .5f - h * .4f) / ry;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        if (d >= .7f) continue;
                        int v = Math.round((1 - d / .7f) * .10f * 255);
                        if (v > 0) img.setRGB(x, y, (v << 24) | 0x9B5CFF);
                    }
                return img;
            }
        });
        if (b != null) Tex.drawTinted(g, b.tex, 0, 0, b.w * 4, b.h * 4, 0, 0, b.w, b.h, b.w, b.h, 0xFFFFFFFF);
    }
}
