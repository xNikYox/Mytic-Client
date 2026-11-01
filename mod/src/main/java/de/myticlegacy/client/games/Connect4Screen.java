package de.myticlegacy.client.games;

import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Vier gewinnt (Mytic Games): 7 Spalten × 6 Reihen, Stein in eine Spalte werfen. Runden (1 / Best of 3 / 5), optional
 * Zeit pro Zug. Steine von Weiß („a“, beginnt Runde 1) in Akzentfarbe, die anderen in Schalter-Farbe.
 */
public class Connect4Screen extends GameScreen {
    private static final int W = 7, H = 6;
    private int cell = 30, bx, by;
    /** eigener Stein, bis der Server ihn bestätigt */
    private String localBoard;
    private int localRound = -1, localCount = -1;
    /** zuletzt gefallener Stein (für die kleine Fall-Animation) */
    private int dropCell = -1;
    private long dropAt;
    private String lastBoard;
    private int seenResults = -1;
    private Games.XoResult showing;
    private long showUntil;

    public Connect4Screen(String id) {
        super("Vier gewinnt", id);
    }

    @Override
    protected String title() {
        return "VIER GEWINNT";
    }

    @Override
    protected int pixelScale() {
        return fit(470, 310, 0.9, 0.9);
    }

    @Override
    protected void layout() {
        super.layout();
        pw = Math.min(vw - 8, 470);
        ph = Math.min(vh - 8, 310);
        px = (vw - pw) / 2;
        py = (vh - ph) / 2;
        cell = Math.max(16, Math.min(36, (ph - HEADER - FOOTER - 30) / H));
        bx = px + 18;
        by = py + HEADER + 14;
    }

    @Override
    protected int[] overlay() {
        return new int[]{bx - 6, by - 6, W * cell + 12, H * cell + 12};
    }

    @Override
    protected String hint() {
        return "Spalte anklicken · Verlassen = aufgeben";
    }

    @Override
    protected ChallengeScreen rematch(Games.Game game) {
        return new ChallengeScreen(game.opponentName(), ChallengeScreen.C4);
    }

    private static int color(char v) {
        return v == 'a' ? Ui.accent() : ChessScreen.highlight();
    }

    private static int count(String board) {
        int n = 0;
        for (char c : board.toCharArray()) if (c == 'a' || c == 'b') n++;
        return n;
    }

    private static int dropRow(String board, int col) {
        for (int r = H - 1; r >= 0; r--) if (board.charAt(r * W + col) == '.') return r;
        return -1;
    }

    private String board(Games.Game g) {
        if (localBoard != null && g.c4Round == localRound && count(g.c4Board) < localCount) return localBoard;
        localBoard = null;
        return g.c4Board;
    }

    @Override
    protected boolean showResult(Games.Game game) {
        return System.currentTimeMillis() >= showUntil;
    }

    @Override
    protected String resultDetail(Games.Game game) {
        boolean meA = game.meWhite();
        return "rounds".equals(game.reason) ? (meA ? game.c4ScoreA + " : " + game.c4ScoreB : game.c4ScoreB + " : " + game.c4ScoreA) : Games.reasonText(game.reason);
    }

    @Override
    protected void drawGame(GuiGraphics g, Games.Game game, int mx, int my) {
        int results = game.c4Results.size();
        if (seenResults >= 0 && results > seenResults) {
            showing = game.c4Results.get(results - 1);
            showUntil = System.currentTimeMillis() + 1800;
        }
        seenResults = results;
        boolean show = showing != null && System.currentTimeMillis() < showUntil && showing.board() != null;
        String board = show ? showing.board() : board(game);
        // neuer Stein → Fall-Animation
        if (lastBoard != null && !show && board.length() == lastBoard.length() && count(board) == count(lastBoard) + 1) {
            for (int i = 0; i < board.length(); i++) if (board.charAt(i) != lastBoard.charAt(i)) dropCell = i;
            dropAt = System.currentTimeMillis();
        }
        lastBoard = show ? null : board;
        int[] line = show && showing.line() != null ? showing.line() : new int[0];

        // Brett mit Rahmen in Akzentfarbe
        int[] o = overlay();
        Ui.rect(g, o[0], o[1], o[2], o[3], radius(6), Ui.mix(0xFF15151B, Ui.accent(), 0.3f));
        boolean canPlay = !show && game.myTurn() && localBoard == null;
        char mine = game.meWhite() ? 'a' : 'b';
        int hoverCol = canPlay && mx >= bx && mx < bx + W * cell && my >= by - 6 && my < by + H * cell ? (mx - bx) / cell : -1;
        int ghostRow = hoverCol >= 0 ? dropRow(board, hoverCol) : -1;
        int hole = ChessScreen.card(false) | 0xFF000000;
        long now = System.currentTimeMillis();
        for (int i = 0; i < W * H; i++) {
            int r = i / W, c = i % W, cx = bx + c * cell + cell / 2, cy = by + r * cell + cell / 2, rad = cell / 2 - 3;
            if (c == hoverCol) g.fill(bx + c * cell, by + r * cell, bx + (c + 1) * cell, by + (r + 1) * cell, 0x18FFFFFF);
            disc(g, cx, cy, rad, Ui.mix(hole, 0xFF000000, 0.35f));
            char v = board.charAt(i);
            if (v == 'a' || v == 'b') {
                int col = color(v);
                if (line.length > 0 && !contains(line, i)) col = Ui.withAlpha(col, 90);
                int dy = 0;
                if (i == dropCell && now - dropAt < 220) dy = -(int) ((1 - (now - dropAt) / 220f) * (r + 1) * cell); // fällt von oben
                disc(g, cx, cy + dy, rad, col);
                disc(g, cx, cy + dy + 1, Math.max(1, rad - 3), Ui.mix(col, 0xFFFFFFFF, 0.12f));
                if (contains(line, i)) Ui.outline(g, cx - rad - 2, cy - rad - 2, 2 * rad + 4, 2 * rad + 4, radius(rad), 0xFFFFFFFF, 0x00000000);
            } else if (c == hoverCol && r == ghostRow) disc(g, cx, cy, rad, Ui.withAlpha(color(mine), 70));
        }
        drawSide(g, game, show);
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    private void drawSide(GuiGraphics g, Games.Game game, boolean show) {
        int sx = bx + W * cell + 18, sw = px + pw - 12 - sx, y = by - 6;
        boolean meA = game.meWhite();
        int my = meA ? game.c4ScoreA : game.c4ScoreB, op = meA ? game.c4ScoreB : game.c4ScoreA;
        Ui.rect(g, sx, y, sw, 42, radius(4), ChessScreen.card(false));
        boolean myTurn = game.myTurn() && !show, opTurn = game.active() && !game.myTurn() && !show;
        head(g, minecraft.getUser().getName(), sx + 6, y + 6, 16);
        if (myTurn) Ui.outline(g, sx + 5, y + 5, 18, 18, 0, ChessScreen.highlight(), 0x00000000);
        disc(g, sx + 14, y + 32, 4, color(meA ? 'a' : 'b'));
        head(g, game.opponentName(), sx + sw - 22, y + 6, 16);
        if (opTurn) Ui.outline(g, sx + sw - 23, y + 5, 18, 18, 0, ChessScreen.highlight(), 0x00000000);
        disc(g, sx + sw - 14, y + 32, 4, color(meA ? 'b' : 'a'));
        String score = my + " : " + op;
        Ui.scaled(g, score, sx + sw / 2f - font.width(score) * 1.4f / 2, y + 10, 1.4f, text(), false);
        // Status und Zug-Timer
        y += 50;
        Ui.rect(g, sx, y, sw, 34, radius(4), ChessScreen.card(false));
        String state;
        int color = text();
        if (error() != null) {
            state = error();
            color = Ui.RED;
        } else if (game.invited()) state = "Warte auf Annahme …";
        else if (show) {
            boolean draw = showing.winner() == null, won = !draw && showing.winner().equals(meA ? "a" : "b");
            state = draw ? "Brett voll – zählt nicht" : "timeout".equals(showing.reason()) ? (won ? "Runde gewonnen (Zeit)" : "Runde verloren (Zeit)") : won ? "Runde gewonnen!" : "Runde verloren";
            color = draw ? muted() : won ? ChessScreen.highlight() : 0xFFFF8F96;
        } else if (game.over()) state = Games.reasonText(game.reason);
        else state = game.myTurn() ? "Du bist dran" : game.opponentName() + " ist dran";
        Ui.text(g, state, sx + 6, y + 6, color, false);
        if (game.rounds > 1) {
            String round = "Runde " + game.c4Round;
            Ui.scaled(g, round, sx + sw - 6 - font.width(round) * 0.75f, y + 7, 0.75f, muted(), false);
        }
        long left = game.moveLeft();
        if (left >= 0 && game.moveTime > 0 && !show) {
            int bw = sw - 12, fill = (int) (bw * Math.min(1.0, left / (game.moveTime * 1000.0)));
            g.fill(sx + 6, y + 22, sx + 6 + bw, y + 26, 0x55000000);
            g.fill(sx + 6, y + 22, sx + 6 + fill, y + 26, left < 3000 ? Ui.RED : ChessScreen.highlight());
        }
        // Runden
        y += 42;
        int bottom = by + H * cell + 6;
        Ui.rect(g, sx, y, sw, bottom - y, radius(4), ChessScreen.card(false));
        int ry = y + 5, n = 0;
        if (game.c4Results.isEmpty()) Ui.text(g, "Noch keine Runde beendet", sx + 6, ry, muted(), false);
        for (Games.XoResult r : game.c4Results) {
            if (ry > bottom - 12) break;
            n++;
            String who = r.winner() == null ? "unentschieden" : r.winner().equals(meA ? "a" : "b") ? "gewonnen" : "verloren";
            if ("timeout".equals(r.reason())) who += " (Zeit)";
            Ui.text(g, "Runde " + n, sx + 6, ry, text(), false);
            Ui.text(g, who, sx + sw - 6 - font.width(who), ry, muted(), false);
            ry += 11;
        }
    }

    @Override
    protected boolean clickGame(Games.Game game, double mx, double my) {
        if (!game.myTurn() || localBoard != null || System.currentTimeMillis() < showUntil) return false;
        if (mx < bx || mx >= bx + W * cell || my < by - 6 || my >= by + H * cell) return false;
        int col = (int) ((mx - bx) / cell);
        String board = board(game);
        int row = dropRow(board, col);
        if (row < 0) {
            showError("Spalte ist voll");
            return true;
        }
        int i = row * W + col, ply = count(board);
        char mine = game.meWhite() ? 'a' : 'b';
        localBoard = board.substring(0, i) + mine + board.substring(i + 1);
        localRound = game.c4Round;
        localCount = ply + 1;
        Games.place(id, col, ply, error -> {
            localBoard = null;
            showError(error);
        });
        return true;
    }
}
