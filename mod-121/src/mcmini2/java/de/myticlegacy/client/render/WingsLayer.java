package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.cosmetics.WingsState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** 1.21.2–1.21.8: Flügel auf dem Rücken (direkt gezeichnet, sonst wie in 1.21.11). */
public class WingsLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    private final ModelPart left;
    private final ModelPart right;

    public WingsLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
        left = wing(false);
        right = wing(true);
    }

    private static ModelPart wing(boolean mirror) {
        MeshDefinition mesh = new MeshDefinition();
        CubeListBuilder cube = CubeListBuilder.create().texOffs(0, 0);
        if (mirror) cube.mirror().addBox(-20f, -10f, 0f, 20, 24, 0);
        else cube.addBox(0f, -10f, 0f, 20, 24, 0);
        mesh.getRoot().addOrReplaceChild("wing", cube, PartPose.ZERO);
        return LayerDefinition.create(mesh, 40, 24).bakeRoot().getChild("wing");
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
        String id = ((WingsState) state).mytic$wings();
        if (id == null || state.isInvisible) return;
        boolean flying = state.isFallFlying;
        float t = state.ageInTicks;
        float flap = (float) Math.sin(t * (flying ? 0.55f : 0.13f)) * (flying ? 22f : 10f);
        float spread = flying ? 10f : 28f;
        float tilt = state.isCrouching ? (float) Math.toRadians(25f) : 0f;
        pose.pushPose();
        pose.translate(0f, state.isCrouching ? 0.25f : 0.1f, 0.14f);
        left.setPos(1f, 0f, 0f);
        left.setRotation(tilt, (float) Math.toRadians(-(spread + flap)), (float) Math.toRadians(-6f));
        right.setPos(-1f, 0f, 0f);
        right.setRotation(tilt, (float) Math.toRadians(spread + flap), (float) Math.toRadians(6f));
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("myticclient", "textures/wings/" + id + ".png");
        left.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        right.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        int frame = (int) ((System.currentTimeMillis() / CosmeticsCore.WING_FX_MS) % CosmeticsCore.WING_FX_FRAMES);
        ResourceLocation fx = ResourceLocation.fromNamespaceAndPath("myticclient", "textures/wings/" + id + "_fx_" + frame + ".png");
        left.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(fx)), light, OverlayTexture.NO_OVERLAY);
        right.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(fx)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
