package de.myticlegacy.client.module;

import de.myticlegacy.client.Config;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Leistungsprofile (1.8.9): „Maximale FPS“, „Ausgewogen“, „Schön“ stellen Sichtweite, Partikel, Schatten, Wolken,
 * Grafik, Bildrate, VSync und die Mytic-Effekte ein; „Eigenes“ stellt die vorher gemerkten Werte wieder her.
 * 1.8.9 kennt keine Simulationsdistanz und keinen Biom-Übergang – diese Teile entfallen hier.
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
    /** Schlüssel mit „1.8.9.“, weil sich die Konfiguration mit den neuen Versionen teilt */
    private static final String P = "1.8.9.";

    private PerfProfiles() {
    }

    private interface Opt {
        String get();

        void set(String value);
    }

    public static int active() {
        String p = MyticClient.config().perfProfile189;
        for (int i = 0; i < OWN; i++) if (NAMES[i].equals(p)) return i;
        return OWN;
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

    private static int num(String v, int fallback) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Map<String, Opt> options(final GameSettings o) {
        Map<String, Opt> out = new LinkedHashMap<String, Opt>();
        out.put(P + "renderDistance", new Opt() {
            public String get() {
                return String.valueOf(o.renderDistanceChunks);
            }

            public void set(String v) {
                o.renderDistanceChunks = Math.max(2, Math.min(32, num(v, o.renderDistanceChunks)));
            }
        });
        out.put(P + "particles", new Opt() {
            public String get() {
                return String.valueOf(o.particleSetting);
            }

            public void set(String v) {
                o.particleSetting = Math.max(0, Math.min(2, num(v, o.particleSetting)));
            }
        });
        out.put(P + "entityShadows", new Opt() {
            public String get() {
                return String.valueOf(o.entityShadows);
            }

            public void set(String v) {
                o.entityShadows = Boolean.parseBoolean(v);
            }
        });
        out.put(P + "clouds", new Opt() {
            public String get() {
                return String.valueOf(o.clouds);
            }

            public void set(String v) {
                o.clouds = Math.max(0, Math.min(2, num(v, o.clouds)));
            }
        });
        out.put(P + "fancy", new Opt() {
            public String get() {
                return String.valueOf(o.fancyGraphics);
            }

            public void set(String v) {
                o.fancyGraphics = Boolean.parseBoolean(v);
            }
        });
        out.put(P + "maxFps", new Opt() {
            public String get() {
                return String.valueOf(o.limitFramerate);
            }

            public void set(String v) {
                o.limitFramerate = Math.max(10, Math.min(260, num(v, o.limitFramerate)));
            }
        });
        out.put(P + "vsync", new Opt() {
            public String get() {
                return String.valueOf(o.enableVsync);
            }

            public void set(String v) {
                o.enableVsync = Boolean.parseBoolean(v);
                try {
                    org.lwjgl.opengl.Display.setVSyncEnabled(o.enableVsync);
                } catch (RuntimeException ignored) {
                    // Fenster noch nicht bereit
                }
            }
        });
        SimpleModules.Theme t = MyticClient.theme;
        if (t != null) {
            out.put("menuAnimations", bool(t.menuAnimations));
            out.put("hudAnimations", bool(t.hudAnimations));
            out.put("titleBackground", mode(t.titleBackground));
            out.put("menuBackground", mode(t.menuBackground));
        }
        return out;
    }

    private static Map<String, String> values(int p) {
        Map<String, String> v = new LinkedHashMap<String, String>();
        int[] render = {6, 10, 16}, fps = {260, 144, 260}, particles = {2, 1, 0}, clouds = {0, 1, 2};
        v.put(P + "renderDistance", String.valueOf(render[p]));
        v.put(P + "particles", String.valueOf(particles[p]));
        v.put(P + "entityShadows", String.valueOf(p > 0));
        v.put(P + "clouds", String.valueOf(clouds[p]));
        v.put(P + "fancy", String.valueOf(p > 0));
        v.put(P + "maxFps", String.valueOf(fps[p]));
        v.put(P + "vsync", String.valueOf(p == 2));
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

    public static void apply(int p) {
        Config c = MyticClient.config();
        if (p == OWN && c.perfProfile189 == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GameSettings o = mc.gameSettings;
        FpsBoostModule boost = MyticClient.fpsBoost;
        boolean boostWasOn = boost != null && boost.enabled();
        if (boostWasOn) {
            boost.setEnabled(false);
            boost.tick(mc); // FPS-Boost gibt seine gemerkten Werte zurück, bevor das Profil sie setzt
        }
        int renderBefore = o.renderDistanceChunks;
        boolean fancyBefore = o.fancyGraphics;
        Map<String, Opt> opts = options(o);
        if (c.perfProfile189 == null || c.perfSaved189.isEmpty()) {
            c.perfSaved189.clear();
            for (Map.Entry<String, Opt> e : opts.entrySet()) c.perfSaved189.put(e.getKey(), e.getValue().get());
            c.perfSaved189.put(BOOST, String.valueOf(boostWasOn));
        }
        Map<String, String> target = new LinkedHashMap<String, String>(c.perfSaved189);
        if (p != OWN) target.putAll(values(p));
        for (Map.Entry<String, Opt> e : opts.entrySet()) {
            String v = target.get(e.getKey());
            if (v != null && !v.equals(e.getValue().get())) e.getValue().set(v);
        }
        if ((o.renderDistanceChunks != renderBefore || o.fancyGraphics != fancyBefore) && mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
        o.saveOptions();
        if (boost != null && Boolean.parseBoolean(target.get(BOOST))) boost.setEnabled(true);
        if (p == OWN) {
            c.perfProfile189 = null;
            c.perfSaved189.clear();
        } else {
            c.perfProfile189 = NAMES[p];
        }
        c.save();
    }
}
