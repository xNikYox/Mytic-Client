package de.myticlegacy.client.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import de.myticlegacy.client.clips.ClipRecorder;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * Bild für Clips von der Grafikkarte holen, ohne auf sie zu warten: Kopie in einen Puffer anstoßen, Daten erst abholen,
 * wenn die Grafikkarte fertig ist. Höchstens drei Kopien gleichzeitig, sonst wird ein Bild ausgelassen.
 * Fassung für 1.21.5 (BufferType/BufferUsage, readBuffer).
 */
public final class FrameGrab {
    private static final ArrayDeque<GpuBuffer> free = new ArrayDeque<>();
    private static int bufferSize;
    private static int inFlight;

    private FrameGrab() {
    }

    /** Bilder kommen von OpenGL auf dem Kopf */
    public static boolean flipped() {
        return true;
    }

    public static int width() {
        return Minecraft.getInstance().getMainRenderTarget().width;
    }

    public static int height() {
        return Minecraft.getInstance().getMainRenderTarget().height;
    }

    /** Läuft das Spiel mit OpenGL? Dann verkleinert GlGrab auf der Grafikkarte (sonst z. B. Vulkan: alter Weg über ffmpeg) */
    public static boolean gpuScale() {
        return Minecraft.getInstance().getMainRenderTarget().getColorTexture() instanceof GlTexture;
    }

    /** Für GlSaturation: Framebuffer des Haupt-Ziels (hier nicht direkt verfügbar → 0) */
    public static int glFramebuffer() {
        return 0;
    }

    /** Für GlSaturation: OpenGL-Textur des Haupt-Ziels oder 0 (z. B. Vulkan) */
    public static int glTexture() {
        return Minecraft.getInstance().getMainRenderTarget().getColorTexture() instanceof GlTexture gl ? gl.glId() : 0;
    }

    public static void grab() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture tex = target.getColorTexture();
        if (tex == null) return;
        int w = target.width, h = target.height;
        if (w != ClipRecorder.sourceWidth() || h != ClipRecorder.sourceHeight()) return; // Größe geändert: Aufnahme startet gleich neu
        if (tex instanceof GlTexture gl && !ClipRecorder.flipped()) { // OpenGL: auf der Grafikkarte verkleinern (GlGrab)
            GlGrab.grab(0, gl.glId(), w, h, ClipRecorder.width(), ClipRecorder.height());
            return;
        }
        if (inFlight >= 3) return;
        int size = w * h * tex.getFormat().pixelSize();
        if (size != bufferSize) {
            for (GpuBuffer b : free) b.close();
            free.clear();
            bufferSize = size;
        }
        GpuBuffer buffer = free.isEmpty()
                ? RenderSystem.getDevice().createBuffer(() -> "Mytic Clip", com.mojang.blaze3d.buffers.BufferType.PIXEL_PACK, com.mojang.blaze3d.buffers.BufferUsage.STREAM_READ, size)
                : free.poll();
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        inFlight++;
        enc.copyTextureToBuffer(tex, buffer, 0, () -> {
            inFlight--;
            if (buffer.size() == bufferSize) {
                try (GpuBuffer.ReadView view = enc.readBuffer(buffer)) {
                    ByteBuffer src = view.data();
                    ByteBuffer dst = ClipRecorder.borrow();
                    if (dst != null && dst.capacity() == src.remaining()) {
                        dst.put(src);
                        dst.flip();
                        ClipRecorder.submit(dst);
                    } else if (dst != null) ClipRecorder.release(dst);
                }
                free.add(buffer);
            } else buffer.close();
        }, 0);
    }
}
