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
 * Sitzung als -Dmytic.friends.url und Umgebungsvariable MYTIC_FRIENDS_TOKEN. Alle Anfragen laufen in einem eigenen Thread; das Spiel
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
        /** Öffentliche Server-Adresse, „Beitreten“ möglich (Server mit join1; ältere Server: aus der Adresse geschätzt) */
        private final boolean join;

        Friend(JsonObject o) {
            uuid = str(o, "uuid");
            name = str(o, "name");
            head = head(str(o, "head"));
            online = o.has("online") && o.get("online").getAsBoolean();
            place = str(o, "place");
            server = str(o, "server");
            version = str(o, "version");
            join = o.has("join") && !o.get("join").isJsonNull() ? o.get("join").getAsBoolean() : JoinTarget.joinable(server);
            long seen = 0;
            if (o.has("lastSeen") && !o.get("lastSeen").isJsonNull()) seen = o.get("lastSeen").getAsLong();
            if (o.has("at") && !o.get("at").isJsonNull()) seen = o.get("at").getAsLong();
            lastSeen = seen;
        }

        public boolean inGame() {
            return online && "game".equals(place);
        }

        /** Kann man diesem Freund auf seinen Server folgen? (im Spiel, öffentliche Adresse) */
        public boolean joinable() {
            return inGame() && join && JoinTarget.joinable(server);
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

    private static final String BASE = withSlash(safeUrl(System.getProperty("mytic.friends.url", "").trim()));
    private static volatile String TOKEN = token();

    /** Token aus der Umgebungsvariable MYTIC_FRIENDS_TOKEN (nicht in der Prozessliste sichtbar), sonst -Dmytic.friends.token. */
    private static String token() {
        String env = System.getenv("MYTIC_FRIENDS_TOKEN");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        return System.getProperty("mytic.friends.token", "").trim();
    }

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
    /** Was Freunde als Server sehen (JoinTarget.shown: Realms/LAN/private Adressen als Platzhalter) */
    public static volatile String sharedServer;
    public static volatile String mcVersion;
    /** Online-Status für Freunde sichtbar / Server anzeigen / Benachrichtigungen (aus den Mod-Einstellungen). */
    public static volatile boolean visible = true;
    public static volatile boolean showServer = true;
    public static volatile boolean notify = true;

    private static final ConcurrentLinkedQueue<String> NOTICES = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService worker;

    // ------------------------------------------------------------------ Lebenszeichen sparsam (Free-Plan: Anfragen zählen)
    /** Lebenszeichen höchstens so oft (der Server zeigt "online" bis 150 s nach dem letzten) */
    private static final long BEAT_MS = 60_000;
    /** Prüftakt im Hintergrund (ohne Netz); Statuswechsel erst senden, wenn er so lange stabil ist … */
    private static final long CHECK_MS = 5_000;
    private static final long SETTLE_MS = 3_000;
    /** … und nicht öfter als so oft */
    private static final long MIN_GAP_MS = 10_000;
    /** Zeitpunkt des letzten Lebenszeichens (auch "refresh") und der damit gemeldete Status */
    private static volatile long beatAt;
    private static volatile String sentState;
    private static String seenState;
    private static long seenSince;
    /** Kann der Server mehr mit dem Lebenszeichen (Spielzeit, Einladungen, Freischaltungen, versteckter Status)? */
    private static volatile boolean presence2;
    /** Freischaltungen aus dem letzten Lebenszeichen (Cosmetics) und Zähler, wie oft sie kamen */
    private static volatile JsonObject owned;
    private static volatile int ownedSeq;
    /**
     * Cosmetics-Änderungen anderer Spieler (Server mit cos1): Stand des letzten Lebenszeichens (Serverzeit) und die
     * seitdem gemeldeten UUIDs – CosmeticsCore fragt nur diese neu ab statt alle paar Minuten alle.
     */
    private static volatile long cosSince;
    private static volatile boolean cosFeed;
    private static final ConcurrentLinkedQueue<String> COS_CHANGED = new ConcurrentLinkedQueue<>();

    private Friends() {
    }

    /** Fähigkeiten des Friends-Servers aus dem letzten Lebenszeichen (z. B. "emote1") */
    private static volatile Set<String> CAPS = Collections.emptySet();

    public static boolean hasCap(String cap) {
        return CAPS.contains(cap);
    }

    /** Adresse der Echtzeit-Verbindung (Emotes) */
    public static String liveUrl() {
        return BASE + "api/live";
    }

    /** Sitzung für die Echtzeit-Verbindung (nur in die Kopfzeile, nie ins Log) */
    public static String authToken() {
        return TOKEN;
    }

    /** Wurde der Client über den Mytic Launcher mit Freundesliste gestartet? */
    public static boolean available() {
        return !BASE.isEmpty() && !TOKEN.isEmpty();
    }

    /**
     * Kontowechsel im Hauptmenü: neue Sitzung des Freunde-Servers übernehmen, alte Listen verwerfen und neu laden.
     * token null/leer = Freundesliste für dieses Konto nicht verfügbar.
     */
    public static void switchSession(String token) {
        TOKEN = token == null ? "" : token.trim();
        friends = Collections.emptyList();
        incoming = Collections.emptyList();
        outgoing = Collections.emptyList();
        me = null;
        loaded = false;
        error = null;
        if (available()) {
            start();
            refresh();
        }
    }

    public static void start() {
        if (!available() || worker != null) return;
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mytic Friends");
            t.setDaemon(true);
            return t;
        });
        // alle 5 s nur lokal prüfen; Anfrage erst, wenn das Lebenszeichen fällig ist oder sich der Status geändert hat
        worker.scheduleWithFixedDelay(Friends::check, 3, CHECK_MS / 1000, TimeUnit.SECONDS);
        // beim Beenden sofort als offline zeigen
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                call("POST", "api/presence/offline", new JsonObject(), 2000);
            } catch (Exception ignored) {
                // egal – nach 150 s gilt man ohnehin als offline
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

    /** Sofort ein Lebenszeichen (Freunde-Fenster geöffnet, Status umgeschaltet); zählt als das fällige. */
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

    /** Status, wie ihn das Lebenszeichen meldet (Wechsel = sofort melden; versteckt zählt nur das Umschalten) */
    private static String state() {
        return visible ? "on|" + showServer + "|" + sharedServer + "|" + mcVersion : "hidden";
    }

    /** Hintergrund-Takt (ohne Netz): Lebenszeichen nur, wenn fällig oder der Status sich geändert hat (Server betreten/verlassen). */
    private static void check() {
        try {
            long now = System.currentTimeMillis();
            String state = state();
            if (!state.equals(seenState)) {
                seenState = state;
                seenSince = now;
            }
            boolean changed = sentState != null && !state.equals(sentState) && now - seenSince >= SETTLE_MS && now - beatAt >= MIN_GAP_MS;
            long every = BEAT_MS;
            // kleiner Spielraum, damit der 5-s-Takt das Lebenszeichen nicht auf 65 s streckt
            if (sentState == null || changed || now - beatAt >= every - CHECK_MS / 2) heartbeat();
        } catch (Throwable ignored) {
            // nächster Takt
        }
    }

    private static void heartbeat() {
        beatAt = System.currentTimeMillis();
        String state = state();
        try {
            JsonObject result;
            if (visible || presence2) {
                JsonObject body = new JsonObject();
                body.addProperty("place", "game");
                if (sharedServer != null) body.addProperty("server", sharedServer);
                if (mcVersion != null) body.addProperty("version", mcVersion);
                body.addProperty("hideServer", !showServer);
                // Cosmetics-Änderungen anderer seit dem letzten Lebenszeichen mitschicken lassen (ältere Server ignorieren das)
                body.addProperty("cosSince", cosSince);
                // versteckt: nur Server, die das kennen (presence2), sonst wie früher nur die Liste holen
                if (!visible) body.addProperty("hidden", true);
                result = call("POST", "api/presence", body, 10000);
            } else {
                result = call("GET", "api/friends", null, 10000);
            }
            sentState = state;
            apply(result);
            extras(result);
            error = null;
        } catch (Exception e) {
            error = e.getMessage();
            if (sentState == null) sentState = state;
        }
    }

    /** Was das Lebenszeichen zusätzlich bringt (Server mit presence2). */
    private static void extras(JsonObject data) {
        boolean caps = false;
        Set<String> all = new HashSet<>();
        if (data.has("caps") && data.get("caps").isJsonArray()) {
            for (JsonElement e : data.getAsJsonArray("caps")) {
                if ("presence2".equals(e.getAsString())) caps = true;
                all.add(e.getAsString());
            }
        }
        presence2 = caps;
        CAPS = all;
        if (data.has("cos") && data.get("cos").isJsonObject()) {
            JsonObject cos = data.getAsJsonObject("cos");
            if (cos.has("changed") && cos.get("changed").isJsonArray()) {
                for (JsonElement e : cos.getAsJsonArray("changed")) if (COS_CHANGED.size() < 1000) COS_CHANGED.add(e.getAsString());
            }
            if (cos.has("since")) cosSince = cos.get("since").getAsLong();
            cosFeed = true;
        }
        if (data.has("owned") && data.get("owned").isJsonObject()) {
            owned = data.getAsJsonObject("owned");
            ownedSeq++;
        }
    }

    /** Meldet der Server Cosmetics-Änderungen mit dem Lebenszeichen? (dann reicht seltenes Nachfragen) */
    public static boolean cosFeed() {
        return cosFeed;
    }

    /** Seit dem letzten Aufruf gemeldete Spieler (UUID ohne Striche), deren Cosmetics sich geändert haben. */
    public static List<String> takeCosChanged() {
        List<String> out = new ArrayList<>();
        String u;
        while ((u = COS_CHANGED.poll()) != null) out.add(u);
        return out;
    }

    /** Freischaltungen aus dem letzten Lebenszeichen ({items, admin}) oder null. */
    public static JsonObject owned() {
        return owned;
    }

    /** Ändert sich mit jedem Lebenszeichen, das Freischaltungen mitbringt (0 = noch keins). */
    public static int ownedSeq() {
        return ownedSeq;
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

    /** Nur https (Token wird mitgeschickt) – http nur für localhost/127.0.0.1 zum Testen; sonst keine Freundesliste */
    static String safeUrl(String url) {
        if (url.isEmpty()) return url;
        try {
            java.net.URI u = new java.net.URI(url);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(java.util.Locale.ROOT);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(java.util.Locale.ROOT);
            if (host.isEmpty() || u.getRawUserInfo() != null) return "";
            if (scheme.equals("https")) return url;
            if (scheme.equals("http") && (host.equals("localhost") || host.equals("127.0.0.1"))) return url;
        } catch (java.net.URISyntaxException ignored) {
            // ungültige Adresse
        }
        return "";
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
