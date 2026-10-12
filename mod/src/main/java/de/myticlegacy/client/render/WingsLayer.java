package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.cosmetics.WingsState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Flügel auf dem Rücken: zwei flache Teile, die sanft schlagen (beim Elytra-Flug schneller und weiter). */
public class WingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Teile je Flügelgröße: {links, rechts} */
    private final java.util.Map<Integer, ModelPart[]> parts = new java.util.HashMap<>();

    public WingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    /** Ein Flügel: w x h Pixel (meist 20x24), Ansatz an der Wirbelsäule; die Textur enthält Vorder- und Rückseite. */
    private static ModelPart wing(boolean mirror, int w, int h) {
        MeshDefinition mesh = new MeshDefinition();
        CubeListBuilder cube = CubeListBuilder.create().texOffs(0, 0);
        if (mirror) cube.mirror().addBox(-w, -10f, 0f, w, h, 0);
        else cube.addBox(0f, -10f, 0f, w, h, 0);
        mesh.getRoot().addOrReplaceChild("wing", cube, PartPose.ZERO);
        return LayerDefinition.create(mesh, 2 * w, h).bakeRoot().getChild("wing");
    }

    private ModelPart[] parts(String id) {
        int[] size = CosmeticsCore.wingSize(id);
        return parts.computeIfAbsent(size[0] * 1000 + size[1], k -> new ModelPart[]{wing(false, size[0], size[1]), wing(true, size[0], size[1])});
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        String id = ((WingsState) state).mytic$wings();
        if (id == null || state.isInvisible) return;
        Identifier texture = Identifier.fromNamespaceAndPath("myticclient", "textures/wings/" + id + ".png");
        boolean flying = state.isFallFlying;
        float t = state.ageInTicks;
        float flap = (float) Math.sin(t * (flying ? 0.55f : 0.13f)) * (flying ? 22f : 10f);
        float spread = flying ? 10f : 28f;
        pose.pushPose();
        // hinter den Rücken, zwischen die Schulterblätter
        pose.translate(0f, state.isCrouching ? 0.25f : 0.1f, 0.14f);
        float tilt = state.isCrouching ? (float) Math.toRadians(25f) : 0f;
        ModelPart[] lr = parts(id);
        ModelPart left = lr[0];
        ModelPart right = lr[1];
        left.setPos(1f, 0f, 0f);
        left.setRotation(tilt, (float) Math.toRadians(-(spread + flap)), (float) Math.toRadians(-6f));
        right.setPos(-1f, 0f, 0f);
        right.setRotation(tilt, (float) Math.toRadians(spread + flap), (float) Math.toRadians(6f));
        collector.submitModelPart(left, pose, RenderTypes.entityCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, null);
        collector.submitModelPart(right, pose, RenderTypes.entityCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, null);
        // animierte Leucht-Ebene mit voller Helligkeit darüber (Lichtband, Funkeln, pulsierender Rand)
        int frame = (int) ((System.currentTimeMillis() / CosmeticsCore.WING_FX_MS) % CosmeticsCore.WING_FX_FRAMES);
        Identifier fx = Identifier.fromNamespaceAndPath("myticclient", "textures/wings/" + id + "_fx_" + frame + ".png");
        collector.submitModelPart(left, pose, RenderTypes.entityTranslucentEmissive(fx), light, OverlayTexture.NO_OVERLAY, null);
        collector.submitModelPart(right, pose, RenderTypes.entityTranslucentEmissive(fx), light, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }
}
