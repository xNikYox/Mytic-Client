package de.myticlegacy.client.clips;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * 1.8.9 (LWJGL 2): fertiges Bild für Clips holen, ohne auf die Grafikkarte zu warten – wie GlGrab der neuen Versionen.
 * glBlitFramebuffer verkleinert und dreht das Bild auf der Grafikkarte, glReadPixels schreibt in einen Pixel-Puffer,
 * abgeholt wird erst, wenn der Fence signalisiert. Braucht OpenGL 3.0 + ARB_sync (Windows/Linux praktisch immer;
 * macOS mit 1.8.9 nur OpenGL 2.1 → Clips nicht verfügbar).
 */
public final class GlCapture {
    private static final int SLOTS = 3;
    private static final int[] pbo = new int[SLOTS];
    private static final GLSync[] fence = new GLSync[SLOTS];
    private static final ArrayDeque<Integer> order = new ArrayDeque<Integer>();
    private static int size, outW, outH;
    private static int drawFbo, drawRbo;
    private static Boolean supported;

    private GlCapture() {
    }

    public static boolean supported() {
        if (supported == null) {
            try {
                ContextCapabilities caps = GLContext.getCapabilities();
                supported = (caps.OpenGL30 || caps.GL_ARB_framebuffer_object) && (caps.OpenGL32 || caps.GL_ARB_sync);
            } catch (Throwable t) {
                supported = false;
            }
        }
        return supported;
    }

    /** srcFbo = Framebuffer des Spiels (0 = Standard-Framebuffer), w/h dessen Größe, ow/oh Clip-Größe */
    public static void grab(int srcFbo, int w, int h, int ow, int oh) {
        collect();
        int bytes = ow * oh * 4;
        if (bytes != size || ow != outW || oh != outH) reset(bytes, ow, oh);
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) if (fence[i] == null) slot = i;
        if (slot < 0) return; // alle Puffer belegt: Bild auslassen

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int prevRbo = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        int prevAlign = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            if (drawFbo == 0) {
                drawRbo = GL30.glGenRenderbuffers();
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, drawRbo);
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, ow, oh);
                drawFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
                GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, drawRbo);
            }
            if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, oh, ow, 0, GL11.GL_COLOR_BUFFER_BIT, ow == w && oh == h ? GL11.GL_NEAREST : GL11.GL_LINEAR);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawFbo);
            if (pbo[slot] == 0) {
                pbo[slot] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[slot]);
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) bytes, GL15.GL_STREAM_READ);
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[slot]);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GL11.glReadPixels(0, 0, ow, oh, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            fence[slot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            order.add(slot);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, prevAlign);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPack);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, prevRbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void collect() {
        if (order.isEmpty()) return;
        int prevPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            while (!order.isEmpty()) {
                int slot = order.peek();
                int r = GL32.glClientWaitSync(fence[slot], 0, 0);
                if (r != GL32.GL_ALREADY_SIGNALED && r != GL32.GL_CONDITION_SATISFIED) return;
                order.poll();
                GL32.glDeleteSync(fence[slot]);
                fence[slot] = null;
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
            }
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPack);
        }
    }

    private static void reset(int bytes, int ow, int oh) {
        for (int i = 0; i < SLOTS; i++) {
            if (fence[i] != null) GL32.glDeleteSync(fence[i]);
            if (pbo[i] != 0) GL15.glDeleteBuffers(pbo[i]);
            fence[i] = null;
            pbo[i] = 0;
        }
        order.clear();
        if (drawFbo != 0) GL30.glDeleteFramebuffers(drawFbo);
        if (drawRbo != 0) GL30.glDeleteRenderbuffers(drawRbo);
        drawFbo = 0;
        drawRbo = 0;
        size = bytes;
        outW = ow;
        outH = oh;
    }
}
