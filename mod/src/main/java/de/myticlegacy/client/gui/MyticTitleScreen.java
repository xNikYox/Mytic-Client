package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.MyticClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/** Hauptmenü des Mytic Client: Panorama, Logo, schlanke Knöpfe (Optionen und Beenden nebeneinander). */
public class MyticTitleScreen extends MyticScreen {
    private static final int BW = 216;
    private static final int BH = 20;
    private static final int GAP = 5;
    private final long openedAt = System.currentTimeMillis();
    private int top;

    private record Button(String label, int x, int y, int w, int danger) {
    }

    public MyticTitleScreen() {
        super(Component.literal(MyticClient.NAME));
    }

    @Override
    protected int pixelScale() {
        // Inhalt (Logo + Knöpfe) etwa ein Drittel der Breite, höchstens zwei Drittel der Höhe
        return fit(210, 170, large() ? 0.44 : 0.36, 0.7);
    }

    @Override
    protected void layout() {
        int block = 44 + 14 + 4 * (BH + GAP);
        top = Math.max(8, (vh - block) / 2 - 6);
    }

    private Button[] buttons() {
        int x = vw / 2 - BW / 2;
        int y = top + 70;
        int third = (BW - 2 * GAP) / 3;
        int row = y + 2 * (BH + GAP);
        return new Button[]{
                new Button("Einzelspieler", x, y, BW, 0),
                new Button("Mehrspieler", x, y + (BH + GAP), BW, 0),
                new Button("Mods & HUD", x, row, third, 0),
                new Button("Optionen", x + third + GAP, row, third, 0),
                new Button("Beenden", x + 2 * (third + GAP), row, BW - 2 * (third + GAP), 1),
        };
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderPanorama(g, delta);
        g.fillGradient(0, 0, width, height, 0x30000000, 0x90000000);
    }

    /** Mond-Emblem: Sichel in Akzentfarbe mit weichem Schein (Zeile für Zeile, ohne Masken). */
    private void drawEmblem(GuiGraphics g, int cx, int cy, int r, float ease) {
        Ui.glow(g, cx - r, cy - r, 2 * r, 2 * r, r, Ui.accent(), 6, 0.3f * ease);
        int ox = Math.round(r * 0.55f);
        int oy = Math.round(r * 0.35f);
        int ir = Math.round(r * 0.85f);
        int color = Ui.alpha(Ui.mix(0xFFFFFFFF, Ui.accent(), 0.25f), ease);
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(r * r - yy * yy));
            int left = cx - half, right = cx + half;
            double iy = yy + oy;
            if (Math.abs(iy) < ir) {
                int ih = (int) Math.round(Math.sqrt(ir * ir - iy * iy));
                int il = cx + ox - ih, irr = cx + ox + ih;
                if (il > left) g.fill(left, cy + dy, Math.min(il, right), cy + dy + 1, color);
                if (irr < right) g.fill(Math.max(irr, left), cy + dy, right, cy + dy + 1, color);
            } else {
                g.fill(left, cy + dy, right, cy + dy + 1, color);
            }
        }
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 450f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        float ly = top + (1 - ease) * 8;

        // Logo wie bei Lunar: Emblem, darunter schlichter weißer Schriftzug
        drawEmblem(g, vw / 2, Math.round(ly + 13), 13, ease);
        String logo = "MYTIC CLIENT";
        float logoScale = 2.5f;
        int logoW = Math.round(font.width(logo) * logoScale);
        Gfx.push(g);
        Gfx.translate(g, vw / 2f - logoW / 2f, ly + 33);
        Gfx.scale(g, logoScale, logoScale);
        g.drawString(font, logo, 0, 0, Ui.alpha(0xFFFFFFFF, ease), true);
        Gfx.pop(g);
        Ui.centered(g, "Minecraft " + Compat.versionName(), vw / 2, Math.round(ly + 56), Ui.alpha(0xB4FFFFFF, ease), false);

        // Knöpfe: halbtransparent dunkel, heller Rand, beim Überfahren heller
        for (Button b : buttons()) {
            boolean hover = Ui.inside(mouseX, mouseY, b.x, b.y, b.w, BH);
            float h = Ui.animate("title:" + b.label, hover, 16f);
            Ui.rect(g, b.x, b.y, b.w, BH, 2, Ui.alpha(Ui.mix(0x70000000, 0x55FFFFFF, h * 0.45f), ease));
            int line = b.danger == 1 ? Ui.mix(0x50FFFFFF, 0xE0FF6B7D, h) : Ui.mix(0x50FFFFFF, 0xE6FFFFFF, h);
            Ui.border(g, b.x, b.y, b.w, BH, Ui.alpha(line, ease));
            Ui.centered(g, b.label, b.x + b.w / 2, b.y + 6, Ui.alpha(0xFFFFFFFF, ease), true);
        }

        String user = minecraft.getUser().getName();
        int uw = font.width(user) + 22;
        Ui.rect(g, vw - uw - 8, 8, uw, 16, 2, 0x70000000);
        Ui.border(g, vw - uw - 8, 8, uw, 16, 0x50FFFFFF);
        Ui.rect(g, vw - uw - 1, 13, 6, 6, 3, Ui.GREEN);
        Ui.text(g, user, vw - uw + 9, 12, Ui.TEXT, false);

        Ui.text(g, MyticClient.NAME + " " + MyticClient.VERSION, 6, vh - 12, Ui.MUTED, false);
        String copyright = "Copyright Mojang AB. Nicht verbreiten!";
        Ui.text(g, copyright, vw - font.width(copyright) - 6, vh - 12, Ui.MUTED, false);
    }

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        for (Button b : buttons()) {
            if (!Ui.inside(event.x(), event.y(), b.x, b.y, b.w, BH)) continue;
            switch (b.label) {
                case "Einzelspieler" -> Compat.setScreen(new SelectWorldScreen(this));
                case "Mehrspieler" -> Compat.setScreen(new JoinMultiplayerScreen(this));
                case "Mods & HUD" -> Compat.setScreen(new ModMenuScreen(this));
                case "Optionen" -> Compat.setScreen(Compat.optionsScreen(this));
                default -> minecraft.stop();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
