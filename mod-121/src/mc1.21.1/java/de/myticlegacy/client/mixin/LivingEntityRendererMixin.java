package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.Nametags;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.21 und 1.21.1: den eigenen Namen samt Nametag in F5 zeigen. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mytic$ownName(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Nametags.showOwn(entity)) cir.setReturnValue(true);
    }
}
