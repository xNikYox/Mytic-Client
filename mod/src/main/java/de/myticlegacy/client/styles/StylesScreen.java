package de.myticlegacy.client.styles;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Blit;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Tex;
import de.myticlegacy.client.gui.Input;
import de.myticlegacy.client.gui.PanelScreen;
import de.myticlegacy.client.gui.Shots;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mytic Styles: links die Kategorien (nur die, für die es Pakete gibt), rechts die Pakete als Karten mit Vorschau.
 * Klicks merken sich nur die Auswahl – übernommen wird beim Schließen, dann einmal neu geladen.
 */
public class StylesScreen extends PanelScreen {
    private static final int SIDE_W = 92, ROW = 16, CARD_W = 74, CARD_H = 84, GAP = 6;
    private final Screen parent;
    private final Map<Category, String> pick = new EnumMap<>(Category.class);
    /** Vorschaubilder: Bild-Daten → Textur (id, Breite, Höhe) */
    private final Map<byte[], Object[]> textures = new HashMap<>();
    private Category current = Category.WHOLE;
    private double scroll, scrollTarget;
    private volatile boolean loading;

    public StylesScreen(Screen parent) {
        super("Mytic Styles");
        this.parent = parent;
        for (Category c : Category.values()) {
            String id = Selection.get(c);
            if (id != null) pick.put(c, id);
        }
        if (!StylePacks.scanned()) rescan();
    }

    private void rescan() {
        loading = true;
        Thread t = new Thread(() -> {
            try {
                StylePacks.rescan();
            } finally {
                loading = false;
            }
        }, "Mytic Styles");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected String title() {
        return "MYTIC STYLES";
    }

    /** Schließen = übernehmen */
    @Override
    protected void back() {
        apply();
        Compat.setScreen(parent);
    }

    private void apply() {
        if (Selection.apply(pick)) de.myticlegacy.client.module.Msg.actionBar(pick.isEmpty() ? "Mytic Styles: Standard-Texturen" : "Mytic Styles werden geladen …");
    }

    @Override
    public void removed() {
        for (Object[] t : textures.values()) if (t != null) Tex.release(t[0]);
        textures.clear();
        super.removed();
    }

    private List<Category> categories() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) if (c == Category.WHOLE || !StylePacks.of(c).isEmpty()) out.add(c);
        return out;
    }

    private int gridX() {
        return px + SIDE_W + 18;
    }

    private int gridY() {
        return py + HEADER + 8;
    }

    private int gridW() {
        return pw - SIDE_W - 30;
    }

    private int gridH() {
        return ph - HEADER - FOOTER - 12;
    }

    private int cols() {
        return Math.max(1, (gridW() + GAP) / (CARD_W + GAP));
    }

    /** Einträge der aktuellen Kategorie, vorn „Standard“ (null) */
    private List<StylePack> entries() {
        List<StylePack> out = new ArrayList<>();
        out.add(null);
        out.addAll(StylePacks.of(current));
        return out;
    }

    private int[] card(int i) {
        int c = i % cols(), r = i / cols();
        return new int[]{gridX() + c * (CARD_W + GAP), gridY() + r * (CARD_H + GAP) - (int) Math.round(scroll)};
    }

    private int maxScroll() {
        int rows = (entries().size() + cols() - 1) / cols();
        return Math.max(0, rows * (CARD_H + GAP) - GAP - gridH());
    }

    private int[] button(int index) {
        String[] labels = {"Ordner öffnen", "Neu laden"};
        int x = px + 14;
        for (int i = 0; i < index; i++) x += font.width(labels[i]) + 26;
        return new int[]{x, py + ph - FOOTER + 6, font.width(labels[index]) + 20, 16};
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        scroll += (scrollTarget - scroll) * 0.35;
        boolean lunar = Ui.lunar();
        int text = lunar ? Ui.lText() : Ui.TEXT, muted = lunar ? Ui.lMuted() : Ui.MUTED, sel = lunar ? Ui.selection() : Ui.accent();
        if (!StylesModule.active()) Ui.text(g, "Modul ist aus – Auswahl wird nicht angewendet", headerX, py + 13, Ui.RED, false);

        // Kategorien
        int y = py + HEADER + 8;
        Category.Group group = null;
        for (Category c : categories()) {
            if (c.group != group) {
                group = c.group;
                if (!group.title.isEmpty()) {
                    Ui.scaled(g, group.title.toUpperCase(java.util.Locale.ROOT), px + 14, y + 3, 0.6f, muted, false);
                    y += 10;
                }
            }
            boolean active = c == current, hover = Ui.inside(mouseX, mouseY, px + 10, y, SIDE_W, ROW - 1);
            if (active || hover) Ui.rect(g, px + 10, y, SIDE_W, ROW - 1, 3, active ? Ui.withAlpha(sel, 0x40) : 0x18FFFFFF);
            Gfx.push(g);
            Gfx.translate(g, px + 13, y + 2);
            Gfx.scale(g, 0.7f, 0.7f);
            g.renderItem(c.icon(), 0, 0);
            Gfx.pop(g);
            Ui.text(g, font.plainSubstrByWidth(c.title, SIDE_W - 30), px + 28, y + 4, active ? text : muted, false);
            if (pick.containsKey(c)) Ui.rect(g, px + SIDE_W + 4, y + 5, 4, 4, 2, sel);
            y += ROW;
            if (y > py + ph - FOOTER - ROW) break;
        }
        g.fill(px + SIDE_W + 12, py + HEADER + 6, px + SIDE_W + 13, py + ph - FOOTER - 4, lunar ? Ui.L_LINE : Ui.LINE);

        // Karten
        if (loading) {
            Ui.centered(g, "Pakete werden gelesen …", gridX() + gridW() / 2, gridY() + 30, muted, false);
        } else if (StylePacks.all().isEmpty()) {
            Ui.centered(g, "Keine Pakete im resourcepacks-Ordner", gridX() + gridW() / 2, gridY() + 24, text, false);
            Ui.centered(g, "Pakete (.zip oder Ordner) hineinlegen, dann „Neu laden“", gridX() + gridW() / 2, gridY() + 38, muted, false);
        } else {
            Gfx.scissor(g, gridX() - 2, gridY() - 2, gridX() + gridW() + 2, gridY() + gridH());
            List<StylePack> list = entries();
            String chosen = pick.get(current);
            for (int i = 0; i < list.size(); i++) {
                int[] c = card(i);
                if (c[1] + CARD_H < gridY() - 4 || c[1] > gridY() + gridH()) continue;
                StylePack p = list.get(i);
                boolean on = p == null ? chosen == null : p.id().equals(chosen);
                boolean hover = Ui.inside(mouseX, mouseY, c[0], c[1], CARD_W, CARD_H) && Ui.inside(mouseX, mouseY, gridX(), gridY(), gridW(), gridH());
                float h = Ui.animate("style:" + current + i, hover, 16f);
                int card = lunar ? Ui.mix(Ui.L_CARD, Ui.L_CARD_HOVER, h) : Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, h);
                Ui.outline(g, c[0], c[1], CARD_W, CARD_H, 4, on ? sel : Ui.mix(lunar ? Ui.L_LINE : Ui.LINE, 0x60FFFFFF, h), card);
                int box = CARD_W - 16;
                if (p == null) Ui.centered(g, "–", c[0] + CARD_W / 2, c[1] + 8 + box / 2 - 4, muted, false);
                else preview(g, p, c[0] + 8, c[1] + 6, box);
                String name = p == null ? "Standard" : p.name();
                Ui.scaled(g, font.plainSubstrByWidth(name, Math.round((CARD_W - 6) / 0.75f)), c[0] + CARD_W / 2f - Math.min(font.width(name), (CARD_W - 6) / 0.75f) * 0.375f, c[1] + CARD_H - 15, 0.75f, on ? text : muted, false);
            }
            Gfx.noScissor(g);
        }

        // Fußzeile
        String[] labels = {"Ordner öffnen", "Neu laden"};
        for (int i = 0; i < labels.length; i++) {
            int[] b = button(i);
            boolean hover = Ui.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
            Ui.outline(g, b[0], b[1], b[2], b[3], 4, hover ? 0xB0FFFFFF : 0x40FFFFFF, lunar ? (hover ? Ui.L_CARD_HOVER : Ui.L_CARD) : (hover ? Ui.SURFACE_HOVER : Ui.SURFACE));
            Ui.text(g, labels[i], b[0] + 10, b[1] + 4, text, false);
        }
        String hint = "Esc = übernehmen";
        Ui.text(g, hint, px + pw - 14 - font.width(hint), py + ph - FOOTER + 10, muted, false);
    }

    /** Vorschau: Material-/Collage-Bilder (bis 4, als 2×2 bzw. nebeneinander) oder das Symbolbild */
    private void preview(GuiGraphics g, StylePack p, int x, int y, int size) {
        List<byte[]> pics = p.collage().isEmpty() ? (p.icon() == null ? List.of() : List.of(p.icon())) : p.collage();
        if (pics.isEmpty()) {
            Gfx.push(g);
            Gfx.translate(g, x + size / 2f - 12, y + size / 2f - 12);
            Gfx.scale(g, 1.5f, 1.5f);
            g.renderItem(p.category().icon(), 0, 0);
            Gfx.pop(g);
            return;
        }
        if (pics.size() == 1) {
            image(g, pics.get(0), x, y, size);
            return;
        }
        int half = size / 2;
        for (int i = 0; i < Math.min(4, pics.size()); i++) image(g, pics.get(i), x + (i % 2) * half, y + (i / 2) * half, half);
    }

    /** PNG als Textur zeichnen (bei Animations-Streifen nur das erste Bild) */
    private void image(GuiGraphics g, byte[] png, int x, int y, int size) {
        Object[] t = textures.computeIfAbsent(png, data -> {
            try {
                int w = ((data[16] & 255) << 24) | ((data[17] & 255) << 16) | ((data[18] & 255) << 8) | (data[19] & 255);
                int h = ((data[20] & 255) << 24) | ((data[21] & 255) << 16) | ((data[22] & 255) << 8) | (data[23] & 255);
                return new Object[]{Tex.upload("styles/" + Integer.toHexString(System.identityHashCode(data)), data), w, h};
            } catch (RuntimeException e) {
                return null;
            }
        });
        if (t == null) return;
        int w = (int) t[1], h = (int) t[2], frame = Math.min(w, h);
        Blit.region(g, (Identifier) t[0], x, y, size, size, 0, 0, frame, frame, w, h);
    }

    @Override
    protected boolean clickContent(double mx, double my) {
        // Fußzeile
        for (int i = 0; i < 2; i++) {
            int[] b = button(i);
            if (!Ui.inside(mx, my, b[0], b[1], b[2], b[3])) continue;
            if (i == 0) Shots.reveal(StylePacks.dir());
            else {
                for (Object[] t : textures.values()) if (t != null) Tex.release(t[0]);
                textures.clear();
                rescan();
            }
            return true;
        }
        // Kategorien
        int y = py + HEADER + 8;
        Category.Group group = null;
        for (Category c : categories()) {
            if (c.group != group) {
                group = c.group;
                if (!group.title.isEmpty()) y += 10;
            }
            if (Ui.inside(mx, my, px + 10, y, SIDE_W, ROW - 1)) {
                current = c;
                scroll = scrollTarget = 0;
                return true;
            }
            y += ROW;
        }
        // Karten
        if (loading || !Ui.inside(mx, my, gridX(), gridY(), gridW(), gridH())) return false;
        List<StylePack> list = entries();
        for (int i = 0; i < list.size(); i++) {
            int[] c = card(i);
            if (!Ui.inside(mx, my, c[0], c[1], CARD_W, CARD_H)) continue;
            StylePack p = list.get(i);
            if (p == null) pick.remove(current);
            else pick.put(current, p.id());
            return true;
        }
        return false;
    }

    @Override
    protected boolean scroll(double mouseX, double mouseY, double amount) {
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - amount * 30));
        return true;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RSHIFT) {
            back();
            return true;
        }
        return super.onKey(event);
    }

    @Override
    public void onClose() {
        back();
    }
}
