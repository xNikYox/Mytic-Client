package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.Level;

/** Fassung für Minecraft 26.1.x: Bildschirme liegen in Minecraft#gui, HUD-Sichtbarkeit in Hud, Zeit in der Welt-Uhr. */
public final class Compat {
    public static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYSYM;

    private Compat() {
    }

    public static Screen optionsScreen(Screen parent) {
        return new OptionsScreen(parent, Minecraft.getInstance().options, false);
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
        return level.getDefaultClockTime();
    }

    public static KeyMapping registerKey(KeyMapping mapping) {
        return KeyMappingHelper.registerKeyMapping(mapping);
    }

    public static void drawFace(GuiGraphicsExtractor g, PlayerInfo info, int x, int y, int size) {
        PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
    }
}
