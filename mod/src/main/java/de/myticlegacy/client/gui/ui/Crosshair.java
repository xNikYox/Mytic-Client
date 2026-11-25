package de.myticlegacy.client.gui.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fadenkreuz-Formen des Mytic-Fadenkreuz-Editors: aus Form, Länge, Dicke, Abstand (bzw. dem 15 × 15-Pixelbild) wird
 * eine Pixelmaske um den Mittelpunkt gebaut und als Zeilenstücke gezeichnet – im HUD mit 1 GUI-Pixel je Punkt, in der
 * Vorschau vergrößert. Teilen über den Code „MYTIC-XH:…“ (Base64). (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Crosshair {
    public static final String[] FORMS = {"Kreuz", "Plus mit Lücke", "Punkt", "Kreis", "T", "Pfeil", "Pixelbild"};
    public static final String PIXELS = "Pixelbild";
    /** Kantenlänge des Pixelbilds (wie das Minecraft-Fadenkreuz) */
    public static final int GRID = 15;
    public static final String PREFIX = "MYTIC-XH:";
    private static final int R = 32, N = 2 * R + 1;

    private Crosshair() {
    }

    /** Aussehen eines Fadenkreuzes */
    public static final class Spec {
        public String form = "Plus mit Lücke";
        public int size = 4, thickness = 1, gap = 2, opacity = 100;
        public boolean outline = true, dot;
        public int color = 0xFFFFFFFF;
        /** Farbe ändern, wenn das Fadenkreuz auf einem Spieler (bzw. Mob) liegt */
        public boolean dynamic = true, mobs;
        public int targetColor = 0xFFFF5C72;
        public boolean[] pixels = new boolean[GRID * GRID];

        public Spec copy() {
            Spec s = new Spec();
            s.form = form;
            s.size = size;
            s.thickness = thickness;
            s.gap = gap;
            s.opacity = opacity;
            s.outline = outline;
            s.dot = dot;
            s.color = color;
            s.dynamic = dynamic;
            s.mobs = mobs;
            s.targetColor = targetColor;
            s.pixels = pixels.clone();
            return s;
        }

        /** Schlüssel der Form (ohne Farben) für den Masken-Zwischenspeicher */
        String shapeKey() {
            StringBuilder b = new StringBuilder(form).append('|').append(size).append('|').append(thickness).append('|').append(gap).append('|').append(dot);
            if (PIXELS.equals(form)) b.append('|').append(pixelsHex(pixels));
            return b.toString();
        }
    }

    // ------------------------------------------------------------------------------------------ Pixelbild als Text

    /** 225 Bits als Hex-Text (57 Zeichen) */
    public static String pixelsHex(boolean[] px) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < GRID * GRID; i += 4) {
            int v = 0;
            for (int j = 0; j < 4; j++) if (i + j < px.length && px[i + j]) v |= 8 >> j;
            b.append(Character.forDigit(v, 16));
        }
        return b.toString();
    }

    public static boolean[] pixelsFromHex(String hex) {
        boolean[] px = new boolean[GRID * GRID];
        if (hex == null) return px;
        for (int c = 0; c < hex.length(); c++) {
            int v = Character.digit(hex.charAt(c), 16);
            if (v < 0) continue;
            for (int j = 0; j < 4; j++) {
                int i = c * 4 + j;
                if (i < px.length) px[i] = (v & (8 >> j)) != 0;
            }
        }
        return px;
    }

    // ------------------------------------------------------------------------------------------ Maske

    private static final Map<String, int[][]> CACHE = new LinkedHashMap<String, int[][]>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[][]> e) {
            return size() > 24;
        }
    };

    /** Maske der Form: true = Punkt gesetzt; Mittelpunkt bei (R, R) */
    static boolean[] mask(Spec s) {
        boolean[] m = new boolean[N * N];
        int t = clamp(s.thickness, 1, 8), len = clamp(s.size, 1, 24), gap = clamp(s.gap, 0, 16);
        int lo = -(t / 2), hi = lo + t - 1;
        String f = s.form;
        if ("Kreuz".equals(f)) {
            box(m, lo - len, lo, hi + len, hi);
            box(m, lo, lo - len, hi, hi + len);
        } else if ("Plus mit Lücke".equals(f) || "T".equals(f)) {
            box(m, hi + 1 + gap, lo, hi + gap + len, hi);
            box(m, lo - gap - len, lo, lo - 1 - gap, hi);
            box(m, lo, hi + 1 + gap, hi, hi + gap + len);
            if (!"T".equals(f)) box(m, lo, lo - gap - len, hi, lo - 1 - gap);
        } else if ("Punkt".equals(f)) {
            double r = Math.max(0.5, len / 2.0);
            for (int y = -R; y <= R; y++)
                for (int x = -R; x <= R; x++) if (x * x + y * y <= r * r + 0.35) set(m, x, y);
        } else if ("Kreis".equals(f)) {
            double rr = gap + len / 2.0 + 1.5, half = t * 0.5 + 0.1;
            for (int y = -R; y <= R; y++)
                for (int x = -R; x <= R; x++) if (Math.abs(Math.sqrt(x * x + y * y) - rr) <= half) set(m, x, y);
        } else if ("Pfeil".equals(f)) {
            for (int i = 0; i < len + 1; i++)
                for (int j = 0; j < t; j++) {
                    set(m, i + j, gap + i);
                    set(m, -(i + j), gap + i);
                }
        } else if (PIXELS.equals(f)) {
            int c = GRID / 2;
            for (int i = 0; i < GRID * GRID && i < s.pixels.length; i++) if (s.pixels[i]) set(m, i % GRID - c, i / GRID - c);
        }
        if (s.dot && !"Punkt".equals(f)) box(m, lo, lo, hi, hi);
        return m;
    }

    private static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    private static void set(boolean[] m, int x, int y) {
        if (x >= -R && x <= R && y >= -R && y <= R) m[(y + R) * N + x + R] = true;
    }

    private static boolean get(boolean[] m, int x, int y) {
        return x >= -R && x <= R && y >= -R && y <= R && m[(y + R) * N + x + R];
    }

    private static void box(boolean[] m, int x1, int y1, int x2, int y2) {
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) set(m, x, y);
    }

    /** Zeilenstücke {x, y, Breite} für Füllung [0] und Umrandung [1] (Koordinaten relativ zur Mitte) */
    static int[][] runs(Spec s) {
        String key = s.shapeKey();
        synchronized (CACHE) {
            int[][] cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        boolean[] m = mask(s);
        boolean[] o = new boolean[N * N];
        for (int y = -R; y <= R; y++)
            for (int x = -R; x <= R; x++) {
                if (get(m, x, y)) continue;
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) for (int dx = -1; dx <= 1 && !near; dx++) near = get(m, x + dx, y + dy);
                if (near) set(o, x, y);
            }
        int[][] out = {toRuns(m), toRuns(o)};
        synchronized (CACHE) {
            CACHE.put(key, out);
        }
        return out;
    }

    private static int[] toRuns(boolean[] m) {
        List<Integer> l = new ArrayList<Integer>();
        for (int y = -R; y <= R; y++) {
            int start = Integer.MIN_VALUE;
            for (int x = -R; x <= R + 1; x++) {
                boolean on = x <= R && get(m, x, y);
                if (on && start == Integer.MIN_VALUE) start = x;
                if (!on && start != Integer.MIN_VALUE) {
                    l.add(start);
                    l.add(y);
                    l.add(x - start);
                    start = Integer.MIN_VALUE;
                }
            }
        }
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    /** Ausdehnung der Form in Punkten vom Mittelpunkt (für die Größe der Vorschau) */
    public static int extent(Spec s) {
        int[][] r = runs(s);
        int e = 1;
        for (int i = 0; i + 2 < r[1].length; i += 3) {
            e = Math.max(e, Math.max(Math.abs(r[1][i]), Math.abs(r[1][i] + r[1][i + 2] - 1)));
            e = Math.max(e, Math.abs(r[1][i + 1]));
        }
        return e;
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    /**
     * Fadenkreuz zeichnen: (cx, cy) = linke obere Ecke des Mittelpunkt-Pixels, unit = Größe eines Punkts.
     * color = Füllfarbe (ARGB); Deckkraft aus spec.opacity.
     */
    public static void draw(GuiGraphics g, int cx, int cy, Spec s, int color, int unit) {
        int[][] r = runs(s);
        float op = Math.max(0, Math.min(100, s.opacity)) / 100f;
        int a = Math.round(((color >>> 24) & 0xFF) * op);
        int fill = (a << 24) | (color & 0xFFFFFF);
        if (s.outline) {
            int oa = Math.round(0xB4 * op);
            int[] o = r[1];
            for (int i = 0; i + 2 < o.length; i += 3)
                g.fill(cx + o[i] * unit, cy + o[i + 1] * unit, cx + (o[i] + o[i + 2]) * unit, cy + (o[i + 1] + 1) * unit, oa << 24);
        }
        int[] f = r[0];
        for (int i = 0; i + 2 < f.length; i += 3)
            g.fill(cx + f[i] * unit, cy + f[i + 1] * unit, cx + (f[i] + f[i + 2]) * unit, cy + (f[i + 1] + 1) * unit, fill);
    }

    // ------------------------------------------------------------------------------------------ Code

    public static String encode(Spec s) {
        byte[] b = new byte[15 + 29];
        b[0] = 1;
        b[1] = (byte) Math.max(0, indexOf(s.form));
        b[2] = (byte) clamp(s.size, 0, 127);
        b[3] = (byte) clamp(s.thickness, 0, 127);
        b[4] = (byte) clamp(s.gap, 0, 127);
        b[5] = (byte) ((s.outline ? 1 : 0) | (s.dot ? 2 : 0) | (s.dynamic ? 4 : 0) | (s.mobs ? 8 : 0));
        putInt(b, 6, s.color);
        putInt(b, 10, s.targetColor);
        b[14] = (byte) clamp(s.opacity, 0, 100);
        for (int i = 0; i < GRID * GRID; i++) if (s.pixels[i]) b[15 + i / 8] |= (byte) (1 << (i % 8));
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Code lesen; null = kein gültiger Fadenkreuz-Code */
    public static Spec decode(String code) {
        if (code == null) return null;
        code = code.trim();
        int at = code.indexOf(PREFIX);
        if (at < 0) return null;
        String data = code.substring(at + PREFIX.length()).trim();
        int end = 0;
        while (end < data.length() && (Character.isLetterOrDigit(data.charAt(end)) || data.charAt(end) == '-' || data.charAt(end) == '_')) end++;
        byte[] b;
        try {
            b = Base64.getUrlDecoder().decode(data.substring(0, end));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (b.length < 15 || b[0] != 1) return null;
        Spec s = new Spec();
        int form = b[1];
        if (form < 0 || form >= FORMS.length) return null;
        s.form = FORMS[form];
        s.size = clamp(b[2], 1, 12);
        s.thickness = clamp(b[3], 1, 4);
        s.gap = clamp(b[4], 0, 10);
        s.outline = (b[5] & 1) != 0;
        s.dot = (b[5] & 2) != 0;
        s.dynamic = (b[5] & 4) != 0;
        s.mobs = (b[5] & 8) != 0;
        s.color = getInt(b, 6) | 0xFF000000;
        s.targetColor = getInt(b, 10) | 0xFF000000;
        s.opacity = clamp(b[14], 10, 100);
        for (int i = 0; i < GRID * GRID; i++) {
            int k = 15 + i / 8;
            s.pixels[i] = k < b.length && (b[k] & (1 << (i % 8))) != 0;
        }
        return s;
    }

    private static int indexOf(String form) {
        for (int i = 0; i < FORMS.length; i++) if (FORMS[i].equals(form)) return i;
        return -1;
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }

    private static int getInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }

    // ------------------------------------------------------------------------------------------ Vorlagen

    public static final String[] PRESETS = {"Klassisch", "Wettkampf", "Punkt", "Kreis & Punkt", "T-Form", "Pfeil", "Mytic", "Fein"};

    public static Spec preset(int i) {
        Spec s = new Spec();
        switch (i) {
            case 0:
                s.form = "Kreuz";
                s.size = 5;
                s.outline = false;
                break;
            case 1:
                s.form = "Plus mit Lücke";
                s.size = 4;
                s.gap = 2;
                s.color = 0xFF5BE38A;
                break;
            case 2:
                s.form = "Punkt";
                s.size = 3;
                break;
            case 3:
                s.form = "Kreis";
                s.size = 5;
                s.gap = 2;
                s.dot = true;
                s.color = 0xFF4DA3FF;
                break;
            case 4:
                s.form = "T";
                s.size = 5;
                s.gap = 2;
                s.color = 0xFF2FD0C0;
                break;
            case 5:
                s.form = "Pfeil";
                s.size = 4;
                s.gap = 1;
                s.color = 0xFFFFD84A;
                break;
            case 6:
                s.form = PIXELS;
                s.color = 0xFFC9B0FF;
                s.pixels = mytic();
                break;
            default:
                s.form = "Plus mit Lücke";
                s.size = 3;
                s.gap = 1;
                s.opacity = 80;
                s.outline = false;
                s.dot = true;
                break;
        }
        return s;
    }

    /** Mytic-Raute als Pixelbild */
    private static boolean[] mytic() {
        boolean[] px = new boolean[GRID * GRID];
        int c = GRID / 2;
        for (int y = 0; y < GRID; y++)
            for (int x = 0; x < GRID; x++) {
                int d = Math.abs(x - c) + Math.abs(y - c);
                px[y * GRID + x] = d == 5 || d == 0;
            }
        return px;
    }
}
