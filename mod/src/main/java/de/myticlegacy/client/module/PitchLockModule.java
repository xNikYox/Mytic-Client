package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Pitch-Sperre: Taste rastet den Blick nach oben/unten ein (aktueller Winkel oder fester Wert), z. B. zum Bridgen.
 * Links/rechts drehen geht weiter. Nochmal drücken löst die Sperre.
 */
public class PitchLockModule extends Module {
    private static PitchLockModule instance;
    public final ModeSetting angleMode;
    public final SliderSetting angle;
    private boolean locked;

    public PitchLockModule() {
        super("pitchlock", "Pitch-Sperre", "Taste: Blick nach oben/unten einrasten (aktueller oder fester Winkel)", Category.MECHANIK, () -> Items.SPYGLASS, true);
        angleMode = new ModeSetting(this, "angleMode", "Winkel", "Aktueller Blick", "Aktueller Blick", "Fester Wert");
        angle = new SliderSetting(this, "angle", "Fester Winkel", -90, 90, 0.5, 78, "°");
        instance = this;
    }

    /** Für den Entity-Mixin: vertikale Mausbewegung ignorieren? */
    public static boolean locked() {
        return instance != null && instance.locked && instance.enabled();
    }

    public void toggle(Minecraft mc) {
        if (!enabled() || mc.player == null) return;
        locked = !locked;
        if (locked && angleMode.is("Fester Wert")) {
            mc.player.setXRot(angle.floatValue());
            mc.player.xRotO = angle.floatValue();
        }
        Msg.actionBar(locked ? "Pitch gesperrt: " + String.format(Locale.ROOT, "%.1f°", mc.player.getXRot()) : "Pitch frei");
    }

    public void tick(Minecraft mc) {
        if (locked && (mc.player == null || !enabled())) locked = false;
    }
}
