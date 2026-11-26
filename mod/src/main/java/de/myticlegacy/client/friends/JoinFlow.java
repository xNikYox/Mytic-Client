package de.myticlegacy.client.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * „Beitreten“: aktuelle Welt bzw. aktuellen Server sauber verlassen (eigene Welt wird gespeichert) und mit dem Server
 * des Freundes verbinden – über den normalen Verbindungsbildschirm von Minecraft. Gilt für alle Fabric-Versionen.
 */
public final class JoinFlow {
    private JoinFlow() {
    }

    /** Nur aus dem Spiel-Thread (z. B. Klick im Freunde-Fenster). false = Adresse ungültig. */
    public static boolean join(String address, String friendName) {
        if (!JoinTarget.joinable(address) || !ServerAddress.isValidAddress(address)) return false;
        Minecraft mc = Minecraft.getInstance();
        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        if (mc.level != null) {
            boolean local = mc.isLocalServer();
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) connection.getConnection().disconnect(Component.translatable("multiplayer.status.quitting"));
            mc.disconnect(local ? new GenericMessageScreen(Component.translatable("menu.savingLevel")) : parent, false);
        }
        ServerData data = new ServerData("Server von " + friendName, address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(address), data, false, null);
        return true;
    }
}
