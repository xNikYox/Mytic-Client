package de.myticlegacy.client.gui.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Übergänge wie CSS-Transitions: Dauer in Millisekunden, Kurve cubic-bezier(.2,.7,.2,1). Wechselt das Ziel
 * mittendrin, startet der Übergang vom aktuellen Wert aus (kein Springen). Zeitbasiert, unabhängig von den FPS.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Motion {
    private static final class State {
        float from, to, value;
        long start;
        int ms;
    }

    private static final Map<Object, State> STATES = new HashMap<Object, State>();
    /** Aus = alle Übergänge sofort (Einstellung „Animationen im Menü“) */
    public static volatile boolean enabled = true;

    private Motion() {
    }

    /** Kurve cubic-bezier(.2,.7,.2,1): x(t) nach t auflösen (Newton), dann y(t). */
    public static float ease(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        final double x1 = 0.2, y1 = 0.7, x2 = 0.2, y2 = 1.0;
        double t = x;
        for (int i = 0; i < 8; i++) {
            double cx = bez(t, x1, x2) - x;
            double d = dbez(t, x1, x2);
            if (Math.abs(cx) < 1e-5) break;
            if (Math.abs(d) < 1e-6) break;
            t -= cx / d;
            if (t < 0) t = 0;
            if (t > 1) t = 1;
        }
        return (float) bez(t, y1, y2);
    }

    private static double bez(double t, double p1, double p2) {
        double u = 1 - t;
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
    }

    private static double dbez(double t, double p1, double p2) {
        double u = 1 - t;
        return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2);
    }

    /** Sanft ein-/ausblenden (CSS ease-in-out), für Pulsieren und Funkeln */
    public static float easeInOut(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Übergang zu 0 oder 1 (Hover, Schalter …) */
    public static float to(Object key, boolean on, int ms) {
        return value(key, on ? 1f : 0f, ms);
    }

    /** Übergang zu einem beliebigen Zielwert */
    public static float value(Object key, float target, int ms) {
        long now = System.nanoTime() / 1_000_000L;
        State s = STATES.get(key);
        if (s == null) {
            s = new State();
            s.from = s.to = s.value = target;
            s.start = now;
            STATES.put(key, s);
            return target;
        }
        if (s.to != target) {
            s.from = s.value;
            s.to = target;
            s.start = now;
            s.ms = ms;
        }
        if (!enabled || s.ms <= 0) {
            s.value = s.to;
        } else {
            float p = Math.min(1f, (now - s.start) / (float) s.ms);
            s.value = s.from + (s.to - s.from) * ease(p);
        }
        return s.value;
    }

    /** Zustand vergessen (z. B. beim Schließen eines Bildschirms), damit nichts alt nachläuft */
    public static void forget(String prefix) {
        STATES.keySet().removeIf(k -> k instanceof String && ((String) k).startsWith(prefix));
    }
}
