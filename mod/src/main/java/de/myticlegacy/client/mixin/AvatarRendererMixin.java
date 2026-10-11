package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.Cosmetics;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
    /** Mytic-Umhänge schwingen zusätzlich im Wind (auch im Stehen). */
    @Inject(method = "extractCapeState", at = @At("TAIL"))
    private void mytic$wind(@org.spongepowered.asm.mixin.injection.Coerce Object entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (state.skin == null || !Cosmetics.isMytic(state.skin.cape())) return;
        float[] wind = Cosmetics.wind(entity.hashCode());
        state.capeLean += wind[0];
        state.capeLean2 += wind[1];
        state.capeFlap += wind[2];
    }
}
