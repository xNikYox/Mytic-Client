package de.myticlegacy.client.stats;

import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.PanelScreen;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Statistiken im Spiel (Rechts-Shift → Statistiken): Kennzahlen, Verlauf der letzten 14 Tage, Server, Versionen, Schach. */
public class StatsScreen extends PanelScreen {
    public StatsScreen() {
        super("Statistiken");
        Stats.refresh(true);
    }

    @Override
    protected String title() {
        return "STATISTIKEN";
    }

    private static int text() {
        return Ui.lunar() ? 0xFFFFFFFF : Ui.TEXT;
    }

    private static int muted() {
        return Ui.lunar() ? 0xFF9A9A9A : Ui.MUTED;
    }

    private static int card() {
        return Ui.lunar() ? Ui.L_CARD : Ui.SURFACE;
    }

    private static int hi() {
        return Ui.lunar() ? (Ui.glass() ? Ui.accent() : Ui.L_GREEN) : Ui.accent();
    }

    private static int radius(int r) {
        return Ui.square() ? 0 : r;
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        Stats.Data d = Stats.data();
        int x0 = px + 12, y0 = py + HEADER + 8, w = pw - 24;
        if (!Friends.available()) {
            Ui.centered(g, "Statistiken gibt es nur mit der Freundesliste – starte über den Mytic Launcher.", px + pw / 2, py + ph / 2 - 4, muted(), false);
            return;
        }
        if (d == null) {
            String msg = Stats.error() != null ? "Nicht geladen: " + Stats.error() : "Lädt …";
            Ui.centered(g, msg, px + pw / 2, py + ph / 2 - 4, muted(), false);
            return;
        }
        Ui.text(g, Stats.loading() ? "aktualisiert …" : "Gezählt wird die Zeit in einer Welt", headerX, py + 13, muted(), false);

        // Kennzahlen
        String[][] tiles = {
                {"Gesamt", Stats.duration(d.seconds)},
                {"Heute", Stats.duration(d.today)},
                {"7 Tage", Stats.duration(d.week)},
                {"Starts", String.valueOf(d.launches)},
                {"Längste Sitzung", Stats.duration(d.longest)},
        };
        int tw = (w - 4 * 6) / 5;
        for (int i = 0; i < tiles.length; i++) {
            int tx = x0 + i * (tw + 6);
            Ui.rect(g, tx, y0, tw, 30, radius(4), card());
            Ui.scaled(g, tiles[i][0], tx + 6, y0 + 5, 0.75f, muted(), false);
            Ui.text(g, tiles[i][1], tx + 6, y0 + 16, i == 0 ? hi() : text(), false);
        }

        int top = y0 + 38, bottom = py + ph - FOOTER - 6;
        int leftW = (w - 8) * 3 / 5, rightX = x0 + leftW + 8, rightW = w - leftW - 8;

        // Verlauf: letzte 14 Tage
        int chartH = Math.max(40, (bottom - top) / 2 - 6);
        Ui.rect(g, x0, top, leftW, chartH, radius(4), card());
        Ui.scaled(g, "LETZTE 14 TAGE", x0 + 6, top + 5, 0.7f, muted(), false);
        List<Stats.Day> days = d.days.subList(Math.max(0, d.days.size() - 14), d.days.size());
        long max = 1;
        for (Stats.Day day : days) max = Math.max(max, day.seconds());
        int barArea = chartH - 26, bw = Math.max(3, (leftW - 12) / Math.max(1, days.size()) - 3);
        String tip = null;
        for (int i = 0; i < days.size(); i++) {
            Stats.Day day = days.get(i);
            int bx = x0 + 6 + i * (bw + 3), bh = Math.max(day.seconds() > 0 ? 2 : 1, (int) (barArea * day.seconds() / max));
            int by = top + 14 + barArea - bh;
            boolean today = i == days.size() - 1;
            boolean over = Ui.inside(mouseX, mouseY, bx, top + 14, bw, barArea);
            g.fill(bx, by, bx + bw, top + 14 + barArea, today ? hi() : Ui.withAlpha(text(), over ? 170 : 90));
            if (over) tip = day.day().substring(8) + "." + day.day().substring(5, 7) + ".: " + Stats.duration(day.seconds()) + " · " + day.launches() + " Starts";
        }
        Ui.scaled(g, tip != null ? tip : "Max " + Stats.duration(max), x0 + 6, top + chartH - 9, 0.7f, tip != null ? text() : muted(), false);

        // Server
        int sy = top + chartH + 8;
        list(g, "MEISTGESPIELTE SERVER", d.servers, x0, sy, leftW, bottom - sy, false);

        // Versionen und Schach
        int vh = (bottom - top) / 2 - 4;
        list(g, "VERSIONEN", d.versions, rightX, top, rightW, vh, true);
        int cy = top + vh + 8, ch = bottom - cy;
        Ui.rect(g, rightX, cy, rightW, ch, radius(4), card());
        Ui.scaled(g, "SCHACH", rightX + 6, cy + 5, 0.7f, muted(), false);
        if (d.chessPlayed == 0) Ui.text(g, "Noch keine Partie – /myticgame <name>", rightX + 6, cy + 16, muted(), false);
        else {
            Ui.text(g, d.chessPlayed + " Partien", rightX + 6, cy + 16, text(), false);
            int barW = rightW - 12, wW = barW * d.chessWins / d.chessPlayed, dW = barW * d.chessDraws / d.chessPlayed;
            g.fill(rightX + 6, cy + 28, rightX + 6 + wW, cy + 33, hi());
            g.fill(rightX + 6 + wW, cy + 28, rightX + 6 + wW + dW, cy + 33, 0xFF8A8A8A);
            g.fill(rightX + 6 + wW + dW, cy + 28, rightX + 6 + barW, cy + 33, Ui.RED);
            Ui.scaled(g, d.chessWins + " S · " + d.chessDraws + " R · " + d.chessLosses + " N", rightX + 6, cy + 37, 0.75f, muted(), false);
            int oy = cy + 48;
            for (Stats.Opponent o : d.opponents) {
                if (oy > cy + ch - 10) break;
                Ui.scaled(g, o.name() + "  " + o.wins() + "-" + o.draws() + "-" + o.losses(), rightX + 6, oy, 0.75f, text(), false);
                oy += 9;
            }
        }
    }

    /** Liste mit Anteils-Balken */
    private void list(GuiGraphics g, String title, List<Stats.Entry> entries, int x, int y, int w, int h, boolean launches) {
        Ui.rect(g, x, y, w, h, radius(4), card());
        Ui.scaled(g, title, x + 6, y + 5, 0.7f, muted(), false);
        if (entries.isEmpty()) {
            Ui.text(g, "Noch keine Daten", x + 6, y + 16, muted(), false);
            return;
        }
        long max = Math.max(1, entries.get(0).seconds());
        int ry = y + 15;
        for (Stats.Entry e : entries) {
            if (ry > y + h - 12) break;
            String right = Stats.duration(e.seconds()) + (launches ? " · " + e.launches() + "×" : "");
            int rw = Math.round(font.width(right) * 0.75f);
            g.fill(x + 6, ry + 8, x + 6 + (int) ((w - 12) * e.seconds() / max), ry + 10, Ui.withAlpha(hi(), 170));
            String name = e.name();
            while (name.length() > 3 && font.width(name) > w - 18 - rw) name = name.substring(0, name.length() - 2) + "…";
            Ui.text(g, name, x + 6, ry, text(), false);
            Ui.scaled(g, right, x + w - 6 - rw, ry + 1, 0.75f, muted(), false);
            ry += 13;
        }
    }
}
