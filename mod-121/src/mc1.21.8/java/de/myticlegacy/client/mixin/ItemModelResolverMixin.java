package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.Shields;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.4–1.21.8: Besitzer eines Items merken, solange es aufgelöst wird (Schild-Cosmetics). */
@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void mytic$owner(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, LivingEntity owner, int seed, CallbackInfo ci) {
        Shields.OWNER.set(owner);
    }

    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void mytic$ownerDone(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, LivingEntity owner, int seed, CallbackInfo ci) {
        Shields.OWNER.remove();
    }
}
