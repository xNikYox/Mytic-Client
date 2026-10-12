package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.cosmetics.Cosmetics;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.cosmetics.MiniMeCore;
import de.myticlegacy.client.friends.Friends;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Cosmetics: Umhang wählen, mit animierter Vorschau. Andere Mytic-Spieler sehen die Auswahl. */
public class CosmeticsScreen extends PanelScreen {
    private static final int CARD_W = 52;
    private static final int CARD_H = 92;
    private static final int GAP = 5;
    /** Vorschaugröße (Seitenverhältnis des Umhangs 10:16). */
    private static final int PREVIEW_W = 40;
    private static final int PREVIEW_H = 64;
    private double scrollPos;
    private double scrollTarget;
    private static final String[] TABS = {"Umhänge", "MiniMe", "Flügel", "Schilde"};
    /** zuletzt offener Reiter bleibt beim nächsten Öffnen */
    private static int tab;
    /** Stelle, deren MiniMe gerade bearbeitet wird (Index in MiniMeCore.POSITIONS) */
    private static int slot;
    private int lastHeaderX;
    private String input = "";
    private boolean inputFocused;
    private String message;
    private final long openedAt = System.currentTimeMillis();

    public CosmeticsScreen() {
        super("Cosmetics");
    }

    @Override
    protected String title() {
        return "COSMETICS";
    }

    // ------------------------------------------------------------------------------------------ Layout

    private int tabX(int i) {
        int x = lastHeaderX;
        for (int k = 0; k < i; k++) x += font.width(TABS[k]) + 14;
        return x;
    }

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

    private int columns() {
        return Math.max(1, (cw() + GAP) / (CARD_W + GAP));
    }

    /** Karte i (0 = "Keiner"), x/y in virtuellen Koordinaten. */
    private int[] card(int i) {
        int cols = columns();
        int used = cols * CARD_W + (cols - 1) * GAP;
        int x0 = cx() + (cw() - used) / 2;
        return new int[]{x0 + (i % cols) * (CARD_W + GAP), cy() + (i / cols) * (CARD_H + GAP) - (int) Math.round(scrollPos)};
    }

    private int count() {
        return (tab == 2 ? CosmeticsCore.wings().size() : tab == 3 ? CosmeticsCore.shields().size() : CosmeticsCore.capes().size()) + 1;
    }

    private int contentHeight() {
        int rows = (count() + columns() - 1) / columns();
        return rows * (CARD_H + GAP) - GAP;
    }

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentHeight() - ch())));
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        boolean lunar = Ui.lunar();
        lastHeaderX = headerX;
        // Reiter: Umhänge, MiniMe, Flügel
        for (int i = 0; i < TABS.length; i++) {
            int x = tabX(i);
            int tw = font.width(TABS[i]);
            boolean active = tab == i;
            boolean hover = Ui.inside(mouseX, mouseY, x - 4, py + 8, tw + 8, 18);
            float t = Ui.animate("cosTab:" + i, active, 14f);
            float h = Ui.animate("cosTabH:" + i, hover, 18f);
            if (lunar) {
                float fillT = Math.max(t, h * 0.4f);
                if (fillT > 0.01f) Ui.rect(g, x - 5, py + 9, tw + 10, 15, 4, Ui.withAlpha(0xFFFFFF, Math.round(fillT * 40)));
                Ui.text(g, TABS[i], x, py + 13, Ui.mix(0xFF9A9A9A, 0xFFFFFFFF, Math.max(t, h * 0.7f)), false);
            } else {
                Ui.text(g, TABS[i], x, py + 13, Ui.mix(Ui.MUTED, Ui.TEXT, Math.max(t, h * 0.7f)), false);
                int line = Math.round(tw * t);
                if (line > 0) Ui.hGradient(g, x + (tw - line) / 2, py + 24, line, 2, Ui.accent(), Ui.accent2());
            }
        }

        int mid = px + pw / 2;
        int midY = py + HEADER + (ph - HEADER - FOOTER) / 2;
        if (tab == 1) {
            drawMiniMe(g, mouseX, mouseY);
            return;
        }
        if (!CosmeticsCore.available()) {
            Ui.centered(g, "Cosmetics gibt es nur, wenn du über den Mytic Launcher startest.", mid, midY - 8, Ui.TEXT, false);
            Ui.centered(g, "Der Launcher lädt das Cosmetics-Paket beim Spielstart herunter.", mid, midY + 6, Ui.MUTED, false);
            return;
        }
        scrollPos += (scrollTarget - scrollPos) * 0.35;
        clampScroll();

        List<CosmeticsCore.Cape> capes = CosmeticsCore.capes();
        boolean wingTab = tab == 2;
        boolean shieldTab = tab == 3;
        // Flügel und Schilde: Liste aus {id, name, ...}
        List<String[]> wings = shieldTab ? CosmeticsCore.shields() : CosmeticsCore.wings();
        String selected = wingTab ? CosmeticsCore.selectedWings() : shieldTab ? CosmeticsCore.selectedShield() : CosmeticsCore.selected();
        boolean inContent = Ui.inside(mouseX, mouseY, cx(), cy(), cw(), ch());
        Gfx.scissor(g, cx(), cy() - 3, cx() + cw(), cy() + ch());
        for (int i = 0; i < count(); i++) {
            int[] c = card(i);
            if (c[1] + CARD_H < cy() - 4 || c[1] > cy() + ch()) continue;
            CosmeticsCore.Cape cape = i == 0 || wingTab || shieldTab ? null : capes.get(i - 1);
            String[] wing = i == 0 || !(wingTab || shieldTab) ? null : wings.get(i - 1);
            String id = cape != null ? cape.id : wing != null ? wing[0] : null;
            boolean active = id == null ? selected == null : id.equals(selected);
            boolean over = inContent && Ui.inside(mouseX, mouseY, c[0], c[1], CARD_W, CARD_H);
            float hv = Ui.animate("cos:" + i, over, 16f);
            float on = Ui.animate("cosOn:" + i, active, 12f);
            int border = Ui.mix(Ui.mix(lunar ? 0x18FFFFFF : Ui.LINE, 0x60FFFFFF, hv), lunar ? Ui.L_GREEN : Ui.accent(), on);
            int fill = lunar ? Ui.mix(Ui.L_CARD, Ui.L_CARD_HOVER, hv) : Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hv);
            if (!lunar && on > 0.02f) Ui.glow(g, c[0], c[1], CARD_W, CARD_H, 6, Ui.accent(), 4, on * 0.7f);
            Ui.outline(g, c[0], c[1], CARD_W, CARD_H, lunar ? 4 : 6, border, fill);

            int pw2 = PREVIEW_W;
            int ph2 = PREVIEW_H;
            int ix = c[0] + (CARD_W - pw2) / 2;
            int iy = c[1] + 6 - Math.round(hv * 2);
            if (wing != null && shieldTab) {
                // Vorderseite 12x22 Pixel, mittig in der Vorschaufläche
                int sw = ph2 * 12 / 22;
                Cosmetics.drawShieldPreview(g, wing[0], c[0] + (CARD_W - sw) / 2, iy, sw, ph2, over || active);
            } else if (wing != null) {
                int ww = CARD_W - 4;
                int wh = ww * 3 / 5;
                Cosmetics.drawWingPreview(g, wing[0], c[0] + 2, iy + (ph2 - wh) / 2, ww, wh);
            } else if (cape == null) {
                Ui.rect(g, ix, iy, pw2, ph2, 2, 0x30FFFFFF);
                Ui.scaled(g, "✕", ix + pw2 / 2f - font.width("✕"), iy + ph2 / 2f - 8, 2f, Ui.MUTED, false);
            } else if (!Cosmetics.drawPreview(g, cape, ix, iy, pw2, ph2, over || active)) {
                Ui.rect(g, ix, iy, pw2, ph2, 2, 0x30FFFFFF);
                Ui.centered(g, "…", ix + pw2 / 2, iy + ph2 / 2 - 4, Ui.MUTED, false);
            }
            String name = cape != null ? cape.name : wing != null ? wing[1] : wingTab ? "Keine" : "Keiner";
            name = font.plainSubstrByWidth(name, Math.round((CARD_W - 6) / 0.8f));
            Ui.scaled(g, name, c[0] + CARD_W / 2f - font.width(name) * 0.4f, c[1] + CARD_H - 15, 0.8f, active ? 0xFFFFFFFF : Ui.TEXT, false);
            if (active) {
                Ui.rect(g, c[0] + CARD_W - 13, c[1] + 3, 10, 10, 5, lunar ? Ui.L_GREEN : Ui.accent());
                Ui.scaled(g, "✓", c[0] + CARD_W - 10.5f, c[1] + 4.5f, 0.75f, 0xFFFFFFFF, false);
            }
        }
        Gfx.noScissor(g);
        int total = contentHeight();
        if (total > ch()) {
            int barH = Math.max(18, ch() * ch() / total);
            int barY = cy() + (int) ((ch() - barH) * (scrollPos / (total - ch())));
            Ui.rect(g, px + pw - 6, barY, 3, barH, 1, lunar ? 0x60FFFFFF : Ui.withAlpha(Ui.accent2(), 200));
        }

        int fy = py + ph - FOOTER + 10;
        String hint = "F5: Ansicht wechseln";
        int hintW = font.width(hint);
        String info = !Friends.available() ? (wingTab ? "Nur du siehst sie (ohne Mytic Launcher)" : "Nur du siehst ihn (ohne Mytic Launcher)")
                : wingTab ? "Andere Mytic-Spieler sehen deine Flügel" : shieldTab ? "Andere Mytic-Spieler sehen deinen Schild" : "Andere Mytic-Spieler sehen deinen Umhang";
        Ui.text(g, font.plainSubstrByWidth(info, pw - 40 - hintW), px + 12, fy, Ui.MUTED, false);
        Ui.text(g, hint, px + pw - 12 - hintW, fy, Ui.withAlpha(Ui.MUTED, 160), false);
    }

    // ------------------------------------------------------------------------------------------ MiniMe

    private int[] miniInput() {
        return new int[]{cx() + 96, cy() + 26, Math.min(150, cw() - 200), 16};
    }

    private int[] miniButton(int i) {
        int[] in = miniInput();
        int w0 = font.width("Übernehmen") + 14;
        int w1 = font.width("Entfernen") + 14;
        return i == 0 ? new int[]{in[0] + in[2] + 6, in[1], w0, 16} : new int[]{in[0] + in[2] + 6 + w0 + 4, in[1], w1, 16};
    }

    private static String slotPos() {
        return MiniMeCore.POSITIONS[slot][0];
    }

    private int posButtonWidth(int i) {
        return font.width(MiniMeCore.POSITIONS[i][1]) + 14 + (MiniMeCore.selected(MiniMeCore.POSITIONS[i][0]) != null ? 12 : 0);
    }

    /** Knopf für Stelle i (Schulter, Kopf, Arm, Bein) unter der Vorschau. */
    private int[] posButton(int i) {
        int x = cx();
        for (int k = 0; k < i; k++) x += posButtonWidth(k) + 4;
        return new int[]{x, cy() + 124, posButtonWidth(i), 16};
    }

    /** Freunde als Schnellauswahl (Name, Kopf). */
    private List<Friends.Friend> suggestions() {
        List<Friends.Friend> list = new java.util.ArrayList<>(Friends.friends());
        if (list.size() > 8) list = list.subList(0, 8);
        return list;
    }

    private void drawMiniMe(GuiGraphics g, int mouseX, int mouseY) {
        boolean lunar = Ui.lunar();
        int x = cx();
        int y = cy();
        String name = MiniMeCore.selected(slotPos());
        MiniMeCore.Profile profile = MiniMeCore.profile(name);

        // Vorschau: Kopf des gewählten Skins
        Ui.outline(g, x, y, 84, 100, lunar ? 4 : 6, lunar ? 0x30FFFFFF : Ui.LINE, lunar ? Ui.L_CARD : Ui.SURFACE);
        if (name == null) {
            Ui.scaled(g, "✕", x + 42 - font.width("✕"), y + 30, 2f, Ui.MUTED, false);
            Ui.centered(g, "Kein MiniMe", x + 42, y + 82, Ui.MUTED, false);
        } else {
            if (profile != null && profile.head != null) FriendsScreen.drawHead(g, profile.head, name, x + 18, y + 12, 48);
            else Ui.centered(g, MiniMeCore.missing(name) ? "?" : "…", x + 42, y + 32, Ui.MUTED, false);
            String shown = profile != null ? profile.name : name;
            Ui.centered(g, font.plainSubstrByWidth(shown, 78), x + 42, y + 82, Ui.TEXT, false);
        }

        // Eingabe und Knöpfe
        Ui.text(g, "Skin von Spieler", x + 96, y + 12, Ui.MUTED, false);
        int[] in = miniInput();
        boolean iHover = Ui.inside(mouseX, mouseY, in[0], in[1], in[2], in[3]);
        if (lunar) Ui.outline(g, in[0], in[1], in[2], in[3], 4, inputFocused ? 0x90FFFFFF : iHover ? 0x50FFFFFF : Ui.L_LINE, 0xFF1C1C1C);
        else Ui.outline(g, in[0], in[1], in[2], in[3], 8, inputFocused ? Ui.accent2() : iHover ? Ui.withAlpha(Ui.accent2(), 120) : Ui.LINE, Ui.SURFACE);
        String shownInput = input.isEmpty() && !inputFocused ? "Minecraft-Name …" : input;
        Ui.text(g, font.plainSubstrByWidth(shownInput, in[2] - 12), in[0] + 6, in[1] + 4, input.isEmpty() ? Ui.MUTED : Ui.TEXT, false);
        if (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caret = in[0] + 6 + font.width(input);
            g.fill(caret, in[1] + 3, caret + 1, in[1] + 13, Ui.TEXT);
        }
        String[] labels = {"Übernehmen", "Entfernen"};
        for (int i = 0; i < 2; i++) {
            int[] b = miniButton(i);
            boolean hover = Ui.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
            int color = i == 0 ? (lunar ? Ui.L_GREEN : Ui.accent()) : 0xFF555555;
            Ui.rect(g, b[0], b[1], b[2], b[3], lunar ? 4 : 8, hover ? Ui.mix(color, 0xFFFFFFFF, 0.15f) : color);
            Ui.centered(g, labels[i], b[0] + b[2] / 2, b[1] + 4, 0xFFFFFFFF, false);
        }
        String status = message;
        if (status == null && name != null) {
            status = MiniMeCore.missing(name) ? "Den Spieler „" + name + "“ gibt es nicht" : profile == null ? "Skin wird geladen …" : placeText();
        }
        if (status == null) status = "Stelle „" + MiniMeCore.POSITIONS[slot][1] + "“: noch kein MiniMe – Namen eingeben";
        if (status != null) Ui.text(g, font.plainSubstrByWidth(status, cw() - 100), x + 96, y + 50, MiniMeCore.missing(name) ? Ui.RED : Ui.MUTED, false);

        // Schnellauswahl: Freunde
        List<Friends.Friend> list = suggestions();
        if (!list.isEmpty()) {
            Ui.scaled(g, "FREUNDE", x + 96, y + 68, 0.75f, Ui.MUTED, false);
            for (int i = 0; i < list.size(); i++) {
                Friends.Friend f = list.get(i);
                int fx = x + 96 + i * 22;
                boolean hover = Ui.inside(mouseX, mouseY, fx, y + 78, 18, 18);
                if (hover) Ui.rect(g, fx - 1, y + 77, 20, 20, 3, 0x40FFFFFF);
                FriendsScreen.drawHead(g, f.head, f.name, fx + 1, y + 79, 16);
                if (hover) Ui.text(g, f.name, x + 96, y + 100, Ui.TEXT, false);
            }
        }

        // Stellen: jede mit eigenem MiniMe; die gewählte wird oben bearbeitet
        Ui.scaled(g, "STELLE – JE STELLE EIN EIGENER SKIN", x, y + 112, 0.75f, Ui.MUTED, false);
        for (int i = 0; i < MiniMeCore.POSITIONS.length; i++) {
            int[] b = posButton(i);
            boolean on = i == slot;
            String slotName = MiniMeCore.selected(MiniMeCore.POSITIONS[i][0]);
            boolean hover = Ui.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
            float t = Ui.animate("miniPos:" + i, on, 14f);
            int accent = lunar ? Ui.L_GREEN : Ui.accent();
            int fill = Ui.mix(lunar ? Ui.L_CARD : Ui.SURFACE, accent, t);
            if (hover && !on) fill = Ui.mix(fill, 0xFFFFFFFF, 0.08f);
            Ui.outline(g, b[0], b[1], b[2], b[3], lunar ? 4 : 8, Ui.mix(lunar ? Ui.L_LINE : Ui.LINE, accent, t), fill);
            int tx = b[0] + 7;
            if (slotName != null) {
                MiniMeCore.Profile p = MiniMeCore.profile(slotName);
                FriendsScreen.drawHead(g, p != null ? p.head : null, slotName, b[0] + 6, b[1] + 4, 8);
                tx += 12;
            }
            Ui.text(g, MiniMeCore.POSITIONS[i][1], tx, b[1] + 4, on ? 0xFFFFFFFF : Ui.TEXT, false);
        }

        int fy = py + ph - FOOTER + 10;
        String info = Friends.available() ? "Andere Mytic-Spieler sehen deinen MiniMe" : "Nur du siehst ihn (ohne Mytic Launcher)";
        Ui.text(g, font.plainSubstrByWidth(info, pw - 40), px + 12, fy, Ui.MUTED, false);
    }

    private static String placeText() {
        switch (slotPos()) {
            case "head": return "Sitzt auf deinem Kopf";
            case "arm": return "Hängt an deiner linken Hand";
            case "leg": return "Hält sich an deinem rechten Bein fest";
            default: return "Sitzt auf deiner linken Schulter";
        }
    }

    private void applyInput() {
        String name = input.trim();
        if (!name.matches(MiniMeCore.NAME)) {
            message = "Name: 3–16 Zeichen, nur Buchstaben, Zahlen und _";
            return;
        }
        MiniMeCore.select(slotPos(), name);
        message = null;
        input = "";
        inputFocused = false;
    }

    private boolean clickMiniMe(double mx, double my) {
        int[] in = miniInput();
        inputFocused = Ui.inside(mx, my, in[0], in[1], in[2], in[3]);
        if (inputFocused) return true;
        if (Ui.inside(mx, my, miniButton(0)[0], miniButton(0)[1], miniButton(0)[2], miniButton(0)[3])) {
            applyInput();
            return true;
        }
        if (Ui.inside(mx, my, miniButton(1)[0], miniButton(1)[1], miniButton(1)[2], miniButton(1)[3])) {
            MiniMeCore.select(slotPos(), null);
            message = null;
            return true;
        }
        for (int i = 0; i < MiniMeCore.POSITIONS.length; i++) {
            int[] b = posButton(i);
            if (Ui.inside(mx, my, b[0], b[1], b[2], b[3])) {
                slot = i;
                message = null;
                input = "";
                return true;
            }
        }
        List<Friends.Friend> list = suggestions();
        for (int i = 0; i < list.size(); i++) {
            if (Ui.inside(mx, my, cx() + 96 + i * 22, cy() + 78, 18, 18)) {
                MiniMeCore.select(slotPos(), list.get(i).name);
                message = null;
                return true;
            }
        }
        return false;
    }

    private static boolean nameChar(String c) {
        return c.length() == 1 && (Character.isLetterOrDigit(c.charAt(0)) && c.charAt(0) < 128 || c.charAt(0) == '_');
    }

    @Override
    protected boolean onChar(Input.Typed event) {
        if (tab != 1 || System.currentTimeMillis() - openedAt < 250) return false;
        String c = event.codepointAsString();
        if (!nameChar(c)) return inputFocused;
        inputFocused = true;
        if (input.length() < 16) input += c;
        return true;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        int key = event.key();
        if (tab == 1 && inputFocused) {
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!input.isEmpty()) input = event.hasControlDown() ? "" : input.substring(0, input.length() - 1);
                return true;
            }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                applyInput();
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

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean clickContent(double mx, double my) {
        for (int i = 0; i < TABS.length; i++) {
            if (Ui.inside(mx, my, tabX(i) - 4, py + 8, font.width(TABS[i]) + 8, 18)) {
                tab = i;
                inputFocused = false;
                scrollPos = scrollTarget = 0;
                return true;
            }
        }
        if (tab == 1) return clickMiniMe(mx, my);
        if (!CosmeticsCore.available() || !Ui.inside(mx, my, cx(), cy(), cw(), ch())) return false;
        List<CosmeticsCore.Cape> capes = CosmeticsCore.capes();
        for (int i = 0; i < count(); i++) {
            int[] c = card(i);
            if (Ui.inside(mx, my, c[0], c[1], CARD_W, CARD_H)) {
                if (tab == 2) CosmeticsCore.selectWings(i == 0 ? null : CosmeticsCore.wings().get(i - 1)[0]);
                else if (tab == 3) CosmeticsCore.selectShield(i == 0 ? null : CosmeticsCore.shields().get(i - 1)[0]);
                else CosmeticsCore.select(i == 0 ? null : capes.get(i - 1).id);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean scroll(double mouseX, double mouseY, double amount) {
        scrollTarget -= amount * 30;
        clampScroll();
        return true;
    }
}
