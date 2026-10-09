package de.myticlegacy.client.module;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

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
        super("zoom", "Zoom", "Mit C zoomen, Mausrad ändert die Stärke", Category.MECHANIK, () -> Items.SPYGLASS, true);
        factor = new SliderSetting(this, "factor", "Zoomstärke", 1.5, 30, 0.5, 4, "×");
        smoothCamera = new BoolSetting(this, "smooth", "Weiche Kamera beim Zoomen", true);
        scroll = new BoolSetting(this, "scroll", "Mit dem Mausrad anpassen", true);
        animation = new BoolSetting(this, "animation", "Animiert zoomen", true);
    }

    public boolean active() {
        return enabled() && MyticClient.zoomKey.isDown() && Compat.screen(Minecraft.getInstance()) == null;
    }

    /** Wird für jedes Bild aus GameRenderer#getFov aufgerufen. */
    public float apply(float fov) {
        double target = active() ? factor.get() : 1.0;
        current = animation.get() ? current + (target - current) * 0.3 : target;
        if (Math.abs(current - target) < 0.01) current = target;
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
            savedSmoothCamera = mc.options.smoothCamera;
            if (smoothCamera.get()) mc.options.smoothCamera = true;
        } else {
            mc.options.smoothCamera = savedSmoothCamera;
            MyticClient.config().save();
        }
    }
}
