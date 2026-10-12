package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.Shields;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.2 und 1.21.3: Besitzer eines gehaltenen Items (Dritte-Person-Ansicht) für die Schild-Cosmetics merken. */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void mytic$owner(LivingEntityRenderState state, BakedModel model, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
                             MultiBufferSource buffers, int light, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (state instanceof PlayerRenderState player && mc.level != null) Shields.OWNER.set(mc.level.getEntity(player.id));
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void mytic$ownerDone(LivingEntityRenderState state, BakedModel model, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
                                 MultiBufferSource buffers, int light, CallbackInfo ci) {
        Shields.OWNER.remove();
    }
}
