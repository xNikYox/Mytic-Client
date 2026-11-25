package de.myticlegacy.client.minimap;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Tex;
import de.myticlegacy.client.gui.ui.Bake;
import de.myticlegacy.client.gui.ui.Draw;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Gefüllte Symbole für Karte und Wegpunkte (Spieler-Pfeil, Raute, Haus, Stern …): einmal je Pixelgröße geglättet
 * gebacken, beim Zeichnen eingefärbt, mit dunklem Rand für Lesbarkeit auf jedem Untergrund.
 */
public final class MapIcons {
    /** Wegpunkt-Symbole (Schlüssel) und ihre Namen */
    public static final String[] KEYS = {"diamond", "dot", "home", "star", "flag", "cross", "skull", "heart"};
    public static final String[] LABELS = {"Raute", "Punkt", "Haus", "Stern", "Flagge", "Kreuz", "Totenkopf", "Herz"};

    private MapIcons() {
    }

    public static String label(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return LABELS[i];
        return LABELS[0];
    }

    /** Umriss im 24er-Raster */
    private static Shape shape(String key) {
        switch (key) {
            case "arrow": {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(12, 2);
                p.lineTo(20, 21);
                p.lineTo(12, 16.5f);
                p.lineTo(4, 21);
                p.closePath();
                return p;
            }
            case "dot":
                return new Ellipse2D.Float(5, 5, 14, 14);
            case "home": {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(12, 3);
                p.lineTo(21, 11);
                p.lineTo(18.5f, 11);
                p.lineTo(18.5f, 20);
                p.lineTo(5.5f, 20);
                p.lineTo(5.5f, 11);
                p.lineTo(3, 11);
                p.closePath();
                return p;
            }
            case "star": {
                Path2D.Float p = new Path2D.Float();
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI / 5 * i - Math.PI / 2;
                    float r = i % 2 == 0 ? 10 : 4.3f;
                    float x = (float) (12 + Math.cos(a) * r), y = (float) (12.8 + Math.sin(a) * r);
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                p.closePath();
                return p;
            }
            case "flag": {
                Path2D.Float p = new Path2D.Float();
                p.append(new Rectangle2D.Float(5, 3, 2.6f, 18), false);
                p.moveTo(7.6f, 3.5f);
                p.lineTo(20, 7.5f);
                p.lineTo(7.6f, 12.5f);
                p.closePath();
                return p;
            }
            case "cross": {
                Path2D.Float p = new Path2D.Float();
                p.append(new RoundRectangle2D.Float(9.5f, 3, 5, 18, 2, 2), false);
                p.append(new RoundRectangle2D.Float(3, 9.5f, 18, 5, 2, 2), false);
                return p;
            }
            case "skull": {
                java.awt.geom.Area a = new java.awt.geom.Area(new Ellipse2D.Float(4, 3, 16, 14));
                a.add(new java.awt.geom.Area(new RoundRectangle2D.Float(7.5f, 12, 9, 9, 3, 3)));
                a.subtract(new java.awt.geom.Area(new Ellipse2D.Float(7, 8.5f, 4, 4)));
                a.subtract(new java.awt.geom.Area(new Ellipse2D.Float(13, 8.5f, 4, 4)));
                a.subtract(new java.awt.geom.Area(new Rectangle2D.Float(11.3f, 17, 1.4f, 4)));
                return a;
            }
            case "heart": {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(12, 20.5f);
                p.curveTo(4, 15, 2.5f, 11, 3.5f, 8);
                p.curveTo(4.8f, 4.2f, 10, 3.5f, 12, 7.2f);
                p.curveTo(14, 3.5f, 19.2f, 4.2f, 20.5f, 8);
                p.curveTo(21.5f, 11, 20, 15, 12, 20.5f);
                p.closePath();
                return p;
            }
            default: { // Raute
                Path2D.Float p = new Path2D.Float();
                p.moveTo(12, 2.5f);
                p.lineTo(20.5f, 12);
                p.lineTo(12, 21.5f);
                p.lineTo(3.5f, 12);
                p.closePath();
                return p;
            }
        }
    }

    /** Gebacken: Füllung (weiß) bzw. Rand (weiß), s = Pixelgröße */
    private static Bake.Baked baked(final String key, final int s, final boolean outline) {
        return Bake.painted("mapicon:" + key + ":" + s + (outline ? ":o" : ":f"), () -> {
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(Color.WHITE);
            float k = s / 24f;
            AffineTransform t = AffineTransform.getScaleInstance(k, k);
            Shape sh = t.createTransformedShape(shape(key));
            if (outline) {
                g.setStroke(new BasicStroke(Math.max(1.2f, 3.2f * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(sh);
            } else {
                g.fill(sh);
            }
            g.dispose();
            return img;
        });
    }

    /** Symbol mittig bei (cx, cy) in Einheiten, Kantenlänge size, Farbe color (mit dunklem Rand) */
    public static void draw(GuiGraphics g, String key, float cx, float cy, float size, int color) {
        draw(g, key, cx, cy, size, color, 0xD0000000);
    }

    public static void draw(GuiGraphics g, String key, float cx, float cy, float size, int color, int outlineColor) {
        if ((color >>> 24) == 0) return;
        float k = Draw.begin(g);
        int s = Math.max(6, Math.round(size * k));
        int x = Math.round(cx * k - s / 2f), y = Math.round(cy * k - s / 2f);
        if ((outlineColor >>> 24) != 0) {
            Bake.Baked o = baked(key, s, true);
            if (o != null) Tex.drawTinted(g, o.tex, x, y, s, s, 0, 0, s, s, o.w, o.h, outlineColor);
        }
        Bake.Baked f = baked(key, s, false);
        if (f != null) Tex.drawTinted(g, f.tex, x, y, s, s, 0, 0, s, s, f.w, f.h, color);
        Draw.end(g);
    }

    /** Spieler-Pfeil, um degrees (im Uhrzeigersinn, 0 = nach oben) gedreht */
    public static void arrow(GuiGraphics g, float cx, float cy, float size, float degrees, int color) {
        Gfx.push(g);
        Gfx.translate(g, cx, cy);
        if (degrees != 0) Gfx.rotate(g, degrees);
        draw(g, "arrow", 0, 0, size, color, 0xE0000000);
        Gfx.pop(g);
    }
}
