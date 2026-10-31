package de.myticlegacy.client.games;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.Input;
import de.myticlegacy.client.gui.PanelScreen;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;

/** „Neues Spiel“ (/myticgame <name>): Bedenkzeit 3/5/10 Minuten und Farbe Zufall/Weiß/Schwarz, dann herausfordern. */
public class ChallengeScreen extends PanelScreen {
    private static final int[] TIMES = {180, 300, 600};
    private static final String[] TIME_LABEL = {"3 Min", "5 Min", "10 Min"};
    private static final String[] TIME_SUB = {"Blitz", "", "Schnell"};
    private static final String[] COLORS = {"random", "white", "black"};
    private static final String[] COLOR_LABEL = {"Zufall", "Weiß", "Schwarz"};
    /** letzte Wahl merken (bis zum Neustart) */
    private static int timeIndex = 1, colorIndex = 0;

    private final String opponent;
    private String status = "";
    private boolean statusError;
    private boolean sending;

    public ChallengeScreen(String opponent) {
        super("Neues Spiel");
        this.opponent = opponent;
    }

    @Override
    protected String title() {
        return "NEUES SPIEL";
    }

    @Override
    protected int pixelScale() {
        return fit(300, 210, 0.6, 0.6);
    }

    @Override
    protected void layout() {
        super.layout();
        pw = Math.min(vw - 8, 300);
        ph = Math.min(vh - 8, 210);
        px = (vw - pw) / 2;
        py = (vh - ph) / 2;
    }

    private int rowY(int row) {
        return py + HEADER + 10 + row * 44;
    }

    private int[] option(int row, int i) {
        int x0 = px + 12, w = (pw - 24 - 2 * 6) / 3;
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
        Ui.text(g, "Schach gegen", headerX, py + 13, Ui.lunar() ? 0xFF9A9A9A : Ui.MUTED, false);
        Ui.text(g, opponent, headerX + font.width("Schach gegen "), py + 13, Ui.lunar() ? 0xFFFFFFFF : Ui.accent(), false);
        label(g, "BEDENKZEIT", 0);
        for (int i = 0; i < 3; i++) choice(g, option(0, i), TIME_LABEL[i], TIME_SUB[i], i == timeIndex, mouseX, mouseY);
        label(g, "DEINE FARBE", 1);
        for (int i = 0; i < 3; i++) choice(g, option(1, i), COLOR_LABEL[i], i == 0 ? "50/50" : "", i == colorIndex, mouseX, mouseY);
        if (!status.isEmpty()) Ui.text(g, status, px + 12, py + ph - FOOTER + 10, statusError ? Ui.RED : Ui.lunar() ? 0xFF9A9A9A : Ui.MUTED, false);
        ChessScreen.button(g, cancelButton(), "Abbrechen", false, mouseX, mouseY);
        ChessScreen.button(g, sendButton(), sending ? "Sende …" : "Herausfordern", true, mouseX, mouseY);
    }

    private void label(GuiGraphics g, String text, int row) {
        Ui.text(g, text, px + 12, rowY(row), Ui.lunar() ? 0xFF8A8A8A : Ui.MUTED, false);
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
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < 3; i++) {
                int[] r = option(row, i);
                if (Ui.inside(mx, my, r[0], r[1], r[2], r[3])) {
                    if (row == 0) timeIndex = i;
                    else colorIndex = i;
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
        int time = TIMES[timeIndex];
        String color = COLOR_LABEL[colorIndex];
        Games.invite(opponent, time, COLORS[colorIndex], game -> {
            Games.notice("§7Schach-Herausforderung an §d" + opponent + "§7 gesendet §8(" + time / 60 + " Min, " + color + ")§7 – warte auf Antwort …");
            if (Compat.screen(minecraft) == this) back();
        }, error -> {
            sending = false;
            statusError = true;
            status = error;
        });
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
