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
    private int appliedHit = Integer.MIN_VALUE;

    public PvpModule() {
        super("pvp", "PvP-Optik", "Treffer-Farbe, Blockrahmen, niedrigeres Feuer, tieferer Schild, Crit-Partikel", Category.VISUELL, () -> Items.NETHERITE_SWORD, false);
        hitColorOn = new BoolSetting(this, "hitColorOn", "Eigene Treffer-Farbe", true);
        hitColor = new ColorSetting(this, "hitColor", "Treffer-Farbe", 0xFF9B5CFF);
        outlineOn = new BoolSetting(this, "outlineOn", "Eigener Blockrahmen", true);
        outlineColor = new ColorSetting(this, "outlineColor", "Blockrahmen-Farbe", 0xFFFFFFFF);
        outlineWidth = new SliderSetting(this, "outlineWidth", "Blockrahmen-Dicke", 1, 8, 0.5, 2.5, " px");
        fireHeight = new SliderSetting(this, "fireHeight", "Feuer im Bild senken", 0, 60, 5, 30, " %");
        shieldLower = new SliderSetting(this, "shieldLower", "Schild senken", 0, 60, 5, 25, " %");
        critMultiplier = new SliderSetting(this, "critMultiplier", "Crit-Partikel", 0, 5, 1, 1, "×");
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

    /** Jeden Tick: Treffer-Farbe bei Änderung in die Overlay-Textur übernehmen */
    public void tick() {
        Integer c = hitColor();
        int want = c == null ? 0xB2FF0000 : (0xB2 << 24) | (c & 0xFFFFFF);
        if (want != appliedHit && PvpCompat.recolorHurt(want)) appliedHit = want;
    }
}
