package de.myticlegacy.client.cosmetics;

/** Zusatzfeld am Render-Zustand eines Spielers: seine Accessoires {kopf, hals} (null = keine). */
public interface AccState {
    CosmeticsCore.Acc[] mytic$acc();

    void mytic$setAcc(CosmeticsCore.Acc[] acc);
}
