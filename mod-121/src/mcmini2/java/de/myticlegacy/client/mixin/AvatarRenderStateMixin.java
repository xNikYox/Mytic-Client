package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.MiniMeState;
import de.myticlegacy.client.cosmetics.WingsState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 1.21.2–1.21.8: MiniMe- und Flügel-Feld am PlayerRenderState (Name wie in 1.21.11). */
@Mixin(PlayerRenderState.class)
abstract class AvatarRenderStateMixin implements MiniMeState, WingsState {
    @Unique
    private PlayerSkin[] mytic$miniMes;

    @Override
    public PlayerSkin[] mytic$miniMes() {
        return mytic$miniMes;
    }

    @Override
    public void mytic$setMiniMes(PlayerSkin[] skins) {
        mytic$miniMes = skins;
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
