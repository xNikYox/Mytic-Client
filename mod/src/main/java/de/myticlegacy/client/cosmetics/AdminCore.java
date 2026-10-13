package de.myticlegacy.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.myticlegacy.client.friends.Friends;

import java.util.Set;
import java.util.TreeSet;

/**
 * Admin: Cosmetics einzelner Spieler freischalten (für Konten mit Admin-Recht). Lädt und speichert im Hintergrund über
 * den Friends-Server; das Menü liest nur den aktuellen Stand. (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class AdminCore {
    public static volatile String uuid;
    public static volatile String name;
    public static volatile String message;
    public static volatile boolean error;
    public static volatile boolean busy;
    /** Freischaltungen des geladenen Spielers (bearbeitet, noch nicht gespeichert) */
    public static final Set<String> ITEMS = new TreeSet<String>();
    public static volatile boolean dirty;

    private AdminCore() {
    }

    public static void load(final String player) {
        if (busy || player == null || player.trim().isEmpty()) return;
        if (!Friends.available()) {
            message = "Nur möglich, wenn du über den Mytic Launcher startest";
            error = true;
            return;
        }
        busy = true;
        message = "Lade …";
        error = false;
        Friends.background(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("name", player.trim());
                apply(Friends.post("api/admin/user", body));
                message = null;
            } catch (Exception e) {
                message = e.getMessage();
                error = true;
            } finally {
                busy = false;
            }
        });
    }

    public static void save() {
        if (busy || uuid == null) return;
        busy = true;
        message = "Speichere …";
        error = false;
        final JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        JsonArray array = new JsonArray();
        synchronized (ITEMS) {
            for (String item : ITEMS) array.add(new JsonPrimitive(item));
        }
        body.add("items", array);
        Friends.background(() -> {
            try {
                apply(Friends.post("api/admin/grants", body));
                message = "Gespeichert";
                Friends.Friend me = Friends.me();
                if (me != null && uuid.equals(me.uuid)) CosmeticsCore.refreshOwned();
            } catch (Exception e) {
                message = e.getMessage();
                error = true;
            } finally {
                busy = false;
            }
        });
    }

    private static void apply(JsonObject user) {
        uuid = user.get("uuid").getAsString();
        name = user.has("name") && !user.get("name").isJsonNull() ? user.get("name").getAsString() : uuid;
        synchronized (ITEMS) {
            ITEMS.clear();
            for (JsonElement e : user.getAsJsonArray("items")) ITEMS.add(e.getAsString());
        }
        dirty = false;
    }

    public static boolean has(String item) {
        synchronized (ITEMS) {
            return ITEMS.contains(item);
        }
    }

    public static void toggle(String item) {
        if (uuid == null) return;
        synchronized (ITEMS) {
            if (!ITEMS.remove(item)) ITEMS.add(item);
        }
        dirty = true;
        message = null;
    }
}
