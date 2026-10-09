package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.GuiGraphics;

import java.util.Collections;

/**
 * Basis für Mytic-Menüs: zeichnet in einer eigenen, kompakteren GUI-Größe (eine Stufe kleiner als die Minecraft-GUI,
 * immer ganzzahlig, damit die Pixel-Schrift scharf bleibt). Unterklassen arbeiten in "virtuellen" Koordinaten vw × vh.
 */
public abstract class MyticScreen extends InputScreen {
    protected float scale = 1f;
    protected int vw;
    protected int vh;
    private String tooltip;

    protected MyticScreen(String title) {
        super(title);
    }

    /**
     * Wählt die Darstellungsgröße anhand der Fensterauflösung: die größte ganzzahlige Stufe (scharfe Pixel-Schrift),
     * bei der der Inhalt mit Mindestgröße minW × minH in shareW × shareH des Fensters passt.
     */
    protected int fit(int minW, int minH, double shareW, double shareH) {
        for (int k = 10; k > 1; k--) {
            if (minW * k <= mc.displayWidth * shareW && minH * k <= mc.displayHeight * shareH) return k;
        }
        return 1;
    }

    /** "Kompakt" (Standard) oder "Groß" aus den Design-Einstellungen. */
    protected boolean large() {
        return MyticClient.theme != null && MyticClient.theme.menuSize.is("Groß");
    }

    /** Pixel pro virtuellem Pixel. Unterklassen legen fest, wie viel Platz sie einnehmen. */
    protected abstract int pixelScale();

    private void computeScale() {
        int gui = Compat.guiScale();
        scale = (float) pixelScale() / gui;
        vw = Math.round(width / scale);
        vh = Math.round(height / scale);
    }

    @Override
    public final void initGui() {
        computeScale();
        layout();
    }

    /** Layout in virtuellen Koordinaten (vw × vh). */
    protected abstract void layout();

    /** Zeichnen in virtuellen Koordinaten, Maus bereits umgerechnet. */
    protected abstract void draw(GuiGraphics g, int mouseX, int mouseY, float delta);

    /** Hintergrund hinter dem Menü: im Spiel abgedunkelt, im Hauptmenü das Panorama/Erde-Muster. */
    protected void renderBackground(GuiGraphics g) {
        drawDefaultBackground();
    }

    public void setTooltip(String text) {
        tooltip = text;
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float delta) {
        Ui.frame();
        GuiGraphics g = new GuiGraphics();
        tooltip = null;
        if (Compat.SCREEN_RENDERS_BACKGROUND) renderBackground(g);
        double mx = org.lwjgl.input.Mouse.getX() * (double) width / mc.displayWidth;
        double my = height - org.lwjgl.input.Mouse.getY() * (double) height / mc.displayHeight;
        Gfx.reset();
        Gfx.push(g);
        Gfx.scale(g, scale, scale);
        draw(g, (int) Math.round(mx / scale), (int) Math.round(my / scale), delta);
        Gfx.pop(g);
        Gfx.noScissor(g);
        if (tooltip != null) drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);
    }

    protected double vx(double x) {
        return x / scale;
    }

    protected double vy(double y) {
        return y / scale;
    }

    @Override
    protected boolean onClick(Input.Click event) {
        return click(new Input.Click(event.x() / scale, event.y() / scale, event.button()), false);
    }

    @Override
    protected boolean onDrag(Input.Click event, double dx, double dy) {
        return drag(new Input.Click(event.x() / scale, event.y() / scale, event.button()), dx / scale, dy / scale);
    }

    @Override
    protected boolean onRelease(Input.Click event) {
        return release(new Input.Click(event.x() / scale, event.y() / scale, event.button()));
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        return scroll(vx(mouseX), vy(mouseY), amount);
    }

    protected boolean click(Input.Click event, boolean doubleClick) {
        return false;
    }

    protected boolean drag(Input.Click event, double dx, double dy) {
        return false;
    }

    protected boolean release(Input.Click event) {
        return false;
    }

    protected boolean scroll(double mouseX, double mouseY, double amount) {
        return false;
    }
}
