package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.BackgroundFpsModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hintergrund-FPS ab 1.21.2: Bildraten-Begrenzung kommt aus dem FramerateLimitTracker */
@Mixin(targets = "com.mojang.blaze3d.platform.FramerateLimitTracker")
public abstract class BackgroundFpsMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void mytic$background(CallbackInfoReturnable<Integer> cir) {
        int limit = BackgroundFpsModule.limit(cir.getReturnValueI());
        if (limit != cir.getReturnValueI()) cir.setReturnValue(limit);
    }
}
