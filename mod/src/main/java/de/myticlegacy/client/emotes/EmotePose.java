package de.myticlegacy.client.emotes;

import java.util.Arrays;

/**
 * Prozedurale Posen der Emotes: aus Emote und Zeit werden Winkel (Bogenmaß) und Verschiebungen (Modell-Pixel) für
 * Kopf, Körper, Arme und Beine berechnet – unabhängig von der Minecraft-Version. Die Versionen übertragen das Ergebnis
 * auf ihr Spielermodell (EmoteModel bzw. 1.8.9 EmotePlayerModel).
 *
 * <p>Richtungen wie im Spielermodell: xRot negativ = Arm/Bein nach vorn bzw. oben, xRot positiv = nach hinten; beim
 * hängenden Arm ist zRot positiv (rechts) bzw. negativ (links) nach außen, beim erhobenen Arm umgekehrt; yRot negativ
 * dreht den rechten Arm zur Mitte. Kopf xRot positiv = nach unten. NaN = Minecraft-Wert behalten.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class EmotePose {
    /** Kopf: Zusatz zu Blickrichtung (x, y, z) */
    public static final int HEAD = 0;
    /** Körper, rechter/linker Arm, rechtes/linkes Bein: Winkel x, y, z (ersetzen den Minecraft-Wert) */
    public static final int BODY = 3, RARM = 6, LARM = 9, RLEG = 12, LLEG = 15;
    /** Vorbeugen ab der Hüfte (Bogenmaß, vorwärts positiv) */
    public static final int LEAN = 18;
    /** ganzes Modell tiefer (Pixel; negativ = höher) */
    public static final int DROP = 19;
    /** Überblendung 0–1 (Ein- und Ausblenden) */
    public static final int WEIGHT = 20;
    public static final int SIZE = 21;

    private static final float PI = (float) Math.PI;
    private static final float IN = 0.22f, OUT = 0.28f;

    private EmotePose() {
    }

    /**
     * Pose des Emotes id nach t Sekunden (Länge len Sekunden) in out schreiben. false = Emote vorbei oder unbekannt.
     */
    public static boolean compute(String id, float t, float len, float[] out) {
        if (t < 0 || t >= len) return false;
        Arrays.fill(out, Float.NaN);
        out[HEAD] = out[HEAD + 1] = out[HEAD + 2] = 0;
        out[LEAN] = 0;
        out[DROP] = 0;
        out[WEIGHT] = smooth(Math.min(t / IN, (len - t) / OUT));
        float w2pi = 2 * PI;
        if ("winken".equals(id)) {
            // erhobener Arm: zRot negativ = nach außen (beim hängenden Arm wäre es positiv)
            arm(out, RARM, -2.75f, 0, -0.22f + 0.42f * (float) Math.sin(t * w2pi * 2.2f));
            arm(out, LARM, 0.05f, 0, -0.08f);
            out[HEAD + 2] = 0.08f * (float) Math.sin(t * w2pi * 1.1f);
        } else if ("jubeln".equals(id)) {
            float s = (float) Math.sin(t * w2pi * 2.4f);
            arm(out, RARM, -2.95f + 0.12f * s, 0, -0.4f - 0.1f * s); // Arme als V nach oben
            arm(out, LARM, -2.95f - 0.12f * s, 0, 0.4f + 0.1f * s);
            out[HEAD] = -0.3f;
            out[DROP] = -2.2f * Math.abs((float) Math.sin(t * PI * 2.4f)); // kleine Sprünge
            legs(out, 0.1f * s, -0.1f * s);
        } else if ("klatschen".equals(id)) {
            float c = 0.5f + 0.5f * (float) Math.cos(t * w2pi * 2.6f); // 1 = Hände zusammen
            arm(out, RARM, -1.3f, -0.2f - 0.38f * c, 0.05f);
            arm(out, LARM, -1.3f, 0.2f + 0.38f * c, -0.05f);
            out[HEAD] = 0.08f * c;
        } else if ("tanzen".equals(id)) {
            float s = (float) Math.sin(t * w2pi * 1.6f), b = Math.abs((float) Math.sin(t * w2pi * 1.6f));
            out[BODY] = 0;
            out[BODY + 1] = 0.35f * s;
            out[BODY + 2] = 0;
            arm(out, RARM, -1.3f + 1.1f * s, 0.2f * s, 0.55f);
            arm(out, LARM, -1.3f - 1.1f * s, 0.2f * s, -0.55f);
            legs(out, 0.38f * s, -0.38f * s);
            out[HEAD + 2] = 0.18f * s;
            out[HEAD + 1] = 0.25f * s;
            out[DROP] = 1.4f * b;
        } else if ("verbeugen".equals(id)) {
            float e = env(t, len, 0.45f, 0.55f);
            out[LEAN] = 0.95f * e;
            arm(out, RARM, -0.95f * e, -0.75f * e, 0.05f);  // Hand vor die Brust
            arm(out, LARM, 0.55f * e, 0.2f * e, -0.12f);    // Hand hinter den Rücken
            legs(out, 0, 0);
            out[HEAD] = 0.15f * e;
        } else if ("facepalm".equals(id)) {
            float e = env(t, len, 0.3f, 0.45f);
            arm(out, RARM, -2.4f * e, -0.55f * e, 0.1f * e);
            arm(out, LARM, 0.05f, 0, -0.08f);
            out[HEAD] = 0.42f * e;
            out[HEAD + 1] = 0.16f * e * (float) Math.sin(t * w2pi * 1.8f); // Kopfschütteln
        } else if ("hinsetzen".equals(id)) {
            float e = env(t, len, 0.5f, 0.5f);
            out[DROP] = 10.5f * e;
            out[RLEG] = -1.48f * e;
            out[RLEG + 1] = 0.22f * e;
            out[RLEG + 2] = 0.05f;
            out[LLEG] = -1.48f * e;
            out[LLEG + 1] = -0.22f * e;
            out[LLEG + 2] = -0.05f;
            arm(out, RARM, 0.5f * e, 0, 0.22f * e);   // Hände hinten aufgestützt
            arm(out, LARM, 0.5f * e, 0, -0.22f * e);
            out[HEAD] = -0.05f * e + 0.04f * (float) Math.sin(t * w2pi * 0.4f);
        } else {
            return false;
        }
        return true;
    }

    /** Überblendung 0–1 mit weichem Verlauf */
    private static float smooth(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }

    /** Hüllkurve: in a Sekunden hinein, b Sekunden vor Schluss wieder hinaus */
    private static float env(float t, float len, float a, float b) {
        return smooth(Math.min(t / a, (len - t) / b));
    }

    private static void arm(float[] out, int i, float x, float y, float z) {
        out[i] = x;
        out[i + 1] = y;
        out[i + 2] = z;
    }

    private static void legs(float[] out, float right, float left) {
        out[RLEG] = right;
        out[RLEG + 1] = 0;
        out[RLEG + 2] = 0;
        out[LLEG] = left;
        out[LLEG + 1] = 0;
        out[LLEG + 2] = 0;
    }

    /**
     * Gemeinsame Rechnung der Versionen: aus Pose p und den Minecraft-Werten eines Teils (rot = x, y, z) die neuen Winkel.
     * Kopf: Zusatz, sonst Überblendung zum Pose-Wert (NaN = Minecraft-Wert).
     */
    public static float blend(float vanilla, float pose, float weight) {
        return Float.isNaN(pose) ? vanilla : vanilla + (pose - vanilla) * weight;
    }

    /**
     * Verschiebungen fürs Vorbeugen ab der Hüfte (Drehpunkt 12 Pixel unter dem Hals): {dy, dz} für einen Punkt, der
     * dist Pixel über der Hüfte am Körper sitzt (Hals 12, Schultern 10).
     */
    public static float leanDy(float lean, float dist) {
        return dist - dist * (float) Math.cos(lean);
    }

    public static float leanDz(float lean, float dist) {
        return -dist * (float) Math.sin(lean);
    }
}
