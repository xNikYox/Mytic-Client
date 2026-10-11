package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.MiniMeState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 1.21.2–1.21.8: MiniMe-Feld am PlayerRenderState (Name wie in 1.21.11). */
@Mixin(PlayerRenderState.class)
abstract class AvatarRenderStateMixin implements MiniMeState {
    @Unique
    private PlayerSkin mytic$miniMe;

    @Override
    public PlayerSkin mytic$miniMe() {
        return mytic$miniMe;
    }

    @Override
    public void mytic$setMiniMe(PlayerSkin skin) {
        mytic$miniMe = skin;
    }
}
