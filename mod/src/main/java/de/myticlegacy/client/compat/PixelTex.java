package de.myticlegacy.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Textur, deren Pixel man direkt setzt und gesammelt hochlädt (Minimap, Weltkarte). Diese Fassung gilt ab 1.21.5;
 * ältere Versionen haben eine eigene PixelTex.java (andere DynamicTexture bzw. setPixelRGBA).
 */
public final class PixelTex {
    private final NativeImage image;
    private final DynamicTexture texture;
    /** Kennung zum Zeichnen über {@link Tex#drawTinted} */
    public final Object id;
    public final int w, h;

    public PixelTex(String name, int w, int h) {
        this.w = w;
        this.h = h;
        image = new NativeImage(w, h, true);
        texture = new DynamicTexture(() -> "mytic " + name, image);
        Identifier rid = Identifier.fromNamespaceAndPath("myticclient", "dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(rid, texture);
        id = rid;
    }

    /** Pixel setzen (ARGB) */
    public void set(int x, int y, int argb) {
        image.setPixel(x, y, argb);
    }

    /** Geänderte Pixel zur Grafikkarte schicken */
    public void upload() {
        texture.upload();
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release((Identifier) id);
    }
}
