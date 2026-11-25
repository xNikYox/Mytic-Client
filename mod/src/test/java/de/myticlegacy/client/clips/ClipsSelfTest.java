package de.myticlegacy.client.clips;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Selbsttest für Clips ohne Minecraft (läuft bei jedem Build der 1.21.11-Mod, Gradle-Aufgabe clipsTest):
 * Ringpuffer (Länge, Speicher-Budget, Spiegeln, Verkleinern), JPEG-Kodierung und AVI-Aufbau (RIFF-Größen, Index).
 */
public final class ClipsSelfTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        ringAndImage();
        budget();
        avi();
        System.out.println("Clips-Selbsttest: " + checks + " Prüfungen ok");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    /** RGBA-Bild: obere Hälfte rot, untere blau (Zeile 0 = oben) */
    private static final java.util.Random RANDOM = new java.util.Random(1);
    private static boolean noise;

    private static void fill(ByteBuffer b, int w, int h, boolean upsideDown) {
        if (noise) { // Rauschen lässt sich kaum packen → große JPEGs
            byte[] all = new byte[w * h * 4];
            RANDOM.nextBytes(all);
            b.position(0);
            b.put(all);
            b.position(0).limit(w * h * 4);
            return;
        }
        for (int y = 0; y < h; y++) {
            int row = upsideDown ? h - 1 - y : y;
            boolean top = y < h / 2;
            for (int x = 0; x < w; x++) {
                int o = (row * w + x) * 4;
                b.put(o, (byte) (top ? 220 : 10));
                b.put(o + 1, (byte) 20);
                b.put(o + 2, (byte) (top ? 10 : 220));
                b.put(o + 3, (byte) 255);
            }
        }
        b.position(0).limit(w * h * 4);
    }

    private static List<ClipBuffer.Frame> feed(ClipBuffer buf, int frames, int w, int h, boolean upsideDown, long sleepMs) throws InterruptedException {
        for (int i = 0; i < frames; i++) {
            ByteBuffer b = null;
            for (int t = 0; t < 200 && b == null; t++) {
                b = buf.borrow();
                if (b == null) Thread.sleep(2);
            }
            check(b != null, "Puffer frei");
            fill(b, w, h, upsideDown);
            buf.submit(b);
            Thread.sleep(sleepMs);
        }
        Thread.sleep(300);
        return buf.last(60);
    }

    private static void ringAndImage() throws Exception {
        // 320×180 auf dem Kopf (OpenGL), auf 160×90 verkleinern
        ClipBuffer buf = new ClipBuffer(320, 180, 160, 90, true, 30, 15, 64L << 20);
        List<ClipBuffer.Frame> frames = feed(buf, 20, 320, 180, true, 5);
        check(buf.error() == null, "kein Kodierfehler: " + buf.error());
        check(frames.size() >= 15, "Bilder im Puffer: " + frames.size());
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(frames.get(0).jpeg));
        check(img.getWidth() == 160 && img.getHeight() == 90, "Größe " + img.getWidth() + "x" + img.getHeight());
        int top = img.getRGB(80, 10), bottom = img.getRGB(80, 80);
        check(((top >> 16) & 0xFF) > 150 && (top & 0xFF) < 80, "oben rot (gespiegelt richtig): " + Integer.toHexString(top));
        check((bottom & 0xFF) > 150 && ((bottom >> 16) & 0xFF) < 80, "unten blau: " + Integer.toHexString(bottom));
        for (int i = 1; i < frames.size(); i++) check(frames.get(i).nanos >= frames.get(i - 1).nanos, "Reihenfolge");
        buf.stop();
        check(buf.borrow() == null, "nach stop keine Puffer mehr");
    }

    private static void budget() throws Exception {
        // winziges Budget: Speicher bleibt darunter, Qualität sinkt
        noise = true;
        ClipBuffer buf = new ClipBuffer(640, 360, 640, 360, false, 30, 60, 2 << 20);
        feed(buf, 60, 640, 360, false, 1);
        noise = false;
        check(buf.bytes() <= 2 << 20, "Budget eingehalten: " + buf.bytes());
        check(buf.quality() < 0.8f, "Qualität gesenkt: " + buf.quality());
        buf.stop();
        check(ClipBuffer.defaultBudget() <= 300L << 20, "Standard-Budget ≤ 300 MB");
    }

    private static int u32(byte[] a, int o) {
        return (a[o] & 0xFF) | (a[o + 1] & 0xFF) << 8 | (a[o + 2] & 0xFF) << 16 | (a[o + 3] & 0xFF) << 24;
    }

    private static String cc(byte[] a, int o) {
        return new String(a, o, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static void avi() throws IOException {
        List<ClipBuffer.Frame> frames = new ArrayList<ClipBuffer.Frame>();
        long t = 0;
        for (int i = 0; i < 31; i++) {
            BufferedImage img = new BufferedImage(64, 36, BufferedImage.TYPE_3BYTE_BGR);
            ByteArrayOutputStream jpg = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", jpg);
            byte[] data = jpg.toByteArray();
            if (i == 3) data = java.util.Arrays.copyOf(data, data.length + (data.length % 2 == 0 ? 1 : 0)); // ungerade Länge → Füllbyte
            frames.add(new ClipBuffer.Frame(data, t));
            t += 33_333_333L;
        }
        double fps = MjpegAvi.fps(frames, 30);
        check(Math.abs(fps - 30) < 0.01, "Bildrate aus Zeitpunkten: " + fps);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MjpegAvi.write(out, frames, 64, 36, fps);
        byte[] a = out.toByteArray();
        check(cc(a, 0).equals("RIFF") && cc(a, 8).equals("AVI "), "RIFF AVI");
        check(u32(a, 4) == a.length - 8, "RIFF-Größe");
        check(cc(a, 12).equals("LIST") && cc(a, 20).equals("hdrl") && cc(a, 24).equals("avih"), "hdrl/avih");
        check(u32(a, 32) == 33333, "µs je Bild");
        check(u32(a, 48) == 31, "Bildanzahl");
        int hdrlEnd = 20 + u32(a, 16);
        check(cc(a, hdrlEnd).equals("LIST") && cc(a, hdrlEnd + 8).equals("movi"), "movi folgt auf hdrl");
        int moviFourcc = hdrlEnd + 8;
        int idx = hdrlEnd + 8 + u32(a, hdrlEnd + 4);
        check(cc(a, idx).equals("idx1") && u32(a, idx + 4) == 16 * 31, "idx1");
        check(idx + 8 + 16 * 31 == a.length, "Datei endet nach dem Index");
        for (int i = 0; i < 31; i++) {
            int e = idx + 8 + i * 16;
            int chunk = moviFourcc + u32(a, e + 8);
            check(cc(a, chunk).equals("00dc") && u32(a, chunk + 4) == u32(a, e + 12), "Index zeigt auf Bild " + i);
            check((a[chunk + 8] & 0xFF) == 0xFF && (a[chunk + 9] & 0xFF) == 0xD8, "JPEG-Anfang in Bild " + i);
        }
    }
}
