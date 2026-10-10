package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.friends.Friends;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Freundesliste: online/zuletzt online, Server, Kopf des Skins; Anfragen senden, annehmen, ablehnen. */
public class FriendsScreen extends PanelScreen {
    private static final int ROW_H = 24;
    private static final int ROW_GAP = 3;
    private int tab;
    private String input = "";
    private boolean inputFocused;
    private double scrollPos;
    private double scrollTarget;
    /** Entfernen erst nach zweitem Klick (uuid → Zeitpunkt des ersten Klicks). */
    private final Map<String, Long> armed = new HashMap<>();
    /** Zeichen direkt nach dem Öffnen ignorieren (die Taste "1" aus der Übersicht kommt sonst hier an). */
    private final long openedAt = System.currentTimeMillis();

    public FriendsScreen() {
        super("Freunde");
        Friends.refresh();
    }

    @Override
    protected String title() {
        return "FREUNDE";
    }

    /** Kopf aus 8×8 Pixeln zeichnen; ohne Skin ein Feld mit Anfangsbuchstaben. */
    public static void drawHead(GuiGraphics g, int[] pixels, String name, int x, int y, int size) {
        if (pixels == null) {
            Ui.rect(g, x, y, size, size, 2, Ui.withAlpha(Ui.accent(), 150));
            if (name != null && !name.isEmpty()) Ui.centered(g, name.substring(0, 1).toUpperCase(), x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF, true);
            return;
        }
        float p = size / 8f;
        for (int py = 0; py < 8; py++) {
            for (int px = 0; px < 8; px++) {
                int x1 = x + Math.round(px * p);
                int y1 = y + Math.round(py * p);
                g.fill(x1, y1, x + Math.round((px + 1) * p), y + Math.round((py + 1) * p), pixels[py * 8 + px] | 0xFF000000);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Layout

    private int cx() {
        return px + 10;
    }

    private int cy() {
        return py + HEADER + 8;
    }

    private int cw() {
        return pw - 20;
    }

    private int ch() {
        return ph - HEADER - 8 - FOOTER - 2;
    }

    private int[] inputBox() {
        int w = Math.min(120, Math.max(80, pw / 4));
        return new int[]{px + pw - 12 - 18 - w, py + 9, w, 16};
    }

    private int[] addButton() {
        return new int[]{px + pw - 12 - 16, py + 9, 16, 16};
    }

    private int[] tabX(int headerX) {
        int first = headerX;
        int second = first + font.width(tabLabel(0)) + 14;
        return new int[]{first, second};
    }

    private String tabLabel(int i) {
        if (i == 0) return "Freunde " + Friends.onlineCount() + "/" + Friends.friends().size();
        int n = Friends.incoming().size();
        return n > 0 ? "Anfragen (" + n + ")" : "Anfragen";
    }

    private int lastHeaderX;

    private int contentHeight() {
        if (tab == 0) return Friends.friends().size() * (ROW_H + ROW_GAP);
        return 14 + Math.max(1, Friends.incoming().size()) * (ROW_H + ROW_GAP) + 18 + Math.max(1, Friends.outgoing().size()) * (ROW_H + ROW_GAP);
    }

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentHeight() - ch())));
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        lastHeaderX = headerX;
        boolean lunar = Ui.lunar();
        scrollPos += (scrollTarget - scrollPos) * 0.35;
        clampScroll();

        if (Friends.available()) {
            // Reiter
            int[] tabs = tabX(headerX);
            for (int i = 0; i < 2; i++) {
                String label = tabLabel(i);
                int w = font.width(label);
                boolean active = tab == i;
                boolean hover = Ui.inside(mouseX, mouseY, tabs[i] - 4, py + 8, w + 8, 18);
                float t = Ui.animate("ftab:" + i, active, 14f);
                float h = Ui.animate("ftabh:" + i, hover, 18f);
                if (lunar) {
                    float fillT = Math.max(t, h * 0.4f);
                    if (fillT > 0.01f) Ui.rect(g, tabs[i] - 5, py + 9, w + 10, 15, 4, Ui.withAlpha(0xFFFFFF, Math.round(fillT * 40)));
                    Ui.text(g, label, tabs[i], py + 13, Ui.mix(0xFF9A9A9A, 0xFFFFFFFF, Math.max(t, h * 0.7f)), false);
                } else {
                    Ui.text(g, label, tabs[i], py + 13, Ui.mix(Ui.MUTED, Ui.TEXT, Math.max(t, h * 0.7f)), false);
                    int line = Math.round(w * t);
                    if (line > 0) Ui.hGradient(g, tabs[i] + (w - line) / 2, py + 24, line, 2, Ui.accent(), Ui.accent2());
                }
                if (i == 1 && !Friends.incoming().isEmpty() && !active) Ui.rect(g, tabs[i] + w + 2, py + 10, 4, 4, 2, Ui.RED);
            }

            // Eingabe "Spielername" + Hinzufügen
            int[] ib = inputBox();
            boolean iHover = Ui.inside(mouseX, mouseY, ib[0], ib[1], ib[2], ib[3]);
            if (lunar) Ui.outline(g, ib[0], ib[1], ib[2], ib[3], 4, inputFocused ? 0x90FFFFFF : iHover ? 0x50FFFFFF : Ui.L_LINE, 0xFF1C1C1C);
            else Ui.outline(g, ib[0], ib[1], ib[2], ib[3], 8, inputFocused ? Ui.accent2() : iHover ? Ui.withAlpha(Ui.accent2(), 120) : Ui.LINE, Ui.SURFACE);
            String shown = input.isEmpty() && !inputFocused ? "Spieler hinzufügen …" : input;
            Ui.text(g, font.plainSubstrByWidth(shown, ib[2] - 12), ib[0] + 6, ib[1] + 4, input.isEmpty() ? Ui.MUTED : Ui.TEXT, false);
            if (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int caret = ib[0] + 6 + font.width(input);
                g.fill(caret, ib[1] + 3, caret + 1, ib[1] + 13, Ui.TEXT);
            }
            int[] ab = addButton();
            boolean aHover = Ui.inside(mouseX, mouseY, ab[0], ab[1], ab[2], ab[3]);
            int addColor = lunar ? (aHover ? 0xFF3FC46A : Ui.L_GREEN) : (aHover ? Ui.mix(Ui.accent(), 0xFFFFFFFF, 0.2f) : Ui.accent());
            Ui.rect(g, ab[0], ab[1], ab[2], ab[3], lunar ? 4 : 8, addColor);
            Ui.centered(g, "+", ab[0] + 8, ab[1] + 4, 0xFFFFFFFF, false);
        }

        // Inhalt
        int x = cx();
        int y = cy();
        int w = cw();
        int mid = px + pw / 2;
        int midY = py + HEADER + (ph - HEADER - FOOTER) / 2;
        if (!Friends.available()) {
            Ui.centered(g, "Freunde gibt es nur, wenn du über den Mytic Launcher startest.", mid, midY - 8, Ui.TEXT, false);
            Ui.centered(g, "Dort meldest du dich mit deinem Minecraft-Konto an.", mid, midY + 6, Ui.MUTED, false);
        } else if (!Friends.loaded()) {
            String error = Friends.error();
            Ui.centered(g, error != null ? error : "Freunde werden geladen …", mid, midY - 4, error != null ? Ui.RED : Ui.MUTED, false);
        } else {
            Gfx.scissor(g, x, y - 3, x + w, y + ch());
            if (tab == 0) drawFriends(g, mouseX, mouseY, x, y - (int) Math.round(scrollPos), w);
            else drawRequests(g, mouseX, mouseY, x, y - (int) Math.round(scrollPos), w);
            Gfx.noScissor(g);
            int total = contentHeight();
            if (total > ch()) {
                int barH = Math.max(18, ch() * ch() / total);
                int barY = y + (int) ((ch() - barH) * (scrollPos / (total - ch())));
                Ui.rect(g, px + pw - 6, barY, 3, barH, 1, lunar ? 0x60FFFFFF : Ui.withAlpha(Ui.accent2(), 200));
            }
        }

        // Fußzeile: Rückmeldung links, Online-Status rechts
        int fy = py + ph - FOOTER + 10;
        String message = Friends.message();
        String error = Friends.loaded() ? Friends.error() : null;
        if (message != null) Ui.text(g, font.plainSubstrByWidth(message, pw - 200), px + 12, fy, Friends.messageError() ? Ui.RED : (lunar ? Ui.L_GREEN : Ui.GREEN), false);
        else if (error != null) Ui.text(g, font.plainSubstrByWidth(error, pw - 200), px + 12, fy, Ui.RED, false);
        else if (Friends.me() != null) {
            drawHead(g, Friends.me().head, Friends.me().name, px + 12, fy - 1, 10);
            Ui.text(g, Friends.me().name, px + 26, fy, Ui.MUTED, false);
        }
        if (Friends.available()) {
            boolean visible = MyticClient.friends.enabled();
            String label = visible ? "Online-Status: sichtbar" : "Online-Status: versteckt";
            int lw = font.width(label);
            int lx = px + pw - 12 - 26 - lw;
            Ui.text(g, label, lx, fy, Ui.MUTED, false);
            Ui.toggle(g, "friends:visible", px + pw - 12 - 20, fy - 1, visible);
        }
    }

    private void drawFriends(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        List<Friends.Friend> list = Friends.friends();
        if (list.isEmpty()) {
            Ui.centered(g, "Noch keine Freunde", x + w / 2, y + 26, Ui.TEXT, false);
            Ui.centered(g, "Oben rechts einen Spielernamen eingeben und auf + klicken.", x + w / 2, y + 40, Ui.MUTED, false);
            Ui.centered(g, "Dein Freund muss den Mytic Client einmal gestartet haben.", x + w / 2, y + 52, Ui.MUTED, false);
            return;
        }
        boolean inContent = Ui.inside(mouseX, mouseY, cx(), cy(), cw(), ch());
        for (Friends.Friend f : list) {
            if (y + ROW_H >= cy() - 4 && y <= cy() + ch()) {
                boolean over = inContent && Ui.inside(mouseX, mouseY, x, y, w, ROW_H);
                drawRow(g, f, x, y, w, over);
                String status = f.status();
                int statusColor = f.inGame() ? Ui.TEXT : Ui.MUTED;
                int dot = f.online ? (Ui.lunar() ? Ui.L_GREEN : Ui.GREEN) : 0xFF5A5A5A;
                Ui.rect(g, x + 26, y + 15, 4, 4, 2, dot);
                Ui.scaled(g, font.plainSubstrByWidth(status, Math.round((w - 120) / 0.8f)), x + 33, y + 14, 0.8f, statusColor, false);
                // Entfernen (zweimal klicken)
                boolean isArmed = armed.containsKey(f.uuid) && System.currentTimeMillis() - armed.get(f.uuid) < 4000;
                String label = isArmed ? "Wirklich entfernen?" : "✕";
                int bw = isArmed ? font.width(label) + 10 : 14;
                int bx = x + w - bw - 5;
                boolean bHover = over && Ui.inside(mouseX, mouseY, bx, y + 5, bw, 14);
                if (over || isArmed) {
                    Ui.rect(g, bx, y + 5, bw, 14, 3, isArmed || bHover ? Ui.withAlpha(Ui.RED, isArmed ? 230 : 160) : 0x30FFFFFF);
                    Ui.centered(g, label, bx + bw / 2, y + 8, 0xFFFFFFFF, false);
                }
            }
            y += ROW_H + ROW_GAP;
        }
    }

    private void drawRow(GuiGraphics g, Friends.Friend f, int x, int y, int w, boolean over) {
        if (Ui.lunar()) Ui.rect(g, x, y, w, ROW_H, 4, over ? Ui.L_CARD_HOVER : Ui.L_CARD);
        else Ui.rect(g, x, y, w, ROW_H, 5, over ? Ui.SURFACE_HOVER : Ui.SURFACE);
        drawHead(g, f.head, f.name, x + 4, y + 4, 16);
        Ui.text(g, f.name, x + 26, y + 4, Ui.TEXT, false);
    }

    private void drawRequests(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        boolean inContent = Ui.inside(mouseX, mouseY, cx(), cy(), cw(), ch());
        Ui.scaled(g, "EINGEHEND", x + 2, y + 2, 0.75f, Ui.MUTED, false);
        y += 14;
        if (Friends.incoming().isEmpty()) {
            Ui.text(g, "Keine offenen Anfragen", x + 4, y + 7, Ui.withAlpha(Ui.MUTED, 170), false);
            y += ROW_H + ROW_GAP;
        }
        for (Friends.Friend f : Friends.incoming()) {
            boolean over = inContent && Ui.inside(mouseX, mouseY, x, y, w, ROW_H);
            drawRow(g, f, x, y, w, over);
            Ui.scaled(g, "möchte mit dir befreundet sein", x + 26, y + 14, 0.8f, Ui.MUTED, false);
            button(g, mouseX, mouseY, x + w - 60, y + 5, 26, "✓", Ui.lunar() ? Ui.L_GREEN : Ui.GREEN, inContent);
            button(g, mouseX, mouseY, x + w - 31, y + 5, 26, "✕", Ui.RED, inContent);
            y += ROW_H + ROW_GAP;
        }
        y += 4;
        Ui.scaled(g, "GESENDET", x + 2, y + 2, 0.75f, Ui.MUTED, false);
        y += 14;
        if (Friends.outgoing().isEmpty()) Ui.text(g, "Keine gesendeten Anfragen", x + 4, y + 7, Ui.withAlpha(Ui.MUTED, 170), false);
        for (Friends.Friend f : Friends.outgoing()) {
            boolean over = inContent && Ui.inside(mouseX, mouseY, x, y, w, ROW_H);
            drawRow(g, f, x, y, w, over);
            Ui.scaled(g, "wartet auf Antwort", x + 26, y + 14, 0.8f, Ui.MUTED, false);
            int bw = font.width("Zurückziehen") + 10;
            button(g, mouseX, mouseY, x + w - bw - 5, y + 5, bw, "Zurückziehen", 0xFF6A6A6A, inContent);
            y += ROW_H + ROW_GAP;
        }
    }

    private void button(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String label, int color, boolean inContent) {
        boolean hover = inContent && Ui.inside(mouseX, mouseY, x, y, w, 14);
        Ui.rect(g, x, y, w, 14, 3, hover ? Ui.mix(color, 0xFFFFFFFF, 0.15f) : Ui.withAlpha(color, 200));
        Ui.centered(g, label, x + w / 2, y + 3, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    private void send() {
        String name = input.trim();
        if (name.length() < 3) return;
        Friends.request(name);
        input = "";
    }

    @Override
    protected boolean clickContent(double mx, double my) {
        if (!Friends.available()) return false;
        int[] ib = inputBox();
        inputFocused = Ui.inside(mx, my, ib[0], ib[1], ib[2], ib[3]);
        if (inputFocused) return true;
        int[] ab = addButton();
        if (Ui.inside(mx, my, ab[0], ab[1], ab[2], ab[3])) {
            send();
            return true;
        }
        int[] tabs = tabX(lastHeaderX);
        for (int i = 0; i < 2; i++) {
            if (Ui.inside(mx, my, tabs[i] - 4, py + 8, font.width(tabLabel(i)) + 8, 18)) {
                tab = i;
                scrollPos = scrollTarget = 0;
                return true;
            }
        }
        int fy = py + ph - FOOTER + 10;
        if (Ui.inside(mx, my, px + pw - 12 - 26 - font.width("Online-Status: versteckt"), fy - 3, font.width("Online-Status: versteckt") + 26, 14)) {
            MyticClient.friends.toggle();
            Friends.refresh();
            return true;
        }
        if (!Friends.loaded() || !Ui.inside(mx, my, cx(), cy(), cw(), ch())) return false;
        int x = cx();
        int w = cw();
        int y = cy() - (int) Math.round(scrollPos);
        if (tab == 0) {
            for (Friends.Friend f : Friends.friends()) {
                boolean isArmed = armed.containsKey(f.uuid) && System.currentTimeMillis() - armed.get(f.uuid) < 4000;
                int bw = isArmed ? font.width("Wirklich entfernen?") + 10 : 14;
                if (Ui.inside(mx, my, x + w - bw - 5, y + 5, bw, 14)) {
                    if (isArmed) {
                        armed.remove(f.uuid);
                        Friends.remove(f.uuid);
                    } else {
                        armed.put(f.uuid, System.currentTimeMillis());
                    }
                    return true;
                }
                y += ROW_H + ROW_GAP;
            }
            return false;
        }
        y += 14;
        if (Friends.incoming().isEmpty()) y += ROW_H + ROW_GAP;
        for (Friends.Friend f : Friends.incoming()) {
            if (Ui.inside(mx, my, x + w - 60, y + 5, 26, 14)) {
                Friends.accept(f.uuid);
                return true;
            }
            if (Ui.inside(mx, my, x + w - 31, y + 5, 26, 14)) {
                Friends.deny(f.uuid);
                return true;
            }
            y += ROW_H + ROW_GAP;
        }
        y += 4 + 14;
        int bw = font.width("Zurückziehen") + 10;
        for (Friends.Friend f : Friends.outgoing()) {
            if (Ui.inside(mx, my, x + w - bw - 5, y + 5, bw, 14)) {
                Friends.remove(f.uuid);
                return true;
            }
            y += ROW_H + ROW_GAP;
        }
        return false;
    }

    @Override
    protected boolean scroll(double mouseX, double mouseY, double amount) {
        scrollTarget -= amount * 26;
        clampScroll();
        return true;
    }

    private static boolean nameChar(String s) {
        return s.length() == 1 && (Character.isLetterOrDigit(s.charAt(0)) && s.charAt(0) < 128 || s.charAt(0) == '_');
    }

    @Override
    protected boolean onChar(Input.Typed event) {
        if (!Friends.available() || System.currentTimeMillis() - openedAt < 250) return false;
        String c = event.codepointAsString();
        if (!nameChar(c)) return inputFocused;
        inputFocused = true;
        if (input.length() < 16) input += c;
        return true;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        int key = event.key();
        if (inputFocused) {
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!input.isEmpty()) input = event.hasControlDown() ? "" : input.substring(0, input.length() - 1);
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                send();
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE && !input.isEmpty()) {
                input = "";
                inputFocused = false;
                return true;
            }
        }
        return super.onKey(event);
    }
}
