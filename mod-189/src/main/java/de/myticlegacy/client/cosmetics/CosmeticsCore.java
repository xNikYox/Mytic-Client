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
    /**
     * Aura aus dem Katalog: Bewegungsmuster, Partikel pro Sekunde, Anzahl eigener Sprites (Textur aura/<id>.png),
     * Größe in Blöcken, Farben (für 1.8.9-Menü).
     */
    public static final class Aura {
        public final String id, name, pattern;
        public final float rate, size;
        public final int sprites;
        public final int[] colors;

        Aura(String id, String name, String pattern, float rate, int sprites, float size, int[] colors) {
            this.id = id;
            this.name = name;
            this.pattern = pattern;
            this.rate = rate;
            this.sprites = sprites;
            this.size = size;
            this.colors = colors;
        }
    }

    private static List<Aura> auras = Collections.emptyList();
    /** uuid (ohne Striche) → Aura-ID oder "" (keine). */
    private static final Map<String, String> REMOTE_AURA = new ConcurrentHashMap<>();
    /** Schilde aus dem Katalog: {id, name, bilder, ms je bild} */
    private static List<String[]> shields = Collections.emptyList();

    /** uuid (ohne Striche) → Umhang-ID oder "" (keiner). */
    private static final Map<String, String> REMOTE = new ConcurrentHashMap<>();
    /** uuid (ohne Striche) → MiniMe-Name oder "" (keiner). */
    /** uuid → MiniMes je Stelle (leer = keine). */
    private static final Map<String, Map<String, String>> REMOTE_MINI = new ConcurrentHashMap<>();
    /** Glanz-Animation der Flügel: Bilder und Dauer pro Bild (wie im Cosmetics-Paket). */
    public static final int WING_FX_FRAMES = 16;
    public static final long WING_FX_MS = 90;
    /** uuid (ohne Striche) → Schild-ID oder "" (keiner). */
    private static final Map<String, String> REMOTE_SHIELD = new ConcurrentHashMap<>();
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
                                // Größe des Flügels in Modell-Pixeln (Standard 20x24, breite Pixel-Flügel 24x20)
                                String ww = o.has("w") ? o.get("w").getAsString() : "20";
                                String wh = o.has("h") ? o.get("h").getAsString() : "24";
                                String feathers = o.has("feathers") && o.get("feathers").getAsBoolean() ? "1" : "0";
                                w.add(new String[]{o.get("id").getAsString(), o.get("name").getAsString(), ww, wh, feathers});
                            }
                            wings = Collections.unmodifiableList(w);
                        }
                        if (root.has("auras")) {
                            List<Aura> list2 = new ArrayList<>();
                            for (JsonElement e : root.getAsJsonArray("auras")) {
                                JsonObject o = e.getAsJsonObject();
                                JsonArray cs = o.getAsJsonArray("colors");
                                int[] colors = new int[cs.size()];
                                for (int i = 0; i < colors.length; i++) colors[i] = (int) Long.parseLong(cs.get(i).getAsString().substring(1), 16) | 0xFF000000;
                                list2.add(new Aura(o.get("id").getAsString(), o.get("name").getAsString(), o.get("pattern").getAsString(),
                                        o.get("rate").getAsFloat(), o.get("sprites").getAsInt(), o.get("size").getAsFloat(), colors));
                            }
                            auras = Collections.unmodifiableList(list2);
                        }
                        if (root.has("shields")) {
                            List<String[]> sh = new ArrayList<>();
                            for (JsonElement e : root.getAsJsonArray("shields")) {
                                JsonObject o = e.getAsJsonObject();
                                sh.add(new String[]{o.get("id").getAsString(), o.get("name").getAsString(), o.get("frames").getAsString(), o.get("frameMs").getAsString()});
                            }
                            shields = Collections.unmodifiableList(sh);
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

    /** Größe eines Flügels in Modell-Pixeln {Breite, Höhe}; die Textur ist doppelt so breit (beide Seiten). */
    public static int[] wingSize(String id) {
        for (String[] w : wings()) {
            if (w[0].equals(id)) {
                try {
                    return new int[]{Integer.parseInt(w[2]), Integer.parseInt(w[3])};
                } catch (Exception e) {
                    break;
                }
            }
        }
        return new int[]{20, 24};
    }

    /** Auren im Paket */
    public static List<Aura> auras() {
        capes();
        return auras;
    }

    public static Aura aura(String id) {
        if (id == null) return null;
        for (Aura a : auras()) if (a.id.equals(id)) return a;
        return null;
    }

    /** Eigene Aura (Vorschau: -Dmytic.auraDemo=<id> oder =all, dann alle 5 Sekunden eine andere). */
    public static String selectedAura() {
        String demo = System.getProperty("mytic.auraDemo");
        if ("all".equals(demo) && !auras().isEmpty()) return auras().get((int) ((System.currentTimeMillis() / 5000) % auras().size())).id;
        if (demo != null && aura(demo) != null) return demo;
        String id = MyticClient.config().aura;
        return aura(id) != null ? id : null;
    }

    public static void selectAura(String id) {
        MyticClient.config().aura = id;
        MyticClient.config().save();
        push();
    }

    /** Aura eines Spielers: eigene aus der Auswahl, andere vom Friends-Server. */
    public static Aura auraOf(String uuid, boolean self) {
        if (self) return aura(selectedAura());
        String id = REMOTE_AURA.get(uuid);
        return id == null || id.isEmpty() ? null : aura(id);
    }

    /** Schilde im Paket: {id, name, bilder, ms je bild} */
    public static List<String[]> shields() {
        capes();
        return shields;
    }

    public static boolean hasShield(String id) {
        if (id == null) return false;
        for (String[] s : shields()) if (s[0].equals(id)) return true;
        return false;
    }

    /** Aktuelles Bild eines animierten Schilds. */
    public static int shieldFrame(String id) {
        for (String[] s : shields()) {
            if (s[0].equals(id)) {
                int frames = Integer.parseInt(s[2]);
                long ms = Long.parseLong(s[3]);
                return (int) ((System.currentTimeMillis() / ms) % frames);
            }
        }
        return 0;
    }

    /** Eigener Schild (Vorschau: -Dmytic.shieldDemo=<id>). */
    public static String selectedShield() {
        String demo = System.getProperty("mytic.shieldDemo");
        if (demo != null && hasShield(demo)) return demo;
        String id = MyticClient.config().shield;
        return hasShield(id) ? id : null;
    }

    public static void selectShield(String id) {
        MyticClient.config().shield = id;
        MyticClient.config().save();
        push();
    }

    /** Schild eines Spielers: eigener aus der Auswahl, andere vom Friends-Server. */
    public static String shieldOf(String uuid, boolean self) {
        if (self) return selectedShield();
        String id = REMOTE_SHIELD.get(uuid);
        return id == null || id.isEmpty() || !hasShield(id) ? null : id;
    }

    /** Verlieren diese Flügel ab und zu Federn (Bild <id>_feather.png im Paket)? */
    public static boolean wingFeathers(String id) {
        for (String[] w : wings()) if (w[0].equals(id)) return w.length > 4 && "1".equals(w[4]);
        return false;
    }

    /** Flügel im Paket: {id, name, breite, höhe, federn} */
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
        final String shieldId = MyticClient.config().shield;
        final String auraId = MyticClient.config().aura;
        Friends.background(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("cape", id);
                JsonObject set = new JsonObject();
                for (Map.Entry<String, String> e : minis.entrySet()) set.addProperty(e.getKey(), e.getValue());
                body.add("minimes", set);
                body.addProperty("wings", wingsId);
                body.addProperty("shield", shieldId);
                body.addProperty("aura", auraId);
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
        if (!pushed && (MyticClient.config().cape != null || MiniMeCore.any() || MyticClient.config().wings != null || MyticClient.config().shield != null || MyticClient.config().aura != null)) push();
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
                Map<String, String> aurasFound = new HashMap<>();
                if (answer.has("auras")) {
                    for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("auras").entrySet()) aurasFound.put(e.getKey(), e.getValue().getAsString());
                }
                Map<String, String> shieldsFound = new HashMap<>();
                if (answer.has("shields")) {
                    for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("shields").entrySet()) shieldsFound.put(e.getKey(), e.getValue().getAsString());
                }
                Map<String, String> wingsFound = new HashMap<>();
                if (answer.has("wings")) {
                    for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("wings").entrySet()) wingsFound.put(e.getKey(), e.getValue().getAsString());
                }
                for (String uuid : ask) {
                    REMOTE.put(uuid, found.containsKey(uuid) ? found.get(uuid) : "");
                    REMOTE_MINI.put(uuid, minis.containsKey(uuid) ? minis.get(uuid) : new HashMap<String, String>());
                    REMOTE_AURA.put(uuid, aurasFound.containsKey(uuid) ? aurasFound.get(uuid) : "");
                    REMOTE_SHIELD.put(uuid, shieldsFound.containsKey(uuid) ? shieldsFound.get(uuid) : "");
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
