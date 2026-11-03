package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * Hintergrund-FPS: Ist das Fenster nicht im Vordergrund (anderes Programm oder eine zweite Minecraft-Instanz aktiv),
 * wird die Bildrate begrenzt – spart Grafikkarte, CPU und Strom, die aktive Instanz läuft flüssiger.
 * Greift direkt in die Bildraten-Begrenzung (BackgroundFpsMixin), ohne die Minecraft-Optionen zu ändern.
 */
public class BackgroundFpsModule extends Module {
    private static BackgroundFpsModule instance;
    private final SliderSetting fps;

    public BackgroundFpsModule() {
        super("backgroundfps", "Hintergrund-FPS", "Weniger FPS, solange das Fenster nicht im Vordergrund ist – mehr Leistung für das aktive Spiel", Category.CLIENT, () -> Items.CLOCK, true);
        fps = new SliderSetting(this, "fps", "FPS im Hintergrund", 5, 60, 5, 30, " FPS");
        instance = this;
    }

    /** Bildraten-Grenze für dieses Bild: im Hintergrund höchstens die eingestellten FPS */
    public static int limit(int vanilla) {
        if (instance == null || !instance.enabled()) return vanilla;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isWindowActive()) return vanilla;
        return Math.min(vanilla, instance.fps.intValue());
    }
}
