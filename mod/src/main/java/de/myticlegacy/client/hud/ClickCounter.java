package de.myticlegacy.client.hud;

import net.minecraft.client.Minecraft;
import de.myticlegacy.client.compat.Compat;

import java.util.ArrayDeque;

/**
 * Zählt Klicks pro Sekunde. Fragt die Angriffs-/Benutzen-Taste jedes Bild ab (keine Mixins nötig) und merkt sich die Zeitpunkte
 * der Klicks der letzten Sekunde. Klicks in Menüs zählen nicht.
 */
public final class ClickCounter {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean leftDown;
    private static boolean rightDown;

    private ClickCounter() {
    }

    public static void poll() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        // über die Tastenbelegung statt direkt über GLFW/SDL: funktioniert in allen Versionen und mit umbelegten Tasten
        boolean left = Compat.screen(mc) == null && mc.options.keyAttack.isDown();
        boolean right = Compat.screen(mc) == null && mc.options.keyUse.isDown();
        if (left && !leftDown) LEFT.addLast(now);
        if (right && !rightDown) RIGHT.addLast(now);
        leftDown = left;
        rightDown = right;
        while (!LEFT.isEmpty() && now - LEFT.peekFirst() > 1000) LEFT.removeFirst();
        while (!RIGHT.isEmpty() && now - RIGHT.peekFirst() > 1000) RIGHT.removeFirst();
    }

    public static int left() {
        return LEFT.size();
    }

    public static int right() {
        return RIGHT.size();
    }
}
