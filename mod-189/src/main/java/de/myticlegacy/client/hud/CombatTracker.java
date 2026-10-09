package de.myticlegacy.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Merkt sich eigene Treffer für Combo-, Reach- und Target-Anzeige. */
public final class CombatTracker {
    private static final long COMBO_TIMEOUT = 2500;
    private static final long TARGET_TIMEOUT = 5000;

    private static int combo;
    private static long lastHit;
    private static double lastReach = -1;
    private static EntityLivingBase lastTarget;
    private static long lastTargetTime;
    private static boolean wasHurt;

    private CombatTracker() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new CombatTracker());
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.entityPlayer != mc.thePlayer) return;
        Entity entity = event.target;
        long now = System.currentTimeMillis();
        if (now - lastHit > COMBO_TIMEOUT) combo = 0;
        combo++;
        lastHit = now;
        lastReach = distance(entity.getEntityBoundingBox(), mc.thePlayer.getPositionEyes(1f));
        if (entity instanceof EntityLivingBase) {
            lastTarget = (EntityLivingBase) entity;
            lastTargetTime = now;
        }
    }

    /** Abstand vom Auge zum nächsten Punkt der Hitbox. */
    private static double distance(AxisAlignedBB box, Vec3 eye) {
        double dx = Math.max(box.minX - eye.xCoord, Math.max(0, eye.xCoord - box.maxX));
        double dy = Math.max(box.minY - eye.yCoord, Math.max(0, eye.yCoord - box.maxY));
        double dz = Math.max(box.minZ - eye.zCoord, Math.max(0, eye.zCoord - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static void tick(Minecraft mc) {
        if (mc.thePlayer == null) {
            combo = 0;
            lastTarget = null;
            return;
        }
        boolean hurt = mc.thePlayer.hurtTime > 0;
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

    public static EntityLivingBase target() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.pointedEntity instanceof EntityLivingBase && mc.pointedEntity.isEntityAlive()) return (EntityLivingBase) mc.pointedEntity;
        if (lastTarget != null && lastTarget.isEntityAlive() && System.currentTimeMillis() - lastTargetTime < TARGET_TIMEOUT) return lastTarget;
        return null;
    }
}
