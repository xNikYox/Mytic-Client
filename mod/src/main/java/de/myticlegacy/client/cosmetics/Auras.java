package de.myticlegacy.client.cosmetics;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Auren: dezente Partikel um Spieler mit Mytic-Aura, mit den Partikeln von Minecraft (je Aura eigenes Muster und eigene
 * Partikel-Arten, Staub in beliebigen Farben). Jeden Tick für alle Spieler in der Nähe; die eigene Aura nur, wenn man
 * sich von außen sieht (sonst stören die Partikel vor der Kamera).
 */
public final class Auras {
    private static final Map<Integer, double[]> PENDING = new HashMap<>();
    private static long counter;

    private Auras() {
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        double t = System.currentTimeMillis() / 1000.0;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.isInvisible() || p.distanceToSqr(mc.player) > 48 * 48) continue;
            boolean self = p == mc.player;
            if (self && mc.options.getCameraType() == CameraType.FIRST_PERSON) continue;
            CosmeticsCore.Aura aura = CosmeticsCore.auraOf(p.getUUID().toString().replace("-", ""), self);
            if (aura == null) continue;
            double[] acc = PENDING.computeIfAbsent(p.getId(), k -> new double[1]);
            acc[0] += aura.rate / 20.0;
            while (acc[0] >= 1) {
                acc[0] -= 1;
                String spec = aura.parts[(int) (counter % aura.parts.length)];
                ParticleOptions particle = particle(spec, t);
                double[] m = AuraMotion.next(aura.pattern, t, counter++);
                if (particle != null) mc.level.addParticle(particle, p.getX() + m[0], p.getY() + m[1], p.getZ() + m[2], m[3], m[4], m[5]);
            }
        }
        if (PENDING.size() > 200) PENDING.clear();
    }

    /** Partikel aus der Angabe im Katalog: Name, oder "dust:RRGGBB:größe" bzw. "dust:rainbow:größe". */
    static ParticleOptions particle(String spec, double t) {
        if (spec.startsWith("dust:")) {
            String[] p = spec.split(":");
            int rgb = "rainbow".equals(p[1]) ? AuraMotion.rainbow(t) : Integer.parseInt(p[1], 16);
            return AuraCompat.dust(rgb, p.length > 2 ? Float.parseFloat(p[2]) : 0.6f);
        }
        switch (spec) {
            case "end_rod": return ParticleTypes.END_ROD;
            case "enchant": return ParticleTypes.ENCHANT;
            case "heart": return ParticleTypes.HEART;
            case "soul": return ParticleTypes.SOUL;
            case "soul_fire_flame": return ParticleTypes.SOUL_FIRE_FLAME;
            case "small_flame": return ParticleTypes.SMALL_FLAME;
            case "snowflake": return ParticleTypes.SNOWFLAKE;
            case "cherry_leaves": return ParticleTypes.CHERRY_LEAVES;
            case "glow": return ParticleTypes.GLOW;
            case "electric_spark": return ParticleTypes.ELECTRIC_SPARK;
            case "note": return ParticleTypes.NOTE;
            case "wax_on": return ParticleTypes.WAX_ON;
            case "wax_off": return ParticleTypes.WAX_OFF;
            case "scrape": return ParticleTypes.SCRAPE;
            case "ash": return ParticleTypes.ASH;
            case "white_ash": return ParticleTypes.WHITE_ASH;
            case "spore_blossom_air": return ParticleTypes.SPORE_BLOSSOM_AIR;
            case "falling_spore_blossom": return ParticleTypes.FALLING_SPORE_BLOSSOM;
            case "falling_nectar": return ParticleTypes.FALLING_NECTAR;
            case "falling_obsidian_tear": return ParticleTypes.FALLING_OBSIDIAN_TEAR;
            case "totem": return ParticleTypes.TOTEM_OF_UNDYING;
            case "portal": return ParticleTypes.PORTAL;
            case "reverse_portal": return ParticleTypes.REVERSE_PORTAL;
            case "sculk_soul": return ParticleTypes.SCULK_SOUL;
            case "crimson_spore": return ParticleTypes.CRIMSON_SPORE;
            case "warped_spore": return ParticleTypes.WARPED_SPORE;
            case "happy_villager": return ParticleTypes.HAPPY_VILLAGER;
            case "nautilus": return ParticleTypes.NAUTILUS;
            case "bubble_pop": return ParticleTypes.BUBBLE_POP;
            case "crit": return ParticleTypes.CRIT;
            case "enchanted_hit": return ParticleTypes.ENCHANTED_HIT;
            case "firework": return ParticleTypes.FIREWORK;
            case "white_smoke": return ParticleTypes.WHITE_SMOKE;
            default: return null;
        }
    }
}
