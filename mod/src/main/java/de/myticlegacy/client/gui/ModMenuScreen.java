package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.gui.ui.Brand;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Motion;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.module.Category;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.setting.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod-Menü im neuen Mytic-Design (wie die UI-Vorschau „Mod-Menü“): links Marke, Kategorien mit Anzahl,
 * „HUD bearbeiten“ und Client-Einstellungen; rechts Suche (einfach lostippen), Filter Alle/Aktiv/Inaktiv und
 * Mod-Kacheln mit Schalter. Ein Klick auf eine Kachel öffnet rechts den Detailbereich mit Live-Vorschau und allen
 * Einstellungen des Mods.
 */
public class ModMenuScreen extends DesignScreen {
    private static final float WIN_W = 1600, WIN_H = 880, SIDE = 264, DETAIL = 460, HEAD = 88, TILE_H = 200, GAP = 14;
    private static final Category[] CATS = Category.values();

    private final Screen parent;
    private Category category;
    private int filter; // 0 alle, 1 aktiv, 2 inaktiv
    private Module selected;
    private final Kit.Field search = new Kit.Field(30);
    private final Kit.Scroll gridScroll = new Kit.Scroll(), detailScroll = new Kit.Scroll();
    private final SettingRows rows = new SettingRows();
    private String lastQuery = "";

    public ModMenuScreen(Screen parent) {
        this(parent, null);
    }

    public ModMenuScreen(Screen parent, Module open) {
        super("mods", MyticClient.NAME);
        this.parent = parent;
        this.selected = open;
    }

    // ------------------------------------------------------------------------------------------ Daten

    private List<Module> visible() {
        String q = search.text.toLowerCase(Locale.ROOT).trim();
        List<Module> list = new ArrayList<Module>();
        for (Module m : MyticClient.MODULES) {
            if (category != null && m.category != category) continue;
            if (filter == 1 && !m.enabled() || filter == 2 && m.enabled()) continue;
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.description.toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(m);
        }
        return list;
    }

    private static int count(Category c) {
        int n = 0;
        for (Module m : MyticClient.MODULES) if (c == null || m.category == c) n++;
        return n;
    }

    private static int active() {
        int n = 0;
        for (Module m : MyticClient.MODULES) if (m.enabled()) n++;
        return n;
    }

    static String icon(Category c) {
        if (c == null) return "grid";
        switch (c) {
            case HUD:
                return "layout";
            case MECHANIK:
                return "swords";
            case VISUELL:
                return "eye";
            default:
                return "gear";
        }
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void frame(GuiGraphics g) {
        if (!search.text.equals(lastQuery)) {
            lastQuery = search.text;
            gridScroll.reset();
        }
        float[] r = center(WIN_W, WIN_H);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        kit.window(x, y, w, h, minecraft.level == null);
        drawSide(x, y, h);
        boolean detail = selected != null;
        float mainX = x + SIDE * d, mainW = w - SIDE * d - (detail ? DETAIL * d : 0);
        drawMain(mainX, y, mainW, h, detail);
        if (detail) drawDetail(x + w - DETAIL * d, y, DETAIL * d, h);
    }

    private void drawSide(float x, float y, float h) {
        float sw = SIDE * d;
        Draw.rect(kit.g, x + 1, y + 1, sw, h - 2, Tokens.RAD_XL * d, 0x04FFFFFF);
        kit.vline(x + sw, y, h);
        Brand.side(kit, x + 28 * d, y + 30 * d, "Mytic", "Mod-Menü");
        kit.caption("Kategorien", x + 36 * d, y + 104 * d);
        float ny = y + 126 * d, nx = x + 24 * d, nw = sw - 48 * d;
        for (int i = -1; i < CATS.length; i++) {
            final Category c = i < 0 ? null : CATS[i];
            kit.nav("cat" + i, nx, ny, nw, icon(c), c == null ? "Alle" : c.label, String.valueOf(count(c)), category == c, () -> {
                category = c;
                gridScroll.reset();
            });
            ny += 46 * d;
        }
        // unten: HUD bearbeiten, Client-Einstellungen, Hinweis
        float by = y + h - 28 * d - 24 * d - 12 * d - 38 * d - 10 * d - 48 * d;
        kit.button("hud", nx, by, nw, 48 * d, "HUD bearbeiten", "layout", Widgets.SECONDARY, () -> Compat.setScreen(new HudEditScreen(this)));
        kit.button("settings", nx, by + 58 * d, nw, 38 * d, "Client-Einstellungen", "gear", Widgets.GHOST, () -> Compat.setScreen(new SettingsScreen(this)));
        float hy = y + h - 28 * d - 24 * d;
        String hint = " öffnet dieses Menü";
        float kw = kitKbdWidth("R-SHIFT", 24 * d), tw = kit.width(hint, 13, Tokens.MEDIUM);
        float hx = x + sw / 2f - (kw + tw) / 2f;
        kit.kbd("R-SHIFT", hx, hy, 24 * d, false, false);
        kit.textMid(hint, hx + kw, hy + 12 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE, 0);
    }

    private float kitKbdWidth(String text, float h) {
        // gleiche Rechnung wie Kit.kbd (ohne zu zeichnen): mindestens 40, sonst Text + Rand
        int s = de.myticlegacy.client.gui.ui.Type.pixelScale(kit.g, Math.min(h * 0.32f, 9 * d));
        float t = de.myticlegacy.client.gui.ui.Type.pixelWidth(kit.g, text, s, 0);
        return Math.max(40 * d * h / (34 * d), t + 24 * d * h / (34 * d));
    }

    private void drawMain(float x, float y, float w, float h, boolean detail) {
        // Kopf: Suche, Filter, Anzahl, Schließen
        float pad = 32 * d;
        kit.iconButton("close", x + w - pad - 40 * d, y + 24 * d, 40 * d, "x", true, this::onClose);
        float fw = Math.max(180 * d, Math.min(420 * d, w - 2 * pad - 52 * d - 290 * d - 190 * d));
        kit.field("search", search, x + pad, y + 21 * d, fw, 46 * d, "Mods durchsuchen …", "search");
        float sx = x + pad + fw + 12 * d;
        float segW = kit.seg("filter", sx, y + 22 * d, new String[]{"Alle", "Aktiv", "Inaktiv"}, null, null, filter, i -> {
            filter = i;
            gridScroll.reset();
        });
        List<Module> mods = visible();
        String info = mods.size() + " von " + MyticClient.MODULES.size() + " · " + active() + " aktiv";
        float ix = sx + segW + 16 * d, room = x + w - pad - 40 * d - 16 * d - ix;
        if (room > kit.width(info, 14.5f, Tokens.MEDIUM)) kit.textMid(info, ix, y + 44 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE, 0);
        kit.hline(x, y + HEAD * d, w);

        // Raster
        float bx = x + pad, by = y + HEAD * d, bw = w - 2 * pad, bh = h - HEAD * d;
        int cols = detail ? 3 : 4;
        if (bw / cols < 200 * d) cols = Math.max(1, cols - 1);
        float tw = (bw - (cols - 1) * GAP * d) / cols, th = TILE_H * d;
        int lines = (mods.size() + cols - 1) / cols;
        float content = 28 * d + lines * (th + GAP * d) - GAP * d + 28 * d;
        float off = kit.scroll(gridScroll, x, by, w, bh, content);
        kit.clip(x, by + 1, w, bh - 2);
        if (mods.isEmpty()) {
            float cx = x + w / 2f, cy = by + 110 * d;
            Icons.draw(kit.g, "search", cx - 16 * d, cy - 16 * d, 32 * d, Tokens.MUTED);
            kit.textCenter("Keine Mods gefunden", cx, cy + 40 * d, 15, Tokens.MEDIUM, Tokens.MUTED);
        }
        for (int i = 0; i < mods.size(); i++) {
            float tx = bx + (i % cols) * (tw + GAP * d), ty = by + 28 * d + (i / cols) * (th + GAP * d) - off;
            if (ty + th < by || ty > by + bh) continue;
            drawTile(mods.get(i), tx, ty, tw, th);
        }
        kit.unclip();
        kit.scrollbar("grid", gridScroll, x + w - 12 * d, by + 8 * d, bh - 16 * d);
    }

    private void drawTile(final Module m, float x, float y, float w, float h) {
        String id = "t:" + m.id;
        boolean hv = kit.hit(id, x, y, w, h, () -> select(m));
        boolean on = m.enabled(), sel = m == selected;
        float hvT = Motion.to(kit.key(id) + ":hv", hv, Tokens.MS_HOVER);
        float selT = Motion.to(kit.key(id) + ":sel", sel, 200);
        float onT = Motion.to(kit.key(id) + ":on", on, Tokens.MS_TOGGLE);
        y -= 2 * d * hvT;
        int fill = Draw.mix(Draw.mix(Tokens.SURFACE, Tokens.SURFACE_HOVER, hvT), 0x1A9B5CFF, selT);
        int line = Draw.mix(Draw.mix(Tokens.LINE, Tokens.LINE_STRONG, hvT), Tokens.ACC_LINE, selT);
        Draw.glass(kit.g, x, y, w, h, 16 * d, fill, line);
        if (selT > 0.01f) Draw.ring(kit.g, x + 1, y + 1, w - 2, h - 2, 15 * d, 1, Draw.alpha(Tokens.ACC_LINE, selT));
        float p = 18 * d;
        iconSquare(m.icon(), x + p, y + p, 46 * d, onT);
        if (m instanceof HudModule) {
            float bw = kit.width("HUD", 11.5f, Tokens.SEMIBOLD) + 18 * d;
            Widgets.badge(kit.g, x + w - p - bw, y + p, 22 * d, d, "HUD", false);
        }
        kit.textMid(m.name, x + p, y + p + 46 * d + 24 * d, 16.5f, Tokens.SEMIBOLD, Tokens.TEXT, w - 2 * p);
        kit.paragraph(m.description, x + p, y + p + 46 * d + 42 * d, w - 2 * p, 13.5f, Tokens.MEDIUM, Tokens.MUTED, 19, 2);
        float fy = y + h - p - 34 * d;
        kit.textMid(on ? "AKTIV" : "AUS", x + p, fy + 17 * d, 12.5f, Tokens.SEMIBOLD, Draw.mix(Tokens.SUBTLE, 0xFFD9C2FF, onT), 0);
        float tgx = x + w - p - 46 * d;
        kit.toggle("tg:" + m.id, tgx, fy + 4 * d, on, () -> {
            m.toggle();
            MyticClient.config().save();
        });
        if (!m.settings.isEmpty()) {
            boolean gh = kit.iconButton("gear:" + m.id, tgx - 8 * d - 34 * d, fy, 34 * d, "gear", false, () -> select(m));
            if (gh) kit.tooltip("Einstellungen", tgx - 8 * d - 17 * d, fy + 40 * d);
        }
    }

    /** Icon-Quadrat mit dem Item des Mods; an = Akzent-Verlauf */
    private void iconSquare(ItemStack stack, float x, float y, float s, float onT) {
        Draw.rect(kit.g, x, y, s, s, 12 * d, 0x12FFFFFF);
        if (onT > 0.01f) Draw.gradient(kit.g, x, y, s, s, 12 * d, Draw.alpha(0x739B5CFF, onT), Draw.alpha(0x59EC5FBF, onT));
        item(stack, x + s / 2f, y + s / 2f, s * 0.56f);
    }

    /** Item mittig bei cx, cy in Kantenlänge size zeichnen */
    private void item(ItemStack stack, float cx, float cy, float size) {
        if (stack == null || stack.isEmpty()) return;
        Gfx.push(kit.g);
        Gfx.translate(kit.g, cx - size / 2f, cy - size / 2f);
        Gfx.scale(kit.g, size / 16f, size / 16f);
        kit.g.renderItem(stack, 0, 0);
        Gfx.pop(kit.g);
    }

    private void select(Module m) {
        if (selected != m) detailScroll.reset();
        selected = m;
    }

    // ------------------------------------------------------------------------------------------ Detailbereich

    private void drawDetail(float x, float y, float w, float h) {
        final Module m = selected;
        float R = Tokens.RAD_XL * d;
        Draw.rect(kit.g, x, y + 1, w - 1, h - 2, R, 0x24000000);
        Draw.rect(kit.g, x, y + 1, R, h - 2, 0, 0x24000000);
        kit.vline(x, y, h);
        kit.block(x, y, w, h, null);
        float p = 24 * d;
        // Kopf
        float is = 42 * d, iy = y + (HEAD * d - is) / 2f;
        Draw.gradient(kit.g, x + p, iy, is, is, 12 * d, 0x739B5CFF, 0x59EC5FBF);
        item(m.icon(), x + p + is / 2f, iy + is / 2f, is * 0.55f);
        float closeX = x + w - p - 34 * d, tgX = closeX - 12 * d - 46 * d;
        float tx = x + p + is + 14 * d, tw = tgX - 12 * d - tx;
        kit.textMid(m.name, tx, y + 34 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT, tw);
        kit.textMid(m.category.label + (m instanceof HudModule ? " · Anzeige" : "") + " · " + (m.enabled() ? "aktiv" : "aus"), tx, y + 56 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE, tw);
        kit.toggle("dtg", tgX, y + HEAD * d / 2f - 13 * d, m.enabled(), () -> {
            m.toggle();
            MyticClient.config().save();
        });
        kit.iconButton("dclose", closeX, y + HEAD * d / 2f - 17 * d, 34 * d, "x", false, () -> selected = null);
        kit.hline(x, y + HEAD * d, w);

        // Live-Vorschau
        float px = x + p, py = y + HEAD * d + 20 * d, pw = w - 2 * p, ph = 170 * d;
        preview(m, px, py, pw, ph);

        // Einstellungen
        float foot = 76 * d;
        float ly = py + ph + 14 * d, lh = y + h - foot - ly;
        float descH = 0;
        List<Setting<?>> list = m.settings;
        float content = 0;
        for (Setting<?> s : list) content += SettingRows.stackedHeight(s) * d;
        int descLines = Math.max(1, Math.min(3, (int) Math.ceil(kit.width(m.description, 14.5f, Tokens.MEDIUM) / pw) + 0));
        descH = (10 + descLines * 21 + 6) * d;
        content += descH + (list.isEmpty() ? 40 * d : 0) + 12 * d;
        float off = kit.scroll(detailScroll, x, ly, w, lh, content);
        kit.clip(x, ly, w, lh);
        float ry = ly - off;
        kit.paragraph(m.description, px, ry + 10 * d, pw, 14.5f, Tokens.MEDIUM, Tokens.MUTED, 21, 3);
        ry += descH;
        if (list.isEmpty()) kit.text("Dieser Mod hat keine Einstellungen.", px, ry + 14 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        for (int i = 0; i < list.size(); i++) {
            Setting<?> s = list.get(i);
            float sh = SettingRows.stackedHeight(s) * d;
            if (ry + sh >= ly && ry <= ly + lh) {
                if (i > 0) SettingRows.divider(kit, px, ry, pw);
                rows.stacked(kit, s, px, ry, pw);
            }
            ry += sh;
        }
        kit.unclip();
        kit.scrollbar("detail", detailScroll, x + w - 10 * d, ly + 4 * d, lh - 8 * d);

        // Fuß
        float fy = y + h - foot;
        kit.hline(x, fy, w);
        float bh = 38 * d, by = fy + (foot - bh) / 2f;
        float bw = kit.button("reset", px, by, bh, "Standard", "rotL", Widgets.GHOST, () -> {
            m.resetSettings();
            MyticClient.config().save();
        });
        if (m instanceof HudModule) {
            final HudModule hud = (HudModule) m;
            kit.button("pos", px + bw + 8 * d, by, bh, "Position", "move", Widgets.GHOST, () -> {
                hud.resetPosition();
                MyticClient.config().save();
            });
            if (kit.hover("pos")) kit.tooltip("Position zurücksetzen", px + bw + 60 * d, by - 34 * d);
        }
        float gw = kit.width("Gespeichert", 12.5f, Tokens.SEMIBOLD) + 36 * d;
        kit.badge("Gespeichert", "check", x + w - p - gw, fy + foot / 2f - 12 * d, Tokens.OK);
    }

    private void preview(Module m, float x, float y, float w, float h) {
        float r = 16 * d, sky = h * 0.56f;
        GuiGraphics g = kit.g;
        Draw.rect(g, x, y, w, sky + r, r, 0xFF4B2F78);
        Draw.vgradient(g, x, y + r, w, sky - r, 0xFF4B2F78, 0xFF9A4F86);
        Draw.rect(g, x, y + sky, w, h - sky, r, 0xFF2D5230);
        Draw.vgradient(g, x, y + sky, w, h - sky - r, 0xFF3D6B3C, 0xFF2D5230);
        Draw.ring(g, x, y, w, h, r, 1, Tokens.LINE);
        Draw.ring(g, x, y + sky, w, 1, 0, 1, 0x14000000);
        kit.text("LIVE-VORSCHAU", x + 12 * d, y + 12 * d, 11, Tokens.SEMIBOLD, 0xB3FFFFFF);
        float cx = x + w / 2f, cy = y + h * 0.54f;
        if (m instanceof HudModule) {
            HudModule hud = (HudModule) m;
            float k = Math.max(0.5f, Math.min(Math.max(2f, Compat.guiScale()), Math.min((w - 40 * d) / Math.max(1, hud.width()), (h - 54 * d) / Math.max(1, hud.height()))));
            kit.clip(x + 1, y + 1, w - 2, h - 2);
            Gfx.push(g);
            Gfx.translate(g, Math.round(cx - hud.width() * k / 2f), Math.round(cy - hud.height() * k / 2f + 8 * d));
            Gfx.scale(g, k, k);
            Gfx.translate(g, -hud.x(), -hud.y());
            try {
                hud.draw(g, true);
            } catch (RuntimeException ignored) {
                // Vorschau ohne Welt: lieber leer als abstürzen
            }
            Gfx.pop(g);
            kit.unclip();
            return;
        }
        String label = m.name + (m.enabled() ? " aktiv" : " aus");
        float fs = 16, bh = 40 * d, bw = kit.width(label, fs, Tokens.SEMIBOLD) + 24 * d + 30 * d;
        Draw.rect(g, cx - bw / 2f, cy - bh / 2f, bw, bh, 8 * d, 0x7308060E);
        item(m.icon(), cx - bw / 2f + 12 * d + 10 * d, cy, 20 * d);
        kit.textMid(label, cx - bw / 2f + 12 * d + 30 * d, cy, fs, Tokens.SEMIBOLD, 0xFFFFFFFF, 0);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean release(Input.Click event) {
        boolean r = super.release(event);
        rows.saveIfDirty();
        return r;
    }

    @Override
    protected boolean typedFree(String s) {
        if (s.isEmpty() || s.charAt(0) < ' ') return false;
        kit.focus(search);
        return kit.typed(s);
    }

    @Override
    protected void back() {
        if (selected != null) {
            selected = null;
        } else if (!search.text.isEmpty()) {
            search.text = "";
        } else {
            onClose();
        }
    }

    @Override
    public void onClose() {
        rows.saveIfDirty();
        MyticClient.config().save();
        Compat.setScreen(parent);
    }
}
