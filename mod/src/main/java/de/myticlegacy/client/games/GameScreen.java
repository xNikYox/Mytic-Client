package de.myticlegacy.client.games;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.FriendsScreen;
import de.myticlegacy.client.gui.Input;
import de.myticlegacy.client.gui.PanelScreen;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Gemeinsames für Vier gewinnt und Schiffe versenken: Verlassen = aufgeben (mit Nachfrage), Warten auf Annahme,
 * Ergebnis mit Revanche, Aufgeben-Knopf, Farben und Köpfe wie im Mod-Menü. Unterklassen zeichnen ihr Spielfeld in
 * drawGame und behandeln Klicks darauf in clickGame; overlay() ist der Bereich, über dem Hinweise erscheinen.
 */
public abstract class GameScreen extends PanelScreen {
    protected final String id;
    protected String status = "";
    protected long statusAt;
    private long resignArmed;
    private boolean confirmLeave, leaving, overlayClosed;

    protected GameScreen(String title, String id) {
        super(title);
        this.id = id;
    }

    public String gameId() {
        return id;
    }

    protected Games.Game game() {
        return Games.get(id);
    }

    /** Bereich für Hinweise (Warten, Ergebnis, Nachfrage): x, y, Breite, Höhe */
    protected abstract int[] overlay();

    protected abstract void drawGame(GuiGraphics g, Games.Game game, int mouseX, int mouseY);

    protected abstract boolean clickGame(Games.Game game, double mx, double my);

    /** Revanche: Herausforderung für dasselbe Spiel */
    protected abstract ChallengeScreen rematch(Games.Game game);

    /** Hinweis unten links */
    protected abstract String hint();

    protected static int radius(int r) {
        return Ui.square() ? 0 : r;
    }

    protected static int text() {
        return Ui.lunar() ? 0xFFFFFFFF : Ui.TEXT;
    }

    protected static int muted() {
        return Ui.lunar() ? 0xFF9A9A9A : Ui.MUTED;
    }

    protected void showError(String error) {
        status = error;
        statusAt = System.currentTimeMillis();
    }

    /** Fehlermeldung der letzten 5 Sekunden oder null */
    protected String error() {
        return System.currentTimeMillis() - statusAt < 5000 && !status.isEmpty() ? status : null;
    }

    // ------------------------------------------------------------------------------------------ Verlassen

    @Override
    protected void back() {
        Games.Game game = game();
        if (game != null && game.active() && !leaving) {
            confirmLeave = !confirmLeave;
            return;
        }
        Compat.setScreen(null);
    }

    @Override
    public void removed() {
        Games.Game game = game();
        if (game != null && game.active() && !leaving) {
            leaving = true;
            Games.resign(id, true);
        }
        super.removed();
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected final void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        Games.Game game = game();
        if (game == null) {
            Ui.centered(g, "Spiel wird geladen …", px + pw / 2, py + ph / 2 - 4, muted(), false);
            return;
        }
        Ui.text(g, "gegen " + game.opponentName() + " · " + game.settings(), headerX, py + 13, muted(), false);
        drawGame(g, game, mouseX, mouseY);
        drawFooter(g, game, mouseX, mouseY);
        if (game.over() && !overlayClosed && showResult(game)) drawResult(g, game, mouseX, mouseY);
        if (game.invited()) drawWaiting(g, game, mouseX, mouseY);
        if (confirmLeave && game.active()) drawLeave(g, game, mouseX, mouseY);
        else confirmLeave = false;
    }

    /** Ergebnis gerade zeigen? (z. B. erst nach der Anzeige des letzten Endbretts) */
    protected boolean showResult(Games.Game game) {
        return true;
    }

    /** Spieler mit Kopf (gleicher Server: Skin, sonst Anfangsbuchstabe) */
    protected void head(GuiGraphics g, String name, int x, int y, int size) {
        var connection = minecraft.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(name);
        if (info != null) Compat.drawFace(g, info, x, y, size);
        else FriendsScreen.drawHead(g, null, name, x, y, size);
    }

    protected void panel(GuiGraphics g, int x, int y, int w, int h) {
        int panel = Ui.lunar() ? Ui.L_PANEL | 0xFF000000 : Ui.PANEL | 0xFF000000;
        Ui.rect(g, x, y, w, h, radius(6), panel);
        Ui.outline(g, x, y, w, h, radius(6), Ui.lunar() ? Ui.L_LINE : Ui.LINE, panel);
    }

    private void dim(GuiGraphics g) {
        int[] o = overlay();
        g.fill(o[0], o[1], o[0] + o[2], o[1] + o[3], 0x99000000);
    }

    private int[] centerButton(int i, int w) {
        int[] o = overlay();
        int cx = o[0] + o[2] / 2, cy = o[1] + o[3] / 2;
        return new int[]{cx - w - 3 + i * (w + 6), cy + 8, w, 16};
    }

    private int[] resignButton() {
        return new int[]{px + pw - 12 - 70, py + ph - FOOTER + 6, 70, 16};
    }

    private void drawFooter(GuiGraphics g, Games.Game game, int mx, int my) {
        Ui.text(g, hint(), px + 12, py + ph - FOOTER + 10, muted(), false);
        if (!game.active()) return;
        boolean armed = System.currentTimeMillis() - resignArmed < 3000;
        int[] r = resignButton();
        boolean hover = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
        Ui.rect(g, r[0], r[1], r[2], r[3], radius(4), armed ? Ui.RED : hover ? Ui.withAlpha(Ui.RED, 90) : ChessScreen.card(false));
        Ui.centered(g, armed ? "Wirklich?" : "Aufgeben", r[0] + r[2] / 2, r[1] + 4, armed ? 0xFFFFFFFF : 0xFFFF9AA0, false);
    }

    private void drawResult(GuiGraphics g, Games.Game game, int mx, int my) {
        dim(g);
        int[] o = overlay();
        int w = Math.min(o[2] - 8, 170), h = 66, x = o[0] + o[2] / 2 - w / 2, y = o[1] + o[3] / 2 - 32;
        panel(g, x, y, w, h);
        boolean won = ("1-0".equals(game.result)) == game.meWhite();
        boolean draw = "1/2-1/2".equals(game.result);
        String title = draw ? "Remis" : won ? "Gewonnen!" : "Verloren";
        Ui.scaled(g, title, x + w / 2f - font.width(title) * 1.6f / 2, y + 7, 1.6f, draw ? text() : won ? ChessScreen.highlight() : 0xFFFF8F96, true);
        Ui.centered(g, resultDetail(game), x + w / 2, y + 24, muted(), false);
        ChessScreen.button(g, centerButton(0, 70), "Revanche", true, mx, my);
        ChessScreen.button(g, centerButton(1, 70), "Schließen", false, mx, my);
    }

    /** Zeile unter Gewonnen/Verloren */
    protected String resultDetail(Games.Game game) {
        return Games.reasonText(game.reason);
    }

    private void drawWaiting(GuiGraphics g, Games.Game game, int mx, int my) {
        dim(g);
        int[] o = overlay();
        String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
        Ui.centered(g, "Warte auf " + game.inviteeName + dots, o[0] + o[2] / 2, o[1] + o[3] / 2 - 12, 0xFFFFFFFF, true);
        ChessScreen.button(g, centerButton(0, 90), "Zurückziehen", false, mx, my);
    }

    private void drawLeave(GuiGraphics g, Games.Game game, int mx, int my) {
        dim(g);
        int[] o = overlay();
        int w = Math.min(o[2] - 4, 180), h = 62, x = o[0] + o[2] / 2 - w / 2, y = o[1] + o[3] / 2 - 32;
        panel(g, x, y, w, h);
        Ui.centered(g, "Spiel verlassen?", x + w / 2, y + 7, text(), true);
        Ui.centered(g, "Dann gewinnt " + game.opponentName() + ".", x + w / 2, y + 19, muted(), false);
        ChessScreen.button(g, centerButton(0, 74), "Weiterspielen", true, mx, my);
        int[] r = centerButton(1, 74);
        boolean hover = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
        Ui.rect(g, r[0], r[1], r[2], r[3], radius(4), hover ? Ui.RED : Ui.withAlpha(Ui.RED, 120));
        Ui.centered(g, "Verlassen", r[0] + r[2] / 2, r[1] + 4, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------------------------------ Eingabe

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        if (super.click(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        Games.Game game = game();
        if (game == null) return false;
        double mx = event.x(), my = event.y();
        if (confirmLeave) {
            if (inside(centerButton(0, 74), mx, my)) confirmLeave = false;
            else if (inside(centerButton(1, 74), mx, my)) {
                leaving = true;
                Games.resign(id, true);
                Compat.setScreen(null);
            }
            return true;
        }
        if (game.invited()) {
            if (inside(centerButton(0, 90), mx, my)) {
                Games.cancel(id);
                back();
            }
            return true;
        }
        if (game.over() && !overlayClosed && showResult(game)) {
            if (inside(centerButton(0, 70), mx, my)) Compat.setScreen(rematch(game));
            else if (inside(centerButton(1, 70), mx, my)) overlayClosed = true;
            return true;
        }
        if (game.active() && inside(resignButton(), mx, my)) {
            if (System.currentTimeMillis() - resignArmed < 3000) {
                leaving = true;
                Games.resign(id, false);
            } else resignArmed = System.currentTimeMillis();
            return true;
        }
        return clickGame(game, mx, my);
    }

    protected static boolean inside(int[] r, double mx, double my) {
        return Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
    }

    /** gefüllter Kreis, Pixel für Pixel (bleibt auch im Stil „Eckig“ rund) */
    protected static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int w = (int) Math.round(Math.sqrt(r * r - y * y));
            g.fill(cx - w, cy + y, cx + w, cy + y + 1, color);
        }
    }
}
