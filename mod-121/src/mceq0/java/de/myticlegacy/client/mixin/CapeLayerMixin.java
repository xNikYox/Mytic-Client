package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.render.PhysicsCape;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.item.equipment.EquipmentModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.2 und 1.21.3: Umhang-Physik statt des starren Umhangs (gleiche Bedingungen wie Minecraft). */
@Mixin(CapeLayer.class)
abstract class CapeLayerMixin {
    @Shadow
    private boolean hasLayer(ItemStack stack, EquipmentModel.LayerType type) {
        throw new AssertionError();
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/PlayerRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void mytic$physics(PoseStack pose, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (!MyticClient.capePhysics.enabled()) return;
        ci.cancel();
        if (state.isInvisible || !state.showCape || state.skin.capeTexture() == null || hasLayer(state.chestItem, EquipmentModel.LayerType.WINGS)) return;
        @SuppressWarnings("unchecked")
        PlayerModel parent = ((RenderLayer<PlayerRenderState, PlayerModel>) (Object) this).getParentModel();
        PhysicsCape.render(pose, buffers, light, state.id, state.x, state.y, state.z, state.bodyRot, parent.body, state.skin.capeTexture(),
                hasLayer(state.chestItem, EquipmentModel.LayerType.HUMANOID));
    }
}
