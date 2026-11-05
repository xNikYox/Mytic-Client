package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.account.AccountScreen;
import de.myticlegacy.client.account.Accounts;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.ServerPing;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.render.MenuPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Hauptmenü „Showcase“: links der eigene Spieler groß als 3D-Modell mit allen Cosmetics (mit der Maus drehbar, ohne
 * Namensschild), in der Mitte Logo, große Knöpfe und runde Symbol-Knöpfe, rechts Freunde, Schnell-Beitreten (echte
 * Server aus der Mehrspieler-Liste mit Ping) und Neuigkeiten. Dahinter Launcher-Design bzw. Panorama mit Effekten.
 */
public class MyticTitleScreen extends MyticScreen {
    private static final int BH = 22, ORB = 22, ORB_GAP = 6, RIGHT_W = 150;
    /** Neuigkeiten der installierten Version (Karte rechts unten, wechselt alle 5 Sekunden) */
    private static final String[][] NEWS = {
            {"Minecraft-Menüs im Mytic-Design", "Einzelspieler, Mehrspieler und Optionen im Stil deines Mod-Menüs."},
            {"Mytic Styles", "Texturpakete mischen: Schwert aus dem einen, GUI aus dem anderen Paket – Taste B."},
            {"Konto wechseln", "Unter deinem Spieler auf den Namen klicken – alle Konten aus dem Launcher."},
    };
    private static final float DEFAULT_YAW = -22f;

    private final long openedAt = System.currentTimeMillis();
    private final ServerPing ping = new ServerPing();
    private final List<ServerData> servers = new ArrayList<>();
    /** Anzahl aller Server in der Mehrspieler-Liste (angezeigt werden nur die ersten 3) */
    private int serverCount;
    private float yaw = DEFAULT_YAW, yawTarget = DEFAULT_YAW;
    private boolean dragging;

    private record Orb(String label, String icon, Runnable action) {
    }

    /** Eigenes Mytic-Icon (Linien-Stil, 64×64, weich skaliert); dark = dunkle Fassung für helle Flächen (Gold) */
    private static void icon(GuiGraphics g, String name, int x, int y, int size, boolean dark) {
        de.myticlegacy.client.compat.Blit.region(g, net.minecraft.resources.Identifier.fromNamespaceAndPath("myticclient", "textures/gui/icons/" + name + (dark ? "_dark" : "_white") + ".png"),
                x, y, size, size, 0, 0, 64, 64, 64, 64);
    }

    public MyticTitleScreen() {
        super(Component.literal(MyticClient.NAME));
        try {
            ServerList list = new ServerList(net.minecraft.client.Minecraft.getInstance());
            list.load();
            serverCount = list.size();
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                ServerData d = list.get(i);
                servers.add(d);
                ping.ping(d);
            }
        } catch (RuntimeException e) {
            MyticClient.LOG.warn("Server-Liste fürs Hauptmenü nicht lesbar", e);
        }
    }

    /** Eine Stufe feiner als die Minecraft-GUI-Größe – aber nie so groß, dass das Layout nicht mehr passt */
    @Override
    protected int pixelScale() {
        return Math.max(1, Math.min(Compat.guiScale() - 1, fit(470, 270, 0.98, 0.98)));
    }

    @Override
    protected void layout() {
    }

    /** Echte Server-Bilder (aus der Server-Liste bzw. vom Ping): Bild-Daten → Textur, neu wenn sich das Bild ändert */
    private final Object[] icons = new Object[3];
    private final byte[][] iconData = new byte[3][];

    private Object icon(int i, ServerData d) {
        byte[] png = d.getIconBytes();
        if (png == null) return null;
        if (png != iconData[i]) {
            if (icons[i] != null) de.myticlegacy.client.compat.Tex.release(icons[i]);
            icons[i] = null;
            iconData[i] = png;
            try {
                icons[i] = de.myticlegacy.client.compat.Tex.upload("server_" + i + "_" + Integer.toHexString(java.util.Arrays.hashCode(png)), png);
            } catch (RuntimeException e) {
                icons[i] = null; // kaputtes Bild: Buchstaben zeigen
            }
        }
        return icons[i];
    }

    @Override
    public void removed() {
        ping.close();
        for (Object t : icons) if (t != null) de.myticlegacy.client.compat.Tex.release(t);
        super.removed();
    }

    @Override
    public void tick() {
        ping.tick();
    }

    // ------------------------------------------------------------------------------------------ Bereiche

    private int[] modelBox() {
        int w = Math.round(vw * 0.32f), h = Math.round(vh * 0.74f);
        return new int[]{Math.round(vw * 0.04f), Math.round(vh * 0.07f), w, h};
    }

    private int[] nameplate() {
        int[] m = modelBox();
        int w = Math.max(96, font.width(minecraft.getUser().getName()) + 40);
        return new int[]{m[0] + m[2] / 2 - w / 2, m[1] + m[3] + 2, w, 22};
    }

    private int colX() {
        return Math.round(vw * 0.385f);
    }

    private int colW() {
        return Math.min(200, vw - colX() - RIGHT_W - 28);
    }

    private int menuY() {
        return Math.round(vh * 0.43f);
    }

    private int[] button(int i) {
        return new int[]{colX(), menuY() + i * (BH + 6), colW(), BH};
    }

    private List<Orb> orbs() {
        List<Orb> out = new ArrayList<>();
        out.add(new Orb("Mods & HUD", "mods", () -> Compat.setScreen(new ModMenuScreen(this))));
        out.add(new Orb("Cosmetics", "cosmetics", () -> Compat.setScreen(new CosmeticsScreen())));
        out.add(new Orb("Freunde", "friends", () -> Compat.setScreen(new FriendsScreen())));
        out.add(new Orb("Screenshots", "screenshots", () -> Compat.setScreen(new ScreenshotsScreen())));
        out.add(new Orb("Statistiken", "stats", () -> Compat.setScreen(new de.myticlegacy.client.stats.StatsScreen())));
        out.add(new Orb("Optionen", "options", () -> Compat.setScreen(Compat.optionsScreen(this))));
        out.add(new Orb("Beenden", "quit", () -> minecraft.stop()));
        return out;
    }

    private int[] orb(int i) {
        return new int[]{colX() + i * (ORB + ORB_GAP), menuY() + 2 * (BH + 6) + 6, ORB, ORB};
    }

    private int rightX() {
        return vw - RIGHT_W - 12;
    }

    private int[] serverRow(int i) {
        return new int[]{rightX() + 4, 64 + 14 + i * 26, RIGHT_W - 8, 24};
    }

    // ------------------------------------------------------------------------------------------ Hintergrund

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (MyticClient.theme.titleBackground.is("Launcher-Design") && ThemeScene.draw(g, width, height, mouseX, mouseY)) {
            g.fillGradient(0, 0, width, height, 0x00000000, 0x70000000);
        } else {
            renderPanorama(g, delta);
            g.fillGradient(0, 0, width, height, 0x30000000, 0x90000000);
        }
    }

    /** Sterne, schwebende Würfel und Leuchten in der Stil-Farbe (leicht, wenige Flächen pro Bild) */
    private void effects(GuiGraphics g, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        float px = (mouseX / (float) Math.max(1, vw) - 0.5f), py = (mouseY / (float) Math.max(1, vh) - 0.5f);
        int accent = Ui.accent(), accent2 = Ui.lunar() ? Ui.selection() : Ui.accent2();
        // Sterne (feste Positionen aus einem Zufallsmuster, funkeln)
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 70; i++) {
            int x = r.nextInt(Math.max(1, vw)), y = r.nextInt(Math.max(1, vh * 6 / 10));
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(now / 700.0 + i * 1.7));
            int a = Math.round(tw * (90 + r.nextInt(120)));
            g.fill(x - Math.round(px * 6), y - Math.round(py * 4), x - Math.round(px * 6) + 1, y - Math.round(py * 4) + 1, Ui.withAlpha(0xFFFFFF, a));
        }
        // Würfel: Rahmen in der zweiten Stil-Farbe, schweben auf und ab
        int[][] cubes = {{6, 66}, {93, 52}, {44, 10}, {82, 16}, {30, 86}};
        for (int i = 0; i < cubes.length; i++) {
            int size = 12 + (i % 3) * 3;
            int x = vw * cubes[i][0] / 100 - Math.round(px * 16), y = vh * cubes[i][1] / 100 - Math.round(py * 10) + Math.round((float) Math.sin(now / 1400.0 + i) * 6);
            Ui.glow(g, x, y, size, size, Ui.square() ? 0 : 2, accent, 3, 0.35f);
            Ui.outline(g, x, y, size, size, Ui.square() ? 0 : 2, Ui.withAlpha(accent2, 0xB0), Ui.withAlpha(accent, 0x22));
        }
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 500f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        boolean lunar = Ui.lunar();
        int accent = Ui.accent(), accent2 = lunar ? Ui.selection() : Ui.accent2();
        int text = lunar ? Ui.lText() : Ui.TEXT, muted = lunar ? Ui.lMuted() : Ui.MUTED;
        int card = lunar ? Ui.L_CARD : Ui.SURFACE, cardHover = lunar ? Ui.L_CARD_HOVER : Ui.SURFACE_HOVER, line = lunar ? Ui.L_LINE : Ui.LINE;
        int r = Ui.square() ? 0 : Ui.gold() ? 2 : 6;
        effects(g, mouseX, mouseY);

        // ---------------- Spieler: Podest, Modell, Namensschild
        // Maus auf dem anderen Bildschirm losgelassen oder Fenster nicht mehr aktiv: Drehen beenden
        if (dragging && (!minecraft.mouseHandler.isLeftPressed() || !minecraft.isWindowActive())) dragging = false;
        if (!dragging) yawTarget = DEFAULT_YAW;
        yaw += (yawTarget - yaw) * (dragging ? 0.6f : 0.08f);
        int[] m = modelBox();
        int podW = m[2] * 7 / 10, podY = m[1] + m[3] - 10;
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 600.0);
        for (int i = 0; i < 8; i++) { // weiche Ellipse aus Streifen
            int w = Math.round(podW * (1 - i / 9f));
            Ui.rect(g, m[0] + m[2] / 2 - w / 2, podY - 4 + i, w, 2, 1, Ui.withAlpha(accent, Math.round((18 + i * 8) * pulse)));
        }
        if (MenuPlayer.supported()) {
            int x0 = Math.round(m[0] * scale), y0 = Math.round(m[1] * scale), x1 = Math.round((m[0] + m[2]) * scale), y1 = Math.round((m[1] + m[3]) * scale);
            float size = (y1 - y0) / 2.15f;
            float bob = (float) Math.sin(System.currentTimeMillis() / 900.0) * 1.5f;
            MenuPlayer.draw(g, x0, y0, x1, y1 + Math.round(bob), size, yaw, 6f);
        } else {
            FriendsScreen.drawHead(g, Friends.me() == null ? null : Friends.me().head, minecraft.getUser().getName(), m[0] + m[2] / 2 - 32, m[1] + m[3] / 2 - 40, 64);
        }
        int[] np = nameplate();
        boolean switchable = Accounts.available();
        float nh = Ui.animate("title:name", switchable && Ui.inside(mouseX, mouseY, np[0], np[1], np[2], np[3]), 16f);
        Ui.outline(g, np[0], np[1], np[2], np[3], r, Ui.mix(line, accent2, nh), Ui.alpha(Ui.mix(card, cardHover, nh), Math.max(0.85f, ease)));
        Ui.rect(g, np[0] + 8, np[1] + 9, 5, 5, 3, Ui.GREEN);
        Ui.text(g, minecraft.getUser().getName(), np[0] + 18, np[1] + 4, text, false);
        Ui.scaled(g, switchable ? "Konto wechseln ▾" : "Microsoft · online", np[0] + 18, np[1] + 14, 0.65f, muted, false);
        if (!dragging && Ui.inside(mouseX, mouseY, m[0], m[1], m[2], m[3])) Ui.scaled(g, "Ziehen zum Drehen", m[0] + m[2] / 2f - font.width("Ziehen zum Drehen") * 0.325f, m[1] + 2, 0.65f, Ui.withAlpha(0xFFFFFF, 0x90), false);

        // ---------------- Logo und Begrüßung
        int lx = colX();
        float ly = vh * 0.12f + (1 - ease) * 8;
        float big = Math.min(5f, colW() / (float) font.width("MYTIC") * 0.95f);
        Gfx.push(g);
        Gfx.translate(g, lx, ly);
        Gfx.scale(g, big, big);
        g.drawString(font, "MYTIC", 1, 1, Ui.withAlpha(accent, 0x70), false);
        g.drawString(font, "MYTIC", 0, 0, Ui.alpha(0xFFFFFFFF, ease), false);
        Gfx.pop(g);
        String client = "C L I E N T";
        Ui.scaled(g, client, lx + 2, ly + 9 * big + 3, 1.4f, Ui.alpha(accent2 | 0xFF000000, ease), false);
        Ui.scaled(g, "Minecraft " + Compat.versionName() + " · Hallo " + minecraft.getUser().getName(), lx + 2, ly + 9 * big + 18, 0.75f, muted, true);

        // ---------------- Große Knöpfe
        String[] labels = {"Einzelspieler", "Mehrspieler"};
        String[] icons = {"singleplayer", "multiplayer"};
        String[] subs = {"", serverCount == 0 ? "" : serverCount + (serverCount == 1 ? " Server" : " Server")};
        for (int i = 0; i < 2; i++) {
            int[] b = button(i);
            boolean hover = Ui.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
            float h = Ui.animate("title:btn" + i, hover, 16f);
            int lift = Math.round(h);
            if (i == 0) {
                Ui.glow(g, b[0], b[1] - lift, b[2], b[3], r, accent, 4, 0.35f + h * 0.4f);
                Ui.rect(g, b[0], b[1] - lift, b[2], b[3], r, Ui.mix(accent | 0xFF000000, 0xFFFFFFFF, h * 0.12f));
                if (!lunar || Ui.glass()) Ui.hGradient(g, b[0] + r, b[1] - lift, b[2] - 2 * r, b[3], Ui.withAlpha(0xFFFFFF, 0), Ui.withAlpha(accent2, 0x50));
            } else {
                Ui.outline(g, b[0], b[1] - lift, b[2], b[3], r, Ui.mix(line, accent2, h), Ui.mix(card, cardHover, h));
            }
            int fg = i == 0 && Ui.gold() ? 0xFF1A1608 : 0xFFFFFFFF;
            icon(g, icons[i], b[0] + 6, b[1] - lift + 3, 16, i == 0 && Ui.gold());
            Ui.text(g, labels[i].toUpperCase(java.util.Locale.ROOT), b[0] + 28, b[1] - lift + 7, fg, i != 0 || !Ui.gold());
            if (!subs[i].isEmpty()) Ui.scaled(g, subs[i], b[0] + b[2] - 8 - font.width(subs[i]) * 0.7f, b[1] - lift + 8, 0.7f, i == 0 ? Ui.withAlpha(fg, 0xC0) : muted, false);
        }

        // ---------------- Runde Symbol-Knöpfe
        List<Orb> orbs = orbs();
        int online = (int) Friends.friends().stream().filter(f -> f.online).count();
        String tip = null;
        int tipX = 0, tipY = 0;
        for (int i = 0; i < orbs.size(); i++) {
            int[] o = orb(i);
            boolean hover = Ui.inside(mouseX, mouseY, o[0], o[1], o[2], o[3]);
            float h = Ui.animate("title:orb" + i, hover, 18f);
            int lift = Math.round(h * 2);
            if (h > 0.02f) Ui.glow(g, o[0], o[1] - lift, ORB, ORB, ORB / 2, accent, 3, h * 0.7f);
            int rr = Ui.square() ? 0 : ORB / 2;
            Ui.outline(g, o[0], o[1] - lift, ORB, ORB, rr, Ui.mix(line, i == orbs.size() - 1 ? 0xFFFF6B7D : accent2, h), Ui.mix(card, cardHover, h));
            icon(g, orbs.get(i).icon(), o[0] + 4, o[1] - lift + 4, ORB - 8, false);
            if (i == 2 && online > 0) { // Freunde online
                String n = String.valueOf(online);
                int bw = Math.max(9, font.width(n) + 4);
                Ui.rect(g, o[0] + ORB - bw + 3, o[1] - lift - 3, bw, 9, Ui.square() ? 0 : 4, accent | 0xFF000000);
                Ui.scaled(g, n, o[0] + ORB - bw + 3 + bw / 2f - font.width(n) * 0.35f, o[1] - lift - 1, 0.7f, 0xFFFFFFFF, false);
            }
            if (hover) {
                tip = orbs.get(i).label();
                tipX = o[0] + ORB / 2;
                tipY = o[1] + ORB + 5;
            }
        }
        if (tip != null) {
            int tw = font.width(tip) + 8;
            Ui.rect(g, tipX - tw / 2, tipY, tw, 12, 2, 0xD0000000);
            Ui.centered(g, tip, tipX, tipY + 2, 0xFFFFFFFF, false);
        }

        // ---------------- Rechte Spalte: Freunde, Schnell beitreten, Neuigkeiten
        int rx = rightX();
        int y = 12;
        if (Friends.available()) {
            Ui.outline(g, rx, y, RIGHT_W, 30, r, line, Ui.alpha(card, 0.9f));
            List<Friends.Friend> on = Friends.friends().stream().filter(f -> f.online).limit(3).toList();
            int hx = rx + 6;
            for (Friends.Friend f : on) {
                FriendsScreen.drawHead(g, f.head, f.name, hx, y + 7, 16);
                hx += 12;
            }
            int tx = on.isEmpty() ? rx + 8 : hx + 8;
            Ui.text(g, online + (online == 1 ? " Freund online" : " Freunde online"), tx, y + 6, text, false);
            String status = on.isEmpty() ? "Gerade niemand da" : on.get(0).name + " · " + on.get(0).status();
            Ui.scaled(g, font.plainSubstrByWidth(status, Math.round((RIGHT_W - (tx - rx) - 6) / 0.65f)), tx, y + 18, 0.65f, muted, false);
            y += 38;
        }
        int boxH = 16 + Math.max(1, servers.size()) * 26 + 4;
        int sy = y;
        Ui.outline(g, rx, sy, RIGHT_W, boxH, r, line, Ui.alpha(card, 0.9f));
        Ui.scaled(g, "SCHNELL BEITRETEN", rx + 8, sy + 6, 0.65f, muted, false);
        if (servers.isEmpty()) Ui.scaled(g, "Noch keine Server in der Liste", rx + 8, sy + 22, 0.75f, muted, false);
        for (int i = 0; i < servers.size(); i++) {
            ServerData d = servers.get(i);
            int[] row = {rx + 4, sy + 16 + i * 26, RIGHT_W - 8, 24};
            boolean hover = Ui.inside(mouseX, mouseY, row[0], row[1], row[2], row[3]);
            if (hover) Ui.rect(g, row[0], row[1], row[2], row[3], Math.min(r, 4), 0x18FFFFFF);
            String initials = d.name.isEmpty() ? "?" : d.name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
            initials = initials.substring(0, Math.min(2, Math.max(1, initials.length())));
            Object icon = icon(i, d);
            if (icon != null) {
                Ui.rect(g, row[0] + 2, row[1] + 2, 20, 20, Math.min(r, 3), 0x40000000);
                de.myticlegacy.client.compat.Tex.draw(g, icon, row[0] + 3, row[1] + 3, 18, 18, 64, 64);
            } else {
                Ui.rect(g, row[0] + 3, row[1] + 3, 18, 18, Math.min(r, 4), accent | 0xFF000000);
                Ui.centered(g, initials, row[0] + 12, row[1] + 8, Ui.gold() ? 0xFF1A1608 : 0xFFFFFFFF, false);
            }
            Ui.text(g, font.plainSubstrByWidth(d.name, RIGHT_W - 80), row[0] + 26, row[1] + 3, text, false);
            String players = d.players == null ? (d.ping == -2 ? "nicht erreichbar" : "wird abgefragt …") : d.players.online() + " online";
            Ui.scaled(g, players, row[0] + 26, row[1] + 14, 0.65f, muted, false);
            if (d.ping >= 0 && d.players != null) {
                int bars = d.ping < 80 ? 4 : d.ping < 150 ? 3 : d.ping < 300 ? 2 : 1;
                int color = bars >= 3 ? 0xFF5BE38A : bars == 2 ? 0xFFFFD84A : 0xFFFF6B7D;
                for (int b = 0; b < 4; b++) g.fill(row[0] + row[2] - 40 + b * 3, row[1] + 12 - b * 2, row[0] + row[2] - 38 + b * 3, row[1] + 14, b < bars ? color : 0x40FFFFFF);
                String ms = d.ping + " ms";
                Ui.scaled(g, ms, row[0] + row[2] - 4 - font.width(ms) * 0.65f, row[1] + 9, 0.65f, color, false);
            }
        }
        // Neuigkeiten unten rechts in der Ecke (wechseln alle 5 Sekunden); nur wenn sie nichts überdecken
        int slide = (int) ((System.currentTimeMillis() / 5000) % NEWS.length);
        int newsH = 66;
        y = vh - 14 - newsH;
        int[] lastOrb = orb(orbs.size() - 1);
        boolean free = y > sy + boxH + 6 && (lastOrb[0] + ORB + 8 < rx || y > lastOrb[1] + ORB + 18);
        if (free) {
            Ui.outline(g, rx, y, RIGHT_W, newsH, r, line, Ui.alpha(card, 0.9f));
            Ui.rect(g, rx + 1, y + 1, RIGHT_W - 2, 16, Ui.square() ? 0 : Math.max(0, r - 1), Ui.withAlpha(accent, 0x60));
            Ui.scaled(g, "NEU · " + MyticClient.VERSION, rx + 8, y + 6, 0.7f, 0xFFFFFFFF, false);
            Ui.text(g, font.plainSubstrByWidth(NEWS[slide][0], RIGHT_W - 14), rx + 8, y + 22, text, false);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(NEWS[slide][1]), Math.round((RIGHT_W - 16) / 0.7f));
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                Gfx.push(g);
                Gfx.translate(g, rx + 8, y + 34 + i * 8);
                Gfx.scale(g, 0.7f, 0.7f);
                g.drawString(font, lines.get(i), 0, 0, muted, false);
                Gfx.pop(g);
            }
            int segW = (RIGHT_W - 16 - 2 * 4) / NEWS.length;
            for (int i = 0; i < NEWS.length; i++) Ui.rect(g, rx + 8 + i * (segW + 4), y + newsH - 7, segW, 2, 1, i == slide ? accent | 0xFF000000 : 0x30FFFFFF);
        }

        // ---------------- Fußzeile
        String version = MyticClient.NAME + " " + MyticClient.VERSION + " · Minecraft " + Compat.versionName();
        Ui.scaled(g, version, 6, vh - 8, 0.55f, Ui.withAlpha(0xFFFFFF, 0x70), false);
        String copyright = "Copyright Mojang AB. Nicht verbreiten!";
        Ui.scaled(g, copyright, vw - 6 - font.width(copyright) * 0.55f, vh - 8, 0.55f, Ui.withAlpha(0xFFFFFF, 0x70), false);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int[] np = nameplate();
        if (Accounts.available() && Ui.inside(mx, my, np[0], np[1], np[2], np[3])) {
            Compat.setScreen(new AccountScreen(this));
            return true;
        }
        int[] m = modelBox();
        if (Ui.inside(mx, my, m[0], m[1], m[2], m[3])) {
            dragging = true;
            return true;
        }
        for (int i = 0; i < 2; i++) {
            int[] b = button(i);
            if (!Ui.inside(mx, my, b[0], b[1], b[2], b[3])) continue;
            Compat.setScreen(i == 0 ? new SelectWorldScreen(this) : new JoinMultiplayerScreen(this));
            return true;
        }
        List<Orb> orbs = orbs();
        for (int i = 0; i < orbs.size(); i++) {
            int[] o = orb(i);
            if (Ui.inside(mx, my, o[0], o[1], o[2], o[3])) {
                orbs.get(i).action().run();
                return true;
            }
        }
        int sy = Friends.available() ? 50 : 12;
        for (int i = 0; i < servers.size(); i++) {
            if (!Ui.inside(mx, my, rightX() + 4, sy + 16 + i * 26, RIGHT_W - 8, 24)) continue;
            ServerData d = servers.get(i);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(d.ip), d, false, null);
            return true;
        }
        return false;
    }

    @Override
    protected boolean drag(Input.Click event, double dx, double dy) {
        if (!dragging) return false;
        if (Math.abs(dx) > 40) return true; // Sprung (z. B. Maus kam vom anderen Bildschirm zurück) nicht als Drehung werten
        yawTarget -= (float) dx * 1.6f; // nach rechts ziehen = Modell dreht sich nach rechts
        return true;
    }

    @Override
    protected boolean release(Input.Click event) {
        if (!dragging) return false;
        dragging = false;
        // zur Ausgangslage über den kürzeren Weg zurück
        yaw = ((yaw - DEFAULT_YAW) % 360f + 540f) % 360f - 180f + DEFAULT_YAW;
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
