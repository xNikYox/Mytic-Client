package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.Culling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS-Boost: Item-Stapel am Boden als ein Modell statt bis zu fünf (Klasse je nach Version verschieden) */
@Pseudo
@Mixin(targets = {"net.minecraft.client.renderer.entity.state.ItemClusterRenderState", "net.minecraft.client.renderer.entity.ItemEntityRenderer"})
public abstract class ItemAmountMixin {
    @Inject(method = "getRenderedAmount(I)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void mytic$one(int count, CallbackInfoReturnable<Integer> cir) {
        if (Culling.singleItemModel()) cir.setReturnValue(1);
    }
}
