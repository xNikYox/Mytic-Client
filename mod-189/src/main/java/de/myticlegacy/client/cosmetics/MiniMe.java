package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * MiniMe für 1.8.9: kleine Figur mit fremdem Skin auf Schulter oder Kopf, oder hängend an Hand bzw. Bein, je Stelle ein
 * eigener (eigene Ebene am Spieler-Renderer).
 */
public final class MiniMe implements LayerRenderer<AbstractClientPlayer> {
    private final RenderPlayer renderer;
    private static final float SCALE = 0.35f;
    /** Profil-UUID → hochgeladener Skin */
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();
    private static boolean registered;
    private final ModelPlayer wide = new ModelPlayer(0f, false);
    private final ModelPlayer slim = new ModelPlayer(0f, true);

    /** Einmal (im ersten Tick): Ebene an beiden Spieler-Renderern (normal und slim) anmelden. */
    public static void register(Minecraft mc) {
        if (registered || mc.getRenderManager() == null) return;
        registered = true;
        try {
            Method add = null;
            for (RenderPlayer renderer : mc.getRenderManager().getSkinMap().values()) {
                if (add == null) add = ReflectionHelper.findMethod(RendererLivingEntity.class, renderer, new String[]{"addLayer", "func_177094_a"}, LayerRenderer.class);
                add.invoke(renderer, new MiniMe(renderer));
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("MiniMe-Ebene nicht angemeldet: " + e);
        }
    }

    private MiniMe(RenderPlayer renderer) {
        this.renderer = renderer;
    }

    private static ResourceLocation texture(MiniMeCore.Profile profile) {
        ResourceLocation tex = TEXTURES.get(profile.uuid);
        if (tex != null || profile.png == null) return tex;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(profile.png));
            // wandelt alte 64x32-Skins um und setzt die Transparenz wie Minecraft
            img = new ImageBufferDownload().parseUserSkin(img);
            tex = new ResourceLocation("myticclient", "minime/" + profile.uuid);
            Minecraft.getMinecraft().getTextureManager().loadTexture(tex, new DynamicTexture(img));
        } catch (Exception e) {
            MyticClient.LOG.warn("MiniMe-Skin nicht lesbar: " + e);
            tex = new ResourceLocation("textures/entity/steve.png");
        }
        TEXTURES.put(profile.uuid, tex);
        return tex;
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                              float netHeadYaw, float headPitch, float scale) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player.isInvisible() || mc.thePlayer == null) return;
        String uuid = player.getUniqueID().toString().replace("-", "");
        for (String[] p : MiniMeCore.POSITIONS) {
            String name = MiniMeCore.nameOf(uuid, player == mc.thePlayer, p[0]);
            MiniMeCore.Profile profile = MiniMeCore.profile(name);
            if (profile == null) continue;
            ResourceLocation tex = texture(profile);
            if (tex != null) renderOne(mc, player, profile, tex, p[0], ageInTicks, netHeadYaw, headPitch, scale);
        }
    }

    private void renderOne(Minecraft mc, AbstractClientPlayer player, MiniMeCore.Profile profile, ResourceLocation tex, String pos, float ageInTicks,
                           float netHeadYaw, float headPitch, float scale) {
        boolean hanging = "arm".equals(pos) || "leg".equals(pos);
        boolean onHead = "head".equals(pos);
        ModelPlayer model = profile.slim ? slim : wide;
        ModelPlayer parent = renderer.getMainModel();
        model.isRiding = !hanging;
        model.isSneak = false;
        model.isChild = false;
        model.swingProgress = 0;
        model.heldItemLeft = 0;
        model.heldItemRight = 0;
        model.aimedBow = false;
        GlStateManager.pushMatrix();
        if (onHead) {
            parent.bipedHead.postRender(scale);
            GlStateManager.translate(0f, -0.5f - 0.75f * SCALE, 0f);
        } else if ("arm".equals(pos)) {
            // wie beim Gegenstand in der Hand: beim Schleichen etwas tiefer
            if (player.isSneaking()) GlStateManager.translate(0f, 0.2f, 0f);
            parent.bipedLeftArm.postRender(scale);
            GlStateManager.translate(0.0625f, 0.625f + 0.5f * SCALE, 0f);
        } else if ("leg".equals(pos)) {
            // außen am rechten Bein
            parent.bipedRightLeg.postRender(scale);
            GlStateManager.translate(-0.24f, 0.05f + 0.5f * SCALE, 0f);
        } else {
            GlStateManager.translate(0.37f, (player.isSneaking() ? 0.2f : 0f) - 0.75f * SCALE - 0.02f, 0f);
        }
        GlStateManager.scale(SCALE, SCALE, SCALE);
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(tex);
        // erst die Haltung berechnen, dann (beim Hängen) die Arme nach oben, dann zeichnen
        model.setRotationAngles(0, 0, ageInTicks, onHead ? 0 : netHeadYaw, onHead ? 0 : headPitch, scale, player);
        if (hanging) {
            for (net.minecraft.client.model.ModelRenderer arm : new net.minecraft.client.model.ModelRenderer[]{model.bipedRightArm, model.bipedLeftArm}) {
                arm.rotateAngleX = (float) Math.PI;
                arm.rotateAngleY = 0f;
                arm.rotateAngleZ = 0f;
            }
            copy(model.bipedRightArm, model.bipedRightArmwear);
            copy(model.bipedLeftArm, model.bipedLeftArmwear);
        }
        renderParts(model, scale);
        GlStateManager.popMatrix();
    }

    private static void copy(net.minecraft.client.model.ModelRenderer from, net.minecraft.client.model.ModelRenderer to) {
        to.rotateAngleX = from.rotateAngleX;
        to.rotateAngleY = from.rotateAngleY;
        to.rotateAngleZ = from.rotateAngleZ;
    }

    /** Wie ModelPlayer.render, aber ohne setRotationAngles (das würde die erhobenen Arme zurücksetzen). */
    private static void renderParts(ModelPlayer m, float scale) {
        m.bipedHead.render(scale);
        m.bipedBody.render(scale);
        m.bipedRightArm.render(scale);
        m.bipedLeftArm.render(scale);
        m.bipedRightLeg.render(scale);
        m.bipedLeftLeg.render(scale);
        m.bipedHeadwear.render(scale);
        m.bipedBodyWear.render(scale);
        m.bipedRightArmwear.render(scale);
        m.bipedLeftArmwear.render(scale);
        m.bipedRightLegwear.render(scale);
        m.bipedLeftLegwear.render(scale);
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
