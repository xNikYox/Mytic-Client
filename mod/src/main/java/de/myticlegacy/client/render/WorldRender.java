package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Zeichnen in der Welt (1.21.11): Vierecke vor den durchsichtigen Blöcken, mit Tiefentest. */
public final class WorldRender {
    public interface Drawer {
        void draw(QuadSink sink);
    }

    private WorldRender() {
    }

    public static void register(Drawer drawer) {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            if (context.consumers() == null) return;
            VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugQuads());
            emit(context.matrices().last(), vc, Minecraft.getInstance().gameRenderer.getMainCamera().position(), drawer);
        });
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, Drawer drawer) {
        drawer.draw((x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, argb) -> {
            int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            vc.addVertex(pose, (float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z)).setColor(r, g, b, a);
            vc.addVertex(pose, (float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z)).setColor(r, g, b, a);
            vc.addVertex(pose, (float) (x3 - cam.x), (float) (y3 - cam.y), (float) (z3 - cam.z)).setColor(r, g, b, a);
            vc.addVertex(pose, (float) (x4 - cam.x), (float) (y4 - cam.y), (float) (z4 - cam.z)).setColor(r, g, b, a);
        });
    }
}
