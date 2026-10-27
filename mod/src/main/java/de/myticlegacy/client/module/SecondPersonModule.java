package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * 2nd-Person-Taste: direkt in die Ansicht von vorne. Umschalten (drücken = an, nochmal = zurück) oder Halten
 * (nur solange gedrückt), einstellbar.
 */
public class SecondPersonModule extends Module {
    public final ModeSetting mode;
    private boolean active;
    private CameraType previous;

    public SecondPersonModule() {
        super("secondperson", "2nd Person", "Taste: direkt in die Ansicht von vorne (umschalten oder halten)", Category.MECHANIK, () -> Items.PLAYER_HEAD, true);
        mode = new ModeSetting(this, "mode", "Modus", "Umschalten", "Umschalten", "Halten");
    }

    private void on(Minecraft mc) {
        previous = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        active = true;
    }

    private void off(Minecraft mc) {
        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT)
            mc.options.setCameraType(previous != null && previous != CameraType.THIRD_PERSON_FRONT ? previous : CameraType.FIRST_PERSON);
        active = false;
    }

    public void tick(Minecraft mc) {
        boolean usable = enabled() && Compat.screen(mc) == null && mc.player != null;
        // mit F5 o. Ä. weggeschaltet: nichts mehr zurückstellen
        if (active && mc.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) active = false;
        if (mode.is("Halten")) {
            while (MyticClient.secondPersonKey.consumeClick()) ; // Klicks verwerfen, zählt nur Halten
            boolean down = usable && Compat.held(MyticClient.secondPersonKey);
            if (down && !active) on(mc);
            else if (!down && active) off(mc);
        } else {
            int presses = 0;
            while (MyticClient.secondPersonKey.consumeClick()) presses++;
            if (!usable) return;
            for (int i = 0; i < presses; i++) {
                if (active) off(mc);
                else on(mc);
            }
        }
    }
}
