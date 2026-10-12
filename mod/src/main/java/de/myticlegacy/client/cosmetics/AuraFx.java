package de.myticlegacy.client.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eigene Aura-Partikel (unabhängig von der Minecraft-Version, daher Java 8): Positionen relativ zu den Füßen des
 * Spielers in Welt-Achsen (die Aura wandert mit). Je Muster eigene Bewegung; Größe wächst am Anfang und schrumpft am Ende.
 */
public final class AuraFx {
    public static final class P {
        /** Position relativ zu den Füßen (Blöcke, Welt-Achsen) */
        public float x, y, z;
        float vx, vy, vz;
        float age;
        final float life;
        public final int sprite;
        final float seed;
        final float scale;
        // Kreisbahn (orbit, halo, spiral, feet)
        float ang, angVel, radius, radiusVel;
        boolean circling;
        /** Drehung in der Bildebene (Bogenmaß) */
        public float roll;
        float rollVel;
        final boolean twinkle;
        /** Teil zum Zeichnen (je Version eigenes Modell-Objekt) */
        public Object part;

        P(float life, int sprite, float seed, float scale, boolean twinkle) {
            this.life = life;
            this.sprite = sprite;
            this.seed = seed;
            this.scale = scale;
            this.twinkle = twinkle;
        }

        /** Größe in Blöcken (0 = unsichtbar) */
        public float size() {
            float k = Math.min(1f, Math.min(age / (life * 0.18f), (life - age) / (life * 0.35f)));
            if (twinkle) k *= 0.55f + 0.45f * (float) Math.sin(age * 9 + seed * 6);
            return Math.max(0f, k) * scale;
        }
    }

    private static final class Owner {
        final List<P> list = new ArrayList<P>();
        long lastNanos;
        double acc;
        long seen;
        long counter;
    }

    private static final int MAX = 40;
    private static final Map<Integer, Owner> OWNERS = new ConcurrentHashMap<Integer, Owner>();
    private static final Random RANDOM = new Random();
    private static long lastCleanup;

    private AuraFx() {
    }

    /** Partikel dieser Figur weiterrechnen; null, wenn sie in diesem Bild schon gezeichnet wurde (z. B. im Inventar). */
    public static List<P> update(int id, CosmeticsCore.Aura aura) {
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
        double t = now / 1e9;
        o.acc += aura.rate * dt;
        while (o.acc >= 1) {
            o.acc -= 1;
            if (o.list.size() < MAX) o.list.add(spawn(aura, t, o.counter++));
        }
        for (Iterator<P> it = o.list.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            if (p.circling) {
                p.ang += p.angVel * dt;
                p.radius += p.radiusVel * dt;
                p.x = (float) Math.cos(p.ang) * p.radius;
                p.z = (float) Math.sin(p.ang) * p.radius;
                p.y += p.vy * dt;
            } else {
                // leichtes Pendeln quer zur Bewegung
                float sway = (float) Math.sin(p.age * 2.4 + p.seed * 6) * 0.25f;
                p.x += (p.vx + sway * Math.abs(p.vy)) * dt;
                p.y += p.vy * dt;
                p.z += (p.vz + sway * 0.5f * Math.abs(p.vy)) * dt;
            }
            p.roll += p.rollVel * dt;
        }
        if (now - lastCleanup > 10_000_000_000L) {
            lastCleanup = now;
            for (Iterator<Owner> it = OWNERS.values().iterator(); it.hasNext(); ) {
                if (now - it.next().seen > 10_000_000_000L) it.remove();
            }
        }
        return Collections.unmodifiableList(new ArrayList<P>(o.list));
    }

    private static P spawn(CosmeticsCore.Aura aura, double t, long k) {
        int sprite = (int) (k % Math.max(1, aura.sprites));
        float scale = aura.size * (0.75f + 0.4f * RANDOM.nextFloat());
        float a = (float) (RANDOM.nextDouble() * Math.PI * 2);
        P p;
        switch (aura.pattern) {
            case "orbit":
                p = new P(2.6f, sprite, RANDOM.nextFloat(), scale, false);
                p.circling = true;
                p.ang = (float) (t * 1.7 + (k % 2) * Math.PI);
                p.angVel = 1.7f;
                p.radius = 0.7f;
                p.y = 0.35f + 1.5f * RANDOM.nextFloat();
                p.vy = (RANDOM.nextFloat() - 0.5f) * 0.15f;
                break;
            case "halo":
                p = new P(1.8f, sprite, RANDOM.nextFloat(), scale * 0.8f, false);
                p.circling = true;
                p.ang = (float) (t * 1.4 + (k % 6) * Math.PI / 3);
                p.angVel = 1.4f;
                p.radius = 0.36f;
                p.y = 2.2f;
                break;
            case "feet":
                p = new P(1.6f, sprite, RANDOM.nextFloat(), scale, false);
                p.circling = true;
                p.ang = a;
                p.angVel = 0.8f;
                p.radius = 0.75f;
                p.radiusVel = -0.18f;
                p.y = 0.05f;
                p.vy = 0.25f;
                break;
            case "spiral":
                p = new P(2.6f, sprite, RANDOM.nextFloat(), scale, false);
                p.circling = true;
                p.ang = (float) (t * 2.6 + (k % 3) * Math.PI * 2 / 3);
                p.angVel = 2.6f;
                p.radius = 0.6f;
                p.y = 0.05f;
                p.vy = 0.8f;
                break;
            case "rise":
                p = new P(2.2f, sprite, RANDOM.nextFloat(), scale, false);
                float r = 0.3f + 0.4f * RANDOM.nextFloat();
                p.x = (float) Math.cos(a) * r;
                p.z = (float) Math.sin(a) * r;
                p.y = 0.1f + RANDOM.nextFloat() * 0.9f;
                p.vy = 0.45f + 0.25f * RANDOM.nextFloat();
                break;
            case "fall":
                p = new P(3.6f, sprite, RANDOM.nextFloat(), scale, false);
                float rf = 0.2f + 0.7f * RANDOM.nextFloat();
                p.x = (float) Math.cos(a) * rf;
                p.z = (float) Math.sin(a) * rf;
                p.y = 2.3f + 0.3f * RANDOM.nextFloat();
                p.vy = -0.45f - 0.15f * RANDOM.nextFloat();
                p.rollVel = (RANDOM.nextFloat() - 0.5f) * 3f;
                p.roll = a;
                break;
            default: // sparkle: zufällig rund um den Körper, blinkt
                p = new P(1.1f, sprite, RANDOM.nextFloat(), scale, true);
                float rs = 0.45f + 0.4f * RANDOM.nextFloat();
                p.x = (float) Math.cos(a) * rs;
                p.z = (float) Math.sin(a) * rs;
                p.y = 0.1f + RANDOM.nextFloat() * 1.9f;
                p.vy = 0.05f;
                break;
        }
        return p;
    }
}
