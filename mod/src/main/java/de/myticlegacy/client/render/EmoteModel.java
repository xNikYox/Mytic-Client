package de.myticlegacy.client.render;

import de.myticlegacy.client.emotes.EmotePose;
import de.myticlegacy.client.emotes.Emotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Emotes aufs Spielermodell übertragen – am Ende von PlayerModel.setupAnim (Mixin je Version). Winkel werden zur Pose
 * überblendet, der Kopf behält die Blickrichtung (plus Zusatz), Vorbeugen dreht um die Hüfte, „Hinsetzen“ senkt das
 * ganze Modell. Gilt für alle Fabric-Versionen; 1.8.9 hat ein eigenes Modell (EmotePlayerModel).
 */
public final class EmoteModel {
    private EmoteModel() {
    }

    /** Ab 1.21.2: Spieler über die Entity-ID im Render-Zustand finden. true = Pose angewandt. */
    public static boolean applyFor(HumanoidModel<?> model, int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Entity e = mc.level.getEntity(entityId);
        return e instanceof Player && apply(model, (Player) e);
    }

    /** Aktuelle Emote-Pose dieses Spielers oder null (nur Render-Thread, wird beim nächsten Aufruf überschrieben). */
    public static float[] poseOf(Player player) {
        return Emotes.pose(player.getUUID().toString(), player.getScoreboardName(), player == Minecraft.getInstance().player);
    }

    public static boolean apply(HumanoidModel<?> model, Player player) {
        float[] p = poseOf(player);
        if (p == null) return false;
        apply(model, p);
        return true;
    }

    /** Pose p (EmotePose) auf die Teile des Modells (Positionen werden addiert, Minecraft setzt sie jedes Bild neu). */
    public static void apply(HumanoidModel<?> m, float[] p) {
        float w = p[EmotePose.WEIGHT], lean = p[EmotePose.LEAN] * w, drop = p[EmotePose.DROP] * w;
        rot(m.body, p, EmotePose.BODY, w);
        rot(m.rightArm, p, EmotePose.RARM, w);
        rot(m.leftArm, p, EmotePose.LARM, w);
        rot(m.rightLeg, p, EmotePose.RLEG, w);
        rot(m.leftLeg, p, EmotePose.LLEG, w);
        m.head.xRot += p[EmotePose.HEAD] * w + lean;
        m.head.yRot += p[EmotePose.HEAD + 1] * w;
        m.head.zRot += p[EmotePose.HEAD + 2] * w;
        m.body.xRot += lean;
        m.rightArm.xRot += lean;
        m.leftArm.xRot += lean;
        float neckY = EmotePose.leanDy(lean, 12), neckZ = EmotePose.leanDz(lean, 12);
        float armY = EmotePose.leanDy(lean, 10), armZ = EmotePose.leanDz(lean, 10);
        m.head.y += drop + neckY;
        m.head.z += neckZ;
        m.body.y += drop + neckY;
        m.body.z += neckZ;
        m.rightArm.y += drop + armY;
        m.rightArm.z += armZ;
        m.leftArm.y += drop + armY;
        m.leftArm.z += armZ;
        m.rightLeg.y += drop;
        m.leftLeg.y += drop;
    }

    private static void rot(ModelPart part, float[] p, int i, float w) {
        part.xRot = EmotePose.blend(part.xRot, p[i], w);
        part.yRot = EmotePose.blend(part.yRot, p[i + 1], w);
        part.zRot = EmotePose.blend(part.zRot, p[i + 2], w);
    }
}
