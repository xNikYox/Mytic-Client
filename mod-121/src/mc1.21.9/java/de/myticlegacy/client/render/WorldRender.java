package de.myticlegacy.client.render;

/** 1.21.9: Die Fabric API dieser Version hat keine Welt-Zeichen-Events – Overlays sind hier nicht verfügbar. */
public final class WorldRender {
    public interface Drawer {
        void draw(QuadSink sink);
    }

    private WorldRender() {
    }

    public static void register(Drawer drawer) {
        // nicht verfügbar
    }
}
