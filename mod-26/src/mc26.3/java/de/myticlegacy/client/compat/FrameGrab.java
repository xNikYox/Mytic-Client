package de.myticlegacy.client.compat;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import de.myticlegacy.client.clips.ClipRecorder;
import net.minecraft.client.Minecraft;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Bild für Clips von der Grafikkarte holen, ohne zu warten (26.3, Renderpearl). Höchstens drei Kopien gleichzeitig. */
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

    public static void grab() {
        RenderTarget target = target();
        GpuTexture tex = target.getColorTexture();
        if (tex == null || inFlight >= 3) return;
        int w = target.width, h = target.height;
        if (w != ClipRecorder.width() || h != ClipRecorder.height()) return;
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
