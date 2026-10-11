package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.MiniMeState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
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
