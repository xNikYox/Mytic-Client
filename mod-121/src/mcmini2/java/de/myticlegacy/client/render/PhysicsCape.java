package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.CapePhysics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** 1.21–1.21.8: Umhang mit Physik (16 Streifen, siehe CapePhysics), direkt gezeichnet. */
public final class PhysicsCape {
    private static ModelPart root;
    private static ModelPart[] segments;

    private PhysicsCape() {
    }

    private static void build() {
        MeshDefinition mesh = new MeshDefinition();
        // wie beim normalen Umhang: 2 Pixel hinter dem Körper, um 180° gedreht (Außenseite nach hinten)
        PartDefinition parent = mesh.getRoot().addOrReplaceChild("base", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 0f, 2f, 0f, (float) Math.PI, 0f));
        for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
            float h = 16f / CapePhysics.SEGMENTS;
            // leicht überlappend, damit beim Biegen keine Fugen aufgehen (der unterste Streifen nicht)
            float overlap = i < CapePhysics.SEGMENTS - 1 ? 0.3f : 0f;
            parent = parent.addOrReplaceChild("s" + i, CubeListBuilder.create().texOffs(0, Math.round(i * h)).addBox(-5f, 0f, -1f, 10f, h + overlap, 1f),
                    i == 0 ? PartPose.ZERO : PartPose.offset(0f, h, 0f));
        }
        root = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        segments = new ModelPart[CapePhysics.SEGMENTS];
        ModelPart part = root.getChild("base");
        for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
            part = part.getChild("s" + i);
            segments[i] = part;
        }
    }

    /** Statt des normalen Umhangs zeichnen; body: Körper des Spielermodells, armor: Brustpanzer an. */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, int id, double x, double y, double z, float bodyRot,
                              ModelPart body, ResourceLocation texture, boolean armor) {
        if (root == null) build();
        float[] angles = CapePhysics.angles(id, x, y, z, bodyRot, body.xRot);
        float back = 0f, side = 0f;
        for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
            segments[i].xRot = -(angles[i] - back);
            segments[i].zRot = angles[CapePhysics.SEGMENTS + i] - side;
            back = angles[i];
            side = angles[CapePhysics.SEGMENTS + i];
        }
        pose.pushPose();
        body.translateAndRotate(pose);
        if (armor) pose.translate(0f, -0.053125f, 0.06875f);
        root.render(pose, buffers.getBuffer(RenderType.entitySolid(texture)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
