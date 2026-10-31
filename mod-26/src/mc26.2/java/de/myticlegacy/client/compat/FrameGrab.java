package de.myticlegacy.client.compat;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import de.myticlegacy.client.clips.ClipRecorder;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Bild für Clips von der Grafikkarte holen, ohne zu warten (26.2). Höchstens drei Kopien gleichzeitig. */
public final class FrameGrab {
    private static final ArrayDeque<GpuBuffer> free = new ArrayDeque<>();
    private static long bufferSize;
    private static int inFlight;

    private FrameGrab() {
    }

    public static boolean flipped() {
        return true;
    }

    private static RenderTarget target() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }

    public static int width() {
        return target().width;
    }

    public static int height() {
        return target().height;
    }

    /** Läuft das Spiel mit OpenGL? Dann verkleinert GlGrab auf der Grafikkarte (sonst z. B. Vulkan: alter Weg über ffmpeg) */
    public static boolean gpuScale() {
        return target().getColorTexture() instanceof GlTexture;
    }

    public static void grab() {
        RenderTarget target = target();
        GpuTexture tex = target.getColorTexture();
        if (tex == null) return;
        int w = target.width, h = target.height;
        if (w != ClipRecorder.sourceWidth() || h != ClipRecorder.sourceHeight()) return; // Größe geändert: Aufnahme startet gleich neu
        if (tex instanceof GlTexture gl && !ClipRecorder.flipped()) { // OpenGL: auf der Grafikkarte verkleinern (GlGrab)
            GlGrab.grab(0, gl.glId(), w, h, ClipRecorder.width(), ClipRecorder.height());
            return;
        }
        if (inFlight >= 3) return;
        long size = (long) w * h * 4; // Haupt-Ziel ist RGBA8
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
                try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
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
