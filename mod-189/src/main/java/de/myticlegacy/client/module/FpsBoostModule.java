package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
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

    public FpsBoostModule() {
        super("fpsboost", "FPS-Boost", "Mehr FPS: Wolken, Partikel und Schatten herunterdrehen", Category.VISUELL, () -> Items.blaze_powder, false);
        clouds = new BoolSetting(this, "clouds", "Wolken aus", true);
        particles = new BoolSetting(this, "particles", "Partikel minimal", true);
        shadows = new BoolSetting(this, "shadows", "Schatten von Entities aus", true);
    }

    public void tick(Minecraft mc) {
        GameSettings o = mc.gameSettings;
        boolean on = enabled();
        o.clouds = option("clouds", o.clouds, on && clouds.get(), 0);
        o.particleSetting = option("particles", o.particleSetting, on && particles.get(), 2);
        o.entityShadows = option("shadows", o.entityShadows ? 1 : 0, on && shadows.get(), 0) == 1;
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
