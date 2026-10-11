package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.Cosmetics;
import de.myticlegacy.client.cosmetics.MiniMe;
import de.myticlegacy.client.cosmetics.MiniMeState;
import de.myticlegacy.client.render.MiniMeLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
        ((MiniMeState) state).mytic$setMiniMe(MiniMe.skinFor(entity));
        ((de.myticlegacy.client.cosmetics.WingsState) state).mytic$setWings(Cosmetics.wingsFor(entity));
        if (state.skin == null || !Cosmetics.isMytic(state.skin.cape())) return;
        float[] wind = Cosmetics.wind(entity.hashCode());
        state.capeLean += wind[0];
        state.capeLean2 += wind[1];
        state.capeFlap += wind[2];
    }

    /** MiniMe-Ebene anmelden (sitzt auf der Schulter). */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mytic$miniMeLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) this).mytic$addLayer(new MiniMeLayer((AvatarRenderer) (Object) this, context.getModelSet()));
        ((LivingEntityRendererInvoker) this).mytic$addLayer(new de.myticlegacy.client.render.WingsLayer((AvatarRenderer) (Object) this));
    }
}
