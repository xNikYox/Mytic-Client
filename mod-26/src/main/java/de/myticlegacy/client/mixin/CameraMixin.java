package de.myticlegacy.client.mixin;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /** Zoom: in 26.x berechnet die Kamera das Sichtfeld selbst. */
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void mytic$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(MyticClient.zoom.apply(cir.getReturnValueF()));
    }

    /** Freelook: nach dem Ausrichten an der Spieler-Blickrichtung die Freelook-Richtung verwenden. */
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void mytic$freelook(float partialTick, CallbackInfo ci) {
        if (MyticClient.freelook.active()) setRotation(MyticClient.freelook.yaw(), MyticClient.freelook.pitch());
    }
}
