package de.myticlegacy.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;

/** Fassung bis 1.21.8: Eingaben kommen als einzelne Werte statt als Ereignis-Objekte. */
public abstract class InputScreen extends Screen {
    protected InputScreen(Component title) {
        super(title);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onClick(new Input.Click(mouseX, mouseY, button)) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return onDrag(new Input.Click(mouseX, mouseY, button), dx, dy) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return onRelease(new Input.Click(mouseX, mouseY, button)) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return onScroll(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return onKey(new Input.Key(key, Screen.hasControlDown())) || super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return onChar(new Input.Typed(String.valueOf(c), StringUtil.isAllowedChatCharacter(c))) || super.charTyped(c, modifiers);
    }
}
