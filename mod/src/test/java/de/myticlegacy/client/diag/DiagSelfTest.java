package de.myticlegacy.client.diag;

/**
 * Selbsttest für die Diagnose-Messung ohne Minecraft (läuft bei jedem Build der 1.21.11-Mod, Gradle-Aufgabe diagTest):
 * Thread-Familien, Leerlauf-Erkennung (FPS-Begrenzer, VSync, park, epoll …) und der Bericht mit künstlichen Stacks.
 */
public final class DiagSelfTest {
    private static int checks;

    public static void main(String[] args) {
        families();
        idle();
        report();
        System.out.println("Diagnose-Selbsttest: " + checks + " Prüfungen ok");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void eq(Object expected, Object actual, String what) {
        check(expected == null ? actual == null : expected.equals(actual), what + ": erwartet " + expected + ", war " + actual);
    }

    private static StackTraceElement f(String cls, String method) {
        return new StackTraceElement(cls, method, null, -1);
    }

    private static StackTraceElement[] st(StackTraceElement... frames) {
        return frames;
    }

    private static void families() {
        eq("Worker-Main", SamplingProfiler.family("Worker-Main-12"), "Worker-Main");
        eq("Render thread", SamplingProfiler.family("Render thread"), "Render thread");
        eq("Chunk Render Task Executor", SamplingProfiler.family("Chunk Render Task Executor #3"), "Chunk Render");
        eq("ForkJoinPool.commonPool-worker", SamplingProfiler.family("ForkJoinPool.commonPool-worker-7"), "ForkJoin");
        eq("IO-Worker", SamplingProfiler.family("IO-Worker-23"), "IO-Worker");
        eq("Netty Client IO", SamplingProfiler.family("Netty Client IO #1"), "Netty");
        eq("c2me-worker", SamplingProfiler.family("c2me-worker-4"), "c2me");
        eq("Sodium Chunk Builder", SamplingProfiler.family("Sodium Chunk Builder 2"), "Sodium");
        eq("Server thread", SamplingProfiler.family("Server thread"), "Server thread");
        eq("pool-2-thread", SamplingProfiler.family("pool-2-thread-9"), "pool-N-thread");
        eq("1234", SamplingProfiler.family("1234"), "nur Ziffern bleibt");
    }

    private static void idle() {
        Thread.State R = Thread.State.RUNNABLE;
        // FPS-Begrenzer: GLFW.glfwWaitEventsTimeout hinter der JNI-Hülle, darunter RenderSystem (Intermediary)
        eq(SamplingProfiler.IDLE_FPS, SamplingProfiler.idleKind(R, st(f("org.lwjgl.system.JNI", "invokeV"), f("org.lwjgl.glfw.GLFW", "glfwWaitEventsTimeout"),
                f("com.mojang.blaze3d.systems.RenderSystem", "method_4881"))), "glfwWaitEventsTimeout");
        eq(SamplingProfiler.IDLE_FPS, SamplingProfiler.idleKind(R, st(f("com.mojang.blaze3d.systems.RenderSystem", "limitDisplayFPS"))), "limitDisplayFPS");
        // VSync
        eq(SamplingProfiler.IDLE_VSYNC, SamplingProfiler.idleKind(R, st(f("org.lwjgl.system.JNI", "invokePV"), f("org.lwjgl.glfw.GLFW", "glfwSwapBuffers"),
                f("com.mojang.blaze3d.platform.Window", "method_15998"))), "glfwSwapBuffers");
        eq(SamplingProfiler.IDLE_VSYNC, SamplingProfiler.idleKind(R, st(f("org.lwjgl.opengl.WindowsContextImplementation", "nSwapBuffers"))), "LWJGL2 nSwapBuffers");
        // 1.8.9: Display.sync → Thread.sleep (Zustand TIMED_WAITING) zählt als FPS-Begrenzer
        eq(SamplingProfiler.IDLE_FPS, SamplingProfiler.idleKind(Thread.State.TIMED_WAITING, st(f("java.lang.Thread", "sleep"), f("org.lwjgl.opengl.Sync", "sync"),
                f("org.lwjgl.opengl.Display", "sync"), f("net.minecraft.client.Minecraft", "func_71411_J"))), "Display.sync");
        // park/wait/sleep
        eq(SamplingProfiler.IDLE_WAIT, SamplingProfiler.idleKind(Thread.State.WAITING, st(f("jdk.internal.misc.Unsafe", "park"),
                f("java.util.concurrent.locks.LockSupport", "park"), f("java.util.concurrent.LinkedBlockingQueue", "take"))), "park");
        eq(SamplingProfiler.IDLE_WAIT, SamplingProfiler.idleKind(R, st(f("java.lang.Thread", "yield0"), f("java.lang.Thread", "yield"))), "yield");
        eq(SamplingProfiler.IDLE_BLOCKED, SamplingProfiler.idleKind(Thread.State.BLOCKED, st(f("net.minecraft.class_1234", "method_1"))), "blockiert");
        // Netzwerk-Warten
        eq(SamplingProfiler.IDLE_IO, SamplingProfiler.idleKind(R, st(f("io.netty.channel.epoll.Native", "epollWait0"), f("io.netty.channel.epoll.Native", "epollWait"))), "epoll");
        eq(SamplingProfiler.IDLE_IO, SamplingProfiler.idleKind(R, st(f("sun.nio.ch.WEPoll", "wait"), f("sun.nio.ch.WEPollSelectorImpl", "doSelect"))), "WEPoll");
        eq(SamplingProfiler.IDLE_IO, SamplingProfiler.idleKind(R, st(f("sun.nio.ch.WindowsSelectorImpl$SubSelector", "poll0"))), "WindowsSelector");
        // echte Arbeit
        eq(null, SamplingProfiler.idleKind(R, st(f("net.minecraft.class_1059", "method_4577"), f("net.minecraft.class_4011", "method_18368"))), "Atlas bauen");
        eq(null, SamplingProfiler.idleKind(R, st(f("java.util.HashMap", "get"), f("net.minecraft.class_1059", "method_4577"))), "HashMap.get ist Arbeit");
        eq(null, SamplingProfiler.idleKind(R, st(f("org.lwjgl.system.JNI", "invokeV"), f("org.lwjgl.opengl.GL11C", "glDrawElements"))), "GL-Aufruf ist Arbeit");
        eq(null, SamplingProfiler.idleKind(R, st(f("com.example.Mod", "poll"))), "fremde poll-Methode ist Arbeit");
    }

    private static void report() {
        SamplingProfiler.ModResolver mods = cls -> cls.startsWith("net.minecraft.") || cls.startsWith("com.mojang.") ? "minecraft"
                : cls.startsWith("me.jellysquid.") ? "sodium" : cls.startsWith("org.redlance.dima_dencep.mods.rrls.") ? "rrls" : null;
        SamplingProfiler p = SamplingProfiler.forTest(1, mods);
        StackTraceElement[] renderIdle = st(f("org.lwjgl.system.JNI", "invokeV"), f("org.lwjgl.glfw.GLFW", "glfwWaitEventsTimeout"), f("com.mojang.blaze3d.systems.RenderSystem", "method_4881"));
        StackTraceElement[] renderWork = st(f("net.minecraft.class_757", "method_3192"), f("net.minecraft.class_310", "method_1523"));
        StackTraceElement[] parked = st(f("jdk.internal.misc.Unsafe", "park"), f("java.util.concurrent.locks.LockSupport", "park"));
        StackTraceElement[] rrls = st(f("org.redlance.dima_dencep.mods.rrls.utils.DummyDrawContext", "fill"), f("net.minecraft.class_1059", "method_4577"));
        StackTraceElement[] atlas = st(f("net.minecraft.class_1059", "method_4577"), f("java.util.concurrent.CompletableFuture$AsyncSupply", "run"));
        StackTraceElement[] sodium = st(f("me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder", "build"));
        p.reloadEvent(true, 1000);
        for (int i = 0; i < 100; i++) {
            boolean reload = i < 60;
            p.accept(10, "Render thread", 1, Thread.State.RUNNABLE, i % 2 == 0 ? renderIdle : renderWork, reload);
            p.accept(10, "Worker-Main-1", 2, Thread.State.RUNNABLE, reload ? (i % 3 == 0 ? rrls : atlas) : parked, reload);
            p.accept(10, "Worker-Main-2", 3, reload ? Thread.State.RUNNABLE : Thread.State.WAITING, reload ? atlas : parked, reload);
            p.accept(10, "Chunk Render Task Executor #1", 4, Thread.State.RUNNABLE, sodium, reload);
            p.accept(10, "Netty Client IO #1", 5, Thread.State.RUNNABLE, st(f("io.netty.channel.epoll.Native", "epollWait0")), reload);
            p.accept(10, "Reference Handler", 6, Thread.State.RUNNABLE, atlas, reload);
        }
        p.reloadEvent(false, 3500);
        String r = p.report(1000);
        check(r.contains("Neuladen der Ressourcen während der Messung: 1× (Dauer 2,5 s)"), "Neuladen-Zeile\n" + r);
        check(r.contains("aktiv:  50.0 %, wartend/blockiert:  50.0 %"), "Render-Thread wartend (FPS-Begrenzer)\n" + r);
        check(r.contains("davon FPS-Begrenzer/Leerlauf  50.0 %"), "FPS-Begrenzer aufgeschlüsselt\n" + r);
        check(r.indexOf("Render-Thread") < r.indexOf("Thread-Familie „Worker-Main“"), "Render-Thread zuerst");
        check(r.contains("1,2 s    60.0 %        2  Worker-Main"), "Worker-Main: 120 aktive Proben à 10 ms, 2 Threads\n" + r);
        check(r.contains("1,0 s   100.0 %        1  Chunk Render Task Executor"), "Chunk-Builder\n" + r);
        check(r.contains("weitere Thread-Familien nur im Leerlauf"), "Netty nur Leerlauf\n" + r);
        check(!r.contains("Reference Handler"), "JVM-Threads ausgeblendet");
        check(r.contains("Thread-Familie „Worker-Main“"), "Abschnitt Worker-Main\n" + r);
        check(r.contains("Während des Neuladens der Ressourcen"), "Abschnitt Neuladen\n" + r);
        String during = r.substring(r.indexOf("Während des Neuladens der Ressourcen"));
        // beim Neuladen aktiv: Worker 2×60 + Sodium 60 = 180 Proben; rrls oben in 20 davon
        check(during.contains(" 11.1 % selbst   11.1 % beteiligt  rrls"), "rrls-Anteil beim Neuladen\n" + during);
        check(during.contains("1,2 s  Worker-Main"), "Worker beim Neuladen\n" + during);
    }
}
