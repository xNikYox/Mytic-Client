package de.myticlegacy.client.mixin;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /** Freelook: direkt nach dem Setzen der Spieler-Blickrichtung die Freelook-Richtung verwenden. */
    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", shift = At.Shift.AFTER))
    private void mytic$freelook(Level level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
        if (MyticClient.freelook.active()) setRotation(MyticClient.freelook.yaw(), MyticClient.freelook.pitch());
    }
}
