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
    /** Flügel aus dem Katalog: {id, name} */
    private static List<String[]> wings = Collections.emptyList();

    /** uuid (ohne Striche) → Umhang-ID oder "" (keiner). */
    private static final Map<String, String> REMOTE = new ConcurrentHashMap<>();
    /** uuid (ohne Striche) → MiniMe-Name oder "" (keiner). */
    /** uuid → MiniMes je Stelle (leer = keine). */
    private static final Map<String, Map<String, String>> REMOTE_MINI = new ConcurrentHashMap<>();
    /** Glanz-Animation der Flügel: Bilder und Dauer pro Bild (wie im Cosmetics-Paket). */
    public static final int WING_FX_FRAMES = 16;
    public static final long WING_FX_MS = 90;
    /** uuid (ohne Striche) → Flügel-ID oder "" (keine). */
    private static final Map<String, String> REMOTE_WINGS = new ConcurrentHashMap<>();
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
                        if (root.has("wings")) {
                            List<String[]> w = new ArrayList<>();
                            for (JsonElement e : root.getAsJsonArray("wings")) {
                                JsonObject o = e.getAsJsonObject();
                                w.add(new String[]{o.get("id").getAsString(), o.get("name").getAsString()});
                            }
                            wings = Collections.unmodifiableList(w);
                        }
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

    /** Flügel im Paket: {id, name} */
    public static List<String[]> wings() {
        capes();
        return wings;
    }

    public static boolean hasWings(String id) {
        if (id == null) return false;
        for (String[] w : wings()) if (w[0].equals(id)) return true;
        return false;
    }

    /** Eigene Flügel (Vorschau: -Dmytic.wingsDemo=true wechselt alle 4 Sekunden). */
    public static String selectedWings() {
        if (Boolean.getBoolean("mytic.wingsDemo") && !wings().isEmpty()) return wings().get((int) ((System.currentTimeMillis() / 4000) % wings().size()))[0];
        String id = MyticClient.config().wings;
        return hasWings(id) ? id : null;
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
        final Map<String, String> minis = MiniMeCore.selectedAll();
        final String wingsId = MyticClient.config().wings;
        Friends.background(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("cape", id);
                JsonObject set = new JsonObject();
                for (Map.Entry<String, String> e : minis.entrySet()) set.addProperty(e.getKey(), e.getValue());
                body.add("minimes", set);
                body.addProperty("wings", wingsId);
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

    /** Flügel eines Spielers: eigene aus der Auswahl, andere vom Friends-Server. */
    public static String wingsOf(String uuid, boolean self) {
        if (self) return selectedWings();
        String id = REMOTE_WINGS.get(uuid);
        return id == null || id.isEmpty() || !hasWings(id) ? null : id;
    }

    public static void selectWings(String id) {
        MyticClient.config().wings = id;
        MyticClient.config().save();
        push();
    }

    /** MiniMe eines anderen Spielers an einer Stelle (vom Friends-Server) oder null. */
    public static String remoteMiniMe(String uuid, String pos) {
        Map<String, String> set = REMOTE_MINI.get(uuid);
        return set == null ? null : set.get(pos);
    }

    /**
     * Regelmäßig aus dem Spiel-Thread: Umhänge der Spieler in der Nähe nachfragen (gesammelt, höchstens 100 auf
     * einmal, je Spieler alle 2 Minuten).
     */
    public static void tick(List<String> nearby) {
        if (!Friends.available()) return;
        if (!pushed && (MyticClient.config().cape != null || MiniMeCore.any() || MyticClient.config().wings != null)) push();
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
                JsonObject answer = Friends.post("api/cosmetics/lookup", body);
                Map<String, String> found = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("capes").entrySet()) found.put(e.getKey(), e.getValue().getAsString());
                Map<String, Map<String, String>> minis = new HashMap<>();
                if (answer.has("minimeSets")) {
                    for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("minimeSets").entrySet()) {
                        Map<String, String> set = new HashMap<>();
                        for (Map.Entry<String, JsonElement> p : e.getValue().getAsJsonObject().entrySet()) set.put(p.getKey(), p.getValue().getAsString());
                        minis.put(e.getKey(), set);
                    }
                }
                Map<String, String> wingsFound = new HashMap<>();
                if (answer.has("wings")) {
                    for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("wings").entrySet()) wingsFound.put(e.getKey(), e.getValue().getAsString());
                }
                for (String uuid : ask) {
                    REMOTE.put(uuid, found.containsKey(uuid) ? found.get(uuid) : "");
                    REMOTE_MINI.put(uuid, minis.containsKey(uuid) ? minis.get(uuid) : new HashMap<String, String>());
                    REMOTE_WINGS.put(uuid, wingsFound.containsKey(uuid) ? wingsFound.get(uuid) : "");
                }
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
