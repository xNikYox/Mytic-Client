package de.myticlegacy.client.emotes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Laufende Emotes: das eigene und die anderer Mytic-Spieler auf demselben Server (über EmoteLink). Die Versionen
 * fragen beim Zeichnen eines Spielers pose(uuid, name) ab und übertragen das Ergebnis aufs Modell.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Emotes {
    /** Mindestabstand zwischen zwei eigenen Emotes (wie die Bremse im Friends-Server) */
    public static final long GAP_MS = 3000;
    private static final int MAX_REMOTE = 64;

    public static final class Active {
        public final EmoteCatalog.Emote emote;
        /** lokale Startzeit (ms) */
        public final long start;
        /** Position beim ersten Sehen (für den Abbruch, wenn der Spieler losläuft); NaN = noch nicht gesehen */
        public double ax = Double.NaN, ay, az;

        Active(EmoteCatalog.Emote emote, long start) {
            this.emote = emote;
            this.start = start;
        }

        public boolean over(long now) {
            return now - start >= emote.ms;
        }
    }

    /** Einstellungen (vom Modul gesetzt) */
    public static volatile boolean enabled = true, showOthers = true;

    private static volatile Active self;
    private static volatile long lastPlay;
    private static volatile long waitUntil;
    /** fremde Emotes je UUID (ohne Striche, klein) */
    private static final Map<String, Active> REMOTE = new ConcurrentHashMap<String, Active>();
    /** Name (klein) → UUID, für Server im Offline-Modus (dort hat der Spieler eine andere UUID) */
    private static final Map<String, String> NAMES = new ConcurrentHashMap<String, String>();
    private static final float[] SCRATCH = new float[EmotePose.SIZE];
    /** zuletzt gehashte Adresse (nicht jeden Tick neu rechnen) */
    private static String hashedAddress, hash;

    private Emotes() {
    }

    // ------------------------------------------------------------------------------------------ eigenes Emote

    /** Eigenes Emote starten; gibt einen Hinweis zurück, warum nicht (oder null = läuft). */
    public static String play(String id) {
        EmoteCatalog.Emote e = EmoteCatalog.get(id);
        if (e == null || !enabled) return "Emotes sind ausgeschaltet";
        if (!EmoteLink.usable(e)) return "Dieses Emote ist nicht freigeschaltet";
        long now = System.currentTimeMillis();
        long wait = Math.max(lastPlay + GAP_MS, waitUntil) - now;
        if (wait > 0) return "Noch " + Math.max(1, (wait + 999) / 1000) + " s warten";
        lastPlay = now;
        self = new Active(e, now);
        EmoteLink.sendEmote(e.id);
        return null;
    }

    /** Eigenes Emote abbrechen (bewegt, angegriffen); send = auch den anderen melden. */
    public static void stopSelf(boolean send) {
        Active a = self;
        if (a == null) return;
        self = null;
        if (send && !a.over(System.currentTimeMillis())) EmoteLink.sendStop();
    }

    public static Active self() {
        Active a = self;
        if (a != null && a.over(System.currentTimeMillis())) self = a = null;
        return a;
    }

    /** Wie lange noch bis zum nächsten Emote (ms, 0 = sofort) */
    public static long cooldown() {
        return Math.max(0, Math.max(lastPlay + GAP_MS, waitUntil) - System.currentTimeMillis());
    }

    static void serverWait(long ms) {
        waitUntil = System.currentTimeMillis() + Math.min(10_000, Math.max(0, ms));
        self = null; // vom Server abgelehnt: bei den anderen läuft es nicht
    }

    // ------------------------------------------------------------------------------------------ andere Spieler

    static void received(String uuid, String name, String id, long localStart) {
        EmoteCatalog.Emote e = EmoteCatalog.get(id);
        if (e == null || !showOthers) return;
        long now = System.currentTimeMillis();
        long start = Math.min(localStart, now); // Uhren nie in die Zukunft
        if (now - start >= e.ms) return;
        String key = key(uuid);
        if (REMOTE.size() >= MAX_REMOTE && !REMOTE.containsKey(key)) prune(now);
        if (REMOTE.size() >= MAX_REMOTE) return;
        REMOTE.put(key, new Active(e, start));
        if (name != null) NAMES.put(name.toLowerCase(Locale.ROOT), key);
    }

    static void receivedStop(String uuid) {
        REMOTE.remove(key(uuid));
    }

    /** Emote eines anderen Spielers (UUID ohne/mit Strichen; Name für Offline-Server) oder null */
    public static Active remote(String uuid, String name) {
        if (REMOTE.isEmpty()) return null;
        Active a = uuid == null ? null : REMOTE.get(key(uuid));
        if (a == null && name != null) {
            String k = NAMES.get(name.toLowerCase(Locale.ROOT));
            if (k != null) a = REMOTE.get(k);
        }
        if (a != null && a.over(System.currentTimeMillis())) return null;
        return a;
    }

    /** Emote eines anderen beenden (er hat sich bewegt) */
    public static void dropRemote(String uuid, String name) {
        if (uuid != null) REMOTE.remove(key(uuid));
        if (name != null) {
            String k = NAMES.get(name.toLowerCase(Locale.ROOT));
            if (k != null) REMOTE.remove(k);
        }
    }

    public static Iterable<Map.Entry<String, Active>> remotes() {
        return REMOTE.entrySet();
    }

    private static void prune(long now) {
        for (Iterator<Map.Entry<String, Active>> it = REMOTE.entrySet().iterator(); it.hasNext(); ) if (it.next().getValue().over(now)) it.remove();
    }

    // ------------------------------------------------------------------------------------------ Takt und Pose

    /**
     * Aus dem Spiel-Thread, jeden Tick: Adresse des Multiplayer-Servers (null = Menü, Einzelspieler, Realms).
     * Verbindet nur, wenn Emotes an sind und man auf einem Server ist.
     */
    public static void tick(String serverAddress) {
        if (serverAddress != null && !serverAddress.equals(hashedAddress)) {
            hashedAddress = serverAddress;
            hash = serverHash(serverAddress);
        }
        EmoteLink.want(serverAddress == null ? null : hash, enabled || showOthers);
        if (serverAddress == null) {
            REMOTE.clear();
            NAMES.clear();
        } else if (!REMOTE.isEmpty()) {
            prune(System.currentTimeMillis());
        }
        if (!showOthers) REMOTE.clear();
        if (NAMES.size() > 256) NAMES.clear();
    }

    /**
     * Pose für einen Spieler (eigenes Emote bei self = true) oder null. Nur aus dem Render-Thread – das Ergebnis wird
     * beim nächsten Aufruf überschrieben.
     */
    public static float[] pose(String uuid, String name, boolean isSelf) {
        Active a = isSelf ? self() : remote(uuid, name);
        if (a == null) return null;
        float t = (System.currentTimeMillis() - a.start) / 1000f;
        return EmotePose.compute(a.emote.id, t, a.emote.ms / 1000f, SCRATCH) ? SCRATCH : null;
    }

    /** UUID-Schlüssel: ohne Striche, klein */
    static String key(String uuid) {
        return uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Hash des Servers: SHA-256 von "mytic-srv|host:port" (klein, Standard-Port 25565), 32 Hex-Zeichen. So erfährt der
     * Friends-Server nie die Adresse, kann aber Spieler auf demselben Server zusammenbringen.
     */
    public static String serverHash(String address) {
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.endsWith(".")) a = a.substring(0, a.length() - 1);
        boolean hasPort = a.startsWith("[") ? a.contains("]:") : a.indexOf(':') >= 0 && a.indexOf(':') == a.lastIndexOf(':');
        if (!hasPort) a = a + ":25565";
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("mytic-srv|" + a).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i] & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
