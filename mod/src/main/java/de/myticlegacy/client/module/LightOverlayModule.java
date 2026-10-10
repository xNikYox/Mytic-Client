package de.myticlegacy.client.module;

import de.myticlegacy.client.render.QuadSink;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Light Level Overlay wie bei Lunar: markiert Blöcke, auf denen Monster spawnen können (Blocklicht 0).
 * Rot = jederzeit (auch kein Himmelslicht), Gelb = nur nachts. Wird alle 10 Ticks neu berechnet.
 */
public class LightOverlayModule extends Module {
    private final SliderSetting radius;
    private final BoolSetting nightToo;
    private final ColorSetting alwaysColor;
    private final ColorSetting nightColor;
    /** x, y, z, Art (0 = jederzeit, 1 = nachts) */
    private volatile List<int[]> markers = new ArrayList<>();
    private int cooldown;

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
            return;
        }
        if (--cooldown > 0) return;
        cooldown = 10;
        Level level = mc.level;
        BlockPos center = mc.player.blockPosition();
        int r = radius.intValue();
        int ry = Math.min(r, 10);
        List<int[]> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int dy = -ry; dy <= ry; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) continue;
                    below.set(pos.getX(), pos.getY() - 1, pos.getZ());
                    BlockState ground = level.getBlockState(below);
                    if (!ground.isFaceSturdy(level, below, Direction.UP)) continue;
                    if (level.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
                    int kind = level.getBrightness(LightLayer.SKY, pos) == 0 ? 0 : 1;
                    found.add(new int[]{pos.getX(), pos.getY(), pos.getZ(), kind});
                }
            }
        }
        markers = found;
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
