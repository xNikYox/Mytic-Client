package de.myticlegacy.client.cosmetics;

/** Zusatzfeld am Render-Zustand eines Spielers: seine Flügel (null = keine). */
public interface WingsState {
    String mytic$wings();

    void mytic$setWings(String id);
}
