package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.Cosmetics;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

/** 1.21 und 1.21.1: Accessoires „Kopf & Hals“ (direkt gezeichnet, Spieler statt Render-Zustand). */
public class AccessoryLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    public AccessoryLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        CosmeticsCore.Acc[] list = Cosmetics.accFor(player);
        if (list == null) return;
        for (CosmeticsCore.Acc acc : list) {
            if (acc == null) continue;
            boolean head = !"hals".equals(acc.slot);
            if (head && CosmeticsCore.hideAccUnderHelmet() && !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) continue;
            ResourceLocation texture = TEXTURES.computeIfAbsent(acc.id, id -> ResourceLocation.fromNamespaceAndPath("myticclient", "textures/acc/" + id + ".png"));
            ModelPart[] parts = AccModels.parts(acc);
            pose.pushPose();
            (head ? getParentModel().head : getParentModel().body).translateAndRotate(pose);
            pose.scale(0.5f, 0.5f, 0.5f);
            if (parts[0] != null) parts[0].render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
            if (parts[1] != null) parts[1].render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
}
