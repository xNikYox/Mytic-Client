package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.MiniMeState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** 26.3 (submitModel ohne CrumblingOverlay). MiniMe: kleine Figur mit fremdem Skin sitzt auf der linken Schulter (wie der Papagei) und schaut in dieselbe Richtung. */
public class MiniMeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final float SCALE = 0.35f;
    private final PlayerModel wide;
    private final PlayerModel slim;

    public MiniMeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
        wide = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        PlayerSkin skin = ((MiniMeState) state).mytic$miniMe();
        if (skin == null || state.isInvisible) return;
        // eigener Zustand: sitzend, gleiche Kopfhaltung wie der Spieler
        AvatarRenderState mini = new AvatarRenderState();
        mini.ageInTicks = state.ageInTicks;
        mini.yRot = state.yRot;
        mini.xRot = state.xRot;
        mini.isPassenger = true;
        mini.mainArm = state.mainArm;
        mini.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        mini.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        mini.skin = skin;
        PlayerModel model = skin.model() == PlayerModelType.SLIM ? slim : wide;
        pose.pushPose();
        // Hüfte der kleinen Figur (12/16 über den Füßen) auf die linke Schulter setzen
        pose.translate(0.37f, (state.isCrouching ? 0.2f : 0f) - 0.75f * SCALE - 0.02f, 0f);
        pose.scale(SCALE, SCALE, SCALE);
        collector.submitModel(model, mini, pose, model.renderType(skin.body().texturePath()), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
