package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.CapePhysics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Umhang mit Physik: 16 waagerechte Streifen (je 10x1x1 Pixel), jeder hängt am vorigen und wird um den Winkel aus
 * CapePhysics gedreht. Die Textur-Koordinaten der Streifen sind so versetzt, dass zusammen der normale Umhang entsteht.
 */
public final class PhysicsCape {
    /** Eigene Teile je Spieler (gezeichnet wird erst später, daher nicht teilen). */
    private static final java.util.Map<Integer, Parts> PARTS = new java.util.HashMap<>();
    private static long lastCleanup;

    private static final class Parts {
        ModelPart root;
        ModelPart sway;
        ModelPart[] segments;
        long seen;
    }

    private PhysicsCape() {
    }

    private static Parts build() {
        MeshDefinition mesh = new MeshDefinition();
        // wie beim normalen Umhang: 2 Pixel hinter dem Körper, um 180° gedreht (Außenseite nach hinten)
        PartDefinition parent = mesh.getRoot().addOrReplaceChild("sway", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 0f, 2f, 0f, (float) Math.PI, 0f));
        for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
            float h = 16f / CapePhysics.SEGMENTS;
            // leicht überlappend, damit beim Biegen keine Fugen aufgehen (der unterste Streifen nicht)
            float overlap = i < CapePhysics.SEGMENTS - 1 ? 0.3f : 0f;
            parent = parent.addOrReplaceChild("s" + i, CubeListBuilder.create().texOffs(0, Math.round(i * h)).addBox(-5f, 0f, -1f, 10f, h + overlap, 1f),
                    i == 0 ? PartPose.ZERO : PartPose.offset(0f, h, 0f));
        }
        Parts p = new Parts();
        p.root = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        p.sway = p.root.getChild("sway");
        p.segments = new ModelPart[CapePhysics.SEGMENTS];
        ModelPart part = p.sway;
        for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
            part = part.getChild("s" + i);
            p.segments[i] = part;
        }
        return p;
    }

    /** Statt des normalen Umhangs zeichnen; armor: Brustpanzer an (Umhang etwas weiter weg). */
    public static void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel parent, Identifier texture, boolean armor) {
        long now = System.currentTimeMillis();
        Parts p = PARTS.get(state.id);
        if (p == null) PARTS.put(state.id, p = build());
        p.seen = now;
        if (now - lastCleanup > 10000) {
            lastCleanup = now;
            PARTS.values().removeIf(old -> now - old.seen > 10000);
        }
        float[] angles = CapePhysics.angles(state.id, state.x, state.y, state.z, state.bodyRot, parent.body.xRot);
        float back = 0f, side = 0f;
        for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
            // jeweils relativ zum oberen Streifen; nach der 180°-Drehung zeigt "nach hinten" in die andere Richtung
            p.segments[i].xRot = -(angles[i] - back);
            p.segments[i].zRot = angles[CapePhysics.SEGMENTS + i] - side;
            back = angles[i];
            side = angles[CapePhysics.SEGMENTS + i];
        }
        pose.pushPose();
        parent.body.translateAndRotate(pose);
        if (armor) pose.translate(0f, -0.053125f, 0.06875f);
        collector.submitModelPart(p.root, pose, RenderTypes.entitySolid(texture), light, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }
}
