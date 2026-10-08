package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Hauptmenü des Mytic Client: Panorama, großes Logo, abgerundete Knöpfe. */
public class MyticTitleScreen extends Screen {
    private static final String[] BUTTONS = {"Einzelspieler", "Mehrspieler", "Mods & HUD", "Optionen", "Beenden"};
    private final long openedAt = System.currentTimeMillis();

    public MyticTitleScreen() {
        super(Component.literal(MyticClient.NAME));
    }

    private int[] button(int index) {
        int w = 200;
        int h = 22;
        int top = height / 2 - 18;
        if (index == 4) return new int[]{width / 2 - w / 2, top + 4 * (h + 6) + 6, w, h};
        return new int[]{width / 2 - w / 2, top + index * (h + 6), w, h};
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderPanorama(g, delta);
        g.fillGradient(0, 0, width, height, 0x400B0812, 0xC00B0812);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Ui.frame();
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 500f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);

        float logoScale = 3.2f;
        float logoW = font.width("MYTIC CLIENT") * logoScale;
        float logoY = height / 2f - 78 - (1 - ease) * 12;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f - logoW / 2, logoY);
        g.pose().scale(logoScale, logoScale);
        int mytic = font.width("MYTIC ");
        for (int i = 3; i >= 1; i--) g.drawString(font, "MYTIC", i, i, Ui.withAlpha(Ui.accent(), 40), false);
        g.drawString(font, "MYTIC", 0, 0, Ui.alpha(Ui.accent(), ease), false);
        g.drawString(font, "CLIENT", mytic + 1, 1, Ui.alpha(0xFF000000, ease * 0.5f), false);
        g.drawString(font, "CLIENT", mytic, 0, Ui.alpha(0xFFFFFFFF, ease), false);
        g.pose().popMatrix();
        Ui.centered(g, "Minecraft " + SharedConstants.getCurrentVersion().name() + " · Fabric", width / 2, (int) (logoY + 32), Ui.alpha(Ui.MUTED, ease), false);

        for (int i = 0; i < BUTTONS.length; i++) {
            int[] b = button(i);
            boolean hover = Ui.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
            float h = Ui.animate("title:" + i, hover, 14f);
            int base = i == 4 ? 0xB0201420 : 0xB0141022;
            int accent = i == 4 ? Ui.RED : Ui.accent();
            int bg = Ui.mix(base, accent, h * 0.85f);
            Ui.rect(g, b[0] - Math.round(h * 3), b[1], b[2] + Math.round(h * 6), b[3], 11, Ui.alpha(bg, ease));
            Ui.centered(g, BUTTONS[i], width / 2, b[1] + 7, Ui.alpha(0xFFFFFFFF, ease), false);
        }

        String user = minecraft.getUser().getName();
        int uw = font.width(user) + 22;
        Ui.rect(g, width - uw - 8, 8, uw, 16, 8, 0xB0141022);
        Ui.rect(g, width - uw - 1, 13, 6, 6, 3, Ui.GREEN);
        Ui.text(g, user, width - uw + 9, 12, Ui.TEXT, false);

        Ui.text(g, MyticClient.NAME + " " + MyticClient.VERSION, 6, height - 12, Ui.MUTED, false);
        String copyright = "Copyright Mojang AB. Nicht verbreiten!";
        Ui.text(g, copyright, width - font.width(copyright) - 6, height - 12, Ui.MUTED, false);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] b = button(i);
            if (!Ui.inside(event.x(), event.y(), b[0], b[1], b[2], b[3])) continue;
            switch (i) {
                case 0 -> minecraft.setScreen(new SelectWorldScreen(this));
                case 1 -> minecraft.setScreen(new JoinMultiplayerScreen(this));
                case 2 -> minecraft.setScreen(new ModMenuScreen(this));
                case 3 -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
                default -> minecraft.stop();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
