package de.myticlegacy.client.hud;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

/** Merkt sich eigene Treffer für Combo-, Reach- und Target-Anzeige. */
public final class CombatTracker {
    private static final long COMBO_TIMEOUT = 2500;
    private static final long TARGET_TIMEOUT = 5000;

    private static int combo;
    private static long lastHit;
    private static double lastReach = -1;
    private static LivingEntity lastTarget;
    private static long lastTargetTime;
    private static boolean wasHurt;

    private CombatTracker() {
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            Minecraft mc = Minecraft.getInstance();
            if (player == mc.player) {
                long now = System.currentTimeMillis();
                if (now - lastHit > COMBO_TIMEOUT) combo = 0;
                combo++;
                lastHit = now;
                lastReach = Math.sqrt(entity.getBoundingBox().distanceToSqr(player.getEyePosition()));
                if (entity instanceof LivingEntity living) {
                    lastTarget = living;
                    lastTargetTime = now;
                }
            }
            return InteractionResult.PASS;
        });
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            combo = 0;
            lastTarget = null;
            return;
        }
        boolean hurt = mc.player.hurtTime > 0;
        if (hurt && !wasHurt) combo = 0;
        wasHurt = hurt;
        if (System.currentTimeMillis() - lastHit > COMBO_TIMEOUT) combo = 0;
    }

    public static int combo() {
        return combo;
    }

    /** Reichweite des letzten Treffers in Blöcken, -1 wenn noch keiner (oder länger her). */
    public static double reach() {
        return System.currentTimeMillis() - lastHit > 3000 ? -1 : lastReach;
    }

    public static LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive()) return living;
        if (lastTarget != null && lastTarget.isAlive() && System.currentTimeMillis() - lastTargetTime < TARGET_TIMEOUT) return lastTarget;
        return null;
    }
}
