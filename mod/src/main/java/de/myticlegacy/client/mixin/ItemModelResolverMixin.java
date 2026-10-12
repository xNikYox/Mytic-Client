package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.Shields;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Schild-Cosmetics: Besitzer eines Items merken, solange es aufgelöst wird (in der Hand, Ego-Ansicht, am Körper). */
@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void mytic$owner(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        Shields.OWNER.set(owner);
    }

    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void mytic$ownerDone(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        Shields.OWNER.remove();
    }
}
