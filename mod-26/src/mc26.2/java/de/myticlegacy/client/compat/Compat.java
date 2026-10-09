package de.myticlegacy.client.compat;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/** Fassung für Minecraft 26.2: Bildschirme liegen in Minecraft#gui, HUD-Sichtbarkeit in Hud, Zeit in der Welt-Uhr. */
public final class Compat {
    public static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYSYM;

    private Compat() {
    }

    public static Screen optionsScreen(Screen parent) {
        return new OptionsScreen(parent, Minecraft.getInstance().options, false);
    }

    /** 26.x (SDL): Zeichen kommen nur an, wenn ein Bildschirm die Texteingabe anfordert. */
    public static void textInput(GuiEventListener owner, boolean enabled) {
        Minecraft.getInstance().onTextInputFocusChange(owner, enabled);
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

    private static KeyMapping.Category category;

    /** Registriert eine Tastenbelegung in der Kategorie "Mytic Client". */
    public static KeyMapping registerKey(String name, int key) {
        if (category == null) category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("myticclient", "main"));
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, KEY_TYPE, key, category));
    }
    public static void drawFace(GuiGraphicsExtractor g, PlayerInfo info, int x, int y, int size) {
        PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
    }
}
