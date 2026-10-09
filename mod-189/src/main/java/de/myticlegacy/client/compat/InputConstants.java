package de.myticlegacy.client.compat;

import org.lwjgl.input.Keyboard;

/** Tastencodes mit den Namen der neuen Versionen (1.8.9 nutzt LWJGL-2-Codes). */
public final class InputConstants {
    public static final int KEY_ESCAPE = Keyboard.KEY_ESCAPE;
    public static final int KEY_BACKSPACE = Keyboard.KEY_BACK;
    public static final int KEY_RSHIFT = Keyboard.KEY_RSHIFT;
    public static final int KEY_C = Keyboard.KEY_C;
    public static final int KEY_LALT = Keyboard.KEY_LMENU;
    public static final int KEY_A = Keyboard.KEY_A;
    public static final int KEY_F = Keyboard.KEY_F;

    private InputConstants() {
    }
}
