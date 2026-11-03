package de.myticlegacy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live-Messung für die Lag-Diagnose im Launcher: Ruckler (lange Bilder), Speicherbereinigungs-Pausen, Arbeitsspeicher,
 * FPS und Ping – zusammengefasst pro Sitzung in logs/mytic-diagnose.json (alle 30 s und beim Beenden). Kostet pro Bild
 * nur eine Zeitmessung, pro Sekunde ein paar Werte.
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
