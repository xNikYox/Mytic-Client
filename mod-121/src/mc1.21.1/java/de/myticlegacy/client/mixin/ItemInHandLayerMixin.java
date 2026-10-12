package de.myticlegacy.client.mixin;

import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import org.spongepowered.asm.mixin.Mixin;

/** 1.21 und 1.21.1: nichts zu tun (der Besitzer kommt über ItemInHandRenderer.renderItem). */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
}
