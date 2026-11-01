package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Einstellungen für Mytic Games (Schach, X und O, Vier gewinnt, Schiffe versenken): Töne bei Herausforderung, Zug, Ende. */
public class GamesModule extends Module {
    private static GamesModule instance;
    public final BoolSetting soundInvite;
    public final BoolSetting soundTurn;
    public final BoolSetting soundEnd;

    public GamesModule() {
        super("games", "Spiele", "Mytic Games (/myticgame): Töne bei Herausforderung, eigenem Zug und Spielende", Category.CLIENT, () -> Items.BOOK, true);
        soundInvite = new BoolSetting(this, "soundInvite", "Ton bei Herausforderung", true);
        soundTurn = new BoolSetting(this, "soundTurn", "Ton wenn du dran bist", true);
        soundEnd = new BoolSetting(this, "soundEnd", "Ton bei Spielende", true);
        instance = this;
    }

    public enum Sound { INVITE, TURN, WIN, LOSE }

    /** Ton abspielen, wenn das Modul und der passende Schalter an sind */
    public static void play(Sound sound) {
        if (instance == null || !instance.enabled()) return;
        boolean on = switch (sound) {
            case INVITE -> instance.soundInvite.get();
            case TURN -> instance.soundTurn.get();
            default -> instance.soundEnd.get();
        };
        if (!on) return;
        switch (sound) {
            case INVITE -> { // heller Doppelton
                pling(1.6f);
                pling(2.0f);
            }
            case TURN -> pling(1.2f);
            case WIN -> {
                pling(1.4f);
                pling(1.9f);
            }
            case LOSE -> pling(0.6f);
        }
    }

    private static void pling(float pitch) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING, pitch));
    }
}
