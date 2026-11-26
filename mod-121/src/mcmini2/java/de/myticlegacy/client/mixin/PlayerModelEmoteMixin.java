package de.myticlegacy.client.mixin;

import de.myticlegacy.client.render.EmoteModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.2–1.21.8: Emote-Pose nach setupAnim(PlayerRenderState) (Hut, Ärmel und Hose hängen an Kopf, Armen, Beinen). */
@Mixin(PlayerModel.class)
abstract class PlayerModelEmoteMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void mytic$emote(PlayerRenderState state, CallbackInfo ci) {
        EmoteModel.applyFor((HumanoidModel<?>) (Object) this, state.id);
    }
}
