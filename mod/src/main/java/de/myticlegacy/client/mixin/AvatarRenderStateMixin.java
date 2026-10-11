package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.MiniMeState;
import de.myticlegacy.client.cosmetics.WingsState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
abstract class AvatarRenderStateMixin implements MiniMeState, WingsState {
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

    @Unique
    private String mytic$miniMePos;

    @Override
    public String mytic$miniMePos() {
        return mytic$miniMePos;
    }

    @Override
    public void mytic$setMiniMePos(String pos) {
        mytic$miniMePos = pos;
    }

    @Unique
    private String mytic$wings;

    @Override
    public String mytic$wings() {
        return mytic$wings;
    }

    @Override
    public void mytic$setWings(String id) {
        mytic$wings = id;
    }
}
