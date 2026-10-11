package de.myticlegacy.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.myticlegacy.client.MyticClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MiniMe unabhängig von der Minecraft-Version: Minecraft-Name → Profil bei Mojang (mit signierten Texturen),
 * Skin-Bild, Kopf für die Vorschau und Slim-Erkennung. Lädt im Hintergrund und merkt sich das Ergebnis.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class MiniMeCore {
    public static final class Profile {
        public final String name;
        public final String uuid;
        /** "textures"-Eintrag von Mojang (Wert und Signatur) – für den Skin-Manager der neuen Versionen */
        public final String textures;
        public final String signature;
        public final boolean slim;
        /** Skin-Bild (PNG) – für 1.8.9 */
        public final byte[] png;
        /** Gesicht mit Hut-Ebene, 64 Pixel ARGB – für die Vorschau im Menü */
        public final int[] head;

        Profile(String name, String uuid, String textures, String signature, boolean slim, byte[] png, int[] head) {
            this.name = name;
            this.uuid = uuid;
            this.textures = textures;
            this.signature = signature;
            this.slim = slim;
            this.png = png;
            this.head = head;
        }
    }

    public static final String NAME = "^[A-Za-z0-9_]{3,16}$";

    private static final Map<String, Profile> PROFILES = new ConcurrentHashMap<>();
    /** Name → Zeitpunkt des letzten Versuchs (für Wiederholung nach Fehlern) */
    private static final Map<String, Long> TRIED = new ConcurrentHashMap<>();
    /** Namen, die es bei Mojang nicht gibt */
    private static final Map<String, Boolean> MISSING = new ConcurrentHashMap<>();

    private MiniMeCore() {
    }

    // ------------------------------------------------------------------------------------------ Auswahl

    /** Eigener MiniMe (Minecraft-Name) oder null. */
    public static String selected() {
        String name = MyticClient.config().miniMe;
        return name == null || !name.matches(NAME) ? null : name;
    }

    public static void select(String name) {
        MyticClient.config().miniMe = name == null || !name.matches(NAME) ? null : name;
        MyticClient.config().save();
        CosmeticsCore.push();
    }

    /** MiniMe-Name eines Spielers: eigener aus der Auswahl, andere vom Friends-Server. */
    public static String nameOf(String uuid, boolean self) {
        return self ? selected() : CosmeticsCore.remoteMiniMe(uuid);
    }

    // ------------------------------------------------------------------------------------------ Laden

    /** Profil zu einem Namen, lädt beim ersten Aufruf im Hintergrund (bis dahin null). */
    public static Profile profile(String name) {
        if (name == null) return null;
        final String key = name.toLowerCase(Locale.ROOT);
        Profile p = PROFILES.get(key);
        if (p != null || MISSING.containsKey(key)) return p;
        Long at = TRIED.get(key);
        if (at == null || System.currentTimeMillis() - at > 60000) {
            TRIED.put(key, System.currentTimeMillis());
            Thread t = new Thread(() -> load(key), "Mytic MiniMe " + key);
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    /** Gibt es diesen Namen bei Mojang nicht? */
    public static boolean missing(String name) {
        return name != null && MISSING.containsKey(name.toLowerCase(Locale.ROOT));
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "MyticClient");
        int status = c.getResponseCode();
        if (status == 204 || status == 404 || status == 400) return null;
        if (status != 200) throw new Exception("HTTP " + status);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    @SuppressWarnings("deprecation")
    private static JsonObject json(byte[] data) {
        return new JsonParser().parse(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void load(String key) {
        try {
            byte[] idData = get("https://api.mojang.com/users/profiles/minecraft/" + key);
            if (idData == null) {
                MISSING.put(key, Boolean.TRUE);
                return;
            }
            String uuid = json(idData).get("id").getAsString();
            byte[] profileData = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            if (profileData == null) return;
            JsonObject profile = json(profileData);
            String textures = null, signature = null;
            JsonArray props = profile.getAsJsonArray("properties");
            for (JsonElement e : props) {
                JsonObject p = e.getAsJsonObject();
                if ("textures".equals(p.get("name").getAsString())) {
                    textures = p.get("value").getAsString();
                    signature = p.has("signature") ? p.get("signature").getAsString() : null;
                }
            }
            boolean slim = false;
            byte[] png = null;
            if (textures != null) {
                JsonObject tex = json(java.util.Base64.getDecoder().decode(textures)).getAsJsonObject("textures");
                if (tex != null && tex.has("SKIN")) {
                    JsonObject skin = tex.getAsJsonObject("SKIN");
                    slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
                    png = get(skin.get("url").getAsString().replace("http://", "https://"));
                }
            }
            int[] head = png != null ? head(png) : null;
            PROFILES.put(key, new Profile(profile.get("name").getAsString(), uuid, textures, signature, slim, png, head));
        } catch (Exception e) {
            MyticClient.LOG.warn("MiniMe " + key + " nicht geladen: " + e);
        }
    }

    /** Gesicht (8,8) mit Hut-Ebene (40,8) aus dem Skin-Bild. */
    private static int[] head(byte[] png) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        if (img == null || img.getWidth() < 64) return null;
        int k = img.getWidth() / 64;
        boolean hat = img.getHeight() >= img.getWidth();
        int[] out = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int c = img.getRGB((8 + x) * k, (8 + y) * k) | 0xFF000000;
                int h = img.getRGB((40 + x) * k, (8 + y) * k);
                int a = (h >>> 24) & 255;
                if (a > 0 && (hat || a == 255)) {
                    float t = a / 255f;
                    int r = Math.round(((h >> 16) & 255) * t + ((c >> 16) & 255) * (1 - t));
                    int g = Math.round(((h >> 8) & 255) * t + ((c >> 8) & 255) * (1 - t));
                    int b = Math.round((h & 255) * t + (c & 255) * (1 - t));
                    c = 0xFF000000 | r << 16 | g << 8 | b;
                }
                out[y * 8 + x] = c;
            }
        }
        return out;
    }
}
