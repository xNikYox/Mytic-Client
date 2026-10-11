package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.render.PhysicsCape;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Umhang-Physik: statt des starren Umhangs die Streifen-Kette zeichnen (gleiche Bedingungen wie Minecraft). */
@Mixin(CapeLayer.class)
abstract class CapeLayerMixin {
    @Shadow
    private boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType type) {
        throw new AssertionError();
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void mytic$physics(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (!MyticClient.capePhysics.enabled()) return;
        ci.cancel();
        if (state.isInvisible || !state.showCape || state.skin.cape() == null || hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) return;
        @SuppressWarnings("unchecked")
        PlayerModel parent = ((RenderLayer<AvatarRenderState, PlayerModel>) (Object) this).getParentModel();
        PhysicsCape.submit(pose, collector, light, state, parent, state.skin.cape().texturePath(),
                hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID));
    }
}
