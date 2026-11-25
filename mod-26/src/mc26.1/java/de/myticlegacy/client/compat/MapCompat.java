package de.myticlegacy.client.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Kamera für Wegpunkt-Markierungen. Fassung 26.1.x (getMainCamera): die Kamera kennt ihr Sichtfeld selbst. */
public final class MapCompat {
    public static volatile float fov = 70f;

    private MapCompat() {
    }

    private static Camera camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static Vec3 cameraPos() {
        return camera().position();
    }

    public static float cameraYaw() {
        return camera().yRot();
    }

    public static float cameraPitch() {
        return camera().xRot();
    }

    public static float fov() {
        float f = camera().getFov();
        return f > 1 && f < 179 ? f : fov;
    }
}
