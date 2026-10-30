package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.TotemPopModule;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem-Pops: Entity-Ereignisse vom Server (35 = Totem verbraucht, 3 = Tod) an den Zähler weitergeben */
@Mixin(LivingEntity.class)
public abstract class TotemPopMixin {
    @Inject(method = "handleEntityEvent(B)V", at = @At("HEAD"), require = 0)
    private void mytic$event(byte id, CallbackInfo ci) {
        TotemPopModule.event((LivingEntity) (Object) this, id);
    }
}
