package de.myticlegacy.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import de.myticlegacy.client.clips.ClipRecorder;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * Bild für Clips holen (1.21–1.21.4, direktes OpenGL): glReadPixels in einen Pixel-Puffer, Fence setzen und erst ein
 * paar Bilder später auslesen, wenn die Grafikkarte fertig ist – so muss das Spiel nicht warten.
 */
public final class FrameGrab {
    private static final int SLOTS = 3;
    private static final int[] pbo = new int[SLOTS];
    private static final long[] fence = new long[SLOTS];
    private static final ArrayDeque<Integer> order = new ArrayDeque<>();
    private static int size;

    private FrameGrab() {
    }

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
        int w = target.width, h = target.height;
        if (w != ClipRecorder.width() || h != ClipRecorder.height()) return;
        collect();
        int bytes = w * h * 4;
        if (bytes != size) reset(bytes);
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) if (fence[i] == 0) slot = i;
        if (slot < 0) return; // alle Puffer belegt: Bild auslassen
        if (pbo[slot] == 0) {
            pbo[slot] = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[slot]);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, bytes, GL15.GL_STREAM_READ);
        }
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[slot]);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        fence[slot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        order.add(slot);
    }

    /** fertige Puffer der Reihe nach auslesen */
    private static void collect() {
        while (!order.isEmpty()) {
            int slot = order.peek();
            int r = GL32.glClientWaitSync(fence[slot], 0, 0);
            if (r != GL32.GL_ALREADY_SIGNALED && r != GL32.GL_CONDITION_SATISFIED) return;
            order.poll();
            GL32.glDeleteSync(fence[slot]);
            fence[slot] = 0;
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[slot]);
            ByteBuffer src = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY, size, null);
            if (src != null) {
                ByteBuffer dst = ClipRecorder.borrow();
                if (dst != null && dst.capacity() == size) {
                    src.limit(size);
                    dst.put(src);
                    dst.flip();
                    ClipRecorder.submit(dst);
                } else if (dst != null) ClipRecorder.release(dst);
                GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        }
    }

    /** Fenstergröße geändert: Puffer neu anlegen */
    private static void reset(int bytes) {
        for (int i = 0; i < SLOTS; i++) {
            if (fence[i] != 0) GL32.glDeleteSync(fence[i]);
            if (pbo[i] != 0) GL15.glDeleteBuffers(pbo[i]);
            fence[i] = 0;
            pbo[i] = 0;
        }
        order.clear();
        size = bytes;
    }
}
