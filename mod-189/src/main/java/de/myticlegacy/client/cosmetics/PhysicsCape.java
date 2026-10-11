package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.List;

/**
 * Umhang-Physik für 1.8.9: ersetzt die Umhang-Ebene von Minecraft. Mit eingeschaltetem Modul 16 Streifen (siehe
 * CapePhysics), sonst der normale Umhang.
 */
public final class PhysicsCape implements LayerRenderer<AbstractClientPlayer> {
    private static boolean registered;
    private final RenderPlayer renderer;
    private final LayerRenderer<AbstractClientPlayer> vanilla;
    private final ModelRenderer root;
    private final ModelRenderer[] segments = new ModelRenderer[CapePhysics.SEGMENTS];

    private PhysicsCape(RenderPlayer renderer, LayerRenderer<AbstractClientPlayer> vanilla) {
        this.renderer = renderer;
        this.vanilla = vanilla;
        ModelBase base = new ModelBase() {
        };
        base.textureWidth = 64;
        base.textureHeight = 32;
        // wie beim normalen Umhang: 2 Pixel hinter dem Körper, um 180° gedreht (Außenseite nach hinten)
        root = new ModelRenderer(base);
        root.setRotationPoint(0f, 0f, 2f);
        root.rotateAngleY = (float) Math.PI;
        ModelRenderer parent = root;
        for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
            ModelRenderer part = new ModelRenderer(base);
            // leicht vergrößert, damit beim Biegen keine Fugen aufgehen
            part.cubeList.add(new ModelBox(part, 0, i, -5f, 0f, -1f, 10, 1, 1, i < CapePhysics.SEGMENTS - 1 ? 0.15f : 0f));
            if (i > 0) part.setRotationPoint(0f, 1f, 0f);
            parent.addChild(part);
            segments[i] = part;
            parent = part;
        }
    }

    /** Einmal (im ersten Tick): die Umhang-Ebene der Spieler-Renderer ersetzen. */
    @SuppressWarnings("unchecked")
    public static void register(Minecraft mc) {
        if (registered || mc.getRenderManager() == null) return;
        registered = true;
        try {
            for (RenderPlayer renderer : mc.getRenderManager().getSkinMap().values()) {
                List<LayerRenderer<AbstractClientPlayer>> layers = (List<LayerRenderer<AbstractClientPlayer>>)
                        ReflectionHelper.getPrivateValue(RendererLivingEntity.class, renderer, "layerRenderers", "field_177097_h");
                for (int i = 0; i < layers.size(); i++) {
                    if (layers.get(i) instanceof LayerCape) layers.set(i, new PhysicsCape(renderer, layers.get(i)));
                }
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("Umhang-Physik nicht angemeldet: " + e);
        }
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                              float netHeadYaw, float headPitch, float scale) {
        if (!MyticClient.capePhysics.enabled()) {
            vanilla.doRenderLayer(player, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
            return;
        }
        if (!player.hasPlayerInfo() || player.isInvisible() || !player.isWearing(EnumPlayerModelParts.CAPE) || player.getLocationCape() == null) return;
        ModelPlayer parent = renderer.getMainModel();
        double x = player.prevPosX + (player.posX - player.prevPosX) * partialTicks;
        double y = player.prevPosY + (player.posY - player.prevPosY) * partialTicks;
        double z = player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks;
        float bodyYaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partialTicks;
        float[] angles = CapePhysics.angles(player.getEntityId(), x, y, z, bodyYaw, parent.bipedBody.rotateAngleX);
        float back = 0f, side = 0f;
        for (int i = 0; i < CapePhysics.SEGMENTS; i++) {
            segments[i].rotateAngleX = -(angles[i] - back);
            segments[i].rotateAngleZ = angles[CapePhysics.SEGMENTS + i] - side;
            back = angles[i];
            side = angles[CapePhysics.SEGMENTS + i];
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(player.getLocationCape());
        GlStateManager.pushMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
        parent.bipedBody.postRender(scale);
        root.render(scale);
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
