package de.myticlegacy.client.diag;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * „Diagnose-Messung“: einfacher Sampling-Profiler für den Render-Thread – auch ohne spark nutzbar.
 * Ein Daemon-Thread holt alle 5 ms den Stack des Render-Threads (Thread.getStackTrace), zählt die obersten Methoden
 * (selbst) und alle Methoden im Stack (gesamt) und ordnet jede Klasse über ihre Jar einer Mod zu. Am Ende steht ein
 * lesbarer Bericht im Diagnose-Ordner. Ohne laufende Messung gibt es keinen Thread und keine Kosten.
 * Reines Java 8 – wird auch für 1.8.9 (Forge) mitgebaut.
 */
public final class SamplingProfiler {
    /** Klasse → Mod-ID (null = unbekannt). Wird nur im Mess-Thread aufgerufen. */
    public interface ModResolver {
        String modOf(String className);
    }

    public static final int INTERVAL_MS = 5;
    private static final int MAX_DEPTH = 160;
    private static final int TOP = 25;
    /** Mixin-Handler tragen die Mod-ID im Namen: handler$zza000$modid$methode (auch redirect$, wrapOperation$ …) */
    private static final Pattern MIXIN_HANDLER = Pattern.compile("^[a-zA-Z]+\\$[a-z]{3}\\d{3}\\$([a-z][a-z0-9_.-]*)\\$");
    private static volatile SamplingProfiler running;

    private final Thread target;
    private final ModResolver resolver;
    private final File dir;
    private final int seconds;
    private final String header;
    private final Callback done;
    private volatile boolean stop;

    // Zähler (nur im Mess-Thread)
    private int samples, waiting;
    private final Map<String, int[]> self = new HashMap<String, int[]>();
    private final Map<String, int[]> total = new HashMap<String, int[]>();
    private final Map<String, int[]> modSelf = new HashMap<String, int[]>();
    private final Map<String, int[]> modTotal = new HashMap<String, int[]>();
    private final Map<String, String> classMods = new HashMap<String, String>();
    private long maxSampleGapMs;

    /** Ergebnis: Datei oder Fehlertext (wird im Mess-Thread aufgerufen) */
    public interface Callback {
        void finished(File report, String error);
    }

    private SamplingProfiler(Thread target, ModResolver resolver, File dir, int seconds, String header, Callback done) {
        this.target = target;
        this.resolver = resolver;
        this.dir = dir;
        this.seconds = seconds;
        this.header = header;
        this.done = done;
    }

    public static boolean isRunning() {
        return running != null;
    }

    /** Startet die Messung (false, wenn schon eine läuft) */
    public static synchronized boolean start(Thread target, ModResolver resolver, File dir, int seconds, String header, Callback done) {
        if (running != null) return false;
        final SamplingProfiler p = new SamplingProfiler(target, resolver, dir, Math.max(5, Math.min(600, seconds)), header, done);
        running = p;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                p.run();
            }
        }, "Mytic Diagnose-Messung");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY + 1);
        t.start();
        return true;
    }

    /** Laufende Messung vorzeitig beenden (Bericht wird trotzdem geschrieben) */
    public static boolean stopRunning() {
        SamplingProfiler p = running;
        if (p == null) return false;
        p.stop = true;
        return true;
    }

    private void run() {
        File report = null;
        String error = null;
        long started = System.currentTimeMillis();
        try {
            long end = started + seconds * 1000L;
            long last = System.nanoTime();
            while (!stop && System.currentTimeMillis() < end && target.isAlive()) {
                sample();
                long now = System.nanoTime();
                maxSampleGapMs = Math.max(maxSampleGapMs, (now - last) / 1_000_000L);
                last = now;
                try {
                    Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
            report = write(System.currentTimeMillis() - started);
        } catch (Throwable t) {
            error = t.toString();
        } finally {
            running = null;
        }
        if (done != null) done.finished(report, error);
    }

    private void sample() {
        StackTraceElement[] stack = target.getStackTrace();
        if (stack.length == 0) return;
        samples++;
        Thread.State state = target.getState();
        if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING || state == Thread.State.BLOCKED) waiting++;
        int depth = Math.min(stack.length, MAX_DEPTH);
        // selbst: oberste Methode; Mod: oberster Frame außerhalb von Java selbst
        inc(self, method(stack[0]));
        String selfMod = null;
        Set<String> seenMethods = new HashSet<String>();
        Set<String> seenMods = new HashSet<String>();
        for (int i = 0; i < depth; i++) {
            StackTraceElement e = stack[i];
            String m = method(e);
            if (seenMethods.add(m)) inc(total, m);
            String mod = modOf(e);
            if (selfMod == null && !"java".equals(mod)) selfMod = mod;
            if (seenMods.add(mod)) inc(modTotal, mod);
        }
        inc(modSelf, selfMod == null ? "java" : selfMod);
    }

    private static String method(StackTraceElement e) {
        return e.getClassName() + "." + e.getMethodName();
    }

    /** Mod eines Frames: Mixin-Handler nach Namen, sonst über die Klasse (mit Zwischenspeicher) */
    String modOf(StackTraceElement e) {
        Matcher m = MIXIN_HANDLER.matcher(e.getMethodName());
        if (m.find()) return m.group(1);
        String cls = e.getClassName();
        String mod = classMods.get(cls);
        if (mod == null) {
            mod = builtin(cls);
            if (mod == null) {
                try {
                    mod = resolver == null ? null : resolver.modOf(cls);
                } catch (Throwable t) {
                    mod = null;
                }
            }
            if (mod == null) mod = "unbekannt";
            classMods.put(cls, mod);
        }
        return mod;
    }

    /** Feste Zuordnung ohne Nachschlagen */
    static String builtin(String cls) {
        if (cls.startsWith("java.") || cls.startsWith("javax.") || cls.startsWith("jdk.") || cls.startsWith("sun.") || cls.startsWith("com.sun.")) return "java";
        if (cls.startsWith("org.lwjgl.")) return "lwjgl";
        if (cls.startsWith("de.myticlegacy.")) return "myticclient";
        return null;
    }

    private static void inc(Map<String, int[]> map, String key) {
        int[] c = map.get(key);
        if (c == null) map.put(key, new int[] {1});
        else c[0]++;
    }

    private static List<Map.Entry<String, int[]>> sorted(Map<String, int[]> map) {
        List<Map.Entry<String, int[]>> list = new ArrayList<Map.Entry<String, int[]>>(map.entrySet());
        Collections.sort(list, (a, b) -> b.getValue()[0] - a.getValue()[0]);
        return list;
    }

    private String pct(int n) {
        return String.format(Locale.ROOT, "%5.1f %%", samples == 0 ? 0.0 : n * 100.0 / samples);
    }

    private String modLabel(String mod) {
        if ("java".equals(mod)) return "java (JVM/Bibliotheken)";
        if ("lwjgl".equals(mod)) return "lwjgl (OpenGL/Treiber/Fenster)";
        if ("minecraft".equals(mod)) return "minecraft (Spiel selbst)";
        return mod;
    }

    private File write(long durationMs) throws Exception {
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        File file = new File(dir, "Mytic-Profil-" + stamp + ".txt");
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        sb.append("Mytic Diagnose-Messung (Render-Thread)").append(nl);
        sb.append("======================================").append(nl);
        if (header != null) sb.append(header).append(nl);
        sb.append(String.format(Locale.ROOT, "Dauer: %.1f s, Proben: %d (alle %d ms), längste Lücke zwischen Proben: %d ms%n",
                durationMs / 1000.0, samples, INTERVAL_MS, maxSampleGapMs));
        sb.append(String.format(Locale.ROOT, "Thread wartend/blockiert: %s (z. B. auf die Grafikkarte, VSync oder Sperren)%n", pct(waiting)));
        sb.append(nl).append("Anteil pro Mod (selbst = Mod war oben im Stack, beteiligt = irgendwo im Stack)").append(nl);
        sb.append("--------------------------------------------------------------------------").append(nl);
        Map<String, int[]> inc = modTotal;
        for (Map.Entry<String, int[]> e : sorted(modSelf)) {
            int[] t = inc.get(e.getKey());
            sb.append(String.format(Locale.ROOT, "%s selbst  %s beteiligt  %s%n", pct(e.getValue()[0]), pct(t == null ? 0 : t[0]), modLabel(e.getKey())));
        }
        for (Map.Entry<String, int[]> e : sorted(modTotal)) {
            if (modSelf.containsKey(e.getKey())) continue;
            sb.append(String.format(Locale.ROOT, "%s selbst  %s beteiligt  %s%n", pct(0), pct(e.getValue()[0]), modLabel(e.getKey())));
        }
        appendTop(sb, nl, "Top-Methoden (selbst – hier wurde gerade gerechnet)", self);
        appendTop(sb, nl, "Top-Methoden (gesamt – inklusive aufgerufener Methoden)", total);
        sb.append(nl).append("Hinweise: Minecraft-Methoden erscheinen in Fabric mit Intermediary-Namen (class_/method_).").append(nl);
        sb.append("Mixin-Code einer Mod wird über den Handler-Namen (handler$…$modid$…) dieser Mod zugeordnet.").append(nl);
        Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            w.write(sb.toString());
        } finally {
            w.close();
        }
        return file;
    }

    private void appendTop(StringBuilder sb, String nl, String title, Map<String, int[]> map) {
        sb.append(nl).append(title).append(nl);
        for (int i = 0; i < title.length(); i++) sb.append('-');
        sb.append(nl);
        int n = 0;
        for (Map.Entry<String, int[]> e : sorted(map)) {
            if (n++ >= TOP) break;
            String m = e.getKey();
            int dot = m.lastIndexOf('.');
            String mod = modOf(new StackTraceElement(dot < 0 ? m : m.substring(0, dot), dot < 0 ? "" : m.substring(dot + 1), null, -1));
            sb.append(String.format(Locale.ROOT, "%s  %-18s %s%n", pct(e.getValue()[0]), "[" + mod + "]", m));
        }
    }
}
