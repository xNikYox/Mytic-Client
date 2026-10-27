package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.Culling;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS-Boost: weit entfernte Mobs nur jeden 4. Tick berechnen */
@Mixin(ClientLevel.class)
public abstract class ClientLevelTickMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$throttle(Entity entity, CallbackInfo ci) {
        if (Culling.skipTick(entity)) ci.cancel();
    }
}
