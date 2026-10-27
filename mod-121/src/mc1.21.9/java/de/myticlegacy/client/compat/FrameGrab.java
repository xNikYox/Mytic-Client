package de.myticlegacy.client.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import de.myticlegacy.client.clips.ClipRecorder;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * Bild für Clips von der Grafikkarte holen, ohne auf sie zu warten: Kopie in einen Puffer anstoßen, Daten erst abholen,
 * wenn die Grafikkarte fertig ist. Höchstens drei Kopien gleichzeitig, sonst wird ein Bild ausgelassen.
 * Fassung für 1.21.6–1.21.10 (Puffergrößen als int).
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

    public static void grab() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture tex = target.getColorTexture();
        if (tex == null || inFlight >= 3) return;
        int w = target.width, h = target.height;
        if (w != ClipRecorder.width() || h != ClipRecorder.height()) return; // Größe geändert: Aufnahme startet gleich neu
        int size = w * h * tex.getFormat().pixelSize();
        if (size != bufferSize) {
            for (GpuBuffer b : free) b.close();
            free.clear();
            bufferSize = size;
        }
        GpuBuffer buffer = free.isEmpty()
                ? RenderSystem.getDevice().createBuffer(() -> "Mytic Clip", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, size)
                : free.poll();
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        inFlight++;
        enc.copyTextureToBuffer(tex, buffer, 0, () -> {
            inFlight--;
            if (buffer.size() == bufferSize) {
                try (GpuBuffer.MappedView view = enc.mapBuffer(buffer, true, false)) {
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
