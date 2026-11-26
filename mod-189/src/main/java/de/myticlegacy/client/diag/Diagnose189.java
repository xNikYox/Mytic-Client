package de.myticlegacy.client.diag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnose für 1.8.9 (Forge): Zeiten (Spielstart bis Hauptmenü, Verbindung bis Welt geladen, längstes Bild beim Join)
 * in logs/mytic-diagnose.json und die „Diagnose-Messung“ (/myticdiagnose, eigene Taste). Ressourcen-Neuladen läuft in
 * 1.8.9 blockierend im Render-Thread und wird hier nicht einzeln gemessen. Pro Tick nur ein paar Vergleiche.
 */
public final class Diagnose189 {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int KEEP = 10, AFTER_JOIN_MS = 5000, DEFAULT_SECONDS = 60;
    private static final long startedAt = System.currentTimeMillis();
    private static KeyBinding key;
    private static long titleAtMs = -1;
    private static long joinStart, joinLevelAt, joinWorldAt, lastFrame;
    private static boolean joinTracking;
    private static long joinMaxFrameNs, afterJoinMaxFrameNs;
    private static String joinKind, joinServer;
    private static final List<Map<String, Object>> joins = new ArrayList<Map<String, Object>>();

    private Diagnose189() {
    }

    public static void register() {
        key = Compat.registerKey("key.myticclient.diagnose", 0);
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override
            public String getCommandName() {
                return "myticdiagnose";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/myticdiagnose [sekunden|stop]";
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
                    SamplingProfiler.stopRunning();
                    return;
                }
                int seconds = DEFAULT_SECONDS;
                if (args.length > 0) {
                    try {
                        seconds = Math.max(5, Math.min(600, Integer.parseInt(args[0])));
                    } catch (NumberFormatException ignored) {
                        // Standard
                    }
                }
                toggle(seconds);
            }

            @Override
            public boolean canCommandSenderUseCommand(ICommandSender sender) {
                return true;
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 0;
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                writeNow();
            }
        }, "Mytic Diagnose"));
    }

    /** Pro Bild (RenderTickEvent START): nur Bildzeit während eines Joins */
    public static void frame() {
        long now = System.nanoTime();
        if (joinTracking && lastFrame != 0) {
            long dt = now - lastFrame;
            if (joinWorldAt == 0) joinMaxFrameNs = Math.max(joinMaxFrameNs, dt);
            else afterJoinMaxFrameNs = Math.max(afterJoinMaxFrameNs, dt);
        }
        lastFrame = now;
    }

    /** Jeden Tick (ClientTickEvent END) */
    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        while (key != null && key.isPressed()) toggle(DEFAULT_SECONDS);
        GuiScreen screen = mc.currentScreen;
        if (titleAtMs < 0 && screen != null) {
            titleAtMs = now - ManagementFactory.getRuntimeMXBean().getStartTime();
            write();
        }
        if (joinTracking && joinLevelAt == 0 && "singleplayer".equals(joinKind) && screen instanceof GuiConnecting) joinTracking = false;
        if (!joinTracking) {
            if (mc.theWorld == null && (screen instanceof GuiConnecting || mc.getIntegratedServer() != null)) {
                joinStart = now;
                joinLevelAt = 0;
                joinWorldAt = 0;
                joinMaxFrameNs = 0;
                afterJoinMaxFrameNs = 0;
                joinKind = screen instanceof GuiConnecting ? "server" : "singleplayer";
                joinServer = "";
                joinTracking = true;
            }
        } else if (joinWorldAt == 0) {
            if (joinLevelAt == 0 && mc.theWorld != null) joinLevelAt = now;
            if (mc.getCurrentServerData() != null) joinServer = mc.getCurrentServerData().serverIP;
            if (mc.theWorld != null && mc.thePlayer != null && !(screen instanceof GuiDownloadTerrain)) {
                joinWorldAt = now;
            } else if (mc.theWorld == null && (screen instanceof GuiDisconnected || screen instanceof GuiMainMenu || screen instanceof de.myticlegacy.client.gui.MyticTitleScreen
                    || screen instanceof net.minecraft.client.gui.GuiMultiplayer || now - joinStart > 300000)) {
                joinTracking = false;
            }
        } else if (mc.theWorld == null) {
            joinTracking = false;
        } else if (now - joinWorldAt >= AFTER_JOIN_MS) {
            Map<String, Object> j = new LinkedHashMap<String, Object>();
            j.put("at", joinStart);
            j.put("kind", joinKind);
            j.put("server", "singleplayer".equals(joinKind) ? "Einzelspieler" : joinServer);
            j.put("connectMs", (joinLevelAt == 0 ? joinWorldAt : joinLevelAt) - joinStart);
            j.put("loadingMs", joinLevelAt == 0 ? 0 : joinWorldAt - joinLevelAt);
            j.put("totalMs", joinWorldAt - joinStart);
            j.put("maxFrameMs", joinMaxFrameNs / 1000000L);
            j.put("afterJoinMaxFrameMs", afterJoinMaxFrameNs / 1000000L);
            joinTracking = false;
            synchronized (joins) {
                joins.add(j);
                while (joins.size() > KEEP) joins.remove(0);
            }
            write();
        }
    }

    private static void toggle(final int seconds) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (SamplingProfiler.isRunning()) {
            SamplingProfiler.stopRunning();
            chat("§7Diagnose-Messung wird beendet …");
            return;
        }
        String server = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : mc.isSingleplayer() ? "Einzelspieler" : "Menü";
        String header = "Mytic " + MyticClient.VERSION + " · Minecraft 1.8.9 (Forge) · Java " + System.getProperty("java.version")
                + "\nOrt: " + server + " · FPS beim Start: " + Minecraft.getDebugFPS() + " · Mods: " + Loader.instance().getActiveModList().size()
                + "\nSichtweite: " + mc.gameSettings.renderDistanceChunks;
        boolean started = SamplingProfiler.start(Thread.currentThread(), new ForgeMods(), dir(), seconds, header, new SamplingProfiler.Callback() {
            @Override
            public void finished(final File report, final String error) {
                mc.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        if (report != null) chat("§aDiagnose-Messung fertig: §f" + report.getName() + " §7(Launcher → Einstellungen → Diagnose)");
                        else chat("§cDiagnose-Messung fehlgeschlagen: " + error);
                    }
                });
            }
        });
        if (started) chat("§7Diagnose-Messung läuft " + seconds + " s – spiel normal weiter. Abbrechen: §f/myticdiagnose stop");
    }

    private static void chat(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null) mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("§8[§dMytic§8] " + text));
    }

    static File dir() {
        String prop = System.getProperty("mytic.diagnose.dir", "").trim();
        if (!prop.isEmpty()) return new File(prop);
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "mytic"), "diagnose");
    }

    private static void write() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                writeNow();
            }
        }, "Mytic Diagnose");
        t.setDaemon(true);
        t.start();
    }

    private static synchronized void writeNow() {
        try {
            Map<String, Object> o = new LinkedHashMap<String, Object>();
            o.put("version", 1);
            o.put("mytic", MyticClient.VERSION);
            o.put("startedAt", startedAt);
            o.put("updatedAt", System.currentTimeMillis());
            o.put("cpus", Runtime.getRuntime().availableProcessors());
            Map<String, Object> t = new LinkedHashMap<String, Object>();
            t.put("gameStartToTitleMs", titleAtMs);
            synchronized (joins) {
                t.put("joins", new ArrayList<Map<String, Object>>(joins));
            }
            t.put("reloads", new ArrayList<Object>());
            o.put("timings", t);
            Map<String, Object> sys = new LinkedHashMap<String, Object>();
            sys.put("minecraft", "1.8.9");
            sys.put("java", System.getProperty("java.version"));
            sys.put("javaVendor", System.getProperty("java.vendor"));
            sys.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
            sys.put("maxMemoryMb", Runtime.getRuntime().maxMemory() / 1048576);
            List<String> args = new ArrayList<String>();
            for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) if (a.startsWith("-X")) args.add(a);
            sys.put("jvmArgs", args);
            o.put("system", sys);
            File dir = new File(Minecraft.getMinecraft().mcDataDir, "logs");
            dir.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir, "mytic-diagnose.json")), StandardCharsets.UTF_8);
            try {
                w.write(GSON.toJson(o));
            } finally {
                w.close();
            }
        } catch (Exception e) {
            MyticClient.LOG.debug("Diagnose nicht geschrieben", e);
        }
    }

    /** Klasse → Mod über die Jar (CodeSource) der Forge-Mods */
    static final class ForgeMods implements SamplingProfiler.ModResolver {
        private final Map<String, String> bySource = new HashMap<String, String>();

        ForgeMods() {
            for (ModContainer m : Loader.instance().getModList()) {
                File source = m.getSource();
                if (source == null || !source.isFile()) continue;
                try {
                    bySource.put(source.getCanonicalPath(), m.getModId());
                } catch (Exception ignored) {
                    // überspringen
                }
            }
        }

        @Override
        public String modOf(String className) {
            try {
                Class<?> c = Class.forName(className, false, Launch.classLoader);
                CodeSource cs = c.getProtectionDomain() == null ? null : c.getProtectionDomain().getCodeSource();
                URL url = cs == null ? null : cs.getLocation();
                if (url != null) {
                    String path = url.getPath();
                    int bang = path.indexOf("!/");
                    if (bang >= 0) path = path.substring(0, bang);
                    if (path.startsWith("file:")) path = path.substring(5);
                    String mod = bySource.get(new File(java.net.URLDecoder.decode(path, "UTF-8")).getCanonicalPath());
                    if (mod != null) return mod;
                }
            } catch (Throwable ignored) {
                // Klasse nicht greifbar
            }
            if (className.startsWith("net.minecraftforge.") || className.startsWith("cpw.mods.")) return "forge";
            if (className.startsWith("net.minecraft.") || className.startsWith("com.mojang.")) return "minecraft";
            return null;
        }
    }
}
