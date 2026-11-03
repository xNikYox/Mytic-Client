package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.BackgroundFpsModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hintergrund-FPS für 1.21 und 1.21.1 (dort liegt die Bildraten-Begrenzung noch in Minecraft); später ohne Wirkung */
@Mixin(Minecraft.class)
public abstract class BackgroundFpsLegacyMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void mytic$background(CallbackInfoReturnable<Integer> cir) {
        int limit = BackgroundFpsModule.limit(cir.getReturnValueI());
        if (limit != cir.getReturnValueI()) cir.setReturnValue(limit);
    }
}
