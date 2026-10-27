package de.myticlegacy.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Kurze Meldung über der Hotbar (26.2) */
public final class Msg {
    private Msg() {
    }

    public static void actionBar(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(text));
    }
}
