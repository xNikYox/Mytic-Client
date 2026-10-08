package de.myticlegacy.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.gui.HudEditScreen;
import de.myticlegacy.client.gui.ModMenuScreen;
import de.myticlegacy.client.gui.MyticTitleScreen;
import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.hud.ArmorModule;
import de.myticlegacy.client.hud.ClickCounter;
import de.myticlegacy.client.hud.CombatTracker;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.hud.ItemCounterModule;
import de.myticlegacy.client.hud.KeystrokesModule;
import de.myticlegacy.client.hud.PotionModule;
import de.myticlegacy.client.hud.TargetHudModule;
import de.myticlegacy.client.hud.TextModule;
import de.myticlegacy.client.mixin.OptionInstanceAccessor;
import de.myticlegacy.client.module.CrosshairModule;
import de.myticlegacy.client.module.FreelookModule;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.module.SimpleModules;
import de.myticlegacy.client.module.ZoomModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class MyticClient implements ClientModInitializer {
    public static final String ID = "myticclient";
    public static final String NAME = "Mytic Client";
    public static final String VERSION = "2.0";
    public static final Logger LOG = LoggerFactory.getLogger(NAME);

    public static final List<Module> MODULES = new ArrayList<>();
    public static final List<HudModule> HUD = new ArrayList<>();

    public static SimpleModules.Theme theme;
    public static ZoomModule zoom;
    public static FreelookModule freelook;
    public static CrosshairModule crosshair;
    public static SimpleModules.TimeChanger timeChanger;
    public static SimpleModules.ScoreboardTweaks scoreboard;
    public static Module toggleSprint;
    public static Module toggleSneak;
    public static Module fullbright;
    public static Module clearWeather;
    public static Module noHurtCam;
    public static Module hideBossbar;

    public static KeyMapping menuKey;
    public static KeyMapping zoomKey;
    public static KeyMapping freelookKey;

    private static Config config;
    private static boolean fullbrightApplied;
    private static boolean sneakApplied;
    private static long sessionStart;

    public static Config config() {
        if (config == null) config = Config.load();
        return config;
    }

    public static int accent() {
        return theme != null ? theme.accent.get() : 0xFF9B5CFF;
    }

    @Override
    public void onInitializeClient() {
        config();
        Minecraft mc = Minecraft.getInstance();
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");

        // ------------------------------------------------------------------------------ HUD
        hud(new TextModule("fps", "FPS", "Bilder pro Sekunde", () -> Items.CLOCK, true, 4, 4, "FPS", p -> String.valueOf(mc.getFps())));
        hud(new TextModule("cps", "CPS", "Klicks pro Sekunde (links | rechts)", () -> Items.STONE_BUTTON, true, 4, 23, "CPS",
                p -> ClickCounter.left() + " | " + ClickCounter.right()));
        hud(new TextModule("ping", "Ping", "Verbindung zum Server", () -> Items.ENDER_PEARL, true, 4, 42, "Ping", p -> {
            if (mc.player == null || mc.getConnection() == null) return p ? "42 ms" : "";
            if (mc.hasSingleplayerServer()) return "0 ms";
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return (info == null ? 0 : info.getLatency()) + " ms";
        }));
        hud(new KeystrokesModule());
        hud(new ArmorModule());
        hud(new PotionModule());
        hud(new TextModule("coords", "Koordinaten", "X, Y und Z deiner Position", () -> Items.COMPASS, true, 10000, 4, "XYZ", p -> {
            if (mc.player == null) return p ? "120 64 -35" : "";
            return mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
        }));
        hud(new TextModule("direction", "Richtung", "Himmelsrichtung, in die du schaust", () -> Items.MAP, true, 10000, 23, "", p -> {
            if (mc.player == null) return p ? "Nord" : "";
            return switch (mc.player.getDirection()) {
                case NORTH -> "Nord";
                case SOUTH -> "Süd";
                case WEST -> "West";
                case EAST -> "Ost";
                default -> "";
            };
        }));
        hud(new TextModule("clock", "Uhrzeit", "Echte Uhrzeit", () -> Items.CLOCK, false, 10000, 42, "", p -> LocalTime.now().format(clock)));
        hud(new TextModule("speed", "Tempo", "Geschwindigkeit in Blöcken pro Sekunde", () -> Items.FEATHER, false, 10000, 61, "", p -> {
            if (mc.player == null) return p ? "5.61 m/s" : "";
            double speed = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo) * 20;
            return String.format("%.2f m/s", speed);
        }));
        hud(new TextModule("memory", "Arbeitsspeicher", "RAM-Verbrauch von Minecraft", () -> Items.REDSTONE, false, 10000, 80, "RAM", p -> {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            return Math.round(used * 100.0 / rt.maxMemory()) + "% · " + used / 1048576 + " MB";
        }));
        hud(new TextModule("server", "Server-IP", "Adresse des Servers, auf dem du spielst", () -> Items.NAME_TAG, false, 10000, 99, "", p -> {
            if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
            if (mc.hasSingleplayerServer()) return "Einzelspieler";
            return p ? "play.myticlegacy.de" : "";
        }));
        hud(new TextModule("biome", "Biom", "Biom, in dem du stehst", () -> Items.GRASS_BLOCK, false, 10000, 118, "Biom", p -> {
            if (mc.player == null || mc.level == null) return p ? "Ebene" : "";
            var key = mc.level.getBiome(mc.player.blockPosition()).unwrapKey();
            if (key.isEmpty()) return "";
            String path = key.get().identifier().getPath();
            return Ui.font() != null ? net.minecraft.client.resources.language.I18n.get("biome.minecraft." + path) : path;
        }));
        hud(new TextModule("daytime", "Spielzeit", "Tag und Uhrzeit in der Minecraft-Welt", () -> Items.DAYLIGHT_DETECTOR, false, 10000, 137, "Tag", p -> {
            if (mc.level == null) return p ? "12 · 14:30" : "";
            long time = mc.level.getDayTime();
            long day = time / 24000 + 1;
            long ticks = (time + 6000) % 24000;
            return day + " · " + String.format("%02d:%02d", ticks / 1000, ticks % 1000 * 60 / 1000);
        }));
        hud(new TextModule("combo", "Combo", "Treffer hintereinander, ohne selbst getroffen zu werden", () -> Items.IRON_SWORD, false, 4, 300, "Combo",
                p -> CombatTracker.combo() > 0 ? String.valueOf(CombatTracker.combo()) : (p ? "3" : "")));
        hud(new TextModule("reach", "Reach", "Abstand deines letzten Treffers", () -> Items.BOW, false, 4, 319, "Reach",
                p -> CombatTracker.reach() >= 0 ? String.format("%.2f m", CombatTracker.reach()) : (p ? "2.94 m" : "")));
        hud(new TextModule("session", "Session", "Wie lange du schon spielst", () -> Items.BELL, false, 10000, 156, "Session", p -> {
            if (sessionStart == 0) return p ? "0:42:10" : "";
            long s = (System.currentTimeMillis() - sessionStart) / 1000;
            return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        }));
        hud(new ItemCounterModule());
        hud(new TargetHudModule());
        hud(new TextModule("watermark", "Wasserzeichen", "Mytic-Client-Schriftzug im HUD", () -> Items.AMETHYST_SHARD, false, 10000, 175, "", p -> "Mytic Client"));

        // ------------------------------------------------------------------------------ Mechanik & Visuell
        zoom = module(new ZoomModule());
        freelook = module(new FreelookModule());
        toggleSprint = module(SimpleModules.toggleSprint());
        toggleSneak = module(SimpleModules.toggleSneak());
        crosshair = module(new CrosshairModule());
        fullbright = module(SimpleModules.fullbright());
        timeChanger = module(new SimpleModules.TimeChanger());
        clearWeather = module(SimpleModules.clearWeather());
        noHurtCam = module(SimpleModules.noHurtCam());
        scoreboard = module(new SimpleModules.ScoreboardTweaks());
        hideBossbar = module(SimpleModules.hideBossbar());
        theme = module(new SimpleModules.Theme());

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.myticclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.myticclient.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.myticclient.freelook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "hud"), (graphics, delta) -> renderHud(graphics));
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
            if (!crosshair.render(graphics)) original.render(graphics, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (graphics, delta) -> {
            if (!scoreboard.enabled()) {
                original.render(graphics, delta);
            } else if (!scoreboard.hide.get()) {
                float s = scoreboard.scale.floatValue();
                graphics.pose().pushMatrix();
                graphics.pose().translate(graphics.guiWidth(), graphics.guiHeight() / 2f);
                graphics.pose().scale(s, s);
                graphics.pose().translate(-graphics.guiWidth(), -graphics.guiHeight() / 2f);
                original.render(graphics, delta);
                graphics.pose().popMatrix();
            }
        });
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> (graphics, delta) -> {
            if (!hideBossbar.enabled()) original.render(graphics, delta);
        });

        CombatTracker.register();
        ClientTickEvents.END_CLIENT_TICK.register(MyticClient::tick);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen && theme.customMenu.get()) {
                client.execute(() -> {
                    if (client.screen instanceof TitleScreen) client.setScreen(new MyticTitleScreen());
                });
            }
        });
        LOG.info("{} {} geladen: {} Mods", NAME, VERSION, MODULES.size());
    }

    private static void hud(HudModule module) {
        HUD.add(module);
        MODULES.add(module);
    }

    private static <T extends Module> T module(T module) {
        MODULES.add(module);
        return module;
    }

    private static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        ClickCounter.poll();
        Ui.frame();
        if (mc.options.hideGui || mc.screen instanceof HudEditScreen) return;
        for (HudModule module : HUD) {
            if (module.enabled()) module.draw(graphics, false);
        }
    }

    private static void tick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ModMenuScreen(null));
        }
        if (mc.level == null) sessionStart = 0;
        else if (sessionStart == 0) sessionStart = System.currentTimeMillis();

        zoom.tick(mc);
        freelook.tick(mc);
        CombatTracker.tick(mc);

        if (toggleSprint.enabled() && mc.player != null && mc.screen == null
                && mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.isUsingItem()) {
            mc.options.keySprint.setDown(true);
        }
        if (toggleSneak.enabled() != sneakApplied) {
            mc.options.toggleCrouch().set(toggleSneak.enabled());
            sneakApplied = toggleSneak.enabled();
        }

        var gamma = mc.options.gamma();
        if (fullbright.enabled()) {
            if (!fullbrightApplied) {
                if (gamma.get() <= 1.0) {
                    config().savedGamma = gamma.get();
                    config().save();
                }
                fullbrightApplied = true;
            }
            ((OptionInstanceAccessor) (Object) gamma).mytic$setValue(15.0);
        } else if (fullbrightApplied || gamma.get() > 1.0) {
            ((OptionInstanceAccessor) (Object) gamma).mytic$setValue(Math.min(1.0, config().savedGamma));
            fullbrightApplied = false;
        }
    }
}
