package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.Level;

/**
 * Alles, was sich zwischen Minecraft-Versionen unterscheidet, an einer Stelle.
 * Diese Fassung gilt für 1.21.6 bis 1.21.8; andere Versionen haben eine eigene Compat.java im jeweiligen Build-Projekt.
 */
public final class Compat {
    /** Tastatur-Eingabetyp für Tastenbelegungen. */
    public static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYSYM;

    private Compat() {
    }

    public static Screen optionsScreen(Screen parent) {
        return new OptionsScreen(parent, Minecraft.getInstance().options);
    }

    /** Texteingabe (getippte Zeichen) für einen Bildschirm ein-/ausschalten. Vor 26.x immer aktiv. */
    public static void textInput(Object owner, boolean enabled) {
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

    /** Registriert eine Tastenbelegung in der Kategorie "Mytic Client" (bis 1.21.8: Kategorie als Übersetzungsschlüssel). */
    public static KeyMapping registerKey(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, KEY_TYPE, key, "key.category.myticclient.main"));
    }    public static void drawFace(GuiGraphics g, PlayerInfo info, int x, int y, int size) {
        PlayerFaceRenderer.draw(g, info.getSkin(), x, y, size);
    }
}
