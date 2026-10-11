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

/** MiniMe für 1.8.9: kleine Figur mit fremdem Skin sitzt auf der linken Schulter (eigene Ebene am Spieler-Renderer). */
public final class MiniMe implements LayerRenderer<AbstractClientPlayer> {
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
                add.invoke(renderer, new MiniMe());
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("MiniMe-Ebene nicht angemeldet: " + e);
        }
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
        String name = MiniMeCore.nameOf(player.getUniqueID().toString().replace("-", ""), player == mc.thePlayer);
        MiniMeCore.Profile profile = MiniMeCore.profile(name);
        if (profile == null) return;
        ResourceLocation tex = texture(profile);
        if (tex == null) return;
        ModelPlayer model = profile.slim ? slim : wide;
        model.isRiding = true;
        model.isSneak = false;
        model.isChild = false;
        model.swingProgress = 0;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.37f, (player.isSneaking() ? 0.2f : 0f) - 0.75f * SCALE - 0.02f, 0f);
        GlStateManager.scale(SCALE, SCALE, SCALE);
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(tex);
        model.render(player, 0, 0, ageInTicks, netHeadYaw, headPitch, scale);
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
