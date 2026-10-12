package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.myticlegacy.client.cosmetics.Shields;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21–1.21.3: Schilde mit Mytic-Cosmetic mit der animierten Textur zeichnen (statt Holz und Bannermuster). */
@Mixin(BlockEntityWithoutLevelRenderer.class)
abstract class ShieldSpecialRendererMixin {
    @Shadow
    private ShieldModel shieldModel;

    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void mytic$draw(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (!stack.is(Items.SHIELD)) return;
        String id = Shields.forOwner();
        if (id == null) return;
        ci.cancel();
        VertexConsumer buffer = buffers.getBuffer(RenderType.entitySolid(Shields.texture(id)));
        pose.pushPose();
        pose.scale(1f, -1f, -1f);
        shieldModel.handle().render(pose, buffer, light, overlay);
        shieldModel.plate().render(pose, buffer, light, overlay);
        pose.popPose();
    }
}
