package de.myticlegacy.client.module;

import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.nio.file.Path;

/**
 * Karte „Clip gespeichert – Ordner öffnen“ oben rechts (Mytic-Design wie die Herausforderungen). Die Maus ist im Spiel
 * gefangen, deshalb öffnet Umschalt + Clip-Taste den Ordner, solange die Karte zu sehen ist.
 */
public final class ClipToast {
    static final long SHOW_MS = 6000;
    private static final int W = 184, H = 40;

    private ClipToast() {
    }

    public static void render(GuiGraphics g, KeyMapping clipKey) {
        Path clip = ClipModule.recentClip();
        if (clip == null || clipKey == null) return;
        Minecraft mc = Minecraft.getInstance();
        long age = System.currentTimeMillis() - ClipModule.recentAt();
        float in = Math.min(1f, age / 250f), out = Math.min(1f, (SHOW_MS - age) / 250f), t = Math.min(in, out);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int sw = mc.getWindow().getGuiScaledWidth();
        int x = sw - W - 6 + Math.round((1 - ease) * (W + 10)), y = 6;
        float dv = 0.4f;
        Draw.shadow(g, x, y + 8, W, H, 14 * dv, 36 * dv, -6 * dv, 0x99000000);
        Draw.glass(g, x, y, W, H, 14 * dv, 0xEB14101E, 0x2BFFFFFF);
        Draw.accentRect(g, x + 3, y + 8, 2, H - 16, 1, 1f);
        Type.draw(g, "Clip gespeichert", x + 10, y + 7.5f, 15 * dv, Tokens.SEMIBOLD, Tokens.TEXT);
        Type.draw(g, Type.ellipsize(g, clip.getFileName().toString(), W - 18, 12.5f * dv, 0), x + 10, y + 16.5f, 12.5f * dv, Tokens.MEDIUM, Tokens.MUTED);
        String key = clipKey.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
        Type.draw(g, Type.ellipsize(g, "[Umschalt + " + key + "] Ordner öffnen", W - 18, 12 * dv, 0), x + 10, y + 27, 12 * dv, Tokens.SEMIBOLD, Tokens.SUBTLE);
        float left = Math.max(0f, 1f - age / (float) SHOW_MS);
        Draw.accentRect(g, x + 10, y + H - 2.5f, Math.max(1.2f, (W - 20) * left), 1.2f, 0.6f, 1f);
    }
}
