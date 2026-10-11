package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.MiniMe;
import de.myticlegacy.client.cosmetics.MiniMeCore;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;

/** 1.21 und 1.21.1: MiniMe auf Schulter oder Kopf, oder hängend an Hand bzw. Bein. */
public class MiniMeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float SCALE = 0.35f;
    private final PlayerModel<AbstractClientPlayer> wide;
    private final PlayerModel<AbstractClientPlayer> slim;

    public MiniMeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        PlayerSkin[] skins = MiniMe.skinsFor(player);
        if (skins == null || player.isInvisible()) return;
        for (int i = 0; i < skins.length; i++) {
            if (skins[i] != null) renderOne(pose, buffers, light, player, ageInTicks, netHeadYaw, headPitch, skins[i], MiniMeCore.POSITIONS[i][0]);
        }
    }

    private void renderOne(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float ageInTicks, float netHeadYaw,
                           float headPitch, PlayerSkin skin, String pos) {
        boolean hanging = "arm".equals(pos) || "leg".equals(pos);
        boolean onHead = "head".equals(pos);
        PlayerModel<AbstractClientPlayer> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        model.riding = !hanging;
        model.crouching = false;
        model.young = false;
        model.attackTime = 0;
        model.setupAnim(player, 0, 0, ageInTicks, onHead ? 0 : netHeadYaw, onHead ? 0 : headPitch);
        if (hanging) MiniMePlace.armsUp(model);
        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        pose.pushPose();
        MiniMePlace.apply(pose, pos, parent.head, parent.leftArm, parent.rightLeg, player.isCrouching(), SCALE);
        pose.scale(SCALE, SCALE, SCALE);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(skin.texture())), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
