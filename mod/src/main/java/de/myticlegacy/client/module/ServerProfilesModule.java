package de.myticlegacy.client.module;

import de.myticlegacy.client.Config;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.hud.HudProfiles;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-Profile: Pro Server werden HUD-Profil und an/aus aller übrigen Mods gemerkt und beim Betreten automatisch gesetzt.
 * Beim Verlassen kommt der Zustand von vorher zurück. Bedienung im Mod-Menü: „Merken“ auf dem jeweiligen Server.
 */
public class ServerProfilesModule extends Module {
    /** Gehören nicht zum Server-Profil (Client-weit) */
    private static final Set<String> GLOBAL = Set.of("serverprofiles", "theme", "friends", "keyconflicts", "cloudsync");

    private final BoolSetting auto;
    private String server;
    private Config.ServerProfile before;

    public ServerProfilesModule() {
        super("serverprofiles", "Server-Profile", "Pro Server eigenes HUD-Profil und eigene Mods – wird beim Betreten automatisch gesetzt",
                Category.CLIENT, () -> Items.COMPASS, true);
        auto = new BoolSetting(this, "auto", "Automatisch wechseln", true);
        new ActionSetting(this, "save", "Aktuellen Stand für diesen Server", () -> server == null ? "Erst Server betreten"
                : (profiles().containsKey(server) ? "Neu merken" : "Merken") + " (" + server + ")", this::remember);
        new ActionSetting(this, "forget", "Profil dieses Servers löschen", () -> server != null && profiles().containsKey(server) ? "Löschen" : "–", this::forget);
    }

    private static Map<String, Config.ServerProfile> profiles() {
        return MyticClient.config().serverProfiles;
    }

    /** Server-Adresse ohne Standard-Port, klein geschrieben; null = keine Welt oder Einzelspieler */
    private static String currentServer(Minecraft mc) {
        if (mc.level == null || mc.getCurrentServer() == null) return null;
        String ip = mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).trim();
        return ip.endsWith(":25565") ? ip.substring(0, ip.length() - 6) : ip;
    }

    private static Config.ServerProfile snapshot() {
        Config.ServerProfile p = new Config.ServerProfile();
        p.hudProfile = HudProfiles.active();
        for (Module m : MyticClient.MODULES) {
            if (m.category == Category.HUD || GLOBAL.contains(m.id)) continue; // HUD-Module stecken im HUD-Profil
            p.modules.put(m.id, m.enabled());
        }
        return p;
    }

    private static void apply(Config.ServerProfile p) {
        HudProfiles.saveActive();
        if (p.hudProfile != null) HudProfiles.switchTo(p.hudProfile);
        for (Module m : MyticClient.MODULES) {
            Boolean on = p.modules.get(m.id);
            if (on != null && on != m.enabled()) MyticClient.config().enabled.put(m.id, on);
        }
        MyticClient.config().save();
    }

    public void tick(Minecraft mc) {
        String now = currentServer(mc);
        if (java.util.Objects.equals(now, server)) return;
        // Server verlassen/gewechselt: vorherigen Zustand zurück
        if (before != null) {
            apply(before);
            before = null;
        }
        server = now;
        Config.ServerProfile p = now == null ? null : profiles().get(now);
        if (p != null && enabled() && auto.get()) {
            before = snapshot();
            apply(p);
            Msg.actionBar("Server-Profil für " + now + " geladen");
        }
    }

    private void remember() {
        if (server == null) {
            Msg.actionBar("Erst einen Server betreten");
            return;
        }
        HudProfiles.saveActive();
        if (before == null) before = snapshot(); // beim Verlassen kommt der Stand von vor dem Merken zurück
        profiles().put(server, snapshot());
        MyticClient.config().save();
        Msg.actionBar("Server-Profil für " + server + " gemerkt");
    }

    private void forget() {
        if (server == null || profiles().remove(server) == null) return;
        MyticClient.config().save();
        Msg.actionBar("Server-Profil für " + server + " gelöscht");
    }
}
