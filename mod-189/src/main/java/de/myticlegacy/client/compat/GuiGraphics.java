package de.myticlegacy.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

/** Zeichenfläche mit den Methoden der neuen Versionen (fill, drawString, renderItem …) für 1.8.9. */
public final class GuiGraphics {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final int width;
    private final int height;

    public GuiGraphics() {
        ScaledResolution resolution = new ScaledResolution(mc);
        width = resolution.getScaledWidth();
        height = resolution.getScaledHeight();
    }

    public int guiWidth() {
        return width;
    }

    public int guiHeight() {
        return height;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        if ((color >>> 24) == 0) return;
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    /** Senkrechter Farbverlauf (über die bewährte Methode von Gui). */
    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        GRADIENT.draw(x1, y1, x2, y2, top, bottom);
    }

    private static final Gradient GRADIENT = new Gradient();

    private static final class Gradient extends Gui {
        void draw(int x1, int y1, int x2, int y2, int top, int bottom) {
            drawGradientRect(x1, y1, x2, y2, top, bottom);
        }
    }

    /** Text; fast durchsichtige Farben werden übersprungen (1.8.9 würde sie sonst voll deckend zeichnen). */
    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        if ((color >>> 24) < 8) return x;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int end = font.renderer.drawString(text, x, y, color, shadow);
        GlStateManager.color(1, 1, 1, 1);
        return end;
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.getItem() == null) return;
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableDepth();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        GlStateManager.disableDepth();
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableBlend();
        GlStateManager.popMatrix();
    }

    /** Haltbarkeitsbalken und Anzahl wie im Inventar. */
    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        if (stack == null || stack.getItem() == null) return;
        mc.getRenderItem().renderItemOverlayIntoGUI(font.renderer, stack, x, y, null);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
    }

    public void renderOutline(int x, int y, int w, int h, int color) {
        fill(x, y, x + w, y + 1, color);
        fill(x, y + h - 1, x + w, y + h, color);
        fill(x, y + 1, x + 1, y + h - 1, color);
        fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        Gfx.scissor(this, x1, y1, x2, y2);
    }

    public void disableScissor() {
        Gfx.noScissor(this);
    }
}
