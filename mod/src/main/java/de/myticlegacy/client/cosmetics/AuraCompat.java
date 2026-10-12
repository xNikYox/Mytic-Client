package de.myticlegacy.client.cosmetics;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;

/** Farbiger Staub (ab 1.21.2: Farbe als int). */
final class AuraCompat {
    private AuraCompat() {
    }

    static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb, size);
    }
}
