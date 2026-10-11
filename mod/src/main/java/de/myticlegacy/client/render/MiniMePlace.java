package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Wo der MiniMe am Spieler sitzt oder hängt (gleich für alle Versionen mit ModelPart). */
public final class MiniMePlace {
    private MiniMePlace() {
    }

    /**
     * Verschiebt die Pose so, dass die kleine Figur (danach mit scale verkleinert) richtig sitzt: auf dem Kopf und der
     * Schulter mit der Hüfte (12/16 über den Füßen), beim Hängen mit den erhobenen Händen (8/16 über dem Hals).
     */
    public static void apply(PoseStack pose, String pos, ModelPart head, ModelPart leftArm, ModelPart leftLeg, boolean crouching, float scale) {
        if ("head".equals(pos)) {
            head.translateAndRotate(pose);
            pose.translate(0f, -0.5f - 0.75f * scale, 0f);
        } else if ("arm".equals(pos)) {
            leftArm.translateAndRotate(pose);
            pose.translate(0.0625f, 0.625f + 0.5f * scale, 0f);
        } else if ("leg".equals(pos)) {
            // außen am linken Bein, Hände knapp unter der Hüfte
            leftLeg.translateAndRotate(pose);
            pose.translate(0.24f, 0.05f + 0.5f * scale, 0f);
        } else {
            pose.translate(0.37f, (crouching ? 0.2f : 0f) - 0.75f * scale - 0.02f, 0f);
        }
    }

    /** Beide Arme gerade nach oben (zum Festhalten). */
    public static void armsUp(HumanoidModel<?> model) {
        for (ModelPart arm : new ModelPart[]{model.rightArm, model.leftArm}) {
            arm.xRot = (float) Math.PI;
            arm.yRot = 0f;
            arm.zRot = 0f;
        }
    }
}
