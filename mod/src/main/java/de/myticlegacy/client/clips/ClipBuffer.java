package de.myticlegacy.client.clips;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Ringpuffer für Clips: nimmt fertige Bilder (RGBA, von der Grafikkarte) an, kodiert sie in einem eigenen Thread als
 * JPEG und hält die letzten Sekunden im Arbeitsspeicher – höchstens {@code budget} Bytes. Passt die eingestellte Länge
 * nicht ins Budget, wird die JPEG-Qualität gesenkt (statt Sekunden zu verlieren); kommt der Kodierer nicht hinterher,
 * werden Bilder ausgelassen (die AVI bekommt dann die tatsächliche Bildrate). Ein Thread schafft etwa 45 Bilder 720p
 * pro Sekunde; für 60 fps oder 1080p kodieren zwei Threads.
 * <p>
 * Ohne Minecraft- und Loader-Klassen und Java-8-tauglich – dieselbe Datei liegt auch in der 1.8.9-Mod.
 */
public final class ClipBuffer {
    /** Ein kodiertes Bild mit Zeitpunkt (System.nanoTime) */
    public static final class Frame {
        public final byte[] jpeg;
        public final long nanos;

        Frame(byte[] jpeg, long nanos) {
            this.jpeg = jpeg;
            this.nanos = nanos;
        }
    }

    private static final class Item {
        final ByteBuffer buf;
        final long nanos;

        Item(ByteBuffer buf, long nanos) {
            this.buf = buf;
            this.nanos = nanos;
        }
    }

    /** Größe der gelieferten Bilder, Größe im Clip */
    public final int inW, inH, outW, outH, fps, seconds;
    public final long budget;
    private final boolean flip;
    private final ArrayBlockingQueue<ByteBuffer> pool;
    private final ArrayBlockingQueue<Item> queue;
    private final ArrayDeque<Frame> ring = new ArrayDeque<Frame>();
    private long bytes;
    private volatile boolean running = true;
    private volatile float quality = 0.8f;
    private volatile String error;
    private final Thread[] threads;

    /**
     * inW/inH = Größe der Bilder, die kommen; outW/outH = Clip-Größe (gerade Zahlen); flip = Bilder stehen auf dem Kopf
     * (OpenGL ohne Umweg über die Grafikkarte); budget = höchstens so viele Bytes JPEG im Speicher.
     */
    public ClipBuffer(int inW, int inH, int outW, int outH, boolean flip, int fps, int seconds, long budget) {
        this.inW = inW;
        this.inH = inH;
        this.outW = outW;
        this.outH = outH;
        this.flip = flip;
        this.fps = fps;
        this.seconds = seconds;
        this.budget = budget;
        // ein Kodier-Thread je ~27 Mio. Bildpunkte pro Sekunde (720p30), höchstens 2 und höchstens ein Viertel der Kerne
        long load = (long) outW * outH * fps;
        int n = (int) Math.max(1, Math.min(Math.min(2, Runtime.getRuntime().availableProcessors() / 4), (load + 27_000_000L) / 28_000_000L));
        pool = new ArrayBlockingQueue<ByteBuffer>(n + 3);
        queue = new ArrayBlockingQueue<Item>(n + 2);
        for (int i = 0; i < n + 3; i++) pool.add(ByteBuffer.allocateDirect(inW * inH * 4));
        threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    encodeLoop();
                }
            }, "Mytic-Clips-JPEG-" + (i + 1));
            threads[i].setDaemon(true);
            threads[i].setPriority(Thread.NORM_PRIORITY - 2); // das Spiel geht vor
            threads[i].start();
        }
    }

    /** Anzahl der Kodier-Threads */
    public int encoders() {
        return threads.length;
    }

    /** Budget für Clips: höchstens 300 MB und höchstens ein Achtel des Java-Speichers */
    public static long defaultBudget() {
        return Math.max(32L << 20, Math.min(300L << 20, Runtime.getRuntime().maxMemory() / 8));
    }

    public boolean running() {
        if (!running) return false;
        for (Thread t : threads) if (t.isAlive()) return true;
        return false;
    }

    /** Fehler des Kodierers (z. B. kein JPEG in dieser Java-Laufzeit) oder null */
    public String error() {
        return error;
    }

    public float quality() {
        return quality;
    }

    /** Freier Puffer für das nächste Bild oder null (dann wird dieses Bild ausgelassen) */
    public ByteBuffer borrow() {
        if (!running) return null;
        ByteBuffer b = pool.poll();
        if (b != null) b.clear();
        return b;
    }

    public void release(ByteBuffer b) {
        if (b != null) pool.offer(b);
    }

    /** gefülltes Bild zum Kodieren geben (Zeitpunkt: jetzt) */
    public void submit(ByteBuffer b) {
        if (!running || !queue.offer(new Item(b, System.nanoTime()))) pool.offer(b);
    }

    public void stop() {
        running = false;
        for (Thread t : threads) t.interrupt();
        synchronized (ring) {
            ring.clear();
            bytes = 0;
        }
    }

    /** Belegter Speicher (JPEG-Daten) */
    public long bytes() {
        synchronized (ring) {
            return bytes;
        }
    }

    /** Sekunden im Puffer */
    public double bufferedSeconds() {
        synchronized (ring) {
            return ring.size() < 2 ? 0 : (ring.peekLast().nanos - ring.peekFirst().nanos) / 1e9;
        }
    }

    /** Die letzten {@code secs} Sekunden (Kopie der Liste, die Bilder selbst werden nicht kopiert) */
    public List<Frame> last(int secs) {
        synchronized (ring) {
            List<Frame> out = new ArrayList<Frame>(ring.size());
            if (ring.isEmpty()) return out;
            long from = ring.peekLast().nanos - secs * 1_000_000_000L;
            for (Frame f : ring) if (f.nanos >= from) out.add(f);
            return out;
        }
    }

    private void encodeLoop() {
        ImageWriter writer;
        try {
            Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
            if (!it.hasNext()) throw new IllegalStateException("kein JPEG-Kodierer");
            writer = it.next();
        } catch (Throwable t) {
            error = "Kein JPEG-Kodierer in dieser Java-Version";
            running = false;
            return;
        }
        BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_3BYTE_BGR);
        byte[] px = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        byte[] row = new byte[inW * 4];
        int[] xs = new int[outW];
        for (int x = 0; x < outW; x++) xs[x] = (int) ((long) x * inW / outW) * 4;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        long target = Math.max(8 * 1024, budget * 9 / 10 / Math.max(1, (long) fps * seconds)); // Bytes je Bild, damit die Länge passt
        double avg = 0;
        int n = 0;
        try {
            while (running) {
                Item item = queue.poll(500, TimeUnit.MILLISECONDS);
                if (item == null) continue;
                try {
                    convert(item.buf, px, row, xs);
                } finally {
                    pool.offer(item.buf);
                }
                bos.reset();
                MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos);
                param.setCompressionQuality(quality);
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
                ios.close();
                byte[] jpeg = bos.toByteArray();
                add(new Frame(jpeg, item.nanos));
                // Qualität nachregeln (alle 15 Bilder), damit die eingestellte Länge ins Budget passt
                avg = n == 0 ? jpeg.length : avg * 0.9 + jpeg.length * 0.1;
                if (++n % 15 == 0) {
                    if (avg > target && quality > 0.3f) quality = Math.max(0.3f, quality - 0.05f);
                    else if (avg < target * 0.6 && quality < 0.85f) quality = Math.min(0.85f, quality + 0.05f);
                }
            }
        } catch (InterruptedException ignored) {
            // beendet
        } catch (Throwable t) {
            error = "Kodieren fehlgeschlagen: " + t;
            running = false;
        } finally {
            writer.dispose();
        }
    }

    private void add(Frame f) {
        synchronized (ring) {
            if (!running) return;
            // mehrere Kodierer: nach Zeitpunkt einsortieren (meist ganz hinten)
            ArrayDeque<Frame> later = null;
            while (!ring.isEmpty() && ring.peekLast().nanos > f.nanos) {
                if (later == null) later = new ArrayDeque<Frame>();
                later.addFirst(ring.pollLast());
            }
            ring.addLast(f);
            if (later != null) ring.addAll(later);
            bytes += f.jpeg.length;
            long keep = (seconds + 1) * 1_000_000_000L, newest = ring.peekLast().nanos;
            while (ring.size() > 1 && (newest - ring.peekFirst().nanos > keep || bytes > budget)) bytes -= ring.pollFirst().jpeg.length;
        }
    }

    /** RGBA (Zeilen ggf. von unten) → BGR in Clip-Größe (nächster Nachbar; meist verkleinert schon die Grafikkarte) */
    private void convert(ByteBuffer src, byte[] px, byte[] row, int[] xs) {
        ByteBuffer d = src.duplicate();
        int stride = inW * 4;
        int o = 0;
        for (int y = 0; y < outH; y++) {
            int sy = (int) ((long) y * inH / outH);
            if (flip) sy = inH - 1 - sy;
            ((Buffer) d).position(sy * stride);
            d.get(row, 0, stride);
            for (int x = 0; x < outW; x++) {
                int s = xs[x];
                px[o++] = row[s + 2];
                px[o++] = row[s + 1];
                px[o++] = row[s];
            }
        }
    }
}
