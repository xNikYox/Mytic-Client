package de.myticlegacy.client.compat;

import de.myticlegacy.client.MyticClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
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

/** Fassung für Minecraft 26.x: Bildschirme liegen in Minecraft#gui, HUD-Sichtbarkeit in Hud, Zeit in der Welt-Uhr. */
public final class Compat {
    public static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYBOARD;

    /** Bis 1.21.5 muss ein Bildschirm seinen Hintergrund selbst zeichnen, danach macht das Minecraft. */
    public static final boolean SCREEN_RENDERS_BACKGROUND = false;

    private Compat() {
    }

    public static Screen optionsScreen(Screen parent) {
        return new OptionsScreen(parent, Minecraft.getInstance().options);
    }

    /** 26.x (SDL): Zeichen kommen nur an, wenn ein Bildschirm die Texteingabe anfordert. */
    public static void textInput(GuiEventListener owner, boolean enabled) {
        Minecraft.getInstance().onTextInputFocusChange(owner, enabled);
    }

    /** Ob die Taste einer Belegung gerade gedrückt ist. */
    public static boolean held(KeyMapping mapping) {
        return mapping.isDown();
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

    public static int guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static String versionName() {
        return SharedConstants.getCurrentVersion().name();
    }

    public static void tooltip(GuiGraphicsExtractor g, Component text, int x, int y) {
        g.setTooltipForNextFrame(text, x, y);
    }

    /** HUD anmelden: eigene Anzeigen, Fadenkreuz, Scoreboard und Bossbar ersetzen. */
    public static void registerHud() {
        HudElementRegistry.addLast(id("hud"), (g, delta) -> MyticClient.renderHud(g));
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, o -> (g, delta) -> MyticClient.hudCrosshair(g, () -> o.extractRenderState(g, delta)));
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, o -> (g, delta) -> MyticClient.hudScoreboard(g, () -> o.extractRenderState(g, delta)));
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, o -> (g, delta) -> MyticClient.hudBossbar(() -> o.extractRenderState(g, delta)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("myticclient", path);
    }
}
