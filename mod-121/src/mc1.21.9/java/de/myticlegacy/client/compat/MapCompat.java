package de.myticlegacy.client.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Kamera für Wegpunkt-Markierungen. Fassung 1.21 bis 1.21.10 (getPosition, getYRot, getXRot). */
public final class MapCompat {
    /** Sichtfeld der Welt im letzten Bild (Grad), vom GameRenderer-Mixin gesetzt */
    public static volatile float fov = 70f;

    private MapCompat() {
    }

    private static Camera camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static Vec3 cameraPos() {
        return camera().getPosition();
    }

    public static float cameraYaw() {
        return camera().getYRot();
    }

    public static float cameraPitch() {
        return camera().getXRot();
    }

    public static float fov() {
        return fov;
    }
}
