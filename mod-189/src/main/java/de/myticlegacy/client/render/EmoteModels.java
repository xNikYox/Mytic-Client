package de.myticlegacy.client.render;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.List;
import java.util.Map;

/**
 * 1.8.9 (ohne Mixins): Die Spieler-Renderer bekommen ein Spielermodell und Rüstungsmodelle, die nach Minecrafts
 * Animation die Emote-Pose anwenden. Nur, wenn dort noch die Original-Modelle sitzen (andere Mods bleiben unberührt).
 */
public final class EmoteModels {
    private static boolean installed;

    private EmoteModels() {
    }

    /** Spielermodell mit Emote-Pose */
    public static final class Player extends ModelPlayer {
        private final float[] base;
        private boolean posed;

        public Player(boolean slim) {
            super(0f, slim);
            base = EmoteBiped.base(this);
        }

        @Override
        public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float yaw, float pitch, float scale, Entity entity) {
            super.setRotationAngles(limbSwing, limbSwingAmount, age, yaw, pitch, scale, entity);
            boolean was = posed;
            posed = EmoteBiped.apply(this, base, was, age, entity);
            if (posed || was) {
                copyModelAngles(bipedBody, bipedBodyWear);
                copyModelAngles(bipedRightArm, bipedRightArmwear);
                copyModelAngles(bipedLeftArm, bipedLeftArmwear);
                copyModelAngles(bipedRightLeg, bipedRightLegwear);
                copyModelAngles(bipedLeftLeg, bipedLeftLegwear);
            }
        }
    }

    /** Rüstung mit Emote-Pose */
    public static final class Armor extends ModelBiped {
        private final float[] base;
        private boolean posed;

        public Armor(float size) {
            super(size);
            base = EmoteBiped.base(this);
        }

        @Override
        public void setRotationAngles(float limbSwing, float limbSwingAmount, float age, float yaw, float pitch, float scale, Entity entity) {
            super.setRotationAngles(limbSwing, limbSwingAmount, age, yaw, pitch, scale, entity);
            posed = EmoteBiped.apply(this, base, posed, age, entity);
        }
    }

    /** Einmal, sobald die Renderer da sind (aus dem Tick). */
    @SuppressWarnings("unchecked")
    public static void install(Minecraft mc) {
        if (installed || mc.getRenderManager() == null) return;
        installed = true;
        try {
            for (Map.Entry<String, RenderPlayer> e : mc.getRenderManager().getSkinMap().entrySet()) {
                RenderPlayer renderer = e.getValue();
                ModelBase main = ReflectionHelper.getPrivateValue(RendererLivingEntity.class, renderer, "mainModel", "field_77045_g");
                if (main != null && main.getClass() == ModelPlayer.class) {
                    ReflectionHelper.setPrivateValue(RendererLivingEntity.class, renderer, new Player("slim".equals(e.getKey())), "mainModel", "field_77045_g");
                }
                List<LayerRenderer<?>> layers = ReflectionHelper.getPrivateValue(RendererLivingEntity.class, renderer, "layerRenderers", "field_177097_h");
                for (LayerRenderer<?> layer : layers) {
                    if (layer.getClass() != LayerBipedArmor.class) continue;
                    swapArmor(layer, 0.5f, "modelLeggings", "field_177189_c");
                    swapArmor(layer, 1.0f, "modelArmor", "field_177186_d");
                }
            }
        } catch (Exception ex) {
            MyticClient.LOG.warn("Emote-Modelle nicht angemeldet: " + ex);
        }
    }

    private static void swapArmor(LayerRenderer<?> layer, float size, String... names) {
        Object old = ReflectionHelper.getPrivateValue(LayerArmorBase.class, (LayerArmorBase<?>) layer, names);
        if (old != null && old.getClass() == ModelBiped.class) ReflectionHelper.setPrivateValue(LayerArmorBase.class, (LayerArmorBase<?>) layer, new Armor(size), names);
    }
}
