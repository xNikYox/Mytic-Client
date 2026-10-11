package de.myticlegacy.client.cosmetics;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Umhang-Physik (unabhängig von der Minecraft-Version, daher Java 8): Der Umhang ist eine Kette aus SEGMENTS Gliedern, die
 * im Körper-Koordinatensystem des Spielers simuliert wird (x = nach links, y = nach unten, z = nach hinten; Einheit: Pixel
 * des Skins). Kräfte: Schwerkraft (beim Bücken gekippt), Fahrtwind aus der Bewegung des Spielers (vor/zurück, seitwärts,
 * hoch/runter) mit leichtem Flattern, Nachschwingen beim Drehen, Luftwiderstand. Der Umhang kann nicht in den Rücken hinein.
 */
public final class CapePhysics {
    public static final int SEGMENTS = 16;
    private static final float LENGTH = 16f / SEGMENTS;
    private static final float STEP = 1f / 60f;
    private static final float GRAVITY = 157f;
    private static final float DRAG = 2.5f;
    /** Wie stark seitliche Bewegung und Drehen den Umhang zur Seite wehen */
    private static final float SIDE = 1.2f;

    private static final class Sim {
        final float[] x = new float[SEGMENTS + 1];
        final float[] y = new float[SEGMENTS + 1];
        final float[] z = new float[SEGMENTS + 1];
        final float[] px = new float[SEGMENTS + 1];
        final float[] py = new float[SEGMENTS + 1];
        final float[] pz = new float[SEGMENTS + 1];
        /** Winkel nach hinten [0..SEGMENTS), seitlich [SEGMENTS..2*SEGMENTS) */
        final float[] out = new float[SEGMENTS * 2];
        double lx, ly, lz;
        float lyaw;
        long lastNanos;
        float acc;
        float forward, up, sideways, yawRate;
        long seen;

        Sim() {
            for (int i = 0; i <= SEGMENTS; i++) {
                y[i] = py[i] = i * LENGTH;
            }
        }
    }

    private static final Map<Integer, Sim> SIMS = new ConcurrentHashMap<Integer, Sim>();
    private static long lastCleanup;

    private CapePhysics() {
    }

    /**
     * Winkel der Glieder für diese Figur, im Bogenmaß (absolut, 0 = senkrecht nach unten): in [i] nach hinten (positiv =
     * vom Rücken weg), in [SEGMENTS + i] zur Seite (positiv = nach links). x/y/z: Position in der Welt, bodyYaw:
     * Körperdrehung in Grad, tilt: Vorneigung des Körpers.
     */
    public static float[] angles(int id, double x, double y, double z, float bodyYaw, float tilt) {
        long now = System.nanoTime();
        Sim s = SIMS.get(id);
        if (s == null) {
            s = new Sim();
            s.lx = x;
            s.ly = y;
            s.lz = z;
            s.lyaw = bodyYaw;
            s.lastNanos = now;
            SIMS.put(id, s);
        }
        s.seen = now;
        float dt = (now - s.lastNanos) / 1e9f;
        // im selben Bild noch einmal gezeichnet (z. B. Figur im Inventar): nichts weiterrechnen
        if (dt > 0.002f) {
            s.lastNanos = now;
            if (dt > 0.25f) {
                // lange nicht gesehen: Bewegung vergessen
                dt = STEP;
                s.lx = x;
                s.ly = y;
                s.lz = z;
                s.lyaw = bodyYaw;
            }
            double dx = (x - s.lx) / dt, dy = (y - s.ly) / dt, dz = (z - s.lz) / dt;
            if (dx * dx + dy * dy + dz * dz > 400) dx = dy = dz = 0; // Teleport
            double yawRad = Math.toRadians(bodyYaw);
            float forward = (float) (-dx * Math.sin(yawRad) + dz * Math.cos(yawRad));
            // positiv = nach links (Blickrichtung Süden: links ist Osten, +x)
            float sideways = (float) (dx * Math.cos(yawRad) + dz * Math.sin(yawRad));
            float yawRate = wrap(bodyYaw - s.lyaw) / dt;
            if (Boolean.getBoolean("mytic.capeDemo")) {
                // Vorschau (16 s): stehen, gehen, sprinten mit Sprüngen, nach links, nach rechts, drehen, anhalten
                float t = (now / 1_000_000L % 16000L) / 1000f;
                forward = t < 2 ? 0f : t < 5 ? 4.3f : t < 8 ? 5.6f : 0f;
                dy = t >= 5 && t < 8 ? 6.0 * Math.cos((t - 5) * Math.PI / 0.6) : 0;
                sideways = t >= 8 && t < 10 ? 4.3f : t >= 10 && t < 12 ? -4.3f : 0f;
                yawRate = t >= 12 && t < 13.5f ? 240f : 0f;
            }
            float k = Math.min(1f, dt * 12f);
            s.forward += (forward - s.forward) * k;
            s.sideways += (sideways - s.sideways) * k;
            s.up += ((float) dy - s.up) * k;
            s.yawRate += (yawRate - s.yawRate) * k;
            s.lx = x;
            s.ly = y;
            s.lz = z;
            s.lyaw = bodyYaw;
            s.acc = Math.min(s.acc + dt, 0.1f);
            while (s.acc >= STEP) {
                step(s, tilt, now / 1e9f);
                s.acc -= STEP;
            }
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float ddx = s.x[i + 1] - s.x[i];
            float ddy = s.y[i + 1] - s.y[i];
            float ddz = s.z[i + 1] - s.z[i];
            s.out[i] = (float) Math.atan2(ddz, ddy);
            s.out[SEGMENTS + i] = (float) Math.atan2(ddx, Math.sqrt(ddy * ddy + ddz * ddz));
        }
        if (now - lastCleanup > 10_000_000_000L) cleanup(now);
        return s.out;
    }

    private static void step(Sim s, float tilt, float time) {
        float speed = (float) Math.sqrt(s.forward * s.forward + s.sideways * s.sideways);
        // Fahrtwind in Pixel/s: vorwärts → nach hinten, hoch → nach unten, seitwärts → zur anderen Seite; dazu leichte Grundneigung
        float airZ = s.forward * 16f + 5f;
        float airY = s.up * 16f;
        float airX = -s.sideways * 16f * SIDE;
        // Drehen: der Körper dreht sich unter dem Umhang weg, das untere Ende bleibt zurück
        float omega = (float) Math.toRadians(s.yawRate) * SIDE;
        float gz = GRAVITY * (float) Math.sin(tilt);
        float gy = GRAVITY * (float) Math.cos(tilt);
        for (int i = 1; i <= SEGMENTS; i++) {
            // Flattern: Wellen laufen den Umhang hinunter, stärker bei Tempo
            float wave = (float) Math.sin(time * 9f - i * 0.55f);
            float flutter = 1f + 0.22f * wave * Math.min(1f, speed / 3f);
            float vx = (s.x[i] - s.px[i]) / STEP;
            float vy = (s.y[i] - s.py[i]) / STEP;
            float vz = (s.z[i] - s.pz[i]) / STEP;
            float ax = DRAG * (airX - omega * (s.z[i] + 2f) - vx) + 6f * speed * (float) Math.sin(time * 7f - i * 0.5f);
            float ay = gy + DRAG * (airY - vy) + 12f * speed * (float) Math.sin(time * 13f - i * 0.8f);
            float az = gz + DRAG * (airZ * flutter - vz);
            float nx = s.x[i] + (s.x[i] - s.px[i]) * 0.985f + ax * STEP * STEP;
            float ny = s.y[i] + (s.y[i] - s.py[i]) * 0.985f + ay * STEP * STEP;
            float nz = s.z[i] + (s.z[i] - s.pz[i]) * 0.985f + az * STEP * STEP;
            s.px[i] = s.x[i];
            s.py[i] = s.y[i];
            s.pz[i] = s.z[i];
            s.x[i] = nx;
            s.y[i] = ny;
            s.z[i] = nz;
        }
        // Gliederlänge halten (oberes Ende fest am Rücken)
        for (int it = 0; it < 6; it++) {
            for (int i = 1; i <= SEGMENTS; i++) {
                float ddx = s.x[i] - s.x[i - 1];
                float ddy = s.y[i] - s.y[i - 1];
                float ddz = s.z[i] - s.z[i - 1];
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                if (d < 1e-4f) continue;
                float f = (d - LENGTH) / d;
                if (i == 1) {
                    s.x[i] -= ddx * f;
                    s.y[i] -= ddy * f;
                    s.z[i] -= ddz * f;
                } else {
                    s.x[i - 1] += ddx * f * 0.5f;
                    s.y[i - 1] += ddy * f * 0.5f;
                    s.z[i - 1] += ddz * f * 0.5f;
                    s.x[i] -= ddx * f * 0.5f;
                    s.y[i] -= ddy * f * 0.5f;
                    s.z[i] -= ddz * f * 0.5f;
                }
            }
            for (int i = 1; i <= SEGMENTS; i++) {
                // nicht in den Rücken, nicht über die Schultern, höchstens etwa 60° zur Seite
                if (s.z[i] < 0) s.z[i] = 0;
                if (s.y[i] < s.y[i - 1] - LENGTH * 0.2f && s.z[i] < 2f) s.y[i] = s.y[i - 1] - LENGTH * 0.2f;
                float maxX = s.y[i] * 1.2f + 1f;
                if (s.x[i] > maxX) s.x[i] = maxX;
                if (s.x[i] < -maxX) s.x[i] = -maxX;
            }
        }
    }

    private static float wrap(float degrees) {
        degrees %= 360f;
        if (degrees >= 180f) degrees -= 360f;
        if (degrees < -180f) degrees += 360f;
        return degrees;
    }

    private static void cleanup(long now) {
        lastCleanup = now;
        for (Iterator<Sim> it = SIMS.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seen > 10_000_000_000L) it.remove();
        }
    }
}
