package de.myticlegacy.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;

/** Eigene Texturen aus Bildern (z. B. Hauptmenü-Szene). Diese Fassung gilt ab 1.21.5; ältere haben eine eigene Tex.java. */
public final class Tex {
    private Tex() {
    }

    /** Bild (PNG) als Textur anmelden; gibt die Kennung zum Zeichnen zurück. */
    public static Object upload(String name, byte[] png) {
        try {
            NativeImage native_ = NativeImage.read(new ByteArrayInputStream(png));
            Identifier id = Identifier.fromNamespaceAndPath("myticclient", "dynamic/" + name);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "mytic " + name, native_));
            return id;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void release(Object texture) {
        Minecraft.getInstance().getTextureManager().release((Identifier) texture);
    }

    public static void draw(GuiGraphics g, Object texture, int x, int y, int w, int h, int texW, int texH) {
        Blit.region(g, (Identifier) texture, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }

    /** Ausschnitt einer eigenen Textur eingefärbt zeichnen (ARGB wird mit den Texturfarben multipliziert). */
    public static void drawTinted(GuiGraphics g, Object texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH, int argb) {
        if ((argb >>> 24) == 0) return;
        Blit.tint(g, (Identifier) texture, x, y, w, h, u, v, uw, vh, texW, texH, argb);
    }
}
