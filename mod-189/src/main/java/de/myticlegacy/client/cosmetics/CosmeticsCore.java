package de.myticlegacy.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.friends.Friends;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Cosmetics unabhängig von der Minecraft-Version: Katalog und Vorschaubilder aus dem Cosmetics-Paket
 * (-Dmytic.cosmetics, vom Launcher), eigene Auswahl und die Umhänge anderer Mytic-Spieler vom Friends-Server.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class CosmeticsCore {
    public static final class Cape {
        public final String id;
        public final String name;
        public final int frames;
        public final long frameMs;
        /** Vorschau pro Bild: PREVIEW_W x PREVIEW_H Pixel ARGB (null, solange nicht geladen). */
        volatile int[][] preview;

        Cape(String id, String name, int frames, long frameMs) {
            this.id = id;
            this.name = name;
            this.frames = frames;
            this.frameMs = frameMs;
        }

        public int frame() {
            return (int) ((System.currentTimeMillis() / frameMs) % frames);
        }

        public int[] previewFrame() {
            int[][] p = preview;
            return p == null ? null : p[frame()];
        }
    }

    public static final int PREVIEW_W = 20;
    public static final int PREVIEW_H = 32;
    /** Wie lange die Umhänge anderer Spieler gemerkt werden, bevor neu gefragt wird. */
    private static final long REMOTE_MS = 2 * 60 * 1000;

    private static final String PACK = System.getProperty("mytic.cosmetics", "").trim();
    private static List<Cape> capes;
    private static volatile boolean previewsRequested;

    /** uuid (ohne Striche) → Umhang-ID oder "" (keiner). */
    private static final Map<String, String> REMOTE = new ConcurrentHashMap<>();
    private static final Map<String, Long> ASKED = new ConcurrentHashMap<>();
    private static volatile boolean lookupRunning;
    private static volatile boolean pushed;

    private CosmeticsCore() {
    }

    public static boolean available() {
        return !capes().isEmpty();
    }

    /** Katalog aus cosmetics.json im Paket (leer, wenn das Paket fehlt). */
    @SuppressWarnings("deprecation")
    public static synchronized List<Cape> capes() {
        if (capes != null) return capes;
        List<Cape> list = new ArrayList<>();
        if (!PACK.isEmpty() && new File(PACK).isFile()) {
            try (ZipFile zip = new ZipFile(PACK)) {
                ZipEntry entry = zip.getEntry("cosmetics.json");
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                        JsonArray array = root.getAsJsonArray("capes");
                        for (JsonElement e : array) {
                            JsonObject o = e.getAsJsonObject();
                            list.add(new Cape(o.get("id").getAsString(), o.get("name").getAsString(), o.get("frames").getAsInt(), o.get("frameMs").getAsLong()));
                        }
                    }
                }
            } catch (Exception e) {
                MyticClient.LOG.warn("Cosmetics-Paket nicht lesbar: " + e);
            }
        }
        capes = Collections.unmodifiableList(list);
        return capes;
    }

    public static Cape byId(String id) {
        if (id == null) return null;
        for (Cape c : capes()) if (c.id.equals(id)) return c;
        return null;
    }

    public static String packPath() {
        return PACK;
    }

    /** Vorschaubilder im Hintergrund laden (für das Cosmetics-Menü). */
    public static void loadPreviews() {
        if (previewsRequested || capes().isEmpty()) return;
        previewsRequested = true;
        Thread t = new Thread(() -> {
            try (ZipFile zip = new ZipFile(PACK)) {
                for (Cape cape : capes()) {
                    int[][] frames = new int[cape.frames][];
                    for (int f = 0; f < cape.frames; f++) frames[f] = preview(zip, "assets/myticclient/textures/cape/" + cape.id + "_" + f + ".png");
                    cape.preview = frames;
                }
            } catch (Exception e) {
                MyticClient.LOG.warn("Cosmetics-Vorschau nicht geladen: " + e);
            }
        }, "Mytic Cosmetics Vorschau");
        t.setDaemon(true);
        t.start();
    }

    /** Außenseite des Umhangs (Bereich 1..11 x 1..17 der 64x32-Vorlage) auf PREVIEW_W x PREVIEW_H verkleinern. */
    private static int[] preview(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        int[] out = new int[PREVIEW_W * PREVIEW_H];
        if (entry == null) return out;
        BufferedImage img;
        try (InputStream in = zip.getInputStream(entry)) {
            img = ImageIO.read(in);
        }
        double k = img.getWidth() / 64.0;
        double x0 = k, y0 = k, w = 10 * k, h = 16 * k;
        for (int py = 0; py < PREVIEW_H; py++) {
            for (int px = 0; px < PREVIEW_W; px++) {
                int sx0 = (int) (x0 + px * w / PREVIEW_W), sx1 = (int) Math.max(sx0 + 1, x0 + (px + 1) * w / PREVIEW_W);
                int sy0 = (int) (y0 + py * h / PREVIEW_H), sy1 = (int) Math.max(sy0 + 1, y0 + (py + 1) * h / PREVIEW_H);
                long r = 0, g = 0, b = 0, n = 0;
                for (int y = sy0; y < sy1; y++) {
                    for (int x = sx0; x < sx1; x++) {
                        int c = img.getRGB(x, y);
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                        n++;
                    }
                }
                out[py * PREVIEW_W + px] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ Auswahl

    public static String selected() {
        String id = MyticClient.config().cape;
        return byId(id) != null ? id : null;
    }

    public static void select(String id) {
        MyticClient.config().cape = id;
        MyticClient.config().save();
        push();
    }

    /** Eigene Auswahl an den Friends-Server melden, damit andere Mytic-Spieler sie sehen. */
    public static void push() {
        pushed = true;
        final String id = MyticClient.config().cape;
        Friends.background(() -> {
            try {
                JsonObject body = new JsonObject();
                if (id != null) body.addProperty("cape", id);
                Friends.post("api/cosmetics", body);
            } catch (Exception e) {
                MyticClient.LOG.warn("Cosmetics nicht gespeichert: " + e.getMessage());
            }
        });
    }

    /** Umhang eines Spielers: eigener aus der Auswahl, andere vom Friends-Server. */
    public static Cape capeOf(String uuid, boolean self) {
        if (self) return byId(MyticClient.config().cape);
        String id = REMOTE.get(uuid);
        return id == null || id.isEmpty() ? null : byId(id);
    }

    /**
     * Regelmäßig aus dem Spiel-Thread: Umhänge der Spieler in der Nähe nachfragen (gesammelt, höchstens 100 auf
     * einmal, je Spieler alle 2 Minuten).
     */
    public static void tick(List<String> nearby) {
        if (!Friends.available() || !available()) return;
        if (!pushed && MyticClient.config().cape != null) push();
        if (lookupRunning) return;
        long now = System.currentTimeMillis();
        final List<String> ask = new ArrayList<>();
        for (String uuid : nearby) {
            Long at = ASKED.get(uuid);
            if (at == null || now - at > REMOTE_MS) ask.add(uuid);
            if (ask.size() >= 100) break;
        }
        if (ask.isEmpty()) return;
        for (String uuid : ask) ASKED.put(uuid, now);
        lookupRunning = true;
        Friends.background(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray array = new JsonArray();
                for (String uuid : ask) array.add(new com.google.gson.JsonPrimitive(uuid));
                body.add("uuids", array);
                JsonObject result = Friends.post("api/cosmetics/lookup", body).getAsJsonObject("capes");
                Map<String, String> found = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : result.entrySet()) found.put(e.getKey(), e.getValue().getAsString());
                for (String uuid : ask) REMOTE.put(uuid, found.containsKey(uuid) ? found.get(uuid) : "");
            } catch (Exception e) {
                // in 30 Sekunden erneut versuchen
                long retry = System.currentTimeMillis() - REMOTE_MS + 30000;
                for (String uuid : ask) ASKED.put(uuid, retry);
            } finally {
                lookupRunning = false;
            }
        });
    }
}
