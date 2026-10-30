package de.myticlegacy.client;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.HubScreen;
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
import de.myticlegacy.client.module.AutoTextModule;
import de.myticlegacy.client.module.CrosshairModule;
import de.myticlegacy.client.module.LightOverlayModule;
import de.myticlegacy.client.module.FpsBoostModule;
import de.myticlegacy.client.module.FreelookModule;
import de.myticlegacy.client.module.FriendsModule;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.module.SimpleModules;
import de.myticlegacy.client.module.ShulkerPreviewModule;
import de.myticlegacy.client.module.SnaplookModule;
import de.myticlegacy.client.module.ZoomModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class MyticClient implements ClientModInitializer {
    public static final String ID = "myticclient";
    public static final String NAME = "Mytic Client";
    public static final String VERSION = "2.16";
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
    public static Module capePhysics;
    public static Module hideBossbar;

    public static KeyMapping menuKey;
    public static KeyMapping zoomKey;
    public static KeyMapping freelookKey;
    public static KeyMapping snaplookKey, secondPersonKey;
    private static KeyMapping pitchLockKey;
    public static de.myticlegacy.client.module.SecondPersonModule secondPerson;
    public static de.myticlegacy.client.module.PitchLockModule pitchLock;
    public static SnaplookModule snaplook;
    /** Tasten für Auto Text (anfangs unbelegt). */
    public static final KeyMapping[] autoTextKeys = new KeyMapping[AutoTextModule.SLOTS];
    public static AutoTextModule autoText;
    public static LightOverlayModule lightOverlay;
    public static FpsBoostModule fpsBoost;
    public static de.myticlegacy.client.module.ChatModule chat;
    public static de.myticlegacy.client.module.StreamerModule streamer;
    public static de.myticlegacy.client.module.ClipModule clips;
    public static de.myticlegacy.client.module.PvpModule pvp;
    private static KeyMapping streamerKey, galleryKey, clipKey;
    private static final KeyMapping[] profileKeys = new KeyMapping[3];
    public static de.myticlegacy.client.hud.ScreenshotModule screenshot;
    public static de.myticlegacy.client.hud.WarningsModule warnings;
    public static de.myticlegacy.client.module.KeyConflictModule keyConflicts;
    public static de.myticlegacy.client.module.ServerProfilesModule serverProfiles;
    public static de.myticlegacy.client.module.PackOverlayModule packOverlays;
    public static de.myticlegacy.client.module.CloudSyncModule cloudSync;
    public static de.myticlegacy.client.module.ProfileCodeModule profileCode;
    public static de.myticlegacy.client.module.TotemPopModule totemPops;
    public static final KeyMapping[] packKeys = new KeyMapping[de.myticlegacy.client.module.PackOverlayModule.SLOTS];
    public static ShulkerPreviewModule shulkerPreview;
    public static FriendsModule friends;

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
        hud(new TextModule("fps", "FPS", "Bilder pro Sekunde", () -> Items.EXPERIENCE_BOTTLE, true, 4, 4, "FPS", p -> String.valueOf(mc.getFps())));
        hud(new TextModule("cps", "CPS", "Klicks pro Sekunde (links | rechts)", () -> Items.REPEATER, true, 4, 23, "CPS",
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
        screenshot = new de.myticlegacy.client.hud.ScreenshotModule();
        hud(screenshot);
        hud(new TextModule("coords", "Koordinaten", "X, Y und Z deiner Position", () -> Items.COMPASS, true, 10000, 4, "XYZ", p -> {
            if (mc.player == null) return p ? "120 64 -35" : "";
            if (de.myticlegacy.client.module.StreamerModule.hidesCoords()) return "??? ??? ???";
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
            if (mc.getCurrentServer() != null) return de.myticlegacy.client.module.StreamerModule.hidesIp() ? "versteckt" : mc.getCurrentServer().ip;
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
            long time = Compat.dayTime(mc.level);
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
        warnings = new de.myticlegacy.client.hud.WarningsModule();
        hud(warnings);
        hud(new TargetHudModule());
        hud(new de.myticlegacy.client.hud.PitchModule());
        de.myticlegacy.client.hud.VanillaHudModule.createAll();
        for (var v : de.myticlegacy.client.hud.VanillaHudModule.all()) hud(v);
        hud(new TextModule("watermark", "Wasserzeichen", "Mytic-Client-Schriftzug im HUD", () -> Items.AMETHYST_SHARD, false, 10000, 175, "", p -> "Mytic Client"));

        // ------------------------------------------------------------------------------ Mechanik & Visuell
        zoom = module(new ZoomModule());
        freelook = module(new FreelookModule());
        snaplook = module(new SnaplookModule());
        secondPerson = module(new de.myticlegacy.client.module.SecondPersonModule());
        pitchLock = module(new de.myticlegacy.client.module.PitchLockModule());
        toggleSprint = module(SimpleModules.toggleSprint());
        toggleSneak = module(SimpleModules.toggleSneak());
        crosshair = module(new CrosshairModule());
        fullbright = module(SimpleModules.fullbright());
        timeChanger = module(new SimpleModules.TimeChanger());
        clearWeather = module(SimpleModules.clearWeather());
        noHurtCam = module(SimpleModules.noHurtCam());
        capePhysics = module(SimpleModules.capePhysics());
        scoreboard = module(new SimpleModules.ScoreboardTweaks());
        hideBossbar = module(SimpleModules.hideBossbar());
        autoText = module(new AutoTextModule());
        lightOverlay = module(new LightOverlayModule());
        fpsBoost = module(new FpsBoostModule());
        chat = module(new de.myticlegacy.client.module.ChatModule());
        streamer = module(new de.myticlegacy.client.module.StreamerModule());
        clips = module(new de.myticlegacy.client.module.ClipModule());
        pvp = module(new de.myticlegacy.client.module.PvpModule());
        shulkerPreview = module(new ShulkerPreviewModule());
        friends = module(new FriendsModule());
        theme = module(new SimpleModules.Theme());
        keyConflicts = module(new de.myticlegacy.client.module.KeyConflictModule());
        serverProfiles = module(new de.myticlegacy.client.module.ServerProfilesModule());
        packOverlays = module(new de.myticlegacy.client.module.PackOverlayModule());
        cloudSync = module(new de.myticlegacy.client.module.CloudSyncModule());
        profileCode = module(new de.myticlegacy.client.module.ProfileCodeModule());
        totemPops = module(new de.myticlegacy.client.module.TotemPopModule());

        menuKey = Compat.registerKey("key.myticclient.menu", InputConstants.KEY_RSHIFT);
        zoomKey = Compat.registerKey("key.myticclient.zoom", InputConstants.KEY_C);
        freelookKey = Compat.registerKey("key.myticclient.freelook", InputConstants.KEY_LALT);
        snaplookKey = Compat.registerKey("key.myticclient.snaplook", InputConstants.KEY_V);
        secondPersonKey = Compat.registerKey("key.myticclient.secondperson", InputConstants.KEY_F6);
        pitchLockKey = Compat.registerKey("key.myticclient.pitchlock", InputConstants.UNKNOWN.getValue());
        streamerKey = Compat.registerKey("key.myticclient.streamer", InputConstants.UNKNOWN.getValue());
        galleryKey = Compat.registerKey("key.myticclient.screenshots", InputConstants.UNKNOWN.getValue());
        clipKey = Compat.registerKey("key.myticclient.clip", InputConstants.KEY_F8);
        for (int i = 0; i < profileKeys.length; i++) profileKeys[i] = Compat.registerKey("key.myticclient.hudprofile" + (i + 1), InputConstants.UNKNOWN.getValue());
        for (int i = 0; i < AutoTextModule.SLOTS; i++) autoTextKeys[i] = Compat.registerKey("key.myticclient.autotext" + (i + 1), -1);
        for (int i = 0; i < packKeys.length; i++) packKeys[i] = Compat.registerKey("key.myticclient.pack" + (i + 1), -1);

        Compat.registerHud();
        de.myticlegacy.client.render.WorldRender.register(sink -> lightOverlay.draw(sink));

        Friends.start();
        CombatTracker.register();
        ClientTickEvents.END_CLIENT_TICK.register(MyticClient::tick);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> shulkerPreview.trimTooltip(stack, lines));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> shulkerPreview.draw(s, g, mouseX, mouseY));
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen && theme.customMenu.get()) {
                client.execute(() -> {
                    if (Compat.screen(client) instanceof TitleScreen) Compat.setScreen(new MyticTitleScreen());
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

    /** Eigenes Fadenkreuz statt des normalen (original zeichnet das normale). */
    public static void hudCrosshair(GuiGraphics graphics, Runnable original) {
        if (!crosshair.render(graphics)) original.run();
    }

    /** Scoreboard verkleinern oder ausblenden. */
    public static void hudScoreboard(GuiGraphics graphics, Runnable original) {
        if (!scoreboard.enabled()) {
            original.run();
        } else if (!scoreboard.hide.get()) {
            float s = scoreboard.scale.floatValue();
            Gfx.push(graphics);
            Gfx.translate(graphics, graphics.guiWidth(), graphics.guiHeight() / 2f);
            Gfx.scale(graphics, s, s);
            Gfx.translate(graphics, -graphics.guiWidth(), -graphics.guiHeight() / 2f);
            original.run();
            Gfx.pop(graphics);
        }
    }

    public static void hudBossbar(Runnable original) {
        if (!hideBossbar.enabled()) original.run();
    }

    public static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        ClickCounter.poll();
        Ui.frame();
        if (Compat.hudHidden(mc) || Compat.screen(mc) instanceof HudEditScreen) return;
        for (HudModule module : HUD) {
            if (module.enabled()) module.draw(graphics, false);
        }
    }

    /** Status für die Freundesliste weitergeben und Benachrichtigungen im Chat zeigen. */
    private static void tickFriends(Minecraft mc) {
        if (!Friends.available()) return;
        Friends.visible = friends.enabled();
        Friends.showServer = friends.showServer.get();
        Friends.notify = friends.notify.get();
        Friends.mcVersion = Compat.versionName();
        if (mc.level == null) Friends.currentServer = null;
        else if (mc.getCurrentServer() != null) Friends.currentServer = mc.getCurrentServer().ip;
        else Friends.currentServer = mc.hasSingleplayerServer() ? "Einzelspieler" : null;
        if (mc.player == null) return;
        String notice;
        while ((notice = Friends.pollNotice()) != null) Compat.chat(mc, "§8[§dMytic§8] " + notice);
    }

    private static void tick(Minecraft mc) {
        // Rechts-Shift: Übersicht (Freunde, Mods, Cosmetics); Strg + Rechts-Shift: direkt die Mods
        while (menuKey.consumeClick()) {
            if (Compat.screen(mc) == null) Compat.setScreen(Compat.ctrlDown() ? new ModMenuScreen(null) : new HubScreen());
        }
        tickFriends(mc);
        if (mc.level == null) sessionStart = 0;
        else if (sessionStart == 0) sessionStart = System.currentTimeMillis();

        zoom.tick(mc);
        freelook.tick(mc);
        snaplook.tick(mc);
        secondPerson.tick(mc);
        pitchLock.tick(mc);
        while (pitchLockKey.consumeClick()) pitchLock.toggle(mc);
        de.myticlegacy.client.cosmetics.Shields.demoTick(mc);
        autoText.tick(mc);
        lightOverlay.tick(mc);
        fpsBoost.tick(mc);
        pvp.tick();
        screenshot.tick();
        clips.tick(mc);
        while (clipKey.consumeClick()) clips.saveClip(mc);
        while (galleryKey.consumeClick()) if (Compat.screen(mc) == null) Compat.setScreen(new de.myticlegacy.client.gui.ScreenshotsScreen());
        for (int i = 0; i < profileKeys.length; i++) {
            while (profileKeys[i].consumeClick()) {
                String name = de.myticlegacy.client.hud.HudProfiles.switchToIndex(i);
                if (name != null) de.myticlegacy.client.module.Msg.actionBar("HUD-Profil: " + name);
            }
        }
        while (streamerKey.consumeClick()) {
            streamer.toggle();
            de.myticlegacy.client.module.Msg.actionBar("Streamer-Modus " + (streamer.enabled() ? "an" : "aus"));
        }
        de.myticlegacy.client.cosmetics.Cosmetics.tick(mc);
        CombatTracker.tick(mc);
        warnings.tick(mc);
        keyConflicts.tick(mc);
        serverProfiles.tick(mc);
        packOverlays.tick(mc);
        cloudSync.tick(mc);
        totemPops.tick(mc);

        if (toggleSprint.enabled() && mc.player != null && Compat.screen(mc) == null
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
