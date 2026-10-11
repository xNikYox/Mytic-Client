package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * Snaplook wie bei Lunar: Taste halten → Ansicht von vorne (oder normale Third-Person von hinten, einstellbar), loslassen →
 * zurück zur vorherigen Ansicht.
 */
public class SnaplookModule extends Module {
    private final ModeSetting perspective;
    private boolean active;
    private CameraType previous;

    public SnaplookModule() {
        super("snaplook", "Snaplook", "Taste V halten: dich selbst von vorne oder hinten sehen", Category.MECHANIK, () -> Items.ARMOR_STAND, true);
        perspective = new ModeSetting(this, "perspective", "Perspektive", "Vorne", "Vorne", "Hinten");
    }

    public void tick(Minecraft mc) {
        boolean want = enabled() && Compat.held(MyticClient.snaplookKey) && Compat.screen(mc) == null && mc.player != null && !MyticClient.freelook.active();
        if (want && !active) {
            previous = mc.options.getCameraType();
            mc.options.setCameraType(perspective.is("Hinten") ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
            active = true;
        } else if (!want && active) {
            mc.options.setCameraType(previous != null ? previous : CameraType.FIRST_PERSON);
            active = false;
        }
    }
}
