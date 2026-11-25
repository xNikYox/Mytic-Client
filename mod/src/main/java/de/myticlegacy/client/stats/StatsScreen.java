package de.myticlegacy.client.stats;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.DesignScreen;
import de.myticlegacy.client.gui.HubScreen;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Motion;
import de.myticlegacy.client.gui.ui.Tokens;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Statistiken im neuen Design (Rechts-Shift → Statistiken): Kennzahlen-Kacheln, Verlauf der letzten 14 Tage als
 * Balken (heute im Akzent-Verlauf, Tooltip beim Überfahren), meistgespielte Server, Versionen und Profile mit
 * Anteils-Balken.
 */
public class StatsScreen extends DesignScreen {
    private static final float W = 1600, H = 880, HEAD = 88;

    public StatsScreen() {
        super("stats", "Statistiken");
        Stats.refresh(true);
    }

    @Override
    protected void back() {
        Compat.setScreen(new HubScreen());
    }

    @Override
    public void onClose() {
        Compat.setScreen(new HubScreen());
    }

    @Override
    protected void frame(GuiGraphics g) {
        float[] r = center(W, H);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        kit.window(x, y, w, h, minecraft.level == null);
        Stats.Data data = Stats.data();

        // Kopf
        kit.iconButton("back", x + 28 * d, y + 24 * d, 40 * d, "chevL", true, this::back);
        kit.text("Statistiken", x + 84 * d, y + 26 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        String sub = !Friends.available() ? "Nur über den Mytic Launcher" : Stats.loading() ? "Wird aktualisiert …"
                : data == null ? "Lädt …" : "Gezählt wird die Zeit in einer Welt";
        kit.text(sub, x + 84 * d, y + 54 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        if (Friends.available()) {
            kit.iconButton("reload", x + w - 32 * d - 96 * d, y + 24 * d, 40 * d, "refresh", true, Stats.loading() ? null : () -> Stats.refresh(true));
            if (kit.hover("reload")) kit.tooltip("Aktualisieren", x + w - 32 * d - 76 * d, y + 70 * d);
        }
        kit.iconButton("close", x + w - 32 * d - 40 * d, y + 24 * d, 40 * d, "x", true, () -> Compat.setScreen(null));
        kit.hline(x, y + HEAD * d, w);

        float bx = x + 32 * d, by = y + HEAD * d + 28 * d, bw = w - 64 * d, bh = y + h - 32 * d - by;
        if (!Friends.available()) {
            empty(x, y + HEAD * d, w, h - HEAD * d, "chart", "Statistiken gibt es nur mit der Freundesliste",
                    "Starte Minecraft über den Mytic Launcher, dann wird deine Spielzeit gezählt.");
            return;
        }
        if (data == null) {
            String err = Stats.error();
            empty(x, y + HEAD * d, w, h - HEAD * d, err != null ? "info" : "refresh", err != null ? "Nicht geladen" : "Lädt …",
                    err != null ? err : "Deine Spielzeit kommt vom Mytic-Server.");
            return;
        }

        // Kennzahlen
        String[][] tiles = {
                {"Gesamt", Stats.duration(data.seconds), "clock"},
                {"Heute", Stats.duration(data.today), "sun"},
                {"7 Tage", Stats.duration(data.week), "chart"},
                {"Starts", String.valueOf(data.launches), "play"},
                {"Längste Sitzung", Stats.duration(data.longest), "hourglass"},
        };
        float gap = 14 * d, tw = (bw - 4 * gap) / 5f, th = 104 * d;
        for (int i = 0; i < tiles.length; i++) {
            float tx = bx + i * (tw + gap);
            card(tx, by, tw, th);
            if (i == 0) Draw.rect(kit.g, tx + 1, by + 1, tw - 2, th - 2, 15 * d, 0x1A9B5CFF);
            Icons.draw(kit.g, tiles[i][2], tx + 18 * d, by + 18 * d, 18 * d, i == 0 ? Tokens.ACC : Tokens.SUBTLE);
            kit.caption(tiles[i][0], tx + 44 * d, by + 22 * d);
            kit.textMid(tiles[i][1], tx + 18 * d, by + 70 * d, 24, Tokens.SEMIBOLD, i == 0 ? 0xFFFFFFFF : Tokens.TEXT, tw - 36 * d);
        }

        float top = by + th + 18 * d, bottom = by + bh;
        float leftW = (bw - 18 * d) * 0.6f, rightX = bx + leftW + 18 * d, rightW = bw - leftW - 18 * d;
        float chartH = Math.max(160 * d, (bottom - top) * 0.55f);
        chart(data, bx, top, leftW, chartH);
        list("Meistgespielte Server", "servers", data.servers, bx, top + chartH + 18 * d, leftW, bottom - top - chartH - 18 * d, false);
        float half = (bottom - top - 18 * d) / 2f;
        list("Versionen", "cube", data.versions, rightX, top, rightW, half, true);
        list("Profile", "layout", data.profiles, rightX, top + half + 18 * d, rightW, bottom - top - half - 18 * d, true);
    }

    private void card(float x, float y, float w, float h) {
        Draw.rect(kit.g, x, y, w, h, 16 * d, Tokens.SURFACE);
        Draw.ring(kit.g, x, y, w, h, 16 * d, 1, Tokens.LINE);
    }

    private void empty(float x, float y, float w, float h, String icon, String title, String text) {
        float cx = x + w / 2f, cy = y + h / 2f - 40 * d;
        Draw.rect(kit.g, cx - 32 * d, cy - 32 * d, 64 * d, 64 * d, 18 * d, Tokens.SURFACE);
        Icons.draw(kit.g, icon, cx - 14 * d, cy - 14 * d, 28 * d, Tokens.MUTED);
        kit.textCenter(title, cx, cy + 60 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT);
        if (text != null) kit.textCenter(text, cx, cy + 88 * d, 14.5f, Tokens.MEDIUM, Tokens.MUTED);
    }

    /** Balken der letzten 14 Tage */
    private void chart(Stats.Data data, float x, float y, float w, float h) {
        card(x, y, w, h);
        float pad = 20 * d;
        kit.caption("Letzte 14 Tage", x + pad, y + pad);
        List<Stats.Day> days = data.days.subList(Math.max(0, data.days.size() - 14), data.days.size());
        long max = 1;
        for (Stats.Day day : days) max = Math.max(max, day.seconds());
        kit.textRight("Max " + Stats.duration(max), x + w - pad, y + pad + 5 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE);
        float areaTop = y + pad + 30 * d, areaH = h - (areaTop - y) - pad - 26 * d, areaW = w - 2 * pad;
        // feine Hilfslinien
        for (int i = 0; i <= 3; i++) kit.hline(x + pad, areaTop + areaH * i / 3f, areaW);
        if (days.isEmpty()) {
            kit.textCenter("Noch keine Daten", x + w / 2, areaTop + areaH / 2, 14.5f, Tokens.MEDIUM, Tokens.MUTED);
            return;
        }
        int n = days.size();
        float slot = areaW / n, bw = Math.max(4 * d, slot * 0.62f);
        for (int i = 0; i < n; i++) {
            Stats.Day day = days.get(i);
            float sx = x + pad + i * slot, bx = sx + (slot - bw) / 2;
            float bh = day.seconds() <= 0 ? 2 * d : Math.max(4 * d, areaH * day.seconds() / max);
            boolean today = i == n - 1;
            boolean hv = kit.hit("bar" + i, sx, areaTop, slot, areaH + 26 * d, null);
            float hvT = Motion.to(kit.key("barh" + i), hv, Tokens.MS_HOVER);
            float r = Math.min(6 * d, bw / 2);
            if (today) Draw.accentRect(kit.g, bx, areaTop + areaH - bh, bw, bh, r, 1f);
            else Draw.rect(kit.g, bx, areaTop + areaH - bh, bw, bh, r, Draw.mix(0x2EFFFFFF, 0x66FFFFFF, hvT));
            String label = day.day().length() >= 10 ? day.day().substring(8, 10) + "." : "";
            kit.textCenter(label, sx + slot / 2, areaTop + areaH + 14 * d, 12, Tokens.MEDIUM, today ? Tokens.ACC_TEXT : Tokens.SUBTLE);
            if (hv) {
                String date = day.day().length() >= 10 ? day.day().substring(8, 10) + "." + day.day().substring(5, 7) + "." : day.day();
                kit.tooltip(date + ": " + Stats.duration(day.seconds()) + " · " + day.launches() + (day.launches() == 1 ? " Start" : " Starts"),
                        sx + slot / 2, areaTop + areaH - bh - 36 * d);
            }
        }
    }

    /** Liste mit Anteils-Balken */
    private void list(String title, String icon, List<Stats.Entry> entries, float x, float y, float w, float h, boolean launches) {
        card(x, y, w, h);
        float pad = 20 * d;
        Icons.draw(kit.g, icon, x + pad, y + pad - 3 * d, 16 * d, Tokens.SUBTLE);
        kit.caption(title, x + pad + 24 * d, y + pad);
        if (entries.isEmpty()) {
            kit.textMid("Noch keine Daten", x + pad, y + pad + 40 * d, 14.5f, Tokens.MEDIUM, Tokens.MUTED, 0);
            return;
        }
        long max = Math.max(1, entries.get(0).seconds());
        float ry = y + pad + 30 * d, rowH = 44 * d;
        for (Stats.Entry e : entries) {
            if (ry + rowH > y + h - pad / 2) break;
            String right = Stats.duration(e.seconds()) + (launches ? " · " + e.launches() + "×" : "");
            float rw = kit.width(right, 13.5f, Tokens.MEDIUM);
            kit.textMid(e.name().isEmpty() ? "—" : e.name(), x + pad, ry + 12 * d, 15, Tokens.MEDIUM, Tokens.TEXT, w - 2 * pad - rw - 12 * d);
            kit.textRight(right, x + w - pad, ry + 12 * d, 13.5f, Tokens.MEDIUM, Tokens.MUTED);
            float tw = w - 2 * pad, fill = Math.max(4 * d, tw * e.seconds() / (float) max);
            Draw.rect(kit.g, x + pad, ry + 26 * d, tw, 5 * d, 2.5f * d, 0x14FFFFFF);
            Draw.accentRect(kit.g, x + pad, ry + 26 * d, fill, 5 * d, 2.5f * d, 0.85f);
            ry += rowH;
        }
    }
}
