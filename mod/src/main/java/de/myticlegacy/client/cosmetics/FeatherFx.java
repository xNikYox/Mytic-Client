package de.myticlegacy.client.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallende Federn (unabhängig von der Minecraft-Version, daher Java 8): Von Flügeln lösen sich ab und zu Federn, segeln
 * schaukelnd und drehend zu Boden und werden am Ende kleiner. Positionen in Weltkoordinaten; local() rechnet sie in das
 * Modell-Koordinatensystem des Spielers um, in dem die Flügel-Ebene zeichnet.
 */
public final class FeatherFx {
    public static final class Feather {
        double x, y, z;
        double vx, vy, vz;
        float age;
        final float life, seed, swayX, swayZ;
        /** Teil zum Zeichnen (je Version eigenes Modell-Objekt) */
        public Object part;

        Feather(double x, double y, double z, double vx, double vy, double vz, float life, float seed, float swayX, float swayZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.life = life;
            this.seed = seed;
            this.swayX = swayX;
            this.swayZ = swayZ;
        }

        /** Drehung um die Hochachse (Grad) */
        public float spin() {
            return (age * 70f + seed * 360f) % 360f;
        }

        /** Schaukeln (Grad) */
        public float rock() {
            return (float) Math.sin(age * 3.1f + seed * 6f) * 35f;
        }

        /** Größe 0..1 (am Anfang kurz wachsen, am Ende schrumpfen) */
        public float size() {
            return Math.min(1f, Math.min(age / 0.25f, (life - age) / 0.6f));
        }
    }

    private static final class Owner {
        final List<Feather> feathers = new ArrayList<Feather>();
        long lastNanos;
        float untilNext = 0.5f;
        long seen;
    }

    private static final int MAX = 14;
    private static final Map<Integer, Owner> OWNERS = new ConcurrentHashMap<Integer, Owner>();
    private static final Random RANDOM = new Random();
    private static long lastCleanup;

    private FeatherFx() {
    }

    /**
     * Federn dieser Figur weiterrechnen und zurückgeben; null, wenn die Figur in diesem Bild schon gezeichnet wurde (z. B.
     * nochmal im Inventar). x/y/z: Position in der Welt, bodyYaw in Grad, flying: Elytra-Flug (mehr Federn).
     */
    public static List<Feather> update(int id, double x, double y, double z, float bodyYaw, boolean flying) {
        long now = System.nanoTime();
        Owner o = OWNERS.get(id);
        if (o == null) {
            o = new Owner();
            o.lastNanos = now;
            OWNERS.put(id, o);
        }
        o.seen = now;
        float dt = (now - o.lastNanos) / 1e9f;
        if (dt < 0.002f) return null;
        o.lastNanos = now;
        if (dt > 0.25f) dt = 0.05f;
        // neue Feder an einer zufälligen Stelle der Flügel (hinter dem Rücken, links oder rechts)
        o.untilNext -= dt;
        if (o.untilNext <= 0 && o.feathers.size() < MAX) {
            o.untilNext = flying ? 0.2f + RANDOM.nextFloat() * 0.25f : 0.45f + RANDOM.nextFloat() * 0.7f;
            double yaw = Math.toRadians(bodyYaw);
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double side = (RANDOM.nextBoolean() ? 1 : -1) * (0.25 + RANDOM.nextDouble() * 0.85);
            double back = 0.2 + RANDOM.nextDouble() * 0.25;
            double px = x + rx * side - fx * back;
            double pz = z + rz * side - fz * back;
            double py = y + 1.05 + RANDOM.nextDouble() * 0.6;
            o.feathers.add(new Feather(px, py, pz, rx * side * 0.15, 0.05, rz * side * 0.15, 3.0f + RANDOM.nextFloat() * 1.5f,
                    RANDOM.nextFloat(), (float) (0.15 + RANDOM.nextDouble() * 0.2), (float) (0.15 + RANDOM.nextDouble() * 0.2)));
        }
        for (Iterator<Feather> it = o.feathers.iterator(); it.hasNext(); ) {
            Feather f = it.next();
            f.age += dt;
            if (f.age >= f.life) {
                it.remove();
                continue;
            }
            // langsam sinken (Luftwiderstand), seitlich hin und her pendeln
            f.vy += (-0.45 - f.vy) * Math.min(1f, dt * 2.5f);
            f.vx += (-f.vx) * Math.min(1f, dt * 1.5f);
            f.vz += (-f.vz) * Math.min(1f, dt * 1.5f);
            double sway = Math.cos(f.age * 2.6f + f.seed * 6f);
            f.x += (f.vx + sway * f.swayX) * dt;
            f.z += (f.vz + Math.sin(f.age * 2.1f + f.seed * 4f) * f.swayZ) * dt;
            f.y += f.vy * dt * (0.7 + 0.3 * Math.abs(sway));
        }
        if (now - lastCleanup > 10_000_000_000L) {
            lastCleanup = now;
            for (Iterator<Owner> it = OWNERS.values().iterator(); it.hasNext(); ) {
                if (now - it.next().seen > 10_000_000_000L) it.remove();
            }
        }
        return Collections.unmodifiableList(new ArrayList<Feather>(o.feathers));
    }

    /**
     * Position einer Feder im Modell-Koordinatensystem des Spielers (wie es die Ebenen bekommen: um 180°-Körperdrehung
     * gedreht, gespiegelt, mit scale verkleinert und um yOffset nach unten verschoben). Einheit: Blöcke.
     */
    public static float[] local(Feather f, double ex, double ey, double ez, float bodyYaw, float scale, float yOffset) {
        double dx = f.x - ex, dy = f.y - ey, dz = f.z - ez;
        double theta = Math.toRadians(180.0 - bodyYaw);
        double c = Math.cos(theta), s = Math.sin(theta);
        double rx = dx * c - dz * s;
        double rz = dx * s + dz * c;
        return new float[]{(float) (-rx / scale), (float) (-dy / scale + yOffset), (float) (rz / scale)};
    }
}
