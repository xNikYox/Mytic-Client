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
        return cullEntityFar(e) || cullEntityHidden(e);
    }

    /** Darf diese Entity überhaupt ausgeblendet werden? (Spieler, Kamera, leuchtend, Reittiere/Reiter nie) */
    private static boolean exempt(Entity e) {
        if (e instanceof Player) return true; // schließt den eigenen Spieler ein
        Minecraft mc = Minecraft.getInstance();
        return e == mc.player || e == mc.getCameraEntity() || e.isCurrentlyGlowing() || e.isVehicle() || e.isPassenger();
    }

    /**
     * Teil 1 (billig, vor der Sichtfeld-Prüfung von Minecraft): true = zu weit weg, nicht zeichnen.
     */
    public static boolean cullEntityFar(Entity e) {
        FpsBoostModule b = boost();
        if (b == null || !b.enabled() || !b.culling.get() || exempt(e)) return false;
        long now = System.currentTimeMillis();
        Vec3 c = camera(now);
        int limit = b.mobDistance.intValue();
        if (limit > 32 && (e instanceof ItemEntity || e instanceof ExperienceOrb)) limit = 32;
        if (e.distanceToSqr(c) > (double) limit * limit) {
            count(now);
            return true;
        }
        return false;
    }

    /**
     * Teil 2 (Sichtstrahlen): true = hinter Wänden, nicht zeichnen. Wird nur für Entities gefragt, die Minecraft sonst
     * zeichnen würde (im Sichtfeld) – so verbrauchen Entities hinter der Kamera kein Strahlen-Budget.
     */
    public static boolean cullEntityHidden(Entity e) {
        FpsBoostModule b = boost();
        if (b == null || !b.enabled() || !b.culling.get() || exempt(e)) return false;
        long now = System.currentTimeMillis();
        if (entityHidden(e, camera(now), now)) {
            count(now);
            return true;
        }
        return false;
    }

    /** true = Block-Entity nicht zeichnen (Leuchtfeuer, End-Portale immer) */
    /** Strahlen über große Entfernung (Leuchtfeuer, End-Portale) nie ausblenden – einmal je Klasse geprüft statt pro Bild */
    private static final ClassValue<Boolean> NEVER_CULL = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getSimpleName();
            return name.contains("Beacon") || name.contains("EndGateway") || name.contains("EndPortal");
        }
    };

    public static boolean cullBlockEntity(BlockEntity be) {
        FpsBoostModule b = boost();
        if (b == null || !b.enabled() || !b.culling.get()) return false;
        if (NEVER_CULL.get(be.getClass())) return false;
        long now = System.currentTimeMillis();
        Vec3 c = camera(now);
        BlockPos p = be.getBlockPos();
        double dx = p.getX() + 0.5 - c.x, dy = p.getY() + 0.5 - c.y, dz = p.getZ() + 0.5 - c.z;
        int limit = b.blockEntityDistance.intValue();
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
        long entry = entities.get(id); // fehlt → 0 (gespeicherte Werte sind nie 0)
        boolean has = entry != 0;
        if (has && fresh(entry, now)) return (entry & 1) == 0;
        if (!takeBudget(now)) return has && (entry & 1) == 0;
        AABB b = e.getBoundingBox();
        double mx = (b.minX + b.maxX) * 0.5, my = (b.minY + b.maxY) * 0.5, mz = (b.minZ + b.maxZ) * 0.5;
        // ohne neue Vec3-Objekte: Mitte, oben Mitte, zwei gegenüberliegende Ecken
        boolean visible = visible(c, mx, my, mz, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)
                || visible(c, mx, b.maxY - 0.05, mz, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)
                || visible(c, b.minX + 0.05, b.minY + 0.05, b.minZ + 0.05, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)
                || visible(c, b.maxX - 0.05, b.maxY - 0.05, b.maxZ - 0.05, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
        entities.put(id, (now << 1) | (visible ? 1 : 0));
        return !visible;
    }

    private static boolean blockHidden(BlockPos pos, Vec3 c, long now) {
        long key = pos.asLong();
        long entry = blocks.get(key);
        boolean has = entry != 0;
        if (has && fresh(entry, now)) return (entry & 1) == 0;
        if (!takeBudget(now)) return has && (entry & 1) == 0;
        // Block als Box (ohne neue AABB/Vec3-Objekte): Mitte, dann die der Kamera zugewandten Ecken
        double x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ(), x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
        double mx = x0 + 0.5, my = y0 + 0.5, mz = z0 + 0.5;
        boolean visible = visible(c, mx, my, mz, x0, y0, z0, x1, y1, z1);
        double midDist = dist2(c, mx, my, mz);
        for (int i = 0; i < 8 && !visible; i++) {
            double cx = (i & 1) == 0 ? x0 + 0.02 : x1 - 0.02, cy = (i & 2) == 0 ? y0 + 0.02 : y1 - 0.02, cz = (i & 4) == 0 ? z0 + 0.02 : z1 - 0.02;
            if (dist2(c, cx, cy, cz) <= midDist) visible = visible(c, cx, cy, cz, x0, y0, z0, x1, y1, z1);
        }
        blocks.put(key, (now << 1) | (visible ? 1 : 0));
        return !visible;
    }

    private static double dist2(Vec3 a, double x, double y, double z) {
        double dx = x - a.x, dy = y - a.y, dz = z - a.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Freie Sicht von a zum Punkt (bx, by, bz)? Blöcke in der eigenen Box (min/max) zählen nicht (Voxel-Durchlauf) */
    private static boolean visible(Vec3 a, double bx, double by, double bz, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return true;
        double dx = bx - a.x, dy = by - a.y, dz = bz - a.z;
        int x = (int) Math.floor(a.x), y = (int) Math.floor(a.y), z = (int) Math.floor(a.z);
        int tx = (int) Math.floor(bx), ty = (int) Math.floor(by), tz = (int) Math.floor(bz);
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
            if (x + 1 > minX && x < maxX && y + 1 > minY && y < maxY && z + 1 > minZ && z < maxZ) continue;
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
