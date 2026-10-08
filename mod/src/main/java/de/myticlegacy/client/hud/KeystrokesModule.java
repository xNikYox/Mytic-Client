package de.myticlegacy.client.hud;

import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;

/** W A S D, Maustasten mit CPS und Leertaste. Gedrückte Tasten leuchten weich auf. */
public class KeystrokesModule extends HudModule {
    private static final int KEY = 22;
    private static final int GAP = 2;

    private final BoolSetting showMouse;
    private final BoolSetting showSpace;
    private final BoolSetting showCps;
    private final ColorSetting pressedColor;

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Zeigt WASD, Maustasten und Leertaste", () -> Items.NOTE_BLOCK, true, 4, 62);
        showMouse = new BoolSetting(this, "mouse", "Maustasten zeigen", true);
        showSpace = new BoolSetting(this, "space", "Leertaste zeigen", true);
        showCps = new BoolSetting(this, "cps", "CPS auf den Maustasten", true);
        pressedColor = new ColorSetting(this, "pressed", "Farbe beim Drücken", 0xFFFFFFFF);
    }

    @Override
    public int baseWidth() {
        return KEY * 3 + GAP * 2;
    }

    @Override
    public int baseHeight() {
        return KEY * 2 + GAP + (showMouse.get() ? GAP + 20 : 0) + (showSpace.get() ? GAP + 10 : 0);
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        var options = Minecraft.getInstance().options;
        key(g, KEY + GAP, 0, KEY, KEY, "W", options.keyUp);
        int row = KEY + GAP;
        key(g, 0, row, KEY, KEY, "A", options.keyLeft);
        key(g, KEY + GAP, row, KEY, KEY, "S", options.keyDown);
        key(g, (KEY + GAP) * 2, row, KEY, KEY, "D", options.keyRight);
        row += KEY + GAP;
        if (showMouse.get()) {
            int half = (baseWidth() - GAP) / 2;
            mouse(g, 0, row, half, "LMB", ClickCounter.left(), options.keyAttack);
            mouse(g, half + GAP, row, baseWidth() - half - GAP, "RMB", ClickCounter.right(), options.keyUse);
            row += 20 + GAP;
        }
        if (showSpace.get()) {
            float t = Ui.animate(this.id + ":space", options.keyJump.isDown(), 18f);
            box(g, 0, row, baseWidth(), 10, t);
            int bar = baseWidth() / 2;
            g.fill(bar - 12, row + 4, bar + 12, row + 6, Ui.mix(color(), 0xFF111111, t));
        }
    }

    private void box(GuiGraphics g, int x, int y, int w, int h, float pressed) {
        int base = Ui.withAlpha(0x0B0812, background.get() ? (int) Math.round(backgroundOpacity.get() * 2.55) : 0);
        int fill = Ui.mix(base, Ui.withAlpha(pressedColor.get(), 210), pressed);
        int r = rounded.get() ? 3 : 0;
        if (background.get()) Ui.outline(g, x, y, w, h, r, Ui.mix(0x30FFFFFF, fill, pressed), fill);
        else Ui.rect(g, x, y, w, h, r, fill);
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, String label, KeyMapping mapping) {
        float t = Ui.animate(id + ":" + label, mapping.isDown(), 18f);
        box(g, x, y, w, h, t);
        var font = Ui.font();
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, Ui.mix(color(), 0xFF111111, t), textShadow() && t < 0.5f);
    }

    private void mouse(GuiGraphics g, int x, int y, int w, String label, int cps, KeyMapping mapping) {
        float t = Ui.animate(id + ":" + label, mapping.isDown(), 18f);
        box(g, x, y, w, 20, t);
        var font = Ui.font();
        int color = Ui.mix(color(), 0xFF111111, t);
        boolean drawShadow = textShadow() && t < 0.5f;
        if (showCps.get()) {
            g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, color, drawShadow);
            String text = cps + " CPS";
            Ui.scaled(g, text, x + w / 2f - font.width(text) * 0.3f, y + 12.5f, 0.6f, color, false);
        } else {
            g.drawString(font, label, x + (w - font.width(label)) / 2, y + 6, color, drawShadow);
        }
    }
}
