package de.myticlegacy.client.compat;

import net.minecraft.client.gui.FontRenderer;

/** Schrift mit den Namen der neuen Versionen (width, lineHeight), damit der Menü-Code fast gleich bleibt. */
public final class Font {
    public final FontRenderer renderer;
    public final int lineHeight;

    public Font(FontRenderer renderer) {
        this.renderer = renderer;
        this.lineHeight = renderer.FONT_HEIGHT;
    }

    public int width(String text) {
        return renderer.getStringWidth(text);
    }

    public String plainSubstrByWidth(String text, int width) {
        return renderer.trimStringToWidth(text, width);
    }
}
