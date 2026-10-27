package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.Culling;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS-Boost (1.21 – 1.21.8): Truhen, Schilder & Co. nur in der Nähe und nicht hinter Wänden zeichnen */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCullMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$cull(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        if (Culling.cullBlockEntity(be)) ci.cancel();
    }
}
