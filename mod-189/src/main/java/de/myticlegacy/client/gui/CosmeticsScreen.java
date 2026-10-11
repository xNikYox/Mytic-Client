package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.compat.GuiGraphics;

import java.util.List;

/** Cosmetics: Umhang wählen, mit animierter Vorschau. Andere Mytic-Spieler sehen die Auswahl. */
public class CosmeticsScreen extends PanelScreen {
    private static final int CARD_W = 52;
    private static final int CARD_H = 92;
    private static final int GAP = 5;
    private static final int PREVIEW_SCALE = 2;
    private double scrollPos;
    private double scrollTarget;

    public CosmeticsScreen() {
        super("Cosmetics");
        CosmeticsCore.loadPreviews();
    }

    @Override
    protected String title() {
        return "COSMETICS";
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
        return CosmeticsCore.capes().size() + 1;
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
        // Reiter (weitere Kategorien folgen)
        String tab = "Umhänge";
        int tw = font.width(tab);
        if (lunar) Ui.rect(g, headerX - 5, py + 9, tw + 10, 15, 4, 0x28FFFFFF);
        Ui.text(g, tab, headerX, py + 13, lunar ? 0xFFFFFFFF : Ui.TEXT, false);
        if (!lunar) Ui.hGradient(g, headerX, py + 24, tw, 2, Ui.accent(), Ui.accent2());

        int mid = px + pw / 2;
        int midY = py + HEADER + (ph - HEADER - FOOTER) / 2;
        if (!CosmeticsCore.available()) {
            Ui.centered(g, "Cosmetics gibt es nur, wenn du über den Mytic Launcher startest.", mid, midY - 8, Ui.TEXT, false);
            Ui.centered(g, "Der Launcher lädt das Cosmetics-Paket beim Spielstart herunter.", mid, midY + 6, Ui.MUTED, false);
            return;
        }
        scrollPos += (scrollTarget - scrollPos) * 0.35;
        clampScroll();

        List<CosmeticsCore.Cape> capes = CosmeticsCore.capes();
        String selected = CosmeticsCore.selected();
        boolean inContent = Ui.inside(mouseX, mouseY, cx(), cy(), cw(), ch());
        Gfx.scissor(g, cx(), cy() - 3, cx() + cw(), cy() + ch());
        for (int i = 0; i < count(); i++) {
            int[] c = card(i);
            if (c[1] + CARD_H < cy() - 4 || c[1] > cy() + ch()) continue;
            CosmeticsCore.Cape cape = i == 0 ? null : capes.get(i - 1);
            boolean active = cape == null ? selected == null : cape.id.equals(selected);
            boolean over = inContent && Ui.inside(mouseX, mouseY, c[0], c[1], CARD_W, CARD_H);
            float hv = Ui.animate("cos:" + i, over, 16f);
            float on = Ui.animate("cosOn:" + i, active, 12f);
            int border = Ui.mix(Ui.mix(lunar ? 0x18FFFFFF : Ui.LINE, 0x60FFFFFF, hv), lunar ? Ui.L_GREEN : Ui.accent(), on);
            int fill = lunar ? Ui.mix(Ui.L_CARD, Ui.L_CARD_HOVER, hv) : Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, hv);
            if (!lunar && on > 0.02f) Ui.glow(g, c[0], c[1], CARD_W, CARD_H, 6, Ui.accent(), 4, on * 0.7f);
            Ui.outline(g, c[0], c[1], CARD_W, CARD_H, lunar ? 4 : 6, border, fill);

            int pw2 = CosmeticsCore.PREVIEW_W * PREVIEW_SCALE;
            int ph2 = CosmeticsCore.PREVIEW_H * PREVIEW_SCALE;
            int ix = c[0] + (CARD_W - pw2) / 2;
            int iy = c[1] + 6 - Math.round(hv * 2);
            if (cape == null) {
                Ui.rect(g, ix, iy, pw2, ph2, 2, 0x30FFFFFF);
                Ui.scaled(g, "✕", ix + pw2 / 2f - font.width("✕"), iy + ph2 / 2f - 8, 2f, Ui.MUTED, false);
            } else {
                int[] pixels = cape.previewFrame();
                if (pixels == null) {
                    Ui.rect(g, ix, iy, pw2, ph2, 2, 0x30FFFFFF);
                    Ui.centered(g, "…", ix + pw2 / 2, iy + ph2 / 2 - 4, Ui.MUTED, false);
                } else {
                    for (int y = 0; y < CosmeticsCore.PREVIEW_H; y++) {
                        for (int x = 0; x < CosmeticsCore.PREVIEW_W; x++) {
                            int px1 = ix + x * PREVIEW_SCALE, py1 = iy + y * PREVIEW_SCALE;
                            g.fill(px1, py1, px1 + PREVIEW_SCALE, py1 + PREVIEW_SCALE, pixels[y * CosmeticsCore.PREVIEW_W + x]);
                        }
                    }
                }
            }
            String name = cape == null ? "Keiner" : cape.name;
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
        String info = Friends.available() ? "Andere Mytic-Spieler sehen deinen Umhang" : "Nur du siehst ihn (ohne Mytic Launcher)";
        Ui.text(g, font.plainSubstrByWidth(info, pw - 40 - hintW), px + 12, fy, Ui.MUTED, false);
        Ui.text(g, hint, px + pw - 12 - hintW, fy, Ui.withAlpha(Ui.MUTED, 160), false);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean clickContent(double mx, double my) {
        if (!CosmeticsCore.available() || !Ui.inside(mx, my, cx(), cy(), cw(), ch())) return false;
        List<CosmeticsCore.Cape> capes = CosmeticsCore.capes();
        for (int i = 0; i < count(); i++) {
            int[] c = card(i);
            if (Ui.inside(mx, my, c[0], c[1], CARD_W, CARD_H)) {
                CosmeticsCore.select(i == 0 ? null : capes.get(i - 1).id);
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
