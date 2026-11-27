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

    /** Cosmetics anderer Spieler erneut fragen: mit Cosmetics nach 3 Minuten, ohne nach 6; fällige in einer Anfrage sammeln */
    private static final long REMOTE_MS = 3 * 60 * 1000;
    private static final long REMOTE_NONE_MS = 6 * 60 * 1000;
    private static final long BATCH_MS = 60 * 1000;
    /**
     * Meldet der Server Änderungen mit dem Lebenszeichen (cos1), werden geänderte Spieler sofort neu gefragt – dann
     * reicht es, alle anderen selten zu prüfen (spart Anfragen).
     */
    private static final long REMOTE_FEED_MS = 10 * 60 * 1000;
    private static final long REMOTE_FEED_NONE_MS = 15 * 60 * 1000;
    /** Fehlgeschlagene Abfragen bzw. Meldungen: erneut nach 30 s, dann doppelt so lange (höchstens 5 bzw. 10 Minuten) */
    private static final long RETRY_MS = 30 * 1000;
    private static int lookupFails;
    private static volatile int pushFails;
    private static volatile boolean pushFailed;
    private static volatile long pushRetryAt;
    /** Spieler mit Offline-UUID, die der Server über den Namen einem Konto zugeordnet hat: Konto → angefragte UUID */
    private static final Map<String, String> ALIAS = new ConcurrentHashMap<>();
    /** schon gemeldete unbekannte IDs (Cosmetics-Paket zu alt) */
    private static final java.util.Set<String> UNKNOWN = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    /** zuletzt übernommene Freischaltungen aus dem Lebenszeichen (Friends.ownedSeq) */
    private static int ownedSeq;

    private static final String PACK = System.getProperty("mytic.cosmetics", "").trim();
    /** volatile: nach dem Laden ohne Sperre lesbar (wird zuletzt gesetzt, nach Flügeln, Auren und Schilden) */
    private static volatile List<Cape> capes;
    /** UUID ohne Striche je Spieler – wird pro Spieler und Frame mehrfach gebraucht, daher zwischengespeichert */
    private static final Map<java.util.UUID, String> KEYS = new ConcurrentHashMap<>();
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

    /**
     * Accessoire „Kopf & Hals“ aus dem Katalog: Box-Modell in Modell-Pixeln (y nach oben, Füße = 0, Hals = 24, Gesicht nach
     * +z, +x = linke Seite des Spielers) mit Pixel-Textur acc/&lt;id&gt;.png im Minecraft-Box-Layout (2 Texel je Pixel).
     */
    public static final class Acc {
        public final String id, name, slot;
        public final int texW, texH;
        public final List<AccBox> boxes;

        Acc(String id, String name, String slot, int texW, int texH, List<AccBox> boxes) {
            this.id = id;
            this.name = name;
            this.slot = slot;
            this.texW = texW;
            this.texH = texH;
            this.boxes = boxes;
        }

        public boolean emissive() {
            for (AccBox b : boxes) if (b.emissive) return true;
            return false;
        }
    }

    /**
     * Eine Box: Ecke und Größe in Modell-Pixeln, Textur-Ecke und Größe in Texeln, gespiegelt, leuchtet (volle Helligkeit),
     * Blinkgruppe (0..2, nur bei leuchtenden Boxen: die Gruppen gehen der Reihe nach kurz aus; -1 = leuchtet dauerhaft).
     */
    public static final class AccBox {
        public final float x, y, z, w, h, d;
        public final int u, v, tw, th, td;
        public final boolean mirror, emissive;
        public final int blink;

        AccBox(float[] from, float[] size, int[] uv, int[] tex, boolean mirror, boolean emissive, int blink) {
            x = from[0];
            y = from[1];
            z = from[2];
            w = size[0];
            h = size[1];
            d = size[2];
            u = uv[0];
            v = uv[1];
            tw = tex[0];
            th = tex[1];
            td = tex[2];
            this.mirror = mirror;
            this.emissive = emissive;
            this.blink = emissive && blink >= 0 && blink <= 2 ? blink : -1;
        }

        /**
         * Box im Minecraft-Modell (Drehpunkt Hals, y nach unten, Gesicht nach -z) in Texel-Einheiten (beim Zeichnen
         * halb so groß): {x, y, z} der Ecke ohne Zugabe und {dx, dy, dz} Zugabe je Seite, damit die ganzzahlige
         * Textur-Größe genau die Modell-Größe ergibt.
         */
        public float[] minecraft() {
            float gx = (2 * w - tw) / 2f, gy = (2 * h - th) / 2f, gz = (2 * d - td) / 2f;
            return new float[]{2 * x + gx, 2 * (24 - y - h) + gy, 2 * (-(z + d)) + gz, gx, gy, gz};
        }
    }

    /** Blinkgruppe g (0..2) gerade an? Alle 350 ms ist eine andere Gruppe aus. */
    public static boolean blinkOn(int g) {
        return (System.currentTimeMillis() / 350 + g) % 3 != 0;
    }

    /** Plätze für Accessoires (je Platz eins): Kopf, Hals */
    public static final String[] ACC_SLOTS = {"kopf", "hals"};
    private static List<Acc> accessories = Collections.emptyList();
    /** Nametag-Vorlagen aus dem Katalog: {id, name, text} */
    private static List<String[]> nametagPresets = Collections.emptyList();
    /** uuid (ohne Striche) → Accessoires je Platz (leer = keine). */
    private static final Map<String, Map<String, String>> REMOTE_ACC = new ConcurrentHashMap<>();
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
    // ------------------------------------------------------------------------------------------ Freischaltungen
    /** Eigene Freischaltungen vom Friends-Server, z. B. "cape:galaxie", "wings:*", "minime", "nametag", "admin", "*" */
    private static volatile java.util.Set<String> owned;
    private static volatile boolean admin;
    private static volatile long ownedAt;
    private static volatile boolean ownedLoading;

    private static java.util.Set<String> ownedSet() {
        if (owned == null) {
            owned = new java.util.HashSet<String>(MyticClient.config().ownedCache == null ? new ArrayList<String>() : MyticClient.config().ownedCache);
            admin = MyticClient.config().adminCache;
        }
        return owned;
    }

    /** Darf ich das benutzen? kind: cape, wings, shield, aura, acc, minime, nametag. */
    public static boolean owns(String kind, String id) {
        java.util.Set<String> items = ownedSet();
        if (items.contains("*")) return true;
        if ("minime".equals(kind) || "nametag".equals(kind)) return items.contains(kind);
        return items.contains(kind + ":*") || items.contains(kind + ":" + id);
    }

    public static boolean isAdmin() {
        ownedSet();
        return admin;
    }

    /** Freischaltungen neu laden (beim Start, nach Admin-Änderungen; ohne Lebenszeichen mit Freischaltungen alle 3 Minuten). */
    public static void refreshOwned() {
        if (!Friends.available() || ownedLoading) return;
        ownedLoading = true;
        Friends.background(() -> {
            try {
                JsonObject answer = Friends.post("api/cosmetics/owned", new JsonObject());
                applyOwned(answer);
            } catch (Exception e) {
                // alter Stand bleibt; nächster Versuch beim nächsten Durchgang
            } finally {
                ownedAt = System.currentTimeMillis();
                ownedLoading = false;
            }
        });
    }

    /** Freischaltungen übernehmen ({items, admin}: Antwort von api/cosmetics/owned oder aus dem Lebenszeichen). */
    private static void applyOwned(JsonObject answer) {
        java.util.Set<String> set = new java.util.HashSet<String>();
        for (JsonElement e : answer.getAsJsonArray("items")) set.add(e.getAsString());
        boolean changed = !set.equals(owned);
        boolean isAdminNow = answer.has("admin") && answer.get("admin").getAsBoolean();
        if (!changed && isAdminNow == admin && owned != null) return; // nichts Neues (kommt jede Minute mit dem Lebenszeichen)
        owned = set;
        forgetSelection();
        admin = isAdminNow;
        // Config nur auf dem Spiel-Thread ändern und speichern – das Spiel ändert sie dort gleichzeitig
        boolean isAdmin = admin;
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            MyticClient.config().ownedCache = new ArrayList<String>(set);
            MyticClient.config().adminCache = isAdmin;
            MyticClient.config().save();
        });
        // neu freigeschaltet: die eigene Auswahl wieder an den Server melden
        if (changed) push();
    }

    /** uuid (ohne Striche) → Nametag (Rohtext mit &-Codes) oder "" (keiner). */
    private static final Map<String, String> REMOTE_NAMETAG = new ConcurrentHashMap<>();
    /** Höchstens so viele sichtbare Zeichen (ohne Farbcodes) bzw. Zeichen insgesamt */
    public static final int NAMETAG_VISIBLE = 32;
    public static final int NAMETAG_RAW = 80;
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
    public static List<Cape> capes() {
        List<Cape> c = capes;
        return c != null ? c : loadCapes();
    }

    /** UUID ohne Striche (Schlüssel für die Cosmetics anderer Spieler) */
    public static String key(java.util.UUID uuid) {
        String k = KEYS.get(uuid);
        if (k == null) {
            if (KEYS.size() > 4096) KEYS.clear();
            k = uuid.toString().replace("-", "");
            KEYS.put(uuid, k);
        }
        return k;
    }

    @SuppressWarnings("deprecation")
    private static synchronized List<Cape> loadCapes() {
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
                        if (root.has("accessories")) {
                            List<Acc> list3 = new ArrayList<>();
                            for (JsonElement e : root.getAsJsonArray("accessories")) {
                                try {
                                    JsonObject o = e.getAsJsonObject();
                                    List<AccBox> boxes = new ArrayList<>();
                                    for (JsonElement be : o.getAsJsonArray("boxes")) {
                                        JsonObject b = be.getAsJsonObject();
                                        boxes.add(new AccBox(floats(b.getAsJsonArray("from")), floats(b.getAsJsonArray("size")), ints(b.getAsJsonArray("uv")),
                                                ints(b.getAsJsonArray("tex")), b.has("mirror") && b.get("mirror").getAsBoolean(), b.has("emissive") && b.get("emissive").getAsBoolean(),
                                                b.has("blink") ? b.get("blink").getAsInt() : -1));
                                    }
                                    int[] tex = ints(o.getAsJsonArray("texture"));
                                    list3.add(new Acc(o.get("id").getAsString(), o.get("name").getAsString(), o.get("slot").getAsString(), tex[0], tex[1],
                                            Collections.unmodifiableList(boxes)));
                                } catch (RuntimeException bad) {
                                    MyticClient.LOG.warn("Accessoire im Cosmetics-Paket unlesbar: " + bad);
                                }
                            }
                            accessories = Collections.unmodifiableList(list3);
                        }
                        if (root.has("nametagPresets")) {
                            List<String[]> presets = new ArrayList<>();
                            for (JsonElement e : root.getAsJsonArray("nametagPresets")) {
                                JsonObject o = e.getAsJsonObject();
                                presets.add(new String[]{o.get("id").getAsString(), o.get("name").getAsString(), o.get("text").getAsString()});
                            }
                            nametagPresets = Collections.unmodifiableList(presets);
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

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    // ------------------------------------------------------------------------------------------ Accessoires (Kopf & Hals)

    /** Accessoires im Paket (ab Paket 1.13) */
    public static List<Acc> accessories() {
        capes();
        return accessories;
    }

    public static Acc acc(String id) {
        if (id == null) return null;
        for (Acc a : accessories()) if (a.id.equals(id)) return a;
        return null;
    }

    /** Nametag-Vorlagen im Paket: {id, name, text mit &-Codes} */
    public static List<String[]> nametagPresets() {
        capes();
        return nametagPresets;
    }

    private static final Map<String, Memo> SEL_ACC = new ConcurrentHashMap<>();

    /** Eigenes Accessoire an einem Platz (kopf, hals) oder null (Vorschau: -Dmytic.accDemo=<id>[,<id>]). */
    public static String selectedAcc(final String slot) {
        String demo = System.getProperty("mytic.accDemo");
        if (demo != null) {
            for (String id : demo.split(",")) {
                Acc a = acc(id.trim());
                if (a != null && a.slot.equals(slot)) return a.id;
            }
        }
        Memo m = SEL_ACC.get(slot);
        if (m == null) {
            m = new Memo();
            SEL_ACC.put(slot, m);
        }
        return memo(m, () -> {
            Map<String, String> set = MyticClient.config().acc;
            String id = set == null ? null : set.get(slot);
            Acc a = acc(id);
            return a != null && a.slot.equals(slot) && owns("acc", id) ? id : null;
        });
    }

    public static void selectAcc(String slot, String id) {
        forgetSelection();
        if (MyticClient.config().acc == null) MyticClient.config().acc = new java.util.LinkedHashMap<String, String>();
        if (id == null) MyticClient.config().acc.remove(slot);
        else MyticClient.config().acc.put(slot, id);
        MyticClient.config().save();
        push();
    }

    /** Accessoire eines Spielers an einem Platz: eigenes aus der Auswahl, andere vom Friends-Server. */
    public static Acc accOf(String uuid, boolean self, String slot) {
        if (self) return acc(selectedAcc(slot));
        Map<String, String> set = REMOTE_ACC.get(uuid);
        Acc a = set == null ? null : acc(set.get(slot));
        return a != null && a.slot.equals(slot) ? a : null;
    }

    /** Accessoires eines Spielers für die Render-Ebene: {kopf, hals} (Einträge null = keins) oder null, wenn gar keins. */
    public static Acc[] accsOf(String uuid, boolean self) {
        Acc head = accOf(uuid, self, "kopf"), neck = accOf(uuid, self, "hals");
        return head == null && neck == null ? null : new Acc[]{head, neck};
    }

    /** Kopf-Accessoire ausblenden, solange ein Helm (oder etwas anderes auf dem Kopf) getragen wird (Einstellung, Standard an) */
    public static boolean hideAccUnderHelmet() {
        return MyticClient.config().accHideHelmet;
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

    /**
     * Nametag-Rohtext in Anzeige-Text umwandeln: &0–&9, &a–&f (Farben) und &k, &l, &m, &n, &o, &r (Format) werden zu
     * Minecraft-Formatcodes; andere §-Zeichen und Steuerzeichen fallen weg. null, wenn nichts Sichtbares übrig bleibt.
     */
    /** Formatierte Nametags je Rohtext – werden pro Spieler und Frame gebraucht */
    private static final Map<String, String> NAMETAGS = new ConcurrentHashMap<>();

    public static String formatNametag(String raw) {
        if (raw == null) return null;
        String done = NAMETAGS.get(raw);
        if (done != null) return done;
        if (NAMETAGS.size() > 512) NAMETAGS.clear();
        done = buildNametag(raw);
        if (done != null) NAMETAGS.put(raw, done);
        return done;
    }

    private static String buildNametag(String raw) {
        StringBuilder out = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < raw.length() && out.length() < NAMETAG_RAW * 2; i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length() && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(raw.charAt(i + 1))) >= 0) {
                out.append('\u00a7').append(Character.toLowerCase(raw.charAt(i + 1)));
                i++;
            } else if (c != '\u00a7' && c >= ' ' && visible < NAMETAG_VISIBLE) {
                out.append(c);
                if (c != ' ') visible++;
                else visible++;
            }
        }
        return visible == 0 || raw.trim().isEmpty() ? null : out.toString();
    }

    /** Eigener Nametag (Rohtext) */
    public static String ownNametag() {
        String demo = System.getProperty("mytic.nametagDemo");
        if (demo != null) return demo.replace("_", " ");
        return owns("nametag", null) ? MyticClient.config().nametag : null;
    }

    public static void selectNametag(String raw) {
        MyticClient.config().nametag = raw == null || raw.trim().isEmpty() ? null : raw.length() > NAMETAG_RAW ? raw.substring(0, NAMETAG_RAW) : raw;
        MyticClient.config().save();
        push();
    }

    /** Nametag eines Spielers als Anzeige-Text (mit §-Codes) oder null. */
    public static String nametagOf(String uuid, boolean self) {
        return formatNametag(self ? ownNametag() : REMOTE_NAMETAG.get(uuid));
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
        return memo(SEL_AURA, () -> {
            String id = MyticClient.config().aura;
            return aura(id) != null && owns("aura", id) ? id : null;
        });
    }

    public static void selectAura(String id) {
        forgetSelection();
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
        return memo(SEL_SHIELD, () -> {
            String id = MyticClient.config().shield;
            return hasShield(id) && owns("shield", id) ? id : null;
        });
    }

    public static void selectShield(String id) {
        forgetSelection();
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
        return memo(SEL_WINGS, () -> {
            String id = MyticClient.config().wings;
            return hasWings(id) && owns("wings", id) ? id : null;
        });
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
        return memo(SEL_CAPE, () -> {
            String id = MyticClient.config().cape;
            return byId(id) != null && owns("cape", id) ? id : null;
        });
    }

    // Eigene Auswahl wird pro Frame mehrfach gebraucht (Freischaltung prüfen baut Texte) – kurz merken, bei Auswahl sofort neu
    private static final class Memo {
        volatile long at;
        volatile String value;
    }

    private static final Memo SEL_CAPE = new Memo(), SEL_WINGS = new Memo(), SEL_SHIELD = new Memo(), SEL_AURA = new Memo();

    private static String memo(Memo m, java.util.function.Supplier<String> compute) {
        long now = System.nanoTime();
        if (m.at != 0 && now - m.at < 250_000_000L) return m.value;
        String v = compute.get();
        m.value = v;
        m.at = now;
        return v;
    }

    private static void forgetSelection() {
        SEL_CAPE.at = SEL_WINGS.at = SEL_SHIELD.at = SEL_AURA.at = 0;
        for (Memo m : SEL_ACC.values()) m.at = 0;
    }

    public static void select(String id) {
        forgetSelection();
        MyticClient.config().cape = id;
        MyticClient.config().save();
        push();
    }

    /** Eigene Auswahl an den Friends-Server melden, damit andere Mytic-Spieler sie sehen. */
    public static void push() {
        pushed = true;
        pushFailed = false;
        final String id = MyticClient.config().cape;
        final Map<String, String> minis = MiniMeCore.selectedAll();
        final String wingsId = MyticClient.config().wings;
        final String shieldId = MyticClient.config().shield;
        final String auraId = MyticClient.config().aura;
        final String nametag = MyticClient.config().nametag;
        final Map<String, String> accSet = MyticClient.config().acc == null ? new HashMap<String, String>() : new HashMap<String, String>(MyticClient.config().acc);
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
                body.addProperty("nametag", nametag);
                JsonObject acc = new JsonObject();
                for (Map.Entry<String, String> e : accSet.entrySet()) if (e.getValue() != null) acc.addProperty(e.getKey(), e.getValue());
                body.add("acc", acc);
                Friends.post("api/cosmetics", body);
                pushFails = 0;
                pushFailed = false;
            } catch (Exception e) {
                // sonst sähen andere die neue Auswahl erst nach der nächsten Änderung bzw. dem nächsten Spielstart
                MyticClient.LOG.warn("Cosmetics nicht gespeichert (neuer Versuch folgt): " + e.getMessage());
                pushFails++;
                pushRetryAt = System.currentTimeMillis() + backoff(pushFails, 10 * 60 * 1000);
                pushFailed = true;
            }
        });
    }

    /** Umhang eines Spielers: eigener aus der Auswahl, andere vom Friends-Server. */
    public static Cape capeOf(String uuid, boolean self) {
        if (self) return byId(selected());
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
        forgetSelection();
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
     * einmal, je Spieler alle 3 Minuten, ohne Cosmetics alle 6; meldet der Server Änderungen mit dem Lebenszeichen,
     * geänderte sofort und alle anderen nur alle 10 bzw. 15 Minuten).
     */
    public static void tick(List<String> nearby) {
        tick(nearby, null);
    }

    /** Wartezeit nach n Fehlschlägen in Folge: 30 s, 60 s, 120 s … höchstens max. */
    private static long backoff(int fails, long max) {
        return Math.min(max, RETRY_MS << Math.min(10, Math.max(0, fails - 1)));
    }

    /** Fehlt eine gemeldete ID im Cosmetics-Paket (Paket älter als das des Trägers)? Einmal ins Log. */
    private static void checkKnown(String kind, String id, boolean known) {
        if (id == null || id.isEmpty() || known || UNKNOWN.size() > 100 || !UNKNOWN.add(kind + ":" + id)) return;
        MyticClient.LOG.warn("Cosmetics: " + kind + " \"" + id + "\" fehlt im Cosmetics-Paket (" + PACK + ") – Launcher neu starten, damit das Paket aktualisiert wird");
    }

    /**
     * names: Spielername je UUID für Spieler mit Offline-UUID (Server im Offline-Modus) – der Server ordnet sie dann
     * über den Namen dem Konto zu; null = keine.
     */
    public static void tick(List<String> nearby, final Map<String, String> names) {
        if (!Friends.available()) return;
        // Wessen Cosmetics sich geändert haben, kommt mit dem Lebenszeichen: diese Spieler gleich neu fragen
        for (String changed : Friends.takeCosChanged()) {
            ASKED.remove(changed);
            String alias = ALIAS.get(changed);
            if (alias != null) ASKED.remove(alias);
        }
        // Freischaltungen kommen mit dem Lebenszeichen der Freundesliste (neuere Server) – sonst alle 3 Minuten selbst fragen
        int seq = Friends.ownedSeq();
        if (seq != ownedSeq) {
            ownedSeq = seq;
            JsonObject answer = Friends.owned();
            ownedAt = System.currentTimeMillis();
            if (answer != null) {
                try {
                    applyOwned(answer);
                } catch (RuntimeException ignored) {
                    // unlesbar – alter Stand bleibt
                }
            }
        } else if (seq == 0 && System.currentTimeMillis() - ownedAt > 3 * 60 * 1000) {
            refreshOwned();
        }
        if (pushFailed && System.currentTimeMillis() >= pushRetryAt) push();
        if (!pushed && (MyticClient.config().cape != null || MiniMeCore.any() || MyticClient.config().wings != null || MyticClient.config().shield != null || MyticClient.config().aura != null || MyticClient.config().nametag != null
                || (MyticClient.config().acc != null && !MyticClient.config().acc.isEmpty()))) push();
        if (lookupRunning) return;
        long now = System.currentTimeMillis();
        final boolean feed = Friends.cosFeed();
        final long remoteMs = feed ? REMOTE_FEED_MS : REMOTE_MS;
        final long noneMs = feed ? REMOTE_FEED_NONE_MS : REMOTE_NONE_MS;
        // erst fragen, wenn einer fällig ist – dann alle, die in der nächsten Minute fällig würden, gleich mit (weniger Anfragen)
        boolean due = false;
        for (String uuid : nearby) {
            Long at = ASKED.get(uuid);
            if (at == null || now - at > remoteMs) {
                due = true;
                break;
            }
        }
        if (!due) return;
        final List<String> ask = new ArrayList<>();
        for (String uuid : nearby) {
            Long at = ASKED.get(uuid);
            if (at == null || now - at > remoteMs - BATCH_MS) ask.add(uuid);
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
                if (names != null) {
                    JsonObject named = new JsonObject();
                    for (String uuid : ask) if (names.containsKey(uuid)) named.addProperty(uuid, names.get(uuid));
                    if (!named.entrySet().isEmpty()) body.add("names", named);
                }
                JsonObject answer = Friends.post("api/cosmetics/lookup", body);
                if (answer.has("aliases") && answer.get("aliases").isJsonObject()) {
                    if (ALIAS.size() > 4096) ALIAS.clear();
                    for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("aliases").entrySet()) ALIAS.put(e.getValue().getAsString(), e.getKey());
                }
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
                Map<String, String> tagsFound = new HashMap<>();
                if (answer.has("nametags")) {
                    for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("nametags").entrySet()) tagsFound.put(e.getKey(), e.getValue().getAsString());
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
                Map<String, Map<String, String>> accFound = new HashMap<>();
                if (answer.has("acc")) {
                    for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("acc").entrySet()) {
                        Map<String, String> set = new HashMap<>();
                        for (Map.Entry<String, JsonElement> p : e.getValue().getAsJsonObject().entrySet()) set.put(p.getKey(), p.getValue().getAsString());
                        accFound.put(e.getKey(), set);
                    }
                }
                // Spieler ohne Cosmetics (meist ohne Mytic) seltener erneut fragen
                long later = System.currentTimeMillis() + noneMs - remoteMs;
                for (String uuid : ask) {
                    if (!found.containsKey(uuid) && !minis.containsKey(uuid) && !tagsFound.containsKey(uuid) && !aurasFound.containsKey(uuid)
                            && !shieldsFound.containsKey(uuid) && !wingsFound.containsKey(uuid) && !accFound.containsKey(uuid)) ASKED.put(uuid, later);
                    REMOTE.put(uuid, found.containsKey(uuid) ? found.get(uuid) : "");
                    REMOTE_MINI.put(uuid, minis.containsKey(uuid) ? minis.get(uuid) : new HashMap<String, String>());
                    REMOTE_NAMETAG.put(uuid, tagsFound.containsKey(uuid) ? tagsFound.get(uuid) : "");
                    REMOTE_AURA.put(uuid, aurasFound.containsKey(uuid) ? aurasFound.get(uuid) : "");
                    REMOTE_SHIELD.put(uuid, shieldsFound.containsKey(uuid) ? shieldsFound.get(uuid) : "");
                    REMOTE_WINGS.put(uuid, wingsFound.containsKey(uuid) ? wingsFound.get(uuid) : "");
                    REMOTE_ACC.put(uuid, accFound.containsKey(uuid) ? accFound.get(uuid) : new HashMap<String, String>());
                    checkKnown("cape", found.get(uuid), byId(found.get(uuid)) != null);
                    checkKnown("wings", wingsFound.get(uuid), hasWings(wingsFound.get(uuid)));
                    checkKnown("aura", aurasFound.get(uuid), aura(aurasFound.get(uuid)) != null);
                    checkKnown("shield", shieldsFound.get(uuid), hasShield(shieldsFound.get(uuid)));
                    if (accFound.containsKey(uuid)) for (String id : accFound.get(uuid).values()) checkKnown("acc", id, acc(id) != null);
                }
                lookupFails = 0;
            } catch (Exception e) {
                // erneut versuchen: nach 30 s, bei weiteren Fehlern in Folge (z. B. 429) seltener, höchstens alle 5 Minuten
                lookupFails++;
                long retry = System.currentTimeMillis() - remoteMs + backoff(lookupFails, 5 * 60 * 1000);
                for (String uuid : ask) ASKED.put(uuid, retry);
            } finally {
                lookupRunning = false;
            }
        });
    }
}
