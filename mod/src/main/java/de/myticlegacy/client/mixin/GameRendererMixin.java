package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void mytic$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (useFovSetting) {
            cir.setReturnValue(MyticClient.zoom.apply(cir.getReturnValueF()));
        }
    }

    /** No Hurt Cam: kein Kamerawackeln bei Schaden. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void mytic$noHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (MyticClient.noHurtCam.enabled()) ci.cancel();
    }
}
