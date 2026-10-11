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

/** 26.3 (submitModel ohne CrumblingOverlay).
 * MiniMe: kleine Figur mit fremdem Skin. Sitzt auf der linken Schulter (wie der Papagei) oder auf dem Kopf, oder hängt mit
 * erhobenen Armen an der linken Hand bzw. am linken Bein.
 */
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
        String pos = ((MiniMeState) state).mytic$miniMePos();
        boolean hanging = "arm".equals(pos) || "leg".equals(pos);
        boolean onHead = "head".equals(pos);
        AvatarRenderState mini = new AvatarRenderState();
        mini.ageInTicks = state.ageInTicks;
        // auf dem Kopf dreht er sich schon mit dem Kopf mit
        mini.yRot = onHead ? 0f : state.yRot;
        mini.xRot = onHead ? 0f : state.xRot;
        mini.isPassenger = !hanging;
        mini.mainArm = state.mainArm;
        // beim Hängen beide Arme nach oben (wie beim Dreizack-Wurf)
        mini.rightArmPose = hanging ? HumanoidModel.ArmPose.THROW_TRIDENT : HumanoidModel.ArmPose.EMPTY;
        mini.leftArmPose = hanging ? HumanoidModel.ArmPose.THROW_TRIDENT : HumanoidModel.ArmPose.EMPTY;
        mini.skin = skin;
        PlayerModel model = skin.model() == PlayerModelType.SLIM ? slim : wide;
        PlayerModel parent = getParentModel();
        pose.pushPose();
        MiniMePlace.apply(pose, pos, parent.head, parent.leftArm, parent.leftLeg, state.isCrouching, SCALE);
        pose.scale(SCALE, SCALE, SCALE);
        collector.submitModel(model, mini, pose, model.renderType(skin.body().texturePath()), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
