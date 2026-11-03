package de.myticlegacy.client.styles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Das eine Paket „Mytic Styles“: zuerst das Komplett-Paket, darüber die gewählten Teile. Liegt nur im Arbeitsspeicher
 * (siehe MemoryPack) und ist immer aktiv, ganz oben – bei ausgeschaltetem Modul leer.
 */
public final class MergedPack {
    public static final String PACK_ID = "myticclient/styles";
    private static volatile Map<String, byte[]> content;
    private static byte[] meta;

    private MergedPack() {
    }

    /** Inhalt für Minecraft ("assets/<ns>/<pfad>" → Daten) */
    public static Map<String, byte[]> content() {
        Map<String, byte[]> c = content;
        if (c == null) {
            if (!StylesModule.active() || Selection.isEmpty()) c = Map.of();
            else {
                StylePacks.ensureScanned();
                c = build(Selection.snapshot());
            }
            content = c;
        }
        return c;
    }

    /** Beim nächsten Laden neu bauen und Ressourcen neu laden */
    public static void reload() {
        content = null;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(mc::reloadResourcePacks);
    }

    static Map<String, byte[]> build(Map<Category, StylePack> sel) {
        Map<String, byte[]> out = new HashMap<>();
        StylePack whole = sel.get(Category.WHOLE);
        if (whole != null) copy(whole, out);
        for (Map.Entry<Category, StylePack> e : sel.entrySet()) if (e.getKey() != Category.WHOLE) copy(e.getValue(), out); // Teile gewinnen
        addMissingAnimations(out);
        MyticClient.LOG.info("Mytic Styles: Paket gebaut – {} Dateien aus {} Einträgen", out.size(), sel.size());
        return Map.copyOf(out);
    }

    private static void copy(StylePack p, Map<String, byte[]> out) {
        Set<String> only = p.files();
        p.source().readAll(name -> {
            if (!name.startsWith("assets/")) return false;
            if (only == null) return true;
            String rest = name.substring("assets/".length());
            int slash = rest.indexOf('/');
            return slash > 0 && only.contains(rest.substring(0, slash) + ":" + rest.substring(slash + 1));
        }, out::put);
    }

    /** Animierte Texturen (senkrechter Streifen) brauchen ihre .mcmeta – fehlt sie, die aus Minecraft nehmen */
    private static void addMissingAnimations(Map<String, byte[]> out) {
        for (String key : List.copyOf(out.keySet())) {
            if (!key.endsWith(".png") || out.containsKey(key + ".mcmeta")) continue;
            byte[] png = out.get(key);
            if (png == null || png.length < 24) continue;
            int w = ((png[16] & 255) << 24) | ((png[17] & 255) << 16) | ((png[18] & 255) << 8) | (png[19] & 255);
            int h = ((png[20] & 255) << 24) | ((png[21] & 255) << 16) | ((png[22] & 255) << 8) | (png[23] & 255);
            if (w <= 0 || h <= w || h % w != 0) continue;
            try (InputStream in = Minecraft.class.getResourceAsStream("/" + key + ".mcmeta")) {
                if (in != null) out.put(key + ".mcmeta", in.readAllBytes());
            } catch (IOException ignored) {
                // ohne Animation weiter
            }
        }
    }

    /**
     * pack.mcmeta passend zur laufenden Minecraft-Version (Format aus version.json): bis 1.21.8 pack_format +
     * supported_formats, ab 1.21.9 zusätzlich min_format/max_format – sonst gilt das Paket als inkompatibel.
     */
    static synchronized byte[] meta() {
        if (meta != null) return meta;
        int format = 34;
        try (InputStream in = Minecraft.class.getResourceAsStream("/version.json")) {
            if (in != null) {
                JsonObject v = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack_version");
                if (v.has("resource_major")) format = v.get("resource_major").getAsInt();
                else if (v.has("resource")) format = v.get("resource").getAsInt();
            }
        } catch (IOException | RuntimeException e) {
            MyticClient.LOG.warn("Mytic Styles: Paketformat unbekannt, nehme {}", format);
        }
        String json = format >= 65
                ? "{\"pack\":{\"description\":\"Mytic Styles\",\"pack_format\":" + format + ",\"min_format\":" + format + ",\"max_format\":9999}}"
                : "{\"pack\":{\"description\":\"Mytic Styles\",\"pack_format\":" + format + ",\"supported_formats\":[" + format + "," + format + "]}}";
        return meta = json.getBytes(StandardCharsets.UTF_8);
    }
}
