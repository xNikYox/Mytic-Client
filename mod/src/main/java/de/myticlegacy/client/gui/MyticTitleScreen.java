package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.account.AccountScreen;
import de.myticlegacy.client.account.Accounts;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Tex;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.ui.Bake;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Motion;
import de.myticlegacy.client.gui.ui.TitleBackground;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Type;
import de.myticlegacy.client.gui.ui.Widgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Titelbildschirm im neuen Mytic-Design (klassisch zentriert, wie die UI-Vorschau): große Wortmarke „Mytic“ mit
 * Emblem, darunter „CLIENT“ in Pixelschrift und ein pulsierender Splash; mittig die Buttons Einzelspieler
 * (Akzent), Mehrspieler, „Mods &amp; Mytic-Menü“ und die Reihe „Optionen …“ | „Beenden“ mit den Icon-Quadraten
 * Kosmetik (links) und Freunde (rechts). In den Ecken: Freunde online (aufklappbar), Konto, Neuigkeiten, Version,
 * Copyright. Hintergrund nach Einstellung: Mytic (animiert), Minecraft-Panorama, einfarbig dunkel.
 * Gezeichnet wird in echten Bildschirm-Pixeln; alle Maße aus dem 1920×1080-Entwurf × Faktor d.
 */
public class MyticTitleScreen extends MyticScreen {
    /** Neuigkeiten der installierten Version (Pille unten, wechselt alle 6 Sekunden) */
    private static final String[][] NEWS = {
            {"", "Fadenkreuz-Editor: eigene Formen, Pixelbild, Teilen per Code"},
            {"Funktion", "Chat+: Tabs, Suche, Hervorheben – Strg + Klick kopiert"},
            {"Funktion", "Leistungsprofile: Einstellungen → Leistung, ein Klick"},
            {"Tipp", "Oben rechts auf deinen Namen: Konto wechseln oder neu einloggen"},
    };
    private static final String[] SPLASHES = {
            "Jetzt mit Mytic Styles!", "Pixel für Pixel!", "Glatte Rundungen!", "Lila ist das neue Grün!",
            "Mit Freunden spielen!", "Frisch gestrichen!", "Mytic-Lila!",
    };
    private static final Widgets.Look TITLE_LOOK = new Widgets.Look(0x8C0E0B16, 0xB0201A32, Tokens.LINE, Tokens.LINE_STRONG);

    private final long openedAt = System.currentTimeMillis();
    private final String splash = SPLASHES[(int) (Math.random() * SPLASHES.length)];
    private final String version = modVersion();
    private boolean friendsOpen;
    private int news;
    private long newsAt = System.currentTimeMillis();
    /** Design-Faktor und Lage des 1920×1080-Rahmens (in Pixeln) */
    private float d, oy;

    /** Klickbare Bereiche des letzten Bildes (oben liegende zuletzt) */
    private static final class Hit {
        final String id;
        final float x, y, w, h;
        final Runnable action;

        Hit(String id, float x, float y, float w, float h, Runnable action) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private String pressed;
    private int mouseX, mouseY;

    public MyticTitleScreen() {
        super(Component.literal(MyticClient.NAME));
    }

    private static String modVersion() {
        String v = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MyticClient.ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(MyticClient.VERSION);
        int plus = v.indexOf('+');
        return plus > 0 ? v.substring(0, plus) : v;
    }

    /** In echten Pixeln zeichnen (1 Einheit = 1 Pixel) */
    @Override
    protected int pixelScale() {
        return 1;
    }

    @Override
    protected void layout() {
    }

    private boolean animate() {
        return MyticClient.theme == null || MyticClient.theme.menuAnimations.get();
    }

    private String background() {
        return MyticClient.theme == null ? "Mytic (animiert)" : MyticClient.theme.titleBackground.get();
    }

    // ------------------------------------------------------------------------------------------ Hintergrund

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        String bg = background();
        if (bg.equals("Minecraft-Panorama")) {
            renderPanorama(g, delta);
            float k = Draw.begin(g);
            TitleBackground.panoramaShade(g, Math.round(width * k), Math.round(height * k));
            Draw.end(g);
            return;
        }
        if (bg.equals("Launcher-Design") && ThemeScene.draw(g, width, height, mouseX, mouseY)) {
            g.fillGradient(0, 0, width, height, 0x00000000, 0x70000000);
            return;
        }
        float k = Draw.begin(g);
        if (bg.equals("Einfarbig dunkel")) TitleBackground.solid(g, Math.round(width * k), Math.round(height * k));
        else TitleBackground.draw(g, Math.round(width * k), Math.round(height * k), mouseX * k, mouseY * k, animate());
        Draw.end(g);
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    private float X(float designX) {
        return vw / 2f + (designX - 960) * d;
    }

    private float Y(float designY) {
        return oy + designY * d;
    }

    private boolean over(float x, float y, float w, float h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    private void hit(String id, float x, float y, float w, float h, Runnable action) {
        hits.add(new Hit(id, x, y, w, h, action));
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        d = Math.min(vw / 1920f, vh / 1080f);
        oy = (vh - 1080 * d) / 2f;
        boolean anim = animate();
        float in = Motion.ease(Math.min(1f, (System.currentTimeMillis() - openedAt) / 450f));
        float rise = (1 - in) * 8 * d;

        drawLogo(g, in, rise, anim);
        drawStack(g, rise);
        drawAccount(g);
        drawNews(g, anim);
        // Fußzeile
        float fs = 13 * d, fy = vh - 16 * d - Type.capHeight(g, fs) - 4 * d;
        Type.draw(g, "Mytic " + version + " · Minecraft " + Compat.versionName(), 20 * d, fy, fs, Tokens.MEDIUM, 0x8CFFFFFF);
        Type.right(g, "Copyright Mojang AB. Nicht verbreiten!", vw - 20 * d, fy, fs, Tokens.MEDIUM, 0x8CFFFFFF);
        drawFriends(g); // zuletzt: die aufgeklappte Karte liegt über allem
    }

    private void drawLogo(GuiGraphics g, float in, float rise, boolean anim) {
        float rowTop = Y(196) + rise;
        int px = Math.max(12, Math.round(124 * d));
        Bake.Baked word = Bake.text("Mytic", px, -0.045f, 0xFFFFFFFF,
                new Bake.Shadow(0, 8 * d, 40 * d, 0x739B5CFF), new Bake.Shadow(0, 2 * d, 0, 0x40000000));
        int es = Math.max(8, Math.round(96 * d));
        Bake.Baked emblem = Bake.emblem(es);
        float wordW = word == null ? 0 : word.extra;
        float total = es + 26 * d + wordW;
        float x0 = vw / 2f - total / 2f;
        int tint = Draw.alpha(0xFFFFFFFF, in);
        float k = Draw.begin(g);
        Draw.pxBaked(g, emblem, Math.round(x0), Math.round(rowTop + 62 * d - es / 2f), tint);
        // Großbuchstaben mittig in der 124er-Zeile (Inter: Oberkante bei 0,137 em)
        if (word != null) Draw.pxBaked(g, word, Math.round(x0 + es + 26 * d), Math.round(rowTop + 0.137f * px), tint);
        Draw.end(g);

        // „CLIENT“ in Pixelschrift, weit gesperrt
        int s = Math.max(1, Math.round(d * 1.6f));
        int track = Math.round(15 * d * 0.6f);
        float cw = Type.pixelWidth(g, "CLIENT", s, track);
        Type.pixel(g, "CLIENT", vw / 2f - cw / 2f, rowTop + 124 * d + 14 * d + 4 * d, s, track, Draw.alpha(0xB3FFFFFF, in));

        // Splash: schräg rechts über der Wortmarke, pulsiert
        float sp = Math.max(9, 22 * d);
        Bake.Baked text = Bake.text(splash, Math.round(sp), 0, 0xFFFFD2F0,
                new Bake.Shadow(0, 0, 18 * d, 0xB3EC5FBF), new Bake.Shadow(0, 2 * d, 0, 0x993C0A32));
        if (text != null) {
            float t = anim ? ((System.currentTimeMillis() - openedAt) % 1400) / 1400f : 0;
            float pulse = 1 + 0.07f * Motion.easeInOut(t < 0.5f ? t * 2 : 2 - t * 2);
            float cx = X(960 + 190) + text.extra / 2f, cy = Y(112) + rise + 16 * d;
            Gfx.push(g);
            Gfx.translate(g, cx, cy);
            Gfx.rotate(g, -14);
            Gfx.scale(g, pulse, pulse);
            Tex.drawTinted(g, text.tex, Math.round(-text.extra / 2f - text.ox), Math.round(-8 * d - text.oy), text.w, text.h, 0, 0, text.w, text.h, text.w, text.h, tint);
            Gfx.pop(g);
        }
    }

    private void drawStack(GuiGraphics g, float rise) {
        float x0 = X(690), w = 540 * d, bh = 60 * d, r = 16 * d, fs = 18 * d;
        String[] labels = {"Einzelspieler", "Mehrspieler", "Mods & Mytic-Menü"};
        String[] icons = {"cube", "servers", "grid"};
        Runnable[] actions = {
                () -> Compat.setScreen(new SelectWorldScreen(this)),
                () -> Compat.setScreen(new JoinMultiplayerScreen(this)),
                () -> Compat.setScreen(new ModMenuScreen(this)),
        };
        for (int i = 0; i < 3; i++) {
            float y = Y(484 + i * 72) + rise;
            String id = "title:b" + i;
            boolean hover = over(x0, y, w, bh);
            Widgets.button(g, id, x0, y, w, bh, d, labels[i], icons[i], i == 0 ? Widgets.PRIMARY : Widgets.SECONDARY, TITLE_LOOK, fs, r, hover, id.equals(pressed) && hover);
            hit(id, x0, y, w, bh, actions[i]);
        }
        float ry = Y(712) + rise, half = (w - 12 * d) / 2f;
        boolean hoverO = over(x0, ry, half, bh), hoverQ = over(x0 + half + 12 * d, ry, half, bh);
        Widgets.button(g, "title:opt", x0, ry, half, bh, d, "Optionen …", null, Widgets.SECONDARY, TITLE_LOOK, fs, r, hoverO, "title:opt".equals(pressed) && hoverO);
        hit("title:opt", x0, ry, half, bh, () -> Compat.setScreen(Compat.optionsScreen(this)));
        Widgets.button(g, "title:quit", x0 + half + 12 * d, ry, half, bh, d, "Beenden", null, Widgets.DANGER, TITLE_LOOK, fs, r, hoverQ, "title:quit".equals(pressed) && hoverQ);
        hit("title:quit", x0 + half + 12 * d, ry, half, bh, () -> minecraft.stop());
        // Icon-Quadrate links (Kosmetik) und rechts (Freunde)
        float lx = x0 - 72 * d, rx = x0 + w + 12 * d;
        boolean hl = over(lx, ry, bh, bh), hr = over(rx, ry, bh, bh);
        Widgets.iconButton(g, "title:cos", lx, ry, bh, d, "sparkle", 22 * d, true, TITLE_LOOK, hl, "title:cos".equals(pressed) && hl);
        hit("title:cos", lx, ry, bh, bh, () -> Compat.setScreen(new CosmeticsScreen()));
        Widgets.iconButton(g, "title:fr", rx, ry, bh, d, "users", 22 * d, true, TITLE_LOOK, hr, "title:fr".equals(pressed) && hr);
        hit("title:fr", rx, ry, bh, bh, () -> Compat.setScreen(new FriendsScreen()));
        if (Friends.available()) { // Zahl der Freunde online am Freunde-Quadrat
            int online = Friends.onlineCount();
            if (online > 0) {
                String n = String.valueOf(online);
                float bs = 12.5f * d, bw = Math.max(20 * d, Type.width(g, n, bs, Tokens.SEMIBOLD) + 12 * d), bhh = 20 * d;
                float bx = rx + bh - bw + 6 * d, by = ry - 6 * d;
                Draw.accentRect(g, bx, by, bw, bhh, bhh / 2, 1f);
                Type.middle(g, n, bx + (bw - Type.width(g, n, bs, Tokens.SEMIBOLD)) / 2f, by + bhh / 2f, bs, Tokens.SEMIBOLD, 0xFFFFFFFF);
            }
        }
        if (hl) tooltip(g, "Kosmetik", lx + bh / 2f, ry + bh + 8 * d);
        if (hr) tooltip(g, "Freunde", rx + bh / 2f, ry + bh + 8 * d);
    }

    private void tooltip(GuiGraphics g, String text, float cx, float y) {
        float fs = 13 * d, w = Type.width(g, text, fs, Tokens.MEDIUM) + 20 * d, h = 28 * d;
        Draw.glass(g, cx - w / 2f, y, w, h, 8 * d, Tokens.GLASS_STRONG, Tokens.LINE_STRONG);
        Type.middle(g, text, cx - w / 2f + 10 * d, y + h / 2f, fs, Tokens.MEDIUM, Tokens.TEXT);
    }

    // ------------------------------------------------------------------------------------------ Ecken

    private List<Friends.Friend> online() {
        List<Friends.Friend> out = new ArrayList<>();
        for (Friends.Friend f : Friends.friends()) if (f.online) out.add(f);
        return out;
    }

    private void drawFriends(GuiGraphics g) {
        if (!Friends.available()) return;
        List<Friends.Friend> on = online();
        float x = 32 * d, y = 28 * d, h = 44 * d, fs = 14.5f * d, head = 28 * d;
        int shown = Math.min(3, on.size());
        float headsW = shown == 0 ? 0 : head + (shown - 1) * (head - 9 * d);
        String count = String.valueOf(on.size()), rest = on.size() == 1 ? " Freund online" : " Freunde online";
        float cw = Type.width(g, count, fs, Tokens.SEMIBOLD), rw = Type.width(g, rest, fs, Tokens.MEDIUM);
        float w = (shown == 0 ? 14 * d : 8 * d + headsW + 10 * d) + cw + rw + 10 * d + 18 * d + 14 * d;
        boolean hover = over(x, y, w, h);
        float hv = Motion.to("title:frpill", hover || friendsOpen, Tokens.MS_HOVER);
        Draw.glass(g, x, y, w, h, h / 2, Draw.mix(Tokens.GLASS, 0xB31E182E, hv), Tokens.LINE);
        float hx = x + 8 * d;
        for (int i = shown - 1; i >= 0; i--) { // rechts beginnen, damit der erste Kopf oben liegt
            float fx = hx + i * (head - 9 * d);
            Draw.circle(g, fx + head / 2f, y + h / 2f, head / 2f, 0xFF1A1428);
            Draw.head(g, on.get(i).head, on.get(i).name, fx + 2 * d, y + h / 2f - head / 2f + 2 * d, head - 4 * d, (head - 4 * d) / 2f);
        }
        float tx = shown == 0 ? x + 14 * d : hx + headsW + 10 * d, cy = y + h / 2f;
        Type.middle(g, count, tx, cy, fs, Tokens.SEMIBOLD, Tokens.TEXT);
        Type.middle(g, rest, tx + cw, cy, fs, Tokens.MEDIUM, Draw.mix(Tokens.MUTED, Tokens.TEXT, hv));
        float open = Motion.to("title:fropen", friendsOpen, 250);
        float ix = tx + cw + rw + 10 * d, is = 18 * d;
        Gfx.push(g);
        Gfx.translate(g, ix + is / 2f, cy);
        Gfx.rotate(g, 180 * open);
        Icons.draw(g, "chevD", -is / 2f, -is / 2f, is, Draw.mix(Tokens.MUTED, Tokens.TEXT, hv));
        Gfx.pop(g);
        hit("title:frpill", x, y, w, h, () -> friendsOpen = !friendsOpen);
        if (open < 0.02f) return;

        // aufgeklappte Karte: bis zu 4 Freunde online, darunter „Alle Freunde“
        float cx = x, cyTop = y + 54 * d - (1 - open) * 4 * d, cwid = 340 * d, row = 46 * d;
        int rows = Math.min(4, on.size());
        float ch = 14 * d + Math.max(1, rows) * row + 6 * d + 38 * d + 14 * d;
        Draw.shadow(g, cx, cyTop + 20 * d, cwid, ch, 20 * d, 50 * d, -10 * d, Draw.alpha(0xB3000000, open));
        Draw.glass(g, cx, cyTop, cwid, ch, Tokens.RAD_LG * d, Draw.alpha(Tokens.GLASS_STRONG, open), Draw.alpha(Tokens.LINE, open));
        // Klick neben die Karte schließt sie; Klicks auf die Karte selbst tun nichts
        hits.add(0, new Hit("title:frclose", 0, 0, vw, vh, () -> friendsOpen = false));
        hit("title:frcard", cx, cyTop, cwid, ch, () -> { });
        float ry = cyTop + 14 * d;
        if (rows == 0) {
            Type.middle(g, "Gerade ist niemand online.", cx + 18 * d, ry + row / 2f, 14 * d, Tokens.MEDIUM, Draw.alpha(Tokens.MUTED, open));
        }
        for (int i = 0; i < rows; i++) {
            Friends.Friend f = on.get(i);
            float rx = cx + 10 * d, rwid = cwid - 20 * d, yy = ry + i * row;
            boolean rh = over(rx, yy, rwid, row);
            float rhv = Motion.to("title:frrow" + i, rh, Tokens.MS_HOVER);
            if (rhv > 0.01f) Draw.rect(g, rx, yy, rwid, row, 12 * d, Draw.alpha(Tokens.SURFACE, rhv * open));
            float hs = 30 * d, hy = yy + (row - hs) / 2f;
            Draw.head(g, f.head, f.name, rx + 8 * d, hy, hs, 7 * d);
            Draw.circle(g, rx + 8 * d + hs - 1 * d, hy + hs - 1 * d, 6 * d, 0xFF141020);
            Draw.circle(g, rx + 8 * d + hs - 1 * d, hy + hs - 1 * d, 3.5f * d, f.inGame() ? Tokens.ACC_1 : Tokens.OK);
            float mx = rx + 8 * d + hs + 12 * d, mw = rwid - (mx - rx) - 8 * d;
            Type.draw(g, Type.ellipsize(g, f.name, mw, 15 * d, Tokens.SEMIBOLD), mx, yy + 9 * d, 15 * d, Tokens.SEMIBOLD, Draw.alpha(Tokens.TEXT, open));
            Type.draw(g, Type.ellipsize(g, f.status(), mw, 13 * d, Tokens.MEDIUM), mx, yy + 27 * d, 13 * d, Tokens.MEDIUM, Draw.alpha(Tokens.MUTED, open));
            hit("title:frrow" + i, rx, yy, rwid, row, () -> Compat.setScreen(new FriendsScreen()));
        }
        float by = ry + Math.max(1, rows) * row + 6 * d, bx = cx + 18 * d, bw = cwid - 36 * d, bh = 38 * d;
        boolean bhov = over(bx, by, bw, bh);
        Widgets.button(g, "title:frall", bx, by, bw, bh, d, "Alle Freunde", Widgets.TRAILING + "chevR", Widgets.GHOST, null, 14.5f * d, Tokens.RAD_SM * d, bhov, "title:frall".equals(pressed) && bhov);
        hit("title:frall", bx, by, bw, bh, () -> Compat.setScreen(new FriendsScreen()));
    }

    private void drawAccount(GuiGraphics g) {
        String name = minecraft.getUser().getName();
        boolean switchable = Accounts.available();
        float h = 44 * d, hs = 30 * d, fs = 14.5f * d;
        float nw = Type.width(g, name, fs, Tokens.SEMIBOLD);
        float w = 7 * d + hs + 10 * d + nw + (switchable ? 10 * d + 30 * d + 6 * d : 14 * d);
        float x = vw - 32 * d - w, y = 28 * d;
        boolean hover = switchable && over(x, y, w, h);
        float hv = Motion.to("title:acc", hover, Tokens.MS_HOVER);
        Draw.glass(g, x, y, w, h, h / 2, Draw.mix(Tokens.GLASS, 0xB31E182E, hv), Tokens.LINE);
        float hy = y + (h - hs) / 2f;
        Friends.Friend me = Friends.me();
        Draw.head(g, me == null ? null : me.head, name, x + 7 * d, hy, hs, hs / 2f);
        Draw.circle(g, x + 7 * d + hs - 2 * d, hy + hs - 2 * d, 6 * d, 0xFF141020);
        Draw.circle(g, x + 7 * d + hs - 2 * d, hy + hs - 2 * d, 3.5f * d, Tokens.OK);
        Type.middle(g, name, x + 7 * d + hs + 10 * d, y + h / 2f, fs, Tokens.SEMIBOLD, Tokens.TEXT);
        if (switchable) {
            float bx = x + w - 6 * d - 30 * d, by = y + (h - 30 * d) / 2f;
            if (hv > 0.01f) Draw.rect(g, bx, by, 30 * d, 30 * d, 9 * d, Draw.alpha(Tokens.SURFACE_HOVER, hv));
            Icons.draw(g, "swap", bx + 6 * d, by + 6 * d, 18 * d, Draw.mix(Tokens.MUTED, Tokens.TEXT, hv));
            hit("title:acc", x, y, w, h, () -> Compat.setScreen(new AccountScreen(this)));
        }
    }

    private void drawNews(GuiGraphics g, boolean anim) {
        if (NEWS.length == 0) return;
        long now = System.currentTimeMillis();
        if (anim && now - newsAt > 6000) {
            news = (news + 1) % NEWS.length;
            newsAt = now;
        }
        String tag = NEWS[news][0].isEmpty() ? "Neu in " + shortVersion() : NEWS[news][0];
        float h = 44 * d, fs = 14.5f * d, bh = 28 * d;
        float tagW = Type.width(g, tag, 12.5f * d, Tokens.SEMIBOLD) + 18 * d;
        String text = Type.ellipsize(g, NEWS[news][1], 640 * d - tagW - 60 * d, fs, Tokens.MEDIUM);
        float tw = Type.width(g, text, fs, Tokens.MEDIUM);
        float w = 8 * d + tagW + 10 * d + tw + 10 * d + 18 * d + 14 * d;
        float x = vw / 2f - w / 2f, y = vh - 64 * d - h;
        boolean hover = over(x, y, w, h);
        float hv = Motion.to("title:news", hover, Tokens.MS_HOVER);
        float fade = anim ? Motion.ease(Math.min(1f, (now - newsAt) / 400f)) : 1f;
        Draw.glass(g, x, y, w, h, h / 2, Draw.mix(Tokens.GLASS, 0xB31E182E, hv), Tokens.LINE);
        Widgets.badge(g, x + 8 * d, y + (h - bh) / 2f, bh, d, tag, true);
        Type.middle(g, text, x + 8 * d + tagW + 10 * d, y + h / 2f, fs, Tokens.MEDIUM, Draw.alpha(Tokens.TEXT, fade));
        Icons.draw(g, "chevR", x + w - 14 * d - 18 * d, y + h / 2f - 9 * d, 18 * d, Draw.mix(Tokens.MUTED, Tokens.TEXT, hv));
        hit("title:news", x, y, w, h, () -> {
            news = (news + 1) % NEWS.length;
            newsAt = System.currentTimeMillis();
        });
    }

    /** „2.54“ aus „2.54.0“ */
    private String shortVersion() {
        String[] p = version.split("\\.");
        return p.length >= 2 ? p[0] + "." + p[1] : version;
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    private Hit top(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(mx, my)) return hits.get(i);
        return null;
    }

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        if (event.button() != 0) return false;
        Hit h = top(event.x(), event.y());
        pressed = h == null ? null : h.id;
        return h != null;
    }

    @Override
    protected boolean release(Input.Click event) {
        String p = pressed;
        pressed = null;
        if (p == null) return false;
        Hit h = top(event.x(), event.y());
        if (h != null && h.id.equals(p)) h.action.run();
        return true;
    }

    @Override
    public void removed() {
        Motion.forget("title:");
        TitleBackground.release(); // GPU-Speicher frei; die fertigen Ebenen (wenige KB) bleiben für das nächste Mal
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
