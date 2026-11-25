package de.myticlegacy.client.games;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.FriendsScreen;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/**
 * Herausforderung oben rechts im Spiel: Karte im Stil des Mod-Menüs mit Kopf, Spiel und Einstellungen, Ablaufbalken
 * (1 Minute) und zwei Knöpfen mit Taste – [J] Annehmen, [K] Ablehnen (unter Steuerung änderbar). Beim Spielen ist die
 * Maus gefangen, daher Tasten statt Klicks.
 */
public final class GameToast {
    private static final int W = 184, H = 54;
    private static KeyMapping acceptKey, declineKey;
    /** wann eine Einladung zuerst gesehen wurde (für Ablaufbalken und Einblenden) */
    private static final Map<String, Long> firstSeen = new HashMap<>();

    private GameToast() {
    }

    public static void registerKeys() {
        acceptKey = Compat.registerKey("key.myticclient.gameaccept", com.mojang.blaze3d.platform.InputConstants.KEY_J);
        declineKey = Compat.registerKey("key.myticclient.gamedecline", com.mojang.blaze3d.platform.InputConstants.KEY_K);
    }

    /** Einladung, die gerade gezeigt wird (neueste an mich), sonst null */
    private static Games.Game current() {
        Games.Game g = Games.incomingInvite();
        if (g == null) return null;
        firstSeen.putIfAbsent(g.id, System.currentTimeMillis());
        return System.currentTimeMillis() - firstSeen.get(g.id) < 60_000 ? g : null;
    }

    /** Tasten J/K (jeden Tick; nur ohne offenes Fenster, damit Tippen im Chat nichts auslöst) */
    public static void tick(Minecraft mc) {
        if (acceptKey == null) return;
        boolean accept = false, decline = false;
        while (acceptKey.consumeClick()) accept = true;
        while (declineKey.consumeClick()) decline = true;
        if (Compat.screen(mc) != null) return;
        Games.Game g = current();
        if (g == null) return;
        if (accept) {
            firstSeen.put(g.id, 0L); // ausblenden
            Games.accept(g.id, game -> Compat.setScreen(Games.screenFor(game)), error -> Games.notice("§c" + error));
        } else if (decline) {
            firstSeen.put(g.id, 0L);
            Games.decline(g.id);
            Games.notice("§7Herausforderung von §d" + g.inviterName + "§7 abgelehnt.");
        }
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
    }

    /** im HUD zeichnen (oben rechts, gleitet herein) */
    public static void render(GuiGraphics g) {
        Games.Game game = current();
        if (game == null || acceptKey == null) return;
        Minecraft mc = Minecraft.getInstance();
        long age = System.currentTimeMillis() - firstSeen.get(game.id);
        float in = Math.min(1f, age / 250f), ease = 1f - (1f - in) * (1f - in) * (1f - in);
        int sw = mc.getWindow().getGuiScaledWidth();
        int x = sw - W - 6 + Math.round((1 - ease) * (W + 10)), y = 6;
        // Mytic-Design: dunkles Glas, Akzent-Streifen, Inter-Schrift (dv = GUI-Pixel je Design-Pixel)
        float dv = 0.4f;
        de.myticlegacy.client.gui.ui.Draw.shadow(g, x, y + 8, W, H, 14 * dv, 36 * dv, -6 * dv, 0x99000000);
        de.myticlegacy.client.gui.ui.Draw.glass(g, x, y, W, H, 14 * dv, 0xEB14101E, 0x2BFFFFFF);
        de.myticlegacy.client.gui.ui.Draw.accentRect(g, x + 3, y + 8, 2, H - 16, 1, 1f);
        // Kopf und Text
        var connection = mc.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(game.inviterName);
        if (info != null) Compat.drawFace(g, info, x + 9, y + 7, 16);
        else FriendsScreen.drawHead(g, null, game.inviterName, x + 9, y + 7, 16);
        int text = de.myticlegacy.client.gui.ui.Tokens.TEXT, muted = de.myticlegacy.client.gui.ui.Tokens.MUTED;
        de.myticlegacy.client.gui.ui.Type.draw(g, game.inviterName, x + 30, y + 7.5f, 15 * dv, de.myticlegacy.client.gui.ui.Tokens.SEMIBOLD, text);
        de.myticlegacy.client.gui.ui.Type.draw(g, de.myticlegacy.client.gui.ui.Type.ellipsize(g, "fordert dich heraus: " + game.gameName(), W - 38, 12.5f * dv, 0),
                x + 30, y + 16.5f, 12.5f * dv, de.myticlegacy.client.gui.ui.Tokens.MEDIUM, muted);
        de.myticlegacy.client.gui.ui.Type.draw(g, de.myticlegacy.client.gui.ui.Type.ellipsize(g, game.settings() + " · " + Games.colorText(game, true), W - 18, 12 * dv, 0),
                x + 9, y + 27, 12 * dv, de.myticlegacy.client.gui.ui.Tokens.MEDIUM, de.myticlegacy.client.gui.ui.Tokens.SUBTLE);
        // Knöpfe mit Taste
        int bw = (W - 9 * 2 - 4) / 2, by = y + 36;
        String a = "[" + keyName(acceptKey) + "] Annehmen", d = "[" + keyName(declineKey) + "] Ablehnen";
        float fs = 12.5f * dv;
        de.myticlegacy.client.gui.ui.Draw.accentRect(g, x + 9, by, bw, 12, 9 * dv, 1f);
        de.myticlegacy.client.gui.ui.Type.middle(g, a, x + 9 + bw / 2f - de.myticlegacy.client.gui.ui.Type.width(g, a, fs, 1) / 2f, by + 6, fs, 1, 0xFFFFFFFF);
        de.myticlegacy.client.gui.ui.Draw.glass(g, x + 13 + bw, by, bw, 12, 9 * dv, 0x14FFFFFF, 0x2BFFFFFF);
        de.myticlegacy.client.gui.ui.Type.middle(g, d, x + 13 + bw + bw / 2f - de.myticlegacy.client.gui.ui.Type.width(g, d, fs, 1) / 2f, by + 6, fs, 1, 0xFFFFD2D8);
        // Ablaufbalken (1 Minute)
        float left = Math.max(0f, 1f - age / 60_000f);
        if (left < 0.2f) de.myticlegacy.client.gui.ui.Draw.rect(g, x + 10, y + H - 2.5f, (W - 20) * left, 1.2f, 0.6f, de.myticlegacy.client.gui.ui.Tokens.BAD);
        else de.myticlegacy.client.gui.ui.Draw.accentRect(g, x + 10, y + H - 2.5f, Math.max(1.2f, (W - 20) * left), 1.2f, 0.6f, 1f);
    }
}
