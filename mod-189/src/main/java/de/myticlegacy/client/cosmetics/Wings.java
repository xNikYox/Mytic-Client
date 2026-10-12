package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

/** Flügel für 1.8.9: zwei flache Teile auf dem Rücken mit animierter Leucht-Ebene (Bilder aus dem Cosmetics-Paket). */
public final class Wings implements LayerRenderer<AbstractClientPlayer> {
    private static boolean registered;
    /** fertig hochgeladene Bilder je Flügel: [0] Grundbild, [1..] Leucht-Ebene */
    private static final Map<String, ResourceLocation[]> READY = new HashMap<String, ResourceLocation[]>();
    private static final Map<String, BufferedImage[]> DECODED = new ConcurrentHashMap<String, BufferedImage[]>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<String, Boolean>();
    /** Teile je Flügelgröße: {links, rechts} */
    private final Map<Integer, ModelRenderer[]> parts = new HashMap<Integer, ModelRenderer[]>();

    private Wings() {
    }

    /** Ein Flügelpaar der Größe w x h Pixel (meist 20x24), Ansatz an der Wirbelsäule. */
    private ModelRenderer[] parts(String id) {
        int[] size = CosmeticsCore.wingSize(id);
        int key = size[0] * 1000 + size[1];
        ModelRenderer[] lr = parts.get(key);
        if (lr == null) {
            ModelBase base = new ModelBase() {
            };
            base.textureWidth = 2 * size[0];
            base.textureHeight = size[1];
            ModelRenderer left = new ModelRenderer(base, 0, 0);
            left.addBox(0f, -10f, 0f, size[0], size[1], 0);
            ModelRenderer right = new ModelRenderer(base, 0, 0);
            right.mirror = true;
            right.addBox(-size[0], -10f, 0f, size[0], size[1], 0);
            lr = new ModelRenderer[]{left, right};
            parts.put(key, lr);
        }
        return lr;
    }

    /** Einmal (im ersten Tick): Ebene an beiden Spieler-Renderern anmelden. */
    public static void register(Minecraft mc) {
        if (registered || mc.getRenderManager() == null) return;
        registered = true;
        try {
            Method add = null;
            for (RenderPlayer renderer : mc.getRenderManager().getSkinMap().values()) {
                if (add == null) add = ReflectionHelper.findMethod(RendererLivingEntity.class, renderer, new String[]{"addLayer", "func_177094_a"}, LayerRenderer.class);
                add.invoke(renderer, new Wings());
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("Flügel-Ebene nicht angemeldet: " + e);
        }
    }

    /**
     * Bilder eines Flügels (Grundbild und Leucht-Ebene) oder null, solange sie im Hintergrund laden. icon: die kleinen
     * Vorschaubilder fürs Menü.
     */
    private static ResourceLocation[] textures(final String id, final boolean icon) {
        final String key = icon ? id + "_icon" : id;
        ResourceLocation[] tex = READY.get(key);
        if (tex != null) return tex;
        BufferedImage[] images = DECODED.remove(key);
        if (images == null) {
            if (LOADING.putIfAbsent(key, Boolean.TRUE) == null) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ZipFile zip = new ZipFile(CosmeticsCore.packPath());
                            try {
                                BufferedImage[] all = new BufferedImage[CosmeticsCore.WING_FX_FRAMES + 1];
                                for (int i = 0; i < all.length; i++) {
                                    String name = i == 0 ? key : key + "_fx_" + (i - 1);
                                    InputStream in = zip.getInputStream(zip.getEntry("assets/myticclient/textures/wings/" + name + ".png"));
                                    try {
                                        all[i] = ImageIO.read(in);
                                    } finally {
                                        in.close();
                                    }
                                }
                                DECODED.put(key, all);
                            } finally {
                                zip.close();
                            }
                        } catch (Exception e) {
                            MyticClient.LOG.warn("Flügel " + id + " nicht geladen: " + e);
                        }
                    }
                }, "Mytic Flügel " + id);
                t.setDaemon(true);
                t.start();
            }
            return null;
        }
        tex = new ResourceLocation[images.length];
        for (int i = 0; i < images.length; i++) {
            tex[i] = new ResourceLocation("myticclient", "wings/" + key + "_" + i);
            Minecraft.getMinecraft().getTextureManager().loadTexture(tex[i], new DynamicTexture(images[i]));
        }
        READY.put(key, tex);
        return tex;
    }

    private static int fxFrame() {
        return 1 + (int) ((System.currentTimeMillis() / CosmeticsCore.WING_FX_MS) % CosmeticsCore.WING_FX_FRAMES);
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                              float netHeadYaw, float headPitch, float scale) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player.isInvisible() || mc.thePlayer == null || !CosmeticsCore.available()) return;
        String id = CosmeticsCore.wingsOf(player.getUniqueID().toString().replace("-", ""), player == mc.thePlayer);
        if (id == null) return;
        ResourceLocation[] tex = textures(id, false);
        if (tex == null) return;
        // 1.8.9 kennt kein Elytra-Fliegen: schneller schlagen, wenn der Spieler fällt
        boolean flying = !player.onGround && player.motionY < -0.5;
        boolean crouching = player.isSneaking();
        float flap = (float) Math.sin(ageInTicks * (flying ? 0.55f : 0.13f)) * (flying ? 22f : 10f);
        float spread = flying ? 10f : 28f;
        float tilt = crouching ? (float) Math.toRadians(25f) : 0f;
        ModelRenderer[] lr = parts(id);
        ModelRenderer left = lr[0];
        ModelRenderer right = lr[1];
        left.rotationPointX = 1f;
        left.rotateAngleX = tilt;
        left.rotateAngleY = (float) Math.toRadians(-(spread + flap));
        left.rotateAngleZ = (float) Math.toRadians(-6f);
        right.rotationPointX = -1f;
        right.rotateAngleX = tilt;
        right.rotateAngleY = (float) Math.toRadians(spread + flap);
        right.rotateAngleZ = (float) Math.toRadians(6f);

        GlStateManager.pushMatrix();
        GlStateManager.translate(0f, crouching ? 0.25f : 0.1f, 0.14f);
        GlStateManager.disableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(tex[0]);
        left.render(scale);
        right.render(scale);
        // Leucht-Ebene: volle Helligkeit, durchscheinend darüber
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        mc.getTextureManager().bindTexture(tex[fxFrame()]);
        left.render(scale);
        right.render(scale);
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

    /** Vorschau eines Flügelpaars im Cosmetics-Menü (kleines Vorschaubild) mit animiertem Glanz. */
    public static void drawPreview(GuiGraphics g, String id, int x, int y, int w, int h) {
        ResourceLocation[] tex = textures(id, true);
        if (tex == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(tex[0]);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 240, 144, w, h, 240, 144);
        mc.getTextureManager().bindTexture(tex[fxFrame()]);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 240, 144, w, h, 240, 144);
    }
}
