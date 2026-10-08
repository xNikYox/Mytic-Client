package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.module.Category;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.Setting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod-Menü im Stil von Lunar/NRC: Kategorien, Suche, Mod-Karten mit Icons und An/Aus-Leiste,
 * Einstellungen pro Mod (Schalter, Regler, Farben, Auswahl).
 */
public class ModMenuScreen extends Screen {
    private static final int CARD_H = 72;
    private static final int GAP = 6;
    private static final int ROW_H = 24;

    private final Screen parent;
    private Category category;
    private Module selected;
    private EditBox search;
    private String query = "";
    private double scroll;
    private double scrollTarget;
    private SliderSetting dragging;
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

    @Override
    protected void init() {
        pw = Math.min(500, width - 24);
        ph = Math.min(320, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int searchW = pw < 430 ? 72 : 104;
        search = new EditBox(font, px + pw - searchW - 6, py + 11, searchW - 16, 12, Component.literal("Suche"));
        search.setBordered(false);
        search.setMaxLength(30);
        search.setHint(Component.literal("Suchen …").withColor(Ui.MUTED));
        search.setTextColor(Ui.TEXT);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            scrollTarget = 0;
        });
        addRenderableWidget(search);
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

    private int contentX() {
        return px + 10;
    }

    private int contentY() {
        return py + 40;
    }

    private int contentW() {
        return pw - 20;
    }

    private int contentH() {
        return ph - 40 - 30;
    }

    private int columns() {
        return Math.max(2, (contentW() + GAP) / (96 + GAP));
    }

    private int cardW() {
        return (contentW() - (columns() - 1) * GAP) / columns();
    }

    private int[] card(int index) {
        int col = index % columns();
        int row = index / columns();
        return new int[]{contentX() + col * (cardW() + GAP), contentY() + row * (CARD_H + GAP) - (int) Math.round(scroll)};
    }

    private int contentHeight() {
        if (selected != null) return 46 + selected.settings.size() * ROW_H + 30;
        int rows = (visibleModules().size() + columns() - 1) / columns();
        return rows * (CARD_H + GAP) - GAP;
    }

    private List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        int x = px + 12 + Math.round(font.width("MYTIC CLIENT") * 1.25f) + 14;
        String[] labels = {"Alle", Category.HUD.label, Category.MECHANIK.label, Category.VISUELL.label, Category.CLIENT.label};
        Category[] cats = {null, Category.HUD, Category.MECHANIK, Category.VISUELL, Category.CLIENT};
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + 14;
            tabs.add(new Tab(labels[i], cats[i], x, py + 9, w, 16));
            x += w + 3;
        }
        return tabs;
    }

    private record Tab(String label, Category category, int x, int y, int w, int h) {
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        String mode = MyticClient.theme.menuBackground.get();
        if (mode.equals("Unschärfe")) super.renderBackground(g, mouseX, mouseY, delta);
        else if (mode.equals("Abdunkeln") || minecraft.level == null) g.fill(0, 0, width, height, 0x99000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Ui.frame();
        scroll += (scrollTarget - scroll) * 0.35;
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 160f);
        float ease = 1f - (1f - open) * (1f - open);

        g.pose().pushMatrix();
        float s = 0.94f + 0.06f * ease;
        g.pose().translate(width / 2f, height / 2f);
        g.pose().scale(s, s);
        g.pose().translate(-width / 2f, -height / 2f);

        // Panel mit Schatten
        Ui.rect(g, px - 2, py + 2, pw + 4, ph + 2, 9, 0x40000000);
        Ui.rect(g, px, py, pw, ph, 8, Ui.PANEL);
        Ui.rect(g, px + 8, py, pw - 16, 1, 0, Ui.withAlpha(Ui.accent(), 140));

        Ui.logo(g, px + 12, py + 13, 1.25f);
        for (Tab tab : tabs()) {
            boolean active = selected == null && tab.category == category;
            boolean hover = Ui.inside(mouseX, mouseY, tab.x, tab.y, tab.w, tab.h);
            float t = Ui.animate("tab:" + tab.label, active, 14f);
            float h = Ui.animate("tabh:" + tab.label, hover, 16f);
            int bg = Ui.mix(Ui.withAlpha(Ui.SURFACE_HOVER, (int) (h * 255)), Ui.accent(), t);
            Ui.rect(g, tab.x, tab.y, tab.w, tab.h, 8, bg);
            Ui.centered(g, tab.label, tab.x + tab.w / 2, tab.y + 4, Ui.mix(Ui.MUTED, Ui.TEXT, Math.max(t, h)), false);
        }
        Ui.rect(g, search.getX() - 6, py + 7, search.getWidth() + 16, 18, 9, search.isFocused() ? Ui.SURFACE_HOVER : Ui.SURFACE);
        Ui.rect(g, px + 10, py + 33, pw - 20, 1, 0, Ui.LINE);

        g.enableScissor(contentX(), contentY() - 2, contentX() + contentW(), contentY() + contentH());
        if (selected == null) renderGrid(g, mouseX, mouseY);
        else renderSettings(g, mouseX, mouseY);
        g.disableScissor();
        renderScrollbar(g);

        // Fußzeile
        int fy = py + ph - 24;
        boolean editHover = Ui.inside(mouseX, mouseY, px + 10, fy, 96, 16);
        Ui.rect(g, px + 10, fy, 96, 16, 8, Ui.mix(Ui.accent(), 0xFFFFFFFF, Ui.animate("btn:edit", editHover, 16f) * 0.18f));
        Ui.centered(g, "HUD bearbeiten", px + 58, fy + 4, 0xFFFFFFFF, false);
        String info = MyticClient.NAME + " " + MyticClient.VERSION + " · " + MyticClient.MODULES.size() + " Mods";
        Ui.text(g, info, px + pw - 10 - font.width(info), fy + 4, Ui.MUTED, false);

        g.pose().popMatrix();
        super.render(g, mouseX, mouseY, delta);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        List<Module> modules = visibleModules();
        if (modules.isEmpty()) {
            Ui.centered(g, "Keine Mods gefunden", contentX() + contentW() / 2, contentY() + 30, Ui.MUTED, false);
            return;
        }
        boolean inContent = Ui.inside(mouseX, mouseY, contentX(), contentY(), contentW(), contentH());
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int[] c = card(i);
            int w = cardW();
            if (c[1] + CARD_H < contentY() - 4 || c[1] > contentY() + contentH()) continue;
            boolean hover = inContent && Ui.inside(mouseX, mouseY, c[0], c[1], w, CARD_H);
            boolean on = m.enabled();
            float hv = Ui.animate("card:" + m.id, hover, 16f);
            float onT = Ui.animate("on:" + m.id, on, 12f);

            int border = Ui.mix(Ui.LINE, Ui.accent(), onT * 0.85f);
            Ui.outline(g, c[0], c[1], w, CARD_H, 6, border, Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hv));

            g.pose().pushMatrix();
            float iconScale = 1.5f + 0.1f * hv;
            g.pose().translate(c[0] + w / 2f - 8 * iconScale, c[1] + 8 - hv);
            g.pose().scale(iconScale, iconScale);
            g.renderItem(m.icon(), 0, 0);
            g.pose().popMatrix();

            String name = font.plainSubstrByWidth(m.name, w - 8);
            Ui.centered(g, name, c[0] + w / 2, c[1] + 37, Ui.TEXT, false);

            int barY = c[1] + CARD_H - 19;
            boolean barHover = hover && Ui.inside(mouseX, mouseY, c[0] + 6, barY, w - 12, 13);
            int barColor = Ui.mix(0xFF2E2742, Ui.accent(), onT);
            if (barHover) barColor = Ui.mix(barColor, 0xFFFFFFFF, 0.12f);
            Ui.rect(g, c[0] + 6, barY, w - 12, 13, 6, barColor);
            String state = on ? "AKTIVIERT" : "DEAKTIVIERT";
            Ui.scaled(g, state, c[0] + w / 2f - font.width(state) * 0.375f, barY + 3.5f, 0.75f, on ? 0xFFFFFFFF : Ui.MUTED, false);

            if (!m.settings.isEmpty()) {
                boolean gearHover = hover && Ui.inside(mouseX, mouseY, c[0] + w - 16, c[1] + 3, 13, 13);
                Ui.text(g, "⚙", c[0] + w - 13, c[1] + 5, gearHover ? Ui.accent() : Ui.withAlpha(Ui.MUTED, hover ? 255 : 120), false);
            }
            if (hover) g.setTooltipForNextFrame(font, Component.literal(m.description), mouseX, mouseY);
        }
    }

    private void renderSettings(GuiGraphics g, int mouseX, int mouseY) {
        Module m = selected;
        int x = contentX();
        int y = contentY() - (int) Math.round(scroll);
        int w = contentW();

        boolean backHover = Ui.inside(mouseX, mouseY, x, y, 54, 16);
        Ui.rect(g, x, y, 54, 16, 8, backHover ? Ui.SURFACE_HOVER : Ui.SURFACE);
        Ui.centered(g, "‹ Zurück", x + 27, y + 4, Ui.TEXT, false);

        g.pose().pushMatrix();
        g.pose().translate(x + 62, y - 1);
        g.pose().scale(1.15f, 1.15f);
        g.renderItem(m.icon(), 0, 0);
        g.pose().popMatrix();
        Ui.scaled(g, m.name, x + 84, y + 1, 1.2f, Ui.TEXT, false);
        Ui.text(g, font.plainSubstrByWidth(m.description, w - 120), x + 84, y + 13, Ui.MUTED, false);
        Ui.toggle(g, "set:on:" + m.id, x + w - 24, y + 4, m.enabled());

        int row = y + 34;
        Ui.rect(g, x, row - 6, w, 1, 0, Ui.LINE);
        for (Setting<?> setting : m.settings) {
            boolean hover = Ui.inside(mouseX, mouseY, x, row, w, ROW_H - 2);
            Ui.rect(g, x, row, w, ROW_H - 2, 5, hover ? Ui.SURFACE_HOVER : Ui.SURFACE);
            Ui.text(g, setting.label, x + 8, row + 7, Ui.TEXT, false);
            int right = x + w - 8;
            if (setting instanceof BoolSetting b) {
                Ui.toggle(g, "set:" + m.id + "." + b.key, right - 20, row + 6, b.get());
            } else if (setting instanceof SliderSetting sl) {
                int trackW = 110;
                int tx = right - trackW;
                int ty = row + 10;
                Ui.rect(g, tx, ty, trackW, 3, 1, 0xFF3A3352);
                int fill = (int) Math.round(trackW * sl.fraction());
                Ui.rect(g, tx, ty, Math.max(3, fill), 3, 1, Ui.accent());
                Ui.rect(g, tx + fill - 4, ty - 3, 9, 9, 4, 0xFFFFFFFF);
                String value = sl.display();
                Ui.text(g, value, tx - 8 - font.width(value), row + 7, Ui.MUTED, false);
            } else if (setting instanceof ColorSetting c) {
                int size = 9;
                int sx = right - ColorSetting.PALETTE.length * (size + 2) + 2;
                for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
                    int color = ColorSetting.PALETTE[i];
                    int cx = sx + i * (size + 2);
                    if (color == c.get()) Ui.rect(g, cx - 1, row + 5, size + 2, size + 2, 3, 0xFFFFFFFF);
                    Ui.rect(g, cx, row + 6, size, size, 2, color == 0xFF000000 ? 0xFF101010 : color);
                }
            } else if (setting instanceof ModeSetting mode) {
                int bw = 96;
                Ui.rect(g, right - bw, row + 3, bw, 16, 8, 0xFF2E2742);
                Ui.centered(g, "‹  " + mode.get() + "  ›", right - bw / 2, row + 7, Ui.TEXT, false);
            }
            row += ROW_H;
        }
        int bx = x;
        boolean resetHover = Ui.inside(mouseX, mouseY, bx, row + 4, 120, 16);
        Ui.rect(g, bx, row + 4, 120, 16, 8, resetHover ? Ui.SURFACE_HOVER : Ui.SURFACE);
        Ui.centered(g, "Auf Standard zurücksetzen", bx + 60, row + 8, Ui.MUTED, false);
        if (m instanceof HudModule) {
            boolean posHover = Ui.inside(mouseX, mouseY, bx + 126, row + 4, 110, 16);
            Ui.rect(g, bx + 126, row + 4, 110, 16, 8, posHover ? Ui.SURFACE_HOVER : Ui.SURFACE);
            Ui.centered(g, "Position zurücksetzen", bx + 181, row + 8, Ui.MUTED, false);
        }
    }

    private void renderScrollbar(GuiGraphics g) {
        int total = contentHeight();
        if (total <= contentH()) return;
        int trackX = px + pw - 6;
        int barH = Math.max(18, contentH() * contentH() / total);
        int barY = contentY() + (int) ((contentH() - barH) * (scroll / (total - contentH())));
        Ui.rect(g, trackX, barY, 3, barH, 1, 0x80FFFFFF);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentHeight() - contentH())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget -= vertical * 24;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x();
        double my = event.y();
        if (event.button() != 0) return false;
        search.setFocused(false);

        for (Tab tab : tabs()) {
            if (Ui.inside(mx, my, tab.x, tab.y, tab.w, tab.h)) {
                category = tab.category;
                selected = null;
                scroll = scrollTarget = 0;
                return true;
            }
        }
        if (Ui.inside(mx, my, px + 10, py + ph - 24, 96, 16)) {
            minecraft.setScreen(new HudEditScreen(this));
            return true;
        }
        if (!Ui.inside(mx, my, contentX(), contentY(), contentW(), contentH())) return false;
        return selected == null ? clickGrid(mx, my) : clickSettings(mx, my);
    }

    private boolean clickGrid(double mx, double my) {
        List<Module> modules = visibleModules();
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int[] c = card(i);
            int w = cardW();
            if (!Ui.inside(mx, my, c[0], c[1], w, CARD_H)) continue;
            boolean gear = Ui.inside(mx, my, c[0] + w - 16, c[1] + 3, 13, 13);
            boolean bar = Ui.inside(mx, my, c[0] + 6, c[1] + CARD_H - 19, w - 12, 13);
            if (gear && !m.settings.isEmpty() && !bar) openSettings(m);
            else m.toggle();
            return true;
        }
        return false;
    }

    private void openSettings(Module m) {
        selected = m;
        scroll = scrollTarget = 0;
    }

    private boolean clickSettings(double mx, double my) {
        Module m = selected;
        int x = contentX();
        int y = contentY() - (int) Math.round(scroll);
        int w = contentW();
        if (Ui.inside(mx, my, x, y, 54, 16)) {
            selected = null;
            scroll = scrollTarget = 0;
            return true;
        }
        if (Ui.inside(mx, my, x + w - 26, y + 2, 24, 14)) {
            m.toggle();
            return true;
        }
        int row = y + 34;
        for (Setting<?> setting : m.settings) {
            if (Ui.inside(mx, my, x, row, w, ROW_H - 2)) {
                int right = x + w - 8;
                if (setting instanceof BoolSetting b) {
                    b.toggle();
                } else if (setting instanceof SliderSetting sl) {
                    int tx = right - 110;
                    if (mx >= tx - 4) {
                        dragging = sl;
                        sl.setFraction((mx - tx) / 110.0);
                    }
                } else if (setting instanceof ColorSetting c) {
                    int sx = right - ColorSetting.PALETTE.length * 11 + 2;
                    int i = (int) Math.floor((mx - sx) / 11);
                    if (i >= 0 && i < ColorSetting.PALETTE.length) c.set(ColorSetting.PALETTE[i]);
                } else if (setting instanceof ModeSetting mode) {
                    if (mx >= right - 96) mode.cycle(mx < right - 48 ? -1 : 1);
                }
                MyticClient.config().save();
                return true;
            }
            row += ROW_H;
        }
        if (Ui.inside(mx, my, x, row + 4, 120, 16)) {
            m.resetSettings();
            return true;
        }
        if (m instanceof HudModule hud && Ui.inside(mx, my, x + 126, row + 4, 110, 16)) {
            hud.resetPosition();
            MyticClient.config().save();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            int tx = contentX() + contentW() - 8 - 110;
            dragging.setFraction((event.x() - tx) / 110.0);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            MyticClient.config().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            selected = null;
            scroll = scrollTarget = 0;
            return true;
        }
        if (search.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        MyticClient.config().save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
