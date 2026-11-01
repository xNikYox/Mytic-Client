package de.myticlegacy.client.games;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.Input;
import de.myticlegacy.client.gui.PanelScreen;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * „Neues Spiel“ (/myticgame <name>): Spiel wählen, dann die Einstellungen –
 * Schach: Bedenkzeit 1/3/5/10 Minuten und Farbe; X und O: Runden 1/3/5, Zeit pro Zug aus/5/10/30 s und Symbol.
 * Die Herausforderung verfällt nach einer Minute.
 */
public class ChallengeScreen extends PanelScreen {
    /** eine Auswahlzeile: Überschrift, Werte, Beschriftung, kleine Zeile darunter */
    private record Row(String title, int[] values, String[] labels, String[] subs) {
    }

    /** Spiele (Index = Wert in picked[0]) */
    public static final int CHESS = 0, XO = 1, C4 = 2, SHIPS = 3;
    private static final Row GAME = new Row("SPIEL", new int[]{CHESS, XO, C4, SHIPS}, new String[]{"Schach", "X und O", "Vier", "Schiffe"},
            new String[]{"", "", "gewinnt", "versenken"});
    private static final Row CHESS_TIME = new Row("BEDENKZEIT", new int[]{60, 180, 300, 600},
            new String[]{"1 Min", "3 Min", "5 Min", "10 Min"}, new String[]{"Bullet", "Blitz", "", "Schnell"});
    private static final Row CHESS_COLOR = new Row("DEINE FARBE", new int[]{0, 1, 2}, new String[]{"Zufall", "Weiß", "Schwarz"}, new String[]{"50/50", "", ""});
    private static final Row XO_ROUNDS = new Row("RUNDEN", new int[]{1, 3, 5}, new String[]{"1 Runde", "Best of 3", "Best of 5"}, new String[]{"", "", ""});
    private static final Row XO_TIME = new Row("ZEIT PRO ZUG", new int[]{0, 5, 10, 30}, new String[]{"aus", "5 s", "10 s", "30 s"}, new String[]{"", "", "", ""});
    private static final Row XO_SYMBOL = new Row("DEIN SYMBOL", new int[]{0, 1, 2}, new String[]{"Zufall", "X", "O"}, new String[]{"50/50", "beginnt", ""});
    private static final Row C4_ROUNDS = new Row("RUNDEN", new int[]{1, 3, 5}, new String[]{"1 Runde", "Best of 3", "Best of 5"}, new String[]{"", "", ""});
    private static final Row C4_TIME = new Row("ZEIT PRO ZUG", new int[]{0, 5, 10, 30}, new String[]{"aus", "5 s", "10 s", "30 s"}, new String[]{"", "", "", ""});
    private static final Row C4_START = new Row("WER BEGINNT", new int[]{0, 1, 2}, new String[]{"Zufall", "Ich", "Gegner"}, new String[]{"50/50", "", ""});
    private static final Row SHIP_TIME = new Row("ZEIT PRO SCHUSS", new int[]{0, 10, 20, 30}, new String[]{"aus", "10 s", "20 s", "30 s"}, new String[]{"", "", "", ""});
    private static final Row SHIP_START = new Row("WER SCHIESST ZUERST", new int[]{0, 1, 2}, new String[]{"Zufall", "Ich", "Gegner"}, new String[]{"50/50", "", ""});
    private static final String[] COLORS = {"random", "white", "black"};
    private static final Row[] ALL = {GAME, CHESS_TIME, CHESS_COLOR, XO_ROUNDS, XO_TIME, XO_SYMBOL, C4_ROUNDS, C4_TIME, C4_START, SHIP_TIME, SHIP_START};

    /** letzte Wahl je Zeile merken (bis zum Neustart), Reihenfolge wie ALL */
    private static final int[] picked = {0, 2, 0, 1, 2, 0, 1, 2, 0, 2, 0};

    private final String opponent;
    private String status = "";
    private boolean statusError;
    private boolean sending;

    public ChallengeScreen(String opponent) {
        super("Neues Spiel");
        this.opponent = opponent;
    }

    /** Revanche: dasselbe Spiel vorauswählen (CHESS, XO, C4, SHIPS) */
    public ChallengeScreen(String opponent, int game) {
        this(opponent);
        picked[0] = game;
    }

    private int gameKind() {
        return picked[0];
    }

    /** Zeilen für das gewählte Spiel */
    private Row[] rows() {
        return switch (gameKind()) {
            case XO -> new Row[]{GAME, XO_ROUNDS, XO_TIME, XO_SYMBOL};
            case C4 -> new Row[]{GAME, C4_ROUNDS, C4_TIME, C4_START};
            case SHIPS -> new Row[]{GAME, SHIP_TIME, SHIP_START};
            default -> new Row[]{GAME, CHESS_TIME, CHESS_COLOR};
        };
    }

    /** Stelle in picked[] für eine Zeile */
    private static int pickIndex(Row row) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i] == row) return i;
        return 0;
    }

    private static int value(Row row) {
        return row.values()[picked[pickIndex(row)]];
    }

    private static String label(Row row) {
        return row.labels()[picked[pickIndex(row)]];
    }

    private static String color(Row row) {
        return COLORS[picked[pickIndex(row)]];
    }

    @Override
    protected String title() {
        return "NEUES SPIEL";
    }

    @Override
    protected int pixelScale() {
        return fit(320, 260, 0.6, 0.66);
    }

    @Override
    protected void layout() {
        super.layout();
        pw = Math.min(vw - 8, 320);
        ph = Math.min(vh - 8, 260);
        px = (vw - pw) / 2;
        py = (vh - ph) / 2;
    }

    private int rowY(int row) {
        return py + HEADER + 8 + row * 44;
    }

    private int[] option(int row, int n, int i) {
        int x0 = px + 12, w = (pw - 24 - (n - 1) * 6) / n;
        return new int[]{x0 + i * (w + 6), rowY(row) + 12, w, 24};
    }

    private int[] cancelButton() {
        return new int[]{px + pw - 12 - 92 - 6 - 70, py + ph - FOOTER + 6, 70, 16};
    }

    private int[] sendButton() {
        return new int[]{px + pw - 12 - 92, py + ph - FOOTER + 6, 92, 16};
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        Ui.text(g, "gegen", headerX, py + 13, Ui.lunar() ? 0xFF9A9A9A : Ui.MUTED, false);
        Ui.text(g, opponent, headerX + font.width("gegen "), py + 13, Ui.lunar() ? 0xFFFFFFFF : Ui.accent(), false);
        Row[] rows = rows();
        for (int r = 0; r < rows.length; r++) {
            Row row = rows[r];
            Ui.text(g, row.title(), px + 12, rowY(r), Ui.lunar() ? 0xFF8A8A8A : Ui.MUTED, false);
            int n = row.values().length, sel = picked[pickIndex(row)];
            for (int i = 0; i < n; i++) choice(g, option(r, n, i), row.labels()[i], row.subs()[i], i == sel, mouseX, mouseY);
        }
        String hint = !status.isEmpty() ? status : "verfällt nach 1 Minute";
        Ui.text(g, hint, px + 12, py + ph - FOOTER + 10, statusError ? Ui.RED : Ui.lunar() ? 0xFF9A9A9A : Ui.MUTED, false);
        ChessScreen.button(g, cancelButton(), "Abbrechen", false, mouseX, mouseY);
        ChessScreen.button(g, sendButton(), sending ? "Sende …" : "Herausfordern", true, mouseX, mouseY);
    }

    private void choice(GuiGraphics g, int[] r, String title, String sub, boolean on, int mx, int my) {
        boolean hover = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
        int radius = Ui.square() ? 0 : 4;
        int hi = ChessScreen.highlight();
        int fill = on ? Ui.mix(ChessScreen.card(false), hi, 0.18f) : ChessScreen.card(hover);
        Ui.rect(g, r[0], r[1], r[2], r[3], radius, fill);
        if (on) Ui.outline(g, r[0], r[1], r[2], r[3], radius, hi, fill);
        int textColor = on ? 0xFFFFFFFF : Ui.lunar() ? 0xFFB0B0B0 : Ui.MUTED;
        if (sub.isEmpty()) Ui.centered(g, title, r[0] + r[2] / 2, r[1] + 8, textColor, false);
        else {
            Ui.centered(g, title, r[0] + r[2] / 2, r[1] + 3, textColor, false);
            Ui.scaled(g, sub, r[0] + r[2] / 2f - font.width(sub) * 0.75f / 2, r[1] + 14, 0.75f, Ui.lunar() ? 0xFF8A8A8A : Ui.MUTED, false);
        }
    }

    @Override
    protected boolean clickContent(double mx, double my) {
        Row[] rows = rows();
        for (int r = 0; r < rows.length; r++) {
            int n = rows[r].values().length;
            for (int i = 0; i < n; i++) {
                int[] o = option(r, n, i);
                if (Ui.inside(mx, my, o[0], o[1], o[2], o[3])) {
                    picked[pickIndex(rows[r])] = i;
                    status = "";
                    return true;
                }
            }
        }
        int[] c = cancelButton();
        if (Ui.inside(mx, my, c[0], c[1], c[2], c[3])) {
            back();
            return true;
        }
        int[] s = sendButton();
        if (Ui.inside(mx, my, s[0], s[1], s[2], s[3])) {
            send();
            return true;
        }
        return false;
    }

    private void send() {
        if (sending) return;
        sending = true;
        statusError = false;
        status = "Herausforderung wird gesendet …";
        int kind = gameKind();
        String name = GAME.labels()[kind] + (GAME.subs()[kind].isEmpty() ? "" : " " + GAME.subs()[kind]);
        String settings = switch (kind) {
            case XO -> label(XO_ROUNDS) + ", " + (value(XO_TIME) == 0 ? "ohne Zeitlimit" : label(XO_TIME) + " pro Zug") + ", " + label(XO_SYMBOL);
            case C4 -> label(C4_ROUNDS) + ", " + (value(C4_TIME) == 0 ? "ohne Zeitlimit" : label(C4_TIME) + " pro Zug") + ", beginnt: " + label(C4_START);
            case SHIPS -> (value(SHIP_TIME) == 0 ? "ohne Zeitlimit" : label(SHIP_TIME) + " pro Schuss") + ", zuerst: " + label(SHIP_START);
            default -> label(CHESS_TIME) + ", " + label(CHESS_COLOR);
        };
        java.util.function.Consumer<Games.Game> ok = game -> {
            if (game.active()) { // hatte uns schon herausgefordert (z. B. beide „Revanche“) → Spiel läuft sofort
                Games.notice("§d" + opponent + "§7 hatte dich schon herausgefordert – das Spiel beginnt.");
                Compat.setScreen(Games.screenFor(game));
                return;
            }
            Games.notice("§7" + name + "-Herausforderung an §d" + opponent + "§7 gesendet §8(" + settings + ")§7 – wartet bis zu 1 Minute auf Antwort …");
            if (Compat.screen(minecraft) == this) back();
        };
        java.util.function.Consumer<String> error = msg -> {
            sending = false;
            statusError = true;
            status = msg;
        };
        switch (kind) {
            case XO -> Games.inviteXo(opponent, value(XO_ROUNDS), value(XO_TIME), color(XO_SYMBOL), ok, error);
            case C4 -> Games.inviteC4(opponent, value(C4_ROUNDS), value(C4_TIME), color(C4_START), ok, error);
            case SHIPS -> Games.inviteShips(opponent, value(SHIP_TIME), color(SHIP_START), ok, error);
            default -> Games.invite(opponent, value(CHESS_TIME), color(CHESS_COLOR), ok, error);
        }
    }

    @Override
    protected boolean onKey(Input.Key event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            send();
            return true;
        }
        return super.onKey(event);
    }

    @Override
    protected void back() {
        Compat.setScreen(null);
    }
}
