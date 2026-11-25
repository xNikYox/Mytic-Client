package de.myticlegacy.client.minimap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.myticlegacy.client.MyticClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wegpunkte je Server bzw. Einzelspieler-Welt (alle Dimensionen in einer Datei): Name, Farbe, Symbol, Koordinaten.
 * Teilen über einen kurzen Code (MWP1:…), den man kopiert und z. B. per Chat an Freunde schickt – die
 * Freundesliste hat (noch) keine Nachrichten.
 */
public final class Waypoints {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Pattern CODE = Pattern.compile("MWP1:([A-Za-z0-9_-]{8,400})");
    public static final int[] COLORS = {0xFFB56CFF, 0xFFFF5C72, 0xFF4ADE80, 0xFF60A5FA, 0xFFFACC15, 0xFFFF9F43, 0xFF2FD0C0, 0xFFFF6FB5, 0xFFFFFFFF};

    public static final class Waypoint {
        public String name = "Wegpunkt";
        public int x, y, z;
        public String dim = "minecraft:overworld";
        public int color = 0xFFB56CFF;
        public String icon = "diamond";
        public boolean visible = true;
        /** automatisch beim Tod gesetzt */
        public boolean death;
        public long created = System.currentTimeMillis();

        public Waypoint copy() {
            Waypoint w = new Waypoint();
            w.name = name;
            w.x = x;
            w.y = y;
            w.z = z;
            w.dim = dim;
            w.color = color;
            w.icon = icon;
            w.visible = visible;
            w.death = death;
            w.created = created;
            return w;
        }
    }

    private static String server;
    private static List<Waypoint> list = new ArrayList<>();

    private Waypoints() {
    }

    private static Path file(String server) {
        return MapWorld.root().resolve(server).resolve("waypoints.json");
    }

    /** Für diesen Server bzw. diese Welt laden (bei Wechsel) */
    public static void use(String serverKey) {
        if (serverKey == null || serverKey.equals(server)) return;
        server = serverKey;
        list = new ArrayList<>();
        try {
            Path f = file(serverKey);
            if (Files.isRegularFile(f)) {
                List<Waypoint> loaded = GSON.fromJson(Files.readString(f), new TypeToken<List<Waypoint>>() {
                }.getType());
                if (loaded != null) for (Waypoint w : loaded) if (w != null) list.add(sane(w));
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("Minimap: Wegpunkte nicht lesbar: {}", e.toString());
        }
    }

    private static Waypoint sane(Waypoint w) {
        if (w.name == null || w.name.isBlank()) w.name = "Wegpunkt";
        if (w.name.length() > 32) w.name = w.name.substring(0, 32);
        if (w.dim == null) w.dim = "minecraft:overworld";
        if (w.icon == null) w.icon = "diamond";
        w.color |= 0xFF000000;
        return w;
    }

    public static String server() {
        return server;
    }

    public static List<Waypoint> all() {
        return list;
    }

    public static void add(Waypoint w) {
        list.add(sane(w));
        save();
    }

    public static void remove(Waypoint w) {
        list.remove(w);
        save();
    }

    public static void save() {
        if (server == null) return;
        String json = GSON.toJson(list);
        Path f = file(server);
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, json);
        } catch (Exception e) {
            MyticClient.LOG.warn("Minimap: Wegpunkte nicht gespeichert: {}", e.toString());
        }
    }

    /** Nächste Farbe für einen neuen Wegpunkt */
    public static int nextColor() {
        return COLORS[list.size() % COLORS.length];
    }

    /** Neuer Todespunkt; ältere automatische Todespunkte über keep hinaus fallen weg */
    public static void death(int x, int y, int z, String dim, int keep) {
        Waypoint w = new Waypoint();
        w.name = "Tod " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        w.x = x;
        w.y = y;
        w.z = z;
        w.dim = dim;
        w.color = 0xFFFF5C72;
        w.icon = "skull";
        w.death = true;
        list.add(w);
        List<Waypoint> deaths = new ArrayList<>();
        for (Waypoint p : list) if (p.death) deaths.add(p);
        for (int i = 0; i < deaths.size() - Math.max(1, keep); i++) list.remove(deaths.get(i));
        save();
    }

    // ------------------------------------------------------------------------------------------ Teilen

    /** Code zum Teilen: MWP1:<Base64 von Name, x, y, z, Dimension, Farbe, Symbol> */
    public static String code(Waypoint w) {
        String raw = String.join("\t", w.name, String.valueOf(w.x), String.valueOf(w.y), String.valueOf(w.z), w.dim,
                Integer.toHexString(w.color & 0xFFFFFF), w.icon);
        return "MWP1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Wegpunkt aus einem Text mit Code (irgendwo darin) oder null */
    public static Waypoint parse(String text) {
        if (text == null) return null;
        Matcher m = CODE.matcher(text);
        if (!m.find()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(m.group(1)), StandardCharsets.UTF_8);
            String[] p = raw.split("\t", -1);
            if (p.length < 5) return null;
            Waypoint w = new Waypoint();
            w.name = p[0];
            w.x = Integer.parseInt(p[1]);
            w.y = Integer.parseInt(p[2]);
            w.z = Integer.parseInt(p[3]);
            w.dim = p[4].matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? p[4] : "minecraft:overworld";
            if (p.length > 5) w.color = 0xFF000000 | Integer.parseInt(p[5], 16);
            if (p.length > 6) w.icon = p[6];
            boolean known = false;
            for (String k : MapIcons.KEYS) known |= k.equals(w.icon);
            if (!known) w.icon = "diamond";
            if (Math.abs(w.x) > 30_000_000 || Math.abs(w.z) > 30_000_000 || Math.abs(w.y) > 4096) return null;
            return sane(w);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
