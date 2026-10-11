package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.MiniMeState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;

/** 1.21.2–1.21.8: MiniMe auf Schulter oder Kopf, oder hängend an Hand bzw. Bein (direkt gezeichnet). */
public class MiniMeLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    private static final float SCALE = 0.35f;
    private final PlayerModel wide;
    private final PlayerModel slim;

    public MiniMeLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
        wide = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
        PlayerSkin skin = ((MiniMeState) state).mytic$miniMe();
        if (skin == null || state.isInvisible) return;
        String pos = ((MiniMeState) state).mytic$miniMePos();
        boolean hanging = "arm".equals(pos) || "leg".equals(pos);
        boolean onHead = "head".equals(pos);
        PlayerRenderState mini = new PlayerRenderState();
        mini.ageInTicks = state.ageInTicks;
        mini.yRot = onHead ? 0f : state.yRot;
        mini.xRot = onHead ? 0f : state.xRot;
        mini.isPassenger = !hanging;
        mini.mainArm = state.mainArm;
        mini.skin = skin;
        PlayerModel model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        PlayerModel parent = getParentModel();
        pose.pushPose();
        MiniMePlace.apply(pose, pos, parent.head, parent.leftArm, parent.leftLeg, state.isCrouching, SCALE);
        pose.scale(SCALE, SCALE, SCALE);
        model.setupAnim(mini);
        if (hanging) MiniMePlace.armsUp(model);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(skin.texture())), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
