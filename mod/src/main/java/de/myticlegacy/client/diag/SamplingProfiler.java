package de.myticlegacy.client.diag;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * „Diagnose-Messung“: einfacher Sampling-Profiler – auch ohne spark nutzbar.
 * Ein Daemon-Thread holt alle 10 ms die Stacks ALLER Threads (ThreadMXBean.dumpAllThreads), fasst sie zu Thread-Familien
 * zusammen (Render-Thread, Worker-Main-*, IO-Worker, ForkJoin-Pool, Chunk-Builder, C2ME, Server-Thread …), trennt
 * Leerlauf (warten, parken, schlafen, FPS-Begrenzer, VSync, epoll/select) von echter Arbeit und ordnet jede Klasse über
 * ihre Jar einer Mod zu. Zeitfenster, in denen Ressourcen neu geladen werden ({@link #reloadPhase}), werden markiert und
 * getrennt ausgewertet. Am Ende steht ein lesbarer Bericht im Diagnose-Ordner.
 * Ohne laufende Messung gibt es keinen Thread und keine Kosten (reloadPhase ist dann nur ein Feldzugriff).
 * Reines Java 8 – wird auch für 1.8.9 (Forge) mitgebaut.
 */
public final class SamplingProfiler {
    /** Klasse → Mod-ID (null = unbekannt). Wird nur im Mess-Thread aufgerufen. */
    public interface ModResolver {
        String modOf(String className);
    }

    /** Ergebnis: Datei oder Fehlertext (wird im Mess-Thread aufgerufen) */
    public interface Callback {
        void finished(File report, String error);
    }

    public static final int INTERVAL_MS = 10;
    static final String THREAD_NAME = "Mytic Diagnose-Messung";
    private static final int MAX_DEPTH = 160;
    private static final int TOP = 25, TOP_FAMILY = 12, TOP_FAMILY_MODS = 10, MAX_FAMILY_SECTIONS = 10;
    /** Längste Zeit, die eine einzelne Probe zählt (sonst verzerren lange Pausen die Sekunden) */
    private static final long MAX_DT_MS = 200;
    /** Mixin-Handler tragen die Mod-ID im Namen: handler$zza000$modid$methode (auch redirect$, wrapOperation$ …) */
    private static final Pattern MIXIN_HANDLER = Pattern.compile("^[a-zA-Z]+\\$[a-z]{3}\\d{3}\\$([a-z][a-z0-9_.-]*)\\$");
    /** Laufende Nummer am Ende des Thread-Namens („Worker-Main-12“, „Chunk Render Task Executor #3“, „pool-2-thread-7“) */
    private static final Pattern TRAILING_NUMBER = Pattern.compile("[\\s#_.:-]*\\d+$");
    /** JVM-Threads, die nie für Minecraft arbeiten */
    private static final Set<String> IGNORED = new HashSet<String>(java.util.Arrays.asList("Reference Handler", "Finalizer", "Signal Dispatcher",
            "Attach Listener", "Common-Cleaner", "DestroyJavaVM", "Notification Thread", "process reaper", THREAD_NAME));

    // Leerlauf-Arten (null = aktiv)
    static final String IDLE_FPS = "fps", IDLE_VSYNC = "vsync", IDLE_WAIT = "wait", IDLE_IO = "io", IDLE_BLOCKED = "blocked";

    private static volatile SamplingProfiler running;
    private static volatile boolean reloadActive;

    private final Thread target;
    private final long targetId;
    private final ModResolver resolver;
    private final File dir;
    private final int seconds;
    private final String header;
    private final Callback done;
    private volatile boolean stop;

    // Zähler (nur im Mess-Thread)
    private int snapshots;
    private long maxSampleGapMs, dumpNanos;
    private final Map<String, String> classMods = new HashMap<String, String>();
    /** Render-Thread: alle Proben (auch Leerlauf), wie bisher */
    private final Stats render = new Stats("Render-Thread");
    private final Map<String, int[]> renderIdle = new HashMap<String, int[]>();
    /** Thread-Familien: nur aktive Proben gehen in Mods/Methoden ein */
    private final Map<String, Family> families = new HashMap<String, Family>();
    /** Alle Threads außer Render-Thread, nur Proben während des Neuladens */
    private final Stats reloadAll = new Stats("Neuladen");
    // Neuladen-Fenster (aus reloadPhase, anderer Thread)
    private final List<long[]> reloads = new ArrayList<long[]>();

    /** Zähler für eine Gruppe von Proben */
    static class Stats {
        final String name;
        int samples, active;
        long activeMs, totalMs;
        final Map<String, int[]> self = new HashMap<String, int[]>();
        final Map<String, int[]> total = new HashMap<String, int[]>();
        final Map<String, int[]> modSelf = new HashMap<String, int[]>();
        final Map<String, int[]> modTotal = new HashMap<String, int[]>();

        Stats(String name) {
            this.name = name;
        }
    }

    static final class Family extends Stats {
        final Set<String> threads = new TreeSet<String>();
        int reloadActive;
        long reloadActiveMs;

        Family(String name) {
            super(name);
        }
    }

    private SamplingProfiler(Thread target, long targetId, ModResolver resolver, File dir, int seconds, String header, Callback done) {
        this.target = target;
        this.targetId = targetId;
        this.resolver = resolver;
        this.dir = dir;
        this.seconds = seconds;
        this.header = header;
        this.done = done;
    }

    /** Für den Selbsttest: Profiler ohne Thread, Proben kommen über accept() */
    static SamplingProfiler forTest(long renderThreadId, ModResolver resolver) {
        return new SamplingProfiler(null, renderThreadId, resolver, null, 60, "Test", null);
    }

    public static boolean isRunning() {
        return running != null;
    }

    /** Startet die Messung (false, wenn schon eine läuft). target = Render-Thread (wird zuerst und vollständig ausgewertet) */
    public static synchronized boolean start(Thread target, ModResolver resolver, File dir, int seconds, String header, Callback done) {
        if (running != null) return false;
        final SamplingProfiler p = new SamplingProfiler(target, target.getId(), resolver, dir, Math.max(5, Math.min(600, seconds)), header, done);
        if (reloadActive) p.reloadEvent(true, System.currentTimeMillis());
        running = p;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                p.run();
            }
        }, THREAD_NAME);
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

    /**
     * Ressourcen werden neu geladen (true) bzw. sind fertig (false) – aus der Zeitmessung der Diagnose, nur bei Wechseln.
     * Ohne laufende Messung kostet das nichts.
     */
    public static void reloadPhase(boolean active) {
        if (reloadActive == active) return;
        reloadActive = active;
        SamplingProfiler p = running;
        if (p != null) p.reloadEvent(active, System.currentTimeMillis());
    }

    void reloadEvent(boolean active, long now) {
        synchronized (reloads) {
            long[] last = reloads.isEmpty() ? null : reloads.get(reloads.size() - 1);
            if (active) {
                if (last == null || last[1] != 0) reloads.add(new long[] {now, 0});
            } else if (last != null && last[1] == 0) last[1] = now;
        }
    }

    private void run() {
        File report = null;
        String error = null;
        long started = System.currentTimeMillis();
        try {
            ThreadMXBean mx = null;
            Method dumpDepth = null;
            try {
                mx = ManagementFactory.getThreadMXBean();
                try { // Java 10+: Tiefe begrenzen spart Zeit
                    dumpDepth = ThreadMXBean.class.getMethod("dumpAllThreads", boolean.class, boolean.class, int.class);
                } catch (NoSuchMethodException ignored) {
                    dumpDepth = null;
                }
            } catch (Throwable t) {
                mx = null;
            }
            long end = started + seconds * 1000L;
            long last = System.nanoTime();
            long interval = INTERVAL_MS;
            while (!stop && System.currentTimeMillis() < end && (target == null || target.isAlive())) {
                long t0 = System.nanoTime();
                long dt = Math.min(MAX_DT_MS, Math.max(1, (t0 - last) / 1_000_000L));
                maxSampleGapMs = Math.max(maxSampleGapMs, (t0 - last) / 1_000_000L);
                last = t0;
                boolean reload = reloadActive;
                mx = snapshot(mx, dumpDepth, dt, reload);
                long cost = System.nanoTime() - t0;
                dumpNanos += cost;
                // Aufwand begrenzen: höchstens etwa ein Viertel eines Kerns für die Messung
                interval = Math.max(INTERVAL_MS, cost * 3 / 1_000_000L);
                try {
                    Thread.sleep(interval);
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

    /** Eine Momentaufnahme aller Threads; liefert null zurück, wenn ThreadMXBean nicht (mehr) geht */
    private ThreadMXBean snapshot(ThreadMXBean mx, Method dumpDepth, long dt, boolean reload) {
        snapshots++;
        if (mx != null) {
            try {
                ThreadInfo[] infos = dumpDepth != null ? (ThreadInfo[]) dumpDepth.invoke(mx, false, false, MAX_DEPTH) : mx.dumpAllThreads(false, false);
                for (ThreadInfo i : infos) {
                    if (i != null) accept(dt, i.getThreadName(), i.getThreadId(), i.getThreadState(), i.getStackTrace(), reload);
                }
                return mx;
            } catch (Throwable t) {
                // z. B. Sicherheitsmanager – auf Thread.getAllStackTraces ausweichen
            }
        }
        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            Thread t = e.getKey();
            accept(dt, t.getName(), t.getId(), t.getState(), e.getValue(), reload);
        }
        return null;
    }

    /** Eine Probe eines Threads verbuchen (auch vom Selbsttest aufgerufen) */
    void accept(long dt, String name, long id, Thread.State state, StackTraceElement[] stack, boolean reload) {
        if (stack == null || stack.length == 0 || name == null || IGNORED.contains(name)) return;
        if (state == Thread.State.NEW || state == Thread.State.TERMINATED) return;
        String idle = idleKind(state, stack);
        if (id == targetId) {
            render.samples++;
            render.totalMs += dt;
            if (idle != null) inc(renderIdle, idle);
            else {
                render.active++;
                render.activeMs += dt;
            }
            count(render, stack);
            return;
        }
        String fam = family(name);
        if (fam.isEmpty() || isGcOrJit(fam)) return;
        Family f = families.get(fam);
        if (f == null) families.put(fam, f = new Family(fam));
        if (f.threads.size() < 64) f.threads.add(name);
        f.samples++;
        f.totalMs += dt;
        if (idle != null) return;
        f.active++;
        f.activeMs += dt;
        count(f, stack);
        if (reload) {
            f.reloadActive++;
            f.reloadActiveMs += dt;
            reloadAll.samples++;
            reloadAll.active++;
            reloadAll.activeMs += dt;
            countMods(reloadAll, stack);
        }
    }

    private static boolean isGcOrJit(String fam) {
        // JVM-eigene Threads (nur bei Thread.getAllStackTraces überhaupt sichtbar)
        return fam.startsWith("GC ") || fam.startsWith("C1 ") || fam.startsWith("C2 ") || fam.startsWith("G1 ");
    }

    /** Thread-Familie: Name ohne laufende Nummer am Ende */
    static String family(String threadName) {
        String n = threadName.trim();
        String prev;
        do { // „Worker-Main-12“ → „Worker-Main“, „Netty Client IO #1“ → „Netty Client IO“
            prev = n;
            n = TRAILING_NUMBER.matcher(n).replaceFirst("").trim();
        } while (!n.equals(prev) && !n.isEmpty() && Character.isDigit(n.charAt(n.length() - 1)));
        return n.isEmpty() ? prev : n;
    }

    /**
     * Wartet der Thread gerade, statt zu rechnen? Liefert die Art (IDLE_*) oder null (= aktiv).
     * Geparkt/wartend/schlafend laut Zustand, sonst ganz oben im Stack ein bekannter Warte-Aufruf (FPS-Begrenzer über
     * glfwWaitEventsTimeout/Display.sync, VSync beim Bildtausch, epoll/select/Socket-Lesen), ggf. hinter JNI-Hüllen.
     */
    static String idleKind(Thread.State state, StackTraceElement[] stack) {
        String found = null;
        for (int i = 0; i < Math.min(6, stack.length); i++) {
            String kind = idleFrame(stack[i].getClassName(), stack[i].getMethodName());
            if (kind != null) {
                if (found == null || IDLE_WAIT.equals(found)) found = kind;
                if (!IDLE_WAIT.equals(kind)) break; // genauer geht es nicht
                continue; // Thread.sleep/park: darunter vielleicht der FPS-Begrenzer (Display.sync)
            }
            if (!wrapper(stack[i].getClassName())) break;
        }
        if (found != null && !IDLE_WAIT.equals(found)) return found;
        if (state == Thread.State.BLOCKED) return IDLE_BLOCKED;
        if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) return IDLE_WAIT;
        return found;
    }

    /** Hüllen um den eigentlichen Warte-Aufruf (JNI-Aufrufe von LWJGL, JDK-Interna) */
    private static boolean wrapper(String cls) {
        return cls.startsWith("org.lwjgl.system.") || cls.startsWith("java.") || cls.startsWith("jdk.internal.") || cls.startsWith("sun.");
    }

    static String idleFrame(String cls, String method) {
        String m = method.toLowerCase(Locale.ROOT);
        if (cls.startsWith("org.lwjgl.")) {
            if (m.contains("waitevents")) return IDLE_FPS; // FPS-Begrenzer (limitDisplayFPS) und Menü-Leerlauf
            if (m.contains("swapbuffers") || m.contains("swapinterval")) return IDLE_VSYNC;
            if ((m.equals("sync") && cls.endsWith(".Display")) || cls.endsWith(".Sync")) return IDLE_FPS; // 1.8.9: Display.sync
            return null;
        }
        if (m.equals("limitdisplayfps")) return IDLE_FPS; // Mojang-Namen (Entwicklung, unverschleierte Versionen)
        if (cls.equals("java.lang.Thread") || cls.equals("java.lang.VirtualThread")) {
            if (m.startsWith("sleep") || m.startsWith("yield") || m.equals("onspinwait")) return IDLE_WAIT;
            return null;
        }
        if (cls.equals("java.lang.Object") && m.startsWith("wait")) return IDLE_WAIT;
        if ((cls.endsWith(".Unsafe") || cls.endsWith(".LockSupport")) && m.startsWith("park")) return IDLE_WAIT;
        if (cls.startsWith("sun.nio.ch.") || cls.startsWith("java.net.") || cls.startsWith("sun.net.") || cls.startsWith("io.netty.channel.")) {
            if (m.equals("wait") || m.startsWith("epollwait") || m.startsWith("poll") || m.startsWith("select") || m.equals("doselect")
                    || m.startsWith("accept") || m.equals("socketaccept") || m.equals("read0") || m.equals("socketread0") || m.startsWith("kevent")) return IDLE_IO;
        }
        if (cls.equals("sun.misc.Signal") || cls.equals("jdk.internal.misc.Signal")) return IDLE_WAIT;
        return null;
    }

    private void count(Stats s, StackTraceElement[] stack) {
        int depth = Math.min(stack.length, MAX_DEPTH);
        inc(s.self, method(stack[0]));
        Set<String> seenMethods = new HashSet<String>();
        for (int i = 0; i < depth; i++) {
            String m = method(stack[i]);
            if (seenMethods.add(m)) inc(s.total, m);
        }
        countMods(s, stack);
    }

    /** selbst: oberster Frame außerhalb von Java selbst; beteiligt: irgendwo im Stack */
    private void countMods(Stats s, StackTraceElement[] stack) {
        int depth = Math.min(stack.length, MAX_DEPTH);
        String selfMod = null;
        Set<String> seenMods = new HashSet<String>();
        for (int i = 0; i < depth; i++) {
            String mod = modOf(stack[i]);
            if (selfMod == null && !"java".equals(mod)) selfMod = mod;
            if (seenMods.add(mod)) inc(s.modTotal, mod);
        }
        inc(s.modSelf, selfMod == null ? "java" : selfMod);
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

    private static int get(Map<String, int[]> map, String key) {
        int[] c = map.get(key);
        return c == null ? 0 : c[0];
    }

    private static List<Map.Entry<String, int[]>> sorted(Map<String, int[]> map) {
        List<Map.Entry<String, int[]>> list = new ArrayList<Map.Entry<String, int[]>>(map.entrySet());
        Collections.sort(list, (a, b) -> b.getValue()[0] != a.getValue()[0] ? b.getValue()[0] - a.getValue()[0] : a.getKey().compareTo(b.getKey()));
        return list;
    }

    private static String pct(int n, int of) {
        return String.format(Locale.ROOT, "%5.1f %%", of == 0 ? 0.0 : n * 100.0 / of);
    }

    private static String sec(long ms) {
        return String.format(Locale.GERMANY, "%.1f s", ms / 1000.0);
    }

    private static String modLabel(String mod) {
        if ("java".equals(mod)) return "java (JVM/Bibliotheken)";
        if ("lwjgl".equals(mod)) return "lwjgl (OpenGL/Treiber/Fenster)";
        if ("minecraft".equals(mod)) return "minecraft (Spiel selbst)";
        return mod;
    }

    private File write(long durationMs) throws Exception {
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        File file = new File(dir, "Mytic-Profil-" + stamp + ".txt");
        Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            w.write(report(durationMs));
        } finally {
            w.close();
        }
        return file;
    }

    /** Bericht als Text (auch vom Selbsttest aufgerufen) */
    String report(long durationMs) {
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        sb.append("Mytic Diagnose-Messung (alle Threads)").append(nl);
        sb.append("=====================================").append(nl);
        if (header != null) sb.append(header).append(nl);
        sb.append(String.format(Locale.ROOT, "Dauer: %.1f s, Momentaufnahmen: %d (alle %d ms, alle Threads), längste Lücke: %d ms, Messaufwand: %.2f ms je Aufnahme%n",
                durationMs / 1000.0, snapshots, INTERVAL_MS, maxSampleGapMs, snapshots == 0 ? 0.0 : dumpNanos / 1e6 / snapshots));
        appendReloads(sb, nl);

        // 1) Render-Thread (wie bisher, alle Proben)
        int rs = render.samples;
        int waiting = rs - render.active;
        sb.append(nl).append("Render-Thread").append(nl).append("-------------").append(nl);
        sb.append(String.format(Locale.ROOT, "Proben: %d, aktiv: %s, wartend/blockiert: %s%n", rs, pct(render.active, rs), pct(waiting, rs)));
        if (waiting > 0) {
            sb.append(String.format(Locale.ROOT, "  davon FPS-Begrenzer/Leerlauf %s · VSync/Bildtausch %s · warten/parken %s · blockiert (Sperre) %s · Netzwerk/Datei %s%n",
                    pct(get(renderIdle, IDLE_FPS), rs), pct(get(renderIdle, IDLE_VSYNC), rs), pct(get(renderIdle, IDLE_WAIT), rs),
                    pct(get(renderIdle, IDLE_BLOCKED), rs), pct(get(renderIdle, IDLE_IO), rs)));
        }
        sb.append(nl).append("Anteil pro Mod (selbst = Mod war oben im Stack, beteiligt = irgendwo im Stack)").append(nl);
        sb.append("--------------------------------------------------------------------------").append(nl);
        appendMods(sb, render, rs, Integer.MAX_VALUE);
        appendTop(sb, nl, "Top-Methoden (selbst – hier wurde gerade gerechnet)", render.self, rs, TOP);
        appendTop(sb, nl, "Top-Methoden (gesamt – inklusive aufgerufener Methoden)", render.total, rs, TOP);

        // 2) Übersicht der Thread-Familien
        List<Family> fams = new ArrayList<Family>(families.values());
        Collections.sort(fams, (a, b) -> a.activeMs != b.activeMs ? Long.compare(b.activeMs, a.activeMs) : a.name.compareTo(b.name));
        String title = "Übersicht aller Threads (aktive Thread-Sekunden; bei mehreren Threads einer Familie addiert)";
        sb.append(nl).append(title).append(nl);
        for (int i = 0; i < title.length(); i++) sb.append('-');
        sb.append(nl);
        sb.append(String.format(Locale.ROOT, "%10s  %8s  %7s  %s%n", "aktiv", "aktiv %", "Threads", "Familie"));
        sb.append(String.format(Locale.ROOT, "%10s  %8s  %7d  %s%n", sec(render.activeMs), pct(render.active, rs), 1, "Render-Thread"));
        int shown = 0, quiet = 0;
        for (Family f : fams) {
            if (f.active == 0) {
                quiet++;
                continue;
            }
            if (shown++ >= 30) break;
            sb.append(String.format(Locale.ROOT, "%10s  %8s  %7d  %s%n", sec(f.activeMs), pct(f.active, f.samples), f.threads.size(), f.name));
        }
        if (quiet > 0) sb.append("(").append(quiet).append(" weitere Thread-Familien nur im Leerlauf)").append(nl);

        // 3) Je Familie: Mods und Top-Methoden (nur aktive Proben)
        int sections = 0;
        for (Family f : fams) {
            if (f.active < 10 || f.activeMs < 300 || sections++ >= MAX_FAMILY_SECTIONS) continue;
            String t = "Thread-Familie „" + f.name + "“ – " + sec(f.activeMs) + " aktiv (" + pct(f.active, f.samples).trim() + " der Zeit), Threads: " + threadList(f);
            sb.append(nl).append(t).append(nl);
            for (int i = 0; i < Math.min(t.length(), 100); i++) sb.append('=');
            sb.append(nl).append("Anteil pro Mod (an der aktiven Zeit dieser Familie)").append(nl);
            appendMods(sb, f, f.active, TOP_FAMILY_MODS);
            appendTop(sb, nl, "Top-Methoden (selbst)", f.self, f.active, TOP_FAMILY);
            appendTop(sb, nl, "Top-Methoden (gesamt)", f.total, f.active, TOP_FAMILY);
        }

        // 4) Während des Neuladens (alle Threads außer Render-Thread)
        long reloadMs = reloadMs();
        if (reloadMs > 0 || reloadAll.active > 0) {
            String t = "Während des Neuladens der Ressourcen (" + sec(reloadMs) + ", alle Threads außer Render-Thread)";
            sb.append(nl).append(t).append(nl);
            for (int i = 0; i < t.length(); i++) sb.append('=');
            sb.append(nl);
            if (reloadAll.active == 0) sb.append("Keine aktiven Hintergrund-Threads beim Neuladen.").append(nl);
            else {
                sb.append("Aktive Thread-Sekunden je Familie:").append(nl);
                List<Family> rf = new ArrayList<Family>(fams);
                Collections.sort(rf, (a, b) -> Long.compare(b.reloadActiveMs, a.reloadActiveMs));
                int n = 0;
                for (Family f : rf) {
                    if (f.reloadActive == 0 || n++ >= TOP_FAMILY) break;
                    sb.append(String.format(Locale.ROOT, "%10s  %s%n", sec(f.reloadActiveMs), f.name));
                }
                sb.append(nl).append("Anteil pro Mod (an der aktiven Zeit beim Neuladen)").append(nl);
                appendMods(sb, reloadAll, reloadAll.active, TOP_FAMILY_MODS + 5);
            }
        }

        sb.append(nl).append("Hinweise: Minecraft-Methoden erscheinen in Fabric mit Intermediary-Namen (class_/method_).").append(nl);
        sb.append("Mixin-Code einer Mod wird über den Handler-Namen (handler$…$modid$…) dieser Mod zugeordnet.").append(nl);
        sb.append("Leerlauf (warten, parken, schlafen, FPS-Begrenzer, VSync, Netzwerk-Warten) zählt bei anderen Threads nicht als aktiv.").append(nl);
        sb.append("Neuladen messen: /myticdiagnose reload startet die Messung und lädt die Ressourcen neu (wie F3+T).").append(nl);
        return sb.toString();
    }

    /** Gesamtdauer der Neuladen-Fenster (offene bis jetzt) */
    private long reloadMs() {
        long sum = 0, now = System.currentTimeMillis();
        synchronized (reloads) {
            for (long[] r : reloads) sum += Math.max(0, (r[1] == 0 ? now : r[1]) - r[0]);
        }
        return sum;
    }

    private void appendReloads(StringBuilder sb, String nl) {
        List<long[]> list;
        synchronized (reloads) {
            list = new ArrayList<long[]>(reloads);
        }
        if (list.isEmpty()) {
            sb.append("Neuladen der Ressourcen während der Messung: keins").append(nl);
            return;
        }
        StringBuilder d = new StringBuilder();
        for (long[] r : list) {
            if (d.length() > 0) d.append(", ");
            d.append(r[1] == 0 ? "läuft noch" : sec(r[1] - r[0]));
        }
        sb.append("Neuladen der Ressourcen während der Messung: ").append(list.size()).append("× (Dauer ").append(d).append(")").append(nl);
    }

    private static String threadList(Family f) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String t : f.threads) {
            if (n++ >= 3) {
                sb.append(" …");
                break;
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(t);
        }
        return f.threads.size() + " (" + sb + ")";
    }

    private void appendMods(StringBuilder sb, Stats s, int of, int max) {
        String nl = System.lineSeparator();
        int n = 0;
        for (Map.Entry<String, int[]> e : sorted(s.modSelf)) {
            if (n++ >= max) return;
            sb.append(String.format(Locale.ROOT, "%s selbst  %s beteiligt  %s%n", pct(e.getValue()[0], of), pct(get(s.modTotal, e.getKey()), of), modLabel(e.getKey())));
        }
        for (Map.Entry<String, int[]> e : sorted(s.modTotal)) {
            if (s.modSelf.containsKey(e.getKey())) continue;
            if (n++ >= max) return;
            sb.append(String.format(Locale.ROOT, "%s selbst  %s beteiligt  %s%n", pct(0, of), pct(e.getValue()[0], of), modLabel(e.getKey())));
        }
        if (n == 0) sb.append("(keine Proben)").append(nl);
    }

    private void appendTop(StringBuilder sb, String nl, String title, Map<String, int[]> map, int of, int max) {
        sb.append(nl).append(title).append(nl);
        for (int i = 0; i < title.length(); i++) sb.append('-');
        sb.append(nl);
        int n = 0;
        for (Map.Entry<String, int[]> e : sorted(map)) {
            if (n++ >= max) break;
            String m = e.getKey();
            int dot = m.lastIndexOf('.');
            String mod = modOf(new StackTraceElement(dot < 0 ? m : m.substring(0, dot), dot < 0 ? "" : m.substring(dot + 1), null, -1));
            sb.append(String.format(Locale.ROOT, "%s  %-18s %s%n", pct(e.getValue()[0], of), "[" + mod + "]", m));
        }
    }
}
