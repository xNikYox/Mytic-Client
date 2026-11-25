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
        icon("link", "<path d=\"M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1\"/><path d=\"M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1\"/>");
        icon("cape", "<path d=\"M8 3.5h8l3 16.5-7-2.5-7 2.5z\"/><path d=\"M8 3.5c.4 1.7 2 2.7 4 2.7s3.6-1 4-2.7\"/>");
        icon("crown", "<path d=\"M3.5 18h17M4.5 15.5L3.5 7l5 4 3.5-6 3.5 6 5-4-1 8.5z\"/>");
        icon("wings", "<path d=\"M12 9C10 5.5 6 4.5 3 5.5c.8 4.5 3.5 8.5 9 10.5 5.5-2 8.2-6 9-10.5-3-1-7 0-9 3.5z\"/><path d=\"M12 9v7\"/>");
        icon("shield", "<path d=\"M12 3l7 3v5.5c0 4.3-3 7.8-7 9.5-4-1.7-7-5.2-7-9.5V6z\"/>");
        icon("tag", "<path d=\"M3.5 12V4h8l9 9-8 8z\"/><path d=\"M7.5 8h.01\"/>");
        icon("smile", "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M8.5 14.5c1 1.3 2.2 2 3.5 2s2.5-.7 3.5-2M9 9.5h.01M15 9.5h.01\"/>");
        icon("eyeOff", "<path d=\"M4 4l16 16\"/><path d=\"M9.5 6A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a16 16 0 0 1-2.7 3.4M6.4 7.6A16 16 0 0 0 2.5 12S6 18.5 12 18.5a9 9 0 0 0 3.9-.9\"/>");
        icon("bolt", "<path d=\"M13 2.5L4.5 14H11l-1 7.5L19.5 10H13z\"/>");
        icon("swords", "<path d=\"M14.5 17.5L4 7V4h3l10.5 10.5\"/><path d=\"M13 19l6-6M16 16l3.5 3.5M18.5 20.5l2-2\"/>");
        icon("toolbox", "<rect x=\"3\" y=\"8\" width=\"18\" height=\"11\" rx=\"2\"/><path d=\"M8.5 8V5.5h7V8M3 13h18M10.5 13v2h3v-2\"/>");
        icon("crosshair", "<circle cx=\"12\" cy=\"12\" r=\"7.5\"/><path d=\"M12 2.5V7M12 17v4.5M2.5 12H7M17 12h4.5\"/>");
        icon("sun", "<circle cx=\"12\" cy=\"12\" r=\"4\"/><path d=\"M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4\"/>");
        icon("moon", "<path d=\"M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z\"/>");
        icon("zoom", "<circle cx=\"11\" cy=\"11\" r=\"6.5\"/><path d=\"M20 20l-4.2-4.2M11 8.5v5M8.5 11h5\"/>");
        icon("clock", "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M12 7.5V12l3 2\"/>");
        icon("compass", "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M15.5 8.5l-2 5-5 2 2-5z\"/>");
        icon("heart", "<path d=\"M12 19.5S4 15 4 9.3A4.2 4.2 0 0 1 12 7a4.2 4.2 0 0 1 8 2.3C20 15 12 19.5 12 19.5z\"/>");
        icon("flask", "<path d=\"M9 3h6M10 3v6l-5 8.6A2 2 0 0 0 6.8 20.5h10.4a2 2 0 0 0 1.8-2.9L14 9V3\"/><path d=\"M7.2 15h9.6\"/>");
        icon("armor", "<path d=\"M8 3.5L4 6l1.8 4 2.2-1v11.5h8V9l2.2 1L20 6l-4-2.5c-.6 1.5-2 2.4-4 2.4S8.6 5 8 3.5z\"/>");
        icon("mouse", "<rect x=\"6.5\" y=\"3\" width=\"11\" height=\"18\" rx=\"5.5\"/><path d=\"M12 3v6\"/>");
        icon("keys", "<rect x=\"9\" y=\"3.5\" width=\"6\" height=\"6\" rx=\"1.3\"/><rect x=\"2.5\" y=\"11.5\" width=\"6\" height=\"6\" rx=\"1.3\"/><rect x=\"9\" y=\"11.5\" width=\"6\" height=\"6\" rx=\"1.3\"/><rect x=\"15.5\" y=\"11.5\" width=\"6\" height=\"6\" rx=\"1.3\"/>");
        icon("signal", "<path d=\"M4 20v-3M9 20v-6M14 20v-9M19 20V5\"/>");
        icon("gauge", "<path d=\"M4 16a8 8 0 1 1 16 0\"/><path d=\"M12 16l4-4.5\"/>");
        icon("chip", "<rect x=\"6.5\" y=\"6.5\" width=\"11\" height=\"11\" rx=\"2\"/><path d=\"M10 3v3.5M14 3v3.5M10 17.5V21M14 17.5V21M3 10h3.5M3 14h3.5M17.5 10H21M17.5 14H21\"/>");
        icon("tree", "<path d=\"M12 3l6 8.5h-3.5L19 18H5l4.5-6.5H6z\"/><path d=\"M12 18v3\"/>");
        icon("hourglass", "<path d=\"M7 3.5h10M7 20.5h10M8 3.5c0 4.5 8 4.5 8 8.5s-8 4-8 8.5M16 3.5c0 4.5-8 4.5-8 8.5s8 4 8 8.5\"/>");
        icon("zap", "<path d=\"M4 14l6-10v6h6l-6 10v-6z\"/>");
        icon("reach", "<path d=\"M3 12h13M12.5 8l4 4-4 4M20.5 5v14\"/>");
        icon("target", "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><circle cx=\"12\" cy=\"12\" r=\"4.5\"/><circle cx=\"12\" cy=\"12\" r=\".8\"/>");
        icon("cloud", "<path d=\"M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 11.2 3.4 3.4 0 0 0 7 18z\"/>");
        icon("list", "<path d=\"M4 6.5h16M4 12h16M4 17.5h10\"/>");
        icon("bar", "<rect x=\"3.5\" y=\"9.5\" width=\"17\" height=\"5\" rx=\"2.5\"/><path d=\"M6 12h7\"/>");
        icon("run", "<path d=\"M4 12h12M12 6.5l5.5 5.5-5.5 5.5M3 6.5h3M3 17.5h3\"/>");
        icon("sneak", "<path d=\"M12 4v11M6.5 10l5.5 5.5 5.5-5.5M5 20h14\"/>");
        icon("chat", "<path d=\"M4 5h16v11H9.5L4 20z\"/><path d=\"M8 9.5h8M8 12.5h5\"/>");
        icon("bulb", "<path d=\"M9 18h6M10 21h4\"/><path d=\"M12 3a6 6 0 0 0-3.8 10.6c.6.6.8 1.4.8 2.4h6c0-1 .2-1.8.8-2.4A6 6 0 0 0 12 3z\"/>");
        icon("box", "<path d=\"M4 8l8-4 8 4v8l-8 4-8-4z\"/><path d=\"M4 8l8 4 8-4M12 12v8\"/>");
        icon("wind", "<path d=\"M3 8.5h11a2.5 2.5 0 1 0-2.5-2.5M3 12.5h15a2.5 2.5 0 1 1-2.5 2.5M3 16.5h7\"/>");
        icon("rotL", "<path d=\"M4 12a8 8 0 1 0 2.3-5.7L4 8.5\"/><path d=\"M4 4v4.5h4.5\"/>");
        icon("rotR", "<path d=\"M20 12a8 8 0 1 1-2.3-5.7L20 8.5\"/><path d=\"M20 4v4.5h-4.5\"/>");
        icon("move", "<path d=\"M12 3v18M3 12h18M12 3l-2.5 2.5M12 3l2.5 2.5M12 21l-2.5-2.5M12 21l2.5-2.5M3 12l2.5-2.5M3 12l2.5 2.5M21 12l-2.5-2.5M21 12l-2.5 2.5\"/>");
        icon("dots9", "<path d=\"M6 6h.01M12 6h.01M18 6h.01M6 12h.01M12 12h.01M18 12h.01M6 18h.01M12 18h.01M18 18h.01\"/>");
        icon("hat", "<path d=\"M4 17.5h16M7 17.5V9.5a5 5 0 0 1 10 0v8\"/><path d=\"M7 13.5h10\"/>");
        icon("mini", "<circle cx=\"12\" cy=\"6.5\" r=\"3\"/><path d=\"M8.5 21v-5.5H7.5v-4a2 2 0 0 1 2-2h5a2 2 0 0 1 2 2v4h-1V21\"/>");
        icon("wave", "<path d=\"M7 11V5.5a1.5 1.5 0 0 1 3 0V10M10 9.5V4a1.5 1.5 0 0 1 3 0v6M13 9.5V5a1.5 1.5 0 0 1 3 0v6.5M16 9.5a1.5 1.5 0 0 1 3 0V14a7 7 0 0 1-7 7 6.5 6.5 0 0 1-5.6-3.2L4 13.6a1.5 1.5 0 0 1 2.5-1.6L7 13\"/>");
        icon("external", "<path d=\"M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5\"/>");
        icon("folder", "<path d=\"M3.5 6.5a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2v8.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z\"/>");
        icon("bug", "<rect x=\"7\" y=\"7.5\" width=\"10\" height=\"12\" rx=\"5\"/><path d=\"M12 11v8.5M3.5 13H7M17 13h3.5M5 7.5l2.5 2M19 7.5l-2.5 2M5 19.5l2.5-2M19 19.5l-2.5-2M9.5 5.5l1 2M14.5 5.5l-1 2\"/>");
        icon("grip", "<path d=\"M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01\"/>");
        icon("magnet", "<path d=\"M6 4v8a6 6 0 0 0 12 0V4h-4v8a2 2 0 0 1-4 0V4z\"/><path d=\"M6 8h4M14 8h4\"/>");
        icon("history", "<path d=\"M4 12a8 8 0 1 0 2.3-5.7L4 8.5\"/><path d=\"M4 4v4.5h4.5M12 8v4l3 2\"/>");
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
