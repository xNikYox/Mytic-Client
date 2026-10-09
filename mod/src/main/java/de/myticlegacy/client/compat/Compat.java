package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.Level;

/**
 * Alles, was sich zwischen Minecraft-Versionen unterscheidet, an einer Stelle.
 * Diese Fassung gilt für 1.21.11; andere Versionen haben eine eigene Compat.java im jeweiligen Build-Projekt.
 */
public final class Compat {
    private Compat() {
    }

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static boolean hudHidden(Minecraft mc) {
        return mc.options.hideGui;
    }

    public static long dayTime(Level level) {
        return level.getDayTime();
    }

    public static KeyMapping registerKey(KeyMapping mapping) {
        return KeyBindingHelper.registerKeyBinding(mapping);
    }

    public static void drawFace(GuiGraphics g, PlayerInfo info, int x, int y, int size) {
        PlayerFaceRenderer.draw(g, info.getSkin(), x, y, size);
    }
}
