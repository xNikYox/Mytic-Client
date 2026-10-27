package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Tex;
import net.minecraft.client.gui.GuiGraphics;

import java.nio.file.Path;
import java.util.List;

/** Screenshot-Galerie: alle Screenshots als Kacheln; Klick = groß ansehen, kopieren, im Ordner zeigen, löschen */
public class ScreenshotsScreen extends PanelScreen {
    private static final int CARD_W = 120, CARD_H = 76, GAP = 8;
    private List<Path> shots = Shots.list();
    private Path selected;
    private double scroll;
    private boolean confirmDelete;
    private String note = "";
    private long noteAt;

    public ScreenshotsScreen() {
        super("Screenshots");
    }

    @Override
    protected String title() {
        return "SCREENSHOTS";
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

    private int cols() {
        return Math.max(1, (cw() + GAP) / (CARD_W + GAP));
    }

    private int[] card(int i) {
        int cols = cols(), used = cols * CARD_W + (cols - 1) * GAP, x0 = cx() + (cw() - used) / 2;
        return new int[]{x0 + (i % cols) * (CARD_W + GAP), cy() + (i / cols) * (CARD_H + GAP) - (int) Math.round(scroll)};
    }

    private void show(String text) {
        note = text;
        noteAt = System.currentTimeMillis();
    }

    // ---------- Zeichnen ----------
    @Override
    protected void drawContent(GuiGraphics g, int mx, int my, int headerX) {
        Ui.text(g, shots.size() + (shots.size() == 1 ? " Bild" : " Bilder"), headerX, py + 13, Ui.MUTED, false);
        int fy = py + ph - FOOTER + 7;
        if (selected != null) {
            drawLarge(g, mx, my);
            int x = cx();
            x += button(g, "Kopieren", x, fy, mx, my) + 6;
            x += button(g, "Im Ordner zeigen", x, fy, mx, my) + 6;
            x += button(g, confirmDelete ? "Wirklich löschen?" : "Löschen", x, fy, mx, my) + 6;
            button(g, "‹ Alle Bilder", x, fy, mx, my);
        } else {
            drawGrid(g, mx, my);
            button(g, "Ordner öffnen", cx(), fy, mx, my);
        }
        if (!note.isEmpty() && System.currentTimeMillis() - noteAt < 2500) {
            int w = Ui.font().width(note) + 12;
            Ui.rect(g, px + pw - 10 - w, fy, w, 14, 4, 0xE0181818);
            Ui.text(g, note, px + pw - 4 - w, fy + 3, Ui.accent(), false);
        }
    }

    private void drawGrid(GuiGraphics g, int mx, int my) {
        if (shots.isEmpty()) {
            Ui.centered(g, "Noch keine Screenshots – drück im Spiel F2.", px + pw / 2, cy() + ch() / 2 - 4, Ui.MUTED, false);
            return;
        }
        int rows = (shots.size() + cols() - 1) / cols();
        double max = Math.max(0, rows * (CARD_H + GAP) - GAP - ch());
        scroll = Math.max(0, Math.min(scroll, max));
        for (int i = 0; i < shots.size(); i++) {
            int[] c = card(i);
            if (c[1] < cy() - 1 || c[1] + CARD_H > cy() + ch() + 1) continue; // nur ganz sichtbare Reihen
            boolean hover = Ui.inside(mx, my, c[0], c[1], CARD_W, CARD_H) && my >= cy() && my < cy() + ch();
            Ui.rect(g, c[0] - 1, c[1] - 1, CARD_W + 2, CARD_H + 2, 4, hover ? Ui.accent() : (Ui.lunar() ? Ui.L_LINE : Ui.LINE));
            Ui.rect(g, c[0], c[1], CARD_W, CARD_H, 3, 0xFF101010);
            Shots.Thumb t = Shots.thumb(shots.get(i), Shots.px(CARD_W));
            if (t != null) {
                int h = Math.min(CARD_H, t.h() * CARD_W / t.w());
                Tex.draw(g, t.texture(), c[0], c[1] + (CARD_H - h) / 2, CARD_W, h, t.w(), t.h());
            } else Ui.centered(g, "…", c[0] + CARD_W / 2, c[1] + CARD_H / 2 - 4, Ui.MUTED, false);
        }
    }

    private void drawLarge(GuiGraphics g, int mx, int my) {
        Shots.Thumb t = Shots.thumb(selected, Shots.px(cw()));
        int w = cw(), h = ch();
        if (t != null) {
            int dw = w, dh = t.h() * w / t.w();
            if (dh > h) {
                dh = h;
                dw = t.w() * h / t.h();
            }
            Tex.draw(g, t.texture(), cx() + (w - dw) / 2, cy() + (h - dh) / 2, dw, dh, t.w(), t.h());
        } else Ui.centered(g, "Lädt …", px + pw / 2, cy() + h / 2 - 4, Ui.MUTED, false);
        String name = selected.getFileName().toString();
        Ui.text(g, Ui.font().plainSubstrByWidth(name, pw - 200), px + pw - 14 - Math.min(Ui.font().width(name), pw - 200), py + 13, Ui.MUTED, false);
    }

    private int button(GuiGraphics g, String label, int x, int y, int mx, int my) {
        int w = Ui.font().width(label) + 14;
        boolean h = Ui.inside(mx, my, x, y, w, 14);
        Ui.outline(g, x, y, w, 14, 7, h ? Ui.accent() : 0x40FFFFFF, h ? (Ui.lunar() ? Ui.L_CARD_HOVER : Ui.SURFACE_HOVER) : (Ui.lunar() ? Ui.L_CARD : Ui.SURFACE));
        Ui.centered(g, label, x + w / 2, y + 3, Ui.TEXT, false);
        return w;
    }

    // ---------- Eingabe ----------
    private boolean hit(String label, int x, int y, double mx, double my) {
        return Ui.inside(mx, my, x, y, Ui.font().width(label) + 14, 14);
    }

    @Override
    protected boolean clickContent(double mx, double my) {
        int fy = py + ph - FOOTER + 7;
        if (selected != null) {
            int x = cx();
            String[] labels = {"Kopieren", "Im Ordner zeigen", confirmDelete ? "Wirklich löschen?" : "Löschen", "‹ Alle Bilder"};
            for (int i = 0; i < labels.length; i++) {
                if (hit(labels[i], x, fy, mx, my)) {
                    action(i);
                    return true;
                }
                x += Ui.font().width(labels[i]) + 14 + 6;
            }
            return false;
        }
        if (hit("Ordner öffnen", cx(), fy, mx, my)) {
            Shots.reveal(Shots.dir());
            return true;
        }
        if (my < cy() || my > cy() + ch()) return false;
        for (int i = 0; i < shots.size(); i++) {
            int[] c = card(i);
            if (Ui.inside(mx, my, c[0], c[1], CARD_W, CARD_H)) {
                selected = shots.get(i);
                confirmDelete = false;
                return true;
            }
        }
        return false;
    }

    private void action(int i) {
        switch (i) {
            case 0 -> new Thread(() -> show(Shots.copy(selected) ? "Kopiert ✓" : "Kopieren nicht möglich"), "Mytic-Kopieren").start();
            case 1 -> Shots.reveal(selected);
            case 2 -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    return;
                }
                Shots.delete(selected);
                shots = Shots.list();
                selected = null;
                show("Gelöscht");
            }
            default -> selected = null;
        }
        if (i != 2) confirmDelete = false;
    }

    @Override
    protected boolean scroll(double mouseX, double mouseY, double amount) {
        if (selected != null) return false;
        scroll -= Math.signum(amount) * (CARD_H + GAP);
        return true;
    }

    @Override
    protected void back() {
        if (selected != null) {
            selected = null;
            return;
        }
        super.back();
    }
}
