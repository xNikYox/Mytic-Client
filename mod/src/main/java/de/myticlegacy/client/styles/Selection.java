package de.myticlegacy.client.styles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.myticlegacy.client.MyticClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Auswahl pro Kategorie (config/mytic-styles.json); daraus baut {@link MergedPack} das Paket „Mytic Styles“. */
public final class Selection {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Category, String> chosen = new EnumMap<>(Category.class);
    private static boolean loaded;

    private Selection() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("mytic-styles.json");
    }

    /** Gewählte Kennung einer Kategorie oder null (= Standard) */
    public static synchronized String get(Category c) {
        load();
        return chosen.get(c);
    }

    public static synchronized boolean isEmpty() {
        load();
        return chosen.isEmpty();
    }

    /** Auswahl mit den Paketen, die es (noch) gibt */
    static synchronized Map<Category, StylePack> snapshot() {
        load();
        Map<Category, StylePack> out = new EnumMap<>(Category.class);
        for (Map.Entry<Category, String> e : chosen.entrySet()) {
            StylePack p = StylePacks.byId(e.getValue());
            if (p != null) out.put(e.getKey(), p);
        }
        return out;
    }

    /** Neue Auswahl speichern, Paket neu bauen und einmal neu laden – nur wenn sich etwas geändert hat */
    public static synchronized boolean apply(Map<Category, String> next) {
        load();
        Map<Category, String> clean = new EnumMap<>(Category.class);
        for (Map.Entry<Category, String> e : next.entrySet()) if (e.getValue() != null) clean.put(e.getKey(), e.getValue());
        if (clean.equals(chosen)) return false;
        chosen.clear();
        chosen.putAll(clean);
        save();
        MergedPack.reload();
        return true;
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(file())) {
                JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("auswahl");
                if (o != null) for (String k : o.keySet()) {
                    Category c = Category.byKey(k);
                    if (c != null) chosen.put(c, o.get(k).getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            MyticClient.LOG.warn("Mytic Styles: Auswahl nicht lesbar – starte mit Standard", e);
        }
    }

    private static void save() {
        JsonObject root = new JsonObject(), o = new JsonObject();
        for (Map.Entry<Category, String> e : chosen.entrySet()) o.addProperty(e.getKey().key(), e.getValue());
        root.add("auswahl", o);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MyticClient.LOG.error("Mytic Styles: Auswahl konnte nicht gespeichert werden", e);
        }
    }
}
