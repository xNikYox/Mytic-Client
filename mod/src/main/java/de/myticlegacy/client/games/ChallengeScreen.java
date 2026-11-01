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

    private static final Row GAME = new Row("SPIEL", new int[]{0, 1}, new String[]{"Schach", "X und O"}, new String[]{"", ""});
    private static final Row CHESS_TIME = new Row("BEDENKZEIT", new int[]{60, 180, 300, 600},
            new String[]{"1 Min", "3 Min", "5 Min", "10 Min"}, new String[]{"Bullet", "Blitz", "", "Schnell"});
    private static final Row CHESS_COLOR = new Row("DEINE FARBE", new int[]{0, 1, 2}, new String[]{"Zufall", "Weiß", "Schwarz"}, new String[]{"50/50", "", ""});
    private static final Row XO_ROUNDS = new Row("RUNDEN", new int[]{1, 3, 5}, new String[]{"1 Runde", "Best of 3", "Best of 5"}, new String[]{"", "", ""});
    private static final Row XO_TIME = new Row("ZEIT PRO ZUG", new int[]{0, 5, 10, 30}, new String[]{"aus", "5 s", "10 s", "30 s"}, new String[]{"", "", "", ""});
    private static final Row XO_SYMBOL = new Row("DEIN SYMBOL", new int[]{0, 1, 2}, new String[]{"Zufall", "X", "O"}, new String[]{"50/50", "beginnt", ""});
    private static final String[] COLORS = {"random", "white", "black"};

    /** letzte Wahl merken (bis zum Neustart): Spiel, Bedenkzeit, Farbe, Runden, Zeit pro Zug, Symbol */
    private static final int[] picked = {0, 2, 0, 1, 2, 0};

    private final String opponent;
    private String status = "";
    private boolean statusError;
    private boolean sending;

    public ChallengeScreen(String opponent) {
        super("Neues Spiel");
        this.opponent = opponent;
    }

    /** Revanche: gleiches Spiel vorauswählen */
    public ChallengeScreen(String opponent, boolean xo) {
        this(opponent);
        picked[0] = xo ? 1 : 0;
    }

    private boolean xo() {
        return picked[0] == 1;
    }

    /** Zeilen für das gewählte Spiel */
    private Row[] rows() {
        return xo() ? new Row[]{GAME, XO_ROUNDS, XO_TIME, XO_SYMBOL} : new Row[]{GAME, CHESS_TIME, CHESS_COLOR};
    }

    /** Stelle in picked[] für eine Zeile */
    private static int pickIndex(Row row) {
        if (row == GAME) return 0;
        if (row == CHESS_TIME) return 1;
        if (row == CHESS_COLOR) return 2;
        if (row == XO_ROUNDS) return 3;
        if (row == XO_TIME) return 4;
        return 5;
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
        boolean xo = xo();
        String settings = xo
                ? XO_ROUNDS.labels()[picked[3]] + ", " + (picked[4] == 0 ? "ohne Zeitlimit" : XO_TIME.labels()[picked[4]] + " pro Zug") + ", " + XO_SYMBOL.labels()[picked[5]]
                : CHESS_TIME.labels()[picked[1]] + ", " + CHESS_COLOR.labels()[picked[2]];
        java.util.function.Consumer<Games.Game> ok = game -> {
            if (game.active()) { // hatte uns schon herausgefordert (z. B. beide „Revanche“) → Spiel läuft sofort
                Games.notice("§d" + opponent + "§7 hatte dich schon herausgefordert – das Spiel beginnt.");
                Compat.setScreen(Games.screenFor(game));
                return;
            }
            Games.notice("§7" + (xo ? "X und O" : "Schach") + "-Herausforderung an §d" + opponent + "§7 gesendet §8(" + settings + ")§7 – wartet bis zu 1 Minute auf Antwort …");
            if (Compat.screen(minecraft) == this) back();
        };
        java.util.function.Consumer<String> error = msg -> {
            sending = false;
            statusError = true;
            status = msg;
        };
        if (xo) Games.inviteXo(opponent, XO_ROUNDS.values()[picked[3]], XO_TIME.values()[picked[4]], COLORS[picked[5]], ok, error);
        else Games.invite(opponent, CHESS_TIME.values()[picked[1]], COLORS[picked[2]], ok, error);
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
