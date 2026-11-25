package de.myticlegacy.client.gui.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Mytic-Emblem und Seiten-Marke („side-brand“ der Vorschau: Emblem, fetter Titel, kleiner Untertitel).
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Brand {
    private Brand() {
    }

    /** Emblem (Kantenlänge size Einheiten) bei x, y */
    public static void emblem(GuiGraphics g, float x, float y, float size) {
        float k = Draw.begin(g);
        int s = Math.max(8, Math.round(size * k));
        Draw.pxBaked(g, Bake.emblem(s), Math.round(x * k), Math.round(y * k), 0xFFFFFFFF);
        Draw.end(g);
    }

    /** Emblem 40 + Titel 19 + Untertitel 12,5 (Design-Pixel) */
    public static void side(Kit kit, float x, float y, String title, String sub) {
        float d = kit.d;
        emblem(kit.g, x, y, 40 * d);
        kit.text(title, x + 52 * d, y + 5 * d, 19, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.text(sub, x + 52 * d, y + 28 * d, 12.5f, Tokens.MEDIUM, Tokens.SUBTLE);
    }
}
