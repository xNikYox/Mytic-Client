package de.myticlegacy.client.mixin;

import de.myticlegacy.client.render.MiniMeLayer;
import de.myticlegacy.client.render.WingsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21 und 1.21.1: MiniMe- und Flügel-Ebene (Wind gibt es hier nicht, es fehlt der Render-Zustand für den Umhang). */
@Mixin(PlayerRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mytic$miniMeLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new MiniMeLayer((PlayerRenderer) (Object) this, context.getModelSet()));
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new WingsLayer((PlayerRenderer) (Object) this));
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new de.myticlegacy.client.render.AuraLayer((PlayerRenderer) (Object) this));
    }
}
