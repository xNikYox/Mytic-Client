package de.myticlegacy.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/** Das drehende, weichgezeichnete Panorama des Minecraft-Hauptmenüs (aus GuiMainMenu von 1.8.9 übernommen). */
final class Panorama {
    private static final ResourceLocation[] PATHS = new ResourceLocation[6];
    private static DynamicTexture viewport;
    private static ResourceLocation background;
    private static final long START = System.currentTimeMillis();

    static {
        for (int i = 0; i < 6; i++) PATHS[i] = new ResourceLocation("textures/gui/title/background/panorama_" + i + ".png");
    }

    private Panorama() {
    }

    static void render(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (viewport == null) {
            viewport = new DynamicTexture(256, 256);
            background = mc.getTextureManager().getDynamicTextureLocation("mytic_background", viewport);
        }
        float time = (System.currentTimeMillis() - START) / 50f;
        GlStateManager.disableAlpha();
        mc.getFramebuffer().unbindFramebuffer();
        GlStateManager.viewport(0, 0, 256, 256);
        draw(mc, time);
        for (int i = 0; i < 7; i++) blur(mc, width, height);
        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        float f = width > height ? 120.0F / width : 120.0F / height;
        float f1 = height * f / 256.0F;
        float f2 = width * f / 256.0F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(0.0, height, 0).tex(0.5F - f1, 0.5F + f2).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        buffer.pos(width, height, 0).tex(0.5F - f1, 0.5F - f2).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        buffer.pos(width, 0.0, 0).tex(0.5F + f1, 0.5F - f2).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        buffer.pos(0.0, 0.0, 0).tex(0.5F + f1, 0.5F + f2).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        tessellator.draw();
        GlStateManager.enableAlpha();
    }

    private static void draw(Minecraft mc, float time) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(120.0F, 1.0F, 0.05F, 10.0F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int n = 8;
        for (int j = 0; j < n * n; j++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(((float) (j % n) / n - 0.5F) / 64.0F, ((float) (j / n) / n - 0.5F) / 64.0F, 0.0F);
            GlStateManager.rotate(MathHelper.sin(time / 400.0F) * 25.0F + 20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-time * 0.1F, 0.0F, 1.0F, 0.0F);
            for (int k = 0; k < 6; k++) {
                GlStateManager.pushMatrix();
                if (k == 1) GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
                if (k == 2) GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
                if (k == 3) GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
                if (k == 4) GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                if (k == 5) GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
                mc.getTextureManager().bindTexture(PATHS[k]);
                buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                int alpha = 255 / (j + 1);
                buffer.pos(-1.0, -1.0, 1.0).tex(0.0, 0.0).color(255, 255, 255, alpha).endVertex();
                buffer.pos(1.0, -1.0, 1.0).tex(1.0, 0.0).color(255, 255, 255, alpha).endVertex();
                buffer.pos(1.0, 1.0, 1.0).tex(1.0, 1.0).color(255, 255, 255, alpha).endVertex();
                buffer.pos(-1.0, 1.0, 1.0).tex(0.0, 1.0).color(255, 255, 255, alpha).endVertex();
                tessellator.draw();
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
            GlStateManager.colorMask(true, true, true, false);
        }
        buffer.setTranslation(0.0, 0.0, 0.0);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    private static void blur(Minecraft mc, int width, int height) {
        mc.getTextureManager().bindTexture(background);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 256, 256);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.colorMask(true, true, true, false);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        GlStateManager.disableAlpha();
        for (int j = 0; j < 3; j++) {
            float a = 1.0F / (j + 1);
            float shift = (j - 1) / 256.0F;
            buffer.pos(width, height, 0).tex(0.0F + shift, 1.0).color(1.0F, 1.0F, 1.0F, a).endVertex();
            buffer.pos(width, 0.0, 0).tex(1.0F + shift, 1.0).color(1.0F, 1.0F, 1.0F, a).endVertex();
            buffer.pos(0.0, 0.0, 0).tex(1.0F + shift, 0.0).color(1.0F, 1.0F, 1.0F, a).endVertex();
            buffer.pos(0.0, height, 0).tex(0.0F + shift, 0.0).color(1.0F, 1.0F, 1.0F, a).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableAlpha();
        GlStateManager.colorMask(true, true, true, true);
    }
}
