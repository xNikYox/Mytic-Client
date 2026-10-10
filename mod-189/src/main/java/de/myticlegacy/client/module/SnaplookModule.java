package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;

/** Snaplook wie bei Lunar: Taste halten → Ansicht von vorne, loslassen → zurück zur vorherigen Ansicht. */
public class SnaplookModule extends Module {
    private boolean active;
    private int previous;

    public SnaplookModule() {
        super("snaplook", "Snaplook", "Taste V halten: dich selbst von vorne sehen", Category.MECHANIK, () -> Items.armor_stand, true);
    }

    public void tick(Minecraft mc) {
        boolean want = enabled() && Compat.held(MyticClient.snaplookKey) && Compat.screen(mc) == null && mc.thePlayer != null && !MyticClient.freelook.active();
        if (want && !active) {
            previous = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 2;
            active = true;
        } else if (!want && active) {
            mc.gameSettings.thirdPersonView = previous;
            active = false;
        }
    }
}
