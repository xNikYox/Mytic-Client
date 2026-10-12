package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.Shields;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21–1.21.3: Besitzer eines Items in der Hand merken, solange es gezeichnet wird (Schild-Cosmetics). */
@Mixin(ItemInHandRenderer.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void mytic$owner(LivingEntity owner, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        Shields.OWNER.set(owner);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void mytic$ownerDone(LivingEntity owner, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        Shields.OWNER.remove();
    }
}
