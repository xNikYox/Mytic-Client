package de.myticlegacy.client.minimap;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Liest die Oberfläche geladener Chunks (oberster sichtbarer Block je Spalte, wie bei einer Karte aus Papier):
 * keine Höhlen, keine Erze, keine Wesen. Gras, Laub und Wasser in Biom-Farben, Höhenstufen als Licht und Schatten,
 * Wasser je nach Tiefe dunkler. Je Tick nur wenige Chunks (nahe öfter, ferne seltener).
 */
public final class MapScanner {
    private static final int MAX_RADIUS = 34;
    /** Chunk-Versätze nach Entfernung sortiert */
    private static final int[][] SPIRAL;

    static {
        List<int[]> list = new ArrayList<>();
        for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++)
            for (int dz = -MAX_RADIUS; dz <= MAX_RADIUS; dz++) list.add(new int[]{dx, dz, dx * dx + dz * dz});
        list.sort((a, b) -> Integer.compare(a[2], b[2]));
        SPIRAL = list.toArray(new int[0][]);
    }

    private final Map<Long, Long> scannedAt = new HashMap<>();
    private int cursor, nearCursor;
    private long ticks;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public void reset() {
        scannedAt.clear();
        cursor = 0;
        nearCursor = 0;
    }

    /** Einen Tick lang scannen: ein naher Chunk (Bauten in der Nähe sofort sichtbar) und einige ferne */
    public void tick(ClientLevel level, MapData data, int playerCx, int playerCz, int renderDistance) {
        ticks++;
        // nah (5 × 5): reihum, jeder etwa alle 1,3 s
        int[] o = SPIRAL[nearCursor];
        nearCursor = (nearCursor + 1) % 25;
        scan(level, data, playerCx + o[0], playerCz + o[1]);
        // fern: unbekannte oder ältere als 30 s
        int radius = Math.min(MAX_RADIUS, renderDistance + 1);
        int limit = (2 * radius + 1) * (2 * radius + 1);
        int done = 0;
        for (int tries = 0; tries < 96 && done < 4; tries++) {
            if (cursor >= limit || cursor >= SPIRAL.length) cursor = 25;
            int[] s = SPIRAL[cursor++];
            int cx = playerCx + s[0], cz = playerCz + s[1];
            Long at = scannedAt.get(MapData.pack(cx, cz));
            if (at != null && ticks - at < 600) continue;
            if (scan(level, data, cx, cz)) done++;
        }
        if (scannedAt.size() > 20000) scannedAt.clear();
    }

    private boolean scan(ClientLevel level, MapData data, int cx, int cz) {
        if (!level.getChunkSource().hasChunk(cx, cz)) return false;
        LevelChunk chunk = level.getChunk(cx, cz);
        if (chunk == null || chunk.isEmpty()) return false;
        scannedAt.put(MapData.pack(cx, cz), ticks);
        data.put(cx, cz, colors(level, chunk, cx, cz));
        return true;
    }

    private int[] colors(ClientLevel level, LevelChunk chunk, int cx, int cz) {
        int[] out = new int[256];
        int[] height = new int[256];
        int bx = cx << 4, bz = cz << 4;
        int[] north = new int[16];
        for (int lx = 0; lx < 16; lx++) north[lx] = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx + lx, bz - 1) - 1;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = bx + lx, z = bz + lz;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                pos.set(x, y, z);
                BlockState state = chunk.getBlockState(pos);
                MapColor mc = state.getMapColor(level, pos);
                // durchsichtige Blöcke (Glas, Luft) überspringen – höchstens ein paar Blöcke
                for (int steps = 0; mc == MapColor.NONE && steps < 24; steps++) {
                    pos.setY(--y);
                    state = chunk.getBlockState(pos);
                    mc = state.getMapColor(level, pos);
                }
                int i = lz << 4 | lx;
                height[i] = y;
                if (mc == MapColor.NONE) {
                    out[i] = 0xFF0C0A12; // Leere (z. B. End-Abgrund)
                    continue;
                }
                int up = lz > 0 ? height[i - 16] : north[lx];
                if (state.getFluidState().is(FluidTags.WATER)) {
                    int depth = 0;
                    while (depth < 12) {
                        pos.setY(y - 1 - depth);
                        if (!chunk.getBlockState(pos).getFluidState().is(FluidTags.WATER)) break;
                        depth++;
                    }
                    pos.setY(y);
                    int water = BiomeColors.getAverageWaterColor(level, pos);
                    int base = mix(water, MapColor.WATER.col, 0.35f);
                    out[i] = 0xFF000000 | scale(base, 1.04f - Math.min(depth, 10) * 0.045f);
                    continue;
                }
                int base = mc.col;
                if (mc == MapColor.GRASS) base = scale(BiomeColors.getAverageGrassColor(level, pos), 0.9f);
                else if (mc == MapColor.PLANT) base = mix(scale(BiomeColors.getAverageFoliageColor(level, pos), 0.85f), base, 0.3f);
                int diff = y - up;
                float shade = diff >= 2 ? 1.1f : diff == 1 ? 1.03f : diff == 0 ? 0.94f : diff == -1 ? 0.85f : 0.77f;
                out[i] = 0xFF000000 | scale(base, shade);
            }
        }
        return out;
    }

    static int scale(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * f));
        int b = Math.min(255, Math.round((rgb & 255) * f));
        return Math.max(1, r << 16 | g << 8 | b);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
