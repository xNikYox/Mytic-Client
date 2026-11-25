package de.myticlegacy.client.minimap;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.PixelTex;
import de.myticlegacy.client.compat.Tex;
import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.module.Msg;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import de.myticlegacy.client.setting.TextSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Minimap mit Wegpunkten: Oberfläche der geladenen Chunks (keine Höhlen, Erze oder Wesen – kein Radar),
 * quadratisch oder rund, mitdrehend oder genordet, Zoom, Koordinaten und Biom. Die Kartentextur wird nur bei
 * Bewegung oder neuen Daten neu berechnet (höchstens alle 50 ms), nicht in jedem Bild. Server können die Karte
 * über die Sperrliste oder den Xaero-Code „§n§o§m§i§n§i§m§a§p“ abschalten.
 */
public class MinimapModule extends HudModule {
    private static final String NO_MINIMAP = "§n§o§m§i§n§i§m§a§p";
    private static final float[] ZOOMS = {0.25f, 0.5f, 1f, 2f, 4f};
    private static final int TEX = 192;
    private static MinimapModule instance;

    public final ModeSetting shape, orientation;
    public final SliderSetting mapSize, zoom, markerRange, deathKeep;
    public final BoolSetting coords, biome, cardinals, mapWaypoints, markers, beams, deaths, serverCodes;
    public final TextSetting disabledServers;

    private final MapScanner scanner = new MapScanner();
    private MapData data;
    private String worldKey, serverKey, dim;
    private boolean blockedByServer;
    private String blockedReason;
    private long lastTickNanos, lastSave;
    private boolean wasDead;
    private KeyMapping worldMapKey, waypointKey, waypointsKey, zoomKey;
    /** zuletzt im Chat gesehener Wegpunkt-Code (zum Übernehmen im Wegpunkte-Fenster) */
    public static String lastChatCode;

    // Kartentextur der Minimap
    private PixelTex tex;
    private int builtCx = Integer.MIN_VALUE, builtCz, builtN, builtNt, builtB, builtChanges = -1;
    private String builtStyle = "";
    private long builtAt;

    public MinimapModule() {
        super("minimap", "Minimap", "Karte der Umgebung mit Wegpunkten, Todespunkt und Weltkarte (ohne Höhlen- oder Spieler-Radar)",
                () -> Items.FILLED_MAP, false, 10000, 4);
        instance = this;
        shape = new ModeSetting(this, "shape", "Form", "Quadratisch", "Quadratisch", "Rund");
        mapSize = new SliderSetting(this, "size", "Kartengröße", 64, 192, 4, 108, " px");
        zoom = new SliderSetting(this, "zoom", "Zoom", 0.25, 4, 0.25, 1, "×");
        orientation = new ModeSetting(this, "orientation", "Ausrichtung", "Mitdrehen", "Mitdrehen", "Norden oben");
        coords = new BoolSetting(this, "coords", "Koordinaten unter der Karte", true);
        biome = new BoolSetting(this, "biome", "Biom unter der Karte", true);
        cardinals = new BoolSetting(this, "cardinals", "Himmelsrichtungen am Rand", true);
        mapWaypoints = new BoolSetting(this, "mapWaypoints", "Wegpunkte auf der Minimap", true);
        markers = new BoolSetting(this, "markers", "Wegpunkte in der Welt (Name, Entfernung)", true);
        beams = new BoolSetting(this, "beams", "Lichtsäulen bei nahen Wegpunkten", true);
        markerRange = new SliderSetting(this, "markerRange", "Sichtweite der Markierungen", 100, 10000, 100, 10000, " m");
        deaths = new BoolSetting(this, "deaths", "Todespunkt automatisch setzen", true);
        deathKeep = new SliderSetting(this, "deathKeep", "Todespunkte behalten", 1, 10, 1, 3, "");
        disabledServers = new TextSetting(this, "disabled", "Aus auf diesen Servern", "", () -> "Adressen, mit Komma getrennt");
        serverCodes = new BoolSetting(this, "serverCodes", "Sperr-Code des Servers beachten (Xaero)", true);
        new ActionSetting(this, "openWaypoints", "Wegpunkte (Taste U)", () -> "Öffnen", () -> openLater(WaypointsScreen::new));
        new ActionSetting(this, "openWorldMap", "Weltkarte (Taste M)", () -> "Öffnen", () -> openLater(WorldMapScreen::new));
        new ActionSetting(this, "clearMap", "Erkundete Karte dieser Welt", () -> "Löschen", () -> {
            if (data != null) data.clear();
            builtChanges = -1;
            Msg.actionBar("Karte dieser Welt gelöscht");
        });
    }

    public static MinimapModule get() {
        return instance;
    }

    private static void openLater(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            Msg.actionBar("Nur in einer Welt verfügbar");
            return;
        }
        mc.execute(() -> Compat.setScreen(screen.get()));
    }

    @Override
    protected boolean defaultBackground() {
        return true;
    }

    // ------------------------------------------------------------------------------------------ Anmelden, Tick

    public void register() {
        worldMapKey = Compat.registerKey("key.myticclient.minimap_worldmap", InputConstants.KEY_M);
        waypointKey = Compat.registerKey("key.myticclient.minimap_waypoint", InputConstants.KEY_N);
        waypointsKey = Compat.registerKey("key.myticclient.minimap_waypoints", InputConstants.KEY_U);
        zoomKey = Compat.registerKey("key.myticclient.minimap_zoom", InputConstants.UNKNOWN.getValue());
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> onMessage(message));
        de.myticlegacy.client.render.WorldRender.register(sink -> WaypointMarkers.beams(this, sink));
    }

    private void onMessage(Component message) {
        if (message == null) return;
        String s = message.getString();
        if (serverCodes.get() && s.contains(NO_MINIMAP)) {
            blockedByServer = true;
            blockedReason = "vom Server abgeschaltet";
        }
        java.util.regex.Matcher m = Waypoints.CODE.matcher(s);
        if (m.find()) {
            lastChatCode = m.group();
            if (enabled()) Msg.actionBar("Wegpunkt-Code im Chat – Taste U → „Aus Chat übernehmen“");
        }
    }

    /** Karte auf diesem Server gesperrt (Sperrliste oder Server-Code)? Grund oder null */
    public String blocked() {
        if (blockedByServer) return blockedReason;
        String addr = MapWorld.address(Minecraft.getInstance());
        if (!addr.isEmpty()) {
            for (String part : disabledServers.get().split(",")) {
                String p = part.trim().toLowerCase(Locale.ROOT);
                if (!p.isEmpty() && addr.contains(p)) return "auf diesem Server abgeschaltet";
            }
        }
        return null;
    }

    public MapData data() {
        return data;
    }

    public String dimension() {
        return dim;
    }

    public void tick(Minecraft mc) {
        lastTickNanos = System.nanoTime();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            leave();
            return;
        }
        String server = MapWorld.server(mc), d = MapWorld.dimension(level);
        String key = server + "/" + d;
        if (!key.equals(worldKey)) {
            if (data != null) data.save();
            if (!java.util.Objects.equals(server, serverKey)) {
                blockedByServer = false;
                var sd = mc.getCurrentServer();
                if (sd != null && sd.motd != null && serverCodes.get() && sd.motd.getString().contains(NO_MINIMAP)) {
                    blockedByServer = true;
                    blockedReason = "vom Server abgeschaltet";
                }
            }
            worldKey = key;
            serverKey = server;
            dim = d;
            data = new MapData(key, MapWorld.root().resolve(server).resolve(MapWorld.dimensionFolder(d)));
            scanner.reset();
            builtChanges = -1;
            Waypoints.use(server);
        }
        // Tasten
        while (worldMapKey.consumeClick()) if (enabled()) open(new WorldMapScreen());
        while (waypointsKey.consumeClick()) if (enabled()) open(new WaypointsScreen());
        while (waypointKey.consumeClick()) if (enabled()) open(WaypointsScreen.createHere());
        while (zoomKey.consumeClick()) if (enabled()) cycleZoom();
        // Todespunkt
        boolean dead = player.isDeadOrDying();
        if (dead && !wasDead && enabled() && deaths.get()) {
            Waypoints.death(player.getBlockX(), player.getBlockY(), player.getBlockZ(), d, deathKeep.intValue());
        }
        wasDead = dead;
        if (!enabled() || blocked() != null) return;
        // beim Betreten der Welt nicht scannen: der Spiel-Thread baut gerade die ersten Chunks fertig
        if (de.myticlegacy.client.compat.LevelLoading.active(mc)) return;
        scanner.tick(level, data, player.getBlockX() >> 4, player.getBlockZ() >> 4, mc.options.getEffectiveRenderDistance());
        long now = System.currentTimeMillis();
        if (now - lastSave > 60_000) {
            lastSave = now;
            data.save();
        }
    }

    private void open(net.minecraft.client.gui.screens.Screen screen) {
        if (blocked() != null) {
            Msg.actionBar("§cMinimap " + blocked());
            return;
        }
        if (Compat.screen(Minecraft.getInstance()) == null) Compat.setScreen(screen);
    }

    private void cycleZoom() {
        float z = zoom.floatValue();
        int i = 0;
        while (i < ZOOMS.length && ZOOMS[i] <= z + 0.01f) i++;
        zoom.set((double) ZOOMS[i % ZOOMS.length]);
        MyticClient.config().save();
        Msg.actionBar("Minimap-Zoom " + zoom.display());
    }

    private void leave() {
        if (data != null) data.save();
        data = null;
        worldKey = null;
        serverKey = null;
        blockedByServer = false;
        wasDead = false;
    }

    /** Anteil des laufenden Ticks (für weiche Bewegung ohne versionsabhängige API) */
    float partial() {
        return Math.max(0, Math.min(1, (System.nanoTime() - lastTickNanos) / 50_000_000f));
    }

    // ------------------------------------------------------------------------------------------ HUD

    private int infoLines() {
        return (coords.get() ? 1 : 0) + (biome.get() ? 1 : 0);
    }

    @Override
    public int baseWidth() {
        return mapSize.intValue();
    }

    @Override
    public int baseHeight() {
        return mapSize.intValue() + (infoLines() > 0 ? 4 + infoLines() * 10 : 0);
    }

    @Override
    public boolean hasContent() {
        return blocked() == null;
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        int S = mapSize.intValue();
        boolean round = shape.is("Rund");
        float r = round ? S / 2f : (rounded.get() ? 6 : 3);
        int bg = background.get() ? Draw.alpha(0xF0100C18, (float) (backgroundOpacity.get() / 100.0) + 0.25f) : 0x55100C18;
        Draw.rect(g, 0, 0, S, S, r, bg);
        LocalPlayer p = mc.player;
        if (p != null && mc.level != null && data != null) {
            drawMap(g, p, S, round);
        } else {
            // Vorschau ohne Welt: angedeutete Landschaft
            Draw.rect(g, S * 0.1f, S * 0.15f, S * 0.5f, S * 0.4f, S * 0.12f, 0x664ADE80);
            Draw.rect(g, S * 0.45f, S * 0.5f, S * 0.45f, S * 0.35f, S * 0.12f, 0x6660A5FA);
            MapIcons.arrow(g, S / 2f, S / 2f, 9, 0, 0xFFFFFFFF);
        }
        Draw.ring(g, 0, 0, S, S, r, 1, Tokens.LINE_STRONG);
        if (neon()) Draw.ring(g, -1, -1, S + 2, S + 2, r + 1, 1, Draw.alpha(Ui.accent(), 0.5f));
        // Infozeilen
        int ty = S + 4;
        var font = mc.font;
        if (coords.get()) {
            String c;
            if (p == null) c = "120 64 -35";
            else if (de.myticlegacy.client.module.StreamerModule.hidesCoords()) c = "??? ??? ???";
            else c = p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
            g.drawString(font, c, (S - font.width(c)) / 2, ty, color(), textShadow());
            ty += 10;
        }
        if (biome.get()) {
            String b = p == null || mc.level == null ? "Ebene" : biomeName(mc.level, p);
            g.drawString(font, b, (S - font.width(b)) / 2, ty, Draw.alpha(color(), 0.75f), textShadow());
        }
    }

    private static String biomeName(ClientLevel level, LocalPlayer p) {
        var key = level.getBiome(p.blockPosition()).unwrapKey();
        if (key.isEmpty()) return "";
        String path = key.get().identifier().getPath();
        String t = net.minecraft.client.resources.language.I18n.get("biome.minecraft." + path);
        return t.startsWith("biome.") ? path : t;
    }

    /** Ausrichtung: Drehung der Karte im Uhrzeigersinn (Grad) */
    float mapRotation(LocalPlayer p) {
        return orientation.is("Mitdrehen") ? 180f - p.getYRot() : 0f;
    }

    private void drawMap(GuiGraphics g, LocalPlayer p, int S, boolean round) {
        float pt = partial();
        double px = p.xo + (p.getX() - p.xo) * pt, pz = p.zo + (p.getZ() - p.zo) * pt;
        float z = zoom.floatValue();
        int visible = Math.max(16, Math.round(128 / z)); // Blöcke über die Kartenbreite
        int b = Math.max(1, visible / 128);              // Blöcke je Texel
        int n = visible / b;                             // Texel über die Breite
        boolean rotate = orientation.is("Mitdrehen");
        int nt = round || !rotate ? n + 2 : Math.min(TEX, (int) Math.ceil(n * 1.42f) + 2);
        int cx = Math.floorDiv((int) Math.floor(px), b) * b, cz = Math.floorDiv((int) Math.floor(pz), b) * b;
        rebuild(cx, cz, n, nt, b, round, rotate);
        float unit = (float) S / n;
        float rot = mapRotation(p);
        if (!round) Gfx.scissor(g, 0, 0, S, S);
        Gfx.push(g);
        Gfx.translate(g, S / 2f, S / 2f);
        if (rot != 0) Gfx.rotate(g, rot);
        Gfx.scale(g, unit, unit);
        Gfx.translate(g, -(nt / 2 + (float) (px - cx) / b), -(nt / 2 + (float) (pz - cz) / b));
        Tex.drawTinted(g, tex.id, 0, 0, nt, nt, 0, 0, nt, nt, TEX, TEX, 0xFFFFFFFF);
        Gfx.pop(g);
        // Wegpunkte, Himmelsrichtungen, Spieler
        float half = S / 2f;
        double rad = Math.toRadians(rot);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        if (mapWaypoints.get()) {
            for (Waypoints.Waypoint w : Waypoints.all()) {
                if (!w.visible || !w.dim.equals(dim)) continue;
                double dx = (w.x + 0.5 - px) / b * unit, dz = (w.z + 0.5 - pz) / b * unit;
                double sx = dx * cos - dz * sin, sy = dx * sin + dz * cos;
                double edge = half - 5;
                boolean outside;
                if (round) {
                    double len = Math.hypot(sx, sy);
                    outside = len > edge;
                    if (outside) {
                        sx = sx / len * edge;
                        sy = sy / len * edge;
                    }
                } else {
                    double m = Math.max(Math.abs(sx), Math.abs(sy));
                    outside = m > edge;
                    if (outside) {
                        sx = sx / m * edge;
                        sy = sy / m * edge;
                    }
                }
                MapIcons.draw(g, w.icon, (float) (half + sx), (float) (half + sy), outside ? 6 : 8, outside ? Draw.alpha(w.color, 0.75f) : w.color);
            }
        }
        if (!round) Gfx.noScissor(g);
        if (cardinals.get()) {
            String[] names = {"N", "O", "S", "W"};
            double[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
            var font = Minecraft.getInstance().font;
            for (int i = 0; i < 4; i++) {
                if (!rotate && i != 0) continue;
                double sx = dirs[i][0] * cos - dirs[i][1] * sin, sy = dirs[i][0] * sin + dirs[i][1] * cos;
                double e = half - 6;
                if (!round) {
                    double m = Math.max(Math.abs(sx), Math.abs(sy));
                    sx /= m;
                    sy /= m;
                }
                float lx = (float) (half + sx * e), ly = (float) (half + sy * e);
                Draw.circle(g, lx, ly, 4.5f, 0xB0100C18);
                int c = i == 0 ? 0xFFFF5C72 : 0xFFE6E2F0;
                Gfx.push(g);
                Gfx.translate(g, lx - font.width(names[i]) * 0.35f, ly - 2.8f);
                Gfx.scale(g, 0.7f, 0.7f);
                g.drawString(font, names[i], 0, 0, c, false);
                Gfx.pop(g);
            }
        }
        float arrowRot = rotate ? 0 : p.getYRot() + 180f;
        MapIcons.arrow(g, half, half, 9, arrowRot, 0xFFFFFFFF);
    }

    /** Kartentextur neu berechnen, wenn sich Mitte, Zoom, Form oder Daten geändert haben (nicht in jedem Bild) */
    private void rebuild(int cx, int cz, int n, int nt, int b, boolean round, boolean rotate) {
        if (tex == null) tex = new PixelTex("minimap", TEX, TEX);
        String style = round + ":" + rotate;
        long now = System.currentTimeMillis();
        boolean moved = cx != builtCx || cz != builtCz || n != builtN || nt != builtNt || b != builtB || !style.equals(builtStyle);
        boolean changed = data.changes() != builtChanges;
        if (!moved && !(changed && now - builtAt > 250)) return;
        if (moved && now - builtAt < 30 && builtCx != Integer.MIN_VALUE && n == builtN && style.equals(builtStyle)) return;
        builtCx = cx;
        builtCz = cz;
        builtN = n;
        builtNt = nt;
        builtB = b;
        builtStyle = style;
        builtChanges = data.changes();
        builtAt = now;
        int half = nt / 2;
        float radius = n / 2f;
        int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
        int[] chunk = null;
        for (int j = 0; j < nt; j++) {
            int wz = cz + (j - half) * b;
            for (int i = 0; i < nt; i++) {
                int wx = cx + (i - half) * b;
                int ccx = wx >> 4, ccz = wz >> 4;
                if (ccx != lastCx || ccz != lastCz) {
                    chunk = data.chunk(ccx, ccz);
                    lastCx = ccx;
                    lastCz = ccz;
                }
                int c = chunk == null ? 0 : chunk[(wz & 15) << 4 | (wx & 15)];
                if (round && c != 0) {
                    float dx = i - half + 0.5f, dz = j - half + 0.5f;
                    float dist = (float) Math.sqrt(dx * dx + dz * dz);
                    if (dist > radius) c = 0;
                    else if (dist > radius - 1) c = Draw.alpha(c, radius - dist);
                }
                tex.set(i, j, c);
            }
        }
        tex.upload();
    }

    // ------------------------------------------------------------------------------------------ Markierungen

    /** Wegpunkt-Markierungen über der Welt (im HUD, vor den Anzeigen) */
    public void drawMarkers(GuiGraphics g) {
        if (!enabled() || !markers.get() || blocked() != null) return;
        WaypointMarkers.labels(this, g);
    }
}
