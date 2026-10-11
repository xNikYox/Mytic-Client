package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Tex;
import de.myticlegacy.client.compat.GuiGraphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Hintergrund des Mytic-Hauptmenüs im Design des Launchers (Sonnenuntergang, Mitternacht, Nether …): Himmel,
 * Sonne oder Mond, Bergkette mit Neon-Kante, schwebender Würfel und Neon-Gitter – wie die Startseite des Launchers.
 * Die Ebenen werden einmal pro Fenstergröße (im Hintergrund) gezeichnet und mit Maus-Versatz angezeigt; Sterne und
 * Gitterlinien sind live animiert. Das Design kommt vom Launcher (-Dmytic.launcherTheme).
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class ThemeScene {
    private static final class Palette {
        final boolean moon;
        final int c1, c2, c3, bgGlow, bgMid, bgDeep, sun1, sun2, sun3, ridgeTop, ridgeBottom, cubeTop1, cubeTop2, cubeLeft1, cubeLeft2, cubeRight1, cubeRight2;

        Palette(boolean moon, int... c) {
            this.moon = moon;
            c1 = c[0]; c2 = c[1]; c3 = c[2]; bgGlow = c[3]; bgMid = c[4]; bgDeep = c[5]; sun1 = c[6]; sun2 = c[7]; sun3 = c[8];
            ridgeTop = c[9]; ridgeBottom = c[10]; cubeTop1 = c[11]; cubeTop2 = c[12]; cubeLeft1 = c[13]; cubeLeft2 = c[14]; cubeRight1 = c[15]; cubeRight2 = c[16];
        }
    }

    private static final Map<String, Palette> THEMES = new HashMap<String, Palette>();

    private static void theme(String id, boolean moon, int... colors) {
        THEMES.put(id, new Palette(moon, colors));
    }

    static {
        // aus launcher/src/renderer/neon.css übernommen
        theme("sunset", false, 0x22E5FF, 0xA259FF, 0xFF2BD6, 0x1A0B33, 0x07040F, 0x030208, 0xFFE66B, 0xFF7A59, 0xFF2BD6, 0x1A0F33, 0x07050F, 0x7DF9FF, 0x22B8FF, 0xB56BFF, 0x5B1FD1, 0xFF4FD8, 0x8F1A9E);
        theme("midnight", true, 0x7DD3FC, 0x6366F1, 0xA5B4FC, 0x0B1533, 0x050914, 0x02040A, 0xF1F5F9, 0xCBD5E1, 0x94A3B8, 0x0F1A3A, 0x050914, 0xC7D2FE, 0x818CF8, 0x6366F1, 0x312E81, 0x38BDF8, 0x0C4A6E);
        theme("ocean", false, 0x2DD4BF, 0x0EA5E9, 0x22D3EE, 0x06283A, 0x031520, 0x020A10, 0xFEF08A, 0xFDBA74, 0x2DD4BF, 0x0B3A4A, 0x031520, 0x99F6E4, 0x2DD4BF, 0x0EA5E9, 0x075985, 0x22D3EE, 0x0E7490);
        theme("forest", false, 0x86EFAC, 0x22C55E, 0xFACC15, 0x0F2A16, 0x06140A, 0x030A05, 0xFEF9C3, 0xFDE047, 0xF59E0B, 0x14532D, 0x052E16, 0x86EFAC, 0x22C55E, 0x92400E, 0x451A03, 0xA16207, 0x713F12);
        theme("nether", false, 0xFB923C, 0xEF4444, 0xFACC15, 0x3A0A0A, 0x180404, 0x0A0202, 0xFDE68A, 0xF97316, 0xDC2626, 0x450A0A, 0x1C0505, 0xFCA5A5, 0xEF4444, 0x991B1B, 0x450A0A, 0xB91C1C, 0x7F1D1D);
        theme("end", true, 0xFDE68A, 0xA855F7, 0xD946EF, 0x1E0B33, 0x0C0418, 0x05020C, 0xFEFCE8, 0xF5F3C4, 0xD6D3A0, 0x2E1065, 0x0C0418, 0xA855F7, 0x581C87, 0x3B0764, 0x1E0B33, 0x6B21A8, 0x2E1065);
        theme("ice", false, 0xE0F2FE, 0x7DD3FC, 0xA5F3FC, 0x0C2A3D, 0x061826, 0x030C14, 0xF0F9FF, 0xBAE6FD, 0x7DD3FC, 0x1E3A5F, 0x0C1A2B, 0xF0F9FF, 0xBAE6FD, 0x7DD3FC, 0x0369A1, 0xBAE6FD, 0x0284C7);
        theme("sakura", false, 0xFBCFE8, 0xF472B6, 0xFDA4AF, 0x3A1028, 0x1A0612, 0x0C0308, 0xFFF1F2, 0xFDA4AF, 0xF472B6, 0x4A1D35, 0x1A0612, 0xFBCFE8, 0xF472B6, 0xBE185D, 0x831843, 0xEC4899, 0x9D174D);
        theme("gold", false, 0xFDE68A, 0xEAB308, 0xF59E0B, 0x2A2008, 0x120D03, 0x080601, 0xFFFBEB, 0xFDE68A, 0xF59E0B, 0x3D2E0A, 0x120D03, 0xFEF08A, 0xEAB308, 0xA16207, 0x713F12, 0xCA8A04, 0x854D0E);
        theme("matrix", true, 0x4ADE80, 0x22C55E, 0x86EFAC, 0x052E16, 0x02120A, 0x010804, 0xD9F99D, 0x4ADE80, 0x15803D, 0x064E3B, 0x02120A, 0x86EFAC, 0x22C55E, 0x15803D, 0x052E16, 0x16A34A, 0x14532D);
        theme("mono", true, 0xE5E7EB, 0x9CA3AF, 0xF9FAFB, 0x1F2937, 0x0B0F14, 0x050608, 0xF9FAFB, 0xD1D5DB, 0x9CA3AF, 0x1F2937, 0x0B0F14, 0xF3F4F6, 0x9CA3AF, 0x6B7280, 0x374151, 0x9CA3AF, 0x4B5563);
    }

    /** Gewähltes Launcher-Design oder null (Start ohne Mytic Launcher bzw. älterer Launcher). */
    public static String theme() {
        String id = System.getProperty("mytic.launcherTheme", "").trim();
        return THEMES.containsKey(id) ? id : null;
    }

    // ------------------------------------------------------------------------------------------ Zustand

    private static final int LAYERS = 4; // 0 Himmel, 1 Sonne/Mond, 2 Berge und Boden, 3 Würfel
    private static final float[] DEPTH = {0f, 6f, 14f, 28f};
    private static String key;
    /** fertig gezeichnete Ebenen als PNG (im Hintergrund kodiert) und ihre Größe */
    private static volatile byte[][] rendered;
    private static volatile int renderedW, renderedH;
    private static volatile String renderedKey;
    private static boolean rendering;
    private static Object[] textures;
    private static int texW, texH, margin;
    private static final long START = System.currentTimeMillis();
    private static float mx = 0.5f, my = 0.5f;

    private ThemeScene() {
    }

    /** Szene über den ganzen Bildschirm zeichnen (GUI-Koordinaten). Gibt false zurück, solange sie noch entsteht. */
    public static boolean draw(GuiGraphics g, int width, int height, int mouseX, int mouseY) {
        String id = theme();
        if (id == null) return false;
        Palette p = THEMES.get(id);
        int scale = Math.max(1, Compat.guiScale());
        int pw = width * scale, ph = height * scale;
        String want = id + ":" + pw + "x" + ph;
        if (!want.equals(key)) {
            key = want;
            request(p, pw, ph, want);
        }
        if (rendered != null && want.equals(renderedKey) && (textures == null || !want.equals(uploadedKey))) upload(scale);
        // Grundfarbe, falls die Bilder noch nicht fertig sind
        g.fillGradient(0, 0, width, height, 0xFF000000 | p.bgGlow, 0xFF000000 | p.bgDeep);
        if (textures == null || !want.equals(uploadedKey)) return true;

        // Maus-Versatz weich nachführen
        mx += ((float) mouseX / Math.max(1, width) - mx) * 0.08f;
        my += ((float) mouseY / Math.max(1, height) - my) * 0.08f;
        float t = (System.currentTimeMillis() - START) / 1000f;
        float s = height / 420f;
        float m = (float) margin / scale;
        for (int i = 0; i < LAYERS; i++) {
            float dx = -(mx - 0.5f) * DEPTH[i] * s;
            float dy = -(my - 0.5f) * DEPTH[i] * 0.6f * s + (i == 3 ? (float) Math.sin(t * 1.2) * 4 * s : 0);
            Gfx.push(g);
            Gfx.translate(g, -m + dx, dy);
            Tex.draw(g, textures[i], 0, 0, Math.round(texW / (float) scale), Math.round(texH / (float) scale), texW, texH);
            Gfx.pop(g);
            if (i == 0) stars(g, width, height, t, p);
            if (i == 2) grid(g, width, height, t, p, dx, s);
        }
        return true;
    }

    private static String uploadedKey;

    private static void upload(int scale) {
        if (textures != null) for (Object tex : textures) Tex.release(tex);
        byte[][] pngs = rendered;
        textures = new Object[LAYERS];
        for (int i = 0; i < LAYERS; i++) textures[i] = Tex.upload("scene_" + i, pngs[i]);
        texW = renderedW;
        texH = renderedH;
        uploadedKey = renderedKey;
    }

    private static synchronized void request(final Palette p, final int pw, final int ph, final String want) {
        if (rendering) return;
        rendering = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedImage[] images = render(p, pw, ph);
                    byte[][] pngs = new byte[images.length][];
                    for (int i = 0; i < images.length; i++) {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(images[i], "png", out);
                        pngs[i] = out.toByteArray();
                    }
                    synchronized (ThemeScene.class) {
                        rendered = pngs;
                        renderedW = images[0].getWidth();
                        renderedH = images[0].getHeight();
                        renderedKey = want;
                    }
                } catch (Throwable e) {
                    MyticClient.LOG.warn("Hauptmenü-Szene nicht gezeichnet: " + e);
                } finally {
                    synchronized (ThemeScene.class) {
                        rendering = false;
                    }
                    // Fenstergröße hat sich währenddessen geändert: nochmal
                    if (!want.equals(key)) key = null;
                }
            }
        }, "Mytic Hauptmenü-Szene");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------------------------------ Live-Teile

    private static float rnd(int i) {
        double v = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    private static void stars(GuiGraphics g, int width, int height, float t, Palette p) {
        for (int i = 0; i < 90; i++) {
            int x = (int) (rnd(i) * width);
            int y = (int) (rnd(i + 300) * height * 0.62f);
            float a = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(t * (1.2 + rnd(i + 600) * 2) + rnd(i + 900) * 6.28));
            int c = i % 9 == 0 ? p.c1 : 0xFFFFFF;
            g.fill(x, y, x + 1, y + 1, ((int) (a * 200) << 24) | c);
        }
    }

    /** Waagerechte Gitterlinien laufen auf den Betrachter zu (die senkrechten sind im Bild). */
    private static void grid(GuiGraphics g, int width, int height, float t, Palette p, float dx, float s) {
        int horizon = Math.round(330 * s);
        int floor = height - horizon;
        if (floor <= 4) return;
        float phase = (t * 0.35f) % 1f;
        for (int i = 0; i < 9; i++) {
            float k = (i + phase) / 9f;
            float depth = k * k;
            int y = horizon + Math.round(depth * floor);
            int alpha = Math.round(30 + 120 * depth);
            g.fill(0, y, width, y + 1, (alpha << 24) | p.c2);
        }
        g.fill(0, horizon, width, horizon + 1, 0xC0000000 | p.c1);
    }

    // ------------------------------------------------------------------------------------------ Bilder zeichnen

    private static Color col(int rgb, float alpha) {
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, Math.round(alpha * 255))));
    }

    private static Color col(int rgb) {
        return col(rgb, 1f);
    }

    private static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Vier Ebenen (Breite mit Rand für den Maus-Versatz). Koordinaten wie im Launcher (900x420, rechts bündig). */
    static BufferedImage[] render(Palette p, int pw, int ph) {
        final float s = ph / 420f;
        margin = Math.round(30 * s);
        final int w = pw + 2 * margin;
        final float right = w - margin; // rechter Bildschirmrand im Bild
        BufferedImage[] out = new BufferedImage[LAYERS];
        for (int i = 0; i < LAYERS; i++) out[i] = new BufferedImage(w, ph, BufferedImage.TYPE_INT_ARGB);

        // 0: Himmel
        Graphics2D sky = gfx(out[0]);
        sky.setPaint(new LinearGradientPaint(0, 0, 0, ph, new float[]{0f, 0.55f, 1f}, new Color[]{col(p.bgGlow), col(p.bgMid), col(p.bgDeep)}));
        sky.fill(new Rectangle2D.Float(0, 0, w, ph));
        sky.dispose();

        // 1: Sonne oder Mond mit Schein
        Graphics2D far = gfx(out[1]);
        float cx = right - (900 - 640) * s;
        float glowR = 230 * s;
        far.setPaint(new RadialGradientPaint(cx, 190 * s, glowR, new float[]{0f, 1f}, new Color[]{col(p.sun3, 0.55f), col(p.sun3, 0f)}));
        far.fill(new Ellipse2D.Float(cx - glowR, 190 * s - glowR, 2 * glowR, 2 * glowR));
        if (!p.moon) {
            float r = 118 * s, cy = 200 * s;
            Area sun = new Area(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
            float[][] cuts = {{214, 5}, {232, 7}, {252, 9}, {274, 11}};
            for (float[] c : cuts) sun.subtract(new Area(new Rectangle2D.Float(cx - r - 2, c[0] * s, 2 * r + 4, c[1] * s)));
            far.setPaint(new LinearGradientPaint(0, cy - r, 0, cy + r, new float[]{0f, 0.45f, 1f}, new Color[]{col(p.sun1), col(p.sun2), col(p.sun3)}));
            far.fill(sun);
        } else {
            float r = 96 * s, cy = 196 * s;
            far.setPaint(new RadialGradientPaint(cx - r + 0.76f * r, cy - r + 0.70f * r, 1.5f * r, new float[]{0f, 0.7f, 1f}, new Color[]{col(p.sun1), col(p.sun2), col(p.sun3)}));
            far.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
            float[][] craters = {{610, 170, 16, 0.35f}, {672, 214, 11, 0.3f}, {648, 150, 7, 0.3f}};
            for (float[] c : craters) {
                float x = right - (900 - c[0]) * s, y = c[1] * s, cr = c[2] * s;
                far.setColor(col(p.sun3, c[3]));
                far.fill(new Ellipse2D.Float(x - cr, y - cr, 2 * cr, 2 * cr));
            }
        }
        far.dispose();

        // 2: Bergkette (Muster des Launchers, nach links wiederholt) und Boden mit Neon-Gitter
        Graphics2D mid = gfx(out[2]);
        float[] px = {380, 470, 520, 600, 660, 720, 790, 850, 900};
        float[] py = {300, 222, 258, 176, 240, 196, 262, 214, 250};
        Path2D.Float ridge = new Path2D.Float();
        Path2D.Float edge = new Path2D.Float();
        Path2D.Float inner = new Path2D.Float();
        int repeats = (int) Math.ceil(w / (520 * s)) + 1;
        boolean first = true;
        for (int k = repeats; k >= 0; k--) {
            for (int i = (k == repeats ? 0 : 1); i < px.length; i++) {
                float x = right - (900 - px[i] + 520 * k) * s;
                float y = py[i] * s;
                if (first) {
                    ridge.moveTo(x, 330 * s);
                    ridge.lineTo(x, y);
                    edge.moveTo(x, y);
                    first = false;
                } else {
                    ridge.lineTo(x, y);
                    edge.lineTo(x, y);
                }
                if (i == 1 || i == 3 || i == 5 || i == 7) {
                    float[] foot = {500, 590, 740, 830};
                    float fx = right - (900 - foot[(i - 1) / 2] + 520 * k) * s;
                    inner.moveTo(x, y);
                    inner.lineTo(fx, 300 * s);
                }
            }
        }
        ridge.lineTo(w + 10, 250 * s);
        ridge.lineTo(w + 10, 330 * s);
        ridge.closePath();
        mid.setPaint(new GradientPaint(0, 176 * s, col(p.ridgeTop), 0, 330 * s, col(p.ridgeBottom)));
        mid.fill(ridge);
        // Boden
        float horizon = 330 * s;
        mid.setPaint(new GradientPaint(0, horizon, col(p.bgMid), 0, ph, col(p.bgDeep)));
        mid.fill(new Rectangle2D.Float(0, horizon, w, ph - horizon));
        // senkrechte Gitterlinien laufen zum Fluchtpunkt
        mid.setStroke(new BasicStroke(Math.max(1f, 1.2f * s)));
        float vx = right - 450 * s;
        for (int i = -24; i <= 24; i++) {
            float bottomX = vx + i * 70 * s;
            mid.setColor(col(p.c2, 0.35f));
            mid.draw(new java.awt.geom.Line2D.Float(vx + i * 14 * s, horizon, bottomX + i * 40 * s, ph + 40 * s));
        }
        // Neon-Kante mit Schein
        float[][] glow = {{12, 0.06f}, {7, 0.12f}, {4, 0.25f}};
        for (float[] gl : glow) {
            mid.setStroke(new BasicStroke(gl[0] * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            mid.setColor(col(p.c1, gl[1]));
            mid.draw(edge);
        }
        mid.setStroke(new BasicStroke(2 * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        mid.setColor(col(p.c1));
        mid.draw(edge);
        mid.setStroke(new BasicStroke(1.5f * s));
        mid.setColor(col(p.c3, 0.7f));
        mid.draw(inner);
        mid.dispose();

        // 3: schwebender Würfel und Rauten
        Graphics2D near = gfx(out[3]);
        float bx = right - (900 - 780) * s, by = 96 * s;
        Path2D.Float top = new Path2D.Float();
        top.moveTo(bx, by); top.lineTo(bx + 46 * s, by - 26 * s); top.lineTo(bx + 92 * s, by); top.lineTo(bx + 46 * s, by + 26 * s); top.closePath();
        Path2D.Float left = new Path2D.Float();
        left.moveTo(bx, by); left.lineTo(bx + 46 * s, by + 26 * s); left.lineTo(bx + 46 * s, by + 80 * s); left.lineTo(bx, by + 54 * s); left.closePath();
        Path2D.Float rightFace = new Path2D.Float();
        rightFace.moveTo(bx + 92 * s, by); rightFace.lineTo(bx + 46 * s, by + 26 * s); rightFace.lineTo(bx + 46 * s, by + 80 * s); rightFace.lineTo(bx + 92 * s, by + 54 * s); rightFace.closePath();
        near.setPaint(new GradientPaint(bx, by - 26 * s, col(p.cubeTop1), bx + 92 * s, by + 26 * s, col(p.cubeTop2)));
        near.fill(top);
        near.setPaint(new GradientPaint(0, by, col(p.cubeLeft1), 0, by + 80 * s, col(p.cubeLeft2)));
        near.fill(left);
        near.setPaint(new GradientPaint(0, by, col(p.cubeRight1), 0, by + 80 * s, col(p.cubeRight2)));
        near.fill(rightFace);
        // nur die untere Raute – die obere säße im Hauptmenü direkt neben dem Mytic-Emblem
        float[][] diamonds = {{560, 330, 7, 14}};
        int[] diamondColors = {p.c3};
        for (int i = 0; i < diamonds.length; i++) {
            float[] d = diamonds[i];
            float x = right - (900 - d[0]) * s, y = d[1] * s;
            Path2D.Float dia = new Path2D.Float();
            dia.moveTo(x, y); dia.lineTo(x + d[2] * s, y + d[3] * s); dia.lineTo(x, y + 2 * d[3] * s); dia.lineTo(x - d[2] * s, y + d[3] * s); dia.closePath();
            near.setColor(col(diamondColors[i]));
            near.fill(dia);
        }
        near.dispose();
        return out;
    }
}
