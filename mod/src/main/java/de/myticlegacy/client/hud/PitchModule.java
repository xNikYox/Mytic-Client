package de.myticlegacy.client.hud;

import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Blickwinkel nach oben/unten (Pitch); zeigt „(fest)“, wenn die Pitch-Sperre an ist. Yaw ist eine eigene Anzeige (YawModule). */
public class PitchModule extends TextModule {
    private static PitchModule instance;
    public final BoolSetting decimals;

    public PitchModule() {
        super("pitch", "Pitch", "Blickwinkel nach oben/unten", () -> Items.SPYGLASS, false, 10000, 175, "Pitch", PitchModule::text);
        decimals = new BoolSetting(this, "decimals", "Nachkommastelle", true);
        instance = this;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, instance.decimals.get() ? "%.1f°" : "%.0f°", v);
    }

    private static String text(boolean preview) {
        if (instance == null) return "";
        Minecraft mc = Minecraft.getInstance();
        float pitch = preview || mc.player == null ? 12.5f : mc.player.getXRot();
        String s = fmt(pitch);
        if (de.myticlegacy.client.module.PitchLockModule.locked()) s += " (fest)";
        return s;
    }
}
