package de.myticlegacy.client.gui.ui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Tex;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kleine Texturen für das neue Design, einmal gezeichnet und zwischengespeichert: geglättete Kreise und Ringe
 * (Ecken der abgerundeten Flächen, genau auf echte Pixel), weiche Schatten, runde Köpfe, Wortmarke, Emblem und
 * Splash-Text (Inter ExtraBold über Java2D). Weiße Masken werden beim Zeichnen eingefärbt (Tex.drawTinted).
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Bake {
    /** Fertige Textur: Kennung, Größe in Pixeln, Ursprung (ox, oy) innerhalb des Bildes */
    public static final class Baked {
        public final Object tex;
        public final int w, h, ox, oy;
        /** frei verwendbar (z. B. Vorschub-Breite eines Textes) */
        public final int extra;

        Baked(Object tex, int w, int h, int ox, int oy, int extra) {
            this.tex = tex;
            this.w = w;
            this.h = h;
            this.ox = ox;
            this.oy = oy;
            this.extra = extra;
        }
    }

    private static int seq;
    private static final Map<String, Baked> CACHE = new LinkedHashMap<String, Baked>(64, 0.75f, true);
    private static final int MAX = 160;
    private static final Map<String, Font> FONTS = new HashMap<String, Font>();

    private Bake() {
    }

    // ------------------------------------------------------------------ Grundlagen

    /** Bild als Textur hochladen (PNG über die vorhandene Compat-Schicht) */
    public static Baked upload(BufferedImage img, int ox, int oy, int extra) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            Object tex = Tex.upload("ui/b" + (seq++), out.toByteArray());
            return new Baked(tex, img.getWidth(), img.getHeight(), ox, oy, extra);
        } catch (Exception e) {
            MyticClient.LOG.warn("Mytic-UI: Textur nicht erstellt: " + e);
            return null;
        }
    }

    public static void release(Baked b) {
        if (b != null && b.tex != null) {
            try {
                Tex.release(b.tex);
            } catch (RuntimeException ignored) {
                // Textur schon weg (z. B. nach Neuladen der Ressourcen)
            }
        }
    }

    interface Maker {
        Baked make();
    }

    /** Aus dem Zwischenspeicher oder neu (älteste Einträge werden freigegeben) */
    static Baked cached(String key, Maker maker) {
        Baked b = CACHE.get(key);
        if (b != null) return b;
        b = maker.make();
        if (b == null) return null;
        CACHE.put(key, b);
        if (CACHE.size() > MAX) {
            Iterator<Map.Entry<String, Baked>> it = CACHE.entrySet().iterator();
            Map.Entry<String, Baked> eldest = it.next();
            release(eldest.getValue());
            it.remove();
        }
        return b;
    }

    /** Alles freigeben (z. B. Fenstergröße stark geändert) */
    public static void clear() {
        for (Baked b : CACHE.values()) release(b);
        CACHE.clear();
    }

    static BufferedImage image(int w, int h) {
        return new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
    }

    static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        return g;
    }

    static Color col(int argb) {
        return new Color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    // ------------------------------------------------------------------ Kreise, Ringe, Schatten

    /** Deckung eines Pixels (i, j) für einen Kreis um (c, c) mit Radius r (4×4 Abtastpunkte) */
    private static float cover(int i, int j, float c, float r) {
        int n = 0;
        float r2 = r * r;
        for (int a = 0; a < 4; a++) {
            float y = j + (a + 0.5f) / 4f - c;
            for (int b = 0; b < 4; b++) {
                float x = i + (b + 0.5f) / 4f - c;
                if (x * x + y * y <= r2) n++;
            }
        }
        return n / 16f;
    }

    /** Weiße, geglättete Kreisscheibe 2R × 2R (Ecken für abgerundete Flächen) */
    public static Baked disc(final int r) {
        return cached("disc" + r, new Maker() {
            public Baked make() {
                BufferedImage img = image(2 * r, 2 * r);
                for (int j = 0; j < 2 * r; j++)
                    for (int i = 0; i < 2 * r; i++) {
                        int a = Math.round(cover(i, j, r, r) * 255);
                        if (a > 0) img.setRGB(i, j, (a << 24) | 0xFFFFFF);
                    }
                return upload(img, 0, 0, 0);
            }
        });
    }

    /** Weißer, geglätteter Ring 2R × 2R mit Stärke t (Ecken für Rahmen) */
    public static Baked ring(final int r, final int t) {
        return cached("ring" + r + "_" + t, new Maker() {
            public Baked make() {
                BufferedImage img = image(2 * r, 2 * r);
                for (int j = 0; j < 2 * r; j++)
                    for (int i = 0; i < 2 * r; i++) {
                        float c = cover(i, j, r, r) - cover(i, j, r, r - t);
                        int a = Math.round(Math.max(0, c) * 255);
                        if (a > 0) img.setRGB(i, j, (a << 24) | 0xFFFFFF);
                    }
                return upload(img, 0, 0, 0);
            }
        });
    }

    /** Weicher Schatten (weiße Maske) einer abgerundeten Fläche w × h mit Weichzeichnung blur; Rand = 2 × blur */
    public static Baked shadow(final int w, final int h, final int r, final int blur) {
        if ((long) (w + 4 * blur) * (h + 4 * blur) > 2_000_000L) return null;
        return cached("shadow" + w + "x" + h + "_" + r + "_" + blur, new Maker() {
            public Baked make() {
                int pad = 2 * blur;
                int W = w + 2 * pad, H = h + 2 * pad;
                float[] a = new float[W * H];
                int rr = Math.min(r, Math.min(w, h) / 2);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        float c = 1f;
                        int cx = x < rr ? rr : x >= w - rr ? w - rr : -1, cy = y < rr ? rr : y >= h - rr ? h - rr : -1;
                        if (cx >= 0 && cy >= 0) {
                            float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                            c = Math.max(0, Math.min(1, rr - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f));
                        }
                        a[(y + pad) * W + x + pad] = c;
                    }
                blur(a, W, H, blur / 2f);
                BufferedImage img = image(W, H);
                for (int i = 0; i < a.length; i++) {
                    int v = Math.round(Math.min(1, a[i]) * 255);
                    if (v > 0) img.setRGB(i % W, i / W, (v << 24) | 0xFFFFFF);
                }
                return upload(img, pad, pad, 0);
            }
        });
    }

    /** Annähernd gaußsche Weichzeichnung (3 × Kastenfilter) einer Deckungs-Karte */
    static void blur(float[] a, int w, int h, float sigma) {
        if (sigma < 0.5f) return;
        int r = Math.max(1, Math.round((float) Math.sqrt(12 * sigma * sigma / 3 + 1) / 2f));
        float[] tmp = new float[a.length];
        for (int pass = 0; pass < 3; pass++) {
            box(a, tmp, w, h, r, true);
            box(tmp, a, w, h, r, false);
        }
    }

    private static void box(float[] src, float[] dst, int w, int h, int r, boolean horizontal) {
        int n = horizontal ? w : h, lines = horizontal ? h : w;
        float inv = 1f / (2 * r + 1);
        for (int l = 0; l < lines; l++) {
            float sum = 0;
            for (int i = -r; i <= r; i++) sum += at(src, w, h, horizontal, l, i, n);
            for (int i = 0; i < n; i++) {
                int idx = horizontal ? l * w + i : i * w + l;
                dst[idx] = sum * inv;
                sum += at(src, w, h, horizontal, l, i + r + 1, n) - at(src, w, h, horizontal, l, i - r, n);
            }
        }
    }

    private static float at(float[] a, int w, int h, boolean horizontal, int line, int i, int n) {
        if (i < 0 || i >= n) return 0;
        return horizontal ? a[line * w + i] : a[i * w + line];
    }

    // ------------------------------------------------------------------ Köpfe

    /** Spielerkopf (8 × 8 ARGB) pixelgenau vergrößert, mit geglätteter runder Maske (radius in Pixeln) */
    public static Baked head(final int[] pixels, final int size, final int radius) {
        if (pixels == null || pixels.length < 64) return null;
        return cached("head" + java.util.Arrays.hashCode(pixels) + "_" + size + "_" + radius, new Maker() {
            public Baked make() {
                BufferedImage img = image(size, size);
                int rr = Math.min(radius, size / 2);
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++) {
                        float c = 1f;
                        int cx = x < rr ? rr : x >= size - rr ? size - rr : -1, cy = y < rr ? rr : y >= size - rr ? size - rr : -1;
                        if (cx >= 0 && cy >= 0) {
                            int n = 0;
                            for (int a = 0; a < 4; a++)
                                for (int b = 0; b < 4; b++) {
                                    float dx = x + (b + 0.5f) / 4f - cx, dy = y + (a + 0.5f) / 4f - cy;
                                    if (dx * dx + dy * dy <= rr * rr) n++;
                                }
                            c = n / 16f;
                        }
                        if (c <= 0) continue;
                        int p = pixels[(y * 8 / size) * 8 + (x * 8 / size)] | 0xFF000000;
                        img.setRGB(x, y, (Math.round(c * 255) << 24) | (p & 0xFFFFFF));
                    }
                return upload(img, 0, 0, 0);
            }
        });
    }

    // ------------------------------------------------------------------ Schrift (Java2D) für Grafik-Text

    /** Inter-Schnitt aus den Mod-Ressourcen (assets/myticclient/font/…) */
    static Font font(String file) {
        Font f = FONTS.get(file);
        if (f != null) return f;
        try {
            InputStream in = Bake.class.getResourceAsStream("/assets/myticclient/font/" + file);
            if (in == null) throw new IllegalStateException("fehlt");
            try {
                f = Font.createFont(Font.TRUETYPE_FONT, in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("Mytic-UI: Schrift " + file + " nicht geladen: " + e);
            f = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        }
        FONTS.put(file, f);
        return f;
    }

    /** Leuchten/Schatten hinter Grafik-Text: Versatz, Weichzeichnung (CSS-Blur-Radius), Farbe */
    public static final class Shadow {
        final float dx, dy, blur;
        final int color;

        public Shadow(float dx, float dy, float blur, int color) {
            this.dx = dx;
            this.dy = dy;
            this.blur = blur;
            this.color = color;
        }
    }

    /**
     * Text als Bild (Inter ExtraBold): Größe px (Em), Laufweite tracking (in Em, z. B. -0.045), Farbe, Schatten.
     * Ergebnis: ox/oy = Lage von Textanfang/Oberkante der Großbuchstaben im Bild, extra = Vorschub-Breite.
     */
    public static Baked text(final String text, final float px, final float tracking, final int color, final Shadow... shadows) {
        StringBuilder key = new StringBuilder("text").append(text).append('|').append(px).append('|').append(tracking).append('|').append(color);
        for (Shadow s : shadows) key.append('|').append(s.dx).append(',').append(s.dy).append(',').append(s.blur).append(',').append(s.color);
        return cached(key.toString(), new Maker() {
            public Baked make() {
                Image t = textImage(text, px, tracking, color, shadows);
                return upload(t.img, t.ox, t.oy, t.extra);
            }
        });
    }

    /** Bild samt Ursprung (für upload und Tests) */
    static final class Image {
        final BufferedImage img;
        final int ox, oy, extra;

        Image(BufferedImage img, int ox, int oy, int extra) {
            this.img = img;
            this.ox = ox;
            this.oy = oy;
            this.extra = extra;
        }
    }

    static Image textImage(String text, float px, float tracking, int color, Shadow... shadows) {
        Map<TextAttribute, Object> attrs = new HashMap<TextAttribute, Object>();
        attrs.put(TextAttribute.SIZE, px);
        attrs.put(TextAttribute.TRACKING, tracking);
        Font f = font("inter_extrabold.ttf").deriveFont(attrs);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = f.createGlyphVector(frc, text);
        Rectangle2D ink = gv.getVisualBounds();
        float cap = (float) f.createGlyphVector(frc, "H").getVisualBounds().getHeight();
        float advance = (float) f.getStringBounds(text, frc).getWidth() - tracking * px;
        int pad = 2;
        for (Shadow s : shadows) pad = Math.max(pad, (int) Math.ceil(s.blur + Math.max(Math.abs(s.dx), Math.abs(s.dy)) + 2));
        int w = (int) Math.ceil(Math.max(ink.getMaxX(), advance) - Math.min(0, ink.getMinX())) + 2 * pad;
        int h = (int) Math.ceil(ink.getHeight()) + 2 * pad;
        float baseX = pad - (float) Math.min(0, ink.getMinX());
        float baseY = pad - (float) ink.getMinY();
        BufferedImage out = image(w, h);
        Graphics2D g = gfx(out);
        for (Shadow s : shadows) {
            BufferedImage mask = image(w, h);
            Graphics2D mg = gfx(mask);
            mg.setColor(Color.WHITE);
            mg.drawGlyphVector(gv, baseX + s.dx, baseY + s.dy);
            mg.dispose();
            float[] a = new float[w * h];
            for (int i = 0; i < a.length; i++) a[i] = ((mask.getRGB(i % w, i / w) >>> 24) & 255) / 255f;
            blur(a, w, h, s.blur / 2f);
            BufferedImage tinted = image(w, h);
            int ca = (s.color >>> 24) & 255;
            for (int i = 0; i < a.length; i++) {
                int v = Math.round(Math.min(1, a[i]) * ca);
                if (v > 0) tinted.setRGB(i % w, i / w, (v << 24) | (s.color & 0xFFFFFF));
            }
            g.drawImage(tinted, 0, 0, null);
        }
        g.setColor(col(color));
        g.drawGlyphVector(gv, baseX, baseY);
        g.dispose();
        return new Image(out, Math.round(baseX), Math.round(baseY - cap), Math.round(advance));
    }

    // ------------------------------------------------------------------ Emblem

    /** Mytic-Emblem: abgerundetes Quadrat im Akzent-Verlauf, weiße Mondsichel und Pixel-Funkeln, lila Schein */
    public static Baked emblem(final int size) {
        return cached("emblem" + size, new Maker() {
            public Baked make() {
                Image e = emblemImage(size);
                return upload(e.img, e.ox, e.oy, 0);
            }
        });
    }

    static Image emblemImage(int size) {
        int pad = Math.max(4, Math.round(size * 0.45f));
        int W = size + 2 * pad;
        BufferedImage out = image(W, W);
        // Schein: 0 10px 30px -8px rgba(181,108,255,.6) (relativ zu 56 px)
        float k = size / 56f;
        float[] a = new float[W * W];
        float inset = 8 * k, oy = 10 * k;
        for (int y = 0; y < W; y++)
            for (int x = 0; x < W; x++) {
                float lx = x - pad - inset, ly = y - pad - oy - inset, s = size - 2 * inset;
                if (lx >= 0 && ly >= 0 && lx < s && ly < s) a[y * W + x] = 1;
            }
        blur(a, W, W, 15 * k);
        for (int i = 0; i < a.length; i++) {
            int v = Math.round(Math.min(1, a[i]) * 0.6f * 255);
            if (v > 0) out.setRGB(i % W, i / W, (v << 24) | 0xB56CFF);
        }
        Graphics2D g = gfx(out);
        java.awt.geom.RoundRectangle2D body = new java.awt.geom.RoundRectangle2D.Float(pad, pad, size, size, size * 0.6f, size * 0.6f);
        g.setPaint(new java.awt.LinearGradientPaint(pad, pad, pad + size, pad + size, new float[]{0f, 0.5f, 1f},
                new Color[]{col(Tokens.ACC_1), col(Tokens.ACC_MID), col(Tokens.ACC_2)}));
        g.fill(body);
        // Lichtkante oben (inset 0 1px 0 rgba(255,255,255,.35))
        java.awt.Shape old = g.getClip();
        g.setClip(body);
        g.setColor(new Color(255, 255, 255, 90));
        g.fill(new java.awt.geom.Rectangle2D.Float(pad, pad, size, Math.max(1, k)));
        g.setClip(old);
        // Mondsichel: Kreis (inset 22 %) minus versetzter Kreis, um -20° gedreht
        float cx = pad + size / 2f, cy = pad + size / 2f, r = size * 0.28f;
        java.awt.geom.Area moon = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        moon.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(cx - r - size * 0.09f, cy - r - size * 0.03f, 2 * r, 2 * r)));
        moon.transform(java.awt.geom.AffineTransform.getRotateInstance(Math.toRadians(-20), cx, cy));
        g.setColor(Color.WHITE);
        g.fill(moon);
        // Pixel-Funkeln oben rechts
        float s = size * 0.12f, fx = pad + size - size * 0.20f - s, fy = pad + size * 0.18f;
        g.fill(new java.awt.geom.Rectangle2D.Float(Math.round(fx), Math.round(fy), Math.round(s), Math.round(s)));
        float s2 = Math.max(1, s - 2 * k);
        g.setColor(new Color(255, 255, 255, 180));
        g.fill(new java.awt.geom.Rectangle2D.Float(Math.round(fx - size * 0.09f + k), Math.round(fy + size * 0.13f + k), Math.round(s2), Math.round(s2)));
        g.dispose();
        return new Image(out, pad, pad, 0);
    }

    // ------------------------------------------------------------------ freie Bilder (Hintergrund-Verläufe)

    /** Bild, das ein Maler einmal erzeugt (Schlüssel bestimmt die Wiederverwendung) */
    public static Baked painted(String key, final java.util.function.Supplier<BufferedImage> painter) {
        return cached(key, new Maker() {
            public Baked make() {
                return upload(painter.get(), 0, 0, 0);
            }
        });
    }
}
