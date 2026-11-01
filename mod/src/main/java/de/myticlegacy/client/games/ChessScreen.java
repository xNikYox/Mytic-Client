package de.myticlegacy.client.games;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Blit;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.FriendsScreen;
import de.myticlegacy.client.gui.Input;
import de.myticlegacy.client.gui.PanelScreen;
import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.module.SimpleModules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Schachbrett (Mytic Games). Sieht aus wie das Mod-Menü (Menü-Stil, Menü-Farbe, Schalter-Farbe, Akzent), Figuren per
 * Ziehen oder Klicken. Züge erscheinen sofort und gehen im Hintergrund an den Friends-Server, der die Regeln prüft.
 * Schließen beendet die Partie nicht – /myticgame öffnet das Brett wieder.
 */
public class ChessScreen extends PanelScreen {
    private static final Identifier PIECES = Identifier.fromNamespaceAndPath("myticclient", "textures/chess/pieces.png");
    private static final String ORDER = "KQRBNP";

    private final String id;
    private boolean userFlip;
    private int sq = 32, bx, by;
    private int selected = -1;
    private boolean dragging, dragMoved;
    private double dragX, dragY;
    /** eigener Zug, bis der Server ihn bestätigt */
    private Chess local;
    private String localUci;
    private int localPly = -1;
    private String status = "";
    private long statusAt;
    private long resignArmed;
    /** Nachfrage „Partie verlassen?“ offen; leaving = bewusst verlassen (dann kein zweites Aufgeben in removed) */
    private boolean confirmLeave, leaving;
    private boolean overlayClosed;
    /** Zugliste in Kurzschreibweise (neu berechnet, wenn sich die Zahl der Züge ändert) */
    private List<String> san = new ArrayList<>();
    private int sanFor = -1;

    public ChessScreen(String id) {
        super("Schach");
        this.id = id;
    }

    public String gameId() {
        return id;
    }

    @Override
    protected String title() {
        return "SCHACH";
    }

    @Override
    protected int pixelScale() {
        return fit(470, 344, 0.95, 0.94);
    }

    @Override
    protected void layout() {
        super.layout();
        pw = Math.min(vw - 8, 470);
        ph = Math.min(vh - 8, 344);
        px = (vw - pw) / 2;
        py = (vh - ph) / 2;
        sq = Math.max(14, Math.min(32, (ph - HEADER - FOOTER - 16) / 8));
        bx = px + 12;
        by = py + HEADER + 8;
    }

    /** Esc, ‹ oder ✕: in einer laufenden Partie erst nachfragen – Verlassen zählt als Aufgeben */
    @Override
    protected void back() {
        Games.Game game = game();
        if (game != null && game.active() && !leaving) {
            confirmLeave = !confirmLeave; // zweites Esc = weiterspielen
            return;
        }
        Compat.setScreen(null);
    }

    private void leave() {
        leaving = true;
        Games.resign(id, true);
        Compat.setScreen(null);
    }

    /** Fenster wird anders geschlossen (Tod, Server-Fenster, Trennung …): Partie gilt als verlassen */
    @Override
    public void removed() {
        Games.Game game = game();
        if (game != null && game.active() && !leaving) {
            leaving = true;
            Games.resign(id, true);
        }
        super.removed();
    }

    // ------------------------------------------------------------------------------------------ Farben (wie Mod-Menü)

    static int highlight() {
        return Ui.lunar() ? (Ui.glass() ? Ui.accent() : Ui.L_GREEN) : Ui.accent();
    }

    static int card(boolean hover) {
        return Ui.lunar() ? (hover ? Ui.L_CARD_HOVER : Ui.L_CARD) : (hover ? Ui.SURFACE_HOVER : Ui.SURFACE);
    }

    private static int text() {
        return Ui.lunar() ? 0xFFFFFFFF : Ui.TEXT;
    }

    private static int muted() {
        return Ui.lunar() ? 0xFF9A9A9A : Ui.MUTED;
    }

    private static int radius(int r) {
        return Ui.square() ? 0 : r;
    }

    /** Feldfarben: dunkle Felder nehmen Menü-Farbe bzw. Akzent an */
    private static int[] squares() {
        int menu = MyticClient.theme != null ? MyticClient.theme.menuColor.get() | 0xFF000000 : SimpleModules.Theme.MENU_COLOR_DEFAULT;
        boolean custom = menu != SimpleModules.Theme.MENU_COLOR_DEFAULT;
        if (!Ui.lunar()) return new int[]{Ui.mix(0xFFCFC6E8, Ui.accent(), 0.12f), Ui.mix(0xFF2A1D4A, Ui.accent(), 0.35f)};
        int tint = Ui.glass() ? Ui.accent() : custom ? menu : 0xFF6E6880;
        return new int[]{Ui.mix(0xFFDDD7C9, tint, 0.08f), Ui.mix(0xFF5F5A6E, tint, custom || Ui.glass() ? 0.45f : 0.15f)};
    }

    static void button(GuiGraphics g, int[] r, String label, boolean primary, int mx, int my) {
        boolean hover = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
        int fill = primary ? (hover ? Ui.mix(highlight(), 0xFFFFFFFF, 0.12f) : highlight()) : card(hover);
        Ui.rect(g, r[0], r[1], r[2], r[3], radius(4), fill);
        Ui.centered(g, label, r[0] + r[2] / 2, r[1] + (r[3] - 8) / 2, primary ? 0xFFFFFFFF : text(), false);
    }

    private static void piece(GuiGraphics g, char p, int x, int y, int size) {
        int col = ORDER.indexOf(Character.toUpperCase(p));
        if (col < 0) return;
        // Figuren „cburnett“ (Colin M.L. Burnett, BSD-Lizenz) als 128-px-Bilder, weich skaliert (pieces.png.mcmeta)
        Blit.region(g, PIECES, x, y, size, size, col * 128, Chess.white(p) ? 0 : 128, 128, 128, 768, 256);
    }

    // ------------------------------------------------------------------------------------------ Stellung

    private Games.Game game() {
        return Games.get(id);
    }

    /** Stellung, die gezeigt wird: eigener Zug schon ausgeführt, solange der Server ihn noch nicht bestätigt hat */
    private Chess position(Games.Game g) {
        if (local != null && g.moves.size() < localPly) return local;
        local = null;
        localPly = -1;
        return g.position();
    }

    private List<String> moves(Games.Game g) {
        List<String> list = new ArrayList<>(g.moves);
        if (local != null && g.moves.size() < localPly) list.add(localUci);
        return list;
    }

    private List<String> sanList(List<String> moves) {
        if (sanFor == moves.size()) return san;
        List<String> out = new ArrayList<>();
        Chess c = Chess.start();
        for (String u : moves) {
            Chess.Move m = c.parse(u);
            if (m == null) break;
            out.add(c.san(m));
            c = c.apply(m);
        }
        san = out;
        sanFor = moves.size();
        return out;
    }

    private boolean flipped(Games.Game g) {
        return !g.meWhite() ^ userFlip;
    }

    private int squareAt(double mx, double my, boolean flipped) {
        if (mx < bx || my < by || mx >= bx + 8 * sq || my >= by + 8 * sq) return -1;
        int v = (int) ((my - by) / sq) * 8 + (int) ((mx - bx) / sq);
        return flipped ? 63 - v : v;
    }

    private int[] squarePos(int i, boolean flipped) {
        int v = flipped ? 63 - i : i;
        return new int[]{bx + (v & 7) * sq, by + (v >> 3) * sq};
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        Games.Game game = game();
        if (game == null) {
            Ui.centered(g, "Partie wird geladen …", px + pw / 2, py + ph / 2 - 4, muted(), false);
            return;
        }
        Ui.text(g, "gegen " + game.opponentName() + " · " + game.time / 60 + " Min", headerX, py + 13, muted(), false);
        boolean flipped = flipped(game);
        Chess pos = position(game);
        List<String> moves = moves(game);
        drawBoard(g, game, pos, moves, flipped, mouseX, mouseY);
        drawSide(g, game, pos, moves, mouseX, mouseY);
        drawFooter(g, game, mouseX, mouseY);
        if (game.over() && !overlayClosed) drawResult(g, game, mouseX, mouseY);
        if (game.invited()) drawWaiting(g, game, mouseX, mouseY);
        if (confirmLeave && game.active()) drawLeave(g, mouseX, mouseY);
        else confirmLeave = false;
        if (dragging && dragMoved && selected >= 0 && pos.board[selected] != 0) {
            int size = sq + 4;
            piece(g, pos.board[selected], (int) dragX - size / 2, (int) dragY - size / 2, size);
        }
    }

    private void drawBoard(GuiGraphics g, Games.Game game, Chess pos, List<String> moves, boolean flipped, int mx, int my) {
        int[] colors = squares();
        Ui.rect(g, bx - 2, by - 2, 8 * sq + 4, 8 * sq + 4, radius(4), 0x66000000);
        int from = -1, to = -1;
        if (!moves.isEmpty()) {
            String last = moves.get(moves.size() - 1);
            from = Chess.index(last.substring(0, 2));
            to = Chess.index(last.substring(2, 4));
        }
        int check = game.active() && pos.inCheck() ? pos.king(pos.whiteToMove) : -1;
        List<Integer> targets = new ArrayList<>();
        if (selected >= 0 && game.myTurn()) for (Chess.Move m : pos.legal()) {
            if (m.from() != selected) continue;
            if (!targets.contains(m.to())) targets.add(m.to());
            // Rochade: Turm ist ebenfalls Ziel (König auf den Turm ziehen)
            if (Character.toUpperCase(pos.board[m.from()]) == 'K' && Math.abs(m.to() - m.from()) == 2) targets.add(m.to() > m.from() ? m.from() + 3 : m.from() - 4);
        }
        int hover = dragging && dragMoved ? squareAt(mx, my, flipped) : -1;
        int hi = highlight();
        for (int i = 0; i < 64; i++) {
            int[] p = squarePos(i, flipped);
            boolean light = ((i >> 3) + (i & 7)) % 2 == 0;
            g.fill(p[0], p[1], p[0] + sq, p[1] + sq, light ? colors[0] : colors[1]);
            if (i == from || i == to) g.fill(p[0], p[1], p[0] + sq, p[1] + sq, Ui.withAlpha(Ui.accent(), 105));
            if (i == selected) g.fill(p[0], p[1], p[0] + sq, p[1] + sq, Ui.withAlpha(hi, 150));
            if (i == check) {
                g.fill(p[0], p[1], p[0] + sq, p[1] + sq, 0x88FF3B47);
                g.fill(p[0] + 3, p[1] + 3, p[0] + sq - 3, p[1] + sq - 3, 0x55FF3B47);
            }
            if (i == hover) Ui.outline(g, p[0], p[1], sq, sq, 0, 0xDDFFFFFF, 0x00000000);
            // Koordinaten am Rand
            int coord = light ? colors[1] : colors[0];
            if ((flipped ? 7 - (i & 7) : (i & 7)) == 0) Ui.scaled(g, String.valueOf((char) ('8' - (i >> 3))), p[0] + 1.5f, p[1] + 1.5f, 0.6f, coord, false);
            if ((flipped ? 7 - (i >> 3) : (i >> 3)) == 7) Ui.scaled(g, String.valueOf((char) ('a' + (i & 7))), p[0] + sq - 4.5f, p[1] + sq - 6f, 0.6f, coord, false);
            char piece = pos.board[i];
            if (piece != 0 && !(dragging && dragMoved && i == selected)) piece(g, piece, p[0], p[1], sq);
            if (targets.contains(i)) {
                int d = Math.max(4, sq / 4);
                if (piece != 0) Ui.outline(g, p[0] + 1, p[1] + 1, sq - 2, sq - 2, radius(sq / 2), 0x66000000, 0x00000000);
                else Ui.rect(g, p[0] + (sq - d) / 2, p[1] + (sq - d) / 2, d, d, radius(d / 2), 0x55141418);
            }
        }
    }

    private void drawSide(GuiGraphics g, Games.Game game, Chess pos, List<String> moves, int mx, int my) {
        int sx = bx + 8 * sq + 12, sw = px + pw - 12 - sx;
        boolean meWhite = game.meWhite();
        int boardBottom = by + 8 * sq;
        player(g, game, sx, by, sw, game.opponentName(), !meWhite, pos, false);
        player(g, game, sx, boardBottom - 30, sw, minecraft.getUser().getName(), meWhite, pos, true);
        // Status
        int y = by + 36;
        String state;
        int stateColor = text();
        if (System.currentTimeMillis() - statusAt < 5000 && !status.isEmpty()) {
            state = status;
            stateColor = Ui.RED;
        } else if (game.invited()) state = "Warte auf Annahme …";
        else if (game.over()) state = Games.reasonText(game.reason);
        else if (game.myTurn()) state = pos.inCheck() ? "Schach! Du bist am Zug" : "Du bist am Zug";
        else state = game.opponentName() + " ist am Zug";
        if (game.active() && game.drawOffer != null && !game.drawOffer.equals(Games.myUuid())) {
            state = game.opponentName() + " bietet Remis an";
            stateColor = highlight();
        }
        if (game.myTurn() && game.active()) Ui.rect(g, sx, y + 2, 4, 4, radius(2), highlight());
        Ui.text(g, state, sx + (game.myTurn() && game.active() ? 8 : 0), y, stateColor, false);
        // Zugliste (die letzten Züge, zwei pro Zeile)
        List<String> list = sanList(moves);
        int top = y + 14, bottom = boardBottom - 36, rows = Math.max(1, (bottom - top) / 10);
        Ui.rect(g, sx, top - 2, sw, bottom - top + 2, radius(4), card(false));
        int totalRows = (list.size() + 1) / 2, first = Math.max(0, totalRows - rows);
        for (int r = first; r < totalRows; r++) {
            int ry = top + 1 + (r - first) * 10;
            Ui.text(g, (r + 1) + ".", sx + 4, ry, muted(), false);
            for (int c = 0; c < 2; c++) {
                int k = r * 2 + c;
                if (k >= list.size()) break;
                int cx = sx + 24 + c * ((sw - 26) / 2);
                if (k == list.size() - 1) Ui.rect(g, cx - 2, ry - 1, (sw - 26) / 2 - 2, 10, radius(2), card(true));
                Ui.text(g, list.get(k), cx, ry, text(), false);
            }
        }
    }

    private void player(GuiGraphics g, Games.Game game, int x, int y, int w, String name, boolean white, Chess pos, boolean me) {
        boolean turn = game.active() && (pos.whiteToMove == white);
        Ui.rect(g, x, y, w, 30, radius(4), card(false));
        if (turn) Ui.rect(g, x, y, 2, 30, 0, highlight());
        // Skin-Kopf, wenn der Spieler auf demselben Server ist (Tab-Liste), sonst Anfangsbuchstabe
        var connection = minecraft.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(name);
        if (info != null) Compat.drawFace(g, info, x + 6, y + 7, 16);
        else FriendsScreen.drawHead(g, null, name, x + 6, y + 7, 16);
        Ui.text(g, me ? name + " (du)" : name, x + 27, y + 6, text(), false);
        Ui.rect(g, x + 27, y + 18, 6, 6, radius(3), white ? 0xFFF2F0EA : 0xFF2A2730);
        Ui.outline(g, x + 27, y + 18, 6, 6, radius(3), 0x66000000, white ? 0xFFF2F0EA : 0xFF2A2730);
        // geschlagene Figuren der Gegenseite (aus dem Material berechnet)
        String taken = captured(pos, !white);
        int cx = x + 36;
        for (char p : taken.toCharArray()) {
            if (cx > x + w - 60) break;
            piece(g, p, cx, y + 16, 10);
            cx += 7;
        }
        long ms = game.clock(white);
        String clock = (ms / 60000) + ":" + String.format("%02d", (ms / 1000) % 60);
        int cw = 40;
        boolean low = game.active() && ms < 20000 && turn;
        int fill = low ? Ui.RED : turn ? highlight() : 0x55000000;
        Ui.rect(g, x + w - cw - 4, y + 5, cw, 20, radius(3), fill);
        Ui.scaled(g, clock, x + w - 4 - cw / 2f - font.width(clock) * 1.3f / 2, y + 9.5f, 1.3f, turn || low ? 0xFFFFFFFF : text(), false);
    }

    /** Geschlagene Figuren einer Farbe: was gegenüber der Startaufstellung fehlt */
    private static String captured(Chess pos, boolean whitePieces) {
        int[] start = {1, 1, 2, 2, 2, 8};
        int[] now = new int[6];
        for (char p : pos.board) if (p != 0 && Chess.white(p) == whitePieces) now[ORDER.indexOf(Character.toUpperCase(p))]++;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < 6; i++) for (int k = now[i]; k < start[i]; k++) sb.append(whitePieces ? ORDER.charAt(i) : Character.toLowerCase(ORDER.charAt(i)));
        return sb.toString();
    }

    // Knöpfe unten
    private int[] flipButton() {
        return new int[]{px + pw - 12 - 70 - 6 - 84 - 6 - 50, py + ph - FOOTER + 6, 50, 16};
    }

    private int[] drawButton() {
        return new int[]{px + pw - 12 - 70 - 6 - 84, py + ph - FOOTER + 6, 84, 16};
    }

    private int[] resignButton() {
        return new int[]{px + pw - 12 - 70, py + ph - FOOTER + 6, 70, 16};
    }

    private String drawLabel(Games.Game game) {
        if (game.drawOffer == null) return "Remis anbieten";
        return game.drawOffer.equals(Games.myUuid()) ? "Remis angeboten" : "Remis annehmen";
    }

    private void drawFooter(GuiGraphics g, Games.Game game, int mx, int my) {
        Ui.text(g, "Ziehen oder klicken · Verlassen = aufgeben", px + 12, py + ph - FOOTER + 10, muted(), false);
        button(g, flipButton(), "Drehen", false, mx, my);
        if (!game.active()) return;
        boolean offerIn = game.drawOffer != null && !game.drawOffer.equals(Games.myUuid());
        button(g, drawButton(), drawLabel(game), offerIn, mx, my);
        boolean armed = System.currentTimeMillis() - resignArmed < 3000;
        int[] r = resignButton();
        boolean hover = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
        Ui.rect(g, r[0], r[1], r[2], r[3], radius(4), armed ? Ui.RED : hover ? Ui.withAlpha(Ui.RED, 90) : card(false));
        Ui.centered(g, armed ? "Wirklich?" : "Aufgeben", r[0] + r[2] / 2, r[1] + 4, armed ? 0xFFFFFFFF : 0xFFFF9AA0, false);
    }

    private int[] overlayButton(int i) {
        int w = 70, cx = bx + 4 * sq;
        return new int[]{cx - w - 3 + i * (w + 6), by + 4 * sq + 10, w, 16};
    }

    private void drawResult(GuiGraphics g, Games.Game game, int mx, int my) {
        g.fill(bx, by, bx + 8 * sq, by + 8 * sq, 0x99000000);
        int w = 160, h = 66, x = bx + 4 * sq - w / 2, y = by + 4 * sq - 32;
        Ui.rect(g, x, y, w, h, radius(6), Ui.lunar() ? Ui.L_PANEL | 0xFF000000 : Ui.PANEL | 0xFF000000);
        Ui.outline(g, x, y, w, h, radius(6), Ui.lunar() ? Ui.L_LINE : Ui.LINE, Ui.lunar() ? Ui.L_PANEL | 0xFF000000 : Ui.PANEL | 0xFF000000);
        boolean draw = "1/2-1/2".equals(game.result);
        boolean won = !draw && ("1-0".equals(game.result) == game.meWhite());
        String title = draw ? "Remis" : won ? "Gewonnen!" : "Verloren";
        int color = draw ? text() : won ? highlight() : 0xFFFF8F96;
        Ui.scaled(g, title, x + w / 2f - font.width(title) * 1.6f / 2, y + 7, 1.6f, color, true);
        Ui.centered(g, Games.reasonText(game.reason), x + w / 2, y + 24, muted(), false);
        button(g, overlayButton(0), "Revanche", true, mx, my);
        button(g, overlayButton(1), "Schließen", false, mx, my);
    }

    private int[] leaveButton(int i) {
        int w = 84, cx = bx + 4 * sq;
        return new int[]{cx - w - 3 + i * (w + 6), by + 4 * sq + 8, w, 16};
    }

    private void drawLeave(GuiGraphics g, int mx, int my) {
        g.fill(bx, by, bx + 8 * sq, by + 8 * sq, 0xAA000000);
        int w = 190, h = 62, x = bx + 4 * sq - w / 2, y = by + 4 * sq - 32;
        int panel = Ui.lunar() ? Ui.L_PANEL | 0xFF000000 : Ui.PANEL | 0xFF000000;
        Ui.rect(g, x, y, w, h, radius(6), panel);
        Ui.outline(g, x, y, w, h, radius(6), Ui.lunar() ? Ui.L_LINE : Ui.LINE, panel);
        Ui.centered(g, "Partie verlassen?", x + w / 2, y + 7, text(), true);
        Ui.centered(g, "Dann gewinnt " + game().opponentName() + ".", x + w / 2, y + 19, muted(), false);
        button(g, leaveButton(0), "Weiterspielen", true, mx, my);
        int[] r = leaveButton(1);
        boolean hover = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
        Ui.rect(g, r[0], r[1], r[2], r[3], radius(4), hover ? Ui.RED : Ui.withAlpha(Ui.RED, 120));
        Ui.centered(g, "Verlassen", r[0] + r[2] / 2, r[1] + 4, 0xFFFFFFFF, false);
    }

    private int[] withdrawButton() {
        return new int[]{bx + 4 * sq - 45, by + 4 * sq + 6, 90, 16};
    }

    private void drawWaiting(GuiGraphics g, Games.Game game, int mx, int my) {
        g.fill(bx, by, bx + 8 * sq, by + 8 * sq, 0x99000000);
        String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
        Ui.centered(g, "Warte auf " + game.inviteeName + dots, bx + 4 * sq, by + 4 * sq - 12, 0xFFFFFFFF, true);
        button(g, withdrawButton(), "Zurückziehen", false, mx, my);
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
            else if (inside(leaveButton(1), mx, my)) leave();
            return true;
        }
        if (inside(flipButton(), mx, my)) {
            userFlip = !userFlip;
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
            if (inside(overlayButton(0), mx, my)) Compat.setScreen(new ChallengeScreen(game.opponentName(), ChallengeScreen.CHESS));
            else if (inside(overlayButton(1), mx, my)) overlayClosed = true;
            return true;
        }
        if (game.active()) {
            if (inside(drawButton(), mx, my)) {
                if (game.drawOffer == null) Games.draw(id, "offer");
                else if (!game.drawOffer.equals(Games.myUuid())) Games.draw(id, "accept");
                return true;
            }
            if (inside(resignButton(), mx, my)) {
                if (System.currentTimeMillis() - resignArmed < 3000) {
                    leaving = true; // bewusst aufgegeben: beim Schließen nicht noch einmal
                    Games.resign(id, false);
                }
                else resignArmed = System.currentTimeMillis();
                return true;
            }
        }
        if (!game.active() || !game.myTurn() || local != null) return false;
        boolean flipped = flipped(game);
        int i = squareAt(mx, my, flipped);
        if (i < 0) return false;
        Chess pos = position(game);
        if (selected >= 0 && selected != i && tryMove(game, pos, selected, i)) return true;
        if (pos.board[i] != 0 && Chess.white(pos.board[i]) == game.meWhite()) {
            selected = i;
            dragging = true;
            dragMoved = false;
            dragX = mx;
            dragY = my;
        } else selected = -1;
        return true;
    }

    @Override
    protected boolean drag(Input.Click event, double dx, double dy) {
        if (!dragging) return false;
        dragX = event.x();
        dragY = event.y();
        dragMoved = true;
        return true;
    }

    @Override
    protected boolean release(Input.Click event) {
        if (!dragging) return false;
        dragging = false;
        Games.Game game = game();
        if (!dragMoved || game == null) return true;
        dragMoved = false;
        int to = squareAt(event.x(), event.y(), flipped(game));
        if (to >= 0 && to != selected) tryMove(game, position(game), selected, to);
        return true;
    }

    private boolean tryMove(Games.Game game, Chess pos, int from, int to) {
        Chess.Move m = pos.find(from, to);
        if (m == null) m = castleOntoRook(pos, from, to);
        if (m == null) return false;
        int ply = game.moves.size();
        local = pos.apply(m);
        localUci = m.uci();
        localPly = ply + 1;
        selected = -1;
        Games.move(id, localUci, ply, error -> {
            local = null;
            localPly = -1;
            status = error;
            statusAt = System.currentTimeMillis();
        });
        return true;
    }

    /** Rochade auch so wie in vielen Schach-Apps: König auf den eigenen Turm ziehen oder klicken */
    private static Chess.Move castleOntoRook(Chess pos, int from, int to) {
        char k = pos.board[from], r = pos.board[to];
        if (Character.toUpperCase(k) != 'K' || Character.toUpperCase(r) != 'R' || Chess.white(k) != Chess.white(r) || (from >> 3) != (to >> 3)) return null;
        return pos.find(from, from + (to > from ? 2 : -2));
    }

    private static boolean inside(int[] r, double mx, double my) {
        return Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
    }
}
