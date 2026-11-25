package de.myticlegacy.client.clips;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Clips für 1.8.9: wie in den neuen Versionen ein JPEG-Ringpuffer im Arbeitsspeicher ({@link ClipBuffer}), gespeichert
 * als Motion-JPEG-AVI ({@link MjpegAvi}); mit ffmpeg (falls vorhanden) zusätzlich als MP4. Java 8.
 */
public final class ClipRecorder {
    private static volatile ClipBuffer buffer;
    private static volatile boolean saving;
    private static volatile String ffmpeg;

    private ClipRecorder() {
    }

    public static File clipsDir() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "mytic"), "clips");
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean running() {
        ClipBuffer b = buffer;
        return b != null && b.running();
    }

    public static synchronized void start(int w, int h, int fps, int seconds) {
        stop();
        buffer = new ClipBuffer(w, h, w, h, false, fps, seconds, ClipBuffer.defaultBudget());
        MyticClient.LOG.info("Clips: Aufnahme {}x{}, {} fps, {} s, höchstens {} MB", w, h, fps, seconds, buffer.budget >> 20);
    }

    public static synchronized void stop() {
        ClipBuffer b = buffer;
        buffer = null;
        if (b != null) b.stop();
    }

    public static String error() {
        ClipBuffer b = buffer;
        return b == null ? null : b.error();
    }

    static ByteBuffer borrow() {
        ClipBuffer b = buffer;
        return b == null ? null : b.borrow();
    }

    static void release(ByteBuffer buf) {
        ClipBuffer b = buffer;
        if (b != null) b.release(buf);
    }

    static void submit(ByteBuffer buf) {
        ClipBuffer b = buffer;
        if (b != null) b.submit(buf);
    }

    public interface Done {
        void done(File clip, String error);
    }

    public static void save(int seconds, final boolean mp4, final Done done) {
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
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    File dir = clipsDir();
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner nicht anlegbar");
                    String name = "clip_" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
                    File avi = new File(dir, name + ".avi"), part = new File(dir, name + ".avi.part");
                    MjpegAvi.write(part, frames, w, h, MjpegAvi.fps(frames, fps));
                    Files.move(part.toPath(), avi.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    File out = avi;
                    if (mp4) {
                        File converted = toMp4(avi);
                        if (converted != null) out = converted;
                    }
                    done.done(out, null);
                } catch (Exception e) {
                    MyticClient.LOG.warn("Clips: Speichern fehlgeschlagen", e);
                    done.done(null, "Speichern fehlgeschlagen");
                } finally {
                    saving = false;
                }
            }
        }, "Mytic-Clips-Save");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 2);
        t.start();
    }

    private static File toMp4(File avi) {
        String exe = findFfmpeg();
        if (exe.isEmpty()) return null;
        File mp4 = new File(avi.getParentFile(), avi.getName().replaceAll("\\.avi$", ".mp4"));
        File log = new File(avi.getParentFile(), ".ffmpeg.log");
        try {
            Process p = new ProcessBuilder(exe, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", avi.getPath(),
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", "-threads", "2", "-movflags", "+faststart", mp4.getPath())
                    .redirectErrorStream(true).redirectOutput(log).start();
            boolean ok = p.waitFor(180, TimeUnit.SECONDS);
            if (!ok) p.destroy();
            if (!ok || p.exitValue() != 0 || !mp4.isFile() || mp4.length() == 0) {
                mp4.delete();
                return null;
            }
            avi.delete();
            log.delete();
            return mp4;
        } catch (Exception e) {
            MyticClient.LOG.warn("Clips: Umwandeln in MP4 fehlgeschlagen – AVI bleibt", e);
            return null;
        }
    }

    /** ffmpeg suchen (einmal): Ordner, in den neuere Mytic-Versionen es früher geladen haben, dann PATH */
    public static String findFfmpeg() {
        String f = ffmpeg;
        if (f != null) return f;
        String local = System.getenv("LOCALAPPDATA");
        Path base = windows() && local != null ? Paths.get(local) : Paths.get(System.getProperty("user.home"));
        Path own = base.resolve(windows() ? "MyticClient" : ".mytic-client").resolve("ffmpeg").resolve(windows() ? "ffmpeg.exe" : "ffmpeg");
        if (Files.isRegularFile(own) && works(own.toString())) f = own.toString();
        else if (works("ffmpeg")) f = "ffmpeg";
        else f = "";
        ffmpeg = f;
        return f;
    }

    private static boolean works(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-hide_banner", "-version").redirectErrorStream(true).start();
            java.io.InputStream in = p.getInputStream();
            byte[] buf = new byte[4096];
            while (in.read(buf) > 0) {
                // Ausgabe verwerfen
            }
            in.close();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void openFolder() {
        try {
            File dir = clipsDir();
            dir.mkdirs();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String cmd = os.contains("win") ? "explorer" : os.contains("mac") ? "open" : "xdg-open";
            new ProcessBuilder(cmd, dir.getAbsolutePath()).start();
        } catch (Exception e) {
            MyticClient.LOG.warn("Clips: Ordner nicht geöffnet", e);
        }
    }
}
