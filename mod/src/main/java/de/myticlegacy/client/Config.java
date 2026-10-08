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
