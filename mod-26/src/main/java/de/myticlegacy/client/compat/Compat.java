package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.Level;

/** Fassung für Minecraft 26.x: Bildschirme liegen in Minecraft#gui, HUD-Sichtbarkeit in Hud, Zeit in der Welt-Uhr. */
public final class Compat {
    private Compat() {
    }

    public static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static boolean hudHidden(Minecraft mc) {
        return mc.gui.hud.isHidden();
    }

    public static long dayTime(Level level) {
        return level.getDefaultClockTime();
    }

    public static KeyMapping registerKey(KeyMapping mapping) {
        return KeyMappingHelper.registerKeyMapping(mapping);
    }

    public static void drawFace(GuiGraphicsExtractor g, PlayerInfo info, int x, int y, int size) {
        PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
    }
}
