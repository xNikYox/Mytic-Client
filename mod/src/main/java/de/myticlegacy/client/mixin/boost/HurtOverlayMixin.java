package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.PvpModule;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PvP-Optik „Treffer-Farbe nur für Spieler“: getroffene Nicht-Spieler nutzen eine Zeile der Overlay-Textur, die rot bleibt */
@Mixin(LivingEntityRenderer.class)
public abstract class HurtOverlayMixin {
    @Inject(method = "getOverlayCoords(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)I", at = @At("RETURN"), cancellable = true, require = 0)
    private static void mytic$playersOnly(LivingEntityRenderState state, float white, CallbackInfoReturnable<Integer> cir) {
        if (state.hasRedOverlay && !(state instanceof AvatarRenderState) && PvpModule.playersOnly())
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(white), PvpModule.VANILLA_HURT_ROW));
    }
}
