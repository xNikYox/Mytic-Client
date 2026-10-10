package de.myticlegacy.client;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.GuiGraphics;
import de.myticlegacy.client.compat.InputConstants;
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
import de.myticlegacy.client.module.CrosshairModule;
import de.myticlegacy.client.module.FreelookModule;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.module.SimpleModules;
import de.myticlegacy.client.module.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Mytic Client für Minecraft 1.8.9 (Forge): gleiche Module und Einstellungen wie die Fabric-Mod. */
@Mod(modid = MyticClient.ID, name = MyticClient.NAME, version = MyticClient.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public final class MyticClient {
    public static final String ID = "myticclient";
    public static final String NAME = "Mytic Client";
    public static final String VERSION = "2.7";
    public static final Logger LOG = LogManager.getLogger(NAME);

    public static final List<Module> MODULES = new ArrayList<Module>();
    public static final List<HudModule> HUD = new ArrayList<HudModule>();

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

    public static KeyBinding menuKey;
    public static KeyBinding zoomKey;
    public static KeyBinding freelookKey;

    private static Config config;
    private static boolean fullbrightApplied;
    private static boolean sneakToggled;
    private static long sessionStart;
    private static Method renderScoreboard;

    public static Config config() {
        if (config == null) config = Config.load();
        return config;
    }

    public static int accent() {
        return theme != null ? theme.accent.get() : 0xFF9B5CFF;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.configDir = event.getModConfigurationDirectory();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        config();
        final Minecraft mc = Minecraft.getMinecraft();
        final SimpleDateFormat clock = new SimpleDateFormat("HH:mm");

        // ------------------------------------------------------------------------------ HUD
        hud(new TextModule("fps", "FPS", "Bilder pro Sekunde", () -> Items.experience_bottle, true, 4, 4, "FPS", p -> String.valueOf(Minecraft.getDebugFPS())));
        hud(new TextModule("cps", "CPS", "Klicks pro Sekunde (links | rechts)", () -> Items.repeater, true, 4, 23, "CPS",
                p -> ClickCounter.left() + " | " + ClickCounter.right()));
        hud(new TextModule("ping", "Ping", "Verbindung zum Server", () -> Items.ender_pearl, true, 4, 42, "Ping", p -> {
            if (mc.thePlayer == null || mc.getNetHandler() == null) return p ? "42 ms" : "";
            if (mc.isSingleplayer()) return "0 ms";
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            return (info == null ? 0 : info.getResponseTime()) + " ms";
        }));
        hud(new KeystrokesModule());
        hud(new ArmorModule());
        hud(new PotionModule());
        hud(new TextModule("coords", "Koordinaten", "X, Y und Z deiner Position", () -> Items.compass, true, 10000, 4, "XYZ", p -> {
            if (mc.thePlayer == null) return p ? "120 64 -35" : "";
            return MathHelper.floor_double(mc.thePlayer.posX) + " " + MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) + " "
                    + MathHelper.floor_double(mc.thePlayer.posZ);
        }));
        hud(new TextModule("direction", "Richtung", "Himmelsrichtung, in die du schaust", () -> Items.map, true, 10000, 23, "", p -> {
            if (mc.thePlayer == null) return p ? "Nord" : "";
            switch (mc.thePlayer.getHorizontalFacing()) {
                case NORTH: return "Nord";
                case SOUTH: return "Süd";
                case WEST: return "West";
                case EAST: return "Ost";
                default: return "";
            }
        }));
        hud(new TextModule("clock", "Uhrzeit", "Echte Uhrzeit", () -> Items.clock, false, 10000, 42, "", p -> clock.format(new Date())));
        hud(new TextModule("speed", "Tempo", "Geschwindigkeit in Blöcken pro Sekunde", () -> Items.feather, false, 10000, 61, "", p -> {
            if (mc.thePlayer == null) return p ? "5.61 m/s" : "";
            double speed = Math.hypot(mc.thePlayer.posX - mc.thePlayer.lastTickPosX, mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * 20;
            return String.format("%.2f m/s", speed);
        }));
        hud(new TextModule("memory", "Arbeitsspeicher", "RAM-Verbrauch von Minecraft", () -> Items.redstone, false, 10000, 80, "RAM", p -> {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            return Math.round(used * 100.0 / rt.maxMemory()) + "% · " + used / 1048576 + " MB";
        }));
        hud(new TextModule("server", "Server-IP", "Adresse des Servers, auf dem du spielst", () -> Items.name_tag, false, 10000, 99, "", p -> {
            if (mc.getCurrentServerData() != null) return mc.getCurrentServerData().serverIP;
            if (mc.isSingleplayer()) return "Einzelspieler";
            return p ? "play.myticlegacy.de" : "";
        }));
        hud(new TextModule("biome", "Biom", "Biom, in dem du stehst", () -> Item.getItemFromBlock(Blocks.grass), false, 10000, 118, "Biom", p -> {
            if (mc.thePlayer == null || mc.theWorld == null) return p ? "Ebene" : "";
            return mc.theWorld.getBiomeGenForCoords(new BlockPos(mc.thePlayer)).biomeName;
        }));
        hud(new TextModule("daytime", "Spielzeit", "Tag und Uhrzeit in der Minecraft-Welt", () -> Item.getItemFromBlock(Blocks.daylight_detector), false, 10000, 137, "Tag", p -> {
            if (mc.theWorld == null) return p ? "12 · 14:30" : "";
            long time = Compat.dayTime(mc.theWorld);
            long day = time / 24000 + 1;
            long ticks = (time + 6000) % 24000;
            return day + " · " + String.format("%02d:%02d", ticks / 1000, ticks % 1000 * 60 / 1000);
        }));
        hud(new TextModule("combo", "Combo", "Treffer hintereinander, ohne selbst getroffen zu werden", () -> Items.iron_sword, false, 4, 300, "Combo",
                p -> CombatTracker.combo() > 0 ? String.valueOf(CombatTracker.combo()) : (p ? "3" : "")));
        hud(new TextModule("reach", "Reach", "Abstand deines letzten Treffers", () -> Items.bow, false, 4, 319, "Reach",
                p -> CombatTracker.reach() >= 0 ? String.format("%.2f m", CombatTracker.reach()) : (p ? "2.94 m" : "")));
        hud(new TextModule("session", "Session", "Wie lange du schon spielst", () -> Items.gold_ingot, false, 10000, 156, "Session", p -> {
            if (sessionStart == 0) return p ? "0:42:10" : "";
            long s = (System.currentTimeMillis() - sessionStart) / 1000;
            return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        }));
        hud(new ItemCounterModule());
        hud(new TargetHudModule());
        hud(new TextModule("watermark", "Wasserzeichen", "Mytic-Client-Schriftzug im HUD", () -> Items.nether_star, false, 10000, 175, "", p -> "Mytic Client"));

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

        menuKey = Compat.registerKey("key.myticclient.menu", InputConstants.KEY_RSHIFT);
        zoomKey = Compat.registerKey("key.myticclient.zoom", InputConstants.KEY_C);
        freelookKey = Compat.registerKey("key.myticclient.freelook", InputConstants.KEY_LALT);

        CombatTracker.register();
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        Display.setTitle(NAME + " " + Compat.versionName());
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

    // ---------------------------------------------------------------------------------------------- HUD

    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        switch (event.type) {
            case ALL:
                // Scoreboard zeichnet der Mytic Client bei Bedarf selbst (verkleinert)
                GuiIngameForge.renderObjective = !scoreboard.enabled();
                break;
            case CROSSHAIRS:
                if (crosshair.render(new GuiGraphics())) event.setCanceled(true);
                break;
            case BOSSHEALTH:
                if (hideBossbar.enabled()) event.setCanceled(true);
                break;
            default:
                break;
        }
    }

    @SubscribeEvent
    public void onOverlayPost(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        GuiGraphics g = new GuiGraphics();
        if (scoreboard.enabled() && !scoreboard.hide.get()) drawScoreboard(g, event.resolution);
        renderHud(g);
    }

    private static void drawScoreboard(GuiGraphics g, ScaledResolution resolution) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.gameSettings.hideGUI) return;
        Scoreboard board = mc.theWorld.getScoreboard();
        ScoreObjective objective = null;
        ScorePlayerTeam team = board.getPlayersTeam(mc.thePlayer.getName());
        if (team != null && team.getChatFormat().getColorIndex() >= 0) objective = board.getObjectiveInDisplaySlot(3 + team.getChatFormat().getColorIndex());
        if (objective == null) objective = board.getObjectiveInDisplaySlot(1);
        if (objective == null) return;
        try {
            if (renderScoreboard == null) {
                renderScoreboard = ReflectionHelper.findMethod(GuiIngame.class, mc.ingameGUI, new String[]{"renderScoreboard", "func_180475_a"},
                        ScoreObjective.class, ScaledResolution.class);
            }
            float s = scoreboard.scale.floatValue();
            Gfx.push(g);
            Gfx.translate(g, g.guiWidth(), g.guiHeight() / 2f);
            Gfx.scale(g, s, s);
            Gfx.translate(g, -g.guiWidth(), -g.guiHeight() / 2f);
            renderScoreboard.invoke(mc.ingameGUI, objective, resolution);
            Gfx.pop(g);
        } catch (ReflectiveOperationException | RuntimeException e) {
            GuiIngameForge.renderObjective = true;
        }
    }

    public static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getMinecraft();
        Ui.frame();
        Gfx.reset();
        if (Compat.hudHidden(mc) || Compat.screen(mc) instanceof HudEditScreen) return;
        for (HudModule module : HUD) {
            if (module.enabled()) module.draw(graphics, false);
        }
    }

    // ---------------------------------------------------------------------------------------------- Kamera

    @SubscribeEvent
    public void onFov(EntityViewRenderEvent.FOVModifier event) {
        event.setFOV(zoom.apply(event.getFOV()));
    }

    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup event) {
        float[] camera = freelook.camera(event.entity);
        if (camera != null) {
            event.yaw = camera[0];
            event.pitch = camera[1];
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouse(MouseEvent event) {
        if (event.dwheel != 0 && zoom.scroll(event.dwheel)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        ClickCounter.poll();
        zoom.frame();
        freelook.frameStart(mc);
        if (mc.theWorld != null) {
            if (timeChanger.enabled()) mc.theWorld.setWorldTime(timeChanger.dayTime());
            if (clearWeather.enabled()) {
                mc.theWorld.setRainStrength(0);
                mc.theWorld.setThunderStrength(0);
            }
        }
        if (noHurtCam.enabled() && mc.thePlayer != null) mc.thePlayer.hurtTime = 0;
    }

    // ---------------------------------------------------------------------------------------------- Ablauf

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof GuiMainMenu && theme.customMenu.get()) event.gui = new MyticTitleScreen();
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        int sneak = mc.gameSettings.keyBindSneak.getKeyCode();
        if (toggleSneak.enabled() && Keyboard.getEventKey() == sneak && Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent()) {
            sneakToggled = !sneakToggled;
        }
        applyMovementKeys(mc);
    }

    /** Toggle Sprint/Sneak: Tasten als gedrückt markieren (auch nach dem Loslassen der echten Taste). */
    private static void applyMovementKeys(Minecraft mc) {
        if (mc.thePlayer == null || Compat.screen(mc) != null) return;
        if (toggleSneak.enabled() && sneakToggled) KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        if (toggleSprint.enabled() && mc.gameSettings.keyBindForward.isKeyDown() && !mc.thePlayer.isSneaking() && !mc.thePlayer.isUsingItem()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.START) {
            applyMovementKeys(mc);
            return;
        }
        while (menuKey.isPressed()) {
            if (Compat.screen(mc) == null) Compat.setScreen(new ModMenuScreen(null));
        }
        if (mc.theWorld == null) sessionStart = 0;
        else if (sessionStart == 0) sessionStart = System.currentTimeMillis();
        if (!toggleSneak.enabled() && sneakToggled) {
            sneakToggled = false;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), Compat.held(mc.gameSettings.keyBindSneak));
        }

        zoom.tick(mc);
        freelook.tick(mc);
        CombatTracker.tick(mc);

        float gamma = mc.gameSettings.gammaSetting;
        if (fullbright.enabled()) {
            if (!fullbrightApplied) {
                if (gamma <= 1.0f) {
                    config().savedGamma = gamma;
                    config().save();
                }
                fullbrightApplied = true;
            }
            mc.gameSettings.gammaSetting = 15f;
        } else if (fullbrightApplied || gamma > 1.0f) {
            mc.gameSettings.gammaSetting = (float) Math.min(1.0, config().savedGamma);
            fullbrightApplied = false;
        }
    }
}
