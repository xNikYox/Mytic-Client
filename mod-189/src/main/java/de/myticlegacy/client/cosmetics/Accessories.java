package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Accessoires „Kopf & Hals“ für 1.8.9 (Mütze, Geweih, Schal …): Box-Modelle aus dem Katalog, die dem Kopf bzw.
 * Oberkörper folgen; Texturen aus dem Cosmetics-Paket. Die Boxen liegen in Texel-Einheiten (2 je Modell-Pixel).
 */
public final class Accessories implements LayerRenderer<AbstractClientPlayer> {
    private static boolean registered;
    /** Bilder je Name (Textur "<id>" oder Menü-Bild "<id>_icon"): hochgeladen, im Hintergrund gelesen, wird gerade gelesen */
    private static final Map<String, ResourceLocation> READY = new HashMap<String, ResourceLocation>();
    private static final Map<String, BufferedImage> DECODED = new ConcurrentHashMap<String, BufferedImage>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<String, Boolean>();
    /** Teile je Accessoire: {normale Boxen, dauerhaft leuchtende Boxen, Blinkgruppe 0, 1, 2} */
    private static final Map<String, List<ModelRenderer>[]> PARTS = new HashMap<String, List<ModelRenderer>[]>();
    private final RenderPlayer renderer;

    private Accessories(RenderPlayer renderer) {
        this.renderer = renderer;
    }

    /** Einmal (im ersten Tick): Ebene an beiden Spieler-Renderern anmelden. */
    public static void register(Minecraft mc) {
        if (registered || mc.getRenderManager() == null) return;
        registered = true;
        try {
            Method add = null;
            for (RenderPlayer renderer : mc.getRenderManager().getSkinMap().values()) {
                if (add == null) add = ReflectionHelper.findMethod(RendererLivingEntity.class, renderer, new String[]{"addLayer", "func_177094_a"}, LayerRenderer.class);
                add.invoke(renderer, new Accessories(renderer));
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("Accessoire-Ebene nicht angemeldet: " + e);
        }
    }

    /** Bild aus dem Paket (textures/acc/<name>.png) oder null, solange es im Hintergrund lädt. */
    private static ResourceLocation texture(final String name) {
        ResourceLocation tex = READY.get(name);
        if (tex != null) return tex;
        BufferedImage image = DECODED.remove(name);
        if (image != null) {
            tex = new ResourceLocation("myticclient", "acc/" + name);
            Minecraft.getMinecraft().getTextureManager().loadTexture(tex, new DynamicTexture(image));
            READY.put(name, tex);
            return tex;
        }
        if (LOADING.putIfAbsent(name, Boolean.TRUE) == null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ZipFile zip = new ZipFile(CosmeticsCore.packPath());
                        try {
                            ZipEntry entry = zip.getEntry("assets/myticclient/textures/acc/" + name + ".png");
                            InputStream in = zip.getInputStream(entry);
                            try {
                                DECODED.put(name, ImageIO.read(in));
                            } finally {
                                in.close();
                            }
                        } finally {
                            zip.close();
                        }
                    } catch (Exception e) {
                        MyticClient.LOG.warn("Accessoire " + name + " nicht geladen: " + e);
                    }
                }
            }, "Mytic Accessoire " + name);
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<ModelRenderer>[] parts(CosmeticsCore.Acc acc) {
        List<ModelRenderer>[] lists = PARTS.get(acc.id);
        if (lists != null) return lists;
        lists = new List[]{new ArrayList<ModelRenderer>(), new ArrayList<ModelRenderer>(), new ArrayList<ModelRenderer>(),
                new ArrayList<ModelRenderer>(), new ArrayList<ModelRenderer>()};
        ModelBase base = new ModelBase() {
        };
        base.textureWidth = acc.texW;
        base.textureHeight = acc.texH;
        for (CosmeticsCore.AccBox b : acc.boxes) {
            float[] m = b.minecraft();
            // 1.8.9 kennt nur eine Zugabe für alle Seiten: die der Breite (sonst Höhe, Tiefe)
            float grow = m[3] != 0 ? m[3] : m[4] != 0 ? m[4] : m[5];
            ModelRenderer part = new ModelRenderer(base, b.u, b.v);
            part.mirror = b.mirror;
            part.addBox(m[0] - m[3] + grow, m[1] - m[4] + grow, m[2] - m[5] + grow, b.tw, b.th, b.td, grow);
            lists[!b.emissive ? 0 : b.blink < 0 ? 1 : 2 + b.blink].add(part);
        }
        PARTS.put(acc.id, lists);
        return lists;
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                              float netHeadYaw, float headPitch, float scale) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player.isInvisible() || mc.thePlayer == null || !CosmeticsCore.available()) return;
        CosmeticsCore.Acc[] list = CosmeticsCore.accsOf(player.getUniqueID().toString().replace("-", ""), player == mc.thePlayer);
        if (list == null) return;
        ModelPlayer model = renderer.getMainModel();
        for (CosmeticsCore.Acc acc : list) {
            if (acc == null) continue;
            boolean head = !"hals".equals(acc.slot);
            if (head && CosmeticsCore.hideAccUnderHelmet() && player.getCurrentArmor(3) != null) continue;
            ResourceLocation tex = texture(acc.id);
            if (tex == null) continue;
            List<ModelRenderer>[] parts = parts(acc);
            GlStateManager.pushMatrix();
            if (player.isSneaking()) GlStateManager.translate(0f, 0.2f, 0f);
            (head ? model.bipedHead : model.bipedBody).postRender(0.0625f);
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(tex);
            for (ModelRenderer p : parts[0]) p.render(0.03125f);
            // leuchtende Teile (rote Nase) mit voller Helligkeit; Blinkgruppen (Lichterkette) gerade aus: normales Licht
            List<ModelRenderer> lit = new ArrayList<ModelRenderer>(parts[1]);
            for (int g = 0; g < 3; g++) {
                if (CosmeticsCore.blinkOn(g)) lit.addAll(parts[2 + g]);
                else for (ModelRenderer p : parts[2 + g]) p.render(0.03125f);
            }
            if (!lit.isEmpty()) {
                float lastX = OpenGlHelper.lastBrightnessX;
                float lastY = OpenGlHelper.lastBrightnessY;
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
                GlStateManager.disableLighting();
                for (ModelRenderer p : lit) p.render(0.03125f);
                GlStateManager.enableLighting();
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
            }
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

    /** Vorschau eines Accessoires im Cosmetics-Menü (Bild acc/<id>_icon.png, 128x128). */
    public static void drawPreview(GuiGraphics g, String id, int x, int y, int w, int h) {
        ResourceLocation tex = texture(id + "_icon");
        if (tex == null) return;
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 128, 128, w, h, 128, 128);
    }
}
