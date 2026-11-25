package de.myticlegacy.client.clips;

import de.myticlegacy.client.MyticClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Clips (Instant Replay): hält die letzten Sekunden als JPEG-Bilder im Arbeitsspeicher ({@link ClipBuffer}) und schreibt
 * sie auf Tastendruck im Hintergrund als Motion-JPEG-AVI ({@link MjpegAvi}) – ohne Zusatzprogramm. Ist ffmpeg vorhanden
 * (eigener Ordner aus älteren Versionen oder im PATH) und gewünscht, wird danach in eine kleinere MP4 umgewandelt.
 * <p>
 * Die Bilder liefern FrameGrab/GlGrab: auf der Grafikkarte verkleinert und asynchron ausgelesen (Pixel-Puffer + Fence).
 */
public final class ClipRecorder {
    private static volatile ClipBuffer buffer;
    private static int sourceWidth, sourceHeight;
    private static volatile boolean saving;
    public static volatile String status = "";
    /** ffmpeg: null = noch nicht gesucht, leer = keins */
    private static volatile String ffmpeg;

    private ClipRecorder() {
    }

    public static Path clipsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mytic").resolve("clips");
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Ordner, in den ältere Mytic-Versionen ffmpeg geladen haben (wird weiter genutzt, aber nicht mehr geladen) */
    private static Path toolsDir() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = windows() && local != null ? Path.of(local) : Path.of(System.getProperty("user.home"));
        return base.resolve(windows() ? "MyticClient" : ".mytic-client").resolve("ffmpeg");
    }

    /** Nichts mehr einzurichten – AVI geht immer */
    public static boolean ready() {
        return true;
    }

    public static void prepare() {
    }

    public static void resetRetries() {
    }

    public static boolean running() {
        ClipBuffer b = buffer;
        return b != null && b.running();
    }

    public static String encoderName() {
        return "MJPEG im Arbeitsspeicher";
    }

    /** Speicher, den die Aufnahme gerade belegt (MB) */
    public static long memoryMb() {
        ClipBuffer b = buffer;
        return b == null ? 0 : b.bytes() >> 20;
    }

    /**
     * Aufnahme starten. srcW/srcH = Größe des Spielbilds, w/h = Größe der gelieferten Bilder (von der Grafikkarte schon
     * verkleinert oder gleich srcW/srcH), outHeight = Zielhöhe (0 = wie geliefert), flip = Bilder kommen auf dem Kopf.
     */
    public static synchronized void start(int srcW, int srcH, int w, int h, int outHeight, int fps, int seconds, boolean flip) {
        stop();
        sourceWidth = srcW;
        sourceHeight = srcH;
        flipped = flip;
        int oh = (outHeight > 0 && outHeight < h ? outHeight : h) & ~1;
        int ow = Math.max(2, Math.round(w * (float) oh / h) & ~1);
        buffer = new ClipBuffer(w, h, ow, oh, flip, fps, seconds, ClipBuffer.defaultBudget());
        status = "";
        MyticClient.LOG.info("Clips: Aufnahme {}x{} (Spiel {}x{}) → {}x{}, {} fps, {} s, höchstens {} MB", w, h, srcW, srcH, ow, oh, fps, seconds,
                buffer.budget >> 20);
    }

    public static synchronized void stop() {
        ClipBuffer b = buffer;
        buffer = null;
        if (b != null) b.stop();
    }

    public static int width() {
        ClipBuffer b = buffer;
        return b == null ? 0 : b.inW;
    }

    public static int height() {
        ClipBuffer b = buffer;
        return b == null ? 0 : b.inH;
    }

    /** Bilder kommen auf dem Kopf (Weg ohne Verkleinern auf der Grafikkarte, z. B. Vulkan) */
    public static boolean flipped() {
        return flipped;
    }

    private static volatile boolean flipped;

    public static int sourceWidth() {
        return sourceWidth;
    }

    public static int sourceHeight() {
        return sourceHeight;
    }

    public static ByteBuffer borrow() {
        ClipBuffer b = buffer;
        return b == null ? null : b.borrow();
    }

    public static void release(ByteBuffer buf) {
        ClipBuffer b = buffer;
        if (b != null) b.release(buf);
    }

    public static void submit(ByteBuffer buf) {
        ClipBuffer b = buffer;
        if (b != null) b.submit(buf);
    }

    /** Fehler der Aufnahme (z. B. kein JPEG-Kodierer) oder null */
    public static String error() {
        ClipBuffer b = buffer;
        return b == null ? null : b.error();
    }

    // ---------- Speichern ----------

    public interface Done {
        void done(Path clip, String error);
    }

    /** Die letzten {@code seconds} Sekunden speichern (im Hintergrund): AVI, auf Wunsch danach MP4 mit ffmpeg */
    public static void save(int seconds, boolean mp4, Done done) {
        ClipBuffer b = buffer;
        if (b == null || !b.running()) {
            done.done(null, "Aufnahme läuft nicht");
            return;
        }
        if (saving) {
            done.done(null, "Wird schon gespeichert …");
            return;
        }
        final List<ClipBuffer.Frame> frames = b.last(seconds);
        if (frames.size() < 2) {
            done.done(null, "Noch nichts aufgenommen");
            return;
        }
        saving = true;
        final int w = b.outW, h = b.outH, fps = b.fps;
        Thread t = new Thread(() -> {
            try {
                Files.createDirectories(clipsDir());
                String name = "clip_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
                Path avi = clipsDir().resolve(name + ".avi"), part = clipsDir().resolve(name + ".avi.part");
                MjpegAvi.write(part.toFile(), frames, w, h, MjpegAvi.fps(frames, fps));
                Files.move(part, avi, StandardCopyOption.REPLACE_EXISTING);
                Path out = avi;
                if (mp4) {
                    Path converted = toMp4(avi);
                    if (converted != null) out = converted;
                }
                done.done(out, null);
            } catch (Exception e) {
                MyticClient.LOG.warn("Clips: Speichern fehlgeschlagen", e);
                done.done(null, "Speichern fehlgeschlagen");
            } finally {
                saving = false;
            }
        }, "Mytic-Clips-Save");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 2);
        t.start();
    }

    /** AVI → MP4 (H.264) mit ffmpeg, falls vorhanden; danach die AVI löschen. Sonst null (AVI bleibt). */
    private static Path toMp4(Path avi) {
        String exe = findFfmpeg();
        if (exe.isEmpty()) return null;
        Path mp4 = avi.resolveSibling(avi.getFileName().toString().replaceAll("\\.avi$", ".mp4"));
        try {
            Process p = new ProcessBuilder(exe, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", avi.toString(),
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", "-threads", "2", "-movflags", "+faststart", mp4.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(180, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                Files.deleteIfExists(mp4);
                return null;
            }
            if (p.exitValue() != 0 || !Files.isRegularFile(mp4) || Files.size(mp4) == 0) {
                Files.deleteIfExists(mp4);
                return null;
            }
            Files.deleteIfExists(avi);
            return mp4;
        } catch (Exception e) {
            MyticClient.LOG.warn("Clips: Umwandeln in MP4 fehlgeschlagen – AVI bleibt", e);
            return null;
        }
    }

    /** ffmpeg suchen (einmal): eigener Ordner früherer Versionen, dann PATH. Leer = keins. */
    public static String findFfmpeg() {
        String f = ffmpeg;
        if (f != null) return f;
        Path own = toolsDir().resolve(windows() ? "ffmpeg.exe" : "ffmpeg");
        if (Files.isRegularFile(own) && works(own.toString())) f = own.toString();
        else if (works("ffmpeg")) f = "ffmpeg";
        else f = "";
        ffmpeg = f;
        return f;
    }

    private static boolean works(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-hide_banner", "-version").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Ordner im Datei-Explorer öffnen (ohne AWT) */
    public static void openFolder() {
        try {
            Path dir = clipsDir();
            Files.createDirectories(dir);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String cmd = os.contains("win") ? "explorer" : os.contains("mac") ? "open" : "xdg-open";
            new ProcessBuilder(cmd, dir.toAbsolutePath().toString()).start();
        } catch (Exception e) {
            MyticClient.LOG.warn("Clips: Ordner nicht geöffnet", e);
        }
    }

    /** Alte Reste (Puffer früherer ffmpeg-Aufnahmen) im alten Ordner „clips/.puffer“ entfernen */
    public static void cleanupOld() {
        Path old = FabricLoader.getInstance().getGameDir().resolve("clips").resolve(".puffer");
        if (!Files.isDirectory(old)) return;
        try (java.util.stream.Stream<Path> s = Files.walk(old)) {
            s.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (Exception ignored) {
        }
    }
}
