package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.cosmetics.FeatherFx;
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
        Identifier texture = WING_TEXTURES.computeIfAbsent(id, w -> Identifier.fromNamespaceAndPath("myticclient", "textures/wings/" + w + ".png"));
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
        Identifier fx = WING_FX.computeIfAbsent(id, w -> {
            Identifier[] all = new Identifier[CosmeticsCore.WING_FX_FRAMES];
            for (int i = 0; i < all.length; i++) all[i] = Identifier.fromNamespaceAndPath("myticclient", "textures/wings/" + w + "_fx_" + i + ".png");
            return all;
        })[frame];
        collector.submitModelPart(left, pose, RenderTypes.entityTranslucentEmissive(fx), light, OverlayTexture.NO_OVERLAY, null);
        collector.submitModelPart(right, pose, RenderTypes.entityTranslucentEmissive(fx), light, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
        if (CosmeticsCore.wingFeathers(id)) feathers(pose, collector, light, state, id);
    }

    /** Fallende Federn (Weltpositionen aus FeatherFx, hier ins Modell-Koordinatensystem umgerechnet). */
    private static void feathers(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, String id) {
        java.util.List<FeatherFx.Feather> list = FeatherFx.update(state.id, state.x, state.y, state.z, state.bodyRot, state.isFallFlying);
        if (list == null) return;
        Identifier texture = FEATHER_TEXTURES.computeIfAbsent(id, w -> Identifier.fromNamespaceAndPath("myticclient", "textures/wings/" + w + "_feather.png"));
        if (feather == null) feather = featherPart();
        for (FeatherFx.Feather f : list) {
            float[] at = FeatherFx.local(f, state.x, state.y, state.z, state.bodyRot, 0.9375f, 1.501f);
            float size = f.size() * 1.6f;
            pose.pushPose();
            pose.translate(at[0], at[1], at[2]);
            pose.scale(size, size, size);
            // Drehung in die Pose statt ins Modellteil: alle Federn teilen sich ein Teil, gezeichnet wird erst später
            pose.mulPose(SPIN.rotationZYX((float) Math.toRadians(f.rock()), (float) Math.toRadians(f.spin()), 0f));
            collector.submitModelPart(feather, pose, RenderTypes.entityCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
        }
    }

    private static ModelPart feather;
    // Textur-Namen je Flügel einmal anlegen statt pro Frame (nur Render-Thread)
    private static final java.util.Map<String, Identifier> WING_TEXTURES = new java.util.HashMap<>();
    private static final java.util.Map<String, Identifier[]> WING_FX = new java.util.HashMap<>();
    private static final java.util.Map<String, Identifier> FEATHER_TEXTURES = new java.util.HashMap<>();
    private static final org.joml.Matrix4f SPIN = new org.joml.Matrix4f(); // Hilfsmatrix, nur Render-Thread (mulPose rechnet sie sofort ein)

    /** Eine Feder: 4x8 Pixel, flach; Textur 8x8 mit Vorder- und Rückseite. */
    private static ModelPart featherPart() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("f", CubeListBuilder.create().texOffs(0, 0).addBox(-2f, -4f, 0f, 4, 8, 0), PartPose.ZERO);
        return LayerDefinition.create(mesh, 8, 8).bakeRoot().getChild("f");
    }
}
