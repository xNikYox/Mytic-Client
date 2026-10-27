package de.myticlegacy.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Kurze Meldung über der Hotbar (26.x) */
public final class Msg {
    private Msg() {
    }

    public static void actionBar(String text) {
        Minecraft.getInstance().gui.setOverlayMessage(Component.literal(text), false);
    }
}
