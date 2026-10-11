package de.myticlegacy.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;

/** 1.21 und 1.21.1: ohne Wind (dort gibt es noch keinen Render-Zustand für den Umhang); nicht in der Mixin-Liste. */
@Mixin(AbstractClientPlayer.class)
abstract class AvatarRendererMixin {
}
