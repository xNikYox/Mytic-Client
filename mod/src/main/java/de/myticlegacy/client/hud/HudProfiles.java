package de.myticlegacy.client.hud;

import de.myticlegacy.client.Config;
import de.myticlegacy.client.MyticClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HUD-Profile (z. B. „PvP“, „Bauen“, „Stream“): jedes Profil merkt sich, welche HUD-Module an sind, wo sie stehen und
 * ihre Einstellungen (Größe, Farben …). Umschalten im HUD-Editor oder per Taste (Profil 1–3).
 */
public final class HudProfiles {
    private HudProfiles() {
    }

    private static Config cfg() {
        return MyticClient.config();
    }

    /** Beim ersten Mal: aktuelles HUD als Profil „Standard“ */
    public static void ensure() {
        if (cfg().hudProfiles.isEmpty()) {
            cfg().hudProfiles.put("Standard", capture());
            cfg().hudProfile = "Standard";
            cfg().save();
        }
        if (cfg().hudProfile == null || !cfg().hudProfiles.containsKey(cfg().hudProfile)) cfg().hudProfile = names().get(0);
    }

    public static List<String> names() {
        return new ArrayList<>(cfg().hudProfiles.keySet());
    }

    public static String active() {
        ensure();
        return cfg().hudProfile;
    }

    private static Config.HudProfile capture() {
        Config.HudProfile p = new Config.HudProfile();
        for (HudModule m : MyticClient.HUD) {
            int[] pos = cfg().positions.get(m.id);
            if (pos != null) p.positions.put(m.id, pos.clone());
            p.enabled.put(m.id, m.enabled());
            for (Map.Entry<String, Object> e : cfg().settings.entrySet()) if (e.getKey().startsWith(m.id + ".")) p.settings.put(e.getKey(), e.getValue());
        }
        return p;
    }

    /** aktuellen Stand im aktiven Profil sichern (z. B. nach dem Verschieben) */
    public static void saveActive() {
        ensure();
        cfg().hudProfiles.put(cfg().hudProfile, capture());
        cfg().save();
    }

    public static void switchTo(String name) {
        ensure();
        Config.HudProfile target = cfg().hudProfiles.get(name);
        if (target == null || name.equals(cfg().hudProfile)) return;
        cfg().hudProfiles.put(cfg().hudProfile, capture());
        for (HudModule m : MyticClient.HUD) {
            int[] pos = target.positions.get(m.id);
            if (pos != null) cfg().positions.put(m.id, pos.clone());
            else cfg().positions.remove(m.id);
            Boolean on = target.enabled.get(m.id);
            if (on != null) cfg().enabled.put(m.id, on);
            cfg().settings.keySet().removeIf(k -> k.startsWith(m.id + "."));
        }
        cfg().settings.putAll(target.settings);
        cfg().hudProfile = name;
        cfg().save();
    }

    /** Profil 1–3 per Taste */
    public static String switchToIndex(int i) {
        List<String> n = names();
        if (i < 0 || i >= n.size()) return null;
        switchTo(n.get(i));
        return n.get(i);
    }

    /** neues Profil als Kopie des aktuellen HUDs */
    public static String create() {
        ensure();
        int k = cfg().hudProfiles.size() + 1;
        String name = "Profil " + k;
        while (cfg().hudProfiles.containsKey(name)) name = "Profil " + (++k);
        cfg().hudProfiles.put(cfg().hudProfile, capture());
        cfg().hudProfiles.put(name, capture());
        cfg().hudProfile = name;
        cfg().save();
        return name;
    }

    public static boolean rename(String from, String to) {
        to = to == null ? "" : to.trim();
        if (to.isEmpty() || to.length() > 20 || cfg().hudProfiles.containsKey(to) || !cfg().hudProfiles.containsKey(from)) return false;
        Map<String, Config.HudProfile> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Config.HudProfile> e : cfg().hudProfiles.entrySet()) copy.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
        cfg().hudProfiles = copy;
        if (from.equals(cfg().hudProfile)) cfg().hudProfile = to;
        cfg().save();
        return true;
    }

    /** löschen (das letzte bleibt) */
    public static boolean delete(String name) {
        if (cfg().hudProfiles.size() <= 1) return false;
        boolean wasActive = name.equals(cfg().hudProfile);
        if (wasActive) {
            String other = names().stream().filter(n -> !n.equals(name)).findFirst().orElse(null);
            switchTo(other);
        }
        cfg().hudProfiles.remove(name);
        cfg().save();
        return true;
    }
}
