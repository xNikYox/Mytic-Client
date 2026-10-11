package de.myticlegacy.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;

/** Eigene Texturen aus Bildern (z. B. Hauptmenü-Szene). Fassung bis 1.21.4 (DynamicTexture ohne Namen). */
public final class Tex {
    private Tex() {
    }

    /** Bild (PNG) als Textur anmelden; gibt die Kennung zum Zeichnen zurück. */
    public static Object upload(String name, byte[] png) {
        try {
            NativeImage native_ = NativeImage.read(new ByteArrayInputStream(png));
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("myticclient", "dynamic/" + name);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(native_));
            return id;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void release(Object texture) {
        Minecraft.getInstance().getTextureManager().release((ResourceLocation) texture);
    }

    public static void draw(GuiGraphics g, Object texture, int x, int y, int w, int h, int texW, int texH) {
        Blit.region(g, (ResourceLocation) texture, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }
}
