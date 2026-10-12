package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

/** Auren für 1.8.9: eigene Partikel (Sprites aus dem Cosmetics-Paket), zur Kamera gedreht, mit voller Helligkeit. */
public final class AuraLayer implements LayerRenderer<AbstractClientPlayer> {
    private static boolean registered;
    private static final Map<String, ResourceLocation> READY = new HashMap<String, ResourceLocation>();
    private static final Map<String, BufferedImage> DECODED = new ConcurrentHashMap<String, BufferedImage>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<String, Boolean>();

    private AuraLayer() {
    }

    public static void register(Minecraft mc) {
        if (registered || mc.getRenderManager() == null) return;
        registered = true;
        try {
            Method add = null;
            for (RenderPlayer renderer : mc.getRenderManager().getSkinMap().values()) {
                if (add == null) add = ReflectionHelper.findMethod(RendererLivingEntity.class, renderer, new String[]{"addLayer", "func_177094_a"}, LayerRenderer.class);
                add.invoke(renderer, new AuraLayer());
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("Aura-Ebene nicht angemeldet: " + e);
        }
    }

    private static ResourceLocation texture(final String id) {
        ResourceLocation tex = READY.get(id);
        if (tex != null) return tex;
        BufferedImage image = DECODED.remove(id);
        if (image != null) {
            tex = new ResourceLocation("myticclient", "aura/" + id);
            Minecraft.getMinecraft().getTextureManager().loadTexture(tex, new DynamicTexture(image));
            READY.put(id, tex);
            return tex;
        }
        if (LOADING.putIfAbsent(id, Boolean.TRUE) == null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ZipFile zip = new ZipFile(CosmeticsCore.packPath());
                        try {
                            InputStream in = zip.getInputStream(zip.getEntry("assets/myticclient/textures/aura/" + id + ".png"));
                            try {
                                DECODED.put(id, ImageIO.read(in));
                            } finally {
                                in.close();
                            }
                        } finally {
                            zip.close();
                        }
                    } catch (Exception e) {
                        MyticClient.LOG.warn("Aura " + id + " nicht geladen: " + e);
                    }
                }
            }, "Mytic Aura " + id);
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                              float netHeadYaw, float headPitch, float scale) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player.isInvisible() || mc.thePlayer == null) return;
        CosmeticsCore.Aura aura = CosmeticsCore.auraOf(player.getUniqueID().toString().replace("-", ""), player == mc.thePlayer);
        if (aura == null) return;
        List<AuraFx.P> list = AuraFx.update(player.getEntityId(), aura);
        ResourceLocation tex = texture(aura.id);
        if (list == null || tex == null) return;
        RenderManager rm = mc.getRenderManager();
        float bodyYaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
        mc.getTextureManager().bindTexture(tex);
        GlStateManager.pushMatrix();
        // Modell-Drehung rückgängig: zurück zu Welt-Achsen an den Füßen
        GlStateManager.translate(0f, 1.5078125f, 0f);
        GlStateManager.scale(1f / 0.9375f, 1f / 0.9375f, 1f / 0.9375f);
        GlStateManager.scale(-1f, -1f, 1f);
        GlStateManager.rotate(-(180f - bodyYaw), 0f, 1f, 0f);
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.color(1f, 1f, 1f, 1f);
        float du = 1f / Math.max(1, aura.sprites);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        for (AuraFx.P p : list) {
            float s = p.size() / 2f;
            if (s <= 0.0005f) continue;
            float u0 = p.sprite * du, u1 = u0 + du / 2f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(p.x, p.y, p.z);
            GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
            GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
            GlStateManager.rotate((float) Math.toDegrees(p.roll), 0f, 0f, 1f);
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            wr.pos(-s, -s, 0).tex(u1, 1).endVertex();
            wr.pos(s, -s, 0).tex(u0, 1).endVertex();
            wr.pos(s, s, 0).tex(u0, 0).endVertex();
            wr.pos(-s, s, 0).tex(u1, 0).endVertex();
            tess.draw();
            GlStateManager.popMatrix();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
