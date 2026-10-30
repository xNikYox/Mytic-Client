package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.PvpModule;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (1.21 – 1.21.1, noch ohne Render-States) PvP-Optik „Treffer-Farbe nur für Spieler“: Nicht-Spieler bleiben rot */
@Mixin(LivingEntityRenderer.class)
public abstract class HurtOverlayMixin {
    @Inject(method = "getOverlayCoords(Lnet/minecraft/world/entity/LivingEntity;F)I", at = @At("RETURN"), cancellable = true, require = 0)
    private static void mytic$playersOnly(LivingEntity entity, float white, CallbackInfoReturnable<Integer> cir) {
        if ((entity.hurtTime > 0 || entity.deathTime > 0) && !(entity instanceof Player) && PvpModule.playersOnly())
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(white), PvpModule.VANILLA_HURT_ROW));
    }
}
