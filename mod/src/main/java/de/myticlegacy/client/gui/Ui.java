package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.module.SimpleModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/** Zeichen-Helfer für das Mytic-Design: abgerundete Flächen, Farben, weiche Übergänge. */
public final class Ui {
    // Flächen-Farben: nicht final, Deckkraft und Stil (Design → Menü-Deckkraft / Menü-Stil) werden in refreshPalette() eingerechnet
    public static int PANEL = 0xF20A0716;
    public static int SURFACE = 0xFF130D24;
    public static int SURFACE_HOVER = 0xFF1F1638;
    public static final int LINE = 0xFF2C2050;
    public static final int TEXT = 0xFFF1EEF9;
    public static final int MUTED = 0xFF9A93B4;
    public static final int GREEN = 0xFF5BE38A;
    public static final int RED = 0xFFFF5C72;

    private static final Map<Object, Float> ANIMATIONS = new HashMap<>();
    private static long lastFrame = System.nanoTime();
    private static float frameSeconds;

    private Ui() {
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int accent() {
        return MyticClient.accent();
    }

    /** Einmal pro Bild aufrufen, damit Animationen unabhängig von den FPS gleich schnell sind. */
    public static void frame() {
        long now = System.nanoTime();
        frameSeconds = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
    }

    /** Letzter Zeitpunkt je Animation – jede Animation misst ihre eigene Zeit (frame() wird pro Bild mehrfach aufgerufen). */
    private static final Map<Object, Long> ANIMATED_AT = new HashMap<Object, Long>();

    /** Weicher Übergang zu einem Zielwert (0..1), z. B. für Hover-Effekte – unabhängig von den FPS. */
    public static float animate(Object key, boolean target, float speed) {
        long now = System.nanoTime();
        Long last = ANIMATED_AT.put(key, now);
        float dt = last == null ? 0f : Math.min(0.1f, (now - last) / 1_000_000_000f);
        Float stored = ANIMATIONS.get(key);
        float value = stored != null ? stored : (target ? 1f : 0f);
        float goal = target ? 1f : 0f;
        value += (goal - value) * Math.min(1f, dt * speed);
        if (Math.abs(goal - value) < 0.002f) value = goal;
        ANIMATIONS.put(key, value);
        return value;
    }

    public static int alpha(int color, float factor) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, factor)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Rechteck mit abgerundeten Ecken (Radius in GUI-Pixeln); im Menü-Stil „Eckig“ immer mit scharfen Ecken. */
    public static void rect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        if (square()) r = 0;
        else if (gold()) r = Math.min(r, 2);
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        // Rundung in echten Bildschirm-Pixeln mit geglätteter Kante (statt Treppen aus groben Menü-Pixeln)
        float k = Gfx.scaleX(g) * (float) de.myticlegacy.client.compat.Compat.guiScale();
        if (k > 1.2f) {
            smoothRect(g, x, y, w, h, r, color, k);
            return;
        }
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int dx = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x + dx, y + i, x + r, y + i + 1, color);
            g.fill(x + w - r, y + i, x + w - dx, y + i + 1, color);
            g.fill(x + dx, y + h - 1 - i, x + r, y + h - i, color);
            g.fill(x + w - r, y + h - 1 - i, x + w - dx, y + h - i, color);
        }
    }

    /**
     * Abgerundetes Rechteck, gezeichnet in echten Pixeln (k = Pixel je Zeichen-Einheit): Ecken Zeile für Zeile aus
     * dem Viertelkreis, das angeschnittene Pixel am Rand halbdurchsichtig – so sind Rundungen glatt statt treppig.
     */
    private static void smoothRect(GuiGraphics g, int x, int y, int w, int h, int r, int color, float k) {
        Gfx.push(g);
        Gfx.scale(g, 1f / k, 1f / k);
        int X = Math.round(x * k), Y = Math.round(y * k);
        int W = Math.round((x + w) * k) - X, H = Math.round((y + h) * k) - Y;
        int R = Math.max(1, Math.min(Math.round(r * k), Math.min(W, H) / 2));
        g.fill(X + R, Y, X + W - R, Y + H, color);
        g.fill(X, Y + R, X + R, Y + H - R, color);
        g.fill(X + W - R, Y + R, X + W, Y + H - R, color);
        for (int i = 0; i < R; i++) {
            double dy = R - i - 0.5;
            double edge = R - Math.sqrt(Math.max(0, (double) R * R - dy * dy)); // Einzug dieser Zeile
            int full = (int) Math.ceil(edge);
            float cover = (float) (full - edge); // Anteil des angeschnittenen Pixels
            int top = Y + i, bottom = Y + H - 1 - i;
            if (full < R) {
                g.fill(X + full, top, X + R, top + 1, color);
                g.fill(X + W - R, top, X + W - full, top + 1, color);
                g.fill(X + full, bottom, X + R, bottom + 1, color);
                g.fill(X + W - R, bottom, X + W - full, bottom + 1, color);
            }
            if (full > 0 && cover > 0.03f) {
                int soft = alpha(color, cover);
                g.fill(X + full - 1, top, X + full, top + 1, soft);
                g.fill(X + W - full, top, X + W - full + 1, top + 1, soft);
                g.fill(X + full - 1, bottom, X + full, bottom + 1, soft);
                g.fill(X + W - full, bottom, X + W - full + 1, bottom + 1, soft);
            }
        }
        Gfx.pop(g);
    }

    /** Kreis mit glatter Kante – auch im Stil „Eckig“ rund (Spielsteine, Marker) */
    public static void circle(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        float k = Gfx.scaleX(g) * (float) de.myticlegacy.client.compat.Compat.guiScale();
        if (k > 1.2f) {
            smoothRect(g, cx - r, cy - r, 2 * r + 1, 2 * r + 1, r, color, k);
            return;
        }
        for (int y = -r; y <= r; y++) {
            int w = (int) Math.round(Math.sqrt(r * r - y * y));
            g.fill(cx - w, cy + y, cx + w, cy + y + 1, color);
        }
    }

    /** Abgerundeter Rahmen (1 Pixel), durch zwei Flächen übereinander. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int color, int inner) {
        rect(g, x, y, w, h, r, color);
        rect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), inner);
    }

    public static void text(GuiGraphics g, String text, int x, int y, int color, boolean shadow) {
        g.drawString(font(), text, x, y, color, shadow);
    }

    public static void centered(GuiGraphics g, String text, int cx, int y, int color, boolean shadow) {
        g.drawString(font(), text, cx - font().width(text) / 2, y, color, shadow);
    }

    public static void scaled(GuiGraphics g, String text, float x, float y, float scale, int color, boolean shadow) {
        Gfx.push(g);
        Gfx.translate(g, x, y);
        Gfx.scale(g, scale, scale);
        g.drawString(font(), text, 0, 0, color, shadow);
        Gfx.pop(g);
    }

    /** Mytic-Logo als Text: "MYTIC" in der Akzentfarbe, "CLIENT" weiß, kursiv wirkend durch Versatz-Schatten. */
    public static void logo(GuiGraphics g, float x, float y, float scale) {
        Gfx.push(g);
        Gfx.translate(g, x, y);
        Gfx.scale(g, scale, scale);
        int w = font().width("MYTIC ");
        g.drawString(font(), "MYTIC", 1, 1, withAlpha(accent(), 90), false);
        g.drawString(font(), "MYTIC", 0, 0, accent(), false);
        g.drawString(font(), "CLIENT", w, 0, TEXT, false);
        Gfx.pop(g);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Schalter wie bei Lunar: Pille mit Knopf, animiert. */
    // ------------------------------------------------------------------ Lunar-Stil

    public static int L_PANEL = 0xF2141414;
    public static int L_CARD = 0xFF222222;
    public static int L_CARD_HOVER = 0xFF2C2C2C;
    public static int L_LINE = 0x22FFFFFF;
    public static int L_GREEN = 0xFF33B45A;
    /** Auswahl-Farbe (aktive Kategorie) */
    public static int L_SELECT = 0xFFFFFFFF;
    private static String paletteKey = "";
    public static final int L_RED = 0xFFD8434B;

    /** Menü im Lunar-Stil (Design → Menü-Stil, Standard) statt Neon. */
    public static boolean lunar() {
        refreshPalette();
        return MyticClient.theme == null || !MyticClient.theme.menuStyle.is("Neon"); // Glas nutzt das Lunar-Layout
    }

    /** Menü-Stil „Eckig“ (Standard): dunkelgrau, keine runden Ecken – nutzt das Lunar-Layout */
    public static boolean square() {
        return MyticClient.theme != null && MyticClient.theme.menuStyle.is("Eckig");
    }

    /** Menü-Stil „Glas“: milchig durchsichtig, helle Kanten, Akzentfarbe statt Grün */
    public static boolean glass() {
        return MyticClient.theme != null && MyticClient.theme.menuStyle.is("Glas");
    }

    /** Menü-Stil „Gold“: fast schwarz mit Goldkanten, kaum Rundungen – nutzt das Lunar-Layout */
    public static boolean gold() {
        return MyticClient.theme != null && MyticClient.theme.menuStyle.is("Gold");
    }

    /** Menü-Stil „Seitenleiste“: Kategorien links als Icon-Leiste, große Karten mit Schalter oben rechts */
    public static boolean sidebar() {
        return MyticClient.theme != null && MyticClient.theme.menuStyle.is("Seitenleiste");
    }

    /** Eckenradius der Mod-Karten je Stil (Lunar weicher, Glas am rundesten) */
    public static int cardRadius() {
        if (glass()) return 8;
        if (sidebar()) return 6;
        if (MyticClient.theme != null && MyticClient.theme.menuStyle.is("Lunar")) return 6;
        return 4;
    }

    /** Farbe für ausgewählte Kategorien/Markierungen: Gold, Blau (Seitenleiste/Lunar) oder Schalter-Farbe */
    public static int selection() {
        refreshPalette();
        return L_SELECT;
    }

    /** Textfarbe im Lunar-Layout (Gold: cremeweiß) */
    public static int lText() {
        return gold() ? 0xFFF5EFE1 : 0xFFFFFFFF;
    }

    /** Gedämpfte Textfarbe im Lunar-Layout */
    public static int lMuted() {
        return gold() ? 0xFFA39A85 : sidebar() ? 0xFF8B93A7 : 0xFF9A9A9A;
    }

    private static int a(int rgb, float alpha) {
        return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Farben nach Stil und Deckkraft neu berechnen (nur wenn sich etwas geändert hat) */
    public static void refreshPalette() {
        if (MyticClient.theme == null) return;
        float op = (float) (MyticClient.theme.menuOpacity.get() / 100.0);
        String style = MyticClient.theme.menuStyle.get();
        int acc = accent();
        int menuColor = MyticClient.theme.menuColor.get() | 0xFF000000;
        int highlight = MyticClient.theme.menuHighlight.get() | 0xFF000000;
        String key = style + op + acc + "/" + menuColor + "/" + highlight;
        if (key.equals(paletteKey)) return;
        paletteKey = key;
        boolean customHighlight = highlight != (SimpleModules.Theme.HIGHLIGHT_DEFAULT | 0xFF000000);
        if (style.equals("Glas")) {
            // milchig, hellere Kanten
            L_PANEL = a(0x0C0C14, 0.58f * op);
            L_CARD = a(0xFFFFFF, 0.08f + 0.05f * op);
            L_CARD_HOVER = a(0xFFFFFF, 0.15f + 0.07f * op);
            L_LINE = 0x40FFFFFF;
            L_GREEN = acc | 0xFF000000;
            L_SELECT = 0xFFFFFFFF;
        } else if (style.equals("Eckig")) {
            // dunkles Grau mit klaren Kanten
            L_PANEL = a(0x1B1B1D, 0.97f * op);
            L_CARD = a(0x26272A, Math.min(1f, op + 0.15f));
            L_CARD_HOVER = a(0x303135, Math.min(1f, op + 0.2f));
            L_LINE = 0x1FFFFFFF;
            L_GREEN = highlight;
            L_SELECT = highlight;
        } else if (style.equals("Gold")) {
            // fast schwarz, Gold für Kanten, Schalter und Auswahl
            int goldTone = customHighlight ? highlight : 0xFFD4AF37;
            L_PANEL = a(0x0E0D0B, 0.97f * op);
            L_CARD = a(0x17150F, Math.min(1f, op + 0.15f));
            L_CARD_HOVER = a(0x201D14, Math.min(1f, op + 0.2f));
            L_LINE = withAlpha(goldTone, 0x30);
            L_GREEN = goldTone;
            L_SELECT = goldTone;
        } else if (style.equals("Seitenleiste")) {
            // kühles Blaugrau, blaue Auswahl
            int blue = customHighlight ? highlight : 0xFF3B82F6;
            L_PANEL = a(0x11141C, 0.97f * op);
            L_CARD = a(0x171C27, Math.min(1f, op + 0.15f));
            L_CARD_HOVER = a(0x1E2433, Math.min(1f, op + 0.2f));
            L_LINE = 0x14FFFFFF;
            L_GREEN = blue;
            L_SELECT = blue;
        } else {
            // Lunar Soft: weicher, blaue Auswahl, grüne Schalter
            L_PANEL = a(0x141416, 0.93f * op);
            L_CARD = a(0x222225, Math.min(1f, op + 0.15f));
            L_CARD_HOVER = a(0x2C2C30, Math.min(1f, op + 0.2f));
            L_LINE = 0x1CFFFFFF;
            L_GREEN = customHighlight ? highlight : 0xFF3CCF7A;
            L_SELECT = 0xFF4F9CF9;
        }
        // Eigene Menü-Farbe (Design → Menü-Farbe): abgedunkelt, damit weiße Schrift lesbar bleibt; Karten etwas heller
        if (!style.equals("Glas") && !style.equals("Gold") && menuColor != SimpleModules.Theme.MENU_COLOR_DEFAULT) {
            int panel = mix(0xFF111113, menuColor, 0.35f);
            L_PANEL = a(panel, 0.97f * op);
            L_CARD = a(mix(panel, 0xFFFFFFFF, 0.07f), Math.min(1f, op + 0.15f));
            L_CARD_HOVER = a(mix(panel, 0xFFFFFFFF, 0.13f), Math.min(1f, op + 0.2f));
        }
        // Neon (Synthwave): tiefes Violett
        PANEL = a(0x100924, 0.94f * op);
        SURFACE = a(0x1A1033, Math.min(1f, op + 0.15f));
        SURFACE_HOVER = a(0x26164A, Math.min(1f, op + 0.2f));
    }

    /** Schlichtes Panel wie bei Lunar: dunkelgrau, feiner heller Rand, keine Leuchteffekte. */
    public static void lunarPanel(GuiGraphics g, int x, int y, int w, int h, int r) {
        if (glass()) {
            glassPanel(g, x, y, w, h, r);
            return;
        }
        rect(g, x - 2, y + 2, w + 4, h + 2, r + 2, 0x45000000);
        rect(g, x, y, w, h, r, gold() ? withAlpha(L_GREEN, 0x60) : sidebar() ? 0x1EFFFFFF : 0x2EFFFFFF);
        rect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), L_PANEL);
        // Gold: zweite, feine Innenkante wie ein Rahmen
        if (gold()) {
            int c = withAlpha(L_GREEN, 0x1C);
            g.fill(x + 3, y + 3, x + w - 3, y + 4, c);
            g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, c);
            g.fill(x + 3, y + 4, x + 4, y + h - 4, c);
            g.fill(x + w - 4, y + 4, x + w - 3, y + h - 4, c);
        }
    }

    /** Glas-Panel: weicher Schatten, helle Kante (oben heller), milchige Fläche, Lichtreflex und Akzent-Schimmer */
    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h, int r) {
        rect(g, x - 3, y + 3, w + 6, h + 3, r + 3, 0x30000000);
        rect(g, x, y, w, h, r, 0x40FFFFFF);
        rect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), L_PANEL);
        g.fillGradient(x + r, y + 1, x + w - r, y + 2, 0x80FFFFFF, 0x80FFFFFF);          // Lichtkante oben
        g.fillGradient(x + 1, y + 2, x + w - 1, y + Math.min(h - 2, 44), 0x16FFFFFF, 0x00FFFFFF); // Reflex
        g.fillGradient(x + 1, y + h - 40, x + w - 1, y + h - 1, 0x00000000, withAlpha(accent(), 34)); // Akzent-Schimmer unten
    }

    public static void toggle(GuiGraphics g, Object key, int x, int y, boolean on) {
        float t = animate(key, on, 14f);
        if (lunar()) {
            if (glass() && t > 0.01f) glow(g, x, y, 20, 10, 5, withAlpha(accent(), Math.round(120 * t)), 2, 0.6f);
            rect(g, x, y, 20, 10, 5, mix(glass() ? 0x55FFFFFF : gold() ? 0xFF2A261A : sidebar() ? 0xFF2A3142 : 0xFF4A4A4A, L_GREEN, t));
            rect(g, x + 1 + Math.round(10 * t), y + 1, 8, 8, 4, gold() ? mix(0xFF8A8270, 0xFF1A1608, t) : 0xFFFFFFFF);
            return;
        }
        if (t > 0.01f) glow(g, x, y, 20, 10, 5, withAlpha(accent2(), Math.round(200 * t)), 3, 0.8f);
        rect(g, x, y, 20, 10, 5, 0xFF2A2240);
        if (t > 0.01f) {
            int w = Math.max(10, Math.round(20 * t));
            rect(g, x, y, w, 10, 5, alpha(accent(), t));
            hGradient(g, x + 3, y + 1, w - 6, 8, alpha(accent(), t), alpha(accent2(), t));
        }
        int knob = x + 1 + Math.round(10 * t);
        rect(g, knob, y + 1, 8, 8, 4, 0xFFFFFFFF);
    }
    // ------------------------------------------------------------------ Neon-Design

    public static final int CYAN = 0xFF22E5FF;
    public static final int PINK = 0xFFFF2BD6;

    /** Zweite Neonfarbe zur Akzentfarbe: Cyan, bei bläulichen Akzenten Pink. */
    public static int accent2() {
        int a = accent();
        int r = (a >> 16) & 0xFF, gr = (a >> 8) & 0xFF, b = a & 0xFF;
        return b > 180 && gr > 140 && r < 120 ? PINK : CYAN;
    }

    /** Weiches Leuchten um ein Rechteck (vor der Fläche zeichnen). */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int color, int size, float strength) {
        int base = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, strength)));
        for (int i = size; i >= 1; i--) {
            int a = Math.round(base * 0.55f * (1f - (float) (i - 1) / size) / size * 2f);
            if (a > 0) rect(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, withAlpha(color, Math.min(255, a)));
        }
    }

    /** Waagerechter Farbverlauf von links nach rechts. */
    public static void hGradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        int step = Math.max(1, w / 48);
        for (int i = 0; i < w; i += step) {
            g.fill(x + i, y, x + Math.min(w, i + step), y + h, mix(left, right, (float) i / Math.max(1, w - 1)));
        }
    }

    /** Neon-Linie: zweite Neonfarbe, Akzent, Pink – mit schwachem Schein darüber und darunter. */
    public static void neonLine(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        int c1 = alpha(accent2(), alpha), c2 = alpha(accent(), alpha), c3 = alpha(PINK, alpha);
        int half = w / 2;
        hGradient(g, x, y - 1, half, 1, withAlpha(c1, 50), withAlpha(c2, 50));
        hGradient(g, x + half, y - 1, w - half, 1, withAlpha(c2, 50), withAlpha(c3, 50));
        hGradient(g, x, y, half, h, c1, c2);
        hGradient(g, x + half, y, w - half, h, c2, c3);
        hGradient(g, x, y + h, half, 1, withAlpha(c1, 50), withAlpha(c2, 50));
        hGradient(g, x + half, y + h, w - half, 1, withAlpha(c2, 50), withAlpha(c3, 50));
    }

    /** Ecken-Markierungen wie bei einem HUD: oben links in c1, unten rechts in c2. */
    public static void corners(GuiGraphics g, int x, int y, int w, int h, int len, int c1, int c2) {
        g.fill(x, y, x + len, y + 1, c1);
        g.fill(x, y, x + 1, y + len, c1);
        g.fill(x + w - len, y + h - 1, x + w, y + h, c2);
        g.fill(x + w - 1, y + h - len, x + w, y + h, c2);
    }

    /** Leuchtende Schrift: weicher Schein in der Textfarbe hinter dem Text. */
    public static void glowText(GuiGraphics g, String text, int x, int y, int color, float strength) {
        int halo = withAlpha(color, Math.round(70 * strength * (((color >>> 24) & 0xFF) / 255f)));
        g.drawString(font(), text, x - 1, y, halo, false);
        g.drawString(font(), text, x + 1, y, halo, false);
        g.drawString(font(), text, x, y - 1, halo, false);
        g.drawString(font(), text, x, y + 1, halo, false);
        g.drawString(font(), text, x, y, color, false);
    }

    /** Panel im Neon-Stil: Leuchten, dunkle Fläche, Neon-Kante oben, HUD-Ecken. */
    public static void neonPanel(GuiGraphics g, int x, int y, int w, int h, int r, float alpha) {
        glow(g, x, y, w, h, r, withAlpha(accent(), Math.round(255 * alpha)), 8, 0.5f);
        rect(g, x, y, w, h, r, alpha(mix(0xFF0A0716, accent(), 0.35f), alpha));
        rect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), alpha(PANEL, alpha));
        neonLine(g, x + r + 4, y, w - 2 * r - 8, 1, alpha);
        corners(g, x + 3, y + 3, w - 6, h - 6, 8, alpha(accent2(), alpha), alpha(PINK, alpha));
    }
    /** 1-Pixel-Rahmen mit leicht abgerundeten Ecken (Lunar-Knöpfe). */
    public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + 1, color);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + 1, y + h - 2, color);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
    }
}
