package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;

/**
 * Maus-Klicks eines fremden Bildschirms abfangen (z. B. Anzeigen neben einem Inventar verschieben). Diese Fassung gilt
 * ab 1.21.9 (Klick als Ereignis-Objekt); 1.21 bis 1.21.8 haben eine eigene ScreenMouse.java.
 */
public final class ScreenMouse {
    /** true = Klick verbraucht (der Bildschirm bekommt ihn nicht) */
    public interface Handler {
        boolean down(double x, double y, int button);

        boolean up(double x, double y, int button);
    }

    private ScreenMouse() {
    }

    public static void attach(Screen screen, Handler handler) {
        ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !handler.down(click.x(), click.y(), click.button()));
        ScreenMouseEvents.allowMouseRelease(screen).register((s, click) -> !handler.up(click.x(), click.y(), click.button()));
    }
}
