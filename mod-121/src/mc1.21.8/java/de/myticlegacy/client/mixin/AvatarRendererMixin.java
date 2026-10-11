package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.Cosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.2–1.21.8: Wind für Mytic-Umhänge (Name wie in 1.21.11, Ziel ist hier PlayerRenderer). */
@Mixin(PlayerRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "extractCapeState", at = @At("TAIL"))
    private static void mytic$wind(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        if (state.skin == null || !Cosmetics.isMytic(state.skin.capeTexture())) return;
        float[] wind = Cosmetics.wind(player.getId());
        state.capeLean += wind[0];
        state.capeLean2 += wind[1];
        state.capeFlap += wind[2];
    }
}
