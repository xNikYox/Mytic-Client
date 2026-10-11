package de.myticlegacy.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

/** Eigene Texturen aus Bildern (z. B. Hauptmenü-Szene) für 1.8.9. */
public final class Tex {
    private Tex() {
    }

    /** Bild (PNG) als Textur anmelden; gibt die Kennung zum Zeichnen zurück. */
    public static Object upload(String name, byte[] png) {
        try {
            ResourceLocation id = new ResourceLocation("myticclient", "dynamic/" + name);
            Minecraft.getMinecraft().getTextureManager().loadTexture(id, new DynamicTexture(ImageIO.read(new ByteArrayInputStream(png))));
            return id;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void release(Object texture) {
        Minecraft.getMinecraft().getTextureManager().deleteTexture((ResourceLocation) texture);
    }

    public static void draw(GuiGraphics g, Object texture, int x, int y, int w, int h, int texW, int texH) {
        Minecraft.getMinecraft().getTextureManager().bindTexture((ResourceLocation) texture);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, texW, texH, w, h, texW, texH);
    }
}
