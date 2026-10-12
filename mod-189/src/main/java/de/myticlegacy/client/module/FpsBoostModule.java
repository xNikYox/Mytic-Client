package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Items;

import java.util.Map;

/**
 * FPS-Boost wie der FPS-Modus bei Lunar: dreht teure Grafik-Optionen herunter. Die eigenen Werte werden vorher
 * gemerkt (auch über einen Neustart hinweg) und beim Ausschalten wiederhergestellt.
 */
public class FpsBoostModule extends Module {
    public final BoolSetting clouds;
    public final BoolSetting particles;
    public final BoolSetting shadows;
    public final BoolSetting fastGraphics;
    public final BoolSetting smoothLighting;
    public final BoolSetting background;
    public final BoolSetting autoDistance;
    public final SliderSetting targetFps;
    private long lastAdjust;

    public FpsBoostModule() {
        super("fpsboost", "FPS-Boost", "Mehr FPS: teure Grafik herunterdrehen, Sichtweite anpassen, im Hintergrund drosseln", Category.VISUELL, () -> Items.blaze_powder, false);
        clouds = new BoolSetting(this, "clouds", "Wolken aus", true);
        particles = new BoolSetting(this, "particles", "Partikel minimal", true);
        shadows = new BoolSetting(this, "shadows", "Schatten von Entities aus", true);
        fastGraphics = new BoolSetting(this, "fastGraphics", "Schnelle Grafik (deckendes Laub)", true);
        smoothLighting = new BoolSetting(this, "smoothLighting", "Weiche Beleuchtung aus", false);
        background = new BoolSetting(this, "background", "Im Hintergrund nur 15 FPS", true);
        autoDistance = new BoolSetting(this, "autoDistance", "Sichtweite automatisch anpassen", false);
        targetFps = new SliderSetting(this, "targetFps", "Ziel-FPS", 30, 240, 10, 60, " FPS");
    }

    public void tick(Minecraft mc) {
        GameSettings o = mc.gameSettings;
        boolean on = enabled();
        o.clouds = option("clouds", o.clouds, on && clouds.get(), 0);
        o.particleSetting = option("particles", o.particleSetting, on && particles.get(), 2);
        o.entityShadows = option("shadows", o.entityShadows ? 1 : 0, on && shadows.get(), 0) == 1;
        // Grafik und Beleuchtung: Chunks neu bauen, wenn sich etwas ändert
        boolean fancy = option("fancy", o.fancyGraphics ? 1 : 0, on && fastGraphics.get(), 0) == 1;
        int ao = option("ao", o.ambientOcclusion, on && smoothLighting.get(), 0);
        if (fancy != o.fancyGraphics || ao != o.ambientOcclusion) {
            o.fancyGraphics = fancy;
            o.ambientOcclusion = ao;
            if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
        }
        // Fenster nicht im Vordergrund: Bildrate drosseln
        o.limitFramerate = option("background", o.limitFramerate, on && background.get() && !org.lwjgl.opengl.Display.isActive(), 15);
        autoDistance(mc, o, on && autoDistance.get());
    }

    /** Sichtweite automatisch: unter dem Ziel eine Stufe weniger (bis 6), deutlich darüber wieder mehr (bis zur eigenen). */
    private void autoDistance(Minecraft mc, GameSettings o, boolean active) {
        String key = "1.8.9.renderDistance";
        Map<String, String> saved = MyticClient.config().savedOptions;
        if (!active || mc.theWorld == null) {
            o.renderDistanceChunks = option("renderDistance", o.renderDistanceChunks, false, 0);
            return;
        }
        if (!saved.containsKey(key)) {
            saved.put(key, String.valueOf(o.renderDistanceChunks));
            MyticClient.config().save();
        }
        long now = System.currentTimeMillis();
        if (now - lastAdjust < 4000) return;
        int own = Integer.parseInt(saved.get(key));
        int fps = Minecraft.getDebugFPS();
        int target = targetFps.intValue();
        if (fps < target * 0.85 && o.renderDistanceChunks > Math.min(6, own)) {
            o.renderDistanceChunks--;
            lastAdjust = now;
        } else if (fps > target * 1.35 && o.renderDistanceChunks < own) {
            o.renderDistanceChunks++;
            lastAdjust = now;
        }
    }

    /**
     * Boost-Wert zurückgeben bzw. den gemerkten eigenen Wert wiederherstellen. Schlüssel mit "1.8.9.", weil sich
     * 1.8.9 die Konfiguration mit den neuen Versionen teilt (dort andere Werte).
     */
    private static int option(String name, int current, boolean boost, int boostValue) {
        String key = "1.8.9." + name;
        Map<String, String> saved = MyticClient.config().savedOptions;
        if (boost) {
            if (!saved.containsKey(key)) {
                saved.put(key, String.valueOf(current));
                MyticClient.config().save();
            }
            return boostValue;
        }
        if (saved.containsKey(key)) {
            String original = saved.remove(key);
            MyticClient.config().save();
            try {
                return Integer.parseInt(original);
            } catch (NumberFormatException e) {
                return current;
            }
        }
        return current;
    }
}
