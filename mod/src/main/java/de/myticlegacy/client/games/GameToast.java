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
        boolean lunar = Ui.lunar();
        int r = Ui.square() ? 0 : 6;
        int panel = lunar ? Ui.L_PANEL | 0xFF000000 : Ui.PANEL | 0xFF000000;
        Ui.rect(g, x + 2, y + 2, W, H, r, 0x55000000);
        Ui.rect(g, x, y, W, H, r, panel);
        Ui.outline(g, x, y, W, H, r, lunar ? Ui.L_LINE : Ui.LINE, panel);
        g.fill(x, y + (Ui.square() ? 0 : 4), x + 2, y + H - (Ui.square() ? 0 : 4), Ui.accent() | 0xFF000000);
        // Kopf und Text
        var connection = mc.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(game.inviterName);
        if (info != null) Compat.drawFace(g, info, x + 8, y + 7, 16);
        else FriendsScreen.drawHead(g, null, game.inviterName, x + 8, y + 7, 16);
        int text = lunar ? 0xFFFFFFFF : Ui.TEXT, muted = lunar ? 0xFF9A9A9A : Ui.MUTED;
        Ui.text(g, game.inviterName, x + 29, y + 6, text, false);
        Ui.scaled(g, "fordert dich heraus: " + game.gameName(), x + 29, y + 16, 0.75f, muted, false);
        Ui.scaled(g, game.settings() + " · " + Games.colorText(game, true), x + 8, y + 27, 0.7f, muted, false);
        // Knöpfe mit Taste
        int hi = ChessScreen.highlight();
        int bw = (W - 8 * 2 - 4) / 2, by = y + 36;
        String a = "[" + keyName(acceptKey) + "] Annehmen", d = "[" + keyName(declineKey) + "] Ablehnen";
        Ui.rect(g, x + 8, by, bw, 12, Ui.square() ? 0 : 3, hi);
        Ui.centered(g, a, x + 8 + bw / 2, by + 2, 0xFFFFFFFF, false);
        Ui.rect(g, x + 12 + bw, by, bw, 12, Ui.square() ? 0 : 3, ChessScreen.card(false));
        Ui.centered(g, d, x + 12 + bw + bw / 2, by + 2, 0xFFFF9AA0, false);
        // Ablaufbalken (1 Minute)
        float left = Math.max(0f, 1f - age / 60_000f);
        g.fill(x + 2, y + H - 2, x + 2 + Math.round((W - 4) * left), y + H - 1, left < 0.2f ? Ui.RED : hi);
    }
}
