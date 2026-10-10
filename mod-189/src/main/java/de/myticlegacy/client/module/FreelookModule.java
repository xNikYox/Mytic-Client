package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;

/**
 * Freelook: Taste halten und dich umsehen, ohne die Laufrichtung zu ändern (Kamera in der dritten Person).
 * In 1.8.9 ohne Mixins: Die Mausbewegung eines Bildes wird vom Spieler zurückgenommen und auf die Kamera übertragen.
 */
public class FreelookModule extends Module {
    private final BoolSetting invert;
    private final ModeSetting mode;
    /** Umschalten-Modus: an/aus per Tastendruck. */
    private boolean toggled;
    private boolean wasDown;
    private boolean active;
    private float yaw;
    private float pitch;
    private float savedYaw;
    private float savedPitch;
    private int previous;

    public FreelookModule() {
        super("freelook", "Freelook", "Umsehen mit gedrückter Alt-Taste, ohne dich zu drehen (auf manchen Servern verboten)",
                Category.MECHANIK, () -> Items.ender_eye, false);
        mode = new ModeSetting(this, "mode", "Modus", "Halten", "Halten", "Umschalten");
        invert = new BoolSetting(this, "invert", "Maus umkehren", false);
    }

    public boolean active() {
        return active;
    }

    public void tick(Minecraft mc) {
        // wie bei Lunar: Halten (nur solange gedrückt) oder Umschalten (einmal drücken an, nochmal aus)
        boolean down = enabled() && Compat.held(MyticClient.freelookKey) && Compat.screen(mc) == null;
        if (!enabled() || mc.thePlayer == null) toggled = false;
        else if (down && !wasDown) toggled = !toggled;
        wasDown = down;
        boolean want = enabled() && mc.thePlayer != null && Compat.screen(mc) == null && (mode.is("Umschalten") ? toggled : down);
        if (want && !active) {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
            previous = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 1;
            active = true;
        } else if (!want && active) {
            mc.gameSettings.thirdPersonView = previous;
            active = false;
        }
    }

    /** Zu Beginn jedes Bildes: Blickrichtung des Spielers merken. */
    public void frameStart(Minecraft mc) {
        if (!active || mc.thePlayer == null) return;
        savedYaw = mc.thePlayer.rotationYaw;
        savedPitch = mc.thePlayer.rotationPitch;
    }

    /** Beim Ausrichten der Kamera: Mausbewegung vom Spieler auf die Kamera verschieben. Liefert {yaw, pitch} oder null. */
    public float[] camera(Entity entity) {
        if (!active || entity != Minecraft.getMinecraft().thePlayer) return null;
        float dYaw = entity.rotationYaw - savedYaw;
        float dPitch = entity.rotationPitch - savedPitch;
        entity.rotationYaw = savedYaw;
        entity.prevRotationYaw -= dYaw;
        entity.rotationPitch = savedPitch;
        entity.prevRotationPitch -= dPitch;
        yaw += dYaw;
        pitch = Math.max(-90f, Math.min(90f, pitch + (invert.get() ? -dPitch : dPitch)));
        return new float[]{yaw + 180f, pitch};
    }
}
