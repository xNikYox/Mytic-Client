package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.MyticClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Hauptmenü des Mytic Client: Panorama, Logo, schlanke Knöpfe (Optionen und Beenden nebeneinander). */
public class MyticTitleScreen extends MyticScreen {
    private static final int BW = 190;
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
        int y = top + 58;
        int half = (BW - GAP) / 2;
        return new Button[]{
                new Button("Einzelspieler", x, y, BW, 0),
                new Button("Mehrspieler", x, y + (BH + GAP), BW, 0),
                new Button("Mods & HUD", x, y + 2 * (BH + GAP), BW, 0),
                new Button("Optionen", x, y + 3 * (BH + GAP), half, 0),
                new Button("Beenden", x + half + GAP, y + 3 * (BH + GAP), BW - half - GAP, 1),
        };
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderPanorama(g, delta);
        g.fillGradient(0, 0, width, height, 0x300B0812, 0xB00B0812);
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 450f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);

        // Logo: doppelte Pixelgröße, ein sauberer Schatten, Akzentlinie darunter
        float logoScale = 3f;
        int logoW = Math.round(font.width("MYTIC CLIENT") * logoScale);
        float lx = vw / 2f - logoW / 2f;
        float ly = top + (1 - ease) * 8;
        g.pose().pushMatrix();
        g.pose().translate(lx, ly);
        g.pose().scale(logoScale, logoScale);
        int mytic = font.width("MYTIC ");
        g.drawString(font, "MYTIC", 1, 1, Ui.alpha(0xFF1A0B33, ease), false);
        g.drawString(font, "CLIENT", mytic + 1, 1, Ui.alpha(0xFF15121E, ease), false);
        g.drawString(font, "MYTIC", 0, 0, Ui.alpha(Ui.accent(), ease), false);
        g.drawString(font, "CLIENT", mytic, 0, Ui.alpha(0xFFFFFFFF, ease), false);
        g.pose().popMatrix();
        int lineW = Math.round(logoW * ease * 0.5f);
        Ui.rect(g, vw / 2 - lineW / 2, Math.round(ly + 30), lineW, 2, 1, Ui.alpha(Ui.accent(), ease));
        Ui.centered(g, "Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Fabric", vw / 2, Math.round(ly + 37), Ui.alpha(Ui.MUTED, ease), false);

        for (Button b : buttons()) {
            boolean hover = Ui.inside(mouseX, mouseY, b.x, b.y, b.w, BH);
            float h = Ui.animate("title:" + b.label, hover, 16f);
            int accent = b.danger == 1 ? Ui.RED : Ui.accent();
            int border = Ui.mix(0x40FFFFFF, accent, h);
            int fill = Ui.mix(0xC0120E1C, Ui.withAlpha(accent, 220), h * 0.9f);
            Ui.outline(g, b.x, b.y, b.w, BH, 6, Ui.alpha(border, ease), Ui.alpha(fill, ease));
            Ui.centered(g, b.label, b.x + b.w / 2, b.y + 6, Ui.alpha(0xFFFFFFFF, ease), true);
        }

        String user = minecraft.getUser().getName();
        int uw = font.width(user) + 22;
        Ui.outline(g, vw - uw - 8, 8, uw, 16, 8, 0x40FFFFFF, 0xC0120E1C);
        Ui.rect(g, vw - uw - 1, 13, 6, 6, 3, Ui.GREEN);
        Ui.text(g, user, vw - uw + 9, 12, Ui.TEXT, false);

        Ui.text(g, MyticClient.NAME + " " + MyticClient.VERSION, 6, vh - 12, Ui.MUTED, false);
        String copyright = "Copyright Mojang AB. Nicht verbreiten!";
        Ui.text(g, copyright, vw - font.width(copyright) - 6, vh - 12, Ui.MUTED, false);
    }

    @Override
    protected boolean click(MouseButtonEvent event, boolean doubleClick) {
        for (Button b : buttons()) {
            if (!Ui.inside(event.x(), event.y(), b.x, b.y, b.w, BH)) continue;
            switch (b.label) {
                case "Einzelspieler" -> Compat.setScreen(new SelectWorldScreen(this));
                case "Mehrspieler" -> Compat.setScreen(new JoinMultiplayerScreen(this));
                case "Mods & HUD" -> Compat.setScreen(new ModMenuScreen(this));
                case "Optionen" -> Compat.setScreen(new OptionsScreen(this, minecraft.options));
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
