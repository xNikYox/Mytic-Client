package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Übersetzt die Eingabe-Methoden von Minecraft in versionsunabhängige Aufrufe (onClick, onKey …).
 * Diese Fassung gilt ab 1.21.9; ältere Versionen haben eine eigene InputScreen.java im Build-Projekt.
 */
public abstract class InputScreen extends Screen {
    protected InputScreen(Component title) {
        super(title);
    }

    /** Maustaste vereinheitlichen: 0 = links, 1 = rechts, 2 = Mitte (26.x zählt ab 1). */
    private static int button(int raw) {
        if (raw == InputConstants.MOUSE_BUTTON_LEFT) return 0;
        if (raw == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
        if (raw == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
        return raw;
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return onClick(new Input.Click(event.x(), event.y(), button(event.button()))) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return onDrag(new Input.Click(event.x(), event.y(), button(event.button())), dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return onRelease(new Input.Click(event.x(), event.y(), button(event.button()))) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return onScroll(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKey(new Input.Key(event.key(), event.hasControlDown())) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return onChar(new Input.Typed(event.codepointAsString(), event.isAllowedChatCharacter())) || super.charTyped(event);
    }
}
