package de.myticlegacy.client.cosmetics;

import java.util.Random;

/**
 * Bewegungsmuster der Auren (unabhängig von der Minecraft-Version, daher Java 8): liefert für ein neues Partikel den
 * Startpunkt relativ zu den Füßen des Spielers und die Geschwindigkeit (Blöcke pro Tick).
 */
public final class AuraMotion {
    private static final Random RANDOM = new Random();

    private AuraMotion() {
    }

    /** {dx, dy, dz, vx, vy, vz}; t = Zeit in Sekunden, k = laufende Nummer des Partikels. */
    public static double[] next(String pattern, double t, long k) {
        double r = RANDOM.nextDouble();
        double a;
        switch (pattern) {
            case "orbit": {
                // zwei Lichter kreisen gegenläufig in wechselnder Höhe
                a = t * 2.2 + (k % 2) * Math.PI;
                double y = 1.0 + 0.7 * Math.sin(t * 1.3 + (k % 2) * 2);
                return new double[]{Math.cos(a) * 0.75, y, Math.sin(a) * 0.75, 0, 0, 0};
            }
            case "halo":
                a = t * 1.6 + (k % 6) * Math.PI / 3;
                return new double[]{Math.cos(a) * 0.35, 2.25, Math.sin(a) * 0.35, 0, 0.002, 0};
            case "feet":
                a = RANDOM.nextDouble() * Math.PI * 2;
                return new double[]{Math.cos(a) * 0.65, 0.05, Math.sin(a) * 0.65, -Math.cos(a) * 0.01, 0.01, -Math.sin(a) * 0.01};
            case "rise":
                a = RANDOM.nextDouble() * Math.PI * 2;
                r = 0.35 + 0.35 * RANDOM.nextDouble();
                return new double[]{Math.cos(a) * r, RANDOM.nextDouble() * 1.2, Math.sin(a) * r, 0, 0.02 + 0.02 * RANDOM.nextDouble(), 0};
            case "fall":
                a = RANDOM.nextDouble() * Math.PI * 2;
                r = 0.2 + 0.7 * RANDOM.nextDouble();
                return new double[]{Math.cos(a) * r, 2.3 + 0.3 * RANDOM.nextDouble(), Math.sin(a) * r, 0, -0.03, 0};
            case "spiral": {
                a = t * 3.5 + (k % 3) * Math.PI * 2 / 3;
                double y = (t * 0.9 + (k % 3) * 0.66) % 2.1;
                return new double[]{Math.cos(a) * 0.6, y, Math.sin(a) * 0.6, 0, 0, 0};
            }
            default: // sparkle: zufällig rund um den Körper
                a = RANDOM.nextDouble() * Math.PI * 2;
                r = 0.45 + 0.4 * RANDOM.nextDouble();
                return new double[]{Math.cos(a) * r, 0.1 + RANDOM.nextDouble() * 1.9, Math.sin(a) * r, 0, 0.004, 0};
        }
    }

    /** Regenbogenfarbe zur Zeit t (Sekunden), als 0xRRGGBB. */
    public static int rainbow(double t) {
        float h = (float) ((t * 0.25) % 1.0);
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float q = 1 - f;
        float[] c;
        switch (i % 6) {
            case 0: c = new float[]{1, f, 0}; break;
            case 1: c = new float[]{q, 1, 0}; break;
            case 2: c = new float[]{0, 1, f}; break;
            case 3: c = new float[]{0, q, 1}; break;
            case 4: c = new float[]{f, 0, 1}; break;
            default: c = new float[]{1, 0, q};
        }
        return (Math.round(c[0] * 255) << 16) | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
    }
}
