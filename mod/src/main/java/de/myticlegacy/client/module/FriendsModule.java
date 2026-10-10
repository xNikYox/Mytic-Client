package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.world.item.Items;

/** Einstellungen der Freundesliste. An = Freunde sehen, dass du online bist. */
public class FriendsModule extends Module {
    public final BoolSetting showServer;
    public final BoolSetting notify;

    public FriendsModule() {
        super("friends", "Freunde", "Freunde sehen, dass du online bist (Rechts-Shift → Freunde)", Category.CLIENT, () -> Items.PLAYER_HEAD, true);
        showServer = new BoolSetting(this, "showServer", "Server für Freunde anzeigen", true);
        notify = new BoolSetting(this, "notify", "Benachrichtigen, wenn Freunde online kommen", true);
    }
}
