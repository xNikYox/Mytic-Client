package de.myticlegacy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.AccState;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Accessoires „Kopf & Hals“ (Mütze, Geweih, Schal …): Box-Modelle, die Kopf bzw. Oberkörper folgen. */
public class AccessoryLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    public AccessoryLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        CosmeticsCore.Acc[] list = ((AccState) state).mytic$acc();
        if (list == null || state.isInvisible) return;
        for (CosmeticsCore.Acc acc : list) {
            if (acc == null) continue;
            boolean head = !"hals".equals(acc.slot);
            if (head && CosmeticsCore.hideAccUnderHelmet() && !state.headEquipment.isEmpty()) continue;
            Identifier texture = TEXTURES.computeIfAbsent(acc.id, id -> Identifier.fromNamespaceAndPath("myticclient", "textures/acc/" + id + ".png"));
            ModelPart[] parts = AccModels.parts(acc);
            pose.pushPose();
            getParentModel().root().translateAndRotate(pose);
            (head ? getParentModel().head : getParentModel().body).translateAndRotate(pose);
            pose.scale(0.5f, 0.5f, 0.5f);
            // leuchtende Teile (rote Nase) mit voller Helligkeit, Blinkgruppen (Lichterkette) im Takt
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null) collector.submitModelPart(parts[i], pose, RenderTypes.entityCutoutNoCull(texture), AccModels.light(i, light, FULL_BRIGHT), OverlayTexture.NO_OVERLAY, null);
            }
            pose.popPose();
        }
    }
}
