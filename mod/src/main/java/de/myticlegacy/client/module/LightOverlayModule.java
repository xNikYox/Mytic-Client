package de.myticlegacy.client.module;

import de.myticlegacy.client.render.QuadSink;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Light Level Overlay wie bei Lunar: markiert Blöcke, auf denen Monster spawnen können (Blocklicht 0).
 * Rot = jederzeit (auch kein Himmelslicht), Gelb = nur nachts. Der Scan ist auf 4 Ticks verteilt.
 */
public class LightOverlayModule extends Module {
    private final SliderSetting radius;
    private final BoolSetting nightToo;
    private final ColorSetting alwaysColor;
    private final ColorSetting nightColor;
    /** x, y, z, Art (0 = jederzeit, 1 = nachts) */
    private volatile List<int[]> markers = new ArrayList<>();
    /** Laufender Durchgang: wird über SLICES Ticks verteilt aufgebaut, dann getauscht (kein Ruckeln bei großem Radius) */
    private List<int[]> building = new ArrayList<>();
    private int slice;
    private BlockPos sweepCenter;
    private static final int SLICES = 4;

    public LightOverlayModule() {
        super("lightoverlay", "Light Level Overlay", "Zeigt, wo Monster spawnen können (rot: immer, gelb: nachts)", Category.VISUELL, () -> Items.GLOWSTONE_DUST, false);
        radius = new SliderSetting(this, "radius", "Reichweite", 4, 32, 1, 16, " Blöcke");
        nightToo = new BoolSetting(this, "night", "Auch nachts-gefährliche Stellen (gelb)", true);
        alwaysColor = new ColorSetting(this, "always", "Farbe: immer gefährlich", 0xE6FF3B3B);
        nightColor = new ColorSetting(this, "nightColor", "Farbe: nachts gefährlich", 0xE6FFD23F);
    }

    public void tick(Minecraft mc) {
        if (!enabled() || mc.level == null || mc.player == null) {
            if (!markers.isEmpty()) markers = new ArrayList<>();
            slice = 0;
            building = new ArrayList<>();
            return;
        }
        Level level = mc.level;
        if (slice == 0) sweepCenter = mc.player.blockPosition();
        BlockPos center = sweepCenter;
        int r = radius.intValue();
        int ry = Math.min(r, 10);
        // je Tick ein Streifen von x-Spalten
        int from = -r + (2 * r + 1) * slice / SLICES;
        int to = -r + (2 * r + 1) * (slice + 1) / SLICES;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int dx = from; dx < to; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int dy = -ry; dy <= ry; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (spawnable(level, pos, below, above)) {
                        int kind = level.getBrightness(LightLayer.SKY, pos) == 0 ? 0 : 1;
                        building.add(new int[]{pos.getX(), pos.getY(), pos.getZ(), kind});
                    }
                }
            }
        }
        if (++slice >= SLICES) {
            markers = building;
            building = new ArrayList<>();
            slice = 0;
        }
    }

    /**
     * Kann hier ein Monster (Zombie) spawnen? Wie Minecraft: Spawn-Block leer (keine Kollision, kein Wasser, keine Schiene
     * o. Ä.), darüber Platz, darunter ein Block, auf dem Monster spawnen dürfen (nicht Glas, Laub, Halbstufe unten, Eis
     * …), kein Blocklicht, kein Pilzland.
     */
    private static boolean spawnable(Level level, BlockPos pos, BlockPos.MutableBlockPos below, BlockPos.MutableBlockPos above) {
        BlockState state = level.getBlockState(pos);
        if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, state, state.getFluidState(), EntityType.ZOMBIE)) return false;
        below.set(pos.getX(), pos.getY() - 1, pos.getZ());
        BlockState ground = level.getBlockState(below);
        if (!ground.isValidSpawn(level, below, EntityType.ZOMBIE)) return false;
        if (level.getBrightness(LightLayer.BLOCK, pos) > 0) return false;
        above.set(pos.getX(), pos.getY() + 1, pos.getZ());
        BlockState head = level.getBlockState(above);
        if (!head.getCollisionShape(level, above).isEmpty()) return false;
        return !level.getBiome(pos).is(Biomes.MUSHROOM_FIELDS);
    }

    /** Zeichnet ein flaches X auf jeden markierten Block. */
    public void draw(QuadSink sink) {
        if (!enabled()) return;
        for (int[] m : markers) {
            if (m[3] == 1 && !nightToo.get()) continue;
            int color = m[3] == 0 ? alwaysColor.get() : nightColor.get();
            cross(sink, m[0], m[1] + 0.02, m[2], color);
        }
    }

    static void cross(QuadSink sink, double x, double y, double z, int color) {
        double a = 0.18, b = 0.82, t = 0.06;
        // Diagonale von (a, a) nach (b, b)
        sink.quad(x + a + t, y, z + a - t, x + b + t, y, z + b - t, x + b - t, y, z + b + t, x + a - t, y, z + a + t, color);
        // Diagonale von (a, b) nach (b, a)
        sink.quad(x + a - t, y, z + b - t, x + b - t, y, z + a - t, x + b + t, y, z + a + t, x + a + t, y, z + b + t, color);
    }
}
