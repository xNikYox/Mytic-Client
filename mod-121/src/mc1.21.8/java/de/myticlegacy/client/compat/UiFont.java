package de.myticlegacy.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Inter (TTF, OFL) als eigene Minecraft-Schrift für das neue Mytic-Design. Jede Größe ist eine eigene Schrift
 * (assets/myticclient/font/ui_m_&lt;px&gt;.json = Medium, ui_s_&lt;px&gt;.json = SemiBold) mit oversample 1: Glyphen
 * werden genau in Bildschirm-Pixeln gezeichnet (Schrift-Texturen sind ungefiltert – so bleibt die Kantenglättung
 * von FreeType scharf). Alle Werte hier in echten Pixeln; die Aufrufer zeichnen im Pixel-Raum.
 * Fassung bis 1.21.8 (Schrift als ResourceLocation am Style).
 */
public final class UiFont {
    /** Vorhandene Pixelgrößen (Em-Höhe) */
    public static final int[] SIZES = {9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24, 27, 30, 34, 38};
    private static final Style[][] STYLES = new Style[2][SIZES.length];

    static {
        for (int w = 0; w < 2; w++) {
            for (int i = 0; i < SIZES.length; i++) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("myticclient", (w == 0 ? "ui_m_" : "ui_s_") + SIZES[i]);
                STYLES[w][i] = Style.EMPTY.withFont(id);
            }
        }
    }

    private UiFont() {
    }

    /** Echte TTF-Schrift (sonst Minecraft-Pixelschrift als Ersatz) */
    public static boolean ttf() {
        return true;
    }

    private static int index(float px) {
        int best = 0;
        for (int i = 1; i < SIZES.length; i++) if (Math.abs(SIZES[i] - px) < Math.abs(SIZES[best] - px)) best = i;
        return best;
    }

    /** Tatsächlich verwendete Pixelgröße für den Wunsch px */
    public static int size(float px) {
        return SIZES[index(px)];
    }

    private static Component text(String s, float px, int weight) {
        return Component.literal(s).withStyle(STYLES[weight == 0 ? 0 : 1][index(px)]);
    }

    /** Breite in Pixeln */
    public static int width(String s, float px, int weight) {
        return Minecraft.getInstance().font.width(text(s, px, weight));
    }

    /** Höhe der Großbuchstaben in Pixeln (Inter: 0,727 em) */
    public static int capHeight(float px) {
        return Math.round(size(px) * 0.727f);
    }

    /** Zeichnet so, dass die Oberkante der Großbuchstaben bei y = 0 liegt (Grundlinie bei capHeight). */
    public static void draw(GuiGraphics g, String s, float px, int weight, int color) {
        if ((color >>> 24) < 4) return;
        // Minecraft legt die Grundlinie jeder Schrift 7 Einheiten unter y
        g.drawString(Minecraft.getInstance().font, text(s, px, weight), 0, capHeight(px) - 7, color, false);
    }
}
