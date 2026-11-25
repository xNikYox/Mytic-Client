package de.myticlegacy.client.minimap;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.DesignScreen;
import de.myticlegacy.client.gui.ui.ColorField;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Toasts;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Wegpunkte im Mytic-Design: links die Liste (diese Dimension oder alle) mit Sichtbarkeit und Entfernung, rechts
 * Bearbeiten (Name, Koordinaten, Farbe, Symbol). Neu an der eigenen Position, Code kopieren zum Teilen,
 * Code aus der Zwischenablage oder dem Chat übernehmen.
 */
public class WaypointsScreen extends DesignScreen {
    private static final float WIN_W = 1280, WIN_H = 780, HEAD = 88, LIST_W = 640, ROW_H = 66;
    private int filter; // 0 diese Dimension, 1 alle
    private Waypoints.Waypoint selected;
    private final Kit.Field name = new Kit.Field(32), fx = new Kit.Field(9), fy = new Kit.Field(5), fz = new Kit.Field(9);
    private final ColorField colorField = new ColorField();
    private int editColor;
    private String editIcon = "diamond";
    private boolean editVisible = true;
    private final Kit.Scroll scroll = new Kit.Scroll();
    private boolean confirmDelete;

    public WaypointsScreen() {
        super("waypoints", "Wegpunkte");
        for (Kit.Field f : new Kit.Field[]{fx, fy, fz}) f.allowed = "-0123456789";
        name.onEnter = this::save;
    }

    /** Neuer Wegpunkt an der eigenen Position, gleich zum Benennen geöffnet */
    public static WaypointsScreen createHere() {
        WaypointsScreen s = new WaypointsScreen();
        Waypoints.Waypoint w = here();
        if (w != null) {
            Waypoints.add(w);
            s.select(w);
            s.kit.focus(s.name);
            Toasts.ok("Wegpunkt gesetzt");
        }
        return s;
    }

    private static Waypoints.Waypoint here() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        MinimapModule m = MinimapModule.get();
        if (p == null || m == null || m.dimension() == null) return null;
        Waypoints.Waypoint w = new Waypoints.Waypoint();
        int n = 1;
        for (Waypoints.Waypoint o : Waypoints.all()) if (!o.death) n++;
        w.name = "Wegpunkt " + n;
        w.x = p.getBlockX();
        w.y = p.getBlockY();
        w.z = p.getBlockZ();
        w.dim = m.dimension();
        w.color = Waypoints.nextColor();
        return w;
    }

    /** Mit diesem Wegpunkt zum Bearbeiten öffnen (z. B. von der Weltkarte) */
    public void open(Waypoints.Waypoint w) {
        MinimapModule m = MinimapModule.get();
        if (m != null && !w.dim.equals(m.dimension())) filter = 1;
        select(w);
    }

    private void select(Waypoints.Waypoint w) {
        selected = w;
        confirmDelete = false;
        if (w == null) return;
        name.text = w.name;
        fx.text = String.valueOf(w.x);
        fy.text = String.valueOf(w.y);
        fz.text = String.valueOf(w.z);
        editColor = w.color;
        editIcon = w.icon;
        editVisible = w.visible;
    }

    private List<Waypoints.Waypoint> visible() {
        MinimapModule m = MinimapModule.get();
        String dim = m == null ? null : m.dimension();
        List<Waypoints.Waypoint> out = new ArrayList<>();
        for (Waypoints.Waypoint w : Waypoints.all()) if (filter == 1 || w.dim.equals(dim)) out.add(w);
        return out;
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save() {
        if (selected == null) return;
        selected.name = name.text.isBlank() ? "Wegpunkt" : name.text.trim();
        selected.x = Math.max(-30_000_000, Math.min(30_000_000, parse(fx.text, selected.x)));
        selected.y = Math.max(-2048, Math.min(4096, parse(fy.text, selected.y)));
        selected.z = Math.max(-30_000_000, Math.min(30_000_000, parse(fz.text, selected.z)));
        selected.color = editColor | 0xFF000000;
        selected.icon = editIcon;
        selected.visible = editVisible;
        if (!Waypoints.all().contains(selected)) Waypoints.add(selected);
        else Waypoints.save();
        kit.focus(null);
        Toasts.ok("Gespeichert");
    }

    private void importCode(String text, String source) {
        Waypoints.Waypoint w = Waypoints.parse(text);
        if (w == null) {
            Toasts.error("Kein Wegpunkt-Code " + source);
            return;
        }
        Waypoints.add(w);
        if (MinimapModule.get() != null && !w.dim.equals(MinimapModule.get().dimension())) filter = 1;
        select(w);
        Toasts.ok("„" + w.name + "“ übernommen");
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void frame(GuiGraphics g) {
        float[] r = center(WIN_W, WIN_H);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        kit.window(x, y, w, h, minecraft.level == null);
        float p = 28 * d;
        // Kopf
        float is = 44 * d, iy = y + (HEAD * d - is) / 2;
        Draw.gradient(kit.g, x + p, iy, is, is, 12 * d, 0x739B5CFF, 0x59EC5FBF);
        MapIcons.draw(kit.g, "diamond", x + p + is / 2, iy + is / 2, 24 * d, 0xFFFFFFFF);
        MinimapModule m = MinimapModule.get();
        String dim = m == null || m.dimension() == null ? "" : MapWorld.dimensionLabel(m.dimension());
        kit.textMid("Wegpunkte", x + p + is + 16 * d, y + 34 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT, 0);
        kit.textMid(Waypoints.all().size() + " gespeichert · " + dim, x + p + is + 16 * d, y + 58 * d, 13.5f, Tokens.MEDIUM, Tokens.SUBTLE, 0);
        kit.iconButton("close", x + w - p - 40 * d, y + 24 * d, 40 * d, "x", true, this::onClose);
        float mw = kit.buttonWidth("Weltkarte", "globe", 40 * d);
        kit.button("map", x + w - p - 40 * d - 12 * d - mw, y + 24 * d, mw, 40 * d, "Weltkarte", "globe", Widgets.SECONDARY,
                () -> Compat.setScreen(new WorldMapScreen()));
        kit.hline(x, y + HEAD * d, w);
        drawList(x, y + HEAD * d, LIST_W * d, h - HEAD * d);
        kit.vline(x + LIST_W * d, y + HEAD * d, h - HEAD * d);
        drawEditor(x + LIST_W * d, y + HEAD * d, w - LIST_W * d, h - HEAD * d);
    }

    private void drawList(float x, float y, float w, float h) {
        float p = 28 * d;
        float sy = y + 20 * d;
        kit.seg("filter", x + p, sy, new String[]{"Diese Dimension", "Alle"}, null, null, filter, i -> {
            filter = i;
            scroll.reset();
        });
        // Werkzeuge
        float bh = 40 * d, by = sy + 56 * d;
        float bx = x + p;
        bx += kit.button("here", bx, by, bh, "Hier setzen", "plus", Widgets.PRIMARY, () -> {
            Waypoints.Waypoint wp = here();
            if (wp != null) {
                Waypoints.add(wp);
                filter = 0;
                select(wp);
                kit.focus(name);
            }
        }) + 10 * d;
        bx += kit.button("paste", bx, by, bh, "Code einfügen", "link", Widgets.SECONDARY,
                () -> importCode(minecraft.keyboardHandler.getClipboard(), "in der Zwischenablage")) + 10 * d;
        if (MinimapModule.lastChatCode != null) {
            kit.button("chat", bx, by, bh, "Aus Chat übernehmen", "chat", Widgets.GHOST, () -> {
                importCode(MinimapModule.lastChatCode, "im Chat");
                MinimapModule.lastChatCode = null;
            });
        }
        // Liste
        List<Waypoints.Waypoint> list = visible();
        float ly = by + bh + 18 * d, lh = y + h - ly - 12 * d;
        float content = list.size() * (ROW_H + 8) * d + 8 * d;
        float off = kit.scroll(scroll, x, ly, w, lh, content);
        kit.clip(x, ly, w, lh);
        if (list.isEmpty()) {
            kit.textCenter("Noch keine Wegpunkte", x + w / 2, ly + 60 * d, 15, Tokens.MEDIUM, Tokens.MUTED);
            kit.textCenter("Taste N setzt einen Wegpunkt an deiner Position", x + w / 2, ly + 88 * d, 13.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        }
        LocalPlayer pl = minecraft.player;
        float ry = ly - off;
        for (final Waypoints.Waypoint wp : list) {
            float rh = ROW_H * d;
            if (ry + rh >= ly && ry <= ly + lh) row(wp, x + p, ry, w - 2 * p, rh, pl);
            ry += rh + 8 * d;
        }
        kit.unclip();
        kit.scrollbar("list", scroll, x + w - 10 * d, ly + 4 * d, lh - 8 * d);
    }

    private void row(final Waypoints.Waypoint wp, float x, float y, float w, float h, LocalPlayer pl) {
        String id = "wp:" + System.identityHashCode(wp);
        boolean sel = wp == selected;
        boolean hv = kit.hit(id, x, y, w, h, () -> select(wp));
        Draw.glass(kit.g, x, y, w, h, 14 * d, sel ? 0x1A9B5CFF : hv ? Tokens.SURFACE_HOVER : Tokens.SURFACE, sel ? Tokens.ACC_LINE : Tokens.LINE);
        float is = 40 * d;
        Draw.rect(kit.g, x + 13 * d, y + (h - is) / 2, is, is, 11 * d, Draw.alpha(wp.color, 0.18f));
        MapIcons.draw(kit.g, wp.icon, x + 13 * d + is / 2, y + h / 2, 22 * d, wp.visible ? wp.color : Draw.alpha(wp.color, 0.4f));
        float tx = x + 13 * d + is + 14 * d;
        float right = x + w - 14 * d;
        // Sichtbarkeit
        float eyeX = right - 34 * d;
        boolean eh = kit.iconButton(id + ":eye", eyeX, y + h / 2 - 17 * d, 34 * d, wp.visible ? "eye" : "eyeOff", false, () -> {
            wp.visible = !wp.visible;
            if (wp == selected) editVisible = wp.visible;
            Waypoints.save();
        });
        if (eh) kit.tooltip(wp.visible ? "Ausblenden" : "Einblenden", eyeX + 17 * d, y + h / 2 + 22 * d);
        String dist = "";
        MinimapModule m = MinimapModule.get();
        if (pl != null && m != null && wp.dim.equals(m.dimension())) {
            double dd = Math.sqrt(Math.pow(wp.x + 0.5 - pl.getX(), 2) + Math.pow(wp.y - pl.getY(), 2) + Math.pow(wp.z + 0.5 - pl.getZ(), 2));
            dist = dd >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", dd / 1000) : Math.round(dd) + " m";
        }
        float dw = dist.isEmpty() ? 0 : kit.width(dist, 14, Tokens.SEMIBOLD) + 16 * d;
        if (!dist.isEmpty()) kit.textRight(dist, eyeX - 12 * d, y + h / 2, 14, Tokens.SEMIBOLD, Tokens.MUTED);
        float tw = eyeX - 12 * d - dw - tx;
        kit.textMid(wp.name, tx, y + h / 2 - 10 * d, 15.5f, Tokens.SEMIBOLD, wp.visible ? Tokens.TEXT : Tokens.MUTED, tw);
        String sub = wp.x + " / " + wp.y + " / " + wp.z + (filter == 1 ? " · " + MapWorld.dimensionLabel(wp.dim) : "") + (wp.death ? " · Todespunkt" : "");
        kit.textMid(sub, tx, y + h / 2 + 11 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE, tw);
    }

    private void drawEditor(float x, float y, float w, float h) {
        float p = 32 * d, cx = x + p, cw = w - 2 * p;
        if (selected == null) {
            float cy = y + h / 2 - 40 * d;
            MapIcons.draw(kit.g, "diamond", x + w / 2, cy, 34 * d, Draw.alpha(Tokens.ACC, 0.6f));
            kit.textCenter("Wegpunkt wählen oder neu setzen", x + w / 2, cy + 44 * d, 15, Tokens.MEDIUM, Tokens.MUTED);
            kit.textCenter("Rechtsklick auf der Weltkarte setzt ebenfalls einen", x + w / 2, cy + 70 * d, 13.5f, Tokens.MEDIUM, Tokens.SUBTLE);
            return;
        }
        float cy = y + 24 * d;
        kit.caption("Name", cx, cy);
        kit.field("name", name, cx, cy + 18 * d, cw, 46 * d, "Name des Wegpunkts", "pencil");
        cy += 18 * d + 46 * d + 22 * d;
        kit.caption("Koordinaten", cx, cy);
        float fw = (cw - 2 * 10 * d - 120 * d) / 3;
        String[] labels = {"X", "Y", "Z"};
        Kit.Field[] fields = {fx, fy, fz};
        for (int i = 0; i < 3; i++) {
            float fxp = cx + i * (fw + 10 * d);
            kit.field("c" + i, fields[i], fxp, cy + 18 * d, fw, 40 * d, labels[i], null);
        }
        kit.button("pos", cx + cw - 110 * d, cy + 18 * d, 110 * d, 40 * d, "Hier", "compass", Widgets.SECONDARY, () -> {
            LocalPlayer pl = minecraft.player;
            if (pl == null) return;
            fx.text = String.valueOf(pl.getBlockX());
            fy.text = String.valueOf(pl.getBlockY());
            fz.text = String.valueOf(pl.getBlockZ());
            MinimapModule m = MinimapModule.get();
            if (m != null && m.dimension() != null) selected.dim = m.dimension();
        });
        cy += 18 * d + 40 * d + 22 * d;
        kit.caption("Farbe", cx, cy);
        colorField.draw(kit, "color", cx, cy + 18 * d, cw, editColor, Waypoints.COLORS, v -> editColor = v, () -> {
        });
        cy += 18 * d + ColorField.HEIGHT * d + 20 * d;
        kit.caption("Symbol", cx, cy);
        float s = 44 * d, gap = 8 * d;
        for (int i = 0; i < MapIcons.KEYS.length; i++) {
            final String key = MapIcons.KEYS[i];
            float sx = cx + i * (s + gap), sy2 = cy + 18 * d;
            if (sx + s > cx + cw) break;
            boolean on = key.equals(editIcon);
            boolean hv = kit.hit("ic:" + key, sx, sy2, s, s, () -> editIcon = key);
            Draw.glass(kit.g, sx, sy2, s, s, 12 * d, on ? 0x269B5CFF : hv ? Tokens.SURFACE_HOVER : Tokens.SURFACE, on ? Tokens.ACC_LINE : Tokens.LINE);
            MapIcons.draw(kit.g, key, sx + s / 2, sy2 + s / 2, 22 * d, on ? editColor | 0xFF000000 : Tokens.MUTED, on ? 0xD0000000 : 0);
            if (hv) kit.tooltip(MapIcons.LABELS[i], sx + s / 2, sy2 + s + 6 * d);
        }
        cy += 18 * d + s + 22 * d;
        kit.textMid("In der Welt und auf der Karte zeigen", cx, cy + 13 * d, 15, Tokens.MEDIUM, Tokens.TEXT, cw - 60 * d);
        kit.toggle("vis", cx + cw - 46 * d, cy, editVisible, () -> editVisible = !editVisible);
        kit.textMid(MapWorld.dimensionLabel(selected.dim) + (selected.death ? " · automatisch beim Tod gesetzt" : ""), cx, cy + 40 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE, cw);
        // Fuß
        float fy0 = y + h - 76 * d;
        kit.hline(x, fy0, w);
        float bh = 40 * d, by = fy0 + (76 * d - bh) / 2;
        float bx = cx;
        bx += kit.button("save", bx, by, bh, "Speichern", "check", Widgets.PRIMARY, this::save) + 10 * d;
        bx += kit.button("share", bx, by, bh, "Code kopieren", "link", Widgets.SECONDARY, () -> {
            save();
            minecraft.keyboardHandler.setClipboard(Waypoints.code(selected));
            Toasts.ok("Code kopiert – im Chat einfügen und an Freunde schicken");
        }) + 10 * d;
        if (kit.hover("share")) kit.tooltip("Freunde fügen ihn unter Wegpunkte → Code einfügen ein", bx - 80 * d, by - 36 * d);
        String del = confirmDelete ? "Wirklich löschen?" : "Löschen";
        float dw = kit.buttonWidth(del, "trash", bh);
        kit.button("delete", cx + cw - dw, by, dw, bh, del, "trash", Widgets.DANGER, () -> {
            if (!confirmDelete) {
                confirmDelete = true;
                return;
            }
            Waypoints.remove(selected);
            select(null);
            Toasts.ok("Wegpunkt gelöscht");
        });
    }

    @Override
    protected void back() {
        if (kit.typing()) kit.focus(null);
        else if (selected != null) select(null);
        else onClose();
    }

    @Override
    public void onClose() {
        Compat.setScreen(null);
    }
}
