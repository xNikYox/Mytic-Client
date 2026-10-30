package de.myticlegacy.client.module;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.Config;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * Cloud-Sync: Mods (an/aus, Einstellungen), HUD-Positionen, HUD- und Server-Profile und alle Tasten am Mytic-Konto sichern
 * (Friends-Server, Anmeldung über den Launcher). Auf einem neuen PC wird beim ersten Start automatisch wiederhergestellt.
 */
public class CloudSyncModule extends Module {
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
        new ActionSetting(this, "upload", "Jetzt sichern", () -> busy ? "…" : "Sichern", () -> upload(true));
        new ActionSetting(this, "download", "Aus der Cloud laden", () -> busy ? "…" : remoteTime > 0 ? "Laden (" + when(remoteTime) + ")" : "Laden", this::download);
    }

    private static String when(long t) {
        return new java.text.SimpleDateFormat("dd.MM. HH:mm").format(new java.util.Date(t));
    }

    /** Aktueller Stand als JSON: Konfigurationsfelder + Tastenbelegung */
    private static JsonObject snapshot() {
        JsonObject all = GSON.toJsonTree(MyticClient.config()).getAsJsonObject();
        JsonObject out = new JsonObject();
        for (String f : FIELDS) if (all.has(f)) out.add(f, all.get(f));
        JsonObject keys = new JsonObject();
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) keys.addProperty(km.getName(), km.saveString());
        out.add("keys", keys);
        return out;
    }

    /** Gesicherten Stand übernehmen (im Render-Thread) */
    private static void apply(JsonObject data) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject all = GSON.toJsonTree(MyticClient.config()).getAsJsonObject();
        for (String f : FIELDS) if (data.has(f)) all.add(f, data.get(f));
        Config loaded = GSON.fromJson(all, Config.class);
        Config cfg = MyticClient.config();
        if (loaded.enabled != null) cfg.enabled = loaded.enabled;
        if (loaded.positions != null) cfg.positions = loaded.positions;
        if (loaded.settings != null) cfg.settings = loaded.settings;
        if (loaded.hudProfiles != null) cfg.hudProfiles = loaded.hudProfiles;
        cfg.hudProfile = loaded.hudProfile;
        if (loaded.serverProfiles != null) cfg.serverProfiles = loaded.serverProfiles;
        cfg.save();
        if (data.has("keys") && data.get("keys").isJsonObject()) {
            JsonObject keys = data.getAsJsonObject("keys");
            for (KeyMapping km : mc.options.keyMappings) {
                JsonElement k = keys.get(km.getName());
                if (k == null) continue;
                try {
                    km.setKey(InputConstants.getKey(k.getAsString()));
                } catch (RuntimeException e) {
                    // Taste gibt es hier nicht (andere Version) – überspringen
                }
            }
            KeyMapping.resetMapping();
            mc.options.save();
        }
    }

    public void tick(Minecraft mc) {
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
                    if (restore && data != null && data.isJsonObject()) {
                        apply(data.getAsJsonObject());
                        Msg.actionBar("Einstellungen aus der Cloud geladen");
                    }
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
        if (!Friends.available()) {
            Msg.actionBar("Cloud-Sync geht nur über den Mytic Launcher");
            return;
        }
        if (busy) return;
        Msg.actionBar("Lade aus der Cloud …");
        fetch(true);
    }
}
