package de.myticlegacy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Einstellungen des Mytic Client, gespeichert in config/myticclient.json. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, Boolean> enabled = new LinkedHashMap<>();
    public Map<String, int[]> positions = new LinkedHashMap<>();
    /** Einstellungen der Module, Schlüssel "modul.einstellung". Zahlen werden von Gson als Double gelesen. */
    public Map<String, Object> settings = new LinkedHashMap<>();
    /** Helligkeit vor dem Einschalten von Fullbright, wird beim Ausschalten wiederhergestellt. */
    public double savedGamma = 0.5;
    /** Gewählter Umhang (Cosmetics), null = keiner. */
    public String cape;
    /** MiniMes je Stelle (shoulder, head, arm, leg) → Minecraft-Name, dessen Skin die kleine Figur trägt. */
    public Map<String, String> miniMes = new LinkedHashMap<String, String>();
    /** Bis 2.18: ein MiniMe und seine Stelle – wird beim ersten Start nach miniMes übernommen. */
    public String miniMe;
    public String miniMePos;
    /** Nametag: eigene Zeile über dem Namen mit &-Farbcodes, null = keiner. */
    public String nametag;
    /** Eigenen Namen samt Nametag in der Third-Person-Ansicht (F5) zeigen */
    public boolean nametagSelf = true;
    /** Zuletzt bekannte Freischaltungen (falls der Friends-Server gerade nicht erreichbar ist) */
    public java.util.List<String> ownedCache = new java.util.ArrayList<>();
    public boolean adminCache;
    /** Gewählte Aura (Cosmetics), null = keine. */
    public String aura;
    /** Gewählter Schild (Cosmetics), null = keiner. */
    public String shield;
    /** Gewählte Flügel (Cosmetics), null = keine. */
    public String wings;
    /** Grafik-Optionen vor dem Einschalten von FPS-Boost (Name → Wert als Text), werden beim Ausschalten zurückgesetzt. */
    public Map<String, String> savedOptions = new LinkedHashMap<>();
    /** HUD-Profile (Name → Positionen, an/aus und Einstellungen der HUD-Module) und das aktive Profil */
    public Map<String, HudProfile> hudProfiles = new LinkedHashMap<>();
    public String hudProfile;

    /** Server-Profile: Server-Adresse → HUD-Profil und an/aus der übrigen Mods, beim Betreten automatisch gesetzt */
    public Map<String, ServerProfile> serverProfiles = new LinkedHashMap<>();

    public static final class ServerProfile {
        public String hudProfile;
        public Map<String, Boolean> modules = new LinkedHashMap<>();
    }

    public static final class HudProfile {
        public Map<String, int[]> positions = new LinkedHashMap<>();
        public Map<String, Boolean> enabled = new LinkedHashMap<>();
        public Map<String, Object> settings = new LinkedHashMap<>();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("myticclient.json");
    }

    /** Sinnvoller Bereich für HUD-Positionen (GUI-Pixel) */
    public static final int POS_MIN = -4096, POS_MAX = 16384;

    /** Eine HUD-Position aus JSON: genau zwei ganze Zahlen im sinnvollen Bereich, sonst null */
    public static int[] position(JsonElement e) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 2) return null;
        int[] p = new int[2];
        for (int i = 0; i < 2; i++) {
            JsonElement v = e.getAsJsonArray().get(i);
            if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) return null;
            double d = v.getAsDouble();
            if (d != Math.rint(d) || d < POS_MIN || d > POS_MAX) return null;
            p[i] = (int) d;
        }
        return p;
    }

    /** Positionen (Modul → [x, y]) aus JSON; kaputte Einträge fallen weg */
    public static Map<String, int[]> positions(JsonElement e) {
        Map<String, int[]> out = new LinkedHashMap<>();
        if (e == null || !e.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
            int[] p = position(en.getValue());
            if (p != null) out.put(en.getKey(), p);
        }
        return out;
    }

    /** Kaputte Positionen vor dem Einlesen entfernen, damit eine beschädigte Konfiguration sich selbst repariert */
    private static void cleanPositions(JsonObject o) {
        if (o.has("positions")) o.add("positions", GSON.toJsonTree(positions(o.get("positions"))));
    }

    public static Config load() {
        try {
            if (Files.exists(file())) {
                JsonElement tree = JsonParser.parseString(Files.readString(file()));
                if (tree.isJsonObject()) {
                    cleanPositions(tree.getAsJsonObject());
                    JsonElement hud = tree.getAsJsonObject().get("hudProfiles");
                    if (hud != null && hud.isJsonObject())
                        for (Map.Entry<String, JsonElement> e : hud.getAsJsonObject().entrySet()) if (e.getValue().isJsonObject()) cleanPositions(e.getValue().getAsJsonObject());
                }
                Config config = GSON.fromJson(tree, Config.class);
                if (config != null) {
                    if (config.enabled == null) config.enabled = new LinkedHashMap<>();
                    if (config.positions == null) config.positions = new LinkedHashMap<>();
                    if (config.settings == null) config.settings = new LinkedHashMap<>();
                    if (config.savedOptions == null) config.savedOptions = new LinkedHashMap<>();
                    if (config.hudProfiles == null) config.hudProfiles = new LinkedHashMap<>();
                    if (config.serverProfiles == null) config.serverProfiles = new LinkedHashMap<>();
                    config.hudProfiles.values().removeIf(p -> p == null);
                    for (HudProfile p : config.hudProfiles.values()) {
                        if (p.positions == null) p.positions = new LinkedHashMap<>();
                        if (p.enabled == null) p.enabled = new LinkedHashMap<>();
                        if (p.settings == null) p.settings = new LinkedHashMap<>();
                    }
                    config.serverProfiles.values().removeIf(p -> p == null);
                    for (ServerProfile p : config.serverProfiles.values()) if (p.modules == null) p.modules = new LinkedHashMap<>();
                    return config;
                }
            }
        } catch (IOException | RuntimeException e) {
            MyticClient.LOG.warn("Konfiguration konnte nicht gelesen werden, nutze Standardwerte", e);
        }
        return new Config();
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(this));
        } catch (IOException e) {
            MyticClient.LOG.warn("Konfiguration konnte nicht gespeichert werden", e);
        }
    }
}
