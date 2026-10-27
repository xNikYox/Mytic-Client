package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.Culling;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS-Boost: Truhen, Schilder & Co. nur in der Nähe und nicht hinter Wänden zeichnen (1.21.9+; ältere: leere Fassung) */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCullMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$cull(BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (Culling.cullBlockEntity(be)) cir.setReturnValue(null);
    }
}
