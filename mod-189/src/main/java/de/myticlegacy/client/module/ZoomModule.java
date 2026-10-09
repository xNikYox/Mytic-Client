package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;

/** Zoom auf Taste C: weiches Ein- und Auszoomen, Stärke per Mausrad, optional weiche Kamera. */
public class ZoomModule extends Module {
    public final SliderSetting factor;
    private final BoolSetting smoothCamera;
    private final BoolSetting scroll;
    private final BoolSetting animation;

    private double current = 1.0;
    private boolean zooming;
    private boolean savedSmoothCamera;

    public ZoomModule() {
        super("zoom", "Zoom", "Mit C zoomen, Mausrad ändert die Stärke", Category.MECHANIK, () -> Items.fishing_rod, true);
        factor = new SliderSetting(this, "factor", "Zoomstärke", 1.5, 30, 0.5, 4, "×");
        smoothCamera = new BoolSetting(this, "smooth", "Weiche Kamera beim Zoomen", true);
        scroll = new BoolSetting(this, "scroll", "Mit dem Mausrad anpassen", true);
        animation = new BoolSetting(this, "animation", "Animiert zoomen", true);
    }

    public boolean active() {
        return enabled() && Compat.held(MyticClient.zoomKey) && Compat.screen(Minecraft.getMinecraft()) == null;
    }

    /** Einmal pro Bild: Zoomstufe weich nachführen. */
    public void frame() {
        double target = active() ? factor.get() : 1.0;
        current = animation.get() ? current + (target - current) * 0.3 : target;
        if (Math.abs(current - target) < 0.01) current = target;
    }

    public float apply(float fov) {
        return (float) (fov / current);
    }

    public boolean scroll(double amount) {
        if (!active() || !scroll.get()) return false;
        factor.set(factor.get() * (amount > 0 ? 1.2 : 1 / 1.2));
        return true;
    }

    public void tick(Minecraft mc) {
        boolean now = active();
        if (now == zooming) return;
        zooming = now;
        if (now) {
            savedSmoothCamera = mc.gameSettings.smoothCamera;
            if (smoothCamera.get()) mc.gameSettings.smoothCamera = true;
        } else {
            mc.gameSettings.smoothCamera = savedSmoothCamera;
            MyticClient.config().save();
        }
    }
}
