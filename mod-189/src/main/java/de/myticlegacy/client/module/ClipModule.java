package de.myticlegacy.client.module;

import de.myticlegacy.client.clips.ClipRecorder;
import de.myticlegacy.client.clips.GlCapture;
import de.myticlegacy.client.compat.GuiGraphics;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Type;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

import java.io.File;

/**
 * Clips für 1.8.9 (Instant Replay): die letzten 15/30/60 Sekunden verkleinert im Arbeitsspeicher, eine Taste speichert sie
 * als AVI (Motion-JPEG) bzw. mit ffmpeg als MP4 in .minecraft/mytic/clips. Aufnahme am Ende jedes Bildes
 * (RenderTickEvent END: Welt, HUD und Menüs sind fertig), Verkleinern auf der Grafikkarte. Standardmäßig aus.
 */
public class ClipModule extends Module {
    static final long SHOW_MS = 6000;
    public final ModeSetting length;
    public final ModeSetting resolution;
    public final ModeSetting fps;
    public final ModeSetting format;
    private String config = "";
    private long lastFrame;
    private File lastClip;
    private long lastClipAt;

    public ClipModule() {
        super("clips", "Clips", "Die letzten Sekunden per Taste als Video speichern (Ordner „mytic/clips“)", Category.CLIENT, () -> Items.record_cat, false);
        length = new ModeSetting(this, "duration", "Länge (Sekunden)", "30", "15", "30", "60");
        resolution = new ModeSetting(this, "resolution", "Auflösung", "720p", "480p", "720p", "1080p");
        fps = new ModeSetting(this, "fps", "Bilder pro Sekunde", "30", "30", "60");
        format = new ModeSetting(this, "format", "Format", "MP4, wenn ffmpeg da ist", "AVI (immer)", "MP4, wenn ffmpeg da ist");
    }

    private static void actionBar(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI != null) mc.ingameGUI.setRecordPlaying(text, false);
    }

    private int targetFps() {
        return "60".equals(fps.get()) ? 60 : 30;
    }

    private int seconds() {
        try {
            return Integer.parseInt(length.get());
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    private int outHeight() {
        String r = resolution.get();
        return "480p".equals(r) ? 480 : "1080p".equals(r) ? 1080 : 720;
    }

    private int[] size(Minecraft mc) {
        int w = mc.displayWidth, h = mc.displayHeight;
        int oh = Math.max(2, (outHeight() < h ? outHeight() : h) & ~1);
        int ow = Math.max(2, Math.round(w * (float) oh / Math.max(1, h)) & ~1);
        return new int[]{w, h, ow, oh};
    }

    public void tick(Minecraft mc) {
        if (!enabled() || mc.theWorld == null) {
            if (ClipRecorder.running()) ClipRecorder.stop();
            if (!enabled()) config = "";
            return;
        }
        if (!GlCapture.supported()) {
            if (!config.equals("nogl")) actionBar("Clips brauchen OpenGL 3.2 – auf diesem PC nicht verfügbar");
            config = "nogl";
            return;
        }
        String err = ClipRecorder.error();
        if (err != null) {
            if (!config.equals("error")) actionBar("Clips: " + err);
            config = "error";
            ClipRecorder.stop();
            return;
        }
        if (config.equals("error")) return;
        // erst nach „Loading terrain“ starten – beim Betreten braucht das Spiel Thread, Kerne und Speicher selbst
        if (loading(mc)) return;
        int[] s = size(mc);
        String c = s[0] + "x" + s[1] + "/" + s[3] + "/" + targetFps() + "/" + seconds();
        if (!ClipRecorder.running() || !c.equals(config)) {
            boolean first = config.isEmpty();
            config = c;
            ClipRecorder.start(s[2], s[3], targetFps(), seconds());
            if (first) actionBar("Clips: Aufnahme läuft (" + s[3] + "p, " + targetFps() + " fps, " + seconds() + " s)");
        }
    }

    /** Welt wird gerade betreten (Ladebildschirm) – dann nicht aufnehmen */
    private static boolean loading(Minecraft mc) {
        return mc.theWorld == null || mc.thePlayer == null || mc.currentScreen instanceof net.minecraft.client.gui.GuiDownloadTerrain;
    }

    /** Ende jedes Bildes (RenderTickEvent END) */
    public void frame(Minecraft mc) {
        if (!ClipRecorder.running() || loading(mc)) return;
        long now = System.nanoTime(), step = 1_000_000_000L / targetFps();
        if (now - lastFrame < step * 9 / 10) return;
        lastFrame = now;
        int[] s = size(mc);
        int fbo = OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null ? mc.getFramebuffer().framebufferObject : 0;
        int w = fbo != 0 ? mc.getFramebuffer().framebufferWidth : s[0], h = fbo != 0 ? mc.getFramebuffer().framebufferHeight : s[1];
        if (w != s[0] || h != s[1]) return; // Fenstergröße ändert sich gerade
        GlCapture.grab(fbo, w, h, s[2], s[3]);
    }

    /** Taste „Clip speichern“; mit Umschalt, solange die Karte zu sehen ist: Ordner öffnen */
    public void saveClip(final Minecraft mc) {
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (shift && lastClip != null && System.currentTimeMillis() - lastClipAt < SHOW_MS) {
            ClipRecorder.openFolder();
            lastClipAt = 0;
            return;
        }
        if (!enabled()) {
            actionBar("Clips sind aus – im Mod-Menü unter Client einschalten");
            return;
        }
        if (!ClipRecorder.running()) {
            actionBar(config.equals("nogl") ? "Clips brauchen OpenGL 3.2" : "Clips: Aufnahme startet noch …");
            return;
        }
        actionBar("Clip wird gespeichert …");
        ClipRecorder.save(seconds(), format.get().startsWith("MP4"), new ClipRecorder.Done() {
            public void done(final File clip, final String error) {
                mc.addScheduledTask(new Runnable() {
                    public void run() {
                        if (error != null) {
                            actionBar("Clip: " + error);
                            return;
                        }
                        lastClip = clip;
                        lastClipAt = System.currentTimeMillis();
                        actionBar("Clip gespeichert – Ordner öffnen: Umschalt + Clip-Taste");
                        if (mc.thePlayer != null) mc.thePlayer.addChatComponentMessage(new ChatComponentText("§dMytic §7» Clip gespeichert: §f" + clip.getName()));
                    }
                });
            }
        });
    }

    /** Karte „Clip gespeichert – Ordner öffnen“ oben rechts */
    public void renderToast(GuiGraphics g, KeyBinding clipKey) {
        if (lastClip == null || clipKey == null) return;
        long age = System.currentTimeMillis() - lastClipAt;
        if (age >= SHOW_MS) return;
        final int W = 184, H = 40;
        float in = Math.min(1f, age / 250f), out = Math.min(1f, (SHOW_MS - age) / 250f), t = Math.min(in, out);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int x = g.guiWidth() - W - 6 + Math.round((1 - ease) * (W + 10)), y = 6;
        float dv = 0.4f;
        Draw.shadow(g, x, y + 8, W, H, 14 * dv, 36 * dv, -6 * dv, 0x99000000);
        Draw.glass(g, x, y, W, H, 14 * dv, 0xEB14101E, 0x2BFFFFFF);
        Draw.accentRect(g, x + 3, y + 8, 2, H - 16, 1, 1f);
        Type.draw(g, "Clip gespeichert", x + 10, y + 7.5f, 15 * dv, Tokens.SEMIBOLD, Tokens.TEXT);
        Type.draw(g, Type.ellipsize(g, lastClip.getName(), W - 18, 12.5f * dv, 0), x + 10, y + 16.5f, 12.5f * dv, Tokens.MEDIUM, Tokens.MUTED);
        String key = Keyboard.getKeyName(clipKey.getKeyCode());
        Type.draw(g, Type.ellipsize(g, "[Umschalt + " + key + "] Ordner öffnen", W - 18, 12 * dv, 0), x + 10, y + 27, 12 * dv, Tokens.SEMIBOLD, Tokens.SUBTLE);
        float left = Math.max(0f, 1f - age / (float) SHOW_MS);
        Draw.accentRect(g, x + 10, y + H - 2.5f, Math.max(1.2f, (W - 20) * left), 1.2f, 0.6f, 1f);
    }
}
