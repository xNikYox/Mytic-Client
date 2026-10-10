package de.myticlegacy.client.render;

/** Nimmt Vierecke in Weltkoordinaten entgegen (für Overlays in der Welt, z. B. Light Level Overlay). */
public interface QuadSink {
    void quad(double x1, double y1, double z1, double x2, double y2, double z2,
              double x3, double y3, double z3, double x4, double y4, double z4, int argb);
}
