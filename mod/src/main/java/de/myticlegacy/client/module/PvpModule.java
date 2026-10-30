package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.world.item.Items;

/** PvP-Optik: Treffer-Farbe, Blockrahmen, niedrigeres Feuer, tieferer Schild, Crit-Partikel */
public class PvpModule extends Module {
    private static PvpModule instance;
    public final BoolSetting hitColorOn;
    public final ColorSetting hitColor;
    public final BoolSetting outlineOn;
    public final ColorSetting outlineColor;
    public final SliderSetting outlineWidth;
    public final SliderSetting fireHeight;
    public final SliderSetting shieldLower;
    public final SliderSetting critMultiplier;
    public final SliderSetting sharpMultiplier;
    public final de.myticlegacy.client.setting.ModeSetting hitTarget;
    private int appliedHit = Integer.MIN_VALUE;

    public PvpModule() {
        super("pvp", "PvP-Optik", "Treffer-Farbe, Blockrahmen, niedrigeres Feuer, tieferer Schild, Crit-Partikel", Category.VISUELL, () -> Items.NETHERITE_SWORD, false);
        hitColorOn = new BoolSetting(this, "hitColorOn", "Eigene Treffer-Farbe", true);
        hitColor = new ColorSetting(this, "hitColor", "Treffer-Farbe", 0xFF9B5CFF);
        hitTarget = new de.myticlegacy.client.setting.ModeSetting(this, "hitTarget", "Treffer-Farbe für", "Alle", "Alle", "Nur Spieler");
        outlineOn = new BoolSetting(this, "outlineOn", "Eigener Blockrahmen", true);
        outlineColor = new ColorSetting(this, "outlineColor", "Blockrahmen-Farbe", 0xFFFFFFFF);
        outlineWidth = new SliderSetting(this, "outlineWidth", "Blockrahmen-Dicke", 1, 8, 0.5, 2.5, " px");
        fireHeight = new SliderSetting(this, "fireHeight", "Feuer im Bild senken", 0, 60, 5, 30, " %");
        shieldLower = new SliderSetting(this, "shieldLower", "Schild senken", 0, 60, 5, 25, " %");
        critMultiplier = new SliderSetting(this, "critMultiplier", "Crit-Partikel", 0, 10, 1, 1, "×");
        sharpMultiplier = new SliderSetting(this, "sharpMultiplier", "Schärfe-Partikel", 0, 10, 1, 1, "×");
        instance = this;
    }

    private static boolean on() {
        return instance != null && instance.enabled();
    }

    /** Treffer-Farbe (ARGB) oder null = Minecraft-Rot */
    public static Integer hitColor() {
        return on() && instance.hitColorOn.get() ? instance.hitColor.get() : null;
    }

    public static Integer outlineColor() {
        return on() && instance.outlineOn.get() ? instance.outlineColor.get() : null;
    }

    public static float outlineWidth(float vanilla) {
        return on() && instance.outlineOn.get() ? instance.outlineWidth.floatValue() : vanilla;
    }

    /** Verschiebung für Feuer (Anteil der Bildhöhe, 0 = normal) */
    public static float fireOffset() {
        return on() ? (float) (instance.fireHeight.get() / 100.0) : 0;
    }

    public static float shieldOffset() {
        return on() ? (float) (instance.shieldLower.get() / 100.0) : 0;
    }

    /** Crit-Partikel-Faktor (1 = normal) */
    public static int crits() {
        return on() ? instance.critMultiplier.intValue() : 1;
    }

    /** Schärfe-Partikel-Faktor (1 = normal) */
    public static int sharps() {
        return on() ? instance.sharpMultiplier.intValue() : 1;
    }

    /** Eigene Treffer-Farbe nur für Spieler – andere Wesen bleiben rot (HurtOverlayMixin) */
    public static boolean playersOnly() {
        return hitColor() != null && instance.hitTarget.is("Nur Spieler");
    }

    /** Zeile der Overlay-Textur für getroffene Nicht-Spieler, wenn „Nur Spieler“ an ist (dort bleibt Minecraft-Rot) */
    public static final int VANILLA_HURT_ROW = 6;

    /** Jeden Tick: Treffer-Farbe bei Änderung in die Overlay-Textur übernehmen */
    public void tick() {
        Integer c = hitColor();
        int want = c == null ? 0xB2FF0000 : (0xB2 << 24) | (c & 0xFFFFFF);
        boolean split = playersOnly();
        int key = want ^ (split ? 0x5A5A5A : 0);
        if (key != appliedHit && PvpCompat.recolorHurt(want, split)) appliedHit = key;
    }
}
