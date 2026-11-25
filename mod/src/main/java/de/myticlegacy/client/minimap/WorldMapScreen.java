package de.myticlegacy.client.minimap;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.PixelTex;
import de.myticlegacy.client.compat.Tex;
import de.myticlegacy.client.gui.DesignScreen;
import de.myticlegacy.client.gui.Input;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Einfache Weltkarte aus den selbst erkundeten (lokal gespeicherten) Chunks: ziehen zum Verschieben, Mausrad zum
 * Zoomen, Rechtsklick setzt einen Wegpunkt. Gezeichnet aus Kacheln (128 Texel), die nur bei neuen Daten und
 * höchstens ein paar pro Bild neu berechnet werden; weit herausgezoomt mit gröberen Kacheln.
 */
public class WorldMapScreen extends DesignScreen {
    private static final int TILE = 128, MAX_TILES = 220, BUILDS_PER_FRAME = 3;
    private static float savedScale = 1.5f;

    private static final class Tile {
        PixelTex tex;
        int stamp = -1;
        long builtAt;
    }

    /** Kacheln über alle Karten-Fenster hinweg (bis die Welt wechselt) */
    private static final Map<Long, Tile> TILES = new LinkedHashMap<>(64, 0.75f, true);
    private static String tilesFor;
    private static int texSeq;

    private double centerX, centerZ;
    private float scale = savedScale; // Bildschirm-Pixel je Block
    private boolean panning, moved;
    private double panMouseX, panMouseY, panStartX, panStartZ;
    private int builds;

    public WorldMapScreen() {
        super("worldmap", "Weltkarte");
        LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null) {
            centerX = p.getX();
            centerZ = p.getZ();
        }
    }

    private static MapData data() {
        MinimapModule m = MinimapModule.get();
        return m == null ? null : m.data();
    }

    // ------------------------------------------------------------------------------------------ Kacheln

    private static void dropAll() {
        for (Tile t : TILES.values()) if (t.tex != null) t.tex.close();
        TILES.clear();
    }

    private Tile tile(MapData data, int lod, int tx, int tz) {
        if (!data.key.equals(tilesFor)) {
            dropAll();
            tilesFor = data.key;
        }
        long key = ((long) lod << 60) ^ ((long) (tx & 0x3FFFFFFF) << 30) ^ (tz & 0x3FFFFFFF);
        Tile t = TILES.get(key);
        if (t == null) {
            t = new Tile();
            TILES.put(key, t);
            if (TILES.size() > MAX_TILES) {
                Iterator<Map.Entry<Long, Tile>> it = TILES.entrySet().iterator();
                Tile old = it.next().getValue();
                it.remove();
                if (old.tex != null) old.tex.close();
            }
        }
        int changes = data.changes();
        long now = System.currentTimeMillis();
        boolean fresh = t.stamp == -1;
        boolean stale = t.stamp != changes && now - t.builtAt > 2500;
        if ((fresh || stale) && builds < BUILDS_PER_FRAME) {
            builds++;
            if (t.tex == null) t.tex = new PixelTex("worldmap/" + (texSeq++), TILE, TILE);
            int bpt = 1 << (2 * lod), x0 = tx * TILE * bpt, z0 = tz * TILE * bpt;
            for (int j = 0; j < TILE; j++)
                for (int i = 0; i < TILE; i++) t.tex.set(i, j, data.color(x0 + i * bpt, z0 + j * bpt));
            t.tex.upload();
            t.stamp = changes;
            t.builtAt = now;
        }
        return t.stamp == -1 ? null : t;
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xFF0B0911);
    }

    private double blockX(double sx) {
        return centerX + (sx - vw / 2.0) / scale;
    }

    private double blockZ(double sy) {
        return centerZ + (sy - vh / 2.0) / scale;
    }

    private float screenX(double bx) {
        return (float) ((bx - centerX) * scale + vw / 2.0);
    }

    private float screenY(double bz) {
        return (float) ((bz - centerZ) * scale + vh / 2.0);
    }

    @Override
    protected void frame(GuiGraphics g) {
        builds = 0;
        MapData data = data();
        MinimapModule m = MinimapModule.get();
        Draw.rect(g, 0, 0, vw, vh, 0, 0xFF0B0911);
        // feines Raster im Hintergrund (Chunk-Raster beim Hineinzoomen)
        float step = scale >= 2 ? 16 * scale : 128 * scale;
        if (step >= 12) {
            float ox = (float) ((-centerX * scale + vw / 2.0) % step), oz = (float) ((-centerZ * scale + vh / 2.0) % step);
            for (float gx = ox; gx < vw; gx += step) g.fill(Math.round(gx), 0, Math.round(gx) + 1, vh, 0x0CFFFFFF);
            for (float gz = oz; gz < vh; gz += step) g.fill(0, Math.round(gz), vw, Math.round(gz) + 1, 0x0CFFFFFF);
        }
        String blocked = m == null ? "nicht verfügbar" : m.blocked();
        if (data != null && blocked == null) {
            int lod = scale >= 1 ? 0 : scale >= 0.25f ? 1 : 2;
            int span = TILE << (2 * lod);
            int tx0 = (int) Math.floor(blockX(0) / span), tx1 = (int) Math.floor(blockX(vw) / span);
            int tz0 = (int) Math.floor(blockZ(0) / span), tz1 = (int) Math.floor(blockZ(vh) / span);
            // von der Mitte nach außen bauen
            int ctx = (int) Math.floor(centerX / span), ctz = (int) Math.floor(centerZ / span);
            int rad = Math.max(Math.max(ctx - tx0, tx1 - ctx), Math.max(ctz - tz0, tz1 - ctz));
            for (int r = 0; r <= rad; r++) {
                for (int tz = ctz - r; tz <= ctz + r; tz++) {
                    for (int tx = ctx - r; tx <= ctx + r; tx++) {
                        if (Math.max(Math.abs(tx - ctx), Math.abs(tz - ctz)) != r) continue;
                        if (tx < tx0 || tx > tx1 || tz < tz0 || tz > tz1) continue;
                        Tile t = tile(data, lod, tx, tz);
                        if (t == null) continue;
                        int x0 = Math.round(screenX((double) tx * span)), x1 = Math.round(screenX((double) (tx + 1) * span));
                        int y0 = Math.round(screenY((double) tz * span)), y1 = Math.round(screenY((double) (tz + 1) * span));
                        Tex.drawTinted(g, t.tex.id, x0, y0, x1 - x0, y1 - y0, 0, 0, TILE, TILE, TILE, TILE, 0xFFFFFFFF);
                    }
                }
            }
            drawWaypoints(m);
            LocalPlayer p = minecraft.player;
            if (p != null) MapIcons.arrow(g, screenX(p.getX()), screenY(p.getZ()), 22 * d, p.getYRot() + 180f, 0xFFFFFFFF);
        } else {
            kit.textCenter("Weltkarte " + (blocked == null ? "nicht verfügbar" : blocked), vw / 2f, vh / 2f, 17, Tokens.SEMIBOLD, Tokens.MUTED);
        }
        drawChrome(m);
    }

    private void drawWaypoints(MinimapModule m) {
        for (final Waypoints.Waypoint w : Waypoints.all()) {
            if (!w.dim.equals(m.dimension())) continue;
            float sx = screenX(w.x + 0.5), sy = screenY(w.z + 0.5);
            if (sx < -40 || sy < -40 || sx > vw + 40 || sy > vh + 40) continue;
            float s = 22 * d;
            boolean hv = kit.hit("wpm:" + System.identityHashCode(w), sx - s / 2, sy - s / 2, s, s, () -> {
                WaypointsScreen ws = new WaypointsScreen();
                Compat.setScreen(ws);
                ws.open(w);
            });
            MapIcons.draw(kit.g, w.icon, sx, sy, hv ? s * 1.15f : s, w.visible ? w.color : Draw.alpha(w.color, 0.45f));
            if (hv || scale >= 2) {
                float tw = kit.width(w.name, 13, Tokens.SEMIBOLD) + 16 * d;
                Draw.rect(kit.g, sx - tw / 2, sy + s / 2 + 4 * d, tw, 22 * d, 8 * d, 0xD0100C18);
                kit.textCenter(w.name, sx, sy + s / 2 + 15 * d, 13, Tokens.SEMIBOLD, Tokens.TEXT);
            }
            if (hv) kit.tooltip(w.x + " / " + w.y + " / " + w.z + " · Klick: bearbeiten", sx, sy + s / 2 + 32 * d);
        }
    }

    private void drawChrome(MinimapModule m) {
        float p = 24 * d;
        // Titelkarte
        String dim = m == null || m.dimension() == null ? "" : MapWorld.dimensionLabel(m.dimension());
        String cursor = (int) Math.floor(blockX(kit.mx)) + " / " + (int) Math.floor(blockZ(kit.my));
        float cw = Math.max(260 * d, kit.width(cursor, 14, Tokens.MEDIUM) + 80 * d);
        Draw.shadow(kit.g, p, p + 10 * d, cw, 74 * d, 18 * d, 30 * d, -8 * d, 0x80000000);
        Draw.glass(kit.g, p, p, cw, 74 * d, 18 * d, 0xE6141020, Tokens.LINE_STRONG);
        kit.block(p, p, cw, 74 * d, null);
        Icons.draw(kit.g, "globe", p + 18 * d, p + 16 * d, 20 * d, Tokens.ACC);
        kit.textMid("Weltkarte · " + dim, p + 48 * d, p + 26 * d, 16, Tokens.SEMIBOLD, Tokens.TEXT, cw - 64 * d);
        kit.textMid("Maus: " + cursor, p + 48 * d, p + 52 * d, 13.5f, Tokens.MEDIUM, Tokens.MUTED, cw - 64 * d);
        if (m != null && m.dimension() != null && m.dimension().endsWith("the_nether")) {
            kit.textMid("Nur die Oberfläche (Nether-Decke) – keine Höhlenansicht", p, p + 74 * d + 18 * d, 12.5f, Tokens.MEDIUM, Tokens.SUBTLE, 0);
        }
        // Werkzeuge oben rechts
        float bs = 42 * d, x = vw - p - bs;
        kit.iconButton("close", x, p, bs, "x", true, this::onClose);
        x -= bs + 10 * d;
        kit.iconButton("zin", x, p, bs, "plus", true, () -> zoom(1.5f, vw / 2.0, vh / 2.0));
        x -= bs + 6 * d;
        kit.iconButton("zout", x, p, bs, "zoom", true, () -> zoom(1 / 1.5f, vw / 2.0, vh / 2.0));
        if (kit.hover("zout")) kit.tooltip("Herauszoomen", x + bs / 2, p + bs + 8 * d);
        x -= bs + 10 * d;
        boolean me = kit.iconButton("me", x, p, bs, "compass", true, () -> {
            LocalPlayer pl = minecraft.player;
            if (pl != null) {
                centerX = pl.getX();
                centerZ = pl.getZ();
            }
        });
        if (me) kit.tooltip("Zu mir", x + bs / 2, p + bs + 8 * d);
        float ww = kit.buttonWidth("Wegpunkte", "list", bs);
        x -= ww + 10 * d;
        kit.button("wps", x, p, ww, bs, "Wegpunkte", "list", Widgets.SECONDARY, () -> Compat.setScreen(new WaypointsScreen()));
        // Hinweis unten
        String hint = "Ziehen: verschieben · Mausrad: zoomen · Rechtsklick: Wegpunkt setzen";
        float hw = kit.width(hint, 13, Tokens.MEDIUM) + 32 * d;
        Draw.glass(kit.g, vw / 2f - hw / 2, vh - p - 36 * d, hw, 36 * d, 12 * d, 0xD0141020, Tokens.LINE);
        kit.textCenter(hint, vw / 2f, vh - p - 18 * d, 13, Tokens.MEDIUM, Tokens.MUTED);
        String sc = scale >= 1 ? Math.round(scale * 100) / 100f + " px/Block" : "1 px = " + Math.round(1 / scale) + " Blöcke";
        kit.textRight(sc, vw - p, vh - p - 18 * d, 12.5f, Tokens.MEDIUM, Tokens.SUBTLE);
    }

    private void zoom(float factor, double sx, double sy) {
        double bx = blockX(sx), bz = blockZ(sy);
        scale = Math.max(1 / 16f, Math.min(8f, scale * factor));
        savedScale = scale;
        centerX = bx - (sx - vw / 2.0) / scale;
        centerZ = bz - (sy - vh / 2.0) / scale;
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean click(Input.Click e, boolean doubleClick) {
        if (super.click(e, doubleClick)) return true;
        if (e.button() == 0) {
            panning = true;
            moved = false;
            panMouseX = e.x();
            panMouseY = e.y();
            panStartX = centerX;
            panStartZ = centerZ;
            return true;
        }
        if (e.button() == 1 && minecraft.player != null && MinimapModule.get() != null && MinimapModule.get().dimension() != null) {
            Waypoints.Waypoint w = new Waypoints.Waypoint();
            w.x = (int) Math.floor(blockX(e.x()));
            w.z = (int) Math.floor(blockZ(e.y()));
            w.y = minecraft.player.getBlockY();
            w.dim = MinimapModule.get().dimension();
            w.color = Waypoints.nextColor();
            w.name = "Wegpunkt " + (Waypoints.all().size() + 1);
            Waypoints.add(w);
            WaypointsScreen ws = new WaypointsScreen();
            Compat.setScreen(ws);
            ws.open(w);
            return true;
        }
        return false;
    }

    @Override
    protected boolean drag(Input.Click e, double dx, double dy) {
        if (panning) {
            moved = true;
            centerX = panStartX - (e.x() - panMouseX) / scale;
            centerZ = panStartZ - (e.y() - panMouseY) / scale;
            return true;
        }
        return super.drag(e, dx, dy);
    }

    @Override
    protected boolean release(Input.Click e) {
        if (panning) {
            panning = false;
            return true;
        }
        return super.release(e);
    }

    @Override
    protected boolean scroll(double mouseX, double mouseY, double amount) {
        if (amount != 0) zoom(amount > 0 ? 1.25f : 0.8f, mouseX, mouseY);
        return true;
    }

    @Override
    public void onClose() {
        Compat.setScreen(null);
    }
}
