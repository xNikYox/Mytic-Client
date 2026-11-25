package de.myticlegacy.client.module;

import de.myticlegacy.client.Config;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Leistungsprofile mit einem Klick (Client-Einstellungen → Leistung): „Maximale FPS“, „Ausgewogen“, „Schön“ stellen
 * Minecraft-Optionen (Sichtweite, Simulationsdistanz, Partikel, Schatten, Wolken, Grafik, Biom-Übergang, Bildrate,
 * VSync) und Mytic-Effekte (Menü-Animationen, Hauptmenü-Hintergrund, Unschärfe, HUD-Animationen, FPS-Boost) ein.
 * Vor dem ersten Profil werden die eigenen Werte gemerkt (auch über Neustarts) – „Eigenes“ stellt sie wieder her.
 */
public final class PerfProfiles {
    public static final String[] NAMES = {"Maximale FPS", "Ausgewogen", "Schön", "Eigenes"};
    public static final String[] DESCS = {
            "Kurze Sichtweite, wenig Effekte, FPS-Boost an",
            "Gute Bildrate mit ordentlicher Optik",
            "Weite Sicht, alle Effekte, VSync",
            "Deine Einstellungen von vor dem Profil"};
    public static final String[] ICONS = {"bolt", "gauge", "sparkle", "pencil"};
    public static final int OWN = 3;
    private static final String BOOST = "mod:fpsboost";

    private PerfProfiles() {
    }

    /** Eine umstellbare Einstellung: Wert als Text lesen und setzen */
    private interface Opt {
        String get();

        void set(String value);
    }

    /** Aktives Profil (Index in NAMES) */
    public static int active() {
        String p = MyticClient.config().perfProfile;
        for (int i = 0; i < OWN; i++) if (NAMES[i].equals(p)) return i;
        return OWN;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Opt vanilla(final OptionInstance option) {
        return new Opt() {
            public String get() {
                return FpsBoostModule.encode(option.get());
            }

            public void set(String value) {
                Object v = FpsBoostModule.decode(value, option.get());
                if (v != null && !v.equals(option.get())) option.set(v);
            }
        };
    }

    private static Opt bool(final BoolSetting s) {
        return new Opt() {
            public String get() {
                return String.valueOf(s.get());
            }

            public void set(String value) {
                s.set(Boolean.parseBoolean(value));
            }
        };
    }

    private static Opt mode(final ModeSetting s) {
        return new Opt() {
            public String get() {
                return s.get();
            }

            public void set(String value) {
                s.set(value);
            }
        };
    }

    private static Map<String, Opt> options(Minecraft mc) {
        Options o = mc.options;
        Map<String, Opt> out = new LinkedHashMap<String, Opt>();
        out.put("renderDistance", vanilla(o.renderDistance()));
        out.put("simulationDistance", vanilla(o.simulationDistance()));
        out.put("particles", vanilla(o.particles()));
        out.put("entityShadows", vanilla(o.entityShadows()));
        out.put("clouds", vanilla(o.cloudStatus()));
        out.put("biomeBlend", vanilla(o.biomeBlendRadius()));
        out.put("maxFps", vanilla(o.framerateLimit()));
        out.put("vsync", vanilla(o.enableVsync()));
        Map<String, OptionInstance<?>> gfx = new LinkedHashMap<String, OptionInstance<?>>();
        FpsCompat.perfGraphics(o, gfx);
        for (Map.Entry<String, OptionInstance<?>> e : gfx.entrySet()) out.put("gfx:" + e.getKey(), vanilla(e.getValue()));
        SimpleModules.Theme t = MyticClient.theme;
        if (t != null) {
            out.put("menuAnimations", bool(t.menuAnimations));
            out.put("hudAnimations", bool(t.hudAnimations));
            out.put("titleBackground", mode(t.titleBackground));
            out.put("menuBackground", mode(t.menuBackground));
        }
        return out;
    }

    /** Zielwerte eines Profils (fehlender Schlüssel = eigener Wert) */
    private static Map<String, String> values(int p, Map<String, Opt> opts) {
        Map<String, String> v = new LinkedHashMap<String, String>();
        int[] render = {6, 10, 16}, sim = {5, 8, 10}, blend = {0, 2, 5}, fps = {260, 144, 260};
        String[] particles = {"MINIMAL", "DECREASED", "ALL"}, clouds = {"OFF", "FAST", "FANCY"};
        v.put("renderDistance", String.valueOf(render[p]));
        v.put("simulationDistance", String.valueOf(sim[p]));
        v.put("particles", particles[p]);
        v.put("entityShadows", String.valueOf(p > 0));
        v.put("clouds", clouds[p]);
        v.put("biomeBlend", String.valueOf(blend[p]));
        v.put("maxFps", String.valueOf(fps[p]));
        v.put("vsync", String.valueOf(p == 2));
        for (String key : opts.keySet()) {
            if (!key.startsWith("gfx:")) continue;
            String g = FpsCompat.perfGraphicsValue(key.substring(4), p);
            if (g != null) v.put(key, g);
        }
        if (p == 0) {
            v.put("menuAnimations", "false");
            v.put("hudAnimations", "false");
            v.put("titleBackground", "Einfarbig dunkel");
            v.put("menuBackground", "Abdunkeln");
        } else if (p == 2) {
            v.put("menuAnimations", "true");
            v.put("hudAnimations", "true");
            v.put("menuBackground", "Unschärfe");
        }
        v.put(BOOST, String.valueOf(p == 0));
        return v;
    }

    /** Profil anwenden (OWN = eigene Werte zurück) */
    public static void apply(int p) {
        Config c = MyticClient.config();
        if (p == OWN && c.perfProfile == null) return;
        Minecraft mc = Minecraft.getInstance();
        FpsBoostModule boost = MyticClient.fpsBoost;
        boolean boostWasOn = boost != null && boost.enabled();
        if (boostWasOn) {
            boost.setEnabled(false);
            boost.tick(mc); // FPS-Boost gibt seine gemerkten Werte zurück, bevor das Profil sie setzt
        }
        Map<String, Opt> opts = options(mc);
        if (c.perfProfile == null || c.perfSaved.isEmpty()) {
            c.perfSaved.clear();
            for (Map.Entry<String, Opt> e : opts.entrySet()) c.perfSaved.put(e.getKey(), e.getValue().get());
            c.perfSaved.put(BOOST, String.valueOf(boostWasOn));
        }
        Map<String, String> target = new LinkedHashMap<String, String>(c.perfSaved);
        if (p != OWN) target.putAll(values(p, opts));
        for (Map.Entry<String, Opt> e : opts.entrySet()) {
            String v = target.get(e.getKey());
            if (v == null) continue;
            try {
                if (!v.equals(e.getValue().get())) e.getValue().set(v);
            } catch (RuntimeException ex) {
                MyticClient.LOG.warn("Leistungsprofil: {} nicht gesetzt", e.getKey(), ex);
            }
        }
        mc.options.save();
        if (boost != null && Boolean.parseBoolean(target.get(BOOST))) boost.setEnabled(true);
        if (p == OWN) {
            c.perfProfile = null;
            c.perfSaved.clear();
        } else {
            c.perfProfile = NAMES[p];
        }
        c.save();
    }
}
