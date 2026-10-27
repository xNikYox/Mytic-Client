package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * FPS-Boost: Entities und Block-Entities (Truhen, Schilder …) nicht zeichnen, wenn sie weit weg oder hinter Wänden sind.
 * „Hinter Wänden“ per Sichtstrahl: liegt auf allen Strahlen von der Kamera zum Objekt ein voller Block, wird nicht
 * gezeichnet. Ergebnisse werden kurz gemerkt, neue Prüfungen pro Bild sind begrenzt – im Zweifel wird gezeichnet.
 */
public final class Culling {
    private static final int BUDGET = 250, MAX_STEPS = 96;
    private static final long VISIBLE_MS = 120, HIDDEN_MS = 60;
    private static final Int2LongOpenHashMap entities = new Int2LongOpenHashMap();   // Zeit << 1 | sichtbar
    private static final Long2LongOpenHashMap blocks = new Long2LongOpenHashMap();
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private static long windowStart;
    private static int budget;
    private static Level lastLevel;
    private static Vec3 cam = Vec3.ZERO;
    private static long camAt;
    public static int culled, culledNow;
    private static long countAt;

    private Culling() {
    }

    private static FpsBoostModule boost() {
        return MyticClient.fpsBoost;
    }

    private static boolean active() {
        FpsBoostModule b = boost();
        return b != null && b.enabled() && b.culling.get();
    }

    private static Vec3 camera(long now) {
        if (now - camAt > 4) {
            cam = BoostCompat.camera();
            camAt = now;
        }
        return cam;
    }

    private static void count(long now) {
        if (now - countAt >= 1000) {
            culled = culledNow;
            culledNow = 0;
            countAt = now;
        }
        culledNow++;
    }

    /** true = Entity nicht zeichnen */
    public static boolean cullEntity(Entity e) {
        if (!active()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player || e == mc.getCameraEntity() || e instanceof Player || e.isCurrentlyGlowing() || e.isVehicle() || e.isPassenger()) return false;
        long now = System.currentTimeMillis();
        Vec3 c = camera(now);
        int limit = e instanceof ItemEntity || e instanceof ExperienceOrb ? Math.min(32, boost().mobDistance.intValue()) : boost().mobDistance.intValue();
        if (e.distanceToSqr(c) > (double) limit * limit) {
            count(now);
            return true;
        }
        if (entityHidden(e, c, now)) {
            count(now);
            return true;
        }
        return false;
    }

    /** true = Block-Entity nicht zeichnen (Leuchtfeuer, End-Portale immer) */
    public static boolean cullBlockEntity(BlockEntity be) {
        if (!active()) return false;
        String type = be.getClass().getSimpleName();
        if (type.contains("Beacon") || type.contains("EndGateway") || type.contains("EndPortal")) return false;
        long now = System.currentTimeMillis();
        Vec3 c = camera(now);
        BlockPos p = be.getBlockPos();
        double dx = p.getX() + 0.5 - c.x, dy = p.getY() + 0.5 - c.y, dz = p.getZ() + 0.5 - c.z;
        int limit = boost().blockEntityDistance.intValue();
        if (dx * dx + dy * dy + dz * dz > (double) limit * limit || blockHidden(p, c, now)) {
            count(now);
            return true;
        }
        return false;
    }

    private static boolean takeBudget(long now) {
        Minecraft mc = Minecraft.getInstance();
        if (now - windowStart >= 16) {
            windowStart = now;
            budget = BUDGET;
            if (lastLevel != mc.level || entities.size() > 20000 || blocks.size() > 20000) {
                entities.clear();
                blocks.clear();
                lastLevel = mc.level;
            }
        }
        return budget-- > 0;
    }

    private static boolean fresh(long entry, long now) {
        boolean visible = (entry & 1) == 1;
        return now - (entry >>> 1) < (visible ? VISIBLE_MS : HIDDEN_MS);
    }

    private static boolean entityHidden(Entity e, Vec3 c, long now) {
        int id = e.getId();
        boolean has = entities.containsKey(id);
        long entry = has ? entities.get(id) : 0;
        if (has && fresh(entry, now)) return (entry & 1) == 0;
        if (!takeBudget(now)) return has && (entry & 1) == 0;
        AABB b = e.getBoundingBox();
        Vec3 mid = b.getCenter();
        boolean visible = visible(c, mid, b) || visible(c, new Vec3(mid.x, b.maxY - 0.05, mid.z), b)
                || visible(c, new Vec3(b.minX + 0.05, b.minY + 0.05, b.minZ + 0.05), b) || visible(c, new Vec3(b.maxX - 0.05, b.maxY - 0.05, b.maxZ - 0.05), b);
        entities.put(id, (now << 1) | (visible ? 1 : 0));
        return !visible;
    }

    private static boolean blockHidden(BlockPos pos, Vec3 c, long now) {
        long key = pos.asLong();
        boolean has = blocks.containsKey(key);
        long entry = has ? blocks.get(key) : 0;
        if (has && fresh(entry, now)) return (entry & 1) == 0;
        if (!takeBudget(now)) return has && (entry & 1) == 0;
        AABB b = new AABB(pos);
        Vec3 mid = b.getCenter();
        boolean visible = visible(c, mid, b);
        for (int i = 0; i < 8 && !visible; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? b.minX + 0.02 : b.maxX - 0.02, (i & 2) == 0 ? b.minY + 0.02 : b.maxY - 0.02, (i & 4) == 0 ? b.minZ + 0.02 : b.maxZ - 0.02);
            if (corner.distanceToSqr(c) <= mid.distanceToSqr(c)) visible = visible(c, corner, b);
        }
        blocks.put(key, (now << 1) | (visible ? 1 : 0));
        return !visible;
    }

    /** Freie Sicht von a nach b? Blöcke im Ziel selbst zählen nicht (Voxel-Durchlauf) */
    private static boolean visible(Vec3 a, Vec3 b, AABB own) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return true;
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        int x = (int) Math.floor(a.x), y = (int) Math.floor(a.y), z = (int) Math.floor(a.z);
        int tx = (int) Math.floor(b.x), ty = (int) Math.floor(b.y), tz = (int) Math.floor(b.z);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double inf = Double.MAX_VALUE;
        double tdx = dx == 0 ? inf : Math.abs(1 / dx), tdy = dy == 0 ? inf : Math.abs(1 / dy), tdz = dz == 0 ? inf : Math.abs(1 / dz);
        double mx = dx == 0 ? inf : (dx > 0 ? (x + 1 - a.x) : (a.x - x)) * tdx;
        double my = dy == 0 ? inf : (dy > 0 ? (y + 1 - a.y) : (a.y - y)) * tdy;
        double mz = dz == 0 ? inf : (dz > 0 ? (z + 1 - a.z) : (a.z - z)) * tdz;
        for (int step = 0; step < MAX_STEPS; step++) {
            if (x == tx && y == ty && z == tz) return true;
            if (mx < my && mx < mz) { x += sx; mx += tdx; }
            else if (my < mz) { y += sy; my += tdy; }
            else { z += sz; mz += tdz; }
            if (x == tx && y == ty && z == tz) return true;
            if (x + 1 > own.minX && x < own.maxX && y + 1 > own.minY && y < own.maxY && z + 1 > own.minZ && z < own.maxZ) continue;
            cursor.set(x, y, z);
            if (BoostCompat.solid(level, cursor)) return false;
        }
        return true;
    }

    // ---------- Ferne Entities seltener aktualisieren ----------
    /** true = diesen Tick überspringen (weit weg, wird ohnehin nicht gezeichnet; nur jeder 4. Tick) */
    public static boolean skipTick(Entity e) {
        FpsBoostModule b = boost();
        if (b == null || !b.enabled() || !b.farTicks.get()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e instanceof Player || e.isVehicle() || e.isPassenger() || e.isCurrentlyGlowing()) return false;
        double limit = b.mobDistance.intValue() + 16;
        if (e.distanceToSqr(mc.player) < limit * limit) return false;
        return (mc.player.tickCount + e.getId()) % 4 != 0;
    }

    // ---------- Partikel-Limit ----------
    private static long particleWindow;
    private static int particlesInWindow;
    public static int droppedParticles;

    /** true = Partikel verwerfen (mehr als das Limit pro Sekunde) */
    public static boolean dropParticle() {
        FpsBoostModule b = boost();
        if (b == null || !b.enabled()) return false;
        int perSecond = b.particleLimit.intValue();
        if (perSecond >= 10000) return false; // „unbegrenzt“
        long now = System.currentTimeMillis();
        if (now - particleWindow >= 100) {
            particleWindow = now;
            particlesInWindow = 0;
        }
        if (++particlesInWindow > perSecond / 10) {
            droppedParticles++;
            return true;
        }
        return false;
    }

    public static boolean singleItemModel() {
        FpsBoostModule b = boost();
        return b != null && b.enabled() && b.itemStacks.get();
    }
}
