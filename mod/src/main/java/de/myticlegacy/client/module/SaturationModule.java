package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.world.item.Items;

/** Farbsättigung der Welt (HUD und Menüs bleiben unverändert) – Shader in GlSaturation, nur mit OpenGL */
public class SaturationModule extends Module {
    private static SaturationModule instance;
    public final SliderSetting saturation;

    public SaturationModule() {
        super("saturation", "Farbsättigung", "Farben der Welt kräftiger oder blasser (HUD und Menüs bleiben normal)", Category.VISUELL, () -> Items.GLOW_INK_SAC, false);
        saturation = new SliderSetting(this, "saturation", "Sättigung", 0, 200, 5, 130, " %");
        instance = this;
    }

    /** Sättigungs-Faktor (1 = unverändert) oder -1, wenn aus */
    public static float factor() {
        if (instance == null || !instance.enabled()) return -1;
        float f = (float) (instance.saturation.get() / 100.0);
        return Math.abs(f - 1f) < 0.01f ? -1 : f;
    }
}
