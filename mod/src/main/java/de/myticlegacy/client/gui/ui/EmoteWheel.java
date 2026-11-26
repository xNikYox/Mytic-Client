package de.myticlegacy.client.gui.ui;

import de.myticlegacy.client.emotes.EmoteCatalog;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Emote-Rad im Mytic-Design: Glas-Kreis in der Mitte, die Emotes als runde Felder im Kreis (oben beginnend, im
 * Uhrzeigersinn, Tasten 1–8). Nur Zeichnen und Trefferprüfung – die Bildschirme der Versionen kümmern sich um Eingaben.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class EmoteWheel {
    /** Abstand der Felder von der Mitte, Radius der Felder und der Mitte (Design-Pixel) */
    public static final float RING = 205, SLOT = 64, HUB = 96;

    private EmoteWheel() {
    }

    /** Feld unter der Maus: Richtung von der Mitte aus (ab einem kleinen Abstand), sonst -1 */
    public static int hovered(int count, float cx, float cy, float d, float mx, float my) {
        float dx = mx - cx, dy = my - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (count <= 0 || dist < HUB * 0.55f * d || dist > (RING + SLOT * 1.6f) * d) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2; // 0 = oben
        if (angle < 0) angle += Math.PI * 2;
        double step = Math.PI * 2 / count;
        return (int) Math.round(angle / step) % count;
    }

    /** Mittelpunkt von Feld i */
    public static float[] slot(int i, int count, float cx, float cy, float d) {
        double a = -Math.PI / 2 + Math.PI * 2 * i / count;
        return new float[]{cx + (float) Math.cos(a) * RING * d, cy + (float) Math.sin(a) * RING * d};
    }

    /**
     * Rad zeichnen. open 0–1 (Einblenden), hover = Feld unter der Maus (-1), usable[i] = Emote nutzbar,
     * title/sub = Text in der Mitte (null = Name des gewählten Emotes bzw. Hinweis), subColor für Hinweise.
     */
    public static void draw(GuiGraphics g, List<EmoteCatalog.Emote> emotes, boolean[] usable, float cx, float cy, float d, float open, int hover,
                            String title, String sub, int subColor) {
        float e = Motion.ease(open);
        float s = d * (0.86f + 0.14f * e);
        int n = emotes.size();
        // Verbindungsring hinter den Feldern
        float rr = RING * s;
        Draw.ring(g, cx - rr, cy - rr, rr * 2, rr * 2, rr, 1, Draw.alpha(Tokens.LINE_STRONG, e));
        // Mitte
        float hub = HUB * s;
        Draw.shadow(g, cx - hub, cy - hub, hub * 2, hub * 2, hub, 40 * s, 0, Draw.alpha(0x80000000, e));
        Draw.circle(g, cx, cy, hub, Draw.alpha(Tokens.GLASS_STRONG, e));
        Draw.ring(g, cx - hub, cy - hub, hub * 2, hub * 2, hub, 1, Draw.alpha(Tokens.LINE_STRONG, e));
        EmoteCatalog.Emote sel = hover >= 0 && hover < n ? emotes.get(hover) : null;
        String t = title != null ? title : sel != null ? sel.name : "Emotes";
        Icons.draw(g, sel != null ? sel.icon : "smile", cx - 15 * s, cy - 52 * s, 30 * s, Draw.alpha(sel != null ? Tokens.ACC_TEXT : Tokens.MUTED, e));
        Type.centered(g, t, cx, cy - 6 * s, Tokens.FS_H3 * s, Tokens.SEMIBOLD, Draw.alpha(Tokens.TEXT, e));
        if (sub != null) {
            String line = Type.ellipsize(g, sub, hub * 1.7f, Tokens.FS_CAPTION * s, Tokens.MEDIUM);
            Type.centered(g, line, cx, cy + 22 * s, Tokens.FS_CAPTION * s, Tokens.MEDIUM, Draw.alpha(subColor, e));
        }
        // Felder
        for (int i = 0; i < n; i++) {
            EmoteCatalog.Emote em = emotes.get(i);
            double a = -Math.PI / 2 + Math.PI * 2 * i / n;
            // nacheinander einblenden
            float ei = Motion.ease(Math.max(0, Math.min(1, open * 1.6f - i * 0.06f)));
            float dist = RING * s * (0.7f + 0.3f * ei);
            float x = cx + (float) Math.cos(a) * dist, y = cy + (float) Math.sin(a) * dist;
            float h = Motion.to("emotewheel:" + i, i == hover, Tokens.MS_HOVER);
            float r = SLOT * s * (1 + 0.08f * h);
            boolean ok = usable == null || usable[i];
            if (h > 0.01f) Draw.shadow(g, x - r, y - r, r * 2, r * 2, r, 26 * s, 0, Draw.alpha(0x669B5CFF, h * ei));
            Draw.circle(g, x, y, r, Draw.alpha(Draw.mix(Tokens.GLASS_STRONG, 0xF0281848, h), ei));
            if (h > 0.01f) Draw.circle(g, x, y, r, Draw.alpha(Tokens.ACC_SOFT, h * ei));
            Draw.ring(g, x - r, y - r, r * 2, r * 2, r, 1, Draw.alpha(Draw.mix(Tokens.LINE_STRONG, Tokens.ACC_LINE, h), ei));
            int fg = ok ? Draw.mix(Tokens.TEXT, Tokens.ACC_TEXT, h) : Tokens.SUBTLE;
            Icons.draw(g, ok ? em.icon : "lock", x - 17 * s, y - 30 * s, 34 * s, Draw.alpha(fg, ei));
            String label = Type.ellipsize(g, em.name, r * 1.75f, Tokens.FS_CAPTION * s, Tokens.SEMIBOLD);
            Type.centered(g, label, x, y + 14 * s, Tokens.FS_CAPTION * s, Tokens.SEMIBOLD, Draw.alpha(ok ? Draw.mix(Tokens.MUTED, Tokens.TEXT, h) : Tokens.SUBTLE, ei));
            // Tastenziffer
            Type.centered(g, String.valueOf(i + 1), x, y + 34 * s, 11 * s, Tokens.MEDIUM, Draw.alpha(Tokens.SUBTLE, ei * (0.6f + 0.4f * h)));
        }
    }
}
