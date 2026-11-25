package de.myticlegacy.client.module;

import de.myticlegacy.client.clips.ClipRecorder;
import de.myticlegacy.client.compat.FrameGrab;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

import java.nio.file.Path;

/**
 * Clips (Instant Replay): hält die letzten 15/30/60 Sekunden verkleinert im Arbeitsspeicher (JPEG, höchstens 300 MB),
 * eine Taste speichert sie als Video in .minecraft/mytic/clips – als AVI (Motion-JPEG, ohne Zusatzprogramm) oder, wenn
 * ffmpeg da ist, als MP4. Standardmäßig aus.
 */
public class ClipModule extends Module {
    private static ClipModule instance;
    public final ModeSetting length;
    public final ModeSetting resolution;
    public final ModeSetting fps;
    public final ModeSetting format;
    private String config = "";
    private long lastFrame;
    private boolean announced;
    /** Gespeicherter Clip für die Karte „Clip gespeichert“ (und Zeitpunkt) */
    private static volatile Path lastClip;
    private static volatile long lastClipAt;

    public ClipModule() {
        super("clips", "Clips", "Die letzten Sekunden per Taste als Video speichern (Ordner „mytic/clips“)", Category.CLIENT, () -> Items.JUKEBOX, false);
        length = new ModeSetting(this, "duration", "Länge (Sekunden)", "30", "15", "30", "60");
        resolution = new ModeSetting(this, "resolution", "Auflösung", "720p", "480p", "720p", "1080p");
        fps = new ModeSetting(this, "fps", "Bilder pro Sekunde", "30", "30", "60");
        format = new ModeSetting(this, "format", "Format", "MP4, wenn ffmpeg da ist", "AVI (immer)", "MP4, wenn ffmpeg da ist");
        new ActionSetting(this, "folder", "Clip-Ordner", () -> "Ordner öffnen", ClipRecorder::openFolder);
        instance = this;
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
        switch (resolution.get()) {
            case "480p":
                return 480;
            case "1080p":
                return 1080;
            default:
                return 720;
        }
    }

    public void tick(Minecraft mc) {
        boolean want = enabled() && mc.level != null;
        if (!want) {
            if (ClipRecorder.running()) ClipRecorder.stop();
            config = "";
            announced = false;
            return;
        }
        String err = ClipRecorder.error();
        if (err != null) {
            if (!config.equals("error")) Msg.actionBar("Clips: " + err);
            config = "error";
            ClipRecorder.stop();
            return;
        }
        if (config.equals("error")) return; // nicht jeden Tick neu versuchen (aus- und einschalten = neuer Versuch)
        // Aufnahme erst starten, wenn die Welt geladen ist – beim Betreten („Joining world“) braucht Minecraft den
        // Spiel-Thread, die Rechenkerne und den Speicher für die ersten Chunks (Puffer, JPEG-Threads, GPU-Auslesen)
        boolean loading = de.myticlegacy.client.compat.LevelLoading.active(mc);
        if (loading && !ClipRecorder.running()) return;
        int w = FrameGrab.width(), h = FrameGrab.height();
        int len = seconds();
        String c = w + "x" + h + "/" + outHeight() + "/" + targetFps() + "/" + len;
        if (!loading && (!ClipRecorder.running() || !c.equals(config))) {
            if (config.isEmpty()) ClipRecorder.cleanupOld();
            config = c;
            if (FrameGrab.gpuScale()) {
                // Grafikkarte verkleinert und dreht – der Kodierer bekommt fertige Bilder in Clip-Größe (gerade Zahlen)
                int oh = (outHeight() < h ? outHeight() : h) & ~1;
                int ow = Math.max(2, Math.round(w * (float) oh / h) & ~1);
                ClipRecorder.start(w, h, ow, oh, 0, targetFps(), len, false);
            } else ClipRecorder.start(w, h, w, h, outHeight(), targetFps(), len, FrameGrab.flipped());
            if (!announced && ClipRecorder.running()) {
                announced = true;
                Msg.actionBar("Clips: Aufnahme läuft (" + outHeight() + "p, " + targetFps() + " fps, " + len + " s)");
            }
        }
    }

    /** Jedes fertige Bild (vom Mixin kurz vor der Anzeige); holt höchstens so viele Bilder wie eingestellt */
    public static void onFrame() {
        if (instance == null || !ClipRecorder.running()) return;
        if (de.myticlegacy.client.compat.LevelLoading.active(Minecraft.getInstance())) return; // Ladebildschirm nicht aufnehmen
        long now = System.nanoTime(), step = 1_000_000_000L / instance.targetFps();
        if (now - instance.lastFrame < step * 9 / 10) return;
        instance.lastFrame = now;
        FrameGrab.grab();
    }

    /** Taste „Clip speichern“; mit Umschalt kurz nach dem Speichern: Ordner öffnen */
    public void saveClip(Minecraft mc) {
        if (de.myticlegacy.client.compat.Compat.shiftDown() && lastClip != null && System.currentTimeMillis() - lastClipAt < ClipToast.SHOW_MS) {
            ClipRecorder.openFolder();
            lastClipAt = 0;
            return;
        }
        if (!enabled()) {
            Msg.actionBar("Clips sind aus – im Mod-Menü unter Client einschalten");
            return;
        }
        if (!ClipRecorder.running()) {
            Msg.actionBar("Clips: Aufnahme startet noch …");
            return;
        }
        Msg.actionBar("Clip wird gespeichert …");
        ClipRecorder.save(seconds(), format.get().startsWith("MP4"), (clip, error) -> mc.execute(() -> {
            if (error != null) {
                Msg.actionBar("Clip: " + error);
                return;
            }
            lastClip = clip;
            lastClipAt = System.currentTimeMillis();
            Msg.actionBar("Clip gespeichert – Ordner öffnen: Umschalt + Clip-Taste");
        }));
    }

    /** Gerade gespeicherter Clip (für die Karte) oder null */
    static Path recentClip() {
        return lastClip != null && System.currentTimeMillis() - lastClipAt < ClipToast.SHOW_MS ? lastClip : null;
    }

    static long recentAt() {
        return lastClipAt;
    }
}
