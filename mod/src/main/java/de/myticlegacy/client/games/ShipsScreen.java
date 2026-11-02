package de.myticlegacy.client.games;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.gui.Input;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Schiffe versenken (Mytic Games): 10×10, Flotte 5-4-3-3-2, Schiffe dürfen sich nicht berühren. Erst aufstellen
 * (klicken, R dreht, „Zufällig“), dann abwechselnd schießen – Treffer heißt nochmal. Der Server kennt beide Flotten,
 * schickt die gegnerische aber erst nach dem Ende; rund um versenkte Schiffe markiert er die Felder als Wasser.
 */
public class ShipsScreen extends GameScreen {
    private static final int N = 10;
    private static final int[] SIZES = {5, 4, 3, 3, 2};
    private int s = 16, leftX, rightX, gy;
    /** Aufstellung (vor „Bereit“) */
    private final List<int[]> placed = new ArrayList<>();
    private boolean horizontal = true, sentFleet;
    /** Schuss unterwegs (bis der Server antwortet) */
    private int pendingShot = -1, pendingCount = -1;
    /** letzter Schuss: zum Aufblinken merken, wann er neu war */
    private int flashCell = -1;
    private boolean flashByMe;
    private long flashAt;
    private static final int LAST = 0xFFFFD84A; // Markierung des letzten Schusses (gelb, unabhängig vom Design)

    public ShipsScreen(String id) {
        super("Schiffe versenken", id);
    }

    @Override
    protected String title() {
        return "SCHIFFE VERSENKEN";
    }

    @Override
    protected int pixelScale() {
        return fit(520, 330, 0.92, 0.92);
    }

    @Override
    protected void layout() {
        super.layout();
        pw = Math.min(vw - 8, 520);
        ph = Math.min(vh - 8, 330);
        px = (vw - pw) / 2;
        py = (vh - ph) / 2;
        int byWidth = (pw - 24 - 24 - 20) / (2 * N), byHeight = (ph - HEADER - FOOTER - 20 - 16 - 48) / N;
        s = Math.max(9, Math.min(20, Math.min(byWidth, byHeight)));
        gy = py + HEADER + 22;
        leftX = px + 12 + 10;
        rightX = px + pw - 12 - N * s;
    }

    @Override
    protected int[] overlay() {
        return new int[]{px + 8, gy - 14, pw - 16, N * s + 26};
    }

    @Override
    protected String hint() {
        Games.Game g = game();
        return g != null && g.ships != null && "setup".equals(g.ships.phase) ? "Klicken setzt · R dreht · Verlassen = aufgeben" : "Gegner-Feld anklicken · Treffer = nochmal";
    }

    @Override
    protected ChallengeScreen rematch(Games.Game game) {
        return new ChallengeScreen(game.opponentName(), ChallengeScreen.SHIPS);
    }

    // ------------------------------------------------------------------------------------------ Regeln (wie Server)

    private static int[] cellsOf(int at, int len, boolean h) {
        int r = at / N, c = at % N;
        int[] out = new int[len];
        for (int k = 0; k < len; k++) {
            int rr = h ? r : r + k, cc = h ? c + k : c;
            if (rr >= N || cc >= N) return null;
            out[k] = rr * N + cc;
        }
        return out;
    }

    /** passt das Schiff? (in den Grenzen, keine Überlappung, kein Berühren – auch nicht schräg) */
    private static boolean fits(List<int[]> ships, int[] cells) {
        if (cells == null) return false;
        for (int[] ship : ships) {
            for (int x : ship) {
                for (int c : cells) if (Math.abs(x / N - c / N) <= 1 && Math.abs(x % N - c % N) <= 1) return false;
            }
        }
        return true;
    }

    private static List<int[]> randomFleet() {
        Random rnd = new Random();
        for (int tries = 0; tries < 500; tries++) {
            List<int[]> ships = new ArrayList<>();
            boolean ok = true;
            for (int len : SIZES) {
                boolean done = false;
                for (int k = 0; k < 200 && !done; k++) {
                    int[] cells = cellsOf(rnd.nextInt(N * N), len, rnd.nextBoolean());
                    if (fits(ships, cells)) {
                        ships.add(cells);
                        done = true;
                    }
                }
                if (!done) {
                    ok = false;
                    break;
                }
            }
            if (ok) return ships;
        }
        return new ArrayList<>();
    }

    /** Größe des nächsten zu setzenden Schiffs: das größte, das in der Aufstellung noch fehlt (0 = alle gesetzt) */
    private int nextSize() {
        int[] missing = new int[6];
        for (int len : SIZES) missing[len]++;
        for (int[] ship : placed) if (ship.length < 6) missing[ship.length]--;
        for (int len : SIZES) if (missing[len] > 0) return len;
        return 0;
    }

    private static String shipName(int len) {
        return switch (len) {
            case 5 -> "Schlachtschiff";
            case 4 -> "Kreuzer";
            case 3 -> "Zerstörer";
            default -> "U-Boot";
        };
    }

    private static boolean has(List<int[]> ships, int cell) {
        for (int[] ship : ships) for (int c : ship) if (c == cell) return true;
        return false;
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    private int cellAt(int gx, double mx, double my) {
        if (mx < gx || my < gy || mx >= gx + N * s || my >= gy + N * s) return -1;
        return (int) ((my - gy) / s) * N + (int) ((mx - gx) / s);
    }

    private boolean setup(Games.Game g) {
        return g.ships == null || "setup".equals(g.ships.phase);
    }

    @Override
    protected void drawGame(GuiGraphics g, Games.Game game, int mx, int my) {
        Games.Ships sh = game.ships;
        boolean setup = setup(game);
        if (sh != null && pendingShot >= 0 && sh.myShotCount() >= pendingCount) pendingShot = -1;
        if (sh != null && sh.lastCell >= 0 && (sh.lastCell != flashCell || sh.lastByMe != flashByMe)) { // neuer Schuss → aufblinken
            flashCell = sh.lastCell;
            flashByMe = sh.lastByMe;
            flashAt = System.currentTimeMillis();
        }
        List<int[]> myFleet = setup && (sh == null || !sh.myReady) ? placed : sh.myFleet;
        int mySunk = sh == null ? 0 : sh.mySunk.size(), theirSunk = sh == null ? 0 : sh.theirSunk.size();
        // Wer ist dran: Farbe und Puls für das Brett, auf dem gerade geschossen wird
        boolean playing = !setup && game.active() && !game.over() && sh != null;
        boolean mine = playing && game.myTurn();
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
        int turnColor = mine ? ChessScreen.highlight() : OP_TURN;
        // Überschriften
        Ui.scaled(g, "DEINE FLOTTE", leftX, gy - 13, 0.75f, playing && !mine ? turnColor : muted(), false);
        String leftInfo = setup ? placed.size() + " / 5 gesetzt" : (5 - mySunk) + " von 5 übrig";
        if (setup && sh != null && sh.myReady) leftInfo = "bereit";
        Ui.scaled(g, leftInfo, leftX + N * s - font.width(leftInfo) * 0.75f, gy - 13, 0.75f, muted(), false);
        Ui.scaled(g, mine ? "GEGNER · DU SCHIESST" : "GEGNER", rightX, gy - 13, 0.75f, mine ? turnColor : muted(), false);
        String rightInfo = setup ? (sh != null && sh.opReady ? "bereit" : "stellt auf …") : (5 - theirSunk) + " von 5 übrig";
        Ui.scaled(g, rightInfo, rightX + N * s - font.width(rightInfo) * 0.75f, gy - 13, 0.75f, muted(), false);
        // eigenes Meer
        int[] ghost = null;
        boolean ghostOk = false;
        if (setup && !sentFleet && nextSize() > 0) {
            int at = cellAt(leftX, mx, my);
            if (at >= 0) {
                ghost = cellsOf(at, nextSize(), horizontal);
                ghostOk = fits(placed, ghost);
                if (ghost == null) ghost = new int[]{at};
            }
        }
        sea(g, leftX, true, myFleet, sh, ghost, ghostOk, mx, my, game);
        // gegnerisches Meer
        sea(g, rightX, false, null, sh, null, false, mx, my, game);
        if (playing) turnMarker(g, game, mine, turnColor, pulse);
        // Schiffslisten
        shipList(g, leftX, gy + N * s + 4, setup ? placed : null, sh == null ? List.of() : sh.mySunk);
        shipList(g, rightX, gy + N * s + 4, null, sh == null ? List.of() : sh.theirSunk);
        drawBottom(g, game, mx, my);
    }

    private void sea(GuiGraphics g, int x0, boolean own, List<int[]> fleet, Games.Ships sh, int[] ghost, boolean ghostOk, int mx, int my, Games.Game game) {
        int water = Ui.mix(ChessScreen.card(false) | 0xFF000000, Ui.accent(), 0.12f);
        boolean canShoot = !own && game.myTurn() && pendingShot < 0;
        int hover = canShoot ? cellAt(x0, mx, my) : -1;
        for (int i = 0; i < N; i++) {
            Ui.scaled(g, String.valueOf((char) ('A' + i)), x0 + i * s + s / 2f - 2, gy - 6, 0.6f, muted(), false);
            if (own) Ui.scaled(g, String.valueOf(i + 1), x0 - 9, gy + i * s + s / 2f - 3, 0.6f, muted(), false);
        }
        java.util.Map<Integer, Character> shots = sh == null ? java.util.Map.of() : own ? sh.opShots : sh.myShots;
        List<int[]> sunk = sh == null ? List.of() : own ? sh.mySunk : sh.theirSunk;
        for (int c = 0; c < N * N; c++) {
            int x = x0 + (c % N) * s, y = gy + (c / N) * s;
            int fill = water;
            if (own && has(fleet, c)) fill = 0xFF8C93A6;
            if (!own && sh != null && sh.opFleet != null && has(sh.opFleet, c)) fill = 0xFF8C93A6; // nach dem Ende: gegnerische Flotte
            Character shot = shots.get(c);
            if (shot != null && shot == 'm') fill = Ui.mix(water, 0xFF000000, 0.35f); // Wasser: Feld dunkler
            if (shot != null && shot == 'a') fill = Ui.mix(water, 0xFF000000, 0.2f);
            if (shot != null && shot == 'h') fill = Ui.mix(water, Ui.RED, 0.9f);
            if (has(sunk, c)) fill = Ui.mix(0xFF000000, Ui.RED, 0.45f);
            if (ghost != null && contains(ghost, c)) fill = ghostOk ? Ui.mix(water, ChessScreen.highlight(), 0.55f) : Ui.mix(water, Ui.RED, 0.55f);
            if (c == hover && shot == null && !has(sunk, c)) fill = Ui.mix(water, ChessScreen.highlight(), 0.45f);
            if (!own && c == pendingShot) fill = Ui.mix(water, 0xFFFFFFFF, 0.35f);
            g.fill(x + 1, y + 1, x + s, y + s, fill);
            if (shot != null) {
                int cx = x + s / 2 + 1, cy = y + s / 2 + 1;
                if (shot == 'h') XoScreen.symbol(g, 'X', cx, cy, s * 0.7f, 0xFFFFFFFF);
                else if (shot == 'm') disc(g, cx, cy, Math.max(2, s / 4), 0xF0FFFFFF); // Wasser: großer heller Punkt
                else disc(g, cx, cy, Math.max(1, s / 8), 0x66FFFFFF); // automatisch markiert: klein und blass
            }
            // letzter Schuss: erst aufblinken, dann dicker gelber Rahmen bis zum nächsten Schuss
            if (sh != null && sh.lastCell == c && sh.lastByMe != own) {
                long since = System.currentTimeMillis() - flashAt;
                if (flashCell == c && since < 1200 && (since / 150) % 2 == 0) g.fill(x + 1, y + 1, x + s, y + s, 0x99FFFFFF);
                Ui.outline(g, x, y, s + 1, s + 1, 0, LAST, 0x00000000);
                Ui.outline(g, x + 1, y + 1, s - 1, s - 1, 0, LAST, 0x00000000);
            }
        }
    }

    /** Gegner am Zug: Orange, damit es sich klar von der eigenen Farbe abhebt */
    private static final int OP_TURN = 0xFFFF9F43;

    /**
     * Zeigt deutlich, wer dran ist: pulsierender Rahmen um das Brett, auf das geschossen wird, das andere Brett abgedunkelt,
     * dazwischen ein Pfeil mit „AM ZUG“ und Name.
     */
    private void turnMarker(GuiGraphics g, Games.Game game, boolean mine, int color, float pulse) {
        int active = mine ? rightX : leftX, idle = mine ? leftX : rightX, size = N * s;
        g.fill(idle + 1, gy + 1, idle + size, gy + size, 0x50000000);
        int edge = Ui.withAlpha(color, Math.round(130 + 125 * pulse));
        Ui.outline(g, active - 3, gy - 2, size + 6, size + 5, 0, edge, 0x00000000);
        Ui.outline(g, active - 2, gy - 1, size + 4, size + 3, 0, edge, 0x00000000);
        // Pfeil in der Lücke zwischen den Brettern, zeigt auf das Ziel-Brett
        int gapL = leftX + size + 4, gapR = rightX - 4, cx = (gapL + gapR) / 2, cy = gy + size / 2;
        int half = Math.max(5, Math.min(10, (gapR - gapL) / 6));
        int off = Math.round((pulse - 0.5f) * 4) * (mine ? 1 : -1);
        for (int i = 0; i <= half; i++) {
            int len = half - i;
            if (mine) g.fill(cx - half / 2 + i + off, cy - len, cx - half / 2 + i + 1 + off, cy + len + 1, color);
            else g.fill(cx + half / 2 - i + off, cy - len, cx + half / 2 - i + 1 + off, cy + len + 1, color);
        }
        String head = "AM ZUG";
        Ui.scaled(g, head, cx - font.width(head) * 0.3f, cy - half - 10, 0.6f, muted(), false);
        String who = mine ? "DU" : font.plainSubstrByWidth(game.opponentName(), Math.round((gapR - gapL) / 0.75f));
        Ui.scaled(g, who, cx - font.width(who) * 0.375f, cy + half + 5, 0.75f, color, false);
    }

    /** Feld als Koordinate, z. B. „C5“ */
    private static String coord(int cell) {
        return (char) ('A' + cell % N) + String.valueOf(cell / N + 1);
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    /** kleine Übersicht der Schiffe: versenkte rot, beim Aufstellen das nächste markiert */
    /** placing = Aufstellung (nur beim Aufstellen, sonst null): gesetzte Schiffe grau, das nächste markiert */
    private void shipList(GuiGraphics g, int x, int y, List<int[]> placing, List<int[]> sunk) {
        boolean setup = placing != null;
        int[] sunkSizes = new int[6], placedSizes = new int[6];
        for (int[] ship : sunk) sunkSizes[Math.min(5, ship.length)]++;
        if (setup) for (int[] ship : placing) placedSizes[Math.min(5, ship.length)]++;
        int next = setup ? nextSize() : 0;
        boolean nextMarked = false;
        int[] seen = new int[6];
        for (int len : SIZES) {
            int nth = seen[len]++;
            boolean down = nth < sunkSizes[len];
            boolean done = setup && nth < placedSizes[len];
            boolean isNext = setup && !done && len == next && !nextMarked;
            if (isNext) nextMarked = true;
            boolean nextShip = isNext;
            int w = len * 5 + 4;
            if (nextShip) Ui.outline(g, x - 1, y - 1, w + 2, 8, 0, ChessScreen.highlight(), 0x00000000);
            for (int i = 0; i < len; i++)
                g.fill(x + 2 + i * 5, y + 1, x + 6 + i * 5, y + 5, down ? Ui.withAlpha(Ui.RED, 170) : setup && !done ? 0x55FFFFFF : 0xFF8C93A6);
            x += w + 4;
        }
    }

    private int[] rotateButton() {
        return new int[]{px + pw / 2 + 4, gy + N * s + 16, 62, 16};
    }

    private int[] randomButton() {
        return new int[]{px + pw / 2 + 70, gy + N * s + 16, 62, 16};
    }

    private int[] readyButton() {
        return new int[]{px + pw / 2 + 136, gy + N * s + 16, Math.max(60, px + pw - 12 - (px + pw / 2 + 136)), 16};
    }

    private void drawBottom(GuiGraphics g, Games.Game game, int mx, int my) {
        Games.Ships sh = game.ships;
        int y = gy + N * s + 14, x = px + 12, w = pw / 2 - 16;
        Ui.rect(g, x, y, w, 28, radius(4), ChessScreen.card(false));
        String state;
        int color = text();
        boolean setup = setup(game);
        if (error() != null) {
            state = error();
            color = Ui.RED;
        } else if (game.invited()) state = "Warte auf Annahme …";
        else if (game.over()) state = Games.reasonText(game.reason);
        else if (setup) {
            if (sh != null && sh.myReady) state = sh.opReady ? "Gleich geht's los …" : "Warte auf " + game.opponentName() + " …";
            else state = nextSize() > 0 ? "Setze: " + shipName(nextSize()) + " (" + nextSize() + ")" : "Flotte steht – „Bereit“";
        } else {
            String last = "";
            if (sh != null && sh.lastCell >= 0) {
                String who = sh.lastByMe ? "Du: " : game.opponentName() + ": ";
                last = who + coord(sh.lastCell) + " – " + (sh.lastResult == 's' ? "versenkt! " : sh.lastResult == 'h' ? "Treffer! " : "Wasser ");
            }
            state = last + (game.myTurn() ? "· du schießt" : "· " + game.opponentName() + " zielt");
            color = game.myTurn() ? ChessScreen.highlight() : OP_TURN;
        }
        Ui.text(g, state, x + 6, y + 5, color, false);
        long left = setup && sh != null ? (sh.setupLeft < 0 ? -1 : Math.max(0, sh.setupLeft - (System.currentTimeMillis() - game.fetchedAt))) : game.moveLeft();
        long total = setup ? 120_000 : game.moveTime * 1000L;
        if (left >= 0 && total > 0 && game.active()) {
            int bw = w - 12, fill = (int) (bw * Math.min(1.0, left / (double) total));
            g.fill(x + 6, y + 18, x + 6 + bw, y + 22, 0x55000000);
            g.fill(x + 6, y + 18, x + 6 + fill, y + 22, left < 5000 ? Ui.RED : ChessScreen.highlight());
        }
        if (setup && game.active() && (sh == null || !sh.myReady) && !sentFleet) {
            ChessScreen.button(g, rotateButton(), horizontal ? "↻ Drehen" : "↺ Drehen", false, mx, my);
            ChessScreen.button(g, randomButton(), "Zufällig", false, mx, my);
            int[] r = readyButton();
            if (placed.size() == 5) ChessScreen.button(g, r, "Bereit", true, mx, my);
            else {
                Ui.rect(g, r[0], r[1], r[2], r[3], radius(4), ChessScreen.card(false));
                Ui.centered(g, "Bereit", r[0] + r[2] / 2, r[1] + 4, muted(), false);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Eingabe

    @Override
    protected boolean clickGame(Games.Game game, double mx, double my) {
        Games.Ships sh = game.ships;
        if (setup(game) && game.active() && (sh == null || !sh.myReady) && !sentFleet) {
            if (inside(rotateButton(), mx, my)) {
                horizontal = !horizontal;
                return true;
            }
            if (inside(randomButton(), mx, my)) {
                placed.clear();
                placed.addAll(randomFleet());
                return true;
            }
            if (inside(readyButton(), mx, my) && placed.size() == 5) {
                sentFleet = true;
                Games.fleet(id, new ArrayList<>(placed), error -> {
                    sentFleet = false;
                    showError(error);
                });
                return true;
            }
            int at = cellAt(leftX, mx, my);
            if (at >= 0) {
                // auf ein gesetztes Schiff klicken nimmt es wieder weg
                for (int k = placed.size() - 1; k >= 0; k--) {
                    if (contains(placed.get(k), at)) {
                        placed.remove(k);
                        return true;
                    }
                }
                if (nextSize() > 0) {
                    int[] cells = cellsOf(at, nextSize(), horizontal);
                    if (fits(placed, cells)) placed.add(cells);
                    else showError(cells == null ? "Passt nicht aufs Feld" : "Schiffe dürfen sich nicht berühren");
                }
                return true;
            }
            return false;
        }
        if (!game.myTurn() || pendingShot >= 0 || sh == null) return false;
        int at = cellAt(rightX, mx, my);
        if (at < 0) return false;
        if (sh.myShots.containsKey(at)) return true;
        pendingShot = at;
        pendingCount = sh.myShotCount() + 1;
        Games.place(id, at, sh.myShotCount(), error -> {
            pendingShot = -1;
            showError(error);
        });
        return true;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        if (event.key() == InputConstants.KEY_R) {
            horizontal = !horizontal;
            return true;
        }
        return super.onKey(event);
    }
}
