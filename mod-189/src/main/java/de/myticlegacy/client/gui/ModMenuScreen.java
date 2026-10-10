package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.module.Category;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.Setting;
import de.myticlegacy.client.setting.SliderSetting;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod-Menü im Stil von Lunar/NRC: kompaktes Panel, Kategorien mit Unterstrich, eigenes Suchfeld (einfach lostippen),
 * Mod-Karten mit großem Icon und An/Aus-Leiste, Einstellungen pro Mod.
 */
public class ModMenuScreen extends MyticScreen {
    private static final int CARD_H = 80;
    private static final int GAP = 6;
    private static final int ROW_H = 22;
    private static final int HEADER = 34;
    private static final int FOOTER = 28;

    private final GuiScreen parent;
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

    public ModMenuScreen(GuiScreen parent) {
        this(parent, null);
    }

    public ModMenuScreen(GuiScreen parent, Module open) {
        super(MyticClient.NAME);
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
        pw = Math.min(560, Math.max(300, (int) (vw * shareW())));
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
        List<Module> list = new ArrayList<Module>();
        for (Module m : MyticClient.MODULES) {
            if (category != null && m.category != category) continue;
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.description.toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(m);
        }
        return list;
    }

    private int cx() {
        return px + 10;
    }

    private int cy() {
        return py + HEADER + 8;
    }

    private int cw() {
        return pw - 20;
    }

    private int ch() {
        return ph - HEADER - 8 - FOOTER;
    }

    private int columns() {
        return Math.max(2, (cw() + GAP) / (98 + GAP));
    }

    private int cardW() {
        return (cw() - (columns() - 1) * GAP) / columns();
    }

    private int[] card(int index) {
        int col = index % columns();
        int row = index / columns();
        return new int[]{cx() + col * (cardW() + GAP), cy() + row * (CARD_H + GAP) - (int) Math.round(scrollPos)};
    }

    private int contentHeight() {
        if (selected != null) return 40 + selected.settings.size() * (ROW_H + 3) + 26;
        int rows = (visibleModules().size() + columns() - 1) / columns();
        return Math.max(0, rows * (CARD_H + GAP) - GAP);
    }

    private static final class Tab {
        final String label;
        final Category category;
        final int x;
        final int w;

        Tab(String label, Category category, int x, int w) {
            this.label = label;
            this.category = category;
            this.x = x;
            this.w = w;
        }
    }

    private List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<Tab>();
        int x = px + 14 + font.width("MYTIC CLIENT") + 16;
        String[] labels = {"Alle", Category.HUD.label, Category.MECHANIK.label, Category.VISUELL.label, Category.CLIENT.label};
        Category[] cats = {null, Category.HUD, Category.MECHANIK, Category.VISUELL, Category.CLIENT};
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]);
            tabs.add(new Tab(labels[i], cats[i], x, w));
            x += w + 12;
        }
        return tabs;
    }

    private int[] searchBox() {
        int w = Math.max(70, Math.min(110, px + pw - 12 - (tabs().get(tabs().size() - 1).x + tabs().get(tabs().size() - 1).w + 12)));
        return new int[]{px + pw - 12 - w, py + 9, w, 16};
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void renderBackground(GuiGraphics g) {
        String mode = MyticClient.theme.menuBackground.get();
        if (mc.theWorld == null) {
            Panorama.render(width, height);
            g.fill(0, 0, width, height, 0x80000000);
        } else if (mode.equals("Unschärfe")) {
            blur(true);
            g.fill(0, 0, width, height, 0x40000000);
        } else {
            blur(false);
            if (mode.equals("Abdunkeln")) g.fill(0, 0, width, height, 0x99000000);
        }
    }

    /** Unschärfe im Spiel über den Blur-Shader von Minecraft. */
    private void blur(boolean on) {
        if (!OpenGlHelperCheck.shaders()) return;
        ShaderGroup current = mc.entityRenderer.getShaderGroup();
        boolean active = current != null && current.getShaderGroupName().endsWith("mytic_blur.json");
        if (on && !active) mc.entityRenderer.loadShader(new ResourceLocation("myticclient", "shaders/post/mytic_blur.json"));
        else if (!on && active) mc.entityRenderer.stopUseShader();
    }

    private static final class OpenGlHelperCheck {
        static boolean shaders() {
            return net.minecraft.client.renderer.OpenGlHelper.shadersSupported;
        }
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
            Ui.text(g, "MYTIC", px + 14, py + 13, 0xFFFFFFFF, true);
            Ui.text(g, "CLIENT", px + 14 + font.width("MYTIC "), py + 13, 0xFFB4B4B4, true);
        } else {
            Ui.glowText(g, "MYTIC", px + 14, py + 13, Ui.accent(), 1f);
            Ui.glowText(g, "CLIENT", px + 14 + font.width("MYTIC "), py + 13, Ui.accent2(), 0.8f);
        }

        for (Tab tab : tabs()) {
            boolean active = selected == null && tab.category == category;
            boolean hover = Ui.inside(mouseX, mouseY, tab.x - 4, py + 8, tab.w + 8, 18);
            float t = Ui.animate("tab:" + tab.label, active, 14f);
            float h = Ui.animate("tabh:" + tab.label, hover, 18f);
            if (lunar) {
                // Lunar: Kategorien als Pillen, die aktive hell hinterlegt
                float fillT = Math.max(t, h * 0.4f);
                if (fillT > 0.01f) Ui.rect(g, tab.x - 5, py + 9, tab.w + 10, 15, 4, Ui.withAlpha(0xFFFFFF, Math.round(fillT * 40)));
                Ui.text(g, tab.label, tab.x, py + 13, Ui.mix(0xFF9A9A9A, 0xFFFFFFFF, Math.max(t, h * 0.7f)), false);
                continue;
            }
            Ui.text(g, tab.label, tab.x, py + 13, Ui.mix(Ui.MUTED, Ui.TEXT, Math.max(t, h * 0.7f)), false);
            int line = Math.round(tab.w * t);
            if (line > 0) {
                int lx = tab.x + (tab.w - line) / 2;
                Ui.glow(g, lx, py + 24, line, 2, 1, Ui.accent(), 3, t);
                Ui.hGradient(g, lx, py + 24, line, 2, Ui.accent(), Ui.accent2());
            }
        }

        int[] sb = searchBox();
        boolean sHover = Ui.inside(mouseX, mouseY, sb[0], sb[1], sb[2], sb[3]);
        if (lunar) Ui.outline(g, sb[0], sb[1], sb[2], sb[3], 4, searchFocused ? 0x90FFFFFF : sHover ? 0x50FFFFFF : Ui.L_LINE, 0xFF1C1C1C);
        else Ui.outline(g, sb[0], sb[1], sb[2], sb[3], 8, searchFocused ? Ui.accent2() : sHover ? Ui.withAlpha(Ui.accent2(), 120) : Ui.LINE, Ui.SURFACE);
        String shown = query.isEmpty() ? (searchFocused ? "" : "Suchen …") : query;
        String clipped = font.plainSubstrByWidth(shown, sb[2] - 16);
        if (query.length() > 0 && font.width(query) > sb[2] - 16) clipped = tail(query, sb[2] - 16);
        Ui.text(g, clipped, sb[0] + 8, sb[1] + 4, query.isEmpty() ? Ui.MUTED : Ui.TEXT, false);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caret = sb[0] + 8 + font.width(query.isEmpty() ? "" : clipped);
            g.fill(caret, sb[1] + 3, caret + 1, sb[1] + 13, Ui.TEXT);
        }
        Ui.rect(g, px + 10, py + HEADER, pw - 20, 1, 0, lunar ? Ui.L_LINE : Ui.LINE);

        Gfx.scissor(g, cx(), cy() - 3, cx() + cw(), cy() + ch());
        Object hover = selected == null ? drawGrid(g, mouseX, mouseY) : drawSettings(g, mouseX, mouseY);
        Gfx.noScissor(g);
        drawScrollbar(g);

        // Fußzeile
        int fy = py + ph - FOOTER + 6;
        Ui.rect(g, px + 10, fy - 6, pw - 20, 1, 0, lunar ? Ui.L_LINE : Ui.LINE);
        int bw = font.width("HUD bearbeiten") + 20;
        boolean editHover = Ui.inside(mouseX, mouseY, px + 10, fy, bw, 16);
        float eh = Ui.animate("btn:edit", editHover, 16f);
        if (lunar) {
            Ui.outline(g, px + 10, fy, bw, 16, 4, Ui.mix(0x40FFFFFF, 0xB0FFFFFF, eh), Ui.mix(0xFF262626, 0xFF333333, eh));
        } else {
            Ui.glow(g, px + 10, fy, bw, 16, 8, Ui.accent(), 3, 0.4f + eh * 0.6f);
            Ui.rect(g, px + 10, fy, bw, 16, 8, Ui.accent());
            Ui.rect(g, px + 10 + bw - 16, fy, 16, 16, 8, Ui.PINK);
            Ui.hGradient(g, px + 18, fy, bw - 26, 16, Ui.mix(Ui.accent(), 0xFFFFFFFF, eh * 0.15f), Ui.mix(Ui.PINK, 0xFFFFFFFF, eh * 0.15f));
        }
        Ui.text(g, "HUD bearbeiten", px + 20, fy + 4, 0xFFFFFFFF, true);
        String info = MyticClient.MODULES.stream().filter(Module::enabled).count() + " von " + MyticClient.MODULES.size() + " aktiv";
        Ui.text(g, info, px + pw - 12 - font.width(info), fy + 4, Ui.MUTED, false);

        Gfx.pop(g);

        // Tooltip erst nach kurzem Verweilen
        if (hover != hovered) {
            hovered = hover;
            hoverSince = System.currentTimeMillis();
        }
        if (hover instanceof Module && System.currentTimeMillis() - hoverSince > 450) {
            Compat.tooltip(g, ((Module) hover).description, mouseX, mouseY);
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
            if (c[1] + CARD_H < cy() - 4 || c[1] > cy() + ch()) continue;
            boolean over = inContent && Ui.inside(mouseX, mouseY, c[0], c[1], w, CARD_H);
            if (over) hover = m;
            boolean on = m.enabled();
            float hv = Ui.animate("card:" + m.id, over, 16f);
            float onT = Ui.animate("on:" + m.id, on, 12f);

            boolean lunar = Ui.lunar();
            float lit = Math.max(onT * 0.75f, hv);
            if (lunar) Ui.outline(g, c[0], c[1], w, CARD_H, 4, Ui.mix(0x18FFFFFF, 0x50FFFFFF, hv), Ui.mix(Ui.L_CARD, Ui.L_CARD_HOVER, hv));
            else if (lit > 0.02f) Ui.glow(g, c[0], c[1], w, CARD_H, 6, hv > onT ? Ui.accent2() : Ui.accent(), 4, lit * 0.7f);
            if (!lunar) Ui.outline(g, c[0], c[1], w, CARD_H, 6, Ui.mix(Ui.LINE, Ui.withAlpha(hv > onT ? Ui.accent2() : Ui.accent(), 230), Math.max(onT * 0.7f, hv * 0.95f)),
                    Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hv));

            Gfx.push(g);
            Gfx.translate(g, c[0] + w / 2f - 16, c[1] + 8 - Math.round(hv * 2));
            Gfx.scale(g, 2f, 2f);
            g.renderItem(m.icon(), 0, 0);
            Gfx.pop(g);

            String name = font.plainSubstrByWidth(m.name, w - 10);
            Ui.centered(g, name, c[0] + w / 2, c[1] + 45, Ui.TEXT, false);

            int barY = c[1] + CARD_H - 20;
            if (lunar) {
                // Lunar: grüner bzw. roter Balken über die ganze Breite
                int state = Ui.mix(Ui.L_RED, Ui.L_GREEN, onT);
                Ui.rect(g, c[0] + 5, barY, w - 10, 13, 3, over ? Ui.mix(state, 0xFFFFFFFF, 0.12f) : state);
                Ui.centered(g, on ? "AKTIV" : "INAKTIV", c[0] + w / 2, barY + 3, 0xFFFFFFFF, false);
            }
            int barColor = Ui.mix(0xFF2B2440, Ui.accent(), onT);
            if (!lunar) Ui.rect(g, c[0] + 7, barY, w - 14, 13, 6, over ? Ui.mix(barColor, 0xFFFFFFFF, 0.1f) : barColor);
            if (!lunar && onT > 0.02f) {
                Ui.hGradient(g, c[0] + 13, barY, w - 26, 13, Ui.alpha(barColor, onT), Ui.alpha(Ui.mix(Ui.accent(), Ui.PINK, 0.55f), onT));
                Ui.rect(g, c[0] + w - 19, barY, 12, 13, 6, Ui.alpha(Ui.mix(Ui.accent(), Ui.PINK, 0.55f), onT));
            }
            if (!lunar) Ui.centered(g, on ? "AN" : "AUS", c[0] + w / 2, barY + 3, on ? 0xFFFFFFFF : Ui.MUTED, false);

            if (!m.settings.isEmpty()) {
                boolean gearHover = over && Ui.inside(mouseX, mouseY, c[0] + w - 16, c[1] + 3, 13, 13);
                int gearColor = gearHover ? (lunar ? 0xFFFFFFFF : Ui.accent()) : Ui.withAlpha(Ui.MUTED, over ? 255 : 110);
                Ui.text(g, "⚙", c[0] + w - 13, c[1] + 5, gearColor, false);
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
        Ui.rect(g, x, y + 2, 16, 16, 8, backHover ? Ui.SURFACE_HOVER : Ui.SURFACE);
        Ui.centered(g, "‹", x + 8, y + 6, Ui.TEXT, false);
        g.renderItem(m.icon(), x + 24, y + 2);
        Ui.text(g, m.name, x + 44, y + 2, Ui.TEXT, true);
        Ui.text(g, font.plainSubstrByWidth(m.description, w - 80), x + 44, y + 12, Ui.MUTED, false);
        Ui.toggle(g, "set:on:" + m.id, x + w - 22, y + 6, m.enabled());

        int row = y + 32;
        Object hover = null;
        for (Setting<?> setting : m.settings) {
            boolean over = Ui.inside(mouseX, mouseY, x, row, w, ROW_H);
            Ui.rect(g, x, row, w, ROW_H, 5, over ? Ui.SURFACE_HOVER : Ui.SURFACE);
            if (over && !Ui.lunar()) Ui.rect(g, x, row + 4, 2, ROW_H - 8, 1, Ui.accent2());
            Ui.text(g, setting.label, x + 8, row + 7, Ui.TEXT, false);
            int right = x + w - 8;
            if (setting instanceof BoolSetting) {
                BoolSetting b = (BoolSetting) setting;
                Ui.toggle(g, "set:" + m.id + "." + b.key, right - 20, row + 6, b.get());
            } else if (setting instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) setting;
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
            } else if (setting instanceof ColorSetting) {
                ColorSetting c = (ColorSetting) setting;
                int size = 8;
                int sx = right - ColorSetting.PALETTE.length * (size + 2) + 2;
                for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
                    int color = ColorSetting.PALETTE[i];
                    int px2 = sx + i * (size + 2);
                    if (color == c.get()) {
                        if (!Ui.lunar()) Ui.glow(g, px2 - 1, row + 6, size + 2, size + 2, 3, color == 0xFF000000 ? 0xFFFFFFFF : color, 3, 0.9f);
                        Ui.rect(g, px2 - 1, row + 6, size + 2, size + 2, 3, 0xFFFFFFFF);
                    }
                    Ui.rect(g, px2, row + 7, size, size, 2, color == 0xFF000000 ? 0xFF101010 : color);
                }
            } else if (setting instanceof ModeSetting) {
                ModeSetting mode = (ModeSetting) setting;
                int bw = Math.max(80, font.width(mode.get()) + 30);
                boolean ls = Ui.lunar();
                Ui.outline(g, right - bw, row + 3, bw, 16, ls ? 4 : 8, ls ? 0x30FFFFFF : Ui.withAlpha(Ui.accent(), 150), ls ? 0xFF2A2A2A : 0xFF1C1433);
                Ui.text(g, "‹", right - bw + 6, row + 7, ls ? 0xFFB4B4B4 : Ui.accent2(), false);
                Ui.text(g, "›", right - 10, row + 7, ls ? 0xFFB4B4B4 : Ui.accent2(), false);
                Ui.centered(g, mode.get(), right - bw / 2, row + 7, Ui.TEXT, false);
            }
            row += ROW_H + 3;
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
            if (Ui.inside(mx, my, tab.x - 4, py + 8, tab.w + 8, 18)) {
                category = tab.category;
                selected = null;
                scrollPos = scrollTarget = 0;
                return true;
            }
        }
        int fy = py + ph - FOOTER + 6;
        if (Ui.inside(mx, my, px + 10, fy, font.width("HUD bearbeiten") + 20, 16)) {
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
            if (!Ui.inside(mx, my, c[0], c[1], w, CARD_H)) continue;
            boolean gear = Ui.inside(mx, my, c[0] + w - 16, c[1] + 3, 13, 13);
            boolean bar = Ui.inside(mx, my, c[0] + 7, c[1] + CARD_H - 20, w - 14, 13);
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
            if (Ui.inside(mx, my, x, row, w, ROW_H)) {
                int right = x + w - 8;
                if (setting instanceof BoolSetting) {
                BoolSetting b = (BoolSetting) setting;
                    b.toggle();
                } else if (setting instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) setting;
                    int tx = right - 100;
                    if (mx >= tx - 6) {
                        dragging = sl;
                        sl.setFraction((mx - tx) / 100.0);
                    }
                } else if (setting instanceof ColorSetting) {
                ColorSetting c = (ColorSetting) setting;
                    int sx = right - ColorSetting.PALETTE.length * 10 + 2;
                    int i = (int) Math.floor((mx - sx) / 10);
                    if (i >= 0 && i < ColorSetting.PALETTE.length) c.set(ColorSetting.PALETTE[i]);
                } else if (setting instanceof ModeSetting) {
                ModeSetting mode = (ModeSetting) setting;
                    int bw = Math.max(80, font.width(mode.get()) + 30);
                    if (mx >= right - bw) mode.cycle(mx < right - bw / 2.0 ? -1 : 1);
                }
                MyticClient.config().save();
                return true;
            }
            row += ROW_H + 3;
        }
        int bw1 = font.width("Zurücksetzen") + 20;
        if (Ui.inside(mx, my, x, row + 4, bw1, 16)) {
            m.resetSettings();
            return true;
        }
        if (m instanceof HudModule && Ui.inside(mx, my, x + bw1 + 6, row + 4, font.width("Position zurücksetzen") + 20, 16)) {
            ((HudModule) m).resetPosition();
            MyticClient.config().save();
            return true;
        }
        return false;
    }

    @Override
    protected boolean drag(Input.Click event, double dx, double dy) {
        if (dragging == null) return false;
        int tx = cx() + cw() - 8 - 100;
        dragging.setFraction((event.x() - tx) / 100.0);
        return true;
    }

    @Override
    protected boolean release(Input.Click event) {
        if (dragging == null) return false;
        dragging = null;
        MyticClient.config().save();
        return true;
    }

    @Override
    protected boolean onChar(Input.Typed event) {
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
    public void onGuiClosed() {
        Compat.textInput(this, false);
        blur(false);
        super.onGuiClosed();
    }

    @Override
    public void onClose() {
        MyticClient.config().save();
        Compat.setScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
