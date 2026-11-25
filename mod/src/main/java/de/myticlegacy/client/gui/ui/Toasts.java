package de.myticlegacy.client.gui.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Kurze Rückmeldungen unten in der Mitte („Konto gewechselt“, „Gespeichert“): dunkles Glas, Icon in Akzent bzw.
 * Status-Farbe, gleitet ein und blendet nach einigen Sekunden aus. Bildschirme zeichnen sie zuletzt (draw).
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Toasts {
    public static final int INFO = 0, OK = 1, ERROR = 2;
    private static String text;
    private static String icon;
    private static int kind;
    private static long shownAt;
    private static long duration;

    private Toasts() {
    }

    public static void show(String message, String iconName, int type) {
        text = message;
        icon = iconName;
        kind = type;
        shownAt = System.currentTimeMillis();
        duration = type == ERROR ? 5200 : 2800;
    }

    public static void ok(String message) {
        show(message, "check", OK);
    }

    public static void error(String message) {
        show(message, "info", ERROR);
    }

    /** Unten mittig über allem (vw/vh = Bildschirmgröße in Einheiten, d = Design-Faktor) */
    public static void draw(GuiGraphics g, float vw, float vh, float d) {
        if (text == null) return;
        long age = System.currentTimeMillis() - shownAt;
        if (age > duration + 300) {
            text = null;
            return;
        }
        float in = Motion.ease(Math.min(1f, age / 300f));
        float out = age > duration ? 1f - (age - duration) / 300f : 1f;
        float a = Math.max(0, Math.min(in, out));
        float fs = 15 * d, h = 46 * d;
        String shown = Type.ellipsize(g, text, vw * 0.7f, fs, Tokens.MEDIUM);
        float w = 18 * d + 20 * d + 10 * d + Type.width(g, shown, fs, Tokens.MEDIUM) + 18 * d;
        float x = vw / 2f - w / 2f, y = vh - 44 * d - h + (1 - in) * 20 * d;
        Draw.shadow(g, x, y + 20 * d, w, h, 14 * d, 40 * d, -12 * d, Draw.alpha(0x99000000, a));
        Draw.glass(g, x, y, w, h, 14 * d, Draw.alpha(0xEB14101E, a), Draw.alpha(Tokens.LINE_STRONG, a));
        int ic = kind == ERROR ? Tokens.BAD : kind == OK ? Tokens.OK : Tokens.ACC;
        Icons.draw(g, icon == null ? "info" : icon, x + 18 * d, y + h / 2 - 10 * d, 20 * d, Draw.alpha(ic, a));
        Type.middle(g, shown, x + 48 * d, y + h / 2, fs, Tokens.MEDIUM, Draw.alpha(Tokens.TEXT, a));
    }
}
