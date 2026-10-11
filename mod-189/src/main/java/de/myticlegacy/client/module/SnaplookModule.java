package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;

/**
 * Snaplook wie bei Lunar: Taste halten → Ansicht von vorne (oder normale Third-Person von hinten, einstellbar), loslassen →
 * zurück zur vorherigen Ansicht.
 */
public class SnaplookModule extends Module {
    private final ModeSetting perspective;
    private boolean active;
    private int previous;

    public SnaplookModule() {
        super("snaplook", "Snaplook", "Taste V halten: dich selbst von vorne oder hinten sehen", Category.MECHANIK, () -> Items.armor_stand, true);
        perspective = new ModeSetting(this, "perspective", "Perspektive", "Vorne", "Vorne", "Hinten");
    }

    public void tick(Minecraft mc) {
        boolean want = enabled() && Compat.held(MyticClient.snaplookKey) && Compat.screen(mc) == null && mc.thePlayer != null && !MyticClient.freelook.active();
        if (want && !active) {
            previous = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = perspective.is("Hinten") ? 1 : 2;
            active = true;
        } else if (!want && active) {
            mc.gameSettings.thirdPersonView = previous;
            active = false;
        }
    }
}
