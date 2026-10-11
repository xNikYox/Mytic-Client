package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.render.PhysicsCape;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21 und 1.21.1: Umhang-Physik statt des starren Umhangs (gleiche Bedingungen wie Minecraft). */
@Mixin(CapeLayer.class)
abstract class CapeLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void mytic$physics(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                               float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!MyticClient.capePhysics.enabled()) return;
        ci.cancel();
        if (player.isInvisible() || !player.isModelPartShown(PlayerModelPart.CAPE) || player.getSkin().capeTexture() == null
                || player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;
        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> parent = ((RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) this).getParentModel();
        PhysicsCape.render(pose, buffers, light, player.getId(), Mth.lerp(partialTick, player.xo, player.getX()), Mth.lerp(partialTick, player.yo, player.getY()),
                Mth.lerp(partialTick, player.zo, player.getZ()), Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot), parent.body,
                player.getSkin().capeTexture(), false);
    }
}
