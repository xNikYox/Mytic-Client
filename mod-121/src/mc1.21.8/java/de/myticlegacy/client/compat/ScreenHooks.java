package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;

/** Maus und Tasten eines fremden Bildschirms abfangen (1.21–1.21.8: Koordinaten und Tastencodes) */
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
        ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !click.click(x, y, button));
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, k, scancode, modifiers) -> !key.key(k, Screen.hasControlDown()));
    }
}
