package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * Übersetzt die Eingabe von 1.8.9 (LWJGL 2) in die versionsunabhängigen Aufrufe (onClick, onKey …).
 * Mauskoordinaten werden genau (nicht auf GUI-Pixel gerundet) berechnet.
 */
public abstract class InputScreen extends GuiScreen {
    private int pressed = -1;
    private double lastX;
    private double lastY;

    /** Schrift mit den Methodennamen der neuen Versionen. */
    protected Font font;

    protected InputScreen(String title) {
    }

    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        font = new Font(mc.fontRendererObj);
        super.setWorldAndResolution(mc, width, height);
    }

    /** Schließen (Escape): standardmäßig zurück ins Spiel. */
    public void onClose() {
        mc.displayGuiScreen(null);
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    protected boolean onClick(Input.Click click) {
        return false;
    }

    protected boolean onDrag(Input.Click click, double dx, double dy) {
        return false;
    }

    protected boolean onRelease(Input.Click click) {
        return false;
    }

    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    protected boolean onKey(Input.Key key) {
        return false;
    }

    protected boolean onChar(Input.Typed typed) {
        return false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        double x = Mouse.getEventX() * (double) width / mc.displayWidth;
        double y = height - Mouse.getEventY() * (double) height / mc.displayHeight;
        int button = Mouse.getEventButton();
        if (button != -1 && Mouse.getEventButtonState()) {
            pressed = button;
            lastX = x;
            lastY = y;
            if (!onClick(new Input.Click(x, y, button))) mouseClicked((int) x, (int) y, button);
        } else if (button != -1) {
            pressed = -1;
            if (!onRelease(new Input.Click(x, y, button))) mouseReleased((int) x, (int) y, button);
        } else if (pressed != -1 && (x != lastX || y != lastY)) {
            onDrag(new Input.Click(x, y, pressed), x - lastX, y - lastY);
            lastX = x;
            lastY = y;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) onScroll(x, y, wheel > 0 ? 1 : -1);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode != 0 && onKey(new Input.Key(keyCode, isCtrlKeyDown()))) return;
        if (keyCode == 1) {
            if (shouldCloseOnEsc()) onClose();
            return;
        }
        if (typedChar >= ' ' && onChar(new Input.Typed(String.valueOf(typedChar), ChatAllowedCharacters.isAllowedCharacter(typedChar)))) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
