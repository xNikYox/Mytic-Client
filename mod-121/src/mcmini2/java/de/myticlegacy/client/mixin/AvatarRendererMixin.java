package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.Cosmetics;
import de.myticlegacy.client.cosmetics.MiniMe;
import de.myticlegacy.client.cosmetics.MiniMeState;
import de.myticlegacy.client.cosmetics.WingsState;
import de.myticlegacy.client.render.MiniMeLayer;
import de.myticlegacy.client.render.WingsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.2–1.21.8: Wind für Mytic-Umhänge und MiniMe (Name wie in 1.21.11, Ziel ist hier PlayerRenderer). */
@Mixin(PlayerRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "extractCapeState", at = @At("TAIL"))
    private static void mytic$wind(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        ((MiniMeState) state).mytic$setMiniMes(MiniMe.skinsFor(player));
        ((WingsState) state).mytic$setWings(Cosmetics.wingsFor(player));
        if (state.skin == null || !Cosmetics.isMytic(state.skin.capeTexture())) return;
        float[] wind = Cosmetics.wind(player.getId());
        state.capeLean += wind[0];
        state.capeLean2 += wind[1];
        state.capeFlap += wind[2];
    }

    /** Nametag: eigene Zeile über dem Namen (am Ende, wenn Name und Punkte feststehen). */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("TAIL"))
    private void mytic$nametag(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        net.minecraft.network.chat.Component[] lines = de.myticlegacy.client.cosmetics.Nametags.decorate(player, state.nameTag, state.scoreText);
        if (lines != null) {
            state.nameTag = lines[0];
            state.scoreText = lines[1];
        }
    }

    /** MiniMe-Ebene (sitzt auf der Schulter) und Flügel anmelden. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mytic$miniMeLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new MiniMeLayer((PlayerRenderer) (Object) this, context.getModelSet()));
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new WingsLayer((PlayerRenderer) (Object) this));
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new de.myticlegacy.client.render.AuraLayer((PlayerRenderer) (Object) this));
    }
}
