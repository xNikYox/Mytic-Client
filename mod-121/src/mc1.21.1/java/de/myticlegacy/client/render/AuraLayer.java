package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.AuraFx;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 1.21 und 1.21.1. Aura: eigene Partikel um den Spieler (Sprites aus dem Cosmetics-Paket), zur Kamera gedreht und selbstleuchtend. Die
 * Ebene macht die Modell-Drehung des Spielers rückgängig und zeichnet in Welt-Achsen relativ zu den Füßen.
 */
public class AuraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public AuraLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (entity.isInvisible() || mc.level == null || mc.player == null) return;
        CosmeticsCore.Aura aura = CosmeticsCore.auraOf(entity.getUUID().toString().replace("-", ""), entity == mc.player);
        if (aura == null) return;
        List<AuraFx.P> list = AuraFx.update(entity.getId(), aura);
        if (list == null) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("myticclient", "textures/aura/" + aura.id + ".png");
        Matrix4f camera = new Matrix4f().rotation(mc.gameRenderer.getMainCamera().rotation());
        pose.pushPose();
        // Modell-Drehung rückgängig: zurück zu Welt-Achsen an den Füßen
        pose.translate(0f, 1.501f, 0f);
        pose.scale(1f / 0.9375f, 1f / 0.9375f, 1f / 0.9375f);
        pose.scale(-1f, -1f, 1f);
        pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-(180f - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)))));
        for (AuraFx.P p : list) {
            float size = p.size();
            if (size <= 0.001f) continue;
            if (!(p.part instanceof ModelPart)) p.part = quad(p.sprite, aura.sprites);
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(camera);
            pose.mulPose(new Matrix4f().rotationZ(p.roll));
            // Modellteile zählen y nach unten: senkrecht spiegeln, damit die Sprites aufrecht stehen
            pose.scale(size, -size, size);
            ((ModelPart) p.part).render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)), 0xF000F0, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Ein Sprite als flaches Viereck, 1 Block groß; Textur je Sprite 32x16 (vorne, hinten gespiegelt). */
    static ModelPart quad(int sprite, int count) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("q", CubeListBuilder.create().texOffs(sprite * 32, 0).addBox(-8f, -8f, 0f, 16, 16, 0), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32 * Math.max(1, count), 16).bakeRoot().getChild("q");
    }
}
