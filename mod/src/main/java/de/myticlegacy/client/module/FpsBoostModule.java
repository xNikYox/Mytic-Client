package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
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

    public FpsBoostModule() {
        super("fpsboost", "FPS-Boost", "Mehr FPS: Wolken, Partikel, Schatten und Biom-Übergänge herunterdrehen", Category.VISUELL, () -> Items.BLAZE_POWDER, false);
        clouds = new BoolSetting(this, "clouds", "Wolken aus", true);
        particles = new BoolSetting(this, "particles", "Partikel minimal", true);
        shadows = new BoolSetting(this, "shadows", "Schatten von Entities aus", true);
        biomeBlend = new BoolSetting(this, "biomeBlend", "Biom-Übergänge aus", true);
        entityDistance = new BoolSetting(this, "entityDistance", "Entities nur in der Nähe zeichnen", false);
    }

    public void tick(Minecraft mc) {
        Options o = mc.options;
        boolean on = enabled();
        option("clouds", o.cloudStatus(), on && clouds.get(), "OFF");
        option("particles", o.particles(), on && particles.get(), "MINIMAL");
        option("shadows", o.entityShadows(), on && shadows.get(), false);
        option("biomeBlend", o.biomeBlendRadius(), on && biomeBlend.get(), 0);
        option("entityDistance", o.entityDistanceScaling(), on && entityDistance.get(), 0.5);
    }

    /** Option auf den Boost-Wert setzen bzw. den gemerkten eigenen Wert zurückholen. Enums als Name angegeben. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void option(String key, OptionInstance option, boolean boost, Object boostValue) {
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
