package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.PvpModule;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PvP-Optik: Crit- und Schärfe-Partikel vervielfachen (0× = aus) */
@Mixin(ParticleEngine.class)
public abstract class CritMixin {
    private static boolean mytic$inside;

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$crits(Entity entity, ParticleOptions options, CallbackInfo ci) {
        if (mytic$inside || (options.getType() != ParticleTypes.CRIT && options.getType() != ParticleTypes.ENCHANTED_HIT)) return;
        int n = options.getType() == ParticleTypes.CRIT ? PvpModule.crits() : PvpModule.sharps();
        if (n == 1) return;
        if (n <= 0) {
            ci.cancel();
            return;
        }
        mytic$inside = true;
        try {
            for (int i = 1; i < n; i++) ((ParticleEngine) (Object) this).createTrackingEmitter(entity, options);
        } finally {
            mytic$inside = false;
        }
    }
}
