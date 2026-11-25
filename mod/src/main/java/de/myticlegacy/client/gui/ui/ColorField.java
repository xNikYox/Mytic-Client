package de.myticlegacy.client.gui.ui;

/**
 * Farbwahl im neuen Design (wie „Textfarbe“ in der Vorschau): Reihe runder Farbfelder, darunter ein Farbton-Regler
 * und ein Hex-Feld (Enter übernimmt). Ein Objekt je Einstellung (hält das Hex-Feld).
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class ColorField {
    public static final float HEIGHT = 30 + 10 + 40;
    private final Kit.Field hex = new Kit.Field(7);
    private int shown = 1;
    private float hue = -1;

    public ColorField() {
        hex.allowed = "#0123456789abcdefABCDEF";
    }

    private static float[] hsb(int c) {
        return java.awt.Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, null);
    }

    /**
     * Zeichnen bei (x, y), Breite w; palette = Farbfelder (ARGB). onChange bekommt die neue Farbe (ARGB, deckend),
     * onDone wird nach dem Ziehen/Klicken aufgerufen (speichern).
     */
    public void draw(final Kit kit, String id, float x, float y, float w, final int color, int[] palette, final Kit.IntSink onChange, final Runnable onDone) {
        float d = kit.d;
        // Farbfelder
        float sw = 26 * d, gap = 8 * d;
        int n = Math.max(1, Math.min(palette.length, (int) ((w + gap) / (sw + gap))));
        for (int i = 0; i < n; i++) {
            final int c = palette[i] | 0xFF000000;
            float sx = x + i * (sw + gap);
            boolean sel = (color | 0xFF000000) == c;
            boolean hv = kit.hit(id + ".sw" + i, sx, y + 2 * d, sw, sw, new Runnable() {
                public void run() {
                    hue = -1;
                    onChange.accept(c);
                    onDone.run();
                }
            });
            if (sel) Draw.rect(kit.g, sx - 3 * d, y - 1 * d, sw + 6 * d, sw + 6 * d, (sw + 6 * d) / 2, 0xFFFFFFFF);
            if (sel) Draw.rect(kit.g, sx - 1.5f * d, y + 0.5f * d, sw + 3 * d, sw + 3 * d, (sw + 3 * d) / 2, 0xFF141020);
            Draw.rect(kit.g, sx, y + 2 * d, sw, sw, sw / 2, c);
            Draw.ring(kit.g, sx, y + 2 * d, sw, sw, sw / 2, 1, hv ? 0x80FFFFFF : 0x26FFFFFF);
        }
        // Farbton-Regler (Regenbogen-Spur) und Hex-Feld
        float ry = y + 30 * d + 10 * d, hexW = 110 * d, tw = Math.max(40 * d, w - hexW - 14 * d);
        float[] cur = hsb(color);
        if (hue < 0 || Math.abs(hsb(color)[0] - hue) > 0.02f && cur[1] > 0.05f) hue = cur[0];
        final float sat = cur[1] < 0.15f ? 0.7f : cur[1], bri = cur[2] < 0.25f ? 1f : cur[2];
        float th = 8 * d, ty = ry + 20 * d - th / 2;
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            int c = java.awt.Color.HSBtoRGB(i / (float) steps, 0.75f, 1f) | 0xFF000000;
            float sx = x + tw * i / steps, ex = x + tw * (i + 1) / steps;
            Draw.rect(kit.g, sx, ty, ex - sx + 0.5f, th, i == 0 || i == steps - 1 ? th / 2 : 0, c);
        }
        final float tx = x, tww = tw;
        kit.dragArea(id + ".hue", x - 9 * d, ry + 6 * d, tw + 18 * d, 28 * d, false, new Kit.FloatSink() {
            public void accept(float px) {
                hue = Math.max(0, Math.min(0.999f, (px - tx) / tww));
                onChange.accept(java.awt.Color.HSBtoRGB(hue, sat, bri) | 0xFF000000);
            }
        });
        boolean hv = kit.hover(id + ".hue");
        float knob = 18 * d, kx = x + tw * Math.max(0, Math.min(1, hue)) - knob / 2, ky = ry + 20 * d - knob / 2;
        Draw.rect(kit.g, kx - 3 * d, ky - 3 * d, knob + 6 * d, knob + 6 * d, knob / 2 + 3 * d, hv ? 0x66FFFFFF : 0x40FFFFFF);
        Draw.rect(kit.g, kx, ky, knob, knob, knob / 2, 0xFFFFFFFF);
        Draw.rect(kit.g, kx + 4 * d, ky + 4 * d, knob - 8 * d, knob - 8 * d, (knob - 8 * d) / 2, color | 0xFF000000);
        // Hex-Feld
        if (kit.focused() != hex || shown != color) {
            if (kit.focused() != hex) hex.text = String.format("#%06X", color & 0xFFFFFF);
            shown = color;
        }
        hex.onEnter = new Runnable() {
            public void run() {
                String t = hex.text.replace("#", "");
                if (t.matches("[0-9a-fA-F]{6}")) {
                    hue = -1;
                    onChange.accept(Integer.parseInt(t, 16) | 0xFF000000);
                    onDone.run();
                }
                kit.focus(null);
            }
        };
        kit.field(id + ".hex", hex, x + w - hexW, ry, hexW, 40 * d, "#FFFFFF", null);
    }

    /** Höhe in Einheiten */
    public static int height(float d) {
        return Math.round(HEIGHT * d);
    }
}
