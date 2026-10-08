package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Basis für Mytic-Menüs: zeichnet in einer eigenen, kompakteren GUI-Größe (eine Stufe kleiner als die Minecraft-GUI,
 * immer ganzzahlig, damit die Pixel-Schrift scharf bleibt). Unterklassen arbeiten in "virtuellen" Koordinaten vw × vh.
 */
public abstract class MyticScreen extends Screen {
    protected float scale = 1f;
    protected int vw;
    protected int vh;

    protected MyticScreen(Component title) {
        super(title);
    }

    /**
     * Wählt die Darstellungsgröße anhand der Fensterauflösung: die größte ganzzahlige Stufe (scharfe Pixel-Schrift),
     * bei der der Inhalt mit Mindestgröße minW × minH in shareW × shareH des Fensters passt.
     */
    protected int fit(int minW, int minH, double shareW, double shareH) {
        var window = minecraft.getWindow();
        for (int k = 10; k > 1; k--) {
            if (minW * k <= window.getWidth() * shareW && minH * k <= window.getHeight() * shareH) return k;
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
        int gui = minecraft.getWindow().getGuiScale();
        scale = (float) pixelScale() / gui;
        vw = Math.round(width / scale);
        vh = Math.round(height / scale);
    }

    @Override
    protected final void init() {
        computeScale();
        layout();
    }

    /** Layout in virtuellen Koordinaten (vw × vh). */
    protected abstract void layout();

    /** Zeichnen in virtuellen Koordinaten, Maus bereits umgerechnet. */
    protected abstract void draw(GuiGraphics g, int mouseX, int mouseY, float delta);

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Ui.frame();
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        draw(g, Math.round(mouseX / scale), Math.round(mouseY / scale), delta);
        g.pose().popMatrix();
    }

    protected double vx(double x) {
        return x / scale;
    }

    protected double vy(double y) {
        return y / scale;
    }

    protected MouseButtonEvent virtual(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return click(virtual(event), doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return drag(virtual(event), dx / scale, dy / scale);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return release(virtual(event));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return scroll(vx(mouseX), vy(mouseY), vertical);
    }

    protected boolean click(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    protected boolean drag(MouseButtonEvent event, double dx, double dy) {
        return false;
    }

    protected boolean release(MouseButtonEvent event) {
        return false;
    }

    protected boolean scroll(double mouseX, double mouseY, double amount) {
        return false;
    }
}
