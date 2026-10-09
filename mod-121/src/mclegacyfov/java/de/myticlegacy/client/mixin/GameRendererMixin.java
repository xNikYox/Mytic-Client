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

/** 1.21 und 1.21.1: getFov liefert dort noch double. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void mytic$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (useFovSetting) {
            cir.setReturnValue((double) MyticClient.zoom.apply((float) cir.getReturnValueD()));
        }
    }

    /** No Hurt Cam: kein Kamerawackeln bei Schaden. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void mytic$noHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (MyticClient.noHurtCam.enabled()) ci.cancel();
    }
}
