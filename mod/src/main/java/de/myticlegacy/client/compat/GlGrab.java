package de.myticlegacy.client.compat;

import de.myticlegacy.client.clips.ClipRecorder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * Bild für Clips mit direktem OpenGL holen – für alle Versionen gleich, nur die Quelle (Framebuffer bzw. Textur des
 * Haupt-Ziels) liefert die jeweilige FrameGrab.java.
 * <p>
 * Die Grafikkarte verkleinert das fertige Bild per glBlitFramebuffer auf die Clip-Größe und dreht es dabei richtig herum.
 * Ausgelesen wird nur das kleine Bild: glReadPixels in einen Pixel-Puffer, Fence setzen und erst abholen, wenn die
 * Grafikkarte fertig ist – das Spiel wartet nie. ffmpeg muss so weder skalieren noch spiegeln.
 * Der GL-Zustand (Framebuffer, Pixel-Puffer, Renderbuffer, Scissor, Pack-Ausrichtung) wird danach wiederhergestellt,
 * damit Minecrafts eigene Zustands-Zwischenspeicher stimmen.
 */
public final class GlGrab {
    private static final int SLOTS = 3;
    private static final int[] pbo = new int[SLOTS];
    private static final long[] fence = new long[SLOTS];
    private static final ArrayDeque<Integer> order = new ArrayDeque<>();
    private static int size, outW, outH;
    private static int readFbo, drawFbo, drawRbo;

    private GlGrab() {
    }

    /**
     * Ein Bild holen. srcFbo = Framebuffer des Haupt-Ziels (oder 0), sonst srcTex = dessen Farb-Textur.
     * w/h = Größe des Haupt-Ziels, ow/oh = Clip-Größe (gerade Zahlen).
     */
    public static void grab(int srcFbo, int srcTex, int w, int h, int ow, int oh) {
        collect();
        int bytes = ow * oh * 4;
        if (bytes != size || ow != outW || oh != outH) reset(bytes, ow, oh);
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) if (fence[i] == 0) slot = i;
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
            int src = srcFbo;
            if (src == 0) { // Textur des Haupt-Ziels an einen eigenen Lese-Framebuffer hängen
                if (readFbo == 0) readFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
                // jedes Mal neu anhängen: nach Größenänderungen kann Minecraft dieselbe Textur-Nummer neu vergeben
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, srcTex, 0);
                src = readFbo;
            }
            if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST); // Blit wäre sonst auf den Scissor-Bereich beschränkt
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            // verkleinern und senkrecht spiegeln (OpenGL zählt von unten), weich gefiltert
            GL30.glBlitFramebuffer(0, 0, w, h, 0, oh, ow, 0, GL11.GL_COLOR_BUFFER_BIT, ow == w && oh == h ? GL11.GL_NEAREST : GL11.GL_LINEAR);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawFbo);
            if (pbo[slot] == 0) {
                pbo[slot] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[slot]);
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, bytes, GL15.GL_STREAM_READ);
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

    /** fertige Puffer der Reihe nach auslesen und an ffmpeg geben */
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
            }
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPack);
        }
    }

    /** Größe geändert: Puffer und Ziel neu anlegen */
    private static void reset(int bytes, int ow, int oh) {
        for (int i = 0; i < SLOTS; i++) {
            if (fence[i] != 0) GL32.glDeleteSync(fence[i]);
            if (pbo[i] != 0) GL15.glDeleteBuffers(pbo[i]);
            fence[i] = 0;
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
