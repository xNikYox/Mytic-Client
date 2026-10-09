package de.myticlegacy.client.compat;

import de.myticlegacy.client.MyticClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Alles, was sich zwischen Minecraft-Versionen unterscheidet, an einer Stelle.
 * Diese Fassung gilt für 1.21 bis 1.21.5; andere Versionen haben eine eigene Compat.java im jeweiligen Build-Projekt.
 */
public final class Compat {
    /** Tastatur-Eingabetyp für Tastenbelegungen. */
    public static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYSYM;

    /** Bis 1.21.5 muss ein Bildschirm seinen Hintergrund selbst zeichnen, danach macht das Minecraft. */
    public static final boolean SCREEN_RENDERS_BACKGROUND = true;

    private Compat() {
    }

    public static Screen optionsScreen(Screen parent) {
        return new OptionsScreen(parent, Minecraft.getInstance().options);
    }

    /** Texteingabe (getippte Zeichen) für einen Bildschirm ein-/ausschalten. Vor 26.x immer aktiv. */
    public static void textInput(Object owner, boolean enabled) {
    }

    /**
     * Ob die Taste einer Belegung gerade gedrückt ist. Bis 1.21.5 kann jede Taste nur eine Belegung haben
     * (C ist dort z. B. auch "Schnellleiste speichern"), deshalb wird die Tastatur direkt abgefragt.
     */
    public static boolean held(KeyMapping mapping) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        }
        return mapping.isDown();
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

    public static int guiScale() {
        return (int) Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static String versionName() {
        return SharedConstants.getCurrentVersion().getName();
    }

    public static void tooltip(GuiGraphics g, Component text, int x, int y) {
        // bis 1.21.5 zeichnet renderTooltip sofort (im verkleinerten Menü an falscher Stelle): über den Bildschirm verzögern
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) screen.setTooltipForNextRenderPass(text);
    }

    /** HUD anmelden: eigene Anzeigen, Fadenkreuz, Scoreboard und Bossbar ersetzen. */
    public static void registerHud() {
        HudLayerRegistrationCallback.EVENT.register(layers -> layers
                .addLayer(IdentifiedLayer.of(id("hud"), (g, delta) -> MyticClient.renderHud(g)))
                .replaceLayer(IdentifiedLayer.CROSSHAIR, o -> IdentifiedLayer.of(o.id(), (g, delta) -> MyticClient.hudCrosshair(g, () -> o.render(g, delta))))
                .replaceLayer(IdentifiedLayer.SCOREBOARD, o -> IdentifiedLayer.of(o.id(), (g, delta) -> MyticClient.hudScoreboard(g, () -> o.render(g, delta))))
                .replaceLayer(IdentifiedLayer.BOSS_BAR, o -> IdentifiedLayer.of(o.id(), (g, delta) -> MyticClient.hudBossbar(() -> o.render(g, delta)))));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myticclient", path);
    }
}
