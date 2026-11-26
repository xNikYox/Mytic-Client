package de.myticlegacy.client.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

/**
 * 1.8.9: „Beitreten“ – aktuelle Welt bzw. Server sauber verlassen (wie „Trennen“ im Pausenmenü) und über den normalen
 * Verbindungsbildschirm mit dem Server des Freundes verbinden.
 */
public final class JoinFlow {
    private JoinFlow() {
    }

    public static boolean join(String address, String friendName) {
        if (!JoinTarget.joinable(address)) return false;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen parent = new GuiMultiplayer(new GuiMainMenu());
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
        }
        mc.displayGuiScreen(new GuiConnecting(parent, mc, new ServerData("Server von " + friendName, address, false)));
        return true;
    }
}
