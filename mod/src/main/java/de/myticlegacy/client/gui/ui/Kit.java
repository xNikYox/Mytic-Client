package de.myticlegacy.client.gui.ui;

import de.myticlegacy.client.compat.Gfx;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sofort-Oberfläche des neuen Designs: Bildschirme zeichnen jedes Bild alle Bausteine (Fenster, Buttons, Schalter,
 * Regler, Felder, Segmente, Navigation, Auswahllisten, Dialoge) und melden dabei ihre Klickflächen an. Hover gilt
 * nur für die oberste Fläche unter der Maus (aus dem letzten Bild) – Dialoge und aufgeklappte Listen verdecken also,
 * was darunter liegt. Maße in Design-Pixeln × d, Koordinaten in Zeichen-Einheiten (Bildschirme mit pixelScale 1:
 * echte Pixel). Reine Zeichen- und Eingabelogik ohne Minecraft-Bildschirm, daher für alle Versionen gleich.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Kit {
    /** Tasten, die der Bildschirm übersetzt (GLFW bzw. LWJGL 2 unterscheiden sich) */
    public static final int KEY_BACKSPACE = 1, KEY_ENTER = 2, KEY_ESCAPE = 3, KEY_DELETE_WORD = 4, KEY_TAB = 5;

    public interface FloatSink {
        void accept(float value);
    }

    public interface IntSink {
        void accept(int value);
    }

    public interface TextSink {
        void accept(String value);
    }

    /** Eingabefeld-Zustand */
    public static final class Field {
        public String text = "";
        public final int max;
        /** Enter im Feld */
        public Runnable onEnter;
        /** Erlaubte Zeichen (null = alle druckbaren) */
        public String allowed;

        public Field(int max) {
            this.max = max;
        }

        public boolean isEmpty() {
            return text.trim().isEmpty();
        }
    }

    /** Bildlauf eines Bereichs (weich, Mausrad und Griff) */
    public static final class Scroll {
        public float pos, target;
        float content, view;

        public void reset() {
            pos = target = 0;
        }

        void clamp() {
            float max = Math.max(0, content - view);
            if (target < 0) target = 0;
            if (target > max) target = max;
            if (pos > max) pos = max;
        }
    }

    private static final class Hit {
        final String id;
        final float x, y, w, h;
        final Runnable action, right;
        final FloatSink drag;
        final boolean dragY;
        final Field field;

        Hit(String id, float x, float y, float w, float h, Runnable action, Runnable right, FloatSink drag, boolean dragY, Field field) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
            this.right = right;
            this.drag = drag;
            this.dragY = dragY;
            this.field = field;
        }

        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private static final class Region {
        final float x, y, w, h;
        final Scroll scroll;

        Region(float x, float y, float w, float h, Scroll scroll) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.scroll = scroll;
        }
    }

    // ------------------------------------------------------------------ Zustand des Bildes

    public GuiGraphics g;
    /** Design-Faktor (Design-Pixel → Einheiten) */
    public float d = 1;
    public int mx, my, vw, vh;
    private final String prefix;
    private List<Hit> hits = new ArrayList<Hit>(), last = new ArrayList<Hit>();
    private List<Region> regions = new ArrayList<Region>(), lastRegions = new ArrayList<Region>();
    private final List<Runnable> overlays = new ArrayList<Runnable>();
    private float[] clip;
    private String pressed;
    private Hit dragging;
    private Field focus;
    private String openMenu;
    private Scroll dragScroll;
    private float dragScrollGrab;
    private Region dragRegion;

    public Kit(String prefix) {
        this.prefix = prefix + ":";
    }

    public void begin(GuiGraphics g, int vw, int vh, float d, int mx, int my) {
        this.g = g;
        this.vw = vw;
        this.vh = vh;
        this.d = d;
        this.mx = mx;
        this.my = my;
        List<Hit> t = last;
        last = hits;
        hits = t;
        hits.clear();
        List<Region> r = lastRegions;
        lastRegions = regions;
        regions = r;
        regions.clear();
        overlays.clear();
        clip = null;
    }

    /** Aufgeschobene Ebenen (aufgeklappte Listen, Tooltips) zuletzt zeichnen */
    public void end() {
        for (int i = 0; i < overlays.size(); i++) overlays.get(i).run();
        overlays.clear();
    }

    /** Ganz oben zeichnen (nach allem anderen) */
    public void later(Runnable r) {
        overlays.add(r);
    }

    public void forget() {
        Motion.forget(prefix);
    }

    public String key(String id) {
        return prefix + id;
    }

    // ------------------------------------------------------------------ Treffer

    private Hit topLast(double x, double y) {
        for (int i = last.size() - 1; i >= 0; i--) if (last.get(i).contains(x, y)) return last.get(i);
        return null;
    }

    /** Liegt die Maus über dieser Fläche (oberste Fläche im letzten Bild)? */
    public boolean hover(String id) {
        if (dragging != null) return dragging.id.equals(id);
        Hit h = topLast(mx, my);
        return h != null && h.id.equals(id);
    }

    public boolean pressed(String id) {
        return id.equals(pressed) && hover(id);
    }

    /** Reine Geometrie (ohne Verdecken) */
    public boolean over(float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private void add(String id, float x, float y, float w, float h, Runnable action, Runnable right, FloatSink drag, boolean dragY, Field field) {
        if (clip != null) {
            float x2 = Math.min(x + w, clip[2]), y2 = Math.min(y + h, clip[3]);
            x = Math.max(x, clip[0]);
            y = Math.max(y, clip[1]);
            w = x2 - x;
            h = y2 - y;
            if (w <= 0 || h <= 0) return;
        }
        hits.add(new Hit(id, x, y, w, h, action, right, drag, dragY, field));
    }

    /** Klickfläche anmelden; gibt hover(id) zurück */
    public boolean hit(String id, float x, float y, float w, float h, Runnable action) {
        add(id, x, y, w, h, action, null, null, false, null);
        return hover(id);
    }

    /** Klickfläche mit Rechtsklick */
    public boolean hit(String id, float x, float y, float w, float h, Runnable action, Runnable right) {
        add(id, x, y, w, h, action, right, null, false, null);
        return hover(id);
    }

    /** Ziehfläche: sink bekommt die Mausposition (x bzw. y) beim Drücken und Ziehen */
    public boolean dragArea(String id, float x, float y, float w, float h, boolean vertical, FloatSink sink) {
        add(id, x, y, w, h, null, null, sink, vertical, null);
        return hover(id);
    }

    /** Fläche, die Maus und Klicks schluckt (z. B. Dialog-Hintergrund) */
    public void block(float x, float y, float w, float h, Runnable onClick) {
        add("~block" + hits.size(), x, y, w, h, onClick, null, null, false, null);
    }

    public void clip(float x, float y, float w, float h) {
        clip = new float[]{x, y, x + w, y + h};
        Gfx.scissor(g, (int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    public void unclip() {
        clip = null;
        Gfx.noScissor(g);
    }

    // ------------------------------------------------------------------ Eingaben (vom Bildschirm)

    /** Maustaste gedrückt (0 links, 1 rechts); true = verbraucht */
    public boolean mouseDown(double x, double y, int button) {
        mx = (int) Math.round(x);
        my = (int) Math.round(y);
        Hit h = topLast(x, y);
        if (button == 0) {
            Field before = focus;
            focus = h != null && h.field != null ? h.field : null;
            if (h == null || !(h.id.startsWith("dd:") || h.id.startsWith("ddm:"))) {
                if (openMenu != null && (h == null || !h.id.equals("dd:" + openMenu))) {
                    openMenu = null;
                    if (h == null || h.id.startsWith("~block")) return true;
                }
            }
            if (h == null) return before != null;
            pressed = h.id;
            if (h.drag != null) {
                dragging = h;
                h.drag.accept((float) (h.dragY ? y : x));
            }
            if (h.id.startsWith("sb:")) startScrollDrag(h, y);
            return true;
        }
        if (button == 1 && h != null && h.right != null) {
            h.right.run();
            return true;
        }
        return h != null;
    }

    public boolean mouseDrag(double x, double y) {
        mx = (int) Math.round(x);
        my = (int) Math.round(y);
        if (dragScroll != null) {
            Region r = dragRegion;
            float thumb = thumb(r.h, dragScroll.content), range = r.h - thumb;
            if (range > 0) {
                float f = (float) ((y - dragScrollGrab - r.y) / range);
                dragScroll.target = dragScroll.pos = Math.max(0, Math.min(1, f)) * Math.max(0, dragScroll.content - dragScroll.view);
            }
            return true;
        }
        if (dragging != null) {
            dragging.drag.accept((float) (dragging.dragY ? y : x));
            return true;
        }
        return false;
    }

    public boolean mouseUp(double x, double y, int button) {
        mx = (int) Math.round(x);
        my = (int) Math.round(y);
        if (dragScroll != null) {
            dragScroll = null;
            pressed = null;
            return true;
        }
        if (dragging != null) {
            Hit h = dragging;
            dragging = null;
            pressed = null;
            if (h.action != null) h.action.run(); // Ende des Ziehens (z. B. speichern)
            return true;
        }
        String p = pressed;
        pressed = null;
        if (p == null || button != 0) return false;
        Hit h = topLast(x, y);
        if (h != null && h.id.equals(p) && h.action != null) h.action.run();
        return true;
    }

    /** Mausrad über einem Bildlauf-Bereich */
    public boolean wheel(double x, double y, double amount) {
        for (int i = lastRegions.size() - 1; i >= 0; i--) {
            Region r = lastRegions.get(i);
            if (x >= r.x && y >= r.y && x < r.x + r.w && y < r.y + r.h) {
                r.scroll.target -= (float) amount * 90 * d;
                r.scroll.clamp();
                return true;
            }
        }
        return false;
    }

    public boolean typing() {
        return focus != null;
    }

    public Field focused() {
        return focus;
    }

    public void focus(Field f) {
        focus = f;
    }

    /** Taste (KEY_…) für das fokussierte Feld */
    public boolean key(int key) {
        if (openMenu != null && key == KEY_ESCAPE) {
            openMenu = null;
            return true;
        }
        if (focus == null) return false;
        if (key == KEY_BACKSPACE) {
            if (!focus.text.isEmpty()) focus.text = focus.text.substring(0, focus.text.length() - 1);
        } else if (key == KEY_DELETE_WORD) {
            focus.text = "";
        } else if (key == KEY_ENTER) {
            Field f = focus;
            if (f.onEnter != null) f.onEnter.run();
        } else if (key == KEY_ESCAPE || key == KEY_TAB) {
            focus = null;
        } else {
            return false;
        }
        return true;
    }

    public boolean typed(String s) {
        if (focus == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < ' ' || c == 127) continue;
            if (focus.allowed != null && focus.allowed.indexOf(c) < 0) continue;
            if (focus.text.length() < focus.max) focus.text += c;
        }
        return true;
    }

    public boolean menuOpen() {
        return openMenu != null;
    }

    // ------------------------------------------------------------------ Flächen

    /** Fenster: weicher Schatten, Glas, Rahmen, Lichtkante (Radius 28) */
    public void window(float x, float y, float w, float h, boolean opaque) {
        Draw.shadow(g, x, y + 30 * d, w, h, Tokens.RAD_XL * d, 80 * d, -30 * d, 0xA6000000);
        Draw.glass(g, x, y, w, h, Tokens.RAD_XL * d, opaque ? Tokens.GLASS_OPAQUE : Tokens.GLASS, Tokens.LINE);
    }

    /** Glas-Karte (Radius r Design-Pixel) */
    public void card(float x, float y, float w, float h, float r, int fill) {
        Draw.glass(g, x, y, w, h, r * d, fill, Tokens.LINE);
    }

    public void hline(float x, float y, float w) {
        Draw.rect(g, x, y, w, Math.max(1f / Draw.k(g), 1 * d), 0, Tokens.LINE);
    }

    public void vline(float x, float y, float h) {
        Draw.rect(g, x, y, Math.max(1f / Draw.k(g), 1 * d), h, 0, Tokens.LINE);
    }

    /** Überschrift in Großbuchstaben (12,5 / 600 / gedämpft) */
    public void caption(String text, float x, float y) {
        Type.draw(g, text.toUpperCase(java.util.Locale.ROOT), x, y, 12.5f * d, Tokens.SEMIBOLD, Tokens.SUBTLE);
    }

    /** Text (Größe in Design-Pixeln), y = Oberkante der Großbuchstaben */
    public void text(String s, float x, float y, float size, int weight, int color) {
        Type.draw(g, s, x, y, size * d, weight, color);
    }

    /** Text senkrecht mittig um cy, auf maxW gekürzt (maxW ≤ 0: ungekürzt) */
    public void textMid(String s, float x, float cy, float size, int weight, int color, float maxW) {
        if (maxW > 0) s = Type.ellipsize(g, s, maxW, size * d, weight);
        Type.middle(g, s, x, cy, size * d, weight, color);
    }

    public void textRight(String s, float x, float cy, float size, int weight, int color) {
        Type.middle(g, s, x - Type.width(g, s, size * d, weight), cy, size * d, weight, color);
    }

    public void textCenter(String s, float cx, float cy, float size, int weight, int color) {
        Type.middle(g, s, cx - Type.width(g, s, size * d, weight) / 2f, cy, size * d, weight, color);
    }

    public float width(String s, float size, int weight) {
        return Type.width(g, s, size * d, weight);
    }

    /** Mehrzeiliger Text (Umbruch an Wörtern), gibt die Zeilenzahl zurück */
    public int paragraph(String s, float x, float y, float w, float size, int weight, int color, float lineH, int maxLines) {
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        int n = 0;
        for (int i = 0; i <= words.length; i++) {
            String next = i < words.length ? (line.length() == 0 ? words[i] : line + " " + words[i]) : null;
            if (next != null && width(next, size, weight) <= w) {
                line.setLength(0);
                line.append(next);
                continue;
            }
            if (line.length() > 0) {
                boolean lastLine = n == maxLines - 1 && i < words.length;
                String out = lastLine ? Type.ellipsize(g, line + " " + words[i], w, size * d, weight) : line.toString();
                Type.draw(g, out, x, y + n * lineH * d, size * d, weight, color);
                n++;
                if (lastLine) return n;
            }
            line.setLength(0);
            if (i < words.length) line.append(words[i]);
        }
        return n;
    }

    // ------------------------------------------------------------------ Bausteine

    private float fs(float h) {
        return h >= 46 * d ? 16 * d : h >= 36 * d ? 14.5f * d : 13.5f * d;
    }

    private float rad(float h) {
        return h >= 46 * d ? Tokens.RAD_MD * d : h >= 36 * d ? Tokens.RAD_SM * d : 9 * d;
    }

    /** Breite eines Buttons mit Text (und Icon) */
    public float buttonWidth(String label, String icon, float h) {
        float f = fs(h);
        float pad = h >= 46 * d ? 20 * d : h >= 36 * d ? 14 * d : 12 * d;
        float iw = icon == null ? 0 : 22 * d * (f / (18f * d)) + (label == null || label.isEmpty() ? 0 : 10 * d);
        return Type.width(g, label == null ? "" : label, f, Tokens.SEMIBOLD) + iw + 2 * pad;
    }

    /** Button (kind = Widgets.PRIMARY/SECONDARY/GHOST/DANGER); action null = deaktiviert */
    public boolean button(String id, float x, float y, float w, float h, String label, String icon, int kind, Runnable action) {
        boolean disabled = action == null;
        boolean hv = !disabled && hit(id, x, y, w, h, action);
        if (disabled) add(id, x, y, w, h, null, null, null, false, null);
        if (disabled) {
            Draw.glass(g, x, y, w, h, rad(h), 0x08FFFFFF, 0x10FFFFFF);
            float f = fs(h);
            float iconSize = 22 * d * (f / (18f * d)), gap = 10 * d;
            float tw = label == null ? 0 : Type.width(g, label, f, Tokens.SEMIBOLD);
            float total = tw + (icon != null ? iconSize + (tw > 0 ? gap : 0) : 0);
            float cx = x + w / 2f - total / 2f;
            if (icon != null) {
                Icons.draw(g, icon, cx, y + h / 2f - iconSize / 2f, iconSize, Tokens.SUBTLE);
                cx += iconSize + gap;
            }
            if (label != null) Type.middle(g, label, cx, y + h / 2f, f, Tokens.SEMIBOLD, Tokens.SUBTLE);
            return false;
        }
        Widgets.button(g, key(id), x, y, w, h, d, label, icon, kind, null, fs(h), rad(h), hv, pressed(id));
        return hv;
    }

    /** Button mit automatischer Breite; gibt die Breite zurück */
    public float button(String id, float x, float y, float h, String label, String icon, int kind, Runnable action) {
        float w = buttonWidth(label, icon, h);
        button(id, x, y, w, h, label, icon, kind, action);
        return w;
    }

    /** Icon-Button (40 bzw. 34), boxed = mit Glas-Fläche */
    public boolean iconButton(String id, float x, float y, float size, String icon, boolean boxed, Runnable action) {
        boolean hv = hit(id, x, y, size, size, action);
        float is = size >= 38 * d ? 20 * d : 16 * d;
        Widgets.iconButton(g, key(id), x, y, size, d, icon, is, boxed, null, hv, pressed(id));
        return hv;
    }

    /** Schalter 46 × 26 */
    public boolean toggle(String id, float x, float y, boolean on, Runnable action) {
        boolean hv = hit(id, x, y, 46 * d, 26 * d, action);
        Widgets.toggle(g, key(id), x, y, d, on, hv);
        return hv;
    }

    /** Schieberegler (Wert 0..1); onChange beim Ziehen, onDone beim Loslassen */
    public boolean slider(String id, final float x, float y, final float w, float value, final FloatSink onChange, Runnable onDone) {
        add(id, x - 9 * d, y - 4 * d, w + 18 * d, 30 * d, onDone, null, new FloatSink() {
            public void accept(float px) {
                onChange.accept(Math.max(0, Math.min(1, (px - x) / w)));
            }
        }, false, null);
        boolean hv = hover(id);
        Widgets.slider(g, key(id), x, y, w, d, value, hv);
        return hv;
    }

    /** Eingabefeld (Höhe 46 bzw. 40) */
    public boolean field(String id, Field f, float x, float y, float w, float h, String placeholder, String icon) {
        add(id, x, y, w, h, null, null, null, false, f);
        boolean hv = hover(id);
        Widgets.field(g, key(id), x, y, w, h, d, f.text, placeholder, icon, focus == f, hv);
        return hv;
    }

    /** Zahl in einer Pille (z. B. neben Kategorien); gibt die Breite zurück */
    public float count(String text, float x, float cy, boolean accent) {
        float fs = 12 * d, w = Type.width(g, text, fs, Tokens.SEMIBOLD) + 14 * d, h = 18 * d;
        if (accent) Draw.accentRect(g, x, cy - h / 2, w, h, h / 2, 1f);
        else Draw.rect(g, x, cy - h / 2, w, h, h / 2, 0x14FFFFFF);
        Type.middle(g, text, x + 7 * d, cy, fs, Tokens.SEMIBOLD, accent ? 0xFFFFFFFF : Tokens.MUTED);
        return w;
    }

    public float countWidth(String text) {
        return Type.width(g, text, 12 * d, Tokens.SEMIBOLD) + 14 * d;
    }

    /**
     * Segment-Schalter (Höhe 44): Beschriftungen, optional Icons und Zahlen; gibt die Breite zurück.
     * counts[i] mit Präfix „!“ = Akzent-Zahl.
     */
    public float seg(String id, float x, float y, String[] labels, String[] icons, String[] counts, int active, final IntSink onPick) {
        float pad = 4 * d, bh = 34 * d, fs = 14.5f * d, gap = 2 * d;
        float[] widths = new float[labels.length];
        float total = pad * 2 - gap;
        for (int i = 0; i < labels.length; i++) {
            float w = 28 * d + Type.width(g, labels[i], fs, Tokens.SEMIBOLD);
            if (icons != null && icons[i] != null) w += 16 * d + 8 * d;
            if (counts != null && counts[i] != null) w += 8 * d + countWidth(counts[i].replace("!", ""));
            widths[i] = w;
            total += w + gap;
        }
        Draw.rect(g, x, y, total, bh + 2 * pad, 12 * d, Tokens.INSET);
        Draw.ring(g, x, y, total, bh + 2 * pad, 12 * d, 1, Tokens.LINE);
        float bx = x + pad;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            String hid = id + "." + i;
            boolean hv = hit(hid, bx, y + pad, widths[i], bh, new Runnable() {
                public void run() {
                    onPick.accept(idx);
                }
            });
            float a = Motion.to(key(hid) + ":a", i == active, 200);
            if (a > 0.01f) {
                Draw.rect(g, bx, y + pad, widths[i], bh, 9 * d, Draw.alpha(0x1CFFFFFF, a));
                Draw.rect(g, bx + 9 * d, y + pad, widths[i] - 18 * d, Math.max(1f / Draw.k(g), d), 0, Draw.alpha(0x14FFFFFF, a));
            }
            int c = Draw.mix(hv ? Tokens.TEXT : Tokens.MUTED, 0xFFFFFFFF, a);
            float tx = bx + 14 * d, cy = y + pad + bh / 2;
            if (icons != null && icons[i] != null) {
                Icons.draw(g, icons[i], tx, cy - 8 * d, 16 * d, c);
                tx += 24 * d;
            }
            Type.middle(g, labels[i], tx, cy, fs, Tokens.SEMIBOLD, c);
            tx += Type.width(g, labels[i], fs, Tokens.SEMIBOLD) + 8 * d;
            if (counts != null && counts[i] != null) count(counts[i].replace("!", ""), tx, cy, counts[i].startsWith("!"));
            bx += widths[i] + gap;
        }
        return total;
    }

    /** Navigationseintrag (44 hoch) mit Akzent-Strich links, wenn aktiv */
    public boolean nav(String id, float x, float y, float w, String icon, String label, String count, boolean active, Runnable action) {
        float h = 44 * d;
        boolean hv = hit(id, x, y, w, h, action);
        float a = Motion.to(key(id) + ":a", active, 200), hvT = Motion.to(key(id) + ":h", hv, Tokens.MS_HOVER);
        if (a > 0.01f || hvT > 0.01f) Draw.rect(g, x, y, w, h, 12 * d, Draw.mix(Draw.alpha(Tokens.SURFACE, hvT), 0x14FFFFFF, a));
        if (a > 0.01f) {
            float bar = 3 * d;
            float k = Draw.begin(g);
            int X = Math.round((x - 12 * d) * k), Y = Math.round((y + 11 * d) * k), W = Math.max(2, Math.round(bar * k)), H = Math.round((h - 22 * d) * k);
            Draw.pxGradient(g, X, Y, W, H, W / 2, Draw.alpha(0xFFFFFFFF, a), 0, true);
            Draw.end(g);
        }
        int c = Draw.mix(Draw.mix(Tokens.MUTED, Tokens.TEXT, hvT), 0xFFFFFFFF, a);
        float tx = x + 12 * d;
        if (icon != null) {
            Icons.draw(g, icon, tx, y + h / 2 - 10 * d, 20 * d, c);
            tx += 32 * d;
        }
        float right = x + w - 12 * d;
        if (count != null) {
            float cw = countWidth(count);
            count(count, right - cw, y + h / 2, false);
            right -= cw + 8 * d;
        }
        Type.middle(g, Type.ellipsize(g, label, right - tx, 15.5f * d, Tokens.SEMIBOLD), tx, y + h / 2, 15.5f * d, Tokens.SEMIBOLD, c);
        return hv;
    }

    /** Tasten-Abzeichen (Pixelschrift); gibt die Breite zurück */
    public float kbd(String text, float x, float y, float h, boolean listening, boolean hover) {
        int s = Type.pixelScale(g, Math.min(h * 0.32f, 9 * d));
        float tw = Type.pixelWidth(g, text, s, 0);
        float w = Math.max(40 * d * h / (34 * d), tw + 24 * d * h / (34 * d));
        float r = 9 * d * h / (34 * d);
        int line = listening ? Tokens.ACC : hover ? Tokens.ACC_LINE : Tokens.LINE_STRONG;
        Draw.rect(g, x, y, w, h, r, line);
        Draw.rect(g, x + 1f / Draw.k(g), y + 1f / Draw.k(g), w - 2f / Draw.k(g), h - Math.max(2f / Draw.k(g), 3 * d), Math.max(0, r - 1f / Draw.k(g)),
                listening ? 0xFF2B2040 : 0xFF221E2C);
        float th = 7f * s / Draw.k(g);
        Type.pixel(g, text, x + (w - tw) / 2f, y + (h - 2 * d - th) / 2f, s, 0, Tokens.TEXT);
        return w;
    }

    /** Aufklappliste (40 hoch); die Liste liegt über allem */
    public boolean dropdown(final String id, float x, float y, float w, final String[] options, String selected, final TextSink onPick) {
        float h = 40 * d;
        boolean open = id.equals(openMenu);
        boolean hv = hit("dd:" + id, x, y, w, h, new Runnable() {
            public void run() {
                openMenu = id.equals(openMenu) ? null : id;
            }
        });
        Draw.rect(g, x, y, w, h, Tokens.RAD_SM * d, Tokens.INSET);
        Draw.ring(g, x, y, w, h, Tokens.RAD_SM * d, 1, open ? Tokens.ACC_LINE : hv ? Tokens.LINE_STRONG : Tokens.LINE);
        float fs = 14.5f * d;
        Type.middle(g, Type.ellipsize(g, selected, w - 44 * d, fs, Tokens.MEDIUM), x + 14 * d, y + h / 2, fs, Tokens.MEDIUM, Tokens.TEXT);
        Icons.draw(g, open ? "chevD" : "chevD", x + w - 28 * d, y + h / 2 - 8 * d, 16 * d, Tokens.MUTED);
        if (!open) return hv;
        final float mx0 = x, my0 = y + h + 6 * d, mw = Math.max(w, 190 * d);
        final String sel = selected;
        later(new Runnable() {
            public void run() {
                float rowH = 36 * d, mh = options.length * rowH + 12 * d;
                float top = my0 + mh > vh - 8 * d ? my0 - h - 12 * d - mh : my0;
                Draw.shadow(g, mx0, top + 16 * d, mw, mh, 14 * d, 40 * d, -10 * d, 0x99000000);
                Draw.glass(g, mx0, top, mw, mh, 14 * d, 0xF0141020, Tokens.LINE_STRONG);
                for (int i = 0; i < options.length; i++) {
                    final String o = options[i];
                    float ry = top + 6 * d + i * rowH;
                    boolean rh = hit("ddm:" + id + "." + i, mx0 + 6 * d, ry, mw - 12 * d, rowH, new Runnable() {
                        public void run() {
                            openMenu = null;
                            onPick.accept(o);
                        }
                    });
                    if (rh) Draw.rect(g, mx0 + 6 * d, ry, mw - 12 * d, rowH, 8 * d, Tokens.SURFACE_HOVER);
                    boolean s = o.equals(sel);
                    Type.middle(g, Type.ellipsize(g, o, mw - 50 * d, 14.5f * d, Tokens.MEDIUM), mx0 + 16 * d, ry + rowH / 2, 14.5f * d, Tokens.MEDIUM,
                            s ? 0xFFFFFFFF : rh ? Tokens.TEXT : Tokens.MUTED);
                    if (s) {
                        float k = Draw.begin(g);
                        int D = Math.max(3, Math.round(8 * d * k));
                        Draw.pxGradient(g, Math.round((mx0 + mw - 24 * d) * k), Math.round((ry + rowH / 2) * k) - D / 2, D, D, D / 2, 0xFFFFFFFF, 0, true);
                        Draw.end(g);
                    }
                }
            }
        });
        return hv;
    }

    /** Abzeichen (Höhe 24): Akzent oder neutral; gibt die Breite zurück */
    public float badge(String text, float x, float y, boolean accent) {
        return Widgets.badge(g, x, y, 24 * d, d, text, accent);
    }

    /** Status-Abzeichen in Farbe (ok/warn/bad), optional mit Icon; gibt die Breite zurück */
    public float badge(String text, String icon, float x, float y, int color) {
        float fs = 12.5f * d, h = 24 * d;
        float w = Type.width(g, text, fs, Tokens.SEMIBOLD) + 18 * d + (icon != null ? 18 * d : 0);
        Draw.rect(g, x, y, w, h, h / 2, Draw.alpha(color, 0.13f));
        float tx = x + 9 * d;
        int tc = Draw.mix(color, 0xFFFFFFFF, 0.55f);
        if (icon != null) {
            Icons.draw(g, icon, tx, y + h / 2 - 7 * d, 14 * d, tc);
            tx += 18 * d;
        }
        Type.middle(g, text, tx, y + h / 2, fs, Tokens.SEMIBOLD, tc);
        return w;
    }

    /** Spielerkopf (abgerundet 9/40) mit optionalem Status-Punkt (0 = keiner) */
    public void head(int[] pixels, String name, float x, float y, float size, int dot, boolean grey) {
        float r = size * 9f / 40f;
        Draw.head(g, pixels, name, x, y, size, r);
        if (grey) Draw.rect(g, x, y, size, size, r, 0x80140F20);
        Draw.ring(g, x, y, size, size, r, 1, 0x1AFFFFFF);
        if (dot != 0) {
            float dr = Math.max(4 * d, size * 0.14f);
            Draw.circle(g, x + size - dr * 0.6f, y + size - dr * 0.6f, dr + 2.5f * d, 0xFF141020);
            Draw.circle(g, x + size - dr * 0.6f, y + size - dr * 0.6f, dr, dot);
        }
    }

    /** Tooltip (dunkles Glas) mittig über/unter einem Punkt */
    public void tooltip(final String text, final float cx, final float y) {
        later(new Runnable() {
            public void run() {
                float fs = 13 * d, w = Type.width(g, text, fs, Tokens.MEDIUM) + 20 * d, h = 28 * d;
                float x = Math.max(4 * d, Math.min(vw - w - 4 * d, cx - w / 2f));
                Draw.glass(g, x, y, w, h, 8 * d, Tokens.GLASS_STRONG, Tokens.LINE_STRONG);
                Type.middle(g, text, x + 10 * d, y + h / 2f, fs, Tokens.MEDIUM, Tokens.TEXT);
            }
        });
    }

    // ------------------------------------------------------------------ Bildlauf

    private static float thumb(float view, float content) {
        return Math.max(view * 0.12f, view * view / Math.max(1, content));
    }

    /**
     * Bildlauf-Bereich: vor dem Zeichnen des Inhalts aufrufen (weiches Nachziehen), gibt den Versatz zurück.
     * content = Höhe des Inhalts. Nach dem Inhalt scrollbar() für den Griff.
     */
    public float scroll(Scroll s, float x, float y, float w, float h, float content) {
        s.content = content;
        s.view = h;
        s.clamp();
        s.pos += (s.target - s.pos) * 0.35f;
        if (Math.abs(s.target - s.pos) < 0.3f) s.pos = s.target;
        regions.add(new Region(x, y, w, h, s));
        return s.pos;
    }

    /** Griff rechts am Bereich (nur wenn nötig), ziehbar */
    public void scrollbar(String id, Scroll s, float x, float y, float h) {
        if (s.content <= s.view + 1) return;
        float th = thumb(h, s.content), range = h - th;
        float ty = y + range * (s.pos / Math.max(1, s.content - s.view));
        String hid = "sb:" + id;
        Region r = new Region(x - 6 * d, y, 14 * d, h, s);
        add(hid, r.x, r.y, r.w, r.h, null, null, null, false, null);
        boolean hv = hover(hid) || dragScroll == s;
        float t = Motion.to(key(hid), hv, Tokens.MS_HOVER);
        float bw = (4 + 2 * t) * d;
        Draw.rect(g, x - bw / 2, ty, bw, th, bw / 2, Draw.mix(0x26FFFFFF, 0x59FFFFFF, t));
        pendingBars.put(hid, new Object[]{r, Float.valueOf(th), Float.valueOf(ty)});
    }

    private final Map<String, Object[]> pendingBars = new HashMap<String, Object[]>();

    private void startScrollDrag(Hit h, double y) {
        Object[] o = pendingBars.get(h.id);
        if (o == null) return;
        Region r = (Region) o[0];
        float th = (Float) o[1], ty = (Float) o[2];
        dragScroll = r.scroll;
        dragRegion = new Region(r.x, r.y, r.w, r.h, r.scroll);
        dragScrollGrab = y >= ty && y < ty + th ? (float) (y - ty) : th / 2f;
        mouseDrag(mx, y);
    }

    // ------------------------------------------------------------------ Dialog

    /** Abgedunkelter Hintergrund über allem + Glas-Dialog; Klick daneben ruft onOutside */
    public void modal(float x, float y, float w, float h, Runnable onOutside) {
        Draw.rect(g, 0, 0, vw, vh, 0, 0x80060410);
        block(0, 0, vw, vh, onOutside);
        Draw.shadow(g, x, y + 30 * d, w, h, Tokens.RAD_XL * d, 80 * d, -30 * d, 0xB3000000);
        Draw.glass(g, x, y, w, h, Tokens.RAD_XL * d, 0xEB141020, Tokens.LINE_STRONG);
        block(x, y, w, h, null);
    }

    /** Ping-Balken (4 Stufen) */
    public void ping(float x, float y, int ms) {
        int level = ms < 0 ? 0 : ms < 60 ? 4 : ms < 120 ? 3 : ms < 220 ? 2 : 1;
        int on = level >= 3 ? Tokens.OK : level == 2 ? Tokens.WARN : Tokens.BAD;
        for (int i = 0; i < 4; i++) {
            float bh = (5 + i * 3.5f) * d;
            Draw.rect(g, x + i * 6 * d, y + 16 * d - bh, 4 * d, bh, 1.5f * d, i < level ? on : 0x26FFFFFF);
        }
    }
}
