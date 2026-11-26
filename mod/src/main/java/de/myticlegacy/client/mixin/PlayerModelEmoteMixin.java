package de.myticlegacy.client.mixin;

import de.myticlegacy.client.render.EmoteModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: Pose nach Minecrafts eigener Animation aufs Spielermodell legen (Hut, Ärmel und Hose hängen daran). */
@Mixin(PlayerModel.class)
abstract class PlayerModelEmoteMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void mytic$emote(AvatarRenderState state, CallbackInfo ci) {
        EmoteModel.applyFor((HumanoidModel<?>) (Object) this, state.id);
    }
}
