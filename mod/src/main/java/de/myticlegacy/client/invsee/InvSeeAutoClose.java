package de.myticlegacy.client.invsee;

import de.myticlegacy.client.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * „Letztes /invsee wiederholen“ per Taste: das Inventar öffnet sich unsichtbar, wird erfasst, sobald der Inhalt da
 * ist (spätestens nach 10 Ticks), und sofort wieder geschlossen – das HUD zeigt den neuen Stand.
 */
public final class InvSeeAutoClose {
    private static final int MAX_TICKS = 10;
    private static AbstractContainerScreen<?> screen;
    private static int ticks;

    private InvSeeAutoClose() {
    }

    public static void track(AbstractContainerScreen<?> s) {
        screen = s;
        ticks = 0;
    }

    /** Dieser Bildschirm wird gar nicht gezeichnet (Mixin auf Screen) */
    public static boolean isHidden(Screen s) {
        return s != null && s == screen;
    }

    public static void tick(Minecraft mc) {
        if (screen == null) return;
        if (Compat.screen(mc) != screen || mc.player == null) {
            screen = null;
            return;
        }
        ticks++;
        InvSeeOverlay o = InvSeeOverlay.previous(screen);
        boolean filled = o != null && o.hasForeignItems();
        if ((ticks >= 1 && filled) || ticks >= MAX_TICKS) close(mc, o);
    }

    private static void close(Minecraft mc, InvSeeOverlay o) {
        if (o != null) o.captureForHud();
        screen = null;
        if (mc.player != null) mc.player.closeContainer();
    }
}
