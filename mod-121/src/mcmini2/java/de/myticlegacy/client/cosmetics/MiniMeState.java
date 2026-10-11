package de.myticlegacy.client.cosmetics;

import net.minecraft.client.resources.PlayerSkin;

/** Zusatzfeld am Render-Zustand eines Spielers: Skins seiner MiniMes je Stelle (Reihenfolge wie MiniMeCore.POSITIONS, null = keiner). */
public interface MiniMeState {
    PlayerSkin[] mytic$miniMes();

    void mytic$setMiniMes(PlayerSkin[] skins);
}
