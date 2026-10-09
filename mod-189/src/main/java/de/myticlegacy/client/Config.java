package de.myticlegacy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Einstellungen des Mytic Client – gleiches Format wie in der Fabric-Mod. Der Launcher übergibt mit -Dmytic.config den
 * gemeinsamen Pfad, damit Module und HUD-Positionen in allen Versionen gleich sind.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, Boolean> enabled = new LinkedHashMap<String, Boolean>();
    public Map<String, int[]> positions = new LinkedHashMap<String, int[]>();
    /** Einstellungen der Module, Schlüssel "modul.einstellung". Zahlen werden von Gson als Double gelesen. */
    public Map<String, Object> settings = new LinkedHashMap<String, Object>();
    /** Helligkeit vor dem Einschalten von Fullbright, wird beim Ausschalten wiederhergestellt. */
    public double savedGamma = 0.5;

    static File configDir;

    private static File file() {
        String shared = System.getProperty("mytic.config");
        if (shared != null && !shared.isEmpty()) return new File(shared);
        return new File(configDir != null ? configDir : new File("config"), "myticclient.json");
    }

    public static Config load() {
        try {
            File file = file();
            if (file.exists()) {
                Config config = GSON.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Config.class);
                if (config != null) {
                    if (config.enabled == null) config.enabled = new LinkedHashMap<String, Boolean>();
                    if (config.positions == null) config.positions = new LinkedHashMap<String, int[]>();
                    if (config.settings == null) config.settings = new LinkedHashMap<String, Object>();
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
            File file = file();
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            MyticClient.LOG.warn("Konfiguration konnte nicht gespeichert werden", e);
        }
    }
}
