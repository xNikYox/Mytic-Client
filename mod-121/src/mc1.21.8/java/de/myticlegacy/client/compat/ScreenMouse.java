package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;

/** Maus-Klicks eines fremden Bildschirms abfangen. Fassung 1.21 bis 1.21.8 (Koordinaten und Taste einzeln). */
public final class ScreenMouse {
    public interface Handler {
        boolean down(double x, double y, int button);

        boolean up(double x, double y, int button);
    }

    private ScreenMouse() {
    }

    public static void attach(Screen screen, Handler handler) {
        ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !handler.down(x, y, button));
        ScreenMouseEvents.allowMouseRelease(screen).register((s, x, y, button) -> !handler.up(x, y, button));
    }
}
