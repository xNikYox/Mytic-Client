package de.myticlegacy.client.games;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.FriendsScreen;
import de.myticlegacy.client.gui.Input;
import de.myticlegacy.client.gui.PanelScreen;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * X und O (Mytic Games): 3×3-Brett im Stil des Mod-Menüs, mehrere Runden (1 / Best of 3 / Best of 5), optional Zeit pro
 * Zug. Der Server prüft Züge, zählt Punkte und beginnt die nächste Runde; das Endbrett einer Runde wird kurz gezeigt.
 * Verlassen zählt wie beim Schach als Aufgeben.
 */
public class XoScreen extends PanelScreen {
    private final String id;
    private int cell = 60, bx, by;
    /** eigener Zug, bis der Server ihn bestätigt */
    private String localBoard;
    private int localRound = -1, localCount = -1;
    private String status = "";
    private long statusAt;
    private long resignArmed;
    private boolean confirmLeave, leaving, overlayClosed;
    /** Ende einer Runde kurz zeigen: Ergebnis und bis wann */
    private int seenResults = -1;
    private Games.XoResult showing;
    private long showUntil;

    public XoScreen(String id) {
        super("X und O");
        this.id = id;
    }

    public String gameId() {
        return id;
    }

    @Override
    protected String title() {
        return "X UND O";
    }

    @Override
    protected int pixelScale() {
        return fit(420, 300, 0.9, 0.9);
    }

    @Override
    protected void layout() {
        super.layout();
        pw = Math.min(vw - 8, 420);
        ph = Math.min(vh - 8, 300);
        px = (vw - pw) / 2;
        py = (vh - ph) / 2;
        cell = Math.max(24, Math.min(68, (ph - HEADER - FOOTER - 28) / 3));
        bx = px + 12;
        by = py + HEADER + 10;
    }

    private Games.Game game() {
        return Games.get(id);
    }

    private static int radius(int r) {
        return Ui.square() ? 0 : r;
    }

    private static int text() {
        return Ui.lunar() ? 0xFFFFFFFF : Ui.TEXT;
    }

    private static int muted() {
        return Ui.lunar() ? 0xFF9A9A9A : Ui.MUTED;
    }

    /** Farbe eines Symbols: X in Akzentfarbe, O in Schalter-Farbe */
    private static int color(char sym) {
        int x = Ui.accent() | 0xFF000000, o = ChessScreen.highlight() | 0xFF000000;
        // Akzent und Schalter-Farbe zu ähnlich (z. B. beide blau) → O bekommt ein gut unterscheidbares Orange
        if (distance(x, o) < 140) o = distance(x, 0xFFFF9F43) < 140 ? 0xFF4DA3FF : 0xFFFF9F43;
        return sym == 'X' ? x : o;
    }

    /** Farbabstand (RGB) */
    private static int distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return (int) Math.sqrt(dr * dr + dg * dg + db * db);
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

    /** Brett, das gezeigt wird: eigener Zug schon gesetzt, solange der Server ihn nicht bestätigt hat */
    private String board(Games.Game g) {
        if (localBoard != null && g.xoRound == localRound && count(g.xoBoard) < localCount) return localBoard;
        localBoard = null;
        return g.xoBoard;
    }

    private static int count(String board) {
        int n = 0;
        for (char c : board.toCharArray()) if (c == 'X' || c == 'O') n++;
        return n;
    }

    private int[] cellPos(int i) {
        return new int[]{bx + (i % 3) * (cell + 6), by + (i / 3) * (cell + 6)};
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        Games.Game game = game();
        if (game == null) {
            Ui.centered(g, "Spiel wird geladen …", px + pw / 2, py + ph / 2 - 4, muted(), false);
            return;
        }
        Ui.text(g, "gegen " + game.opponentName() + " · " + game.settings(), headerX, py + 13, muted(), false);
        // neues Rundenergebnis → Endbrett kurz zeigen
        int results = game.xoResults.size();
        if (seenResults >= 0 && results > seenResults) {
            showing = game.xoResults.get(results - 1);
            showUntil = System.currentTimeMillis() + 1600;
        }
        seenResults = results;
        boolean show = showing != null && System.currentTimeMillis() < showUntil && showing.board() != null;
        String board = show ? showing.board() : board(game);
        int[] line = show && showing.line() != null ? showing.line() : new int[0];
        drawBoard(g, game, board, line, show, mouseX, mouseY);
        drawSide(g, game, show);
        drawFooter(g, game, mouseX, mouseY);
        if (game.over() && !overlayClosed && !show) drawResult(g, game, mouseX, mouseY);
        if (game.invited()) drawWaiting(g, game, mouseX, mouseY);
        if (confirmLeave && game.active()) drawLeave(g, game, mouseX, mouseY);
        else confirmLeave = false;
    }

    private void drawBoard(GuiGraphics g, Games.Game game, String board, int[] line, boolean show, int mx, int my) {
        int size = 3 * cell + 12;
        Ui.rect(g, bx - 6, by - 6, size, size, radius(6), 0x55000000);
        boolean canPlay = !show && game.myTurn() && localBoard == null;
        char mine = game.mySymbol().equals("x") ? 'X' : 'O';
        for (int i = 0; i < 9; i++) {
            int[] p = cellPos(i);
            char v = i < board.length() ? board.charAt(i) : '.';
            boolean inLine = contains(line, i);
            boolean hover = canPlay && v == '.' && Ui.inside(mx, my, p[0], p[1], cell, cell);
            int fill = inLine ? Ui.mix(ChessScreen.card(false), ChessScreen.highlight(), 0.3f) : ChessScreen.card(hover);
            Ui.rect(g, p[0], p[1], cell, cell, radius(4), fill);
            if (inLine) Ui.outline(g, p[0], p[1], cell, cell, radius(4), ChessScreen.highlight(), fill);
            if (v == 'X' || v == 'O') {
                int c = color(v);
                if (line.length > 0 && !inLine) c = Ui.withAlpha(c, 90); // Rest blass, wenn eine Reihe gewonnen hat
                symbol(g, v, p[0] + cell / 2, p[1] + cell / 2, cell * 0.62f, c);
            } else if (hover) symbol(g, mine, p[0] + cell / 2, p[1] + cell / 2, cell * 0.62f, Ui.withAlpha(color(mine), 55));
        }
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    /** ✕ als zwei dicke Diagonalen, ◯ als Ring – Pixel für Pixel, damit es auch im Stil „Eckig“ rund bleibt */
    static void symbol(GuiGraphics g, char sym, int cx, int cy, float size, int color) {
        int half = Math.round(size / 2), t = Math.max(2, Math.round(size / 8));
        if (sym == 'X') {
            for (int k = -half; k <= half - t; k++) {
                g.fill(cx + k, cy + k, cx + k + t, cy + k + t, color);
                g.fill(cx - k - t, cy + k, cx - k, cy + k + t, color);
            }
        } else {
            int r = half, ri = half - t;
            for (int y = -r; y <= r; y++) {
                int outer = (int) Math.round(Math.sqrt(r * r - y * y));
                if (Math.abs(y) < ri) {
                    int inner = (int) Math.round(Math.sqrt(ri * ri - y * y));
                    g.fill(cx - outer, cy + y, cx - inner, cy + y + 1, color);
                    g.fill(cx + inner, cy + y, cx + outer, cy + y + 1, color);
                } else g.fill(cx - outer, cy + y, cx + outer, cy + y + 1, color);
            }
        }
    }

    private void drawSide(GuiGraphics g, Games.Game game, boolean show) {
        int sx = bx + 3 * cell + 12 + 12, sw = px + pw - 12 - sx, y = by - 6;
        boolean meX = game.mySymbol().equals("x");
        int myScore = meX ? game.xoScoreX : game.xoScoreO, opScore = meX ? game.xoScoreO : game.xoScoreX;
        // Spielstand
        Ui.rect(g, sx, y, sw, 42, radius(4), ChessScreen.card(false));
        boolean myTurn = game.myTurn() && !show;
        player(g, sx + 6, y + 6, minecraft.getUser().getName(), meX ? 'X' : 'O', myTurn, false, sw);
        player(g, sx + sw - 6, y + 6, game.opponentName(), meX ? 'O' : 'X', game.active() && !game.myTurn() && !show, true, sw);
        String score = myScore + " : " + opScore;
        Ui.scaled(g, score, sx + sw / 2f - font.width(score) * 1.4f / 2, y + 8, 1.4f, text(), false);
        // Runden als Punkte
        int dots = game.rounds, dx = sx + sw / 2 - (dots * 7) / 2;
        int k = 0;
        for (Games.XoResult r : game.xoResults) {
            if (r.winner() == null) continue;
            if (k >= dots) break;
            boolean mineWon = r.winner().equals(game.mySymbol());
            Ui.rect(g, dx + k * 7, y + 26, 5, 5, radius(3), mineWon ? ChessScreen.highlight() : Ui.RED);
            k++;
        }
        for (; k < dots; k++) Ui.rect(g, dx + k * 7, y + 26, 5, 5, radius(3), 0x40FFFFFF);
        // Status mit Zug-Timer
        y += 50;
        Ui.rect(g, sx, y, sw, 34, radius(4), ChessScreen.card(false));
        String state;
        int color = text();
        if (System.currentTimeMillis() - statusAt < 5000 && !status.isEmpty()) {
            state = status;
            color = Ui.RED;
        } else if (game.invited()) state = "Warte auf Annahme …";
        else if (show) {
            boolean draw = showing.winner() == null;
            boolean won = !draw && showing.winner().equals(game.mySymbol());
            state = draw ? "Unentschieden – zählt nicht" : "timeout".equals(showing.reason()) ? (won ? "Runde gewonnen (Zeit)" : "Runde verloren (Zeit)") : won ? "Runde gewonnen!" : "Runde verloren";
            color = draw ? muted() : won ? ChessScreen.highlight() : 0xFFFF8F96;
        } else if (game.over()) state = Games.reasonText(game.reason);
        else state = game.myTurn() ? "Du bist dran" : game.opponentName() + " ist dran";
        Ui.text(g, state, sx + 6, y + 6, color, false);
        if (game.rounds > 1) {
            String round = "Runde " + game.xoRound;
            Ui.scaled(g, round, sx + sw - 6 - font.width(round) * 0.75f, y + 7, 0.75f, muted(), false);
        }
        long left = game.moveLeft();
        if (left >= 0 && game.moveTime > 0 && !show) {
            int bw = sw - 12, fillW = (int) (bw * Math.min(1.0, left / (game.moveTime * 1000.0)));
            g.fill(sx + 6, y + 22, sx + 6 + bw, y + 26, 0x55000000);
            g.fill(sx + 6, y + 22, sx + 6 + fillW, y + 26, left < 3000 ? Ui.RED : ChessScreen.highlight());
        }
        // Rundenliste
        y += 42;
        int bottom = by + 3 * cell + 6;
        Ui.rect(g, sx, y, sw, bottom - y, radius(4), ChessScreen.card(false));
        int ry = y + 5, n = 0;
        if (game.xoResults.isEmpty()) Ui.text(g, "Noch keine Runde beendet", sx + 6, ry, muted(), false);
        for (Games.XoResult r : game.xoResults) {
            if (ry > bottom - 12) break;
            n++;
            String who = r.winner() == null ? "unentschieden" : r.winner().equals(game.mySymbol()) ? "gewonnen" : "verloren";
            if ("timeout".equals(r.reason())) who += " (Zeit)";
            Ui.text(g, "Runde " + n, sx + 6, ry, text(), false);
            Ui.text(g, who, sx + sw - 6 - font.width(who), ry, muted(), false);
            ry += 11;
        }
    }

    private void player(GuiGraphics g, int x, int y, String name, char sym, boolean turn, boolean right, int sw) {
        var connection = minecraft.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(name);
        int hx = right ? x - 16 : x;
        if (info != null) Compat.drawFace(g, info, hx, y, 16);
        else FriendsScreen.drawHead(g, null, name, hx, y, 16);
        if (turn) Ui.outline(g, hx - 1, y - 1, 18, 18, 0, ChessScreen.highlight(), 0x00000000);
        symbol(g, sym, right ? x - 8 : x + 8, y + 26, 9, color(sym));
        String shown = name;
        int max = sw / 2 - 40;
        while (shown.length() > 3 && font.width(shown) > max) shown = shown.substring(0, shown.length() - 2) + "…";
        Ui.scaled(g, shown, right ? hx - 3 - font.width(shown) * 0.75f : hx + 19, y + 4, 0.75f, text(), false);
    }

    private int[] resignButton() {
        return new int[]{px + pw - 12 - 70, py + ph - FOOTER + 6, 70, 16};
    }

    private void drawFooter(GuiGraphics g, Games.Game game, int mx, int my) {
        Ui.text(g, "Klicken zum Setzen · Verlassen = aufgeben", px + 12, py + ph - FOOTER + 10, muted(), false);
        if (!game.active()) return;
        boolean armed = System.currentTimeMillis() - resignArmed < 3000;
        int[] r = resignButton();
        boolean hover = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
        Ui.rect(g, r[0], r[1], r[2], r[3], radius(4), armed ? Ui.RED : hover ? Ui.withAlpha(Ui.RED, 90) : ChessScreen.card(false));
        Ui.centered(g, armed ? "Wirklich?" : "Aufgeben", r[0] + r[2] / 2, r[1] + 4, armed ? 0xFFFFFFFF : 0xFFFF9AA0, false);
    }

    private int[] overlayButton(int i) {
        int w = 70, cx = bx + (3 * cell + 12) / 2;
        return new int[]{cx - w - 3 + i * (w + 6), by + (3 * cell + 12) / 2 + 10, w, 16};
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        int panel = Ui.lunar() ? Ui.L_PANEL | 0xFF000000 : Ui.PANEL | 0xFF000000;
        Ui.rect(g, x, y, w, h, radius(6), panel);
        Ui.outline(g, x, y, w, h, radius(6), Ui.lunar() ? Ui.L_LINE : Ui.LINE, panel);
    }

    private void dim(GuiGraphics g) {
        int size = 3 * cell + 12;
        g.fill(bx - 6, by - 6, bx - 6 + size, by - 6 + size, 0x99000000);
    }

    private void drawResult(GuiGraphics g, Games.Game game, int mx, int my) {
        dim(g);
        int size = 3 * cell + 12, w = Math.min(size - 8, 160), h = 66, x = bx - 6 + size / 2 - w / 2, y = by - 6 + size / 2 - 32;
        panel(g, x, y, w, h);
        boolean meX = game.mySymbol().equals("x");
        boolean won = ("1-0".equals(game.result)) == meX;
        String title = won ? "Gewonnen!" : "Verloren";
        int my2 = meX ? game.xoScoreX : game.xoScoreO, op = meX ? game.xoScoreO : game.xoScoreX;
        Ui.scaled(g, title, x + w / 2f - font.width(title) * 1.6f / 2, y + 7, 1.6f, won ? ChessScreen.highlight() : 0xFFFF8F96, true);
        String why = "rounds".equals(game.reason) ? my2 + " : " + op : Games.reasonText(game.reason);
        Ui.centered(g, why, x + w / 2, y + 24, muted(), false);
        ChessScreen.button(g, overlayButton(0), "Revanche", true, mx, my);
        ChessScreen.button(g, overlayButton(1), "Schließen", false, mx, my);
    }

    private int[] withdrawButton() {
        int size = 3 * cell + 12;
        return new int[]{bx - 6 + size / 2 - 45, by - 6 + size / 2 + 6, 90, 16};
    }

    private void drawWaiting(GuiGraphics g, Games.Game game, int mx, int my) {
        dim(g);
        int size = 3 * cell + 12;
        String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
        Ui.centered(g, "Warte auf " + game.inviteeName + dots, bx - 6 + size / 2, by - 6 + size / 2 - 12, 0xFFFFFFFF, true);
        ChessScreen.button(g, withdrawButton(), "Zurückziehen", false, mx, my);
    }

    private int[] leaveButton(int i) {
        int w = 74, cx = bx - 6 + (3 * cell + 12) / 2;
        return new int[]{cx - w - 3 + i * (w + 6), by - 6 + (3 * cell + 12) / 2 + 8, w, 16};
    }

    private void drawLeave(GuiGraphics g, Games.Game game, int mx, int my) {
        dim(g);
        int size = 3 * cell + 12, w = Math.min(size - 4, 170), h = 62, x = bx - 6 + size / 2 - w / 2, y = by - 6 + size / 2 - 32;
        panel(g, x, y, w, h);
        Ui.centered(g, "Spiel verlassen?", x + w / 2, y + 7, text(), true);
        Ui.centered(g, "Dann gewinnt " + game.opponentName() + ".", x + w / 2, y + 19, muted(), false);
        ChessScreen.button(g, leaveButton(0), "Weiterspielen", true, mx, my);
        int[] r = leaveButton(1);
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
            if (inside(leaveButton(0), mx, my)) confirmLeave = false;
            else if (inside(leaveButton(1), mx, my)) {
                leaving = true;
                Games.resign(id, true);
                Compat.setScreen(null);
            }
            return true;
        }
        if (game.invited()) {
            if (inside(withdrawButton(), mx, my)) {
                Games.cancel(id);
                back();
            }
            return true;
        }
        if (game.over() && !overlayClosed) {
            if (inside(overlayButton(0), mx, my)) Compat.setScreen(new ChallengeScreen(game.opponentName(), ChallengeScreen.XO));
            else if (inside(overlayButton(1), mx, my)) overlayClosed = true;
            return true;
        }
        if (game.active() && inside(resignButton(), mx, my)) {
            if (System.currentTimeMillis() - resignArmed < 3000) {
                leaving = true;
                Games.resign(id, false);
            } else resignArmed = System.currentTimeMillis();
            return true;
        }
        if (!game.myTurn() || localBoard != null || System.currentTimeMillis() < showUntil) return false;
        for (int i = 0; i < 9; i++) {
            int[] p = cellPos(i);
            if (!Ui.inside(mx, my, p[0], p[1], cell, cell)) continue;
            String board = board(game);
            if (board.charAt(i) != '.') return true;
            int ply = count(board);
            char mine = game.mySymbol().equals("x") ? 'X' : 'O';
            localBoard = board.substring(0, i) + mine + board.substring(i + 1);
            localRound = game.xoRound;
            localCount = ply + 1;
            Games.place(id, i, ply, error -> {
                localBoard = null;
                status = error;
                statusAt = System.currentTimeMillis();
            });
            return true;
        }
        return false;
    }

    private static boolean inside(int[] r, double mx, double my) {
        return Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
    }
}
