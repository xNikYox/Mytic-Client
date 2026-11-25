package de.myticlegacy.client.gui.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Bausteine des neuen Designs: Buttons (Haupt-Verlauf, Glas, Gefahr, Geist, Icon-Quadrat), Schalter, Schieberegler,
 * Eingabefeld, Abzeichen. Reine Zeichen-Funktionen mit weichen Übergängen (Motion); Trefferprüfung macht der
 * Bildschirm. d = Design-Faktor (Design-Pixel → Einheiten), alle Maße wie in der Vorschau bei d = 1.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Widgets {
    public static final int PRIMARY = 0, SECONDARY = 1, GHOST = 2, DANGER = 3;

    private Widgets() {
    }

    /** Farben eines Glas-Buttons: Ruhe-/Hover-Fläche, Ruhe-/Hover-Rahmen */
    public static final class Look {
        public final int fill, fillHover, line, lineHover;

        public Look(int fill, int fillHover, int line, int lineHover) {
            this.fill = fill;
            this.fillHover = fillHover;
            this.line = line;
            this.lineHover = lineHover;
        }
    }

    public static final Look DEFAULT = new Look(Tokens.SURFACE, Tokens.SURFACE_HOVER, Tokens.LINE, Tokens.LINE_STRONG);

    /** Icon-Name mit diesem Präfix steht rechts vom Text (z. B. "&gt;chevR") */
    public static final String TRAILING = ">";

    /**
     * Button mit optionalem Icon links vom Text (beides zusammen mittig; mit Präfix TRAILING rechts davon).
     * kind = PRIMARY/SECONDARY/GHOST/DANGER. Hover hebt um 1 px an, Drücken senkt um 1 px (180 ms, Mytic-Kurve).
     */
    public static void button(GuiGraphics g, Object key, float x, float y, float w, float h, float d, String label, String icon,
                              int kind, Look look, float fontSize, float radius, boolean hover, boolean pressed) {
        float hv = Motion.to(key + ":h", hover, Tokens.MS_HOVER);
        float pr = Motion.to(key + ":p", pressed, 120);
        float lift = (-1 * hv + 2 * pr) * d;
        float yy = y + lift;
        int text = Tokens.TEXT;
        if (kind == PRIMARY) {
            // Schein unter dem Button: 0 10px 28px -10px rgba(194,94,240,.75) → beim Hover kräftiger
            // (Weichzeichnung bleibt gleich, damit die Schatten-Textur nicht bei jedem Hover-Schritt neu entsteht)
            Draw.shadow(g, x, yy + (10 + 4 * hv) * d, w, h, radius, 28 * d, -10 * d, Draw.alpha(0xFFC25EF0, 0.75f + 0.2f * hv));
            Draw.accentRect(g, x, yy, w, h, radius, 1f);
            if (hv > 0.01f) Draw.rect(g, x, yy, w, h, radius, Draw.alpha(0xFFFFFFFF, 0.08f * hv));
            Draw.rect(g, x + radius, yy, w - 2 * radius, Math.max(1f / Draw.k(g), d), 0, Draw.alpha(0xFFFFFFFF, 0.28f + 0.07f * hv));
            text = 0xFFFFFFFF;
        } else if (kind == GHOST) {
            if (hv > 0.01f) Draw.rect(g, x, yy, w, h, radius, Draw.alpha(Tokens.SURFACE, hv));
            text = Draw.mix(Tokens.MUTED, Tokens.TEXT, hv);
        } else {
            Look l = look == null ? DEFAULT : look;
            int fill = kind == DANGER ? Draw.mix(l.fill, 0x29FF5C72, hv) : Draw.mix(l.fill, l.fillHover, hv);
            int line = kind == DANGER ? Draw.mix(l.line, 0x73FF5C72, hv) : Draw.mix(l.line, l.lineHover, hv);
            Draw.glass(g, x, yy, w, h, radius, fill, line);
            if (kind == DANGER) text = Draw.mix(Tokens.TEXT, 0xFFFFD2D8, hv);
        }
        float iconSize = 22 * d * (fontSize / 18f);
        float gap = 10 * d;
        float tw = label == null ? 0 : Type.width(g, label, fontSize, Tokens.SEMIBOLD);
        float total = tw + (icon != null ? iconSize + (label == null || label.isEmpty() ? 0 : gap) : 0);
        float cx = x + w / 2f - total / 2f, cy = yy + h / 2f;
        boolean trailing = icon != null && icon.startsWith(TRAILING);
        if (icon != null && !trailing) {
            Icons.draw(g, icon, cx, cy - iconSize / 2f, iconSize, text);
            cx += iconSize + gap;
        }
        if (label != null && !label.isEmpty()) Type.middle(g, label, cx, cy, fontSize, Tokens.SEMIBOLD, text);
        if (trailing) Icons.draw(g, icon.substring(TRAILING.length()), cx + tw + gap, cy - iconSize / 2f, iconSize, text);
    }

    /** Quadratischer Icon-Button (boxed = mit Glas-Fläche und Rahmen) */
    public static void iconButton(GuiGraphics g, Object key, float x, float y, float size, float d, String icon, float iconSize,
                                  boolean boxed, Look look, boolean hover, boolean pressed) {
        float hv = Motion.to(key + ":h", hover, Tokens.MS_HOVER);
        float pr = Motion.to(key + ":p", pressed, 120);
        float yy = y + (-1 * hv + 2 * pr) * d;
        float r = size * 0.27f;
        Look l = look == null ? DEFAULT : look;
        if (boxed) Draw.glass(g, x, yy, size, size, r, Draw.mix(l.fill, l.fillHover, hv), Draw.mix(l.line, l.lineHover, hv));
        else if (hv > 0.01f) Draw.rect(g, x, yy, size, size, r, Draw.alpha(Tokens.SURFACE_HOVER, hv));
        int c = Draw.mix(boxed ? Tokens.TEXT : Tokens.MUTED, Tokens.TEXT, hv);
        Icons.draw(g, icon, x + (size - iconSize) / 2f, yy + (size - iconSize) / 2f, iconSize, c);
    }

    /** Schalter 46 × 26 (an: Akzent-Verlauf, Knopf gleitet in 280 ms) */
    public static void toggle(GuiGraphics g, Object key, float x, float y, float d, boolean on, boolean hover) {
        float t = Motion.to(key + ":on", on, Tokens.MS_TOGGLE);
        float hv = Motion.to(key + ":h", hover, Tokens.MS_HOVER);
        float w = 46 * d, h = 26 * d;
        Draw.rect(g, x, y, w, h, h / 2, Draw.mix(0x1AFFFFFF, 0x24FFFFFF, hv));
        if (t > 0.01f) Draw.accentRect(g, x, y, w, h, h / 2, t);
        Draw.ring(g, x, y, w, h, h / 2, 1, Draw.alpha(Draw.mix(0x24FFFFFF, Tokens.LINE_STRONG, hv), 1 - t));
        float knob = 18 * d, kx = x + 3 * d + 20 * d * t, ky = y + 3 * d;
        Draw.shadow(g, kx, ky + 2 * d, knob, knob, knob / 2, 6 * d, 0, 0x59000000);
        Draw.rect(g, kx, ky, knob, knob, knob / 2, Draw.mix(0xFFD9D5E6, 0xFFFFFFFF, t));
    }

    /** Schieberegler: Spur 5 px, gefüllt im Verlauf bis value (0..1), weißer Knopf mit Akzent-Ring */
    public static void slider(GuiGraphics g, Object key, float x, float y, float w, float d, float value, boolean hover) {
        float hv = Motion.to(key + ":h", hover, Tokens.MS_HOVER);
        value = Math.max(0, Math.min(1, value));
        float th = 5 * d, ty = y + 11 * d - th / 2;
        Draw.rect(g, x, ty, w, th, th / 2, 0x1FFFFFFF);
        if (value > 0) Draw.accentRect(g, x, ty, Math.max(th, w * value), th, th / 2, 1f);
        float knob = 18 * d, kx = x + w * value - knob / 2, ky = y + 11 * d - knob / 2;
        float ring = (4 + 2 * hv) * d;
        Draw.rect(g, kx - ring, ky - ring, knob + 2 * ring, knob + 2 * ring, knob / 2 + ring, Draw.alpha(Tokens.ACC, 0.28f + 0.07f * hv));
        Draw.rect(g, kx, ky, knob, knob, knob / 2, 0xFFFFFFFF);
    }

    /** Eingabefeld 46 hoch: vertieft, Fokus = Akzent-Rahmen + weicher Ring; blinkender Cursor */
    public static void field(GuiGraphics g, Object key, float x, float y, float w, float h, float d, String text, String placeholder,
                             String icon, boolean focused, boolean hover) {
        float f = Motion.to(key + ":f", focused, 200);
        float r = Tokens.RAD_MD * d;
        if (f > 0.01f) Draw.rect(g, x - 4 * d, y - 4 * d, w + 8 * d, h + 8 * d, r + 4 * d, Draw.alpha(0x299B5CFF, f));
        Draw.rect(g, x, y, w, h, r, Tokens.INSET);
        Draw.ring(g, x, y, w, h, r, 1, Draw.mix(hover ? Tokens.LINE_STRONG : Tokens.LINE, Tokens.ACC_LINE, f));
        float tx = x + 14 * d, fs = 15.5f * d;
        if (icon != null) {
            Icons.draw(g, icon, tx, y + h / 2 - 9 * d, 18 * d, Tokens.MUTED);
            tx += 28 * d;
        }
        boolean empty = text == null || text.isEmpty();
        String shown = Type.ellipsize(g, empty ? (placeholder == null ? "" : placeholder) : text, x + w - tx - 14 * d, fs, Tokens.MEDIUM);
        Type.middle(g, shown, tx, y + h / 2, fs, Tokens.MEDIUM, empty ? Tokens.SUBTLE : Tokens.TEXT);
        if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
            float cx = tx + (empty ? 0 : Type.width(g, shown, fs, Tokens.MEDIUM)) + 1 * d, ch = Type.capHeight(g, fs) * 1.4f;
            Draw.rect(g, cx, y + h / 2 - ch / 2, Math.max(1f / Draw.k(g), 1.5f * d), ch, 0, Tokens.ACC);
        }
    }

    /** Abzeichen (Pille, Akzent oder neutral); gibt die Breite zurück */
    public static float badge(GuiGraphics g, float x, float y, float h, float d, String text, boolean accent) {
        float fs = 12.5f * d;
        float w = Type.width(g, text, fs, Tokens.SEMIBOLD) + 18 * d;
        Draw.rect(g, x, y, w, h, h / 2, accent ? Tokens.ACC_SOFT : 0x12FFFFFF);
        Type.middle(g, text, x + 9 * d, y + h / 2, fs, Tokens.SEMIBOLD, accent ? Tokens.ACC_TEXT : Tokens.MUTED);
        return w;
    }
}
