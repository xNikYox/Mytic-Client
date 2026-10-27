package de.myticlegacy.client.clips;

import de.myticlegacy.client.MyticClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Clips: nimmt laufend die letzten Sekunden auf und speichert sie auf Tastendruck als MP4.
 * <p>
 * Die Bilder kommen roh (RGBA, von der Grafikkarte) über eine Pipe zu ffmpeg. ffmpeg kodiert – wenn möglich mit der
 * Grafikkarte (NVENC, Quick Sync, AMF) – in kurze Stücke von {@link #SEGMENT} Sekunden, die im Kreis überschrieben
 * werden. Beim Speichern werden die letzten Stücke ohne neues Kodieren zu einer MP4 zusammengefügt.
 */
public final class ClipRecorder {
    public static final int SEGMENT = 2;
    private static final String WIN_URL = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
    private static final String[][] ENCODERS = {
            {"h264_nvenc", "-preset", "p2", "-rc", "vbr", "-cq", "23", "-b:v", "0"},
            {"h264_qsv", "-preset", "veryfast", "-global_quality", "23"},
            {"h264_amf", "-quality", "speed", "-rc", "cqp", "-qp_i", "22", "-qp_p", "24"},
            {"h264_videotoolbox", "-b:v", "10M"},
            {"libx264", "-preset", "ultrafast", "-tune", "zerolatency", "-crf", "23"},
    };

    private static Path ffmpeg;
    private static String encoder;
    private static final AtomicBoolean preparing = new AtomicBoolean();
    public static volatile String status = "";

    private static Process process;
    private static Thread writer;
    private static ArrayBlockingQueue<ByteBuffer> queue;
    private static ArrayBlockingQueue<ByteBuffer> pool;
    private static int width, height, fps, seconds;
    private static Path session;
    private static volatile boolean saving;

    private ClipRecorder() {
    }

    // ---------- ffmpeg finden / laden ----------

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static Path toolsDir() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = windows() && local != null ? Path.of(local) : Path.of(System.getProperty("user.home"));
        return base.resolve(windows() ? "MyticClient" : ".mytic-client").resolve("ffmpeg");
    }

    public static Path clipsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("clips");
    }

    public static boolean ready() {
        return ffmpeg != null && encoder != null;
    }

    public static boolean running() {
        return process != null && process.isAlive();
    }

    public static String encoderName() {
        if (encoder == null) return "–";
        return switch (encoder) {
            case "h264_nvenc" -> "NVIDIA (NVENC)";
            case "h264_qsv" -> "Intel (Quick Sync)";
            case "h264_amf" -> "AMD (AMF)";
            case "h264_videotoolbox" -> "Apple (VideoToolbox)";
            default -> "Prozessor (x264)";
        };
    }

    /** ffmpeg suchen, sonst (Windows) einmalig laden, dann den schnellsten Kodierer bestimmen. Läuft im Hintergrund. */
    public static void prepare() {
        if (ready() || !preparing.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                Path found = find();
                if (found == null && windows()) found = download();
                if (found == null) {
                    status = windows() ? "ffmpeg konnte nicht geladen werden" : "ffmpeg fehlt – bitte installieren (z. B. Paketmanager)";
                    return;
                }
                status = "Grafikkarten-Kodierung wird geprüft …";
                for (String[] enc : ENCODERS) {
                    if (test(found, enc[0])) {
                        ffmpeg = found;
                        encoder = enc[0];
                        status = "";
                        MyticClient.LOG.info("Clips: ffmpeg {} mit {}", found, enc[0]);
                        return;
                    }
                }
                status = "Kein passender Video-Kodierer gefunden";
            } catch (Exception e) {
                status = "Fehler: " + e.getMessage();
                MyticClient.LOG.warn("Clips: ffmpeg nicht bereit", e);
            } finally {
                preparing.set(false);
            }
        }, "Mytic-Clips-Setup");
        t.setDaemon(true);
        t.start();
    }

    private static Path find() {
        Path own = toolsDir().resolve(windows() ? "ffmpeg.exe" : "ffmpeg");
        if (Files.isRegularFile(own) && works(own.toString())) return own;
        if (works("ffmpeg")) return Path.of("ffmpeg");
        return null;
    }

    private static boolean works(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-hide_banner", "-version").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean test(Path exe, String enc) {
        try {
            Process p = new ProcessBuilder(exe.toString(), "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "color=black:s=640x360:d=0.3:r=30",
                    "-c:v", enc, "-pix_fmt", "yuv420p", "-f", "null", "-").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Windows: ffmpeg.exe aus dem „essentials“-Paket von gyan.dev holen (nur diese eine Datei wird entpackt) */
    private static Path download() throws IOException, InterruptedException {
        Path dir = toolsDir();
        Files.createDirectories(dir);
        Path target = dir.resolve("ffmpeg.exe"), part = dir.resolve("ffmpeg.exe.part");
        status = "ffmpeg wird geladen …";
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(java.time.Duration.ofSeconds(20)).build();
        HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(URI.create(WIN_URL)).header("User-Agent", "MyticClient").build(), HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
        long total = res.headers().firstValueAsLong("content-length").orElse(-1);
        try (InputStream raw = res.body(); Counting in = new Counting(raw, total); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.getName().endsWith("/bin/ffmpeg.exe")) continue;
                Files.copy(zip, part, StandardCopyOption.REPLACE_EXISTING);
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                status = "ffmpeg geladen";
                return works(target.toString()) ? target : null;
            }
        }
        return null;
    }

    /** zählt geladene Bytes für die Fortschrittsanzeige */
    private static final class Counting extends java.io.FilterInputStream {
        private final long total;
        private long done;
        private int lastPct = -1;

        Counting(InputStream in, long total) {
            super(in);
            this.total = total;
        }

        private void add(long n) {
            if (n <= 0) return;
            done += n;
            int pct = total > 0 ? (int) (done * 100 / total) : -1;
            if (pct != lastPct) {
                lastPct = pct;
                status = pct >= 0 ? "ffmpeg wird geladen … " + pct + " %" : "ffmpeg wird geladen … " + (done >> 20) + " MB";
            }
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) add(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            add(n);
            return n;
        }
    }

    // ---------- Aufnahme ----------

    /**
     * Aufnahme starten. w/h = Größe der gelieferten Bilder, outHeight = Zielhöhe (0 = wie Bildschirm),
     * flip = Bilder kommen auf dem Kopf (OpenGL).
     */
    public static synchronized void start(int w, int h, int outHeight, int targetFps, int lengthSeconds, boolean flip) {
        stop();
        if (!ready()) return;
        width = w;
        height = h;
        fps = targetFps;
        seconds = lengthSeconds;
        try {
            session = Files.createDirectories(clipsDir().resolve(".puffer").resolve(Long.toString(System.currentTimeMillis())));
            cleanupOld();
            List<String> cmd = new ArrayList<>(List.of(ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-nostdin",
                    "-f", "rawvideo", "-pix_fmt", "rgba", "-s", w + "x" + h, "-use_wallclock_as_timestamps", "1", "-thread_queue_size", "8", "-i", "pipe:0"));
            List<String> vf = new ArrayList<>();
            if (flip) vf.add("vflip");
            int oh = outHeight > 0 && outHeight < h ? outHeight : h;
            if (oh != h || (w & 1) != 0 || (h & 1) != 0) vf.add("scale=-2:" + (oh & ~1));
            vf.add("format=yuv420p");
            cmd.addAll(List.of("-vf", String.join(",", vf), "-fps_mode", "cfr", "-r", Integer.toString(fps)));
            for (String[] enc : ENCODERS) {
                if (!enc[0].equals(encoder)) continue;
                cmd.add("-c:v");
                cmd.addAll(List.of(enc));
            }
            cmd.addAll(List.of("-g", Integer.toString(fps * SEGMENT), "-force_key_frames", "expr:gte(t,n_forced*" + SEGMENT + ")",
                    "-f", "segment", "-segment_time", Integer.toString(SEGMENT), "-segment_wrap", Integer.toString(seconds / SEGMENT + 3),
                    "-segment_format", "mpegts", "-reset_timestamps", "1", session.resolve("s%03d.ts").toString()));
            process = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(session.resolve("ffmpeg.log").toFile()).start();
            int size = w * h * 4;
            queue = new ArrayBlockingQueue<>(3);
            pool = new ArrayBlockingQueue<>(4);
            for (int i = 0; i < 4; i++) pool.add(ByteBuffer.allocateDirect(size));
            Process p = process;
            ArrayBlockingQueue<ByteBuffer> q = queue, free = pool;
            writer = new Thread(() -> {
                try (OutputStream out = p.getOutputStream(); WritableByteChannel ch = Channels.newChannel(out)) {
                    while (p.isAlive()) {
                        ByteBuffer b = q.poll(500, TimeUnit.MILLISECONDS);
                        if (b == null) continue;
                        b.rewind();
                        while (b.hasRemaining()) ch.write(b);
                        free.offer(b);
                    }
                } catch (Exception ignored) {
                    // Aufnahme beendet
                }
            }, "Mytic-Clips");
            writer.setDaemon(true);
            writer.start();
            MyticClient.LOG.info("Clips: Aufnahme {}x{} → {}p, {} fps, {} s, {}", w, h, oh, fps, seconds, encoder);
        } catch (IOException e) {
            MyticClient.LOG.warn("Clips: Start fehlgeschlagen", e);
            status = "Aufnahme konnte nicht starten";
            stop();
        }
    }

    public static synchronized void stop() {
        if (process != null) {
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
            Process p = process;
            Path dir = session;
            process = null;
            // ffmpeg in Ruhe beenden lassen, dann den Puffer löschen (außer es wird gerade gespeichert)
            Thread t = new Thread(() -> {
                try {
                    if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly();
                    while (saving) Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
                delete(dir);
            }, "Mytic-Clips-Stop");
            t.setDaemon(true);
            t.start();
        }
        if (writer != null) writer.interrupt();
        writer = null;
        queue = null;
        pool = null;
        session = null;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    /** Freier Puffer für das nächste Bild oder null (dann wird dieses Bild ausgelassen) */
    public static ByteBuffer borrow() {
        ArrayBlockingQueue<ByteBuffer> p = pool;
        if (p == null || !running()) return null;
        ByteBuffer b = p.poll();
        if (b != null) b.clear();
        return b;
    }

    /** ungenutzten Puffer zurückgeben */
    public static void release(ByteBuffer b) {
        ArrayBlockingQueue<ByteBuffer> p = pool;
        if (p != null) p.offer(b);
    }

    /** gefülltes Bild an ffmpeg geben */
    public static void submit(ByteBuffer b) {
        ArrayBlockingQueue<ByteBuffer> q = queue;
        if (q == null || !q.offer(b)) {
            ArrayBlockingQueue<ByteBuffer> p = pool;
            if (p != null) p.offer(b);
        }
    }

    // ---------- Speichern ----------

    public interface Done {
        void done(Path clip, String error);
    }

    /** Die letzten Sekunden als MP4 speichern (im Hintergrund) */
    public static void save(Done done) {
        Path dir = session;
        if (dir == null || !running()) {
            done.done(null, "Aufnahme läuft nicht");
            return;
        }
        if (saving) {
            done.done(null, "Wird schon gespeichert …");
            return;
        }
        saving = true;
        int len = seconds;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1200); // ffmpeg schreibt mit kurzer Verzögerung
                List<Path> segs;
                try (Stream<Path> s = Files.list(dir)) {
                    segs = new ArrayList<>(s.filter(p -> p.getFileName().toString().endsWith(".ts")).sorted(Comparator.comparingLong(ClipRecorder::modified)).toList());
                }
                int need = (len + SEGMENT - 1) / SEGMENT + 1;
                if (segs.size() > need) segs = segs.subList(segs.size() - need, segs.size());
                if (segs.isEmpty()) {
                    done.done(null, "Noch nichts aufgenommen");
                    return;
                }
                Path list = dir.resolve("liste.txt");
                StringBuilder sb = new StringBuilder();
                for (Path p : segs) sb.append("file '").append(p.toAbsolutePath().toString().replace("\\", "/").replace("'", "'\\''")).append("'\n");
                Files.writeString(list, sb.toString());
                Files.createDirectories(clipsDir());
                Path out = clipsDir().resolve("clip_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".mp4");
                Process p = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", list.toString(),
                        "-c", "copy", "-movflags", "+faststart", out.toString()).redirectErrorStream(true).redirectOutput(dir.resolve("save.log").toFile()).start();
                if (!p.waitFor(60, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    done.done(null, "Speichern dauerte zu lange");
                } else if (p.exitValue() != 0 || !Files.isRegularFile(out)) {
                    done.done(null, "Speichern fehlgeschlagen");
                } else done.done(out, null);
            } catch (Exception e) {
                MyticClient.LOG.warn("Clips: Speichern fehlgeschlagen", e);
                done.done(null, "Speichern fehlgeschlagen");
            } finally {
                saving = false;
            }
        }, "Mytic-Clips-Save");
        t.setDaemon(true);
        t.start();
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Reste alter Aufnahmen (z. B. nach Absturz) entfernen */
    private static void cleanupOld() {
        Path base = clipsDir().resolve(".puffer");
        try (Stream<Path> s = Files.list(base)) {
            s.filter(p -> !p.equals(session)).forEach(ClipRecorder::delete);
        } catch (IOException ignored) {
        }
    }

    private static void delete(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
        }
    }
}
