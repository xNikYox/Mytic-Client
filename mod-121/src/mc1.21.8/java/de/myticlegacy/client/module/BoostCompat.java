package de.myticlegacy.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** FPS-Boost/Culling, versionsabhängiger Teil (1.21.2 – 1.21.8) */
public final class BoostCompat {
    private BoostCompat() {
    }

    public static Vec3 camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    public static boolean solid(Level level, BlockPos pos) {
        return level.getBlockState(pos).isSolidRender();
    }
}
