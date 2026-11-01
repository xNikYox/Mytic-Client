package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.module.Category;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.Setting;
import de.myticlegacy.client.setting.SliderSetting;
import de.myticlegacy.client.setting.TextSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod-Menü im Stil von Lunar/NRC: kompaktes Panel, Kategorien mit Unterstrich, eigenes Suchfeld (einfach lostippen),
 * Mod-Karten mit großem Icon und An/Aus-Leiste, Einstellungen pro Mod.
 */
public class ModMenuScreen extends MyticScreen {
    /** Textfeld, das gerade bearbeitet wird (Auto Text). */
    private TextSetting editingText;

    /** Farbwähler, der sich unter einer Farb-Einstellung öffnet. */
    private final ColorPicker picker = new ColorPicker();

    private int rowH(Setting<?> setting) {
        return ROW_H + (picker.setting() == setting ? ColorPicker.HEIGHT + 4 : 0);
    }

    private static final int CARD_H = 60;
    /** Balken unten in der Karte (AKTIV/INAKTIV): Abstand vom unteren Rand und Höhe. */
    private static final int BAR_OFF = 14;
    private static final int BAR_H = 10;
    private static final int GAP = 5;
    private static final int ROW_H = 22;
    private static final int HEADER = 34;
    private static final int FOOTER = 28;

    private final Screen parent;
    private Category category;
    private Module selected;
    private String query = "";
    private boolean searchFocused;
    private double scrollPos;
    private double scrollTarget;
    private SliderSetting dragging;
    private Object hovered;
    private long hoverSince;
    private final long openedAt = System.currentTimeMillis();

    private int px, py, pw, ph;

    public ModMenuScreen(Screen parent) {
        this(parent, null);
    }

    public ModMenuScreen(Screen parent, Module open) {
        super(Component.literal(MyticClient.NAME));
        this.parent = parent;
        this.selected = open;
    }

    /** Anteil des Bildschirms, den das Menü einnimmt (Breite, Höhe). */
    private double shareW() {
        return large() ? 0.78 : 0.6;
    }

    private double shareH() {
        return large() ? 0.86 : 0.78;
    }

    @Override
    protected int pixelScale() {
        return fit(380, 240, shareW(), shareH());
    }

    @Override
    protected void layout() {
        Compat.textInput(this, true);
        pw = Math.min(600, Math.max(340, (int) (vw * shareW())));
        ph = Math.min(340, Math.max(200, (int) (vh * shareH())));
        pw = Math.min(pw, vw - 8);
        ph = Math.min(ph, vh - 8);
        px = (vw - pw) / 2;
        py = (vh - ph) / 2;
        clampScroll();
    }

    // ------------------------------------------------------------------------------------------ Layout

    private List<Module> visibleModules() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Module> list = new ArrayList<>();
        for (Module m : MyticClient.MODULES) {
            if (category != null && m.category != category) continue;
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.description.toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(m);
        }
        return list;
    }

    /** Breite der Icon-Leiste links (nur Menü-Stil „Seitenleiste“) */
    private static final int SIDE_W = 50;

    private int side() {
        return Ui.sidebar() ? SIDE_W : 0;
    }

    private int cx() {
        return px + side() + 10;
    }

    private int cy() {
        return py + HEADER + 8;
    }

    private int cw() {
        return pw - side() - 20;
    }

    private int ch() {
        return ph - HEADER - 8 - FOOTER;
    }

    private int columns() {
        if (Ui.sidebar()) return Math.max(2, Math.min(4, (cw() + GAP) / (92 + GAP)));
        return Math.max(3, Math.min(5, (cw() + GAP) / (58 + GAP)));
    }

    /** Kartenhöhe: Seitenleiste mit flacheren, breiteren Karten (Icon links oben, Schalter rechts oben) */
    private int cardH() {
        return Ui.sidebar() ? 46 : CARD_H;
    }

    private int cardW() {
        return (cw() - (columns() - 1) * GAP) / columns();
    }

    private int[] card(int index) {
        int col = index % columns();
        int row = index / columns();
        return new int[]{cx() + col * (cardW() + GAP), cy() + row * (cardH() + GAP) - (int) Math.round(scrollPos)};
    }

    private int contentHeight() {
        if (selected != null) {
            int rows = 0;
            for (Setting<?> setting : selected.settings) rows += rowH(setting) + 3;
            return 40 + rows + 26;
        }
        int rows = (visibleModules().size() + columns() - 1) / columns();
        return Math.max(0, rows * (cardH() + GAP) - GAP);
    }

    /** Kategorie-Reiter; x/y/w/h ist die Klickfläche */
    private record Tab(String label, Category category, int x, int y, int w, int h) {
    }

    private static final String[] TAB_LABELS = {"Alle", Category.HUD.label, Category.MECHANIK.label, Category.VISUELL.label, Category.CLIENT.label};
    private static final Category[] TAB_CATS = {null, Category.HUD, Category.MECHANIK, Category.VISUELL, Category.CLIENT};
    private static final net.minecraft.world.item.Item[] TAB_ICONS = {
            net.minecraft.world.item.Items.NETHER_STAR, net.minecraft.world.item.Items.ITEM_FRAME, net.minecraft.world.item.Items.IRON_SWORD,
            net.minecraft.world.item.Items.ENDER_EYE, net.minecraft.world.item.Items.AMETHYST_SHARD};

    private int logoW() {
        return font.width("MYTIC CLIENT") + (Ui.square() ? 12 : 0);
    }

    private List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        if (Ui.sidebar()) {
            // senkrecht in der Icon-Leiste: Icon und kleiner Name darunter
            int y = py + HEADER + 6;
            for (int i = 0; i < TAB_LABELS.length; i++) {
                tabs.add(new Tab(TAB_LABELS[i], TAB_CATS[i], px + 5, y, SIDE_W - 10, 34));
                y += 37;
            }
            return tabs;
        }
        int x = px + 14 + logoW() + 16;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int w = font.width(TAB_LABELS[i]);
            tabs.add(new Tab(TAB_LABELS[i], TAB_CATS[i], x - 4, py + 8, w + 8, 18));
            x += w + 12;
        }
        return tabs;
    }

    private int[] searchBox() {
        if (Ui.sidebar()) {
            int w = Math.max(90, Math.min(150, pw - logoW() - 50));
            return new int[]{px + pw - 12 - w, py + 9, w, 16};
        }
        Tab last = tabs().get(tabs().size() - 1);
        int w = Math.max(70, Math.min(110, px + pw - 12 - (last.x + last.w + 8)));
        return new int[]{px + pw - 12 - w, py + 9, w, 16};
    }

    /** Logo; im Stil „Eckig“ mit Sperrschrift, bei Gold zweifarbig creme/gold */
    private void drawLogo(GuiGraphics g, int x, int y) {
        if (Ui.square()) {
            int cx2 = x;
            for (char ch : "MYTIC".toCharArray()) {
                Ui.text(g, String.valueOf(ch), cx2, y, 0xFFFFFFFF, true);
                cx2 += font.width(String.valueOf(ch)) + 1;
            }
            cx2 += 4;
            for (char ch : "CLIENT".toCharArray()) {
                Ui.text(g, String.valueOf(ch), cx2, y, 0xFF8C8F99, true);
                cx2 += font.width(String.valueOf(ch)) + 1;
            }
            return;
        }
        int second = Ui.gold() || Ui.sidebar() ? Ui.selection() : 0xFFB4B4B4;
        Ui.text(g, "MYTIC", x, y, Ui.lText(), true);
        Ui.text(g, "CLIENT", x + font.width("MYTIC "), y, second, true);
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        String mode = MyticClient.theme.menuBackground.get();
        if (mode.equals("Unschärfe")) super.renderBackground(g, mouseX, mouseY, delta);
        else if (mode.equals("Abdunkeln") || minecraft.level == null) g.fill(0, 0, width, height, 0x99000000);
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        scrollPos += (scrollTarget - scrollPos) * 0.35;
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        float ease = 1f - (1f - open) * (1f - open) * (1f - open);
        Gfx.push(g);
        Gfx.translate(g, 0, (1 - ease) * 10);

        // Panel: Schatten, Fläche, Kopf mit Akzent-Verlauf
        Ui.rect(g, px - 3, py + 3, pw + 6, ph + 3, 10, 0x50000000);
        boolean lunar = Ui.lunar();
        if (lunar) {
            Ui.lunarPanel(g, px, py, pw, ph, 6);
        } else {
            Ui.neonPanel(g, px, py, pw, ph, 8, 1f);
            g.fillGradient(px + 4, py + 2, px + pw - 4, py + HEADER, Ui.withAlpha(Ui.accent(), 42), Ui.withAlpha(Ui.accent(), 0));
        }

        if (lunar) {
            drawLogo(g, px + 14, py + 13);
        } else {
            Ui.glowText(g, "MYTIC", px + 14, py + 13, Ui.accent(), 1f);
            Ui.glowText(g, "CLIENT", px + 14 + font.width("MYTIC "), py + 13, Ui.accent2(), 0.8f);
        }

        if (Ui.sidebar()) {
            // Icon-Leiste: etwas dunkler, mit Trennlinie
            Ui.rect(g, px + 1, py + HEADER + 1, SIDE_W - 1, ph - HEADER - 2, 0, 0x40000000);
            g.fill(px + SIDE_W, py + HEADER + 1, px + SIDE_W + 1, py + ph - 1, Ui.L_LINE);
        }
        for (int i = 0; i < TAB_LABELS.length; i++) {
            Tab tab = tabs().get(i);
            boolean active = selected == null && tab.category == category;
            boolean hover = Ui.inside(mouseX, mouseY, tab.x, tab.y, tab.w, tab.h);
            float t = Ui.animate("tab:" + tab.label, active, 14f);
            float h = Ui.animate("tabh:" + tab.label, hover, 18f);
            if (Ui.sidebar()) {
                float fillT = Math.max(t, h * 0.5f);
                if (fillT > 0.01f) Ui.rect(g, tab.x, tab.y, tab.w, tab.h, 6, t > h * 0.5f ? Ui.withAlpha(Ui.selection(), Math.round(fillT * 0x28)) : Ui.withAlpha(0xFFFFFF, Math.round(fillT * 0x14)));
                Gfx.push(g);
                Gfx.translate(g, tab.x + tab.w / 2f - 6, tab.y + 4);
                Gfx.scale(g, 0.75f, 0.75f);
                g.renderItem(new net.minecraft.world.item.ItemStack(TAB_ICONS[i]), 0, 0);
                Gfx.pop(g);
                String label = font.plainSubstrByWidth(tab.label, Math.round(tab.w / 0.6f));
                Ui.scaled(g, label, tab.x + tab.w / 2f - font.width(label) * 0.3f, tab.y + 22, 0.6f, Ui.mix(Ui.lMuted(), Ui.selection(), t), false);
                continue;
            }
            int tx = tab.x + 4, tw = tab.w - 8;
            if (lunar) {
                if (Ui.gold() || Ui.square()) {
                    // Gold/Eckig: Text in Auswahlfarbe mit feiner Linie darunter
                    Ui.text(g, tab.label, tx, py + 13, Ui.mix(Ui.lMuted(), Ui.gold() ? Ui.selection() : 0xFFFFFFFF, Math.max(t, h * 0.6f)), false);
                    int line = Math.round(tw * t);
                    if (line > 0) g.fill(tx + (tw - line) / 2, py + 23, tx + (tw - line) / 2 + line, py + 24, Ui.selection());
                    continue;
                }
                // Lunar/Glas: Kategorien als Pillen, die aktive hinterlegt (Lunar blau)
                float fillT = Math.max(t, h * 0.4f);
                int pill = Ui.glass() ? 0xFFFFFF : Ui.selection();
                if (fillT > 0.01f) Ui.rect(g, tx - 5, py + 9, tw + 10, 15, 6, t > h * 0.4f ? Ui.withAlpha(pill, Math.round(fillT * (Ui.glass() ? 56 : 70))) : Ui.withAlpha(0xFFFFFF, Math.round(fillT * 40)));
                Ui.text(g, tab.label, tx, py + 13, Ui.mix(0xFF9A9A9A, 0xFFFFFFFF, Math.max(t, h * 0.7f)), false);
                continue;
            }
            // Neon (Synthwave): aktive Kategorie mit Verlauf hinterlegt
            if (t > 0.01f) Ui.hGradient(g, tx - 4, py + 9, tw + 8, 15, Ui.withAlpha(Ui.PINK, Math.round(t * 50)), Ui.withAlpha(Ui.CYAN, Math.round(t * 18)));
            Ui.text(g, tab.label, tx, py + 13, Ui.mix(Ui.MUTED, Ui.TEXT, Math.max(t, h * 0.7f)), false);
            int line = Math.round(tw * t);
            if (line > 0) {
                int lx = tx + (tw - line) / 2;
                Ui.glow(g, lx, py + 24, line, 2, 1, Ui.accent(), 3, t);
                Ui.hGradient(g, lx, py + 24, line, 2, Ui.accent(), Ui.accent2());
            }
        }

        int[] sb = searchBox();
        boolean sHover = Ui.inside(mouseX, mouseY, sb[0], sb[1], sb[2], sb[3]);
        if (lunar) Ui.outline(g, sb[0], sb[1], sb[2], sb[3], 4, searchFocused ? (Ui.gold() || Ui.sidebar() ? Ui.selection() : 0x90FFFFFF) : sHover ? 0x50FFFFFF : Ui.L_LINE,
                Ui.mix(Ui.L_PANEL | 0xFF000000, 0xFF000000, 0.25f));
        else Ui.outline(g, sb[0], sb[1], sb[2], sb[3], 8, searchFocused ? Ui.accent2() : sHover ? Ui.withAlpha(Ui.accent2(), 120) : Ui.LINE, Ui.SURFACE);
        String shown = query.isEmpty() ? (searchFocused ? "" : "Suchen …") : query;
        String clipped = font.plainSubstrByWidth(shown, sb[2] - 16);
        if (query.length() > 0 && font.width(query) > sb[2] - 16) clipped = tail(query, sb[2] - 16);
        Ui.text(g, clipped, sb[0] + 8, sb[1] + 4, query.isEmpty() ? Ui.MUTED : Ui.TEXT, false);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caret = sb[0] + 8 + font.width(query.isEmpty() ? "" : clipped);
            g.fill(caret, sb[1] + 3, caret + 1, sb[1] + 13, Ui.TEXT);
        }
        Ui.rect(g, px + (Ui.sidebar() ? 1 : 10), py + HEADER, pw - (Ui.sidebar() ? 2 : 20), 1, 0, lunar ? Ui.L_LINE : Ui.LINE);

        Gfx.scissor(g, cx(), cy() - 3, cx() + cw(), cy() + ch());
        Object hover = selected == null ? drawGrid(g, mouseX, mouseY) : drawSettings(g, mouseX, mouseY);
        Gfx.noScissor(g);
        drawScrollbar(g);

        // Fußzeile
        int fy = py + ph - FOOTER + 6;
        int fx = cx();
        Ui.rect(g, fx, fy - 6, cw(), 1, 0, lunar ? Ui.L_LINE : Ui.LINE);
        int bw = font.width("HUD bearbeiten") + 20;
        boolean editHover = Ui.inside(mouseX, mouseY, fx, fy, bw, 16);
        float eh = Ui.animate("btn:edit", editHover, 16f);
        if (lunar && (Ui.gold() || Ui.sidebar())) {
            // Gold/Seitenleiste: gefüllter Knopf in der Auswahlfarbe
            Ui.rect(g, fx, fy, bw, 16, 4, Ui.mix(Ui.selection(), 0xFFFFFFFF, eh * 0.15f));
        } else if (lunar) {
            Ui.outline(g, fx, fy, bw, 16, 4, Ui.mix(0x40FFFFFF, 0xB0FFFFFF, eh), Ui.mix(0xFF262626, 0xFF333333, eh));
        } else {
            Ui.glow(g, fx, fy, bw, 16, 8, Ui.accent(), 3, 0.4f + eh * 0.6f);
            Ui.rect(g, fx, fy, bw, 16, 8, Ui.accent());
            Ui.rect(g, fx + bw - 16, fy, 16, 16, 8, Ui.PINK);
            Ui.hGradient(g, px + 18, fy, bw - 26, 16, Ui.mix(Ui.accent(), 0xFFFFFFFF, eh * 0.15f), Ui.mix(Ui.PINK, 0xFFFFFFFF, eh * 0.15f));
        }
        Ui.text(g, "HUD bearbeiten", fx + 10, fy + 4, Ui.gold() ? 0xFF1A1608 : 0xFFFFFFFF, !Ui.gold());
        String info = MyticClient.MODULES.stream().filter(Module::enabled).count() + " von " + MyticClient.MODULES.size() + " aktiv";
        Ui.text(g, info, px + pw - 12 - font.width(info), fy + 4, Ui.MUTED, false);

        Gfx.pop(g);

        // Tooltip erst nach kurzem Verweilen
        if (hover != hovered) {
            hovered = hover;
            hoverSince = System.currentTimeMillis();
        }
        if (hover instanceof Module m && System.currentTimeMillis() - hoverSince > 450) {
            Compat.tooltip(g, Component.literal(m.description), mouseX, mouseY);
        }
    }

    private String tail(String text, int width) {
        String s = text;
        while (s.length() > 1 && font.width(s) > width) s = s.substring(1);
        return s;
    }

    private Object drawGrid(GuiGraphics g, int mouseX, int mouseY) {
        List<Module> modules = visibleModules();
        if (modules.isEmpty()) {
            Ui.centered(g, "Keine Mods gefunden", cx() + cw() / 2, cy() + 30, Ui.MUTED, false);
            return null;
        }
        Object hover = null;
        boolean inContent = Ui.inside(mouseX, mouseY, cx(), cy(), cw(), ch());
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int[] c = card(i);
            int w = cardW();
            if (c[1] + cardH() < cy() - 4 || c[1] > cy() + ch()) continue;
            boolean over = inContent && Ui.inside(mouseX, mouseY, c[0], c[1], w, cardH());
            if (over) hover = m;
            boolean on = m.enabled();
            float hv = Ui.animate("card:" + m.id, over, 16f);
            float onT = Ui.animate("on:" + m.id, on, 12f);

            if (Ui.sidebar()) {
                drawSideCard(g, m, c[0], c[1], w, over, hv, onT, mouseX, mouseY);
                continue;
            }
            boolean lunar = Ui.lunar();
            float lit = Math.max(onT * 0.75f, hv);
            if (lunar) {
                int edge = Ui.gold() ? Ui.withAlpha(Ui.L_GREEN, Math.round(0x22 + hv * 0x60)) : Ui.mix(Ui.glass() ? 0x30FFFFFF : 0x18FFFFFF, 0x50FFFFFF, hv);
                Ui.outline(g, c[0], c[1], w, CARD_H, Ui.cardRadius(), edge, Ui.mix(Ui.L_CARD, Ui.L_CARD_HOVER, hv));
                // Eckig: aktive Karten mit Strich in der Schalter-Farbe unten
                if (Ui.square() && onT > 0.01f) g.fill(c[0] + 1, c[1] + CARD_H - 3, c[0] + w - 1, c[1] + CARD_H - 1, Ui.alpha(Ui.L_GREEN, onT));
            }
            else if (lit > 0.02f) Ui.glow(g, c[0], c[1], w, CARD_H, 6, hv > onT ? Ui.accent2() : Ui.accent(), 4, lit * 0.7f);
            if (!lunar) Ui.outline(g, c[0], c[1], w, CARD_H, 6, Ui.mix(Ui.LINE, Ui.withAlpha(hv > onT ? Ui.accent2() : Ui.accent(), 230), Math.max(onT * 0.7f, hv * 0.95f)),
                    Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hv));

            Gfx.push(g);
            Gfx.translate(g, c[0] + w / 2f - 12, c[1] + 6 - Math.round(hv * 2));
            Gfx.scale(g, 1.5f, 1.5f);
            g.renderItem(m.icon(), 0, 0);
            Gfx.pop(g);

            // Name etwas kleiner, damit er in die schmalen Karten passt
            String name = font.plainSubstrByWidth(m.name, Math.round((w - 6) / 0.8f));
            Ui.scaled(g, name, c[0] + w / 2f - font.width(name) * 0.4f, c[1] + 33, 0.8f, lunar ? Ui.lText() : Ui.TEXT, false);

            int barY = c[1] + CARD_H - BAR_OFF;
            if (lunar) {
                // Lunar: grüner bzw. roter Balken über die ganze Breite
                // Gold: aus = dunkles Braun statt Rot
                int state = Ui.mix(Ui.gold() ? 0xFF2A261A : Ui.L_RED, Ui.L_GREEN, onT);
                Ui.rect(g, c[0] + 4, barY, w - 8, BAR_H, 2, over ? Ui.mix(state, 0xFFFFFFFF, 0.12f) : state);
                String label = on ? "AKTIV" : "INAKTIV";
                int labelColor = Ui.gold() ? Ui.mix(Ui.lMuted(), 0xFF1A1608, onT) : 0xFFFFFFFF;
                Ui.scaled(g, label, c[0] + w / 2f - font.width(label) * 0.375f, barY + 2, 0.75f, labelColor, false);
            }
            int barColor = Ui.mix(0xFF2B2440, Ui.accent(), onT);
            if (!lunar) Ui.rect(g, c[0] + 5, barY, w - 10, BAR_H, 5, over ? Ui.mix(barColor, 0xFFFFFFFF, 0.1f) : barColor);
            if (!lunar && onT > 0.02f) {
                Ui.hGradient(g, c[0] + 10, barY, w - 20, BAR_H, Ui.alpha(barColor, onT), Ui.alpha(Ui.mix(Ui.accent(), Ui.PINK, 0.55f), onT));
                Ui.rect(g, c[0] + w - 15, barY, 10, BAR_H, 5, Ui.alpha(Ui.mix(Ui.accent(), Ui.PINK, 0.55f), onT));
            }
            if (!lunar) {
                String label = on ? "AN" : "AUS";
                Ui.scaled(g, label, c[0] + w / 2f - font.width(label) * 0.375f, barY + 2, 0.75f, on ? 0xFFFFFFFF : Ui.MUTED, false);
            }

            if (!m.settings.isEmpty()) {
                boolean gearHover = over && Ui.inside(mouseX, mouseY, c[0] + w - 13, c[1] + 2, 11, 11);
                int gearColor = gearHover ? (lunar ? (Ui.gold() ? Ui.selection() : 0xFFFFFFFF) : Ui.accent()) : Ui.withAlpha(Ui.MUTED, over ? 255 : 110);
                Ui.scaled(g, "⚙", c[0] + w - 11, c[1] + 3, 0.85f, gearColor, false);
            }
        }
        return hover;
    }

    private Object drawSettings(GuiGraphics g, int mouseX, int mouseY) {
        Module m = selected;
        int x = cx();
        int y = cy() - (int) Math.round(scrollPos);
        int w = cw();

        boolean backHover = Ui.inside(mouseX, mouseY, x, y + 2, 16, 16);
        Ui.rect(g, x, y + 2, 16, 16, 8, Ui.lunar() ? (backHover ? Ui.L_CARD_HOVER : Ui.L_CARD) : (backHover ? Ui.SURFACE_HOVER : Ui.SURFACE));
        Ui.centered(g, "‹", x + 8, y + 6, Ui.TEXT, false);
        g.renderItem(m.icon(), x + 24, y + 2);
        Ui.text(g, m.name, x + 44, y + 2, Ui.TEXT, true);
        Ui.text(g, font.plainSubstrByWidth(m.description, w - 80), x + 44, y + 12, Ui.MUTED, false);
        Ui.toggle(g, "set:on:" + m.id, x + w - 22, y + 6, m.enabled());

        if (picker.setting() != null && !m.settings.contains(picker.setting())) picker.close();
        int row = y + 32;
        Object hover = null;
        for (Setting<?> setting : m.settings) {
            boolean over = Ui.inside(mouseX, mouseY, x, row, w, ROW_H);
            Ui.rect(g, x, row, w, rowH(setting), Ui.lunar() ? 4 : 5, Ui.lunar() ? (over ? Ui.L_CARD_HOVER : Ui.L_CARD) : (over ? Ui.SURFACE_HOVER : Ui.SURFACE));
            if (over && !Ui.lunar()) Ui.rect(g, x, row + 4, 2, ROW_H - 8, 1, Ui.accent2());
            Ui.text(g, setting.label, x + 8, row + 7, Ui.TEXT, false);
            int right = x + w - 8;
            if (setting instanceof de.myticlegacy.client.setting.ActionSetting a) {
                // Knopf (z. B. „Merken“) rechts in der Zeile
                String label = a.button();
                int bw = font.width(label) + 16;
                boolean ls = Ui.lunar(), overBtn = Ui.inside(mouseX, mouseY, right - bw, row + 3, bw, 16);
                Ui.outline(g, right - bw, row + 3, bw, 16, ls ? 4 : 8, overBtn ? 0xB0FFFFFF : ls ? 0x30FFFFFF : Ui.withAlpha(Ui.accent(), 150), ls ? 0xFF2A2A2A : 0xFF1C1433);
                Ui.centered(g, label, right - bw / 2, row + 7, Ui.TEXT, false);
            } else if (setting instanceof BoolSetting b) {
                Ui.toggle(g, "set:" + m.id + "." + b.key, right - 20, row + 6, b.get());
            } else if (setting instanceof SliderSetting sl) {
                int trackW = 100;
                int tx = right - trackW;
                int ty = row + 10;
                Ui.rect(g, tx, ty, trackW, 3, 1, 0xFF3A3352);
                int fill = (int) Math.round(trackW * sl.fraction());
                if (Ui.lunar()) {
                    Ui.rect(g, tx, ty, Math.max(3, fill), 3, 1, 0xFFE6E6E6);
                } else {
                    Ui.glow(g, tx, ty, Math.max(3, fill), 3, 1, Ui.accent(), 2, 0.8f);
                    Ui.hGradient(g, tx, ty, Math.max(3, fill), 3, Ui.accent2(), Ui.accent());
                    Ui.glow(g, tx + fill - 4, ty - 3, 9, 9, 4, Ui.accent2(), 2, 0.9f);
                }
                Ui.rect(g, tx + fill - 4, ty - 3, 9, 9, 4, 0xFFFFFFFF);
                String value = sl.display();
                Ui.text(g, value, tx - 8 - font.width(value), row + 7, Ui.MUTED, false);
            } else if (setting instanceof ColorSetting c) {
                // Farbe als Feld mit Hex-Wert – Klick öffnet die Farbpalette
                int col = c.get();
                boolean open = picker.setting() == c;
                String hexText = String.format("#%06X", col & 0xFFFFFF);
                Ui.text(g, hexText, right - 26 - font.width(hexText), row + 7, Ui.MUTED, false);
                Ui.rect(g, right - 20, row + 5, 20, 12, 3, open ? 0xFFFFFFFF : 0x60FFFFFF);
                Ui.rect(g, right - 19, row + 6, 18, 10, 2, col == 0xFF000000 ? 0xFF101010 : col | 0xFF000000);
                if (open) picker.draw(g, x + 8, row + ROW_H + 2);
            } else if (setting instanceof TextSetting) {
                // Textfeld (z. B. Auto Text): Klick zum Bearbeiten, Taste steht neben der Bezeichnung
                TextSetting ts = (TextSetting) setting;
                boolean edit = editingText == ts;
                String hint = ts.hint();
                if (!hint.isEmpty()) Ui.text(g, hint, x + 14 + font.width(setting.label), row + 7, Ui.MUTED, false);
                int bw = Math.min(160, w / 2);
                Ui.outline(g, right - bw, row + 3, bw, 16, 4, edit ? 0xB0FFFFFF : 0x30FFFFFF, Ui.lunar() ? 0xFF1C1C1C : 0xFF140F24);
                String value = ts.get();
                String shownText = value.isEmpty() && !edit ? "leer" : value;
                String clippedText = font.plainSubstrByWidth(shownText, bw - 12);
                if (edit && font.width(shownText) > bw - 12) clippedText = tail(shownText, bw - 12);
                Ui.text(g, clippedText, right - bw + 6, row + 7, value.isEmpty() ? Ui.MUTED : Ui.TEXT, false);
                if (edit && (System.currentTimeMillis() / 500) % 2 == 0) {
                    int caretX = right - bw + 6 + font.width(clippedText);
                    g.fill(caretX, row + 6, caretX + 1, row + 16, Ui.TEXT);
                }
            } else if (setting instanceof ModeSetting mode) {
                int bw = Math.max(80, font.width(mode.get()) + 30);
                boolean ls = Ui.lunar();
                Ui.outline(g, right - bw, row + 3, bw, 16, ls ? 4 : 8, ls ? 0x30FFFFFF : Ui.withAlpha(Ui.accent(), 150), ls ? 0xFF2A2A2A : 0xFF1C1433);
                Ui.text(g, "‹", right - bw + 6, row + 7, ls ? 0xFFB4B4B4 : Ui.accent2(), false);
                Ui.text(g, "›", right - 10, row + 7, ls ? 0xFFB4B4B4 : Ui.accent2(), false);
                Ui.centered(g, mode.get(), right - bw / 2, row + 7, Ui.TEXT, false);
            }
            row += rowH(setting) + 3;
        }
        int bw1 = font.width("Zurücksetzen") + 20;
        boolean resetHover = Ui.inside(mouseX, mouseY, x, row + 4, bw1, 16);
        Ui.rect(g, x, row + 4, bw1, 16, 8, resetHover ? Ui.SURFACE_HOVER : Ui.SURFACE);
        Ui.text(g, "Zurücksetzen", x + 10, row + 8, Ui.MUTED, false);
        if (m instanceof HudModule) {
            int bw2 = font.width("Position zurücksetzen") + 20;
            boolean posHover = Ui.inside(mouseX, mouseY, x + bw1 + 6, row + 4, bw2, 16);
            Ui.rect(g, x + bw1 + 6, row + 4, bw2, 16, 8, posHover ? Ui.SURFACE_HOVER : Ui.SURFACE);
            Ui.text(g, "Position zurücksetzen", x + bw1 + 16, row + 8, Ui.MUTED, false);
        }
        return hover;
    }

    /** Karte im Stil „Seitenleiste“: Icon oben links, Schalter oben rechts, Name unten, Zahnrad unten rechts */
    private void drawSideCard(GuiGraphics g, Module m, int x, int y, int w, boolean over, float hv, float onT, int mouseX, int mouseY) {
        int h = cardH();
        int edge = Ui.mix(Ui.mix(0x12FFFFFF, 0x30FFFFFF, hv), Ui.withAlpha(Ui.L_GREEN, 0x90), onT);
        Ui.outline(g, x, y, w, h, Ui.cardRadius(), edge, Ui.mix(Ui.L_CARD, Ui.L_CARD_HOVER, hv));
        Ui.rect(g, x + 6, y + 5, 20, 20, 4, 0x40000000);
        Gfx.push(g);
        Gfx.translate(g, x + 8, y + 7);
        g.renderItem(m.icon(), 0, 0);
        Gfx.pop(g);
        Ui.toggle(g, "tg:" + m.id, x + w - 26, y + 7, m.enabled());
        String name = font.plainSubstrByWidth(m.name, Math.round((w - (m.settings.isEmpty() ? 12 : 24)) / 0.85f));
        Ui.scaled(g, name, x + 7, y + h - 14, 0.85f, Ui.lText(), false);
        if (!m.settings.isEmpty()) {
            boolean gearHover = over && Ui.inside(mouseX, mouseY, x + w - 14, y + h - 15, 11, 11);
            Ui.scaled(g, "⚙", x + w - 12, y + h - 14, 0.85f, gearHover ? Ui.selection() : Ui.withAlpha(Ui.lMuted(), over ? 255 : 120), false);
        }
    }

    private void drawScrollbar(GuiGraphics g) {
        int total = contentHeight();
        if (total <= ch()) return;
        int barH = Math.max(18, ch() * ch() / total);
        int barY = cy() + (int) ((ch() - barH) * (scrollPos / (total - ch())));
        if (Ui.lunar()) {
            Ui.rect(g, px + pw - 6, barY, 3, barH, 1, 0x60FFFFFF);
        } else {
            Ui.glow(g, px + pw - 6, barY, 3, barH, 1, Ui.accent(), 2, 0.7f);
            g.fillGradient(px + pw - 6, barY, px + pw - 3, barY + barH, Ui.accent2(), Ui.PINK);
        }
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentHeight() - ch())));
    }

    @Override
    protected boolean scroll(double mouseX, double mouseY, double amount) {
        scrollTarget -= amount * 26;
        clampScroll();
        return true;
    }

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() != 0) return false;
        int[] sb = searchBox();
        searchFocused = Ui.inside(mx, my, sb[0], sb[1], sb[2], sb[3]);
        if (searchFocused) return true;

        for (Tab tab : tabs()) {
            if (Ui.inside(mx, my, tab.x, tab.y, tab.w, tab.h)) {
                category = tab.category;
                selected = null;
                scrollPos = scrollTarget = 0;
                return true;
            }
        }
        int fy = py + ph - FOOTER + 6;
        if (Ui.inside(mx, my, cx(), fy, font.width("HUD bearbeiten") + 20, 16)) {
            Compat.setScreen(new HudEditScreen(this));
            return true;
        }
        if (!Ui.inside(mx, my, cx(), cy(), cw(), ch())) return false;
        return selected == null ? clickGrid(mx, my) : clickSettings(mx, my);
    }

    private boolean clickGrid(double mx, double my) {
        List<Module> modules = visibleModules();
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int[] c = card(i);
            int w = cardW();
            if (!Ui.inside(mx, my, c[0], c[1], w, cardH())) continue;
            boolean gear, bar;
            if (Ui.sidebar()) {
                gear = Ui.inside(mx, my, c[0] + w - 16, c[1] + cardH() - 17, 15, 15);
                bar = Ui.inside(mx, my, c[0] + w - 30, c[1] + 3, 28, 18);
            } else {
                gear = Ui.inside(mx, my, c[0] + w - 13, c[1] + 2, 11, 11);
                bar = Ui.inside(mx, my, c[0] + 4, c[1] + CARD_H - BAR_OFF, w - 8, BAR_H);
            }
            if (!m.settings.isEmpty() && (gear || !bar)) {
                selected = m;
                scrollPos = scrollTarget = 0;
            } else {
                m.toggle();
            }
            return true;
        }
        return false;
    }

    private boolean clickSettings(double mx, double my) {
        editingText = null;
        Module m = selected;
        int x = cx();
        int y = cy() - (int) Math.round(scrollPos);
        int w = cw();
        if (Ui.inside(mx, my, x, y + 2, 16, 16)) {
            selected = null;
            scrollPos = scrollTarget = 0;
            return true;
        }
        if (Ui.inside(mx, my, x + w - 26, y + 2, 26, 16)) {
            m.toggle();
            return true;
        }
        int row = y + 32;
        for (Setting<?> setting : m.settings) {
            if (picker.setting() == setting && picker.click(mx, my)) {
                MyticClient.config().save();
                return true;
            }
            if (Ui.inside(mx, my, x, row, w, ROW_H)) {
                int right = x + w - 8;
                if (setting instanceof de.myticlegacy.client.setting.ActionSetting a) {
                    if (mx >= right - font.width(a.button()) - 16) a.run();
                } else if (setting instanceof BoolSetting b) {
                    b.toggle();
                } else if (setting instanceof SliderSetting sl) {
                    int tx = right - 100;
                    if (mx >= tx - 6) {
                        dragging = sl;
                        sl.setFraction((mx - tx) / 100.0);
                    }
                } else if (setting instanceof ColorSetting c) {
                    if (picker.setting() == c) picker.close();
                    else picker.open(c);
                } else if (setting instanceof TextSetting) {
                    editingText = (TextSetting) setting;
                    Compat.textInput(this, true);
            } else if (setting instanceof ModeSetting mode) {
                    int bw = Math.max(80, font.width(mode.get()) + 30);
                    if (mx >= right - bw) mode.cycle(mx < right - bw / 2.0 ? -1 : 1);
                }
                MyticClient.config().save();
                return true;
            }
            row += rowH(setting) + 3;
        }
        int bw1 = font.width("Zurücksetzen") + 20;
        if (Ui.inside(mx, my, x, row + 4, bw1, 16)) {
            m.resetSettings();
            return true;
        }
        if (m instanceof HudModule hud && Ui.inside(mx, my, x + bw1 + 6, row + 4, font.width("Position zurücksetzen") + 20, 16)) {
            hud.resetPosition();
            MyticClient.config().save();
            return true;
        }
        return false;
    }

    @Override
    protected boolean drag(Input.Click event, double dx, double dy) {
        if (picker.drag(event.x(), event.y())) return true;
        if (dragging == null) return false;
        int tx = cx() + cw() - 8 - 100;
        dragging.setFraction((event.x() - tx) / 100.0);
        return true;
    }

    @Override
    protected boolean release(Input.Click event) {
        if (picker.release()) {
            MyticClient.config().save();
            return true;
        }
        if (dragging == null) return false;
        dragging = null;
        MyticClient.config().save();
        return true;
    }

    @Override
    protected boolean onChar(Input.Typed event) {
        if (editingText != null) {
            if (!event.isAllowedChatCharacter()) return true;
            String now = editingText.get();
            if (now.length() < TextSetting.MAX) editingText.set(now + event.codepointAsString());
            MyticClient.config().save();
            return true;
        }
        if (selected != null || !event.isAllowedChatCharacter()) return false;
        searchFocused = true;
        if (query.length() < 30) {
            query += event.codepointAsString();
            scrollPos = scrollTarget = 0;
        }
        return true;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        int key = event.key();
        if (editingText != null) {
            if (key == InputConstants.KEY_BACKSPACE) {
                String now = editingText.get();
                if (!now.isEmpty()) editingText.set(event.hasControlDown() ? "" : now.substring(0, now.length() - 1));
                MyticClient.config().save();
            } else if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                editingText = null;
            }
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if (selected != null) {
                selected = null;
                scrollPos = scrollTarget = 0;
                return true;
            }
            if (searchFocused && !query.isEmpty()) {
                query = "";
                searchFocused = false;
                return true;
            }
        }
        if (searchFocused && key == InputConstants.KEY_BACKSPACE) {
            if (!query.isEmpty()) query = event.hasControlDown() ? "" : query.substring(0, query.length() - 1);
            scrollPos = scrollTarget = 0;
            return true;
        }
        if (key == InputConstants.KEY_RSHIFT) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        Compat.textInput(this, false);
        super.removed();
    }

    @Override
    public void onClose() {
        MyticClient.config().save();
        Compat.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
