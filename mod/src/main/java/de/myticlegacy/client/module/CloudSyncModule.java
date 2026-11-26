package de.myticlegacy.client.module;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.Config;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.Setting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Cloud-Sync: Mods (an/aus, Einstellungen), HUD-Positionen, HUD- und Server-Profile und alle Tasten am Mytic-Konto sichern
 * (Friends-Server, Anmeldung über den Launcher). Auf einem neuen PC wird beim ersten Start automatisch wiederhergestellt.
 * <p>
 * Neuere Launcher synchronisieren selbst („Einstellungen synchronisieren“, -Dmytic.sync=launcher): vor dem Start, nach
 * Änderungen und beim Beenden. Dann gibt es hier nur die Knöpfe – sie laufen über die Launcher-Verbindung (Accounts), nach
 * dem Herunterladen bzw. Wiederherstellen wird die Datei neu eingelesen. Ohne Launcher-Sync gilt der alte Weg (api/settings).
 */
public class CloudSyncModule extends Module {
    /** Der Launcher gleicht selbst ab – kein eigener automatischer Abgleich */
    private static final boolean VIA_LAUNCHER = "launcher".equals(System.getProperty("mytic.sync", ""));
    /** Launcher kann Sync (GET /sync klappt) – dann laufen die Knöpfe über ihn */
    private volatile boolean launcherSync;
    private boolean probed;
    private volatile String seenCloudHash;
    private volatile java.util.List<JsonObject> backups = java.util.Collections.emptyList();
    private volatile int backupIndex;
    /** Diese Teile der Konfiguration wandern in die Cloud (Cosmetics etc. kommen ohnehin vom Server) */
    private static final String[] FIELDS = {"enabled", "positions", "settings", "hudProfiles", "hudProfile", "serverProfiles"};
    private static final Gson GSON = new Gson();

    private final BoolSetting auto;
    private String uploaded;          // zuletzt gesicherter Stand (zum Erkennen von Änderungen)
    private boolean checkedRemote;
    private volatile boolean busy;
    private int ticks;
    private volatile long remoteTime;

    public CloudSyncModule() {
        super("cloudsync", "Cloud-Sync", "Mods, HUD, Profile und Tasten am Konto sichern – auf anderen PCs wiederherstellen", Category.CLIENT, () -> Items.ENDER_CHEST, true);
        auto = new BoolSetting(this, "auto", "Automatisch sichern", true);
        new ActionSetting(this, "upload", "Jetzt hochladen", () -> busy ? "…" : "Hochladen", () -> upload(true));
        new ActionSetting(this, "download", "Jetzt herunterladen", () -> busy ? "…" : remoteTime > 0 ? "Laden (" + when(remoteTime) + ")" : "Laden", this::download);
        new ActionSetting(this, "backups", "Sicherungen", this::backupLabel, this::nextBackup);
        new ActionSetting(this, "restore", "Gewählte Sicherung wiederherstellen", () -> busy ? "…" : "Wiederherstellen", this::restoreBackup);
    }

    // ---------- Launcher-Sync (Knöpfe über die Launcher-Verbindung) ----------

    private static final java.util.Map<String, String> REASONS = java.util.Map.of("lokal", "dieser PC", "cloud", "Cloud", "wiederherstellen", "vor Wiederherst.");

    private String backupLabel() {
        if (!launcherSync) return "Nur mit Launcher-Sync";
        java.util.List<JsonObject> list = backups;
        if (list.isEmpty()) return busy ? "…" : "Laden";
        JsonObject b = list.get(Math.min(backupIndex, list.size() - 1));
        String reason = b.has("reason") ? REASONS.getOrDefault(b.get("reason").getAsString(), b.get("reason").getAsString()) : "";
        return (backupIndex + 1) + "/" + list.size() + ": " + when(b.get("at").getAsLong()) + " (" + reason + ")";
    }

    /** Sicherungen laden bzw. die nächste wählen */
    private void nextBackup() {
        if (!launcherSync) {
            Msg.actionBar("Sicherungen gibt es mit „Einstellungen synchronisieren“ im Mytic Launcher");
            return;
        }
        if (!backups.isEmpty()) {
            backupIndex = (backupIndex + 1) % backups.size();
            return;
        }
        bridge("GET", "sync/backups", null, r -> {
            java.util.List<JsonObject> list = new java.util.ArrayList<>();
            if (r.has("backups")) for (JsonElement e : r.getAsJsonArray("backups")) list.add(e.getAsJsonObject());
            backups = list;
            backupIndex = 0;
            if (list.isEmpty()) Msg.actionBar("Noch keine Sicherungen – sie entstehen vor jedem Überschreiben");
        });
    }

    private void restoreBackup() {
        if (!launcherSync || backups.isEmpty()) {
            Msg.actionBar(launcherSync ? "Erst unter „Sicherungen“ eine Sicherung wählen" : "Sicherungen gibt es mit „Einstellungen synchronisieren“ im Mytic Launcher");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("id", backups.get(Math.min(backupIndex, backups.size() - 1)).get("id").getAsString());
        bridge("POST", "sync/restore", body, r -> {
            backups = java.util.Collections.emptyList();
            reload("Sicherung wiederhergestellt");
        });
    }

    /** Aufruf an den Launcher im Hintergrund; done läuft auf dem Spiel-Thread */
    private void bridge(String method, String path, JsonObject body, java.util.function.Consumer<JsonObject> done) {
        if (busy) return;
        busy = true;
        Thread t = new Thread(() -> {
            try {
                JsonObject r = de.myticlegacy.client.account.Accounts.call(method, path, body, 30000);
                Minecraft.getInstance().execute(() -> {
                    busy = false;
                    done.accept(r);
                });
            } catch (Exception e) {
                busy = false;
                Minecraft.getInstance().execute(() -> Msg.actionBar("Cloud-Sync: " + e.getMessage()));
            }
        }, "Mytic Sync");
        t.setDaemon(true);
        t.start();
    }

    /** Vom Launcher geschriebene Einstellungen neu einlesen und übernehmen (geprüft wie Cloud-Daten) */
    private static void reload(String what) {
        try {
            java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("myticclient.json");
            JsonObject data = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
            Msg.actionBar(apply(data, false, false) ? what + " – Cosmetics und Styles nach Neustart" : "Einstellungen fehlerhaft – nichts geändert");
        } catch (Exception e) {
            MyticClient.LOG.warn("Cloud-Sync: Einstellungen nicht neu geladen", e);
            Msg.actionBar("Einstellungen konnten nicht neu geladen werden");
        }
    }

    /** Lebenszeichen meldet einen anderen Stand als der letzte Abgleich dieses PCs → Hinweis (ohne eigene Abfrage beim Server) */
    private void checkCloudHash() {
        String h = Friends.syncHash();
        if (h == null || h.equals(seenCloudHash) || busy) return;
        seenCloudHash = h;
        Thread t = new Thread(() -> {
            try {
                JsonObject st = de.myticlegacy.client.account.Accounts.call("GET", "sync", null, 10000);
                JsonObject state = st.has("state") && st.get("state").isJsonObject() ? st.getAsJsonObject("state") : null;
                String mine = state != null && state.has("hash") && !state.get("hash").isJsonNull() ? state.get("hash").getAsString() : null;
                if (!h.equals(mine)) Minecraft.getInstance().execute(() ->
                        Msg.actionBar("Cloud-Sync: neuere Einstellungen von einem anderen PC – Einstellungen → Konto → Jetzt herunterladen"));
            } catch (Exception ignored) {
                // Launcher weg – egal
            }
        }, "Mytic Sync");
        t.setDaemon(true);
        t.start();
    }

    private static String when(long t) {
        return new java.text.SimpleDateFormat("dd.MM. HH:mm").format(new java.util.Date(t));
    }

    private static JsonObject snapshot() {
        return snapshot(true);
    }

    /** Aktueller Stand als JSON: Konfigurationsfelder + (wenn keys) Tastenbelegung – auch für Profil-Codes */
    public static JsonObject snapshot(boolean withKeys) {
        JsonObject all = GSON.toJsonTree(MyticClient.config()).getAsJsonObject();
        JsonObject out = new JsonObject();
        for (String f : FIELDS) if (all.has(f)) out.add(f, all.get(f));
        if (!withKeys) return out;
        JsonObject keys = new JsonObject();
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) keys.addProperty(km.getName(), km.saveString());
        out.add("keys", keys);
        return out;
    }

    /** Module, die Chat-Nachrichten oder Befehle senden: nie aus fremden Profil-Codes übernehmen (an/aus, Einstellungen, Tasten) */
    private static final Set<String> NO_IMPORT = Set.of("autotext");
    private static final Pattern KEY_VALUE = Pattern.compile("key\\.(keyboard|mouse)\\.[a-z0-9._-]{1,40}");
    private static final int MAX_PROFILES = 100, MAX_NAME = 255;

    /** Eigenen gesicherten Stand übernehmen (Cloud, „Rückgängig“) */
    public static boolean apply(JsonObject data) {
        return apply(data, false, true);
    }

    /**
     * Gesicherten Stand übernehmen (im Render-Thread) – auch für Profil-Codes. foreign = Code eines anderen Spielers:
     * Befehls-Module (Auto Text) bleiben unberührt, Tasten nur mit withKeys und nur Mytic-Tasten. Alles wird erst
     * geprüft (nur bekannte Module/Einstellungen, gültige Werte); bei kaputten Daten wird nichts geändert → false.
     */
    public static boolean apply(JsonObject data, boolean foreign, boolean withKeys) {
        try {
            Config cfg = MyticClient.config();
            Map<String, Module> modules = new HashMap<>();
            for (Module m : MyticClient.MODULES) modules.put(m.id, m);
            Map<String, Setting<?>> known = new HashMap<>();
            for (Module m : MyticClient.MODULES) for (Setting<?> st : m.settings) if (!(st instanceof ActionSetting)) known.put(st.path(), st);
            Predicate<String> allowed = id -> modules.containsKey(id) && !(foreign && NO_IMPORT.contains(id));
            Predicate<String> hud = id -> allowed.test(id) && modules.get(id) instanceof HudModule;

            // 1. prüfen und vorbereiten (noch nichts ändern)
            Map<String, Boolean> enabled = obj(data, "enabled") == null ? null : bools(obj(data, "enabled"), allowed);
            Map<String, int[]> positions = obj(data, "positions") == null ? null : positions(obj(data, "positions"), allowed);
            Map<String, Object> settings = obj(data, "settings") == null ? null : settings(obj(data, "settings"), known, allowed);
            Map<String, Config.HudProfile> hudProfiles = null;
            if (obj(data, "hudProfiles") != null) {
                hudProfiles = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : obj(data, "hudProfiles").entrySet()) {
                    if (!e.getValue().isJsonObject() || e.getKey().isBlank() || e.getKey().length() > MAX_NAME || hudProfiles.size() >= MAX_PROFILES) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    Config.HudProfile p = new Config.HudProfile();
                    if (obj(o, "positions") != null) p.positions = positions(obj(o, "positions"), hud);
                    if (obj(o, "enabled") != null) p.enabled = bools(obj(o, "enabled"), hud);
                    if (obj(o, "settings") != null) p.settings = settings(obj(o, "settings"), known, hud);
                    hudProfiles.put(e.getKey(), p);
                }
            }
            boolean hasHudProfile = data.has("hudProfile");
            String hudProfile = hasHudProfile ? string(data.get("hudProfile")) : null;
            Map<String, Config.ServerProfile> serverProfiles = null;
            if (obj(data, "serverProfiles") != null) {
                serverProfiles = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : obj(data, "serverProfiles").entrySet()) {
                    if (!e.getValue().isJsonObject() || e.getKey().isBlank() || e.getKey().length() > MAX_NAME || serverProfiles.size() >= MAX_PROFILES) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    Config.ServerProfile p = new Config.ServerProfile();
                    p.hudProfile = string(o.get("hudProfile"));
                    if (obj(o, "modules") != null) p.modules = bools(obj(o, "modules"), allowed);
                    serverProfiles.put(e.getKey(), p);
                }
            }
            Minecraft mc = Minecraft.getInstance();
            Map<KeyMapping, InputConstants.Key> keys = new LinkedHashMap<>();
            if (withKeys && obj(data, "keys") != null) {
                JsonObject k = obj(data, "keys");
                for (KeyMapping km : mc.options.keyMappings) {
                    String name = km.getName();
                    if (foreign && (!name.startsWith("key.myticclient.") || name.startsWith("key.myticclient.autotext"))) continue;
                    InputConstants.Key key = key(k.get(name));
                    if (key != null) keys.put(km, key);
                }
            }

            // 2. übernehmen – bei fremden Codes bleiben die eigenen Werte der Befehls-Module erhalten
            if (enabled != null) {
                if (foreign) for (String id : NO_IMPORT) if (cfg.enabled.containsKey(id)) enabled.put(id, cfg.enabled.get(id));
                cfg.enabled = de.myticlegacy.client.setting.TrackedMap.of(enabled);
            }
            if (positions != null) cfg.positions = positions;
            if (settings != null) {
                if (foreign) for (Map.Entry<String, Object> e : cfg.settings.entrySet())
                    if (NO_IMPORT.contains(e.getKey().substring(0, Math.max(0, e.getKey().indexOf('.'))))) settings.put(e.getKey(), e.getValue());
                cfg.settings = de.myticlegacy.client.setting.TrackedMap.of(settings);
            }
            if (hudProfiles != null) cfg.hudProfiles = hudProfiles;
            if (hasHudProfile) cfg.hudProfile = hudProfile;
            if (serverProfiles != null) cfg.serverProfiles = serverProfiles;
            cfg.save();
            if (!keys.isEmpty()) {
                for (Map.Entry<KeyMapping, InputConstants.Key> e : keys.entrySet()) e.getKey().setKey(e.getValue());
                KeyMapping.resetMapping();
                mc.options.save();
            }
            return true;
        } catch (RuntimeException e) {
            MyticClient.LOG.warn("Einstellungen konnten nicht übernommen werden", e);
            return false;
        }
    }

    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String string(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && e.getAsString().length() <= MAX_NAME ? e.getAsString() : null;
    }

    /** Modul → an/aus; unbekannte Module und falsche Typen fallen weg */
    private static Map<String, Boolean> bools(JsonObject o, Predicate<String> allowed) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            JsonElement v = e.getValue();
            if (allowed.test(e.getKey()) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) out.put(e.getKey(), v.getAsBoolean());
        }
        return out;
    }

    private static Map<String, int[]> positions(JsonObject o, Predicate<String> allowed) {
        Map<String, int[]> out = Config.positions(o);
        out.keySet().removeIf(id -> !allowed.test(id));
        return out;
    }

    /** Nur registrierte Einstellungen, Wert geprüft wie beim normalen Setzen (Länge, Bereich, Auswahl) */
    private static Map<String, Object> settings(JsonObject o, Map<String, Setting<?>> known, Predicate<String> allowed) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            Setting<?> st = known.get(e.getKey());
            if (st == null || !allowed.test(st.module.id) || !e.getValue().isJsonPrimitive()) continue;
            JsonPrimitive p = e.getValue().getAsJsonPrimitive();
            Object raw = p.isBoolean() ? (Object) p.getAsBoolean() : p.isNumber() ? (Object) p.getAsDouble() : p.getAsString();
            Object value = st.sanitize(raw);
            if (value != null) out.put(e.getKey(), value);
        }
        return out;
    }

    /** Gültiger Tastenname (z. B. „key.keyboard.g“, „key.mouse.left“) oder null */
    private static InputConstants.Key key(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return null;
        String name = e.getAsString();
        if (!KEY_VALUE.matcher(name).matches()) return null;
        try {
            return InputConstants.getKey(name);
        } catch (RuntimeException ex) {
            return null; // Taste gibt es hier nicht (andere Version) – überspringen
        }
    }

    public void tick(Minecraft mc) {
        if (!probed && de.myticlegacy.client.account.Accounts.available()) { // kann der Launcher Sync? (lokale Anfrage)
            probed = true;
            Thread t = new Thread(() -> {
                try {
                    de.myticlegacy.client.account.Accounts.call("GET", "sync", null, 5000);
                    launcherSync = true;
                } catch (Exception ignored) {
                    // älterer Launcher
                }
            }, "Mytic Sync");
            t.setDaemon(true);
            t.start();
        }
        if (VIA_LAUNCHER) {
            if (launcherSync && ++ticks >= 200) { // alle 10 s schauen, ob das Lebenszeichen einen neuen Stand meldet
                ticks = 0;
                checkCloudHash();
            }
            return;
        }
        if (!enabled() || !Friends.available()) return;
        if (!checkedRemote) { // einmal nach dem Start: neuer PC (noch nichts eingestellt) → Stand aus der Cloud holen
            checkedRemote = true;
            boolean fresh = MyticClient.config().enabled.isEmpty() && MyticClient.config().positions.isEmpty();
            fetch(fresh);
            return;
        }
        if (!auto.get() || busy || ++ticks < 400) return; // alle 20 s nach Änderungen schauen
        ticks = 0;
        if (uploaded != null && !snapshot().toString().equals(uploaded)) upload(false);
    }

    /** Stand der Cloud holen; restore = gleich übernehmen */
    private void fetch(boolean restore) {
        busy = true;
        Friends.background(() -> {
            try {
                JsonObject r = Friends.get("api/settings");
                remoteTime = r.has("updated") ? r.get("updated").getAsLong() : 0;
                JsonElement data = r.get("data");
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (restore && data != null && data.isJsonObject())
                        Msg.actionBar(apply(data.getAsJsonObject()) ? "Einstellungen aus der Cloud geladen" : "Cloud-Daten fehlerhaft – nichts geändert");
                    uploaded = snapshot().toString(); // ab jetzt Änderungen sichern
                    busy = false;
                });
            } catch (Exception e) {
                MyticClient.LOG.warn("Cloud-Sync: {}", e.getMessage());
                busy = false;
            }
        });
    }

    private void upload(boolean manual) {
        if (manual && launcherSync) {
            bridge("POST", "sync/upload", null, r -> Msg.actionBar("Einstellungen hochgeladen"));
            return;
        }
        if (!Friends.available()) {
            Msg.actionBar("Cloud-Sync geht nur über den Mytic Launcher");
            return;
        }
        if (busy) return;
        busy = true;
        String text = snapshot().toString();
        JsonObject body = new JsonObject();
        body.add("data", GSON.fromJson(text, JsonObject.class));
        Friends.background(() -> {
            try {
                JsonObject r = Friends.post("api/settings", body);
                remoteTime = r.has("updated") ? r.get("updated").getAsLong() : System.currentTimeMillis();
                uploaded = text;
                if (manual) Minecraft.getInstance().execute(() -> Msg.actionBar("Einstellungen in der Cloud gesichert"));
            } catch (Exception e) {
                if (manual) Minecraft.getInstance().execute(() -> Msg.actionBar("Sichern fehlgeschlagen: " + e.getMessage()));
            } finally {
                busy = false;
            }
        });
    }

    private void download() {
        if (launcherSync) {
            Msg.actionBar("Lade aus der Cloud …");
            bridge("POST", "sync/download", null, r -> reload("Einstellungen aus der Cloud übernommen (alter Stand gesichert)"));
            return;
        }
        if (!Friends.available()) {
            Msg.actionBar("Cloud-Sync geht nur über den Mytic Launcher");
            return;
        }
        if (busy) return;
        Msg.actionBar("Lade aus der Cloud …");
        fetch(true);
    }
}
