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

/** 1.21.2–1.21.8: MiniMe sitzt auf der linken Schulter (direkt gezeichnet). */
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
        PlayerRenderState mini = new PlayerRenderState();
        mini.ageInTicks = state.ageInTicks;
        mini.yRot = state.yRot;
        mini.xRot = state.xRot;
        mini.isPassenger = true;
        mini.mainArm = state.mainArm;
        mini.skin = skin;
        PlayerModel model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        pose.pushPose();
        pose.translate(0.37f, (state.isCrouching ? 0.2f : 0f) - 0.75f * SCALE - 0.02f, 0f);
        pose.scale(SCALE, SCALE, SCALE);
        model.setupAnim(mini);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(skin.texture())), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
