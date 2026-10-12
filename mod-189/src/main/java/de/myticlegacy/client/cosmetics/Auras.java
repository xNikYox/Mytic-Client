package de.myticlegacy.client.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * Auren für 1.8.9: dezente Partikel um Spieler mit Mytic-Aura. Neuere Partikel gibt es hier nicht; sie werden auf die
 * ähnlichsten aus 1.8.9 abgebildet, Farben über farbigen Redstone-Staub.
 */
public final class Auras {
    private static final Map<Integer, double[]> PENDING = new HashMap<Integer, double[]>();
    private static long counter;

    private Auras() {
    }

    public static void tick(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.isGamePaused()) return;
        double t = System.currentTimeMillis() / 1000.0;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p.isInvisible() || p.getDistanceSqToEntity(mc.thePlayer) > 48 * 48) continue;
            boolean self = p == mc.thePlayer;
            if (self && mc.gameSettings.thirdPersonView == 0) continue;
            CosmeticsCore.Aura aura = CosmeticsCore.auraOf(p.getUniqueID().toString().replace("-", ""), self);
            if (aura == null) continue;
            double[] acc = PENDING.get(p.getEntityId());
            if (acc == null) PENDING.put(p.getEntityId(), acc = new double[1]);
            acc[0] += aura.rate / 20.0;
            while (acc[0] >= 1) {
                acc[0] -= 1;
                String spec = aura.parts[(int) (counter % aura.parts.length)];
                double[] m = AuraMotion.next(aura.pattern, t, counter++);
                spawn(mc, spec, t, p.posX + m[0], p.posY + m[1], p.posZ + m[2], m[3], m[4], m[5]);
            }
        }
        if (PENDING.size() > 200) PENDING.clear();
    }

    private static void spawn(Minecraft mc, String spec, double t, double x, double y, double z, double vx, double vy, double vz) {
        int rgb = -1;
        EnumParticleTypes type;
        if (spec.startsWith("dust:")) {
            String c = spec.split(":")[1];
            rgb = "rainbow".equals(c) ? AuraMotion.rainbow(t) : Integer.parseInt(c, 16);
            type = EnumParticleTypes.REDSTONE;
        } else {
            switch (spec) {
                case "end_rod": case "firework": type = EnumParticleTypes.FIREWORKS_SPARK; break;
                case "enchant": type = EnumParticleTypes.ENCHANTMENT_TABLE; break;
                case "heart": type = EnumParticleTypes.HEART; break;
                case "note": type = EnumParticleTypes.NOTE; break;
                case "small_flame": type = EnumParticleTypes.FLAME; break;
                case "portal": case "reverse_portal": case "falling_obsidian_tear": type = EnumParticleTypes.PORTAL; break;
                case "happy_villager": type = EnumParticleTypes.VILLAGER_HAPPY; break;
                case "crit": type = EnumParticleTypes.CRIT; break;
                case "glow": case "electric_spark": case "scrape": case "enchanted_hit": type = EnumParticleTypes.CRIT_MAGIC; break;
                case "ash": type = EnumParticleTypes.TOWN_AURA; break;
                case "white_ash": case "snowflake": rgb = 0xF4F8FF; type = EnumParticleTypes.REDSTONE; break;
                case "white_smoke": case "bubble_pop": type = EnumParticleTypes.CLOUD; break;
                case "falling_nectar": type = EnumParticleTypes.DRIP_LAVA; break;
                case "cherry_leaves": rgb = 0xFFB3CF; type = EnumParticleTypes.REDSTONE; break;
                case "soul": case "soul_fire_flame": case "sculk_soul": rgb = 0x6FE8FF; type = EnumParticleTypes.REDSTONE; break;
                case "wax_on": case "wax_off": rgb = 0xFFCF6A; type = EnumParticleTypes.REDSTONE; break;
                case "crimson_spore": rgb = 0xD84A6A; type = EnumParticleTypes.REDSTONE; break;
                case "warped_spore": case "spore_blossom_air": case "falling_spore_blossom": rgb = 0x7AD8B0; type = EnumParticleTypes.REDSTONE; break;
                case "totem": rgb = (counter % 2 == 0) ? 0x7DFF6A : 0xFFD23F; type = EnumParticleTypes.REDSTONE; break;
                default: return;
            }
        }
        if (type == EnumParticleTypes.REDSTONE) {
            // Farbe über die "Geschwindigkeit"; Rot 0 würde Minecraft als 1 lesen
            double r = Math.max(0.001, ((rgb >> 16) & 255) / 255.0);
            mc.theWorld.spawnParticle(type, x, y, z, r, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0);
        } else {
            mc.theWorld.spawnParticle(type, x, y, z, vx, vy, vz);
        }
    }
}
