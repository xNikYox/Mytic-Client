package de.myticlegacy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.server.packs.resources.ReloadInstance;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live-Messung für die Lag-Diagnose im Launcher: Ruckler (lange Bilder), Speicherbereinigungs-Pausen, Arbeitsspeicher,
 * FPS und Ping – zusammengefasst pro Sitzung in logs/mytic-diagnose.json (alle 30 s und beim Beenden). Kostet pro Bild
 * nur eine Zeitmessung, pro Sekunde ein paar Werte.
 * Dazu Zeiten für die Diagnose im Launcher: Spielstart bis Hauptmenü, Server-Verbindung bis Welt geladen (mit längstem
 * Bild während des Joins) und Ressourcen-Neuladen (mit Anzahl der Pakete) – geprüft einmal pro Tick, ohne eigene Mixins.
 */
public final class Diagnose {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SPIKE_NS = 100_000_000L, HITCH_NS = 250_000_000L;
    private static final AtomicBoolean writing = new AtomicBoolean();

    private static long lastFrame, lastMixinFrame, lastSecond, lastWrite, gcBefore = -1;
    private static long secondMaxFrame;
    private static final long startedAt = System.currentTimeMillis();

    // Sitzungswerte (nur im Spiel mit Fenster im Vordergrund – Hintergrund-FPS verfälscht sonst alles)
    private static long playSeconds, fpsSum, lowFpsSeconds, spikes, hitches, maxFrameMs, gcMs, gcMaxPerSecond, heapMaxPct, heapMaxMb;
    private static long pingSamples, pingSum, pingMax, highPingSeconds, gcBusySeconds;
    private static int renderDistance, simulationDistance;
    private static String server = "";
    private static final Map<Integer, Integer> fpsHistogram = new LinkedHashMap<>();

    // Zeiten (Join, Neuladen, Spielstart); werden im Render-Thread geschrieben, beim Speichern kopiert
    private static final int KEEP = 10, AFTER_JOIN_MS = 5000;
    private static long titleAtMs = -1;
    private static long joinStart, joinLevelAt, joinWorldAt;
    private static volatile boolean joinTracking;
    private static volatile long joinMaxFrameNs, afterJoinMaxFrameNs;
    private static String joinKind, joinServer;
    private static long reloadStart;
    private static int reloadPacks;
    private static boolean reloadInitial = true;
    private static long fullSince;
    private static Map<String, Object> stuckHint;
    private static final List<Map<String, Object>> hints = new ArrayList<>();
    private static final List<Map<String, Object>> joins = new ArrayList<>(), reloads = new ArrayList<>();

    private Diagnose() {
    }

    /** Pro Bild aus Minecraft.runTick (ClipCaptureMixin) */
    public static void frame() {
        lastMixinFrame = System.nanoTime();
        count(lastMixinFrame);
    }

    /** Ersatz, falls der Mixin in einer Version nicht greift: aus dem HUD-Zeichnen */
    public static void hudFrame() {
        long now = System.nanoTime();
        if (now - lastMixinFrame > 2_000_000_000L) count(now);
    }

    private static void count(long now) {
        if (lastFrame != 0) {
            long dt = now - lastFrame;
            if (dt > secondMaxFrame) secondMaxFrame = dt;
            if (joinTracking) {
                if (joinWorldAt == 0) {
                    if (dt > joinMaxFrameNs) joinMaxFrameNs = dt;
                } else if (dt > afterJoinMaxFrameNs) afterJoinMaxFrameNs = dt;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.isWindowActive() && dt < 10_000_000_000L) {
                if (dt > SPIKE_NS) spikes++;
                if (dt > HITCH_NS) hitches++;
                maxFrameMs = Math.max(maxFrameMs, dt / 1_000_000);
            }
        }
        lastFrame = now;
    }

    /** Jeden Tick (20×/s) aus MyticClient; einmal pro Sekunde wird gemessen */
    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        phases(mc, now);
        if (now - lastSecond < 1000) return;
        lastSecond = now;
        long gc = gcPauseMs();
        long gcDelta = gcBefore < 0 ? 0 : Math.max(0, gc - gcBefore);
        gcBefore = gc;
        secondMaxFrame = 0;
        if (mc.level != null && mc.isWindowActive() && mc.player != null) {
            playSeconds++;
            int fps = mc.getFps();
            fpsSum += fps;
            if (fps < 30) lowFpsSeconds++;
            fpsHistogram.merge(Math.min(300, fps / 10 * 10), 1, Integer::sum);
            gcMs += gcDelta;
            gcMaxPerSecond = Math.max(gcMaxPerSecond, gcDelta);
            if (gcDelta > 50) gcBusySeconds++;
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            heapMaxPct = Math.max(heapMaxPct, used * 100 / Math.max(1, rt.maxMemory()));
            heapMaxMb = rt.maxMemory() / 1048576;
            renderDistance = mc.options.renderDistance().get();
            simulationDistance = mc.options.simulationDistance().get();
            if (mc.getConnection() != null && !mc.hasSingleplayerServer()) {
                PlayerInfo me = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (me != null && me.getLatency() > 0) {
                    int ping = me.getLatency();
                    pingSamples++;
                    pingSum += ping;
                    pingMax = Math.max(pingMax, ping);
                    if (ping > 150) highPingSeconds++;
                }
                if (mc.getCurrentServer() != null) server = mc.getCurrentServer().ip;
            } else if (mc.hasSingleplayerServer()) server = "Einzelspieler";
        }
        if (now - lastWrite > 30_000) {
            lastWrite = now;
            write();
        }
    }

    /** Übergänge erkennen: Ladebildschirm (Neuladen), erstes Menü, Verbindung → Welt geladen */
    private static void phases(Minecraft mc, long now) {
        boolean loadingOverlay = mc.getOverlay() instanceof LoadingOverlay;
        Screen screen = de.myticlegacy.client.compat.Compat.screen(mc);
        // Spielstart (JVM) bis zum ersten Menü ohne Ladebildschirm
        if (titleAtMs < 0 && !loadingOverlay && screen != null) {
            titleAtMs = now - ManagementFactory.getRuntimeMXBean().getStartTime();
            write();
        }
        // Ressourcen neu laden (Start, F3+T, Pakete ändern)
        if (loadingOverlay && reloadStart == 0) {
            reloadStart = now;
            reloadPacks = packCount(mc);
            fullSince = 0;
            stuckHint = null;
            de.myticlegacy.client.diag.SamplingProfiler.reloadPhase(true);
        } else if (loadingOverlay) {
            stuckCheck(mc, now);
        } else if (!loadingOverlay && reloadStart != 0) {
            de.myticlegacy.client.diag.SamplingProfiler.reloadPhase(false);
            if (stuckHint != null) finishStuck(now);
            stuckHint = null;
            fullSince = 0;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("at", now);
            r.put("ms", now - reloadStart);
            r.put("packs", reloadPacks);
            r.put("initial", reloadInitial);
            reloadInitial = false;
            reloadStart = 0;
            keep(reloads, r);
        }
        // Join: Verbindung (ConnectScreen) bzw. Einzelspieler-Start → Welt da → kein Ladebildschirm mehr
        // Einzelspieler-Messung, die in Wahrheit das Verlassen einer Welt war: bei Server-Verbindung neu beginnen
        if (joinTracking && joinLevelAt == 0 && "singleplayer".equals(joinKind) && screen instanceof ConnectScreen) {
            joinTracking = false;
            joinStart = 0;
        }
        if (joinStart == 0 && !joinTracking) {
            if (mc.level == null && (screen instanceof ConnectScreen || mc.getSingleplayerServer() != null)) {
                joinStart = now;
                joinLevelAt = 0;
                joinWorldAt = 0;
                joinMaxFrameNs = 0;
                afterJoinMaxFrameNs = 0;
                joinKind = screen instanceof ConnectScreen ? "server" : "singleplayer";
                joinServer = "";
                joinTracking = true;
            }
        } else if (joinTracking && joinWorldAt == 0) {
            if (joinLevelAt == 0 && mc.level != null) joinLevelAt = now;
            if (mc.getCurrentServer() != null) joinServer = mc.getCurrentServer().ip;
            if (mc.level != null && mc.player != null && !de.myticlegacy.client.compat.LevelLoading.active(mc)) {
                joinWorldAt = now;
            } else if (mc.level == null && (screen instanceof DisconnectedScreen || screen instanceof TitleScreen || screen instanceof de.myticlegacy.client.gui.MyticTitleScreen
                    || screen instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen || now - joinStart > 300_000)) {
                joinTracking = false; // abgebrochen oder gescheitert
                joinStart = 0;
            }
        } else if (joinTracking && now - joinWorldAt >= AFTER_JOIN_MS) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("at", joinStart);
            j.put("kind", joinKind);
            j.put("server", "singleplayer".equals(joinKind) ? "Einzelspieler" : joinServer);
            j.put("connectMs", (joinLevelAt == 0 ? joinWorldAt : joinLevelAt) - joinStart);
            j.put("loadingMs", joinLevelAt == 0 ? 0 : joinWorldAt - joinLevelAt);
            j.put("totalMs", joinWorldAt - joinStart);
            j.put("maxFrameMs", joinMaxFrameNs / 1_000_000L);
            j.put("afterJoinMaxFrameMs", afterJoinMaxFrameNs / 1_000_000L);
            joinTracking = false;
            joinStart = 0;
            keep(joins, j);
        }
        if (mc.level == null && joinTracking && joinWorldAt != 0) { // direkt wieder raus
            joinTracking = false;
            joinStart = 0;
        }
    }

    /** Ladebalken steht bei 100 %, der Ladebildschirm geht aber nicht weg – mit rrls ein bekanntes Problem */
    private static void stuckCheck(Minecraft mc, long now) {
        if (stuckHint != null || !FabricLoader.getInstance().isModLoaded("rrls")) return;
        float p = reloadProgress(mc);
        if (p < 0.999f) {
            fullSince = 0;
            return;
        }
        if (fullSince == 0) fullSince = now;
        else if (now - fullSince > 10_000) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("id", "rrls-stuck");
            h.put("level", "warn");
            h.put("at", now);
            h.put("seconds", (now - fullSince) / 1000);
            h.put("title", "Ladebalken hing bei 100 % (rrls)");
            h.put("detail", stuckDetail("über 10 s"));
            stuckHint = h;
            keep(hints, h);
        }
    }

    /** Tatsächliche Dauer bei 100 % nachtragen */
    private static void finishStuck(long now) {
        long s = (now - fullSince) / 1000;
        synchronized (Diagnose.class) {
            stuckHint.put("seconds", s);
            stuckHint.put("detail", stuckDetail(s + " s"));
        }
        write();
    }

    private static String stuckDetail(String time) {
        return "Beim Neuladen der Ressourcen stand der Ladebalken " + time + " auf 100 %. rrls (Remove Reloading Screen) ist installiert und kann den "
                + "Ladebalken hängen lassen bzw. Ladezeiten verlängern – zum Testen deaktivieren. Messen: /myticdiagnose reload";
    }

    private static java.lang.reflect.Field reloadField;
    private static boolean reloadFieldSearched;

    /** Fortschritt des laufenden Neuladens (0…1, -1 = unbekannt) – Feld vom Typ ReloadInstance im LoadingOverlay */
    private static float reloadProgress(Minecraft mc) {
        try {
            if (!reloadFieldSearched) {
                reloadFieldSearched = true;
                for (java.lang.reflect.Field f : LoadingOverlay.class.getDeclaredFields()) {
                    if (ReloadInstance.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        reloadField = f;
                        break;
                    }
                }
            }
            if (reloadField == null) return -1;
            Object r = reloadField.get(mc.getOverlay());
            return r instanceof ReloadInstance ? ((ReloadInstance) r).getActualProgress() : -1;
        } catch (Throwable t) {
            reloadField = null;
            return -1;
        }
    }

    private static int packCount(Minecraft mc) {
        try {
            return mc.getResourcePackRepository().getSelectedIds().size();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static synchronized void keep(List<Map<String, Object>> list, Map<String, Object> entry) {
        list.add(entry);
        while (list.size() > KEEP) list.remove(0);
        write();
    }

    /** Summe der Pausen der Speicherbereinigung (nur echte Pausen, keine nebenläufige Arbeit von ZGC/G1) */
    private static long gcPauseMs() {
        long sum = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            String n = b.getName().toLowerCase(Locale.ROOT);
            if (n.contains("cycles") || n.contains("concurrent")) continue;
            long t = b.getCollectionTime();
            if (t > 0) sum += t;
        }
        return sum;
    }

    /** Beim Beenden: sofort und blockierend schreiben (Shutdown-Hook) */
    public static void flush() {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("logs");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("mytic-diagnose.json"), json(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MyticClient.LOG.debug("Diagnose nicht geschrieben", e);
        }
    }

    /** Zusammenfassung schreiben (im Hintergrund) */
    public static void write() {
        String json = json();
        if (!writing.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                Path dir = FabricLoader.getInstance().getGameDir().resolve("logs");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("mytic-diagnose.json"), json, StandardCharsets.UTF_8);
            } catch (Exception e) {
                MyticClient.LOG.debug("Diagnose nicht geschrieben", e);
            } finally {
                writing.set(false);
            }
        }, "Mytic Diagnose");
        t.setDaemon(true);
        t.start();
    }

    private static synchronized String json() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("version", 1);
        o.put("mytic", MyticClient.VERSION);
        o.put("startedAt", startedAt);
        o.put("updatedAt", System.currentTimeMillis());
        o.put("playSeconds", playSeconds);
        o.put("fpsAvg", playSeconds == 0 ? 0 : fpsSum / playSeconds);
        o.put("fps1Low", percentileLow());
        o.put("lowFpsSeconds", lowFpsSeconds);
        o.put("spikes", spikes);
        o.put("hitches", hitches);
        o.put("maxFrameMs", maxFrameMs);
        o.put("gcPauseMs", gcMs);
        o.put("gcMaxPerSecondMs", gcMaxPerSecond);
        o.put("gcBusySeconds", gcBusySeconds);
        o.put("heapMaxPct", heapMaxPct);
        o.put("heapMaxMb", heapMaxMb);
        o.put("pingAvg", pingSamples == 0 ? 0 : pingSum / pingSamples);
        o.put("pingMax", pingMax);
        o.put("highPingSeconds", highPingSeconds);
        o.put("renderDistance", renderDistance);
        o.put("simulationDistance", simulationDistance);
        o.put("server", server);
        o.put("gc", ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName).toList());
        o.put("cpus", Runtime.getRuntime().availableProcessors());
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("gameStartToTitleMs", titleAtMs);
        t.put("joins", new ArrayList<>(joins));
        t.put("reloads", new ArrayList<>(reloads));
        o.put("timings", t);
        List<Map<String, Object>> hs = new ArrayList<>();
        for (Map<String, Object> h : hints) hs.add(new LinkedHashMap<>(h));
        o.put("hints", hs);
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("minecraft", de.myticlegacy.client.compat.Compat.versionName());
        sys.put("java", System.getProperty("java.version"));
        sys.put("javaVendor", System.getProperty("java.vendor"));
        sys.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        sys.put("maxMemoryMb", Runtime.getRuntime().maxMemory() / 1048576);
        sys.put("jvmArgs", ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(a -> a.startsWith("-X")).toList());
        o.put("system", sys);
        return GSON.toJson(o);
    }

    /** FPS, unter denen die schlechtesten 5 % der Sekunden lagen */
    private static int percentileLow() {
        if (playSeconds == 0) return 0;
        long limit = Math.max(1, playSeconds / 20), seen = 0;
        for (int bucket = 0; bucket <= 300; bucket += 10) {
            seen += fpsHistogram.getOrDefault(bucket, 0);
            if (seen >= limit) return bucket;
        }
        return 0;
    }
}
