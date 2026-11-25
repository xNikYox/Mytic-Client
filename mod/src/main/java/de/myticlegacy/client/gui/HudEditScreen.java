package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.ui.Bake;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Motion;
import de.myticlegacy.client.gui.ui.Toasts;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.hud.HudProfiles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Editor im neuen Mytic-Design: Die echten Anzeigen bleiben an ihrem Platz und lassen sich ziehen (Hilfslinien
 * und Einrasten an Bildschirm-Mitte/-Rändern und anderen Anzeigen, abschaltbar), Mausrad ändert die Größe,
 * Rechtsklick öffnet ein kleines Optionsfenster neben der Anzeige. Oben die Werkzeugleiste (Raster, Einrasten,
 * Zurücksetzen, Fertig), unten links „Elemente“ mit Sichtbarkeit und den HUD-Profilen.
 * Anzeigen liegen in GUI-Einheiten, die Oberfläche wird in echten Pixeln gezeichnet (k = Pixel je GUI-Einheit).
 */
public class HudEditScreen extends InputScreen {
    private static final int SNAP = 4;
    private static final int[] SWATCHES = {0xFFFFFFFF, 0xFFD9C2FF, 0xFFEC5FBF, 0xFF5EA2FF, 0xFF4ADE80, 0xFFFACC15, 0xFFFF8A4C, 0xFFFF5C72};
    /** Bleibt für die Sitzung erhalten */
    private static boolean grid = true, snapping = true, drawerOpen = true;

    private final Screen parent;
    private final Kit kit = new Kit("hud");
    private final Kit.Scroll listScroll = new Kit.Scroll();
    private final Kit.Field renameField = new Kit.Field(20);
    private HudModule dragging, selected, popover;
    private double offsetX, offsetY;
    private int dragX, dragY;
    private final List<int[]> guides = new ArrayList<int[]>();
    /** Flächen der Oberfläche im letzten Bild (Pixel) – darunter keine Anzeigen greifen */
    private List<float[]> chromeDone = new ArrayList<float[]>(), building = new ArrayList<float[]>();
    private boolean confirmReset;
    private String renaming, confirmDelete;
    private float k = 1, d = 1;

    public HudEditScreen(Screen parent) {
        super(Component.literal("HUD bearbeiten"));
        this.parent = parent;
        renameField.allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789äöüÄÖÜß -_";
        renameField.onEnter = this::finishRename;
    }

    private List<HudModule> active() {
        List<HudModule> list = new ArrayList<HudModule>();
        for (HudModule m : MyticClient.HUD) if (m.enabled()) list.add(m);
        return list;
    }

    private HudModule at(double mx, double my) {
        List<HudModule> list = active();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule m = list.get(i);
            if (Ui.inside(mx, my, m.x(), m.y(), m.width(), m.height())) return m;
        }
        return null;
    }

    /** Liegt der Punkt (GUI-Einheiten) auf der Oberfläche (Leiste, Liste, Dialog)? */
    private boolean onChrome(double gx, double gy) {
        if (confirmReset || renaming != null || confirmDelete != null) return true;
        double px = gx * k, py = gy * k;
        for (float[] r : chromeDone) if (px >= r[0] && py >= r[1] && px < r[0] + r[2] && py < r[1] + r[3]) return true;
        return false;
    }

    private void chrome(float x, float y, float w, float h) {
        building.add(new float[]{x, y, w, h});
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x1F07050E); // leichter Schleier rgba(7,5,14,.12): das Spiel bleibt sichtbar
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Ui.frame();
        if (Compat.SCREEN_RENDERS_BACKGROUND) renderBackground(g, mouseX, mouseY, delta);
        paint(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    private void paint(GuiGraphics g, int mouseX, int mouseY) {
        if (popover != null && !popover.enabled()) popover = null;
        if (selected != null && !selected.enabled()) selected = null;
        boolean overUi = onChrome(mouseX, mouseY);
        HudModule hover = dragging != null ? dragging : overUi ? null : at(mouseX, mouseY);
        List<HudModule> list = active();
        for (HudModule m : list) m.draw(g, true);

        k = Draw.begin(g);
        int W = Math.round(width * k), H = Math.round(height * k);
        d = Math.max(0.45f, Math.min(W / 1920f, H / 1080f));
        kit.begin(g, W, H, d, Math.round(mouseX * k), Math.round(mouseY * k));
        building = new ArrayList<float[]>();

        if (grid) dots(g, W, H);
        // Hilfslinien (Akzent) beim Ziehen
        for (int[] guide : guides) {
            int p = Math.round(guide[1] * k);
            if (guide[0] == 0) g.fill(p, 0, p + 1, H, 0xCCB56CFF);
            else g.fill(0, p, W, p + 1, 0xCCB56CFF);
        }
        for (HudModule m : list) outline(g, m, m == hover);
        if (dragging != null) coordTip(g, dragging);
        else if (hover != null && popover == null) nameTip(hover);

        bar(W);
        drawer(H);
        if (popover != null) popover(W, H);
        if (confirmReset) confirmReset(W, H);
        if (renaming != null) renameDialog(W, H);
        if (confirmDelete != null) deleteDialog(W, H);
        kit.end();
        Toasts.draw(g, W, H, d);
        Draw.end(g);
        chromeDone = building;
    }

    private void dots(GuiGraphics g, int W, int H) {
        int step = Math.max(8, Math.round(24 * d));
        int s = Math.max(1, Math.round(1.6f * d));
        for (int y = step / 2; y < H; y += step) {
            for (int x = step / 2; x < W; x += step) g.fill(x, y, x + s, y + s, 0x29FFFFFF);
        }
    }

    /** Gestrichelter Rahmen (Abstand 4 Design-Pixel), ausgewählt: 2 px Akzent mit Griffen an den Ecken */
    private void outline(GuiGraphics g, HudModule m, boolean hover) {
        float hv = Motion.to(kit.key("el:" + m.id), hover, Tokens.MS_HOVER);
        boolean sel = m == selected || m == popover || m == dragging;
        int x = Math.round(m.x() * k), y = Math.round(m.y() * k), w = Math.round(m.width() * k), h = Math.round(m.height() * k);
        if (sel) {
            int o = Math.round(5 * d);
            int t = Math.max(2, Math.round(2 * d));
            Draw.pxRing(g, x - o - t, y - o - t, w + 2 * (o + t), h + 2 * (o + t), Math.round(4 * d), t, Tokens.ACC);
            int hs = Math.max(6, Math.round(9 * d));
            int[][] corners = {{x - o - t, y - o - t}, {x + w + o + t, y - o - t}, {x - o - t, y + h + o + t}, {x + w + o + t, y + h + o + t}};
            for (int[] c : corners) {
                Draw.pxRect(g, c[0] - hs / 2, c[1] - hs / 2, hs, hs, Math.max(1, Math.round(2 * d)), Tokens.ACC);
                int in = Math.max(1, Math.round(2 * d));
                Draw.pxRect(g, c[0] - hs / 2 + in, c[1] - hs / 2 + in, hs - 2 * in, hs - 2 * in, 1, 0xFFFFFFFF);
            }
            return;
        }
        int o = Math.round(4 * d);
        int color = Draw.mix(0x40FFFFFF, 0x8CFFFFFF, hv);
        dashed(g, x - o, y - o, w + 2 * o, h + 2 * o, color);
    }

    private void dashed(GuiGraphics g, int x, int y, int w, int h, int color) {
        int dash = Math.max(3, Math.round(5 * d)), gap = Math.max(2, Math.round(4 * d));
        for (int i = 0; i < w; i += dash + gap) {
            int e = Math.min(w, i + dash);
            g.fill(x + i, y, x + e, y + 1, color);
            g.fill(x + i, y + h - 1, x + e, y + h, color);
        }
        for (int i = 0; i < h; i += dash + gap) {
            int e = Math.min(h, i + dash);
            g.fill(x, y + i, x + 1, y + e, color);
            g.fill(x + w - 1, y + i, x + w, y + e, color);
        }
    }

    private void pill(String text, float cx, float y, boolean accent) {
        float fs = 13, w = kit.width(text, fs, Tokens.SEMIBOLD) + 20 * d, h = 28 * d;
        float x = Math.max(4 * d, Math.min(kit.vw - w - 4 * d, cx - w / 2f));
        y = Math.max(4 * d, Math.min(kit.vh - h - 4 * d, y));
        if (accent) {
            Draw.shadow(kit.g, x, y + 6 * d, w, h, h / 2, 16 * d, -4 * d, 0x80C25EF0);
            Draw.accentRect(kit.g, x, y, w, h, h / 2, 1f);
        } else {
            Draw.glass(kit.g, x, y, w, h, h / 2, Tokens.GLASS_STRONG, Tokens.LINE_STRONG);
        }
        kit.textMid(text, x + 10 * d, y + h / 2, fs, Tokens.SEMIBOLD, accent ? 0xFFFFFFFF : Tokens.TEXT, 0);
    }

    private void coordTip(GuiGraphics g, HudModule m) {
        String text = "X " + m.x() + " · Y " + m.y();
        float cx = (m.x() + m.width() / 2f) * k, top = m.y() * k - 8 * d - 28 * d - 6 * d;
        pill(text, cx, top < 4 * d ? (m.y() + m.height()) * k + 14 * d : top, true);
    }

    private void nameTip(HudModule m) {
        String text = m.name + " · " + Math.round(m.scaleFactor() * 100) + " %";
        float cx = (m.x() + m.width() / 2f) * k, top = m.y() * k - 8 * d - 28 * d - 6 * d;
        pill(text, cx, top < 4 * d ? (m.y() + m.height()) * k + 14 * d : top, false);
    }

    // ------------------------------------------------------------------------------------------ Werkzeugleiste

    private void toggleButton(String id, float x, float y, float w, float h, String label, String icon, boolean on, Runnable action) {
        kit.button(id, x, y, w, h, label, icon, Widgets.SECONDARY, action);
        float a = Motion.to(kit.key(id) + ":on", on, 200);
        if (a > 0.01f) {
            Draw.rect(kit.g, x, y, w, h, Tokens.RAD_SM * d, Draw.alpha(Tokens.ACC_SOFT, a));
            Draw.ring(kit.g, x, y, w, h, Tokens.RAD_SM * d, 1, Draw.alpha(Tokens.ACC_LINE, a));
        }
    }

    private void bar(int W) {
        float bh = 38 * d, fs = 16;
        String hint = "Ziehen · Mausrad: Größe · Rechtsklick: Optionen";
        float es = 26 * d;
        float titleW = es + 10 * d + kit.width("HUD-Editor", fs, Tokens.SEMIBOLD);
        float hintW = kit.width(hint, 13.5f, Tokens.MEDIUM) + 16 * d + 4 * d;
        float wGrid = kit.buttonWidth("Raster", "dots9", bh), wSnap = kit.buttonWidth("Einrasten", "magnet", bh);
        float wReset = kit.buttonWidth("Zurücksetzen", "rotL", bh), wDone = kit.buttonWidth("Fertig", "check", bh) + 8 * d;
        float gap = 12 * d;
        float w = 18 * d + titleW + gap + hintW + gap + wGrid + gap + wSnap + gap + wReset + gap + wDone + 10 * d;
        boolean showHint = w <= W - 32 * d;
        if (!showHint) w -= hintW + gap;
        float h = bh + 20 * d, x = Math.round((W - w) / 2f), y = 32 * d;
        Draw.shadow(kit.g, x, y + 20 * d, w, h, 18 * d, 50 * d, -16 * d, 0x99000000);
        Draw.glass(kit.g, x, y, w, h, 18 * d, Tokens.GLASS_STRONG, Tokens.LINE);
        kit.block(x, y, w, h, null);
        chrome(x, y, w, h);
        float cx = x + 18 * d, cy = y + h / 2;
        float k0 = Draw.begin(kit.g);
        Draw.pxBaked(kit.g, Bake.emblem(Math.max(8, Math.round(es * k0))), Math.round(cx * k0), Math.round((cy - es / 2) * k0), 0xFFFFFFFF);
        Draw.end(kit.g);
        kit.textMid("HUD-Editor", cx + es + 10 * d, cy, fs, Tokens.SEMIBOLD, Tokens.TEXT, 0);
        cx += titleW + gap;
        if (showHint) {
            kit.vline(cx, y + 14 * d, h - 28 * d);
            kit.textMid(hint, cx + 12 * d, cy, 13.5f, Tokens.MEDIUM, Tokens.MUTED, 0);
            cx += hintW + gap;
        }
        float by = y + 10 * d;
        toggleButton("grid", cx, by, wGrid, bh, "Raster", "dots9", grid, () -> grid = !grid);
        cx += wGrid + gap;
        toggleButton("snap", cx, by, wSnap, bh, "Einrasten", "magnet", snapping, () -> snapping = !snapping);
        cx += wSnap + gap;
        kit.button("reset", cx, by, wReset, bh, "Zurücksetzen", "rotL", Widgets.GHOST, () -> confirmReset = true);
        cx += wReset + gap;
        kit.button("done", cx, by, wDone, bh, "Fertig", "check", Widgets.PRIMARY, this::onClose);
    }

    // ------------------------------------------------------------------------------------------ Elemente

    private static String icon(HudModule m) {
        switch (m.id) {
            case "fps": return "gauge";
            case "cps": return "mouse";
            case "ping": return "signal";
            case "keystrokes": return "keys";
            case "armor": return "armor";
            case "potions": return "flask";
            case "coords": return "compass";
            case "direction": return "compass";
            case "clock": return "clock";
            case "speed": return "run";
            case "memory": return "chip";
            case "server": return "servers";
            case "biome": return "tree";
            case "daytime": return "sun";
            case "combo": return "swords";
            case "reach": return "reach";
            case "session": return "hourglass";
            case "items": return "box";
            case "targethud": return "target";
            case "watermark": return "sparkle";
            case "screenshot": return "camera";
            case "warnings": return "bell";
            case "invsee": return "eye";
            default: return "layout";
        }
    }

    private void drawer(int H) {
        float w = 290 * d, x = 32 * d, pad = 18 * d, rowH = 36 * d;
        List<String> names = HudProfiles.names();
        String activeProfile = HudProfiles.active();
        // Profile als kleine Knöpfe (Zeilen umbrechen)
        float chipH = 30 * d, chipGap = 6 * d, inner = w - 2 * pad;
        List<float[]> chips = new ArrayList<float[]>();
        float lx = 0, ly = 0;
        for (int i = 0; i <= names.size(); i++) {
            String label = i < names.size() ? names.get(i) : "Neu";
            float cw = kit.buttonWidth(label, i < names.size() ? null : "plus", chipH);
            cw = Math.min(cw, inner);
            if (lx > 0 && lx + cw > inner) {
                lx = 0;
                ly += chipH + chipGap;
            }
            chips.add(new float[]{lx, ly, cw});
            lx += cw + chipGap;
        }
        float profilesH = 22 * d + ly + chipH + 14 * d;
        int n = MyticClient.HUD.size();
        float listH = n * rowH;
        float headH = 34 * d;
        float maxH = H - 32 * d - (32 * d + 58 * d + 24 * d);
        float h = drawerOpen ? Math.min(maxH, pad + headH + profilesH + listH + pad) : pad + headH + pad - 8 * d;
        float y = H - 32 * d - h;
        Draw.shadow(kit.g, x, y + 20 * d, w, h, 20 * d, 50 * d, -16 * d, 0x99000000);
        Draw.glass(kit.g, x, y, w, h, 20 * d, Tokens.GLASS_STRONG, Tokens.LINE);
        kit.block(x, y, w, h, null);
        chrome(x, y, w, h);
        float cx = x + pad, top = y + pad;
        kit.caption("Elemente", cx + 4 * d, top + 17 * d - 6 * d);
        kit.iconButton("more", x + w - pad - 34 * d - 40 * d, top, 34 * d, "plus", false, () -> Compat.setScreen(new ModMenuScreen(this)));
        if (kit.hover("more")) kit.tooltip("Weitere HUD-Mods im Mod-Menü", x + w - pad - 57 * d, top + 40 * d);
        kit.iconButton("fold", x + w - pad - 34 * d, top, 34 * d, drawerOpen ? "chevD" : "chevR", false, () -> drawerOpen = !drawerOpen);
        if (!drawerOpen) return;
        float py = top + headH + 4 * d;
        kit.caption("Profil", cx + 4 * d, py);
        py += 22 * d;
        for (int i = 0; i < chips.size(); i++) {
            float[] c = chips.get(i);
            final boolean add = i >= names.size();
            final String name = add ? null : names.get(i);
            boolean on = !add && name.equals(activeProfile);
            String id = "prof" + i;
            float bx = cx + c[0], byy = py + c[1];
            if (add) {
                kit.button(id, bx, byy, c[2], chipH, "Neu", "plus", Widgets.GHOST, () -> {
                    String created = HudProfiles.create();
                    if (created != null) Toasts.ok("Profil „" + created + "“ angelegt");
                });
                continue;
            }
            boolean chv = kit.hit(id, bx, byy, c[2], chipH, () -> {
                if (name.equals(HudProfiles.active())) {
                    renaming = name;
                    renameField.text = name;
                    kit.focus(renameField);
                } else {
                    HudProfiles.switchTo(name);
                    selected = popover = null;
                }
            }, () -> {
                if (names.size() > 1) confirmDelete = name;
                else Toasts.error("Das letzte Profil bleibt");
            });
            Widgets.button(kit.g, kit.key(id), bx, byy, c[2], chipH, d, Kit_ellipsize(name, c[2] - 20 * d), null, on ? Widgets.PRIMARY : Widgets.SECONDARY,
                    null, 13.5f * d, 9 * d, chv, kit.pressed(id));
            if (kit.hover(id)) kit.tooltip(on ? "Klick: umbenennen · Rechtsklick: löschen" : "Klick: wechseln · Rechtsklick: löschen", bx + c[2] / 2, byy + chipH + 6 * d);
        }
        py += ly + chipH + 14 * d;
        kit.hline(cx, py - 7 * d, inner);
        float viewH = y + h - pad - py;
        float off = kit.scroll(listScroll, cx - 6 * d, py, inner + 12 * d, viewH, listH);
        kit.clip(cx - 6 * d, py, inner + 12 * d, viewH);
        for (int i = 0; i < n; i++) {
            final HudModule m = MyticClient.HUD.get(i);
            float ry = py + i * rowH - off;
            if (ry + rowH < py || ry > py + viewH) continue;
            boolean sel = m == selected || m == popover;
            boolean hv = kit.hit("row:" + m.id, cx - 6 * d, ry, inner + 12 * d, rowH, () -> {
                if (m.enabled()) {
                    selected = m;
                    popover = m;
                } else {
                    m.setEnabled(true);
                    selected = m;
                    MyticClient.config().save();
                }
            });
            float hvT = Motion.to(kit.key("rowh:" + m.id), hv, Tokens.MS_HOVER), sT = Motion.to(kit.key("rows:" + m.id), sel, 200);
            if (hvT > 0.01f || sT > 0.01f) Draw.rect(kit.g, cx - 6 * d, ry, inner + 12 * d, rowH, 10 * d, Draw.mix(Draw.alpha(Tokens.SURFACE, hvT), 0x249B5CFF, sT));
            boolean on = m.enabled();
            int c = on ? Tokens.TEXT : Tokens.SUBTLE;
            Icons.draw(kit.g, icon(m), cx + 2 * d, ry + rowH / 2 - 8 * d, 16 * d, on ? Tokens.MUTED : Tokens.SUBTLE);
            kit.textMid(m.name, cx + 28 * d, ry + rowH / 2, 14.5f, Tokens.MEDIUM, c, inner - 28 * d - 40 * d);
            kit.iconButton("vis:" + m.id, cx + inner - 34 * d + 6 * d, ry + 1 * d, 34 * d, on ? "eye" : "eyeOff", false, () -> {
                m.toggle();
                MyticClient.config().save();
            });
        }
        kit.unclip();
        kit.scrollbar("list", listScroll, x + w - 7 * d, py, viewH);
    }

    private String Kit_ellipsize(String s, float max) {
        return de.myticlegacy.client.gui.ui.Type.ellipsize(kit.g, s, max, 13.5f * d, Tokens.SEMIBOLD);
    }

    // ------------------------------------------------------------------------------------------ Optionsfenster

    private void popover(int W, int H) {
        final HudModule m = popover;
        float w = 330 * d, pad = 18 * d;
        final boolean colors = true;
        float h = pad + 34 * d + 8 * d + 64 * d + 48 * d + 48 * d + (colors ? 78 * d : 0) + 12 * d + 38 * d + pad;
        float ex = m.x() * k, ey = m.y() * k, ew = m.width() * k;
        float x = ex + ew + 20 * d;
        if (x + w > W - 8 * d) x = ex - 20 * d - w;
        if (x < 8 * d) x = Math.max(8 * d, Math.min(W - w - 8 * d, ex));
        float y = Math.max(32 * d + 58 * d + 16 * d, Math.min(ey, H - h - 20 * d)); // unter der Werkzeugleiste
        Draw.shadow(kit.g, x, y + 24 * d, w, h, 20 * d, 60 * d, -16 * d, 0xB3000000);
        Draw.glass(kit.g, x, y, w, h, 20 * d, 0xE6141020, Tokens.LINE_STRONG);
        kit.block(x, y, w, h, null);
        chrome(x, y, w, h);
        float cx = x + pad, iw = w - 2 * pad, cy = y + pad;
        Icons.draw(kit.g, icon(m), cx, cy + 17 * d - 9 * d, 18 * d, Tokens.ACC);
        kit.textMid(m.name, cx + 28 * d, cy + 17 * d, 16, Tokens.SEMIBOLD, Tokens.TEXT, iw - 28 * d - 40 * d);
        kit.iconButton("pop:x", x + w - pad - 34 * d, cy, 34 * d, "x", false, () -> popover = null);
        cy += 34 * d + 8 * d;
        // Größe
        kit.textMid("Größe", cx, cy + 10 * d, 15, Tokens.SEMIBOLD, Tokens.TEXT, 0);
        String val = Math.round(m.scaleFactor() * 100) + " %";
        kit.textRight(val, cx + iw, cy + 10 * d, 14, Tokens.MEDIUM, Tokens.MUTED);
        kit.slider("pop:size", cx + 9 * d, cy + 28 * d, iw - 18 * d, (float) m.scale.fraction(), v -> m.scale.setFraction(v), () -> MyticClient.config().save());
        cy += 64 * d;
        kit.hline(cx, cy - 6 * d, iw);
        kit.textMid("Hintergrund", cx, cy + 18 * d, 15, Tokens.SEMIBOLD, Tokens.TEXT, 0);
        kit.toggle("pop:bg", cx + iw - 46 * d, cy + 5 * d, m.background.get(), () -> {
            m.background.set(!m.background.get());
            MyticClient.config().save();
        });
        cy += 48 * d;
        kit.hline(cx, cy - 6 * d, iw);
        kit.textMid("Sichtbar", cx, cy + 18 * d, 15, Tokens.SEMIBOLD, Tokens.TEXT, 0);
        kit.toggle("pop:vis", cx + iw - 46 * d, cy + 5 * d, m.enabled(), () -> {
            m.toggle();
            MyticClient.config().save();
        });
        cy += 48 * d;
        if (colors) {
            kit.hline(cx, cy - 6 * d, iw);
            kit.textMid("Textfarbe", cx, cy + 12 * d, 15, Tokens.SEMIBOLD, Tokens.TEXT, 0);
            float sw = 28 * d, sg = (iw - SWATCHES.length * sw) / (SWATCHES.length - 1);
            int cur = m.textColor.get() | 0xFF000000;
            for (int i = 0; i < SWATCHES.length; i++) {
                final int col = SWATCHES[i];
                float sx = cx + i * (sw + sg), sy = cy + 32 * d;
                boolean hv = kit.hit("pop:c" + i, sx, sy, sw, sw, () -> {
                    m.textColor.set(col);
                    MyticClient.config().save();
                });
                boolean sel = cur == col;
                if (sel || hv) Draw.ring(kit.g, sx - 3 * d, sy - 3 * d, sw + 6 * d, sw + 6 * d, 9 * d, Math.max(1, Math.round(2 * d * k / k)), sel ? 0xFFFFFFFF : Tokens.LINE_STRONG);
                Draw.rect(kit.g, sx, sy, sw, sw, 7 * d, col);
            }
            cy += 78 * d;
        }
        cy += 12 * d;
        kit.button("pop:all", cx, cy, iw, 38 * d, "Alle Einstellungen", "gear", Widgets.SECONDARY, () -> Compat.setScreen(new ModMenuScreen(this, m)));
    }

    // ------------------------------------------------------------------------------------------ Dialoge

    private void confirmReset(int W, int H) {
        float w = 520 * d, h = 236 * d, x = (W - w) / 2f, y = (H - h) / 2f;
        kit.modal(x, y, w, h, () -> confirmReset = false);
        kit.text("HUD zurücksetzen?", x + 28 * d, y + 30 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.paragraph("Alle Anzeigen kehren an ihre Standardposition zurück. Größe und Einstellungen bleiben.", x + 28 * d, y + 72 * d, w - 56 * d,
                15, Tokens.MEDIUM, Tokens.MUTED, 22, 3);
        float bh = 44 * d, by = y + h - 28 * d - bh;
        float ew = kit.buttonWidth("Zurücksetzen", "rotL", bh);
        kit.button("rs:ok", x + w - 28 * d - ew, by, ew, bh, "Zurücksetzen", "rotL", Widgets.DANGER, () -> {
            confirmReset = false;
            for (HudModule m : MyticClient.HUD) m.resetPosition();
            MyticClient.config().save();
            Toasts.ok("HUD zurückgesetzt");
        });
        float cw = kit.buttonWidth("Abbrechen", null, bh);
        kit.button("rs:no", x + w - 28 * d - ew - 10 * d - cw, by, cw, bh, "Abbrechen", null, Widgets.GHOST, () -> confirmReset = false);
    }

    private void renameDialog(int W, int H) {
        float w = 520 * d, h = 250 * d, x = (W - w) / 2f, y = (H - h) / 2f;
        kit.modal(x, y, w, h, () -> renaming = null);
        kit.text("Profil umbenennen", x + 28 * d, y + 30 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        if (kit.focused() != renameField) kit.focus(renameField);
        kit.field("rn:f", renameField, x + 28 * d, y + 74 * d, w - 56 * d, 46 * d, "Name des Profils", "pencil");
        float bh = 44 * d, by = y + h - 28 * d - bh;
        float ew = kit.buttonWidth("Speichern", "check", bh);
        kit.button("rn:ok", x + w - 28 * d - ew, by, ew, bh, "Speichern", "check", Widgets.PRIMARY, renameField.isEmpty() ? null : this::finishRename);
        float cw = kit.buttonWidth("Abbrechen", null, bh);
        kit.button("rn:no", x + w - 28 * d - ew - 10 * d - cw, by, cw, bh, "Abbrechen", null, Widgets.GHOST, () -> {
            renaming = null;
            kit.focus(null);
        });
    }

    private void finishRename() {
        if (renaming == null) return;
        String to = renameField.text.trim();
        if (!to.isEmpty() && !to.equals(renaming)) {
            if (HudProfiles.rename(renaming, to)) Toasts.ok("Profil umbenannt");
            else Toasts.error("Name schon vergeben");
        }
        renaming = null;
        kit.focus(null);
    }

    private void deleteDialog(int W, int H) {
        final String name = confirmDelete;
        float w = 520 * d, h = 236 * d, x = (W - w) / 2f, y = (H - h) / 2f;
        kit.modal(x, y, w, h, () -> confirmDelete = null);
        kit.text("Profil löschen?", x + 28 * d, y + 30 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.paragraph("Das HUD-Profil „" + name + "“ mit allen Positionen wird gelöscht.", x + 28 * d, y + 72 * d, w - 56 * d,
                15, Tokens.MEDIUM, Tokens.MUTED, 22, 3);
        float bh = 44 * d, by = y + h - 28 * d - bh;
        float ew = kit.buttonWidth("Löschen", "trash", bh);
        kit.button("dl:ok", x + w - 28 * d - ew, by, ew, bh, "Löschen", "trash", Widgets.DANGER, () -> {
            confirmDelete = null;
            if (HudProfiles.delete(name)) Toasts.ok("Profil gelöscht");
            selected = popover = null;
        });
        float cw = kit.buttonWidth("Abbrechen", null, bh);
        kit.button("dl:no", x + w - 28 * d - ew - 10 * d - cw, by, cw, bh, "Abbrechen", null, Widgets.GHOST, () -> confirmDelete = null);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean onClick(Input.Click event) {
        double mx = event.x(), my = event.y();
        boolean ui = onChrome(mx, my);
        if (kit.mouseDown(mx * k, my * k, event.button()) || ui) return true;
        HudModule module = at(mx, my);
        if (module == null) {
            selected = null;
            popover = null;
            return false;
        }
        selected = module;
        if (event.button() == 1) {
            popover = module;
            return true;
        }
        if (event.button() != 0) return false;
        if (popover != module) popover = null;
        dragging = module;
        offsetX = mx - module.x();
        offsetY = my - module.y();
        return true;
    }

    @Override
    protected boolean onDrag(Input.Click event, double dx, double dy) {
        if (kit.mouseDrag(event.x() * k, event.y() * k)) return true;
        if (dragging == null) return false;
        int w = dragging.width();
        int h = dragging.height();
        int x = (int) Math.round(event.x() - offsetX);
        int y = (int) Math.round(event.y() - offsetY);
        guides.clear();
        if (snapping) {
            // Kandidaten: Bildschirmränder und -mitte, Kanten und Mitten der anderen Anzeigen
            List<Integer> xs = new ArrayList<Integer>(java.util.Arrays.asList(0, width / 2, width));
            List<Integer> ys = new ArrayList<Integer>(java.util.Arrays.asList(0, height / 2, height));
            for (HudModule other : active()) {
                if (other == dragging) continue;
                xs.add(other.x());
                xs.add(other.x() + other.width());
                xs.add(other.x() + other.width() / 2);
                ys.add(other.y());
                ys.add(other.y() + other.height());
                ys.add(other.y() + other.height() / 2);
            }
            x = snap(x, w, xs, 0);
            y = snap(y, h, ys, 1);
        }
        dragging.moveTo(Math.max(0, Math.min(width - w, x)), Math.max(0, Math.min(height - h, y)));
        return true;
    }

    /** Rastet linke Kante, Mitte oder rechte Kante an der nächsten Linie ein. */
    private int snap(int pos, int size, List<Integer> lines, int axis) {
        int best = SNAP + 1;
        int result = pos;
        int guide = -1;
        for (int line : lines) {
            int[] offsets = {0, size / 2, size};
            for (int offset : offsets) {
                int dd = Math.abs(pos + offset - line);
                if (dd < best) {
                    best = dd;
                    result = line - offset;
                    guide = line;
                }
            }
        }
        if (guide >= 0) guides.add(new int[]{axis, Math.min(guide, (axis == 0 ? width : height) - 1)});
        return result;
    }

    @Override
    protected boolean onRelease(Input.Click event) {
        if (kit.mouseUp(event.x() * k, event.y() * k, event.button())) return true;
        if (dragging != null) {
            dragging = null;
            guides.clear();
            MyticClient.config().save();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double vertical) {
        if (kit.wheel(mouseX * k, mouseY * k, vertical)) return true;
        if (onChrome(mouseX, mouseY)) return true;
        HudModule module = at(mouseX, mouseY);
        if (module == null) return false;
        module.scale.set(module.scale.get() + (vertical > 0 ? 0.05 : -0.05));
        MyticClient.config().save();
        return true;
    }

    @Override
    protected boolean onChar(Input.Typed event) {
        return kit.typed(event.codepointAsString()) || renaming != null;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        int key = event.key();
        int kk = key == InputConstants.KEY_BACKSPACE ? (event.hasControlDown() ? Kit.KEY_DELETE_WORD : Kit.KEY_BACKSPACE)
                : key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER ? Kit.KEY_ENTER : 0;
        if (kk != 0 && kit.key(kk)) return true;
        if (key == InputConstants.KEY_ESCAPE) {
            if (renaming != null) {
                renaming = null;
                kit.focus(null);
            } else if (confirmReset) confirmReset = false;
            else if (confirmDelete != null) confirmDelete = null;
            else if (popover != null) popover = null;
            else onClose();
            return true;
        }
        if (renaming != null) return true;
        if (key == InputConstants.KEY_RSHIFT) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        kit.forget();
        super.removed();
    }

    @Override
    public void onClose() {
        HudProfiles.saveActive(); // Verschiebungen gehören zum aktiven Profil
        MyticClient.config().save();
        Compat.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
