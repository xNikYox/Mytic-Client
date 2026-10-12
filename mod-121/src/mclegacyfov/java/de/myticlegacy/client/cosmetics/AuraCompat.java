package de.myticlegacy.client.cosmetics;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import org.joml.Vector3f;

/** 1.21 und 1.21.1: farbiger Staub (Farbe als Vektor). */
final class AuraCompat {
    private AuraCompat() {
    }

    static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), size);
    }
}
