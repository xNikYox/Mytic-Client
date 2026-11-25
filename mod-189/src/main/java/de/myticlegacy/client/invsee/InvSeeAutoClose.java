package de.myticlegacy.client.invsee;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

/** „Letztes /invsee wiederholen“ (1.8.9): unsichtbar öffnen, erfassen, sofort schließen (spätestens nach 10 Ticks). */
public final class InvSeeAutoClose {
    private static final int MAX_TICKS = 10;
    private static GuiContainer screen;
    private static int ticks;

    private InvSeeAutoClose() {
    }

    public static void track(GuiContainer s) {
        screen = s;
        ticks = 0;
    }

    public static boolean isHidden(GuiScreen s) {
        return s != null && s == screen;
    }

    public static void tick(Minecraft mc) {
        if (screen == null) return;
        if (mc.currentScreen != screen || mc.thePlayer == null) {
            screen = null;
            return;
        }
        ticks++;
        InvSeeOverlay o = InvSeeOverlay.of(screen);
        boolean filled = o != null && o.hasForeignItems();
        if ((ticks >= 1 && filled) || ticks >= MAX_TICKS) {
            if (o != null) o.captureForHud();
            screen = null;
            if (mc.thePlayer != null) mc.thePlayer.closeScreen();
        }
    }
}
