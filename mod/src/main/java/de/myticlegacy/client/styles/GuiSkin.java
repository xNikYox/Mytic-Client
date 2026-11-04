package de.myticlegacy.client.styles;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.gui.Ui;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft-Menüs (Einzelspieler, Mehrspieler, Optionen …) im Mytic-Design: Knöpfe, Textfelder, Schieberegler,
 * Häkchen, Reiter, Scrollbalken, Hintergründe und Trennlinien werden passend zum Menü-Stil und den Farben erzeugt
 * und über das Paket „Mytic Styles“ eingeblendet. 4-fache Auflösung (Neun-Teile-Skalierung), damit Rundungen weich sind.
 */
public final class GuiSkin {
    private static final int K = 4;
    private static final String SPRITES = "assets/minecraft/textures/gui/sprites/widget/", GUI = "assets/minecraft/textures/gui/";

    private GuiSkin() {
    }

    /** Ist das Design für Minecraft-Menüs eingeschaltet? */
    public static boolean enabled() {
        return MyticClient.theme == null || MyticClient.theme.vanillaMenus.get();
    }

    /** Schlüssel des aktuellen Aussehens – ändert er sich, müssen die Grafiken neu erzeugt werden */
    public static String key() {
        if (!enabled()) return "aus";
        Ui.refreshPalette();
        return MyticClient.theme.menuStyle.get() + "/" + Integer.toHexString(Ui.L_PANEL) + Integer.toHexString(Ui.L_CARD) + Integer.toHexString(Ui.L_CARD_HOVER)
                + Integer.toHexString(Ui.L_LINE) + Integer.toHexString(Ui.L_SELECT) + Integer.toHexString(Ui.L_GREEN) + Integer.toHexString(Ui.accent())
                + Integer.toHexString(Ui.SURFACE) + Integer.toHexString(Ui.PANEL);
    }

    /** Farben je Stil: Fläche, Fläche (hover), Rand, Rand (hover/aktiv), Hintergrund, Auswahl, Radius */
    private record Look(int card, int cardHover, int line, int lineActive, int panel, int select, float radius, boolean glow) {
    }

    private static Look look() {
        Ui.refreshPalette();
        if (!Ui.lunar()) { // Neon / Synthwave
            int acc = Ui.accent() | 0xFF000000;
            return new Look(Ui.SURFACE | 0xFF000000, Ui.SURFACE_HOVER | 0xFF000000, Ui.mix(0xFF2C2050, acc, 0.35f), Ui.accent2() | 0xFF000000, Ui.PANEL, acc, 4, true);
        }
        int select = Ui.gold() || Ui.sidebar() ? Ui.L_SELECT : Ui.glass() ? 0xFFFFFFFF : Ui.L_GREEN;
        int line = Ui.gold() ? Ui.withAlpha(Ui.L_GREEN, 0x70) : Ui.glass() ? 0x66FFFFFF : 0x40FFFFFF;
        int lineActive = Ui.gold() || Ui.sidebar() ? Ui.L_SELECT : 0xD0FFFFFF;
        float r = Ui.square() ? 0 : Ui.gold() ? 1.5f : Ui.glass() ? 5 : 3;
        return new Look(opaque(Ui.L_CARD, 0.92f), opaque(Ui.L_CARD_HOVER, 0.96f), line, lineActive, Ui.L_PANEL, select, r, false);
    }

    /** Karten-Farbe mit Mindest-Deckkraft (sonst sind Knöpfe vor dem Panorama kaum zu sehen) */
    private static int opaque(int argb, float min) {
        int a = Math.max((argb >>> 24) & 0xFF, Math.round(min * 255));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Alle Grafiken ("assets/…" → Daten) */
    public static Map<String, byte[]> build() {
        Map<String, byte[]> out = new HashMap<>();
        if (!enabled()) return out;
        try {
            Look l = look();
            // Knöpfe 200×20, Rand 3
            button(out, "button", 200, 20, 3, l.card, l.line, l, false);
            button(out, "button_highlighted", 200, 20, 3, l.cardHover, l.lineActive, l, true);
            button(out, "button_disabled", 200, 20, 3, Ui.mix(l.card, 0xFF000000, 0.45f), Ui.withAlpha(l.line, 0x20), l, false);
            // Textfelder und Schieberegler-Bahn: dunkler, Rand 1
            int field = Ui.mix(l.panel | 0xFF000000, 0xFF000000, 0.35f) & 0xF0FFFFFF;
            button(out, "text_field", 200, 20, 1, field, l.line, l, false);
            button(out, "text_field_highlighted", 200, 20, 1, field, l.lineActive, l, true);
            button(out, "slider", 200, 20, 1, field, l.line, l, false);
            button(out, "slider_highlighted", 200, 20, 1, field, l.lineActive, l, true);
            // Regler-Knopf 8×20
            sprite(out, "slider_handle", 8, 20, g -> rounded(g, 0, 0, 8, 20, Math.min(l.radius, 2), l.cardHover, l.lineActive), "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":3}");
            sprite(out, "slider_handle_highlighted", 8, 20, g -> rounded(g, 0, 0, 8, 20, Math.min(l.radius, 2), Ui.mix(l.cardHover, 0xFFFFFFFF, 0.15f), l.select), "{\"left\":2,\"top\":2,\"right\":2,\"bottom\":3}");
            // Häkchen 20×20 (ohne Neun-Teile)
            checkbox(out, "checkbox", false, false, field, l);
            checkbox(out, "checkbox_highlighted", false, true, field, l);
            checkbox(out, "checkbox_selected", true, false, field, l);
            checkbox(out, "checkbox_selected_highlighted", true, true, field, l);
            // Scrollbalken 6×32 – der Rand (Neun-Teile) muss so groß wie die Rundung sein: Minecraft wiederholt die Mitte
            // wie Kacheln, sonst stehen die runden Enden mitten im Balken und es entstehen Lücken
            sprite(out, "scroller", 6, 32, g -> rounded(g, 1, 0, 4, 32, 2, 0x99FFFFFF, 0), "2");
            sprite(out, "scroller_background", 6, 32, g -> rounded(g, 1, 0, 4, 32, 2, 0x30000000, 0), "2");
            // Reiter 130×24 (unten offen)
            tab(out, "tab", l.card, l.line, false, l);
            tab(out, "tab_highlighted", l.cardHover, l.lineActive, false, l);
            tab(out, "tab_selected", l.cardHover, l.line, true, l);
            tab(out, "tab_selected_highlighted", l.cardHover, l.lineActive, true, l);
            // Hintergründe (16×16, gekachelt) und Trennlinien (32×2)
            // Hauptmenü: Panorama scheint leicht durch, Listen etwas dunkler
            int bg = Ui.withAlpha(l.panel, 0x9C);
            plain(out, GUI + "menu_background.png", 16, 16, bg);
            plain(out, GUI + "menu_list_background.png", 16, 16, Ui.withAlpha(Ui.mix(l.panel, 0xFF000000, 0.3f), 0xB8));
            plain(out, GUI + "inworld_menu_background.png", 16, 16, Ui.withAlpha(l.panel, 0x90));
            plain(out, GUI + "inworld_menu_list_background.png", 16, 16, Ui.withAlpha(Ui.mix(l.panel, 0xFF000000, 0.3f), 0x90));
            for (String n : new String[]{"header_separator", "footer_separator", "inworld_header_separator", "inworld_footer_separator"}) {
                plain(out, GUI + n + ".png", 32, 2, l.glow ? Ui.withAlpha(l.select, 0xB0) : l.line);
            }
        } catch (IOException | RuntimeException e) {
            MyticClient.LOG.warn("Mytic-Design für Minecraft-Menüs konnte nicht erzeugt werden", e);
            out.clear();
        }
        return out;
    }

    private interface Painter {
        void paint(Graphics2D g);
    }

    /** Neun-Teile-Grafik: w×h GUI-Pixel in 4-facher Auflösung, border als Zahl oder JSON-Objekt */
    private static void sprite(Map<String, byte[]> out, String name, int w, int h, Painter p, String border) throws IOException {
        BufferedImage img = new BufferedImage(w * K, h * K, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(K, K);
        p.paint(g);
        g.dispose();
        out.put(SPRITES + name + ".png", png(img));
        if (border != null) {
            out.put(SPRITES + name + ".png.mcmeta", ("{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":" + w + ",\"height\":" + h + ",\"border\":" + border + "}}}").getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void button(Map<String, byte[]> out, String name, int w, int h, int border, int fill, int line, Look l, boolean active) throws IOException {
        sprite(out, name, w, h, g -> {
            if (l.glow && active) rounded(g, 0, 0, w, h, l.radius, Ui.withAlpha(l.lineActive, 0x30), 0);
            rounded(g, 0, 0, w, h, l.radius, fill, line);
            // Neon: Leuchtkante unten, Lunar-Stile: feine helle Kante oben
            if (l.glow) fillRect(g, 3, h - 1.5f, w - 6, 1, active ? l.lineActive : Ui.withAlpha(l.select, 0x90));
            else if (active && (Ui.square() || Ui.sidebar())) fillRect(g, 1, h - 1.5f, w - 2, 1f, l.select);
        }, String.valueOf(Math.max(border, (int) Math.ceil(l.radius))));
    }

    private static void tab(Map<String, byte[]> out, String name, int fill, int line, boolean selected, Look l) throws IOException {
        sprite(out, name, 130, 24, g -> {
            rounded(g, 0, 0, 130, 24 + l.radius + 1, l.radius, fill, line); // unten über den Rand hinaus = offen
            if (selected) fillRect(g, 2, 22, 126, 2, l.select);
        }, "{\"left\":" + edge(l) + ",\"top\":" + edge(l) + ",\"right\":" + edge(l) + ",\"bottom\":2}");
    }

    /** Rand der Reiter: mindestens 3, sonst so groß wie die Rundung (gegen Lücken beim Kacheln) */
    private static int edge(Look l) {
        return Math.max(3, (int) Math.ceil(l.radius) + 1);
    }

    private static void checkbox(Map<String, byte[]> out, String name, boolean on, boolean hover, int field, Look l) throws IOException {
        sprite(out, name, 20, 20, g -> {
            rounded(g, 0.5f, 0.5f, 19, 19, Math.min(l.radius, 3), on ? l.select : field, hover ? l.lineActive : l.line);
            if (on) {
                g.setColor(color(Ui.gold() ? 0xFF1A1608 : 0xFFFFFFFF));
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D check = new Path2D.Float();
                check.moveTo(5.5, 10.5);
                check.lineTo(8.7, 13.7);
                check.lineTo(14.5, 6.5);
                g.draw(check);
            }
        }, null);
    }

    private static void plain(Map<String, byte[]> out, String path, int w, int h, int argb) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, argb);
        out.put(path, png(img));
    }

    /** Abgerundetes Rechteck mit 1-Pixel-Rand (line 0 = ohne Rand) */
    private static void rounded(Graphics2D g, float x, float y, float w, float h, float r, int fill, int line) {
        float d = r * 2;
        if (line != 0 && ((line >>> 24) & 0xFF) > 0) {
            g.setColor(color(line));
            g.fill(new RoundRectangle2D.Float(x, y, w, h, d, d));
            g.setColor(color(fill));
            float id = Math.max(0, d - 2);
            // innere Fläche ersetzt (nicht überlagert) den Rand, damit halbdurchsichtige Flächen nicht dunkler werden
            g.setComposite(java.awt.AlphaComposite.Src);
            g.fill(new RoundRectangle2D.Float(x + 1, y + 1, w - 2, h - 2, id, id));
            g.setComposite(java.awt.AlphaComposite.SrcOver);
        } else {
            g.setColor(color(fill));
            g.fill(new RoundRectangle2D.Float(x, y, w, h, d, d));
        }
    }

    private static void fillRect(Graphics2D g, float x, float y, float w, float h, int argb) {
        g.setColor(color(argb));
        g.fill(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
    }

    private static Color color(int argb) {
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        return bytes.toByteArray();
    }
}
