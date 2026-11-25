package de.myticlegacy.client.gui.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eigene Linien-Icons (24er Raster, 1,8er Strich, runde Enden) aus der UI-Vorschau. Sie werden je Pixelgröße
 * einmal weiß und geglättet in einen Atlas gezeichnet und beim Zeichnen eingefärbt – scharf bei jeder Größe.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Icons {
    private static final Map<String, String> SVG = new LinkedHashMap<String, String>();
    private static final List<String> NAMES = new ArrayList<String>();
    private static final Map<String, Integer> INDEX = new HashMap<String, Integer>();

    private static void icon(String name, String svg) {
        INDEX.put(name, NAMES.size());
        NAMES.add(name);
        SVG.put(name, svg);
    }

    static {
        icon("cube", "<path d=\"M12 3l8 4.5v9L12 21l-8-4.5v-9z\"/><path d=\"M4 7.5l8 4.5 8-4.5M12 12v9\"/>");
        icon("servers", "<rect x=\"4\" y=\"4\" width=\"16\" height=\"7\" rx=\"2\"/><rect x=\"4\" y=\"13\" width=\"16\" height=\"7\" rx=\"2\"/><path d=\"M8 7.5h.01M8 16.5h.01M12 7.5h4M12 16.5h4\"/>");
        icon("grid", "<rect x=\"4\" y=\"4\" width=\"7\" height=\"7\" rx=\"2\"/><rect x=\"13\" y=\"4\" width=\"7\" height=\"7\" rx=\"2\"/><rect x=\"4\" y=\"13\" width=\"7\" height=\"7\" rx=\"2\"/><rect x=\"13\" y=\"13\" width=\"7\" height=\"7\" rx=\"2\"/>");
        icon("gear", "<circle cx=\"12\" cy=\"12\" r=\"3\"/><path d=\"M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8\"/><circle cx=\"12\" cy=\"12\" r=\"6.5\"/>");
        icon("power", "<path d=\"M12 3v8\"/><path d=\"M6.6 6.6a7.5 7.5 0 1 0 10.8 0\"/>");
        icon("play", "<path d=\"M8 5.5v13l10.5-6.5z\"/>");
        icon("users", "<circle cx=\"9\" cy=\"8\" r=\"3.3\"/><path d=\"M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6\"/><path d=\"M15.5 4.8a3.3 3.3 0 0 1 0 6.4M17.5 14.4c2 .8 3.5 2.9 3.5 5.6\"/>");
        icon("user", "<circle cx=\"12\" cy=\"8\" r=\"4\"/><path d=\"M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8\"/>");
        icon("userPlus", "<circle cx=\"10\" cy=\"8\" r=\"3.5\"/><path d=\"M3.5 20c0-3.6 2.9-6.5 6.5-6.5 1.7 0 3.2.6 4.4 1.7M19 14v6M16 17h6\"/>");
        icon("search", "<circle cx=\"11\" cy=\"11\" r=\"6.5\"/><path d=\"M20 20l-4.2-4.2\"/>");
        icon("star", "<path d=\"M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.9z\"/>");
        icon("plus", "<path d=\"M12 5v14M5 12h14\"/>");
        icon("x", "<path d=\"M6 6l12 12M18 6L6 18\"/>");
        icon("check", "<path d=\"M5 12.5l4.5 4.5L19 7\"/>");
        icon("chevR", "<path d=\"M9 6l6 6-6 6\"/>");
        icon("chevD", "<path d=\"M6 9l6 6 6-6\"/>");
        icon("chevL", "<path d=\"M15 6l-6 6 6 6\"/>");
        icon("sparkle", "<path d=\"M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z\"/><path d=\"M18.5 15.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z\"/>");
        icon("swap", "<path d=\"M7 4L3.5 7.5 7 11M3.5 7.5H16M17 13l3.5 3.5L17 20M20.5 16.5H8\"/>");
        icon("door", "<path d=\"M14 4h5v16h-5\"/><path d=\"M10 8l-4 4 4 4M6 12h10\"/>");
        icon("mail", "<rect x=\"3\" y=\"5.5\" width=\"18\" height=\"13\" rx=\"2\"/><path d=\"M3.5 7l8.5 6 8.5-6\"/>");
        icon("bell", "<path d=\"M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z\"/><path d=\"M10 20.5h4\"/>");
        icon("info", "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M12 11v5.5M12 7.8h.01\"/>");
        icon("eye", "<path d=\"M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z\"/><circle cx=\"12\" cy=\"12\" r=\"2.8\"/>");
        icon("monitor", "<rect x=\"3\" y=\"4\" width=\"18\" height=\"12\" rx=\"2\"/><path d=\"M8.5 20h7M12 16v4\"/>");
        icon("image", "<rect x=\"3\" y=\"5\" width=\"18\" height=\"14\" rx=\"2\"/><path d=\"M3 16l5-5 4 4 3-3 6 6\"/><circle cx=\"15.5\" cy=\"9\" r=\"1.2\"/>");
        icon("chart", "<path d=\"M4 20V10M10 20V4M16 20v-7M21 20H3\"/>");
        icon("palette", "<path d=\"M12 3.5a8.5 8.5 0 0 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.2-1-1.5-1-2.6 0-.9.7-1.7 1.7-1.7h2.2c2.1 0 3.8-1.7 3.8-3.8 0-4-3.8-7.2-8.5-7.2z\"/><path d=\"M7.5 11.5h.01M10 7.5h.01M14.5 7.5h.01\"/>");
        icon("globe", "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M3.5 12h17M12 3.5c2.4 2.4 3.6 5.2 3.6 8.5s-1.2 6.1-3.6 8.5c-2.4-2.4-3.6-5.2-3.6-8.5S9.6 5.9 12 3.5z\"/>");
        icon("lock", "<rect x=\"5\" y=\"10.5\" width=\"14\" height=\"10\" rx=\"2\"/><path d=\"M8 10.5V8a4 4 0 0 1 8 0v2.5\"/>");
        icon("camera", "<path d=\"M4 8h3l1.5-2.5h7L17 8h3v11H4z\"/><circle cx=\"12\" cy=\"13\" r=\"3.5\"/>");
        icon("refresh", "<path d=\"M20 11a8 8 0 0 0-14.3-4.3L4 8.5\"/><path d=\"M4 4v4.5h4.5\"/><path d=\"M4 13a8 8 0 0 0 14.3 4.3l1.7-1.8\"/><path d=\"M20 20v-4.5h-4.5\"/>");
        icon("trash", "<path d=\"M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13\"/>");
        icon("pencil", "<path d=\"M4 20h4L19 9l-4-4L4 16z\"/><path d=\"M13.5 6.5l4 4\"/>");
        icon("more", "<path d=\"M6 12h.01M12 12h.01M18 12h.01\"/>");
        icon("layout", "<rect x=\"3\" y=\"4.5\" width=\"18\" height=\"15\" rx=\"2.5\"/><path d=\"M6.5 8h4v3h-4zM13.5 15h4v1.5h-4z\"/>");
        icon("keyboard", "<rect x=\"2.5\" y=\"6\" width=\"19\" height=\"12\" rx=\"2\"/><path d=\"M6.5 10h.01M10 10h.01M14 10h.01M17.5 10h.01M7.5 14h9\"/>");
    }

    private Icons() {
    }

    public static boolean has(String name) {
        return INDEX.containsKey(name);
    }

    /** Icon in Einheiten zeichnen (quadratisch, size), eingefärbt */
    public static void draw(GuiGraphics g, String name, float x, float y, float size, int color) {
        Integer idx = INDEX.get(name);
        if (idx == null || (color >>> 24) == 0) return;
        float k = Draw.begin(g);
        int s = Math.max(6, Math.round(size * k));
        Bake.Baked atlas = atlas(s);
        if (atlas != null) {
            de.myticlegacy.client.compat.Tex.drawTinted(g, atlas.tex, Math.round(x * k), Math.round(y * k), s, s, idx * s, 0, s, s, atlas.w, atlas.h, color);
        }
        Draw.end(g);
    }

    /** Alle Icons in Pixelgröße s nebeneinander */
    static Bake.Baked atlas(final int s) {
        return Bake.cached("icons" + s, new Bake.Maker() {
            public Bake.Baked make() {
                BufferedImage img = Bake.image(s * NAMES.size(), s);
                Graphics2D g = Bake.gfx(img);
                g.setColor(Color.WHITE);
                float k = s / 24f;
                g.setStroke(new BasicStroke(Math.max(1f, 1.8f * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < NAMES.size(); i++) {
                    AffineTransform t = new AffineTransform();
                    t.translate(i * s, 0);
                    t.scale(k, k);
                    for (java.awt.Shape shape : parse(SVG.get(NAMES.get(i)))) g.draw(t.createTransformedShape(shape));
                }
                g.dispose();
                return Bake.upload(img, 0, 0, 0);
            }
        });
    }

    // ------------------------------------------------------------------ kleiner SVG-Leser (path, rect, circle)

    private static final Pattern ELEMENT = Pattern.compile("<(path|rect|circle)([^>]*)/>");
    private static final Pattern ATTR = Pattern.compile("([a-z]+)=\"([^\"]*)\"");
    private static final Pattern TOKEN = Pattern.compile("[MmLlHhVvCcSsQqAaZz]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?");

    static List<java.awt.Shape> parse(String svg) {
        List<java.awt.Shape> out = new ArrayList<java.awt.Shape>();
        Matcher m = ELEMENT.matcher(svg);
        while (m.find()) {
            Map<String, String> a = new HashMap<String, String>();
            Matcher am = ATTR.matcher(m.group(2));
            while (am.find()) a.put(am.group(1), am.group(2));
            String type = m.group(1);
            if (type.equals("rect")) {
                float rx = a.containsKey("rx") ? f(a.get("rx")) : 0;
                out.add(new RoundRectangle2D.Float(f(a.get("x")), f(a.get("y")), f(a.get("width")), f(a.get("height")), 2 * rx, 2 * rx));
            } else if (type.equals("circle")) {
                float r = f(a.get("r"));
                out.add(new Ellipse2D.Float(f(a.get("cx")) - r, f(a.get("cy")) - r, 2 * r, 2 * r));
            } else if (a.containsKey("d")) {
                out.add(path(a.get("d")));
            }
        }
        return out;
    }

    private static float f(String s) {
        return s == null ? 0 : Float.parseFloat(s);
    }

    private static Path2D path(String d) {
        List<String> tok = new ArrayList<String>();
        Matcher m = TOKEN.matcher(d);
        while (m.find()) tok.add(m.group());
        Path2D.Float p = new Path2D.Float();
        float cx = 0, cy = 0, sx = 0, sy = 0, lcx = 0, lcy = 0;
        char cmd = 'M', prev = ' ';
        int i = 0;
        while (i < tok.size()) {
            String t = tok.get(i);
            if (Character.isLetter(t.charAt(0))) {
                cmd = t.charAt(0);
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    p.closePath();
                    cx = sx;
                    cy = sy;
                    prev = cmd;
                    continue;
                }
            }
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? cx : 0, oy = rel ? cy : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M': {
                    cx = ox + n(tok, i++);
                    cy = oy + n(tok, i++);
                    p.moveTo(cx, cy);
                    sx = cx;
                    sy = cy;
                    cmd = rel ? 'l' : 'L'; // weitere Paare sind Linien
                    break;
                }
                case 'L':
                    cx = ox + n(tok, i++);
                    cy = oy + n(tok, i++);
                    p.lineTo(cx, cy);
                    break;
                case 'H':
                    cx = ox + n(tok, i++);
                    p.lineTo(cx, cy);
                    break;
                case 'V':
                    cy = oy + n(tok, i++);
                    p.lineTo(cx, cy);
                    break;
                case 'C': {
                    float x1 = ox + n(tok, i++), y1 = oy + n(tok, i++), x2 = ox + n(tok, i++), y2 = oy + n(tok, i++);
                    cx = ox + n(tok, i++);
                    cy = oy + n(tok, i++);
                    p.curveTo(x1, y1, x2, y2, cx, cy);
                    lcx = x2;
                    lcy = y2;
                    break;
                }
                case 'S': {
                    char pu = Character.toUpperCase(prev);
                    float x1 = pu == 'C' || pu == 'S' ? 2 * cx - lcx : cx, y1 = pu == 'C' || pu == 'S' ? 2 * cy - lcy : cy;
                    float x2 = ox + n(tok, i++), y2 = oy + n(tok, i++);
                    cx = ox + n(tok, i++);
                    cy = oy + n(tok, i++);
                    p.curveTo(x1, y1, x2, y2, cx, cy);
                    lcx = x2;
                    lcy = y2;
                    break;
                }
                case 'Q': {
                    float x1 = ox + n(tok, i++), y1 = oy + n(tok, i++);
                    cx = ox + n(tok, i++);
                    cy = oy + n(tok, i++);
                    p.quadTo(x1, y1, cx, cy);
                    break;
                }
                case 'A': {
                    float rx = n(tok, i++), ry = n(tok, i++), rot = n(tok, i++);
                    boolean large = n(tok, i++) != 0, sweep = n(tok, i++) != 0;
                    float x = ox + n(tok, i++), y = oy + n(tok, i++);
                    arc(p, cx, cy, rx, ry, rot, large, sweep, x, y);
                    cx = x;
                    cy = y;
                    break;
                }
                default:
                    i++;
            }
            prev = cmd;
        }
        return p;
    }

    private static float n(List<String> tok, int i) {
        return i < tok.size() ? Float.parseFloat(tok.get(i)) : 0;
    }

    /** SVG-Bogen (Endpunkt-Form) als Bézier-Kurven (SVG 1.1, Anhang F.6.5) */
    private static void arc(Path2D p, double x1, double y1, double rx, double ry, double angle, boolean large, boolean sweep, double x2, double y2) {
        if (rx == 0 || ry == 0) {
            p.lineTo(x2, y2);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(angle), cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x1 - x2) / 2, dy = (y1 - y2) / 2;
        double x1p = cos * dx + sin * dy, y1p = -sin * dx + cos * dy;
        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) {
            rx *= Math.sqrt(lambda);
            ry *= Math.sqrt(lambda);
        }
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double co = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
        double cxp = co * rx * y1p / ry, cyp = -co * ry * x1p / rx;
        double ccx = cos * cxp - sin * cyp + (x1 + x2) / 2, ccy = sin * cxp + cos * cyp + (y1 + y2) / 2;
        double th1 = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
        double th2 = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
        double dth = th2 - th1;
        if (sweep && dth < 0) dth += 2 * Math.PI;
        if (!sweep && dth > 0) dth -= 2 * Math.PI;
        int segs = (int) Math.ceil(Math.abs(dth) / (Math.PI / 2));
        double step = dth / segs, t = 4.0 / 3.0 * Math.tan(step / 4);
        double a = th1;
        for (int s = 0; s < segs; s++) {
            double b = a + step;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            double[] p1 = pt(ccx, ccy, rx, ry, cos, sin, ca - t * sa, sa + t * ca);
            double[] p2 = pt(ccx, ccy, rx, ry, cos, sin, cb + t * sb, sb - t * cb);
            double[] p3 = pt(ccx, ccy, rx, ry, cos, sin, cb, sb);
            p.curveTo(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1]);
            a = b;
        }
    }

    private static double[] pt(double cx, double cy, double rx, double ry, double cos, double sin, double ux, double uy) {
        double x = rx * ux, y = ry * uy;
        return new double[]{cx + cos * x - sin * y, cy + sin * x + cos * y};
    }
}
