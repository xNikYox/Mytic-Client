package de.myticlegacy.client.mixin;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
    /** Freelook: Mausbewegung dreht nur die Kamera, nicht den Spieler. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void mytic$freelook(double yRot, double xRot, CallbackInfo ci) {
        if (MyticClient.freelook.active() && (Object) this == Minecraft.getInstance().player) {
            MyticClient.freelook.turn(yRot, xRot);
            ci.cancel();
        }
    }

    /** Pitch-Sperre: vertikale Mausbewegung des eigenen Spielers ignorieren */
    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double mytic$pitchLock(double xRot) {
        return de.myticlegacy.client.module.PitchLockModule.locked() && (Object) this == Minecraft.getInstance().player ? 0 : xRot;
    }
}
