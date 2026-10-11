package de.myticlegacy.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.friends.Friends;

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
        /** Auflösung gegenüber der 64x32-Vorlage (Paket 1.0: 8 → 512x256). */
        public final int scale;

        Cape(String id, String name, int frames, long frameMs, int scale) {
            this.id = id;
            this.name = name;
            this.frames = frames;
            this.frameMs = frameMs;
            this.scale = scale;
        }

        public int frame() {
            return (int) ((System.currentTimeMillis() / frameMs) % frames);
        }

    }

    /** Wie lange die Umhänge anderer Spieler gemerkt werden, bevor neu gefragt wird. */
    private static final long REMOTE_MS = 2 * 60 * 1000;

    private static final String PACK = System.getProperty("mytic.cosmetics", "").trim();
    private static List<Cape> capes;

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
                            int scale = o.has("scale") ? o.get("scale").getAsInt() : (root.has("scale") ? root.get("scale").getAsInt() : 8);
                            list.add(new Cape(o.get("id").getAsString(), o.get("name").getAsString(), o.get("frames").getAsInt(), o.get("frameMs").getAsLong(), scale));
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
