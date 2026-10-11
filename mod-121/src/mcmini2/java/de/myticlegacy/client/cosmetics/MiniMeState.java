package de.myticlegacy.client.cosmetics;

import net.minecraft.client.resources.PlayerSkin;

/** Zusatzfeld am Render-Zustand eines Spielers: Skin seines MiniMe (null = keiner). */
public interface MiniMeState {
    PlayerSkin mytic$miniMe();

    void mytic$setMiniMe(PlayerSkin skin);
}
