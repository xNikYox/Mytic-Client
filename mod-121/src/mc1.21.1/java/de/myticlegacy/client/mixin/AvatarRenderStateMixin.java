package de.myticlegacy.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;

/** 1.21 und 1.21.1: kein Render-Zustand – leer (die MiniMe-Ebene liest den Spieler direkt). */
@Mixin(AbstractClientPlayer.class)
abstract class AvatarRenderStateMixin {
}
