package de.myticlegacy.client.mixin;

import de.myticlegacy.client.render.EmoteModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21 und 1.21.1: Emote-Pose nach setupAnim(Entity, …). Hier setzt Minecraft nicht alle Positionen jedes Bild neu und
 * Hut/Ärmel/Hose sind eigene Teile – deshalb erst die Grundpositionen herstellen und danach die Kopien angleichen.
 */
@Mixin(PlayerModel.class)
abstract class PlayerModelEmoteMixin {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void mytic$emote(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        // Hand in der Ich-Ansicht (ageInTicks 0) nicht anfassen
        if (!(entity instanceof Player) || ageInTicks == 0) return;
        float[] pose = EmoteModel.poseOf((Player) entity);
        boolean wasPosed = mytic$posed;
        mytic$posed = pose != null;
        if (pose == null && !wasPosed) return;
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        // Grundpositionen herstellen (die Winkel von Minecraft bleiben), dann die Pose darauf
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        float[] rot = new float[18];
        for (int i = 0; i < parts.length; i++) {
            rot[i * 3] = parts[i].xRot;
            rot[i * 3 + 1] = parts[i].yRot;
            rot[i * 3 + 2] = parts[i].zRot;
            parts[i].resetPose();
            parts[i].xRot = rot[i * 3];
            parts[i].yRot = rot[i * 3 + 1];
            parts[i].zRot = rot[i * 3 + 2];
        }
        if (pose != null) EmoteModel.apply(m, pose);
        m.hat.copyFrom(m.head);
        jacket.copyFrom(m.body);
        leftSleeve.copyFrom(m.leftArm);
        rightSleeve.copyFrom(m.rightArm);
        leftPants.copyFrom(m.leftLeg);
        rightPants.copyFrom(m.rightLeg);
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean mytic$posed;
}
