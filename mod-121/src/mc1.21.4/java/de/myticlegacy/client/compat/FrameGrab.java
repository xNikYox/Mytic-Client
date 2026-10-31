package de.myticlegacy.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import de.myticlegacy.client.clips.ClipRecorder;
import net.minecraft.client.Minecraft;

/**
 * Bild für Clips holen (1.21–1.21.4, direktes OpenGL): GlGrab verkleinert den Framebuffer des Haupt-Ziels auf der
 * Grafikkarte und liest ihn ohne Warten aus.
 */
public final class FrameGrab {
    private FrameGrab() {
    }

    /** GlGrab dreht die Bilder schon richtig herum */
    public static boolean flipped() {
        return false;
    }

    /** hier immer OpenGL */
    public static boolean gpuScale() {
        return true;
    }

    /** Für GlSaturation: Framebuffer des Haupt-Ziels */
    public static int glFramebuffer() {
        return Minecraft.getInstance().getMainRenderTarget().frameBufferId;
    }

    public static int glTexture() {
        return Minecraft.getInstance().getMainRenderTarget().getColorTextureId();
    }

    public static int width() {
        return Minecraft.getInstance().getMainRenderTarget().width;
    }

    public static int height() {
        return Minecraft.getInstance().getMainRenderTarget().height;
    }

    public static void grab() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        int w = target.width, h = target.height;
        if (w != ClipRecorder.sourceWidth() || h != ClipRecorder.sourceHeight()) return; // Größe geändert: Aufnahme startet gleich neu
        GlGrab.grab(target.frameBufferId, 0, w, h, ClipRecorder.width(), ClipRecorder.height());
    }
}
