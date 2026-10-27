package de.myticlegacy.client.module;

import de.myticlegacy.client.clips.ClipRecorder;
import de.myticlegacy.client.compat.FrameGrab;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * Clips: nimmt im Hintergrund die letzten Sekunden auf, eine Taste speichert sie als MP4 in den Ordner „clips“.
 * Standardmäßig aus. Beim ersten Einschalten wird ffmpeg gesucht bzw. (Windows) einmalig geladen.
 */
public class ClipModule extends Module {
    private static ClipModule instance;
    public final SliderSetting length;
    public final ModeSetting resolution;
    public final ModeSetting fps;
    private String config = "";
    private String shownStatus = "";
    private long lastFrame;
    private boolean announced;

    public ClipModule() {
        super("clips", "Clips", "Die letzten Sekunden per Taste als Video speichern (Ordner „clips“)", Category.CLIENT, () -> Items.JUKEBOX, false);
        length = new SliderSetting(this, "length", "Länge", 10, 120, 5, 30, " s");
        resolution = new ModeSetting(this, "resolution", "Auflösung", "1080p", "720p", "1080p", "Bildschirm");
        fps = new ModeSetting(this, "fps", "Bilder pro Sekunde", "30", "30", "60");
        instance = this;
    }

    private int targetFps() {
        return "60".equals(fps.get()) ? 60 : 30;
    }

    private int outHeight() {
        return switch (resolution.get()) {
            case "720p" -> 720;
            case "1080p" -> 1080;
            default -> 0;
        };
    }

    public void tick(Minecraft mc) {
        boolean want = enabled() && mc.level != null;
        if (!want) {
            if (ClipRecorder.running()) ClipRecorder.stop();
            config = "";
            announced = false;
            return;
        }
        if (!ClipRecorder.ready()) {
            ClipRecorder.prepare();
            String s = ClipRecorder.status;
            if (!s.equals(shownStatus)) {
                shownStatus = s;
                if (!s.isEmpty()) Msg.actionBar("Clips: " + s);
            }
            return;
        }
        int w = FrameGrab.width(), h = FrameGrab.height();
        int len = (int) Math.round(length.get());
        String c = w + "x" + h + "/" + outHeight() + "/" + targetFps() + "/" + len;
        if (!ClipRecorder.running() || !c.equals(config)) {
            config = c;
            ClipRecorder.start(w, h, outHeight(), targetFps(), len, FrameGrab.flipped());
            if (!announced && ClipRecorder.running()) {
                announced = true;
                Msg.actionBar("Clips: Aufnahme läuft (" + ClipRecorder.encoderName() + ")");
            }
        }
    }

    /** Jedes fertige Bild (vom Mixin kurz vor der Anzeige); holt höchstens so viele Bilder wie eingestellt */
    public static void onFrame() {
        if (instance == null || !ClipRecorder.running()) return;
        long now = System.nanoTime(), step = 1_000_000_000L / instance.targetFps();
        if (now - instance.lastFrame < step * 9 / 10) return;
        instance.lastFrame = now;
        FrameGrab.grab();
    }

    /** Taste „Clip speichern“ */
    public void saveClip(Minecraft mc) {
        if (!enabled()) {
            Msg.actionBar("Clips sind aus – im Mod-Menü unter Client einschalten");
            return;
        }
        if (!ClipRecorder.running()) {
            Msg.actionBar(ClipRecorder.status.isEmpty() ? "Clips: Aufnahme startet noch …" : "Clips: " + ClipRecorder.status);
            return;
        }
        Msg.actionBar("Clip wird gespeichert …");
        ClipRecorder.save((clip, error) -> mc.execute(() ->
                Msg.actionBar(error != null ? "Clip: " + error : "Clip gespeichert: " + clip.getFileName())));
    }
}
