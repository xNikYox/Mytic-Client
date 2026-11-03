package de.myticlegacy.client.account;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.PanelScreen;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Konto wechseln (nur im Hauptmenü): Konten aus dem Mytic Launcher, Klick wechselt sofort. */
public class AccountScreen extends PanelScreen {
    private static final int ROW_H = 26;
    private final Screen parent;
    private List<Accounts.Account> accounts;
    private String status;
    private boolean statusError;
    private String switching;

    public AccountScreen(Screen parent) {
        super("Konto");
        this.parent = parent;
        load();
    }

    private void load() {
        status = "Konten werden geladen …";
        statusError = false;
        Accounts.list(list -> {
            accounts = list;
            status = list.isEmpty() ? "Im Launcher ist kein Konto eingeloggt" : null;
        }, error -> {
            status = error;
            statusError = true;
        });
    }

    @Override
    protected String title() {
        return "KONTO WECHSELN";
    }

    @Override
    protected void back() {
        Compat.setScreen(parent);
    }

    private String current() {
        return minecraft.getUser().getProfileId().toString().replace("-", "");
    }

    private int[] row(int i) {
        return new int[]{px + 14, py + HEADER + 10 + i * (ROW_H + 4), pw - 28, ROW_H};
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        boolean lunar = Ui.lunar();
        int text = lunar ? Ui.lText() : Ui.TEXT, muted = lunar ? Ui.lMuted() : Ui.MUTED;
        int on = lunar ? Ui.selection() : Ui.accent();
        if (accounts != null) {
            String cur = current();
            for (int i = 0; i < accounts.size(); i++) {
                Accounts.Account a = accounts.get(i);
                int[] r = row(i);
                if (r[1] + ROW_H > py + ph - FOOTER - 4) break;
                boolean active = a.uuid().replace("-", "").equalsIgnoreCase(cur);
                boolean hover = !active && switching == null && Ui.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
                float h = Ui.animate("acc:" + a.uuid(), hover, 16f);
                int card = lunar ? Ui.mix(Ui.L_CARD, Ui.L_CARD_HOVER, h) : Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, h);
                Ui.outline(g, r[0], r[1], r[2], r[3], 4, active ? on : Ui.mix(lunar ? Ui.L_LINE : Ui.LINE, 0x60FFFFFF, h), card);
                // Anfangsbuchstabe als kleines Abzeichen
                Ui.rect(g, r[0] + 5, r[1] + 5, 16, 16, 3, active ? on : 0x40FFFFFF);
                Ui.centered(g, a.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT), r[0] + 13, r[1] + 9, active && Ui.gold() ? 0xFF1A1608 : 0xFFFFFFFF, false);
                Ui.text(g, a.name(), r[0] + 28, r[1] + 9, text, false);
                String right = active ? "Aktiv" : a.uuid().equals(switching) ? "Wechsle …" : hover ? "Wechseln" : "";
                Ui.text(g, right, r[0] + r[2] - 8 - font.width(right), r[1] + 9, active ? on : muted, false);
            }
        }
        String foot = status != null ? status : Accounts.busy() ? "Anmeldung wird übernommen …" : "Klick auf ein Konto wechselt sofort · Konten fügst du im Launcher hinzu";
        Ui.text(g, font.plainSubstrByWidth(foot, pw - 28), px + 14, py + ph - FOOTER + 10, statusError ? Ui.RED : muted, false);
    }

    @Override
    protected boolean clickContent(double mx, double my) {
        if (accounts == null || switching != null) return false;
        String cur = current();
        for (int i = 0; i < accounts.size(); i++) {
            int[] r = row(i);
            if (!Ui.inside(mx, my, r[0], r[1], r[2], r[3])) continue;
            Accounts.Account a = accounts.get(i);
            if (a.uuid().replace("-", "").equalsIgnoreCase(cur)) return true;
            switching = a.uuid();
            status = null;
            statusError = false;
            Accounts.switchTo(a, () -> {
                switching = null;
                status = "Angemeldet als " + a.name();
            }, error -> {
                switching = null;
                status = error;
                statusError = true;
            });
            return true;
        }
        return false;
    }
}
