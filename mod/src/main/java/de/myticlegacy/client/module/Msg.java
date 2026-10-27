package de.myticlegacy.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Kurze Meldung über der Hotbar (versionsabhängig; 26.x hat eine eigene Fassung) */
public final class Msg {
    private Msg() {
    }

    public static void actionBar(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }
}
