package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * FPS-Boost wie der FPS-Modus bei Lunar: dreht teure Grafik-Optionen herunter. Die eigenen Werte werden vorher
 * gemerkt (auch über einen Neustart hinweg) und beim Ausschalten wiederhergestellt.
 */
public class FpsBoostModule extends Module {
    public final BoolSetting clouds;
    public final BoolSetting particles;
    public final BoolSetting shadows;
    public final BoolSetting biomeBlend;
    public final BoolSetting entityDistance;
    public final BoolSetting fastGraphics;
    public final BoolSetting smoothLighting;
    public final BoolSetting background;
    public final BoolSetting autoDistance;
    public final SliderSetting targetFps;
    private long lastAdjust;

    public FpsBoostModule() {
        super("fpsboost", "FPS-Boost", "Mehr FPS: teure Grafik herunterdrehen, Sichtweite anpassen, im Hintergrund drosseln", Category.VISUELL, () -> Items.BLAZE_POWDER, false);
        clouds = new BoolSetting(this, "clouds", "Wolken aus", true);
        particles = new BoolSetting(this, "particles", "Partikel minimal", true);
        shadows = new BoolSetting(this, "shadows", "Schatten von Entities aus", true);
        biomeBlend = new BoolSetting(this, "biomeBlend", "Biom-Übergänge aus", true);
        entityDistance = new BoolSetting(this, "entityDistance", "Entities nur in der Nähe zeichnen", false);
        fastGraphics = new BoolSetting(this, "fastGraphics", "Schnelle Grafik (deckendes Laub)", true);
        smoothLighting = new BoolSetting(this, "smoothLighting", "Weiche Beleuchtung aus", false);
        background = new BoolSetting(this, "background", "Im Hintergrund nur 15 FPS", true);
        autoDistance = new BoolSetting(this, "autoDistance", "Sichtweite automatisch anpassen", false);
        targetFps = new SliderSetting(this, "targetFps", "Ziel-FPS", 30, 240, 10, 60, " FPS");
    }

    public void tick(Minecraft mc) {
        Options o = mc.options;
        boolean on = enabled();
        option("clouds", o.cloudStatus(), on && clouds.get(), "OFF");
        option("particles", o.particles(), on && particles.get(), "MINIMAL");
        option("shadows", o.entityShadows(), on && shadows.get(), false);
        option("biomeBlend", o.biomeBlendRadius(), on && biomeBlend.get(), 0);
        option("entityDistance", o.entityDistanceScaling(), on && entityDistance.get(), 0.5);
        option("ao", o.ambientOcclusion(), on && smoothLighting.get(), false);
        FpsCompat.graphics(o, on && fastGraphics.get());
        // Fenster nicht im Vordergrund: Bildrate drosseln (spart Strom und Wärme, gibt anderen Programmen Leistung)
        option("background", o.framerateLimit(), on && background.get() && !mc.isWindowActive(), 15);
        autoDistance(mc, o, on && autoDistance.get());
    }

    /**
     * Sichtweite automatisch: liegt die Bildrate einige Sekunden unter dem Ziel, eine Stufe weniger (bis 6); liegt sie
     * deutlich darüber, wieder eine mehr (bis zur eigenen Einstellung). Höchstens alle 4 Sekunden eine Änderung.
     */
    private void autoDistance(Minecraft mc, Options o, boolean active) {
        Map<String, String> saved = MyticClient.config().savedOptions;
        int current = o.renderDistance().get();
        if (!active || mc.level == null) {
            option("renderDistance", o.renderDistance(), false, 0);
            return;
        }
        if (!saved.containsKey("renderDistance")) {
            saved.put("renderDistance", String.valueOf(current));
            MyticClient.config().save();
        }
        long now = System.currentTimeMillis();
        if (now - lastAdjust < 4000) return;
        int own = Integer.parseInt(saved.get("renderDistance"));
        int fps = mc.getFps();
        int target = targetFps.intValue();
        if (fps < target * 0.85 && current > Math.min(6, own)) {
            o.renderDistance().set(current - 1);
            lastAdjust = now;
        } else if (fps > target * 1.35 && current < own) {
            o.renderDistance().set(current + 1);
            lastAdjust = now;
        }
    }

    /** Option auf den Boost-Wert setzen bzw. den gemerkten eigenen Wert zurückholen. Enums als Name angegeben. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void option(String key, OptionInstance option, boolean boost, Object boostValue) {
        Map<String, String> saved = MyticClient.config().savedOptions;
        Object current = option.get();
        if (boost) {
            if (!saved.containsKey(key)) {
                saved.put(key, encode(current));
                MyticClient.config().save();
            }
            Object target = current instanceof Enum && boostValue instanceof String
                    ? Enum.valueOf(((Enum) current).getDeclaringClass(), (String) boostValue) : boostValue;
            if (!current.equals(target)) option.set(target);
        } else if (saved.containsKey(key)) {
            Object original = decode(saved.remove(key), current);
            MyticClient.config().save();
            if (original != null && !current.equals(original)) option.set(original);
        }
    }

    private static String encode(Object value) {
        return value instanceof Enum ? ((Enum<?>) value).name() : String.valueOf(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object decode(String text, Object sample) {
        try {
            if (sample instanceof Enum) return Enum.valueOf(((Enum) sample).getDeclaringClass(), text);
            if (sample instanceof Boolean) return Boolean.valueOf(text);
            if (sample instanceof Integer) return Integer.valueOf(text);
            if (sample instanceof Double) return Double.valueOf(text);
        } catch (RuntimeException e) {
            MyticClient.LOG.warn("FPS-Boost: alter Wert {} nicht lesbar", text);
        }
        return null;
    }
}
