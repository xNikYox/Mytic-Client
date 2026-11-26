package de.myticlegacy.client.render;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;

/** 1.21.4–1.21.8: Trägt die Figur etwas auf dem Kopf (Helm, Kürbis, Kopf)? */
final class AccHelmet {
    private AccHelmet() {
    }

    static boolean wears(PlayerRenderState state) {
        return state.headEquipment != null && !state.headEquipment.isEmpty();
    }
}
