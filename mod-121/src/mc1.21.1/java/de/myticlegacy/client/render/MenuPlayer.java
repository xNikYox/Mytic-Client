package de.myticlegacy.client.render;

import net.minecraft.client.gui.GuiGraphics;

/** Spieler-Modell im Hauptmenü: in dieser Minecraft-Version nicht ohne Welt zeichenbar – das Menü zeigt stattdessen den Kopf. */
public final class MenuPlayer {
    public static final int ID = -7_000_001;

    private MenuPlayer() {
    }

    public static boolean supported() {
        return false;
    }

    public static void draw(GuiGraphics g, int x0, int y0, int x1, int y1, float size, float yaw, float pitch) {
    }
}
