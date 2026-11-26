package de.myticlegacy.client.invsee;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.UiFont;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;

/**
 * Zeichnet die InvSee-Übersicht im Mytic-Design: Glas-Panel mit Spielerkopf und Name, darunter Zeilen mit
 * Item-Symbol, Name und Anzahl; Rüstung mit Schutzart und farbigem Haltbarkeitsbalken. Alle Maße in GUI-Einheiten
 * bei (0, 0) im aktuellen Zeichenraum; k = echte Pixel je Einheit (für Textbreiten ohne Zeichenfläche).
 */
public final class InvSeePanel {
    public static final float PAD = 6, HEAD = 19, ROW = 18, SEP_H = 22;
    static final float FS = 7.5f, FS_HEAD = 8f, FS_SMALL = 6.5f;

    private InvSeePanel() {
    }

    /** Textbreite in Einheiten bei k echten Pixeln je Einheit */
    static float tw(String s, float size, int weight, float k) {
        return UiFont.width(s, size * k, weight) / k;
    }

    static float nameWidth(InvSeeData.Row r, float k) {
        float w = 0;
        for (InvSeeData.Part p : r.name()) w += tw(p.text(), FS, Tokens.MEDIUM, k);
        return w;
    }

    private static PlayerInfo info(String name) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null || name == null) return null;
        try {
            return conn.getPlayerInfo(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------ Liste

    public static float width(String title, String right, List<InvSeeData.Row> rows, boolean names, float k) {
        float value = 0, name = 0;
        for (InvSeeData.Row r : rows) {
            value = Math.max(value, tw(r.value(), FS, Tokens.SEMIBOLD, k));
            if (r.showsName(names)) name = Math.max(name, nameWidth(r, k));
        }
        float body = 16 + 6 + (name > 0 ? name + 10 : 0) + value;
        float head = 13 + 5 + tw(title, FS_HEAD, Tokens.SEMIBOLD, k) + (right != null ? tw(right, FS_SMALL, Tokens.MEDIUM, k) + 10 : 0);
        return (float) Math.ceil(Math.max(Math.max(body, head), 70) + 2 * PAD);
    }

    public static float height(List<InvSeeData.Row> rows) {
        return PAD + HEAD + 3 + Math.max(1, rows.size()) * ROW + PAD - 2;
    }

    static int fill(int bgPercent) {
        return Draw.alpha(0xF00F0C18, Math.max(0.05f, bgPercent / 100f));
    }

    public static void drawList(GuiGraphics g, String title, String right, List<InvSeeData.Row> rows, float w, boolean names, int bgPercent) {
        float h = height(rows);
        float r = 8;
        if (bgPercent > 0) Draw.shadow(g, 0, 4, w, h, r, 14, -4, Draw.alpha(0x80000000, bgPercent / 100f));
        Draw.glass(g, 0, 0, w, h, r, fill(bgPercent), Tokens.LINE_STRONG);
        // Kopf: Spielerkopf (aus der Tab-Liste) bzw. Akzent-Punkt, Name, rechts Zusatz
        float hx = PAD, hy = PAD;
        PlayerInfo info = info(title);
        if (info != null) {
            Draw.rect(g, hx - 0.5f, hy - 0.5f, 13, 13, 3.5f, Tokens.LINE_STRONG);
            Compat.drawFace(g, info, Math.round(hx), Math.round(hy), 12);
        } else {
            Draw.accentRect(g, hx, hy, 12, 12, 3.5f, 1f);
            String c = title == null || title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            Type.centered(g, c, hx + 6, hy + 6 - Type.capHeight(g, 7) / 2f, 7, Tokens.SEMIBOLD, 0xFFFFFFFF);
        }
        Type.middle(g, title, hx + 13 + 5, hy + 6, FS_HEAD, Tokens.SEMIBOLD, Tokens.TEXT);
        if (right != null) Type.right(g, right, w - PAD, hy + 6 - Type.capHeight(g, FS_SMALL) / 2f, FS_SMALL, Tokens.MEDIUM, Tokens.SUBTLE);
        float ly = PAD + HEAD - 2;
        Draw.rect(g, PAD - 2, ly, w - 2 * PAD + 4, Math.max(1f / Draw.k(g), 0.5f), 0, Tokens.LINE);
        float ry = ly + 3;
        if (rows.isEmpty()) Type.middle(g, "Keine gewählten Items vorhanden", PAD, ry + ROW / 2, FS, Tokens.MEDIUM, Tokens.SUBTLE);
        for (InvSeeData.Row row : rows) {
            drawRow(g, row, PAD, ry, w - 2 * PAD, names);
            ry += ROW;
        }
    }

    /** Eine Zeile (x, y = oben links, w = Breite) */
    static void drawRow(GuiGraphics g, InvSeeData.Row row, float x, float y, float w, boolean names) {
        float cy = y + ROW / 2;
        boolean zero = row.fraction() < 0 && row.valueColor() == Tokens.BAD;
        if (zero) Draw.rect(g, x - 2, y + 1, w + 4, ROW - 2, 4, 0x14FF5C72);
        de.myticlegacy.client.compat.Gfx.push(g);
        de.myticlegacy.client.compat.Gfx.translate(g, x, cy - 8);
        g.renderItem(row.icon(), 0, 0);
        de.myticlegacy.client.compat.Gfx.pop(g);
        float vx = x + w;
        Type.right(g, row.value(), vx, cy - Type.capHeight(g, FS) / 2f - (row.fraction() >= 0 ? 1.5f : 0), FS, Tokens.SEMIBOLD, row.valueColor());
        float tx = x + 16 + 6;
        if (row.showsName(names)) {
            float px = tx;
            for (InvSeeData.Part p : row.name()) {
                Type.draw(g, p.text(), px, cy - Type.capHeight(g, FS) / 2f - (row.fraction() >= 0 ? 1.5f : 0), FS, Tokens.MEDIUM, p.color());
                px += Type.width(g, p.text(), FS, Tokens.MEDIUM);
            }
        }
        if (row.fraction() >= 0) {
            // Haltbarkeitsbalken unter Name und Wert
            float bx = tx, bw = x + w - tx, by = cy + 4.5f, bh = 2;
            Draw.rect(g, bx, by, bw, bh, 1, 0x26FFFFFF);
            Draw.rect(g, bx, by, Math.max(bh, bw * Math.max(0, Math.min(1, row.fraction()))), bh, 1, row.valueColor());
        }
    }

    // ------------------------------------------------------------------------------------------ Einzeln

    public static float separateWidth(InvSeeData.Row row, boolean names, float k) {
        return (float) Math.ceil(PAD + 16 + 6 + (row.showsName(names) ? nameWidth(row, k) + 10 : 0) + tw(row.value(), FS, Tokens.SEMIBOLD, k) + PAD);
    }

    public static void drawSeparate(GuiGraphics g, InvSeeData.Row row, float w, boolean names, int bgPercent) {
        Draw.glass(g, 0, 0, w, SEP_H, 7, fill(bgPercent), Tokens.LINE_STRONG);
        drawRow(g, row, PAD, (SEP_H - ROW) / 2, w - 2 * PAD, names);
    }
}
