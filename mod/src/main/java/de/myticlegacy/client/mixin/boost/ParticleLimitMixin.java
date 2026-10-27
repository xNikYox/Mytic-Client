package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.Culling;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS-Boost: höchstens so viele neue Partikel pro Sekunde wie eingestellt */
@Mixin(ParticleEngine.class)
public abstract class ParticleLimitMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$limit(Particle particle, CallbackInfo ci) {
        if (Culling.dropParticle()) ci.cancel();
    }
}
