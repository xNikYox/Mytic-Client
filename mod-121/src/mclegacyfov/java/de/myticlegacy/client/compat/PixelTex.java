package de.myticlegacy.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Textur mit direkt gesetzten Pixeln. Fassung 1.21 und 1.21.1 (setPixelRGBA erwartet ABGR). */
public final class PixelTex {
    private final NativeImage image;
    private final DynamicTexture texture;
    public final Object id;
    public final int w, h;

    public PixelTex(String name, int w, int h) {
        this.w = w;
        this.h = h;
        image = new NativeImage(w, h, true);
        texture = new DynamicTexture(image);
        ResourceLocation rid = ResourceLocation.fromNamespaceAndPath("myticclient", "dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(rid, texture);
        id = rid;
    }

    public void set(int x, int y, int argb) {
        int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
        image.setPixelRGBA(x, y, abgr);
    }

    public void upload() {
        texture.upload();
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release((ResourceLocation) id);
    }
}
