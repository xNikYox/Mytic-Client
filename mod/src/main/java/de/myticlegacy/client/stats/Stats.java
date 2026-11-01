package de.myticlegacy.client.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.friends.Friends;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Statistiken (gespeichert auf dem Friends-Server, api/stats): Spielzeit wird nur in einer Welt gezählt und einmal pro
 * Minute gemeldet – mit Server, Minecraft-Version und Launcher-Profil (-Dmytic.profile). Starts meldet der Launcher.
 */
public final class Stats {
    private Stats() {
    }

    /** eine Zeile: Name, Sekunden, Starts (bei Servern 0) */
    public record Entry(String name, long seconds, long launches) {
    }

    /** ein Tag im Verlauf */
    public record Day(String day, long seconds, long launches) {
    }

    /** Stand vom Server */
    public static final class Data {
        public long launches, seconds, longest, last, today, week;
        public final List<Day> days = new ArrayList<>();
        public final List<Entry> servers = new ArrayList<>(), versions = new ArrayList<>(), profiles = new ArrayList<>();
    }

    private static final String SESSION = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    private static long sessionSeconds;
    /** gesammelte Spielzeit (ms), noch nicht gemeldet */
    private static long pendingMs;
    private static long lastTick;
    private static long lastSend = System.currentTimeMillis();
    private static volatile Data data;
    private static volatile String error;
    private static volatile boolean loading;
    private static long loadedAt;

    public static Data data() {
        return data;
    }

    public static String error() {
        return error;
    }

    public static boolean loading() {
        return loading;
    }

    static int tz() {
        return java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000;
    }

    /** jeden Client-Tick: Zeit in einer Welt sammeln, einmal pro Minute melden */
    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        long delta = lastTick == 0 ? 0 : Math.min(1000, now - lastTick); // Hänger und Schlafmodus nicht mitzählen
        lastTick = now;
        if (!Friends.available()) return;
        if (mc.level != null) pendingMs += delta;
        if (now - lastSend < 60_000 || pendingMs < 1000) return;
        lastSend = now;
        long seconds = Math.min(120, pendingMs / 1000);
        pendingMs -= seconds * 1000;
        sessionSeconds += seconds;
        JsonObject body = new JsonObject();
        body.addProperty("seconds", seconds);
        String server = Friends.currentServer;
        body.addProperty("server", server == null ? "Einzelspieler" : server);
        body.addProperty("version", Compat.versionName());
        body.addProperty("profile", System.getProperty("mytic.profile", ""));
        body.addProperty("session", SESSION);
        body.addProperty("sessionSeconds", sessionSeconds);
        body.addProperty("tz", tz());
        Friends.background(() -> {
            try {
                Friends.post("api/stats/play", body);
            } catch (Exception ignored) {
                // geht beim nächsten Mal mit (Server zählt nur bis 120 s pro Meldung)
            }
        });
    }

    /** Statistik laden (höchstens alle 20 s, außer force) */
    public static void refresh(boolean force) {
        if (!Friends.available() || loading || (!force && System.currentTimeMillis() - loadedAt < 20_000 && data != null)) return;
        loading = true;
        Friends.background(() -> {
            try {
                data = parse(Friends.get("api/stats?tz=" + tz()));
                error = null;
                loadedAt = System.currentTimeMillis();
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                loading = false;
            }
        });
    }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        JsonObject t = obj(o, "totals");
        d.launches = num(t, "launches");
        d.seconds = num(t, "seconds");
        d.longest = num(t, "longest");
        d.last = num(t, "last");
        d.today = num(t, "today");
        d.week = num(t, "week");
        for (JsonObject e : arr(o, "days")) d.days.add(new Day(str(e, "day"), num(e, "seconds"), num(e, "launches")));
        for (JsonObject e : arr(o, "servers")) d.servers.add(new Entry(str(e, "name"), num(e, "seconds"), 0));
        for (JsonObject e : arr(o, "versions")) d.versions.add(new Entry(str(e, "name"), num(e, "seconds"), num(e, "launches")));
        for (JsonObject e : arr(o, "profiles")) d.profiles.add(new Entry(str(e, "name"), num(e, "seconds"), num(e, "launches")));
        return d;
    }

    /** „12 Std 5 Min“, „45 Min“, „unter 1 Min“ */
    public static String duration(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60;
        if (h > 0) return h + " Std" + (m > 0 ? " " + m + " Min" : "");
        return m > 0 ? m + " Min" : "unter 1 Min";
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static List<JsonObject> arr(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) return Collections.emptyList();
        List<JsonObject> out = new ArrayList<>();
        JsonArray a = o.getAsJsonArray(key);
        for (JsonElement e : a) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        return out;
    }

    private static long num(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0;
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
