package de.myticlegacy.client.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Freundesliste im Spiel. Der Mytic Launcher meldet das Konto beim Friends-Server an und übergibt Adresse und
 * Sitzung als -Dmytic.friends.url / -Dmytic.friends.token. Alle Anfragen laufen in einem eigenen Thread; das Spiel
 * liest nur die zuletzt geladene Liste. (Gleicher Code für alle Minecraft-Versionen, daher Java 8.)
 */
public final class Friends {
    public static final class Friend {
        public final String uuid;
        public final String name;
        /** 64 Pixel ARGB (Gesicht mit Hut-Ebene) oder null. */
        public final int[] head;
        public final boolean online;
        public final String place;
        public final String server;
        public final String version;
        public final long lastSeen;

        Friend(JsonObject o) {
            uuid = str(o, "uuid");
            name = str(o, "name");
            head = head(str(o, "head"));
            online = o.has("online") && o.get("online").getAsBoolean();
            place = str(o, "place");
            server = str(o, "server");
            version = str(o, "version");
            long seen = 0;
            if (o.has("lastSeen") && !o.get("lastSeen").isJsonNull()) seen = o.get("lastSeen").getAsLong();
            if (o.has("at") && !o.get("at").isJsonNull()) seen = o.get("at").getAsLong();
            lastSeen = seen;
        }

        public boolean inGame() {
            return online && "game".equals(place);
        }

        /** Statuszeile wie im Launcher. */
        public String status() {
            if (!online) return ago(lastSeen);
            if (inGame()) {
                String where = server == null ? "im Spiel" : server.equals("Einzelspieler") ? "Einzelspieler" : "auf " + server;
                return "Spielt " + where + (version != null ? " · " + version : "");
            }
            return "Im Launcher";
        }
    }

    private static final String BASE = withSlash(System.getProperty("mytic.friends.url", "").trim());
    private static final String TOKEN = System.getProperty("mytic.friends.token", "").trim();

    private static volatile List<Friend> friends = Collections.emptyList();
    private static volatile List<Friend> incoming = Collections.emptyList();
    private static volatile List<Friend> outgoing = Collections.emptyList();
    private static volatile Friend me;
    private static volatile boolean loaded;
    private static volatile String error;
    private static volatile String message;
    private static volatile boolean messageError;
    private static volatile long messageAt;
    private static volatile long serverOffset;

    /** Vom Spiel-Thread gesetzt: aktueller Server und Minecraft-Version. */
    public static volatile String currentServer;
    public static volatile String mcVersion;
    /** Online-Status für Freunde sichtbar / Server anzeigen / Benachrichtigungen (aus den Mod-Einstellungen). */
    public static volatile boolean visible = true;
    public static volatile boolean showServer = true;
    public static volatile boolean notify = true;

    private static final ConcurrentLinkedQueue<String> NOTICES = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService worker;

    private Friends() {
    }

    /** Wurde der Client über den Mytic Launcher mit Freundesliste gestartet? */
    public static boolean available() {
        return !BASE.isEmpty() && !TOKEN.isEmpty();
    }

    public static void start() {
        if (!available() || worker != null) return;
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mytic Friends");
            t.setDaemon(true);
            return t;
        });
        worker.scheduleWithFixedDelay(Friends::heartbeat, 3, 60, TimeUnit.SECONDS);
        // beim Beenden sofort als offline zeigen
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                call("POST", "api/presence/offline", new JsonObject(), 2000);
            } catch (Exception ignored) {
                // egal – nach 75 s gilt man ohnehin als offline
            }
        }, "Mytic Friends offline"));
    }

    public static List<Friend> friends() {
        return friends;
    }

    public static List<Friend> incoming() {
        return incoming;
    }

    public static List<Friend> outgoing() {
        return outgoing;
    }

    public static Friend me() {
        return me;
    }

    public static boolean loaded() {
        return loaded;
    }

    public static String error() {
        return error;
    }

    /** Rückmeldung zur letzten Aktion (8 Sekunden sichtbar), sonst null. */
    public static String message() {
        return System.currentTimeMillis() - messageAt < 8000 ? message : null;
    }

    public static boolean messageError() {
        return messageError;
    }

    public static int onlineCount() {
        int n = 0;
        for (Friend f : friends) if (f.online) n++;
        return n;
    }

    /** Nächste Chat-Benachrichtigung (Freund online, neue Anfrage) oder null. */
    public static String pollNotice() {
        return NOTICES.poll();
    }

    public static void refresh() {
        if (worker != null) worker.execute(Friends::heartbeat);
    }

    public static void request(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        action("api/friends/request", body);
    }

    public static void accept(String uuid) {
        action("api/friends/accept", uuidBody(uuid));
    }

    public static void deny(String uuid) {
        action("api/friends/deny", uuidBody(uuid));
    }

    /** Freund entfernen oder eigene Anfrage zurückziehen. */
    public static void remove(String uuid) {
        action("api/friends/remove", uuidBody(uuid));
    }

    private static JsonObject uuidBody(String uuid) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        return body;
    }

    private static void action(String path, JsonObject body) {
        if (worker == null) return;
        worker.execute(() -> {
            try {
                JsonObject result = call("POST", path, body, 10000);
                apply(result);
                if (result.has("message")) say(result.get("message").getAsString(), false);
            } catch (Exception e) {
                say(e.getMessage(), true);
            }
        });
    }

    private static void say(String text, boolean isError) {
        message = text;
        messageError = isError;
        messageAt = System.currentTimeMillis();
    }

    private static void heartbeat() {
        try {
            JsonObject result;
            if (visible) {
                JsonObject body = new JsonObject();
                body.addProperty("place", "game");
                if (currentServer != null) body.addProperty("server", currentServer);
                if (mcVersion != null) body.addProperty("version", mcVersion);
                body.addProperty("hideServer", !showServer);
                result = call("POST", "api/presence", body, 10000);
            } else {
                result = call("GET", "api/friends", null, 10000);
            }
            apply(result);
            error = null;
        } catch (Exception e) {
            error = e.getMessage();
        }
    }

    private static synchronized void apply(JsonObject data) {
        if (!data.has("friends")) return;
        List<Friend> newFriends = list(data.getAsJsonArray("friends"));
        List<Friend> newIncoming = list(data.getAsJsonArray("incoming"));
        if (loaded && notify) {
            Set<String> wasOnline = new HashSet<>();
            for (Friend f : friends) if (f.online) wasOnline.add(f.uuid);
            for (Friend f : newFriends) {
                if (f.online && !wasOnline.contains(f.uuid)) NOTICES.add("§a● §f" + f.name + " §7ist jetzt online");
            }
            Set<String> knownRequests = new HashSet<>();
            for (Friend f : incoming) knownRequests.add(f.uuid);
            for (Friend f : newIncoming) {
                if (!knownRequests.contains(f.uuid)) NOTICES.add("§d✉ §f" + f.name + " §7möchte mit dir befreundet sein §8(Rechts-Shift → Freunde)");
            }
        }
        friends = newFriends;
        incoming = newIncoming;
        outgoing = list(data.getAsJsonArray("outgoing"));
        if (data.has("me")) me = new Friend(data.getAsJsonObject("me"));
        if (data.has("serverTime")) serverOffset = System.currentTimeMillis() - data.get("serverTime").getAsLong();
        loaded = true;
    }

    private static List<Friend> list(JsonArray array) {
        List<Friend> out = new ArrayList<>();
        if (array == null) return out;
        for (JsonElement e : array) out.add(new Friend(e.getAsJsonObject()));
        return Collections.unmodifiableList(out);
    }

    /** Aufruf am Friends-Server (blockierend, nur aus Hintergrund-Threads). */
    public static JsonObject post(String path, JsonObject body) throws Exception {
        return call("POST", path, body, 10000);
    }

    /** Abfrage am Friends-Server (blockierend, nur aus Hintergrund-Threads). */
    public static JsonObject get(String path) throws Exception {
        return call("GET", path, null, 10000);
    }

    /** Aufgabe im Hintergrund-Thread der Freundesliste ausführen. */
    public static void background(Runnable task) {
        if (worker != null) worker.execute(task);
    }

    @SuppressWarnings("deprecation")
    private static JsonObject call(String method, String path, JsonObject body, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestProperty("Authorization", "Bearer " + TOKEN);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "MyticClient");
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status;
        try {
            status = c.getResponseCode();
        } catch (Exception e) {
            throw new Exception("Friends-Server nicht erreichbar");
        }
        InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
        String text = "{}";
        if (in != null) {
            try (InputStream stream = in) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = stream.read(chunk)) > 0) buffer.write(chunk, 0, n);
                text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        }
        JsonObject result = new JsonParser().parse(text).getAsJsonObject();
        if (status >= 400) {
            String reason = result.has("error") ? result.get("error").getAsString() : "Fehler " + status;
            if (status == 401) reason = "Anmeldung abgelaufen – starte das Spiel über den Launcher neu";
            throw new Exception(reason);
        }
        return result;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static int[] head(String hex) {
        if (hex == null || hex.length() != 512) return null;
        int[] pixels = new int[64];
        for (int i = 0; i < 64; i++) pixels[i] = (int) Long.parseLong(hex.substring(i * 8, i * 8 + 8), 16);
        return pixels;
    }

    private static String withSlash(String url) {
        return url.isEmpty() || url.endsWith("/") ? url : url + "/";
    }

    static String ago(long time) {
        if (time <= 0) return "noch nie online";
        long s = Math.max(0, (System.currentTimeMillis() - serverOffset - time) / 1000);
        if (s < 3600) return "zuletzt online vor " + Math.max(1, Math.round(s / 60.0)) + " Min.";
        if (s < 86400) return "zuletzt online vor " + Math.round(s / 3600.0) + " Std.";
        long d = Math.round(s / 86400.0);
        return "zuletzt online vor " + d + (d == 1 ? " Tag" : " Tagen");
    }
}
