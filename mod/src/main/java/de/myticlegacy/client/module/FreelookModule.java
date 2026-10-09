package de.myticlegacy.client.module;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Freelook: Taste halten und dich umsehen, ohne die Laufrichtung zu ändern (Kamera in der dritten Person). */
public class FreelookModule extends Module {
    private final BoolSetting invert;
    private boolean active;
    private float yaw;
    private float pitch;
    private CameraType previous;

    public FreelookModule() {
        super("freelook", "Freelook", "Umsehen mit gedrückter Alt-Taste, ohne dich zu drehen (auf manchen Servern verboten)",
                Category.MECHANIK, () -> Items.ENDER_EYE, false);
        invert = new BoolSetting(this, "invert", "Maus umkehren", false);
    }

    public boolean active() {
        return active;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void tick(Minecraft mc) {
        boolean want = enabled() && Compat.held(MyticClient.freelookKey) && Compat.screen(mc) == null && mc.player != null;
        if (want && !active) {
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            previous = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            active = true;
        } else if (!want && active) {
            mc.options.setCameraType(previous != null ? previous : CameraType.FIRST_PERSON);
            active = false;
        }
    }

    /** Mausbewegung, solange Freelook aktiv ist (gleiche Empfindlichkeit wie Entity#turn). */
    public void turn(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch += (float) (invert.get() ? -dy : dy) * 0.15f;
        pitch = Math.max(-90f, Math.min(90f, pitch));
    }
}
