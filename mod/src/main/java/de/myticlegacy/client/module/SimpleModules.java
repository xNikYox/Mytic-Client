package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.world.item.Items;

/** Kleine Module ohne eigene Logik-Klasse. Die Wirkung steckt in MyticClient bzw. den Mixins. */
public final class SimpleModules {
    private SimpleModules() {
    }

    public static final class TimeChanger extends Module {
        public final SliderSetting time;

        public TimeChanger() {
            super("timechanger", "Time Changer", "Eigene Tageszeit, nur für dich sichtbar", Category.VISUELL, () -> Items.CLOCK, false);
            time = new SliderSetting(this, "time", "Uhrzeit", 0, 24, 0.5, 6, " Uhr");
        }

        /** Minecraft-Tageszeit (0 = 6 Uhr morgens). */
        public long dayTime() {
            return Math.round(((time.get() - 6 + 24) % 24) * 1000);
        }
    }

    public static final class ScoreboardTweaks extends Module {
        public final SliderSetting scale;
        public final BoolSetting hide;

        public ScoreboardTweaks() {
            super("scoreboard", "Scoreboard", "Scoreboard verkleinern oder ausblenden", Category.VISUELL, () -> Items.PAINTING, false);
            scale = new SliderSetting(this, "scale", "Größe", 0.5, 1.5, 0.05, 0.85, "×");
            hide = new BoolSetting(this, "hide", "Ganz ausblenden", false);
        }
    }

    public static final class Theme extends Module {
        public final ColorSetting accent;
        public final BoolSetting customMenu;
        public final ModeSetting menuBackground;

        public Theme() {
            super("theme", "Design", "Akzentfarbe, Mytic-Hauptmenü und Menü-Hintergrund", Category.CLIENT, () -> Items.AMETHYST_SHARD, true);
            accent = new ColorSetting(this, "accent", "Akzentfarbe", 0xFF9B5CFF);
            customMenu = new BoolSetting(this, "customMenu", "Mytic-Hauptmenü", true);
            menuBackground = new ModeSetting(this, "menuBackground", "Menü-Hintergrund", "Unschärfe", "Unschärfe", "Abdunkeln", "Aus");
        }
    }

    public static Module toggleSprint() {
        return new Module("togglesprint", "Toggle Sprint", "Automatisch sprinten, solange du nach vorn läufst", Category.MECHANIK, () -> Items.LEATHER_BOOTS, false);
    }

    public static Module toggleSneak() {
        return new Module("togglesneak", "Toggle Sneak", "Schleichen per Tastendruck an/aus statt halten", Category.MECHANIK, () -> Items.CHAINMAIL_BOOTS, false);
    }

    public static Module fullbright() {
        return new Module("fullbright", "Fullbright", "Alles hell, auch in Höhlen und nachts", Category.VISUELL, () -> Items.TORCH, false);
    }

    public static Module clearWeather() {
        return new Module("clearweather", "Klares Wetter", "Kein Regen und kein Gewitter (nur für dich)", Category.VISUELL, () -> Items.WATER_BUCKET, false);
    }

    public static Module noHurtCam() {
        return new Module("nohurtcam", "No Hurt Cam", "Kein Wackeln der Kamera bei Schaden", Category.VISUELL, () -> Items.SHIELD, false);
    }

    public static Module hideBossbar() {
        return new Module("bossbar", "Bossbar ausblenden", "Blendet die Bossleiste oben aus", Category.VISUELL, () -> Items.DRAGON_BREATH, false);
    }
}
