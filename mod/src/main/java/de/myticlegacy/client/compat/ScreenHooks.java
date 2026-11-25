package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;

/** Maus und Tasten eines fremden Bildschirms abfangen (1.21.9+ und 26.x: Ereignis-Objekte) */
public final class ScreenHooks {
    private ScreenHooks() {
    }

    public interface Click {
        /** true = Klick verbraucht */
        boolean click(double x, double y, int button);
    }

    public interface Key {
        /** true = Taste verbraucht */
        boolean key(int key, boolean ctrl);
    }

    public static void register(Screen screen, Click click, Key key) {
        ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !click.click(e.x(), e.y(), e.button()));
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !key.key(e.key(), e.hasControlDown()));
    }
}
