package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

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
    private volatile List<int[]> markers = new ArrayList<int[]>();
    private int cooldown;

    public LightOverlayModule() {
        super("lightoverlay", "Light Level Overlay", "Zeigt, wo Monster spawnen können (rot: immer, gelb: nachts)", Category.VISUELL, () -> Items.glowstone_dust, false);
        radius = new SliderSetting(this, "radius", "Reichweite", 4, 32, 1, 16, " Blöcke");
        nightToo = new BoolSetting(this, "night", "Auch nachts-gefährliche Stellen (gelb)", true);
        alwaysColor = new ColorSetting(this, "always", "Farbe: immer gefährlich", 0xE6FF3B3B);
        nightColor = new ColorSetting(this, "nightColor", "Farbe: nachts gefährlich", 0xE6FFD23F);
    }

    public void tick(Minecraft mc) {
        if (!enabled() || mc.theWorld == null || mc.thePlayer == null) {
            if (!markers.isEmpty()) markers = new ArrayList<int[]>();
            return;
        }
        if (--cooldown > 0) return;
        cooldown = 10;
        World world = mc.theWorld;
        BlockPos center = new BlockPos(mc.thePlayer);
        int r = radius.intValue();
        int ry = Math.min(r, 10);
        List<int[]> found = new ArrayList<int[]>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int dy = -ry; dy <= ry; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    Block block = world.getBlockState(pos).getBlock();
                    if (block.getMaterial().blocksMovement() || block.getMaterial().isLiquid()) continue;
                    if (!World.doesBlockHaveSolidTopSurface(world, pos.down())) continue;
                    if (world.getLightFor(EnumSkyBlock.BLOCK, pos) > 0) continue;
                    int kind = world.getLightFor(EnumSkyBlock.SKY, pos) == 0 ? 0 : 1;
                    found.add(new int[]{pos.getX(), pos.getY(), pos.getZ(), kind});
                }
            }
        }
        markers = found;
    }

    /** Zeichnet die Markierungen (aus RenderWorldLastEvent). */
    public void draw(float partialTicks) {
        if (!enabled() || markers.isEmpty()) return;
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int[] m : markers) {
            if (m[3] == 1 && !nightToo.get()) continue;
            int c = m[3] == 0 ? alwaysColor.get() : nightColor.get();
            int a = (c >>> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            double x = m[0] - cx, y = m[1] + 0.02 - cy, z = m[2] - cz;
            double p = 0.18, q = 0.82, t = 0.06;
            buffer.pos(x + p + t, y, z + p - t).color(r, g, b, a).endVertex();
            buffer.pos(x + q + t, y, z + q - t).color(r, g, b, a).endVertex();
            buffer.pos(x + q - t, y, z + q + t).color(r, g, b, a).endVertex();
            buffer.pos(x + p - t, y, z + p + t).color(r, g, b, a).endVertex();
            buffer.pos(x + p - t, y, z + q - t).color(r, g, b, a).endVertex();
            buffer.pos(x + q - t, y, z + p - t).color(r, g, b, a).endVertex();
            buffer.pos(x + q + t, y, z + p + t).color(r, g, b, a).endVertex();
            buffer.pos(x + p + t, y, z + q + t).color(r, g, b, a).endVertex();
        }
        tessellator.draw();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
