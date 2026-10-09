package de.myticlegacy.client.compat;

import de.myticlegacy.client.gui.MyticScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Unterschiede zwischen den Minecraft-Versionen an einer Stelle – hier für 1.8.9 (Forge). */
public final class Compat {
    /** In 1.8.9 zeichnet der Bildschirm seinen Hintergrund selbst. */
    public static final boolean SCREEN_RENDERS_BACKGROUND = true;

    private Compat() {
    }

    public static GuiScreen optionsScreen(GuiScreen parent) {
        Minecraft mc = Minecraft.getMinecraft();
        return new GuiOptions(parent, mc.gameSettings);
    }

    public static void textInput(Object owner, boolean enabled) {
        Keyboard.enableRepeatEvents(enabled);
    }

    /** Taste gerade gedrückt? Direkt abgefragt, damit Doppelbelegungen (z. B. C) nicht stören. */
    public static boolean held(KeyBinding mapping) {
        int code = mapping.getKeyCode();
        if (code == 0) return false;
        try {
            return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    public static GuiScreen screen(Minecraft mc) {
        return mc.currentScreen;
    }

    public static void setScreen(GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
    }

    public static boolean hudHidden(Minecraft mc) {
        return mc.gameSettings.hideGUI;
    }

    public static long dayTime(World level) {
        return level.getWorldTime();
    }

    public static KeyBinding registerKey(String name, int key) {
        KeyBinding binding = new KeyBinding(name, key, "key.categories.myticclient");
        ClientRegistry.registerKeyBinding(binding);
        return binding;
    }

    /** Kopf eines Spielers (mit Hut-Ebene). */
    public static void drawFace(GuiGraphics g, NetworkPlayerInfo info, int x, int y, int size) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(info.getLocationSkin());
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
    }

    public static int guiWidth() {
        return new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth();
    }

    public static int guiHeight() {
        return new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight();
    }

    public static int guiScale() {
        return new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
    }

    public static String versionName() {
        return "1.8.9";
    }

    /** Tooltip: wird am Ende des Bildes in normaler Größe an der Maus gezeichnet. */
    public static void tooltip(GuiGraphics g, String text, int x, int y) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof MyticScreen) ((MyticScreen) screen).setTooltip(text);
    }
}
