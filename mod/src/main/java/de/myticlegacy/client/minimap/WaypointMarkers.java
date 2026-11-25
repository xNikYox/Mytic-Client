package de.myticlegacy.client.minimap;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.MapCompat;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Type;
import de.myticlegacy.client.render.QuadSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/**
 * Wegpunkte in der Welt: Symbol, Name und Entfernung über dem Punkt (auf den Bildschirm projiziert, auch hinter
 * Wänden – wie ein Kompass, nicht wie ein Radar: nur eigene Wegpunkte) und eine Lichtsäule bei nahen Punkten.
 */
final class WaypointMarkers {
    private WaypointMarkers() {
    }

    static void labels(MinimapModule m, GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || m.dimension() == null || Compat.screen(mc) != null && !(Compat.screen(mc) instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        Vec3 cam = MapCompat.cameraPos();
        double yaw = Math.toRadians(MapCompat.cameraYaw()), pitch = Math.toRadians(MapCompat.cameraPitch());
        // Blickrichtung, rechts, oben (Minecraft: Gierwinkel 0 = Süden)
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = 0 * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - 0 * fx;
        int W = g.guiWidth(), H = g.guiHeight();
        double focal = (H / 2.0) / Math.tan(Math.toRadians(MapCompat.fov()) / 2);
        double range = m.markerRange.get();
        for (Waypoints.Waypoint w : Waypoints.all()) {
            if (!w.visible || !w.dim.equals(m.dimension())) continue;
            double dx = w.x + 0.5 - cam.x, dy = w.y + 1.2 - cam.y, dz = w.z + 0.5 - cam.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > range || dist < 2.5) continue;
            double cz = dx * fx + dy * fy + dz * fz;
            if (cz < 0.1) continue;
            double cx = dx * rx + dz * rz, cy = dx * ux + dy * uy + dz * uz;
            float sx = (float) (W / 2.0 + cx / cz * focal), sy = (float) (H / 2.0 - cy / cz * focal);
            if (sx < -40 || sx > W + 40 || sy < -40 || sy > H + 40) continue;
            // nahe der Bildmitte deutlicher (man schaut hin), sonst dezent
            double off = Math.hypot(sx - W / 2.0, sy - H / 2.0) / (H / 2.0);
            float focus = (float) Math.max(0, 1 - off * 2.2);
            float alpha = 0.55f + 0.45f * focus;
            String dText = dist >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", dist / 1000) : Math.round(dist) + " m";
            String label = focus > 0.25f ? w.name + "  " + dText : dText;
            float fs = 6.5f;
            float tw = Type.width(g, label, fs, Tokens.SEMIBOLD);
            float bw = tw + 8, bh = 10, by = sy - 18;
            Draw.rect(g, sx - bw / 2, by, bw, bh, 5, Draw.alpha(0xC0100C18, alpha));
            Draw.rect(g, sx - bw / 2, by + bh - 1.2f, bw, 1.2f, 0.6f, Draw.alpha(w.color, alpha));
            Type.middle(g, label, sx - tw / 2, by + bh / 2, fs, Tokens.SEMIBOLD, Draw.alpha(0xFFFFFFFF, alpha));
            MapIcons.draw(g, w.icon, sx, sy - 3, 9, Draw.alpha(w.color, alpha), Draw.alpha(0xD0000000, alpha));
        }
    }

    /** Lichtsäulen (nur wenn nah genug, damit sie im Sichtbereich liegen) */
    static void beams(MinimapModule m, QuadSink sink) {
        Minecraft mc = Minecraft.getInstance();
        if (!m.enabled() || !m.beams.get() || mc.player == null || m.dimension() == null || m.blocked() != null) return;
        double max = Math.min(m.markerRange.get(), mc.options.getEffectiveRenderDistance() * 16.0);
        for (Waypoints.Waypoint w : Waypoints.all()) {
            if (!w.visible || !w.dim.equals(m.dimension())) continue;
            double dx = w.x + 0.5 - mc.player.getX(), dz = w.z + 0.5 - mc.player.getZ();
            double dist = Math.hypot(dx, dz);
            if (dist > max || dist < 3) continue;
            double x0 = w.x + 0.35, x1 = w.x + 0.65, z0 = w.z + 0.35, z1 = w.z + 0.65;
            double y0 = w.y, y1 = w.y + 96;
            int c = Draw.alpha(w.color, 0.32f);
            side(sink, x0, z0, x1, z0, y0, y1, c);
            side(sink, x1, z0, x1, z1, y0, y1, c);
            side(sink, x1, z1, x0, z1, y0, y1, c);
            side(sink, x0, z1, x0, z0, y0, y1, c);
        }
    }

    /** senkrechte Fläche, von beiden Seiten sichtbar */
    private static void side(QuadSink sink, double ax, double az, double bx, double bz, double y0, double y1, int c) {
        sink.quad(ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az, c);
        sink.quad(ax, y1, az, bx, y1, bz, bx, y0, bz, ax, y0, az, c);
    }
}
