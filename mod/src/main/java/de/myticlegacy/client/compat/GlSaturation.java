package de.myticlegacy.client.compat;

import de.myticlegacy.client.MyticClient;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * Farbsättigung mit direktem OpenGL – für alle Versionen gleich: Spielbild in eine eigene Textur kopieren und mit einem
 * kleinen Shader (Dreieck über den ganzen Bildschirm) zurück ins Haupt-Ziel zeichnen.
 * Der gesamte angefasste GL-Zustand wird danach wiederhergestellt, damit Minecrafts Zustands-Zwischenspeicher stimmen.
 * Schlägt das Kompilieren fehl, bleibt der Effekt bis zum Neustart aus (mit Log-Eintrag).
 */
public final class GlSaturation {
    private static final String VERTEX = """
            #version 150
            out vec2 uv;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                uv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D tex;
            uniform float sat;
            in vec2 uv;
            out vec4 color;
            void main() {
                vec4 c = texture(tex, uv);
                float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
                color = vec4(clamp(mix(vec3(l), c.rgb, sat), 0.0, 1.0), c.a);
            }
            """;
    private static int program, vao, uSat, copyTex, copyFbo, ownFbo, copyW, copyH;
    private static boolean failed;

    private GlSaturation() {
    }

    /** fbo = Framebuffer des Haupt-Ziels (oder 0, dann tex = dessen Farb-Textur), w/h = Größe, sat = Faktor (1 = normal) */
    public static void apply(int fbo, int tex, int w, int h, float sat) {
        if (failed || w <= 0 || h <= 0 || (fbo == 0 && tex == 0)) return;
        boolean gl33 = GL.getCapabilities().OpenGL33;
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevSampler = gl33 ? GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) : 0;
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean[] mask = new boolean[4];
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.ByteBuffer m = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, m);
            for (int i = 0; i < 4; i++) mask[i] = m.get(i) != 0;
        }
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        try {
            if (program == 0 && !compile()) return;
            int target = fbo;
            if (target == 0) { // Farb-Textur des Haupt-Ziels an einen eigenen Framebuffer hängen (jedes Mal: Nummer kann neu vergeben werden)
                if (ownFbo == 0) ownFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ownFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
                target = ownFbo;
            }
            if (copyTex == 0 || copyW != w || copyH != h) {
                if (copyTex == 0) copyTex = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTex);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                if (copyFbo == 0) copyFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, copyFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, copyTex, 0);
                copyW = w;
                copyH = h;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            // 1. Spielbild kopieren (aus derselben Textur lesen und schreiben geht nicht)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            // 2. mit dem Shader zurückzeichnen
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
            GL11.glViewport(0, 0, w, h);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);
            GL20.glUseProgram(program);
            GL20.glUniform1f(uSat, sat);
            GL30.glBindVertexArray(vao);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTex);
            if (gl33) GL33.glBindSampler(0, 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL20.glUseProgram(prevProgram);
            GL30.glBindVertexArray(prevVao);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            if (gl33) GL33.glBindSampler(0, prevSampler);
            GL13.glActiveTexture(prevActive);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glColorMask(mask[0], mask[1], mask[2], mask[3]);
            set(GL11.GL_BLEND, blend);
            set(GL11.GL_DEPTH_TEST, depth);
            set(GL11.GL_CULL_FACE, cull);
            set(GL11.GL_SCISSOR_TEST, scissor);
            set(GL11.GL_STENCIL_TEST, stencil);
        }
    }

    private static void set(int cap, boolean on) {
        if (on) GL11.glEnable(cap);
        else GL11.glDisable(cap);
    }

    private static boolean compile() {
        int vs = shader(GL20.GL_VERTEX_SHADER, VERTEX), fs = shader(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        if (vs == 0 || fs == 0) {
            failed = true;
            return false;
        }
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL30.glBindFragDataLocation(p, 0, "color");
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            MyticClient.LOG.warn("Farbsättigung: Shader lässt sich nicht verbinden: {}", GL20.glGetProgramInfoLog(p));
            GL20.glDeleteProgram(p);
            failed = true;
            return false;
        }
        GL20.glUseProgram(p);
        GL20.glUniform1i(GL20.glGetUniformLocation(p, "tex"), 0);
        uSat = GL20.glGetUniformLocation(p, "sat");
        program = p;
        vao = GL30.glGenVertexArrays();
        return true;
    }

    private static int shader(int type, String source) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, source);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            MyticClient.LOG.warn("Farbsättigung: Shader-Fehler: {}", GL20.glGetShaderInfoLog(s));
            GL20.glDeleteShader(s);
            return 0;
        }
        return s;
    }
}
