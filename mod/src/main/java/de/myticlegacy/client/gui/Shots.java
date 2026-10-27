package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Tex;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Screenshots: Liste, Vorschaubilder (verkleinert im Hintergrund, als Textur hochgeladen), Kopieren in die
 * Zwischenablage, im Ordner zeigen, löschen. Erkennt neue Screenshots (F2) für die Vorschau-Karte.
 */
public final class Shots {
    public record Thumb(Object texture, int w, int h) {
    }

    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mytic-Screenshots");
        t.setDaemon(true);
        return t;
    });
    private static final Map<Path, Thumb> thumbs = new HashMap<>();
    private static final Map<Path, Boolean> loading = new HashMap<>();
    private static final ConcurrentLinkedQueue<Object[]> ready = new ConcurrentLinkedQueue<>();
    private static int counter;
    private static long lastCheck, newestSeen = -1;
    public static Path latest;
    public static long latestAt;
    public static String latestNote = "";

    private Shots() {
    }

    public static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("screenshots");
    }

    public static List<Path> list() {
        try (Stream<Path> s = Files.list(dir())) {
            List<Path> out = new ArrayList<>(s.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList());
            out.sort(Comparator.comparingLong(Shots::modified).reversed());
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Vorschaubild oder null (wird dann im Hintergrund erzeugt) */
    public static Thumb thumb(Path p, int maxW) {
        pump();
        Thumb t = thumbs.get(p);
        if (t != null || loading.containsKey(p)) return t;
        loading.put(p, true);
        worker.submit(() -> {
            try {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img == null) return;
                int w = Math.min(maxW, img.getWidth()), h = Math.max(1, img.getHeight() * w / img.getWidth());
                BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = small.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, w, h, null);
                g.dispose();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(small, "png", out);
                ready.add(new Object[]{p, out.toByteArray(), w, h});
            } catch (Exception e) {
                MyticClient.LOG.warn("Screenshot-Vorschau nicht möglich: {}", p.getFileName());
            }
        });
        return null;
    }

    /** fertige Vorschaubilder auf dem Render-Thread als Textur anmelden */
    private static void pump() {
        Object[] r;
        while ((r = ready.poll()) != null) {
            Path p = (Path) r[0];
            Object tex = Tex.upload("shot" + (counter++), (byte[]) r[1]);
            thumbs.put(p, new Thumb(tex, (int) r[2], (int) r[3]));
            loading.remove(p);
        }
    }

    public static void forget(Path p) {
        Thumb t = thumbs.remove(p);
        if (t != null) Tex.release(t.texture());
        loading.remove(p);
    }

    public static boolean delete(Path p) {
        try {
            forget(p);
            return Files.deleteIfExists(p);
        } catch (Exception e) {
            return false;
        }
    }

    /** Bild in die Zwischenablage (zum Einfügen in Discord & Co.) – über das Betriebssystem, Minecraft läuft ohne AWT-Fenster */
    public static boolean copy(Path p) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String file = p.toAbsolutePath().toString();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                String ps = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('" + file.replace("'", "''") + "'))";
                pb = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", ps);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("osascript", "-e", "set the clipboard to (read (POSIX file \"" + file + "\") as «class PNGf»)");
            } else {
                pb = new ProcessBuilder("sh", "-c", "(command -v wl-copy >/dev/null && wl-copy --type image/png < \"$0\") || xclip -selection clipboard -t image/png -i \"$0\"", file);
            }
            Process proc = pb.redirectErrorStream(true).start();
            return proc.waitFor(8, java.util.concurrent.TimeUnit.SECONDS) && proc.exitValue() == 0;
        } catch (Exception e) {
            MyticClient.LOG.warn("Screenshot kopieren fehlgeschlagen", e);
            return false;
        }
    }

    /** Datei im Explorer/Finder zeigen */
    public static void reveal(Path p) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            File f = p.toFile();
            if (os.contains("win")) new ProcessBuilder("explorer", "/select," + f.getAbsolutePath()).start();
            else if (os.contains("mac")) new ProcessBuilder("open", "-R", f.getAbsolutePath()).start();
            else new ProcessBuilder("xdg-open", (f.isDirectory() ? f : f.getParentFile()).getAbsolutePath()).start();
        } catch (Exception e) {
            MyticClient.LOG.warn("Ordner öffnen fehlgeschlagen", e);
        }
    }

    /** Jeden Tick: neuen Screenshot erkennen (höchstens 2× pro Sekunde nachsehen) */
    public static void tick(boolean autoCopy) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 500) return;
        lastCheck = now;
        File[] files = dir().toFile().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        long newest = 0;
        File newestFile = null;
        if (files != null) for (File f : files) if (f.lastModified() > newest) {
            newest = f.lastModified();
            newestFile = f;
        }
        if (newestSeen < 0) { // erster Durchlauf: Bestand merken, nicht melden
            newestSeen = newest;
            return;
        }
        if (newestFile != null && newest > newestSeen) {
            newestSeen = newest;
            latest = newestFile.toPath();
            latestAt = now;
            latestNote = "Gespeichert ✓";
            if (autoCopy) {
                Path shot = latest;
                worker.submit(() -> {
                    try {
                        Thread.sleep(400); // Minecraft schreibt die Datei im Hintergrund fertig
                    } catch (InterruptedException ignored) {
                    }
                    latestNote = copy(shot) ? "Kopiert ✓" : "Kopieren nicht möglich";
                });
            }
        }
    }
}
