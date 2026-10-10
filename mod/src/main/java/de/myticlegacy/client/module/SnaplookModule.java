package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Snaplook wie bei Lunar: Taste halten → Ansicht von vorne, loslassen → zurück zur vorherigen Ansicht. */
public class SnaplookModule extends Module {
    private boolean active;
    private CameraType previous;

    public SnaplookModule() {
        super("snaplook", "Snaplook", "Taste V halten: dich selbst von vorne sehen", Category.MECHANIK, () -> Items.ARMOR_STAND, true);
    }

    public void tick(Minecraft mc) {
        boolean want = enabled() && Compat.held(MyticClient.snaplookKey) && Compat.screen(mc) == null && mc.player != null && !MyticClient.freelook.active();
        if (want && !active) {
            previous = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            active = true;
        } else if (!want && active) {
            mc.options.setCameraType(previous != null ? previous : CameraType.FIRST_PERSON);
            active = false;
        }
    }
}
