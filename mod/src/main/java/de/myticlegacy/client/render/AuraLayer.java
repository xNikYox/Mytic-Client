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
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Aura: eigene Partikel um den Spieler (Sprites aus dem Cosmetics-Paket), zur Kamera gedreht und selbstleuchtend. Die
 * Ebene macht die Modell-Drehung des Spielers rückgängig und zeichnet in Welt-Achsen relativ zu den Füßen.
 */
public class AuraLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public AuraLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (state.isInvisible || mc.level == null || mc.player == null) return;
        Entity entity = mc.level.getEntity(state.id);
        if (entity == null) return;
        CosmeticsCore.Aura aura = CosmeticsCore.auraOf(entity.getUUID().toString().replace("-", ""), entity == mc.player);
        if (aura == null) return;
        List<AuraFx.P> list = AuraFx.update(state.id, aura);
        if (list == null) return;
        Identifier texture = Identifier.fromNamespaceAndPath("myticclient", "textures/aura/" + aura.id + ".png");
        Matrix4f camera = new Matrix4f().rotation(mc.gameRenderer.getMainCamera().rotation());
        pose.pushPose();
        // Modell-Drehung rückgängig: zurück zu Welt-Achsen an den Füßen
        pose.translate(0f, 1.501f, 0f);
        pose.scale(1f / 0.9375f, 1f / 0.9375f, 1f / 0.9375f);
        pose.scale(-1f, -1f, 1f);
        pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-(180f - state.bodyRot))));
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
            collector.submitModelPart((ModelPart) p.part, pose, RenderTypes.entityTranslucentEmissive(texture), 0xF000F0, OverlayTexture.NO_OVERLAY, null);
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
