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
 * Sitzung als -Dmytic.friends.url und Umgebungsvariable MYTIC_FRIENDS_TOKEN (früher -Dmytic.friends.token). Alle Anfragen laufen in einem eigenen Thread; das Spiel
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

    private static final String BASE = withSlash(safeUrl(System.getProperty("mytic.friends.url", "").trim()));
    /** Sitzung zuerst aus der Umgebung (nicht in der Prozessliste sichtbar), sonst wie früher als System-Property */
    private static volatile String TOKEN = token();
    /** Größte erlaubte Antwort des Servers */
    private static final int MAX_RESPONSE = 5 * 1024 * 1024;

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

    // ------------------------------------------------------------------ Lebenszeichen sparsam (Free-Plan: Anfragen zählen)
    /** Lebenszeichen höchstens so oft (der Server zeigt "online" bis 150 s nach dem letzten) */
    private static final long BEAT_MS = 60_000;
    /** … und öfter, solange ein Freund im Spiel ist, der zu einer Partie einladen kann (Einladungen kommen mit dem Lebenszeichen) */
    private static final long BEAT_FAST_MS = 30_000;
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
    /** Ein Freund ist im Spiel und kann einladen → Lebenszeichen öfter */
    private static volatile boolean fastBeat;
    /** Offene Einladungen zu Partien laut letztem Lebenszeichen (-1 = Server meldet das nicht); Zähler je Meldung mit Einladung */
    private static volatile int gameInvites = -1;
    private static volatile int gameHints;
    /** Spielzeit für das nächste Lebenszeichen (Stats) */
    private static JsonObject pendingPlay;
    /** Freischaltungen aus dem letzten Lebenszeichen (Cosmetics) und Zähler, wie oft sie kamen */
    private static volatile JsonObject owned;
    private static volatile int ownedSeq;

    private Friends() {
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
        return visible ? "on|" + showServer + "|" + currentServer + "|" + mcVersion : "hidden";
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
            long every = fastBeat ? BEAT_FAST_MS : BEAT_MS;
            // kleiner Spielraum, damit der 5-s-Takt das Lebenszeichen nicht auf 65 s streckt
            if (sentState == null || changed || now - beatAt >= every - CHECK_MS / 2) heartbeat();
        } catch (Throwable ignored) {
            // nächster Takt
        }
    }

    private static void heartbeat() {
        beatAt = System.currentTimeMillis();
        String state = state();
        JsonObject play = presence2 ? takePlay() : null;
        try {
            JsonObject result;
            if (visible || presence2) {
                JsonObject body = new JsonObject();
                body.addProperty("place", "game");
                if (currentServer != null) body.addProperty("server", currentServer);
                if (mcVersion != null) body.addProperty("version", mcVersion);
                body.addProperty("hideServer", !showServer);
                // versteckt: nur Server, die das kennen (presence2), sonst wie früher nur die Liste holen
                if (!visible) body.addProperty("hidden", true);
                if (play != null) body.add("play", play);
                result = call("POST", "api/presence", body, 10000);
            } else {
                result = call("GET", "api/friends", null, 10000);
            }
            play = null; // angekommen (oder ungültig) – nicht noch einmal schicken
            sentState = state;
            apply(result);
            extras(result);
            error = null;
        } catch (Exception e) {
            error = e.getMessage();
            if (sentState == null) sentState = state;
            if (play != null) queuePlay(play); // beim nächsten Mal mit
        }
    }

    /** Was das Lebenszeichen zusätzlich bringt (Server mit presence2). */
    private static void extras(JsonObject data) {
        boolean caps = false;
        if (data.has("caps") && data.get("caps").isJsonArray()) {
            for (JsonElement e : data.getAsJsonArray("caps")) if ("presence2".equals(e.getAsString())) caps = true;
        }
        presence2 = caps;
        if (data.has("games") && data.get("games").isJsonObject()) {
            JsonObject g = data.getAsJsonObject("games");
            gameInvites = g.has("invites") ? g.get("invites").getAsInt() : 0;
            if (gameInvites > 0) gameHints++;
        }
        if (data.has("owned") && data.get("owned").isJsonObject()) {
            owned = data.getAsJsonObject("owned");
            ownedSeq++;
        }
    }

    /** Freischaltungen aus dem letzten Lebenszeichen ({items, admin}) oder null. */
    public static JsonObject owned() {
        return owned;
    }

    /** Ändert sich mit jedem Lebenszeichen, das Freischaltungen mitbringt (0 = noch keins). */
    public static int ownedSeq() {
        return ownedSeq;
    }

    /** Meldet der Server offene Einladungen mit dem Lebenszeichen? (sonst muss Games selbst regelmäßig fragen) */
    public static boolean reportsGames() {
        return gameInvites >= 0;
    }

    /** Ändert sich, wenn ein Lebenszeichen offene Einladungen meldet. */
    public static int gameHints() {
        return gameHints;
    }

    /** Kann die Spielzeit mit dem Lebenszeichen mitgehen? (sonst schickt Stats sie selbst) */
    public static boolean takesPlay() {
        return presence2 && available();
    }

    /** Spielzeit für das nächste Lebenszeichen vormerken; mehrere Meldungen werden zusammengefasst (höchstens 120 s). */
    public static synchronized void queuePlay(JsonObject play) {
        if (pendingPlay != null) {
            long seconds = Math.min(120, pendingPlay.get("seconds").getAsLong() + play.get("seconds").getAsLong());
            play.addProperty("seconds", seconds); // play gehört schon uns (Stats baut es jedes Mal neu)
        }
        pendingPlay = play;
    }

    private static synchronized JsonObject takePlay() {
        JsonObject play = pendingPlay;
        pendingPlay = null;
        return play;
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
        // Einladen können nur Spieler im Spiel mit Mytic Games (nicht 1.8.9)
        boolean fast = false;
        for (Friend f : newFriends) if (f.inGame() && !"1.8.9".equals(f.version)) fast = true;
        fastBeat = fast;
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
                while ((n = stream.read(chunk)) > 0) {
                    buffer.write(chunk, 0, n);
                    if (buffer.size() > MAX_RESPONSE) throw new Exception("Antwort des Friends-Servers zu groß");
                }
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

    private static String token() {
        String env = System.getenv("MYTIC_FRIENDS_TOKEN");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        return System.getProperty("mytic.friends.token", "").trim();
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
