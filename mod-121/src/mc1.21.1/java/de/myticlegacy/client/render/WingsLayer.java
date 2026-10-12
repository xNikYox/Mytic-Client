package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.cosmetics.FeatherFx;
import de.myticlegacy.client.cosmetics.Cosmetics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** 1.21 und 1.21.1: Flügel auf dem Rücken (direkt gezeichnet, sonst wie in 1.21.11). */
public class WingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Teile je Flügelgröße: {links, rechts} */
    private final java.util.Map<Integer, ModelPart[]> parts = new java.util.HashMap<>();

    public WingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
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
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        String id = Cosmetics.wingsFor(player);
        if (id == null || player.isInvisible()) return;
        boolean flying = player.isFallFlying();
        boolean crouching = player.isCrouching();
        float t = ageInTicks;
        float flap = (float) Math.sin(t * (flying ? 0.55f : 0.13f)) * (flying ? 22f : 10f);
        float spread = flying ? 10f : 28f;
        float tilt = crouching ? (float) Math.toRadians(25f) : 0f;
        pose.pushPose();
        pose.translate(0f, crouching ? 0.25f : 0.1f, 0.14f);
        ModelPart[] lr = parts(id);
        ModelPart left = lr[0];
        ModelPart right = lr[1];
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
        if (CosmeticsCore.wingFeathers(id)) {
            feathers(pose, buffers, light, player.getId(), net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX()),
                    net.minecraft.util.Mth.lerp(partialTick, player.yo, player.getY()), net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ()),
                    net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot), flying, id);
        }
    }

    /** Fallende Federn (Weltpositionen aus FeatherFx, hier ins Modell-Koordinatensystem umgerechnet). */
    private static void feathers(PoseStack pose, MultiBufferSource buffers, int light, int entity, double x, double y, double z, float bodyRot,
                                 boolean flying, String id) {
        java.util.List<FeatherFx.Feather> list = FeatherFx.update(entity, x, y, z, bodyRot, flying);
        if (list == null) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("myticclient", "textures/wings/" + id + "_feather.png");
        for (FeatherFx.Feather f : list) {
            if (!(f.part instanceof ModelPart)) f.part = featherPart();
            ModelPart part = (ModelPart) f.part;
            part.setRotation(0f, (float) Math.toRadians(f.spin()), (float) Math.toRadians(f.rock()));
            float[] at = FeatherFx.local(f, x, y, z, bodyRot, 0.9375f, 1.501f);
            float size = f.size() * 1.6f;
            pose.pushPose();
            pose.translate(at[0], at[1], at[2]);
            pose.scale(size, size, size);
            part.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /** Eine Feder: 4x8 Pixel, flach; Textur 8x8 mit Vorder- und Rückseite. */
    private static ModelPart featherPart() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("f", CubeListBuilder.create().texOffs(0, 0).addBox(-2f, -4f, 0f, 4, 8, 0), PartPose.ZERO);
        return LayerDefinition.create(mesh, 8, 8).bakeRoot().getChild("f");
    }
}

