package de.myticlegacy.client.render;

import de.myticlegacy.client.emotes.EmotePose;
import de.myticlegacy.client.emotes.Emotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 1.8.9: Emote-Pose auf ein ModelBiped übertragen (Spielermodell und Rüstung), am Ende von setRotationAngles.
 * Gleiche Rechnung wie EmoteModel der neuen Versionen (EmotePose); Positionen ab den Grundpositionen des Modells.
 */
public final class EmoteBiped {
    private EmoteBiped() {
    }

    /** Grundpositionen (rotationPoint x, y, z) von Kopf, Körper, rechtem/linkem Arm, rechtem/linkem Bein */
    public static float[] base(ModelBiped m) {
        ModelRenderer[] parts = parts(m);
        float[] b = new float[18];
        for (int i = 0; i < 6; i++) {
            b[i * 3] = parts[i].rotationPointX;
            b[i * 3 + 1] = parts[i].rotationPointY;
            b[i * 3 + 2] = parts[i].rotationPointZ;
        }
        return b;
    }

    private static ModelRenderer[] parts(ModelBiped m) {
        return new ModelRenderer[]{m.bipedHead, m.bipedBody, m.bipedRightArm, m.bipedLeftArm, m.bipedRightLeg, m.bipedLeftLeg};
    }

    /**
     * Pose anwenden (true = Modell ist jetzt in Emote-Pose). wasPosed = beim letzten Mal angewandt (dann Positionen,
     * die Minecraft nicht selbst jedes Bild setzt, zurückstellen). age 0 = Hand in der Ich-Ansicht → nichts tun.
     */
    public static boolean apply(ModelBiped m, float[] base, boolean wasPosed, float age, Entity entity) {
        float[] p = null;
        if (entity instanceof EntityPlayer && age != 0) {
            EntityPlayer player = (EntityPlayer) entity;
            p = Emotes.pose(player.getUniqueID().toString(), player.getName(), player == Minecraft.getMinecraft().thePlayer);
        }
        if (p == null) {
            if (wasPosed) {
                m.bipedHead.rotationPointZ = base[2];
                m.bipedBody.rotationPointY = base[4];
                m.bipedBody.rotationPointZ = base[5];
                m.bipedRightArm.rotationPointY = base[7];
                m.bipedRightArm.rotationPointZ = base[8];
                m.bipedLeftArm.rotationPointY = base[10];
                m.bipedLeftArm.rotationPointZ = base[11];
            }
            return false;
        }
        ModelRenderer[] parts = parts(m);
        for (int i = 0; i < 6; i++) {
            parts[i].rotationPointX = base[i * 3];
            parts[i].rotationPointY = base[i * 3 + 1];
            parts[i].rotationPointZ = base[i * 3 + 2];
        }
        float w = p[EmotePose.WEIGHT], lean = p[EmotePose.LEAN] * w, drop = p[EmotePose.DROP] * w;
        rot(m.bipedBody, p, EmotePose.BODY, w);
        rot(m.bipedRightArm, p, EmotePose.RARM, w);
        rot(m.bipedLeftArm, p, EmotePose.LARM, w);
        rot(m.bipedRightLeg, p, EmotePose.RLEG, w);
        rot(m.bipedLeftLeg, p, EmotePose.LLEG, w);
        m.bipedHead.rotateAngleX += p[EmotePose.HEAD] * w + lean;
        m.bipedHead.rotateAngleY += p[EmotePose.HEAD + 1] * w;
        m.bipedHead.rotateAngleZ += p[EmotePose.HEAD + 2] * w;
        m.bipedBody.rotateAngleX += lean;
        m.bipedRightArm.rotateAngleX += lean;
        m.bipedLeftArm.rotateAngleX += lean;
        float neckY = EmotePose.leanDy(lean, 12), neckZ = EmotePose.leanDz(lean, 12);
        float armY = EmotePose.leanDy(lean, 10), armZ = EmotePose.leanDz(lean, 10);
        m.bipedHead.rotationPointY += drop + neckY;
        m.bipedHead.rotationPointZ += neckZ;
        m.bipedBody.rotationPointY += drop + neckY;
        m.bipedBody.rotationPointZ += neckZ;
        m.bipedRightArm.rotationPointY += drop + armY;
        m.bipedRightArm.rotationPointZ += armZ;
        m.bipedLeftArm.rotationPointY += drop + armY;
        m.bipedLeftArm.rotationPointZ += armZ;
        m.bipedRightLeg.rotationPointY += drop;
        m.bipedLeftLeg.rotationPointY += drop;
        ModelBiped.copyModelAngles(m.bipedHead, m.bipedHeadwear);
        return true;
    }

    private static void rot(ModelRenderer part, float[] p, int i, float w) {
        part.rotateAngleX = EmotePose.blend(part.rotateAngleX, p[i], w);
        part.rotateAngleY = EmotePose.blend(part.rotateAngleY, p[i + 1], w);
        part.rotateAngleZ = EmotePose.blend(part.rotateAngleZ, p[i + 2], w);
    }
}
