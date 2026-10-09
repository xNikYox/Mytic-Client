package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.MyticClient;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    /** No Hurt Cam: kein Kamerawackeln bei Schaden. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void mytic$noHurtCam(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (MyticClient.noHurtCam.enabled()) ci.cancel();
    }
}
