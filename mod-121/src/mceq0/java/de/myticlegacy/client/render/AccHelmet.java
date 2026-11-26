package de.myticlegacy.client.render;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;

/** 1.21.2 und 1.21.3: Trägt die Figur etwas auf dem Kopf (Helm, Kürbis, Kopf)? (Feld heißt hier headItem) */
final class AccHelmet {
    private AccHelmet() {
    }

    static boolean wears(PlayerRenderState state) {
        return state.headItem != null && !state.headItem.isEmpty();
    }
}
