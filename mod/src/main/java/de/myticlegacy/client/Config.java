package de.myticlegacy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    /** Gewählte Aura (Cosmetics), null = keine. */
    public String aura;
    /** Gewählter Schild (Cosmetics), null = keiner. */
    public String shield;
    /** Gewählte Flügel (Cosmetics), null = keine. */
    public String wings;
    /** Grafik-Optionen vor dem Einschalten von FPS-Boost (Name → Wert als Text), werden beim Ausschalten zurückgesetzt. */
    public Map<String, String> savedOptions = new LinkedHashMap<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("myticclient.json");
    }

    public static Config load() {
        try {
            if (Files.exists(file())) {
                Config config = GSON.fromJson(Files.readString(file()), Config.class);
                if (config != null) {
                    if (config.enabled == null) config.enabled = new LinkedHashMap<>();
                    if (config.positions == null) config.positions = new LinkedHashMap<>();
                    if (config.settings == null) config.settings = new LinkedHashMap<>();
                    if (config.savedOptions == null) config.savedOptions = new LinkedHashMap<>();
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
