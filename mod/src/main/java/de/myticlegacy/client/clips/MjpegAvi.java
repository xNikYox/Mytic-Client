package de.myticlegacy.client.clips;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Schreibt JPEG-Bilder als Motion-JPEG in eine AVI-Datei (RIFF, AVI 1.0 mit idx1) – reines Java, keine Zusatzprogramme.
 * Spielt in VLC, Windows Media Player/Filme &amp; TV und lässt sich in jedem Schnittprogramm öffnen.
 * Java-8-tauglich (gleiche Datei in der 1.8.9-Mod).
 */
public final class MjpegAvi {
    private static final int AVIF_HASINDEX = 0x10;
    private static final int AVIIF_KEYFRAME = 0x10;

    private MjpegAvi() {
    }

    /** Bildrate aus den Zeitpunkten (ausgelassene Bilder → niedrigere Rate statt Zeitraffer) */
    public static double fps(List<ClipBuffer.Frame> frames, int fallback) {
        if (frames.size() < 2) return fallback;
        double secs = (frames.get(frames.size() - 1).nanos - frames.get(0).nanos) / 1e9;
        double fps = secs <= 0 ? fallback : (frames.size() - 1) / secs;
        return Math.max(1, Math.min(240, fps));
    }

    public static void write(File out, List<ClipBuffer.Frame> frames, int width, int height, double fps) throws IOException {
        OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 20);
        try {
            write(os, frames, width, height, fps);
        } finally {
            os.close();
        }
    }

    public static void write(OutputStream os, List<ClipBuffer.Frame> frames, int width, int height, double fps) throws IOException {
        int n = frames.size();
        long movi = 4, maxFrame = 0;
        for (ClipBuffer.Frame f : frames) {
            movi += 8 + f.jpeg.length + (f.jpeg.length & 1);
            maxFrame = Math.max(maxFrame, f.jpeg.length);
        }
        long idx = 16L * n;
        int hdrl = 4 + (8 + 56) + (8 + 4 + (8 + 56) + (8 + 40));
        long riff = 4 + (8 + hdrl) + (8 + movi) + (8 + idx);
        if (riff > 0xFFFFFFFFL - 8) throw new IOException("Clip zu groß für AVI");
        int scale = 1000, rate = (int) Math.round(fps * scale);
        int usPerFrame = (int) Math.round(1_000_000 / fps);
        double secs = n / fps;
        int bytesPerSec = (int) Math.min(Integer.MAX_VALUE, Math.round((movi) / Math.max(secs, 0.001)));

        W w = new W(os);
        w.fourcc("RIFF").u32(riff).fourcc("AVI ");
        w.fourcc("LIST").u32(hdrl).fourcc("hdrl");
        // Hauptkopf
        w.fourcc("avih").u32(56)
                .u32(usPerFrame).u32(bytesPerSec).u32(0).u32(AVIF_HASINDEX)
                .u32(n).u32(0).u32(1).u32(maxFrame)
                .u32(width).u32(height).u32(0).u32(0).u32(0).u32(0);
        w.fourcc("LIST").u32(4 + (8 + 56) + (8 + 40)).fourcc("strl");
        // Stream-Kopf: Video, Motion-JPEG
        w.fourcc("strh").u32(56)
                .fourcc("vids").fourcc("MJPG").u32(0).u16(0).u16(0).u32(0)
                .u32(scale).u32(rate).u32(0).u32(n).u32(maxFrame).u32(0xFFFFFFFFL).u32(0)
                .u16(0).u16(0).u16(width).u16(height);
        // Bildformat (BITMAPINFOHEADER)
        w.fourcc("strf").u32(40)
                .u32(40).u32(width).u32(height).u16(1).u16(24).fourcc("MJPG").u32((long) width * height * 3)
                .u32(0).u32(0).u32(0).u32(0);
        // Bilder
        w.fourcc("LIST").u32(movi).fourcc("movi");
        for (ClipBuffer.Frame f : frames) {
            w.fourcc("00dc").u32(f.jpeg.length);
            os.write(f.jpeg);
            if ((f.jpeg.length & 1) != 0) os.write(0);
        }
        // Index (Versatz ab dem „movi“-Kennzeichen)
        w.fourcc("idx1").u32(idx);
        long offset = 4;
        for (ClipBuffer.Frame f : frames) {
            w.fourcc("00dc").u32(AVIIF_KEYFRAME).u32(offset).u32(f.jpeg.length);
            offset += 8 + f.jpeg.length + (f.jpeg.length & 1);
        }
        os.flush();
    }

    /** kleine Little-Endian-Hilfe */
    private static final class W {
        private final OutputStream os;
        private final byte[] b = new byte[4];

        W(OutputStream os) {
            this.os = os;
        }

        W fourcc(String s) throws IOException {
            for (int i = 0; i < 4; i++) os.write(s.charAt(i));
            return this;
        }

        W u32(long v) throws IOException {
            b[0] = (byte) v;
            b[1] = (byte) (v >>> 8);
            b[2] = (byte) (v >>> 16);
            b[3] = (byte) (v >>> 24);
            os.write(b, 0, 4);
            return this;
        }

        W u16(int v) throws IOException {
            os.write(v & 0xFF);
            os.write((v >>> 8) & 0xFF);
            return this;
        }
    }
}
