package de.myticlegacy.client.hud;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.module.Category;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Eine verschiebbare HUD-Anzeige mit gemeinsamen Einstellungen (Größe, Hintergrund, Textfarbe, Schatten).
 * Unterklassen zeichnen bei (0, 0) in ihrer Grundgröße; Position und Skalierung übernimmt draw().
 */
public abstract class HudModule extends Module {
    public final SliderSetting scale;
    public final BoolSetting background;
    public final SliderSetting backgroundOpacity;
    public final BoolSetting rounded;
    public final ColorSetting textColor;
    public final BoolSetting shadow;

    private final int defaultX;
    private final int defaultY;

    protected HudModule(String id, String name, String description, Supplier<Item> icon, boolean enabledByDefault, int defaultX, int defaultY) {
        super(id, name, description, Category.HUD, icon, enabledByDefault);
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        scale = new SliderSetting(this, "scale", "Größe", 0.5, 2.5, 0.05, 1.0, "×");
        background = new BoolSetting(this, "background", "Hintergrund", defaultBackground());
        backgroundOpacity = new SliderSetting(this, "bgOpacity", "Hintergrund-Deckkraft", 0, 100, 5, 55, " %");
        rounded = new BoolSetting(this, "rounded", "Abgerundete Ecken", true);
        textColor = new ColorSetting(this, "textColor", "Textfarbe", 0xFFFFFFFF);
        shadow = new BoolSetting(this, "shadow", "Textschatten", true);
    }

    /** Standardwert für den Hintergrund (kann pro Anzeige anders sein). */
    protected boolean defaultBackground() {
        return true;
    }

    public abstract int baseWidth();

    public abstract int baseHeight();

    /** Zeichnet bei (0, 0). preview = im HUD-Editor oder ohne Welt (Beispielwerte zeigen). */
    protected abstract void render(GuiGraphics g, boolean preview);

    /** Ob die Anzeige gerade etwas zu zeigen hat (z. B. Tränke nur mit aktiven Effekten). */
    public boolean hasContent() {
        return true;
    }

    public float scaleFactor() {
        return scale.floatValue();
    }

    public int width() {
        return Math.max(1, Math.round(baseWidth() * scaleFactor()));
    }

    public int height() {
        return Math.max(1, Math.round(baseHeight() * scaleFactor()));
    }

    public void draw(GuiGraphics g, boolean preview) {
        if (!preview && !hasContent()) return;
        Gfx.push(g);
        Gfx.translate(g, x(), y());
        Gfx.scale(g, scaleFactor(), scaleFactor());
        render(g, preview);
        Gfx.pop(g);
    }

    public int x() {
        int[] pos = MyticClient.config().positions.get(id);
        int x = pos != null ? pos[0] : defaultX;
        return Math.max(0, Math.min(x, Minecraft.getInstance().getWindow().getGuiScaledWidth() - width()));
    }

    public int y() {
        int[] pos = MyticClient.config().positions.get(id);
        int y = pos != null ? pos[1] : defaultY;
        return Math.max(0, Math.min(y, Minecraft.getInstance().getWindow().getGuiScaledHeight() - height()));
    }

    public void moveTo(int x, int y) {
        MyticClient.config().positions.put(id, new int[]{x, y});
    }

    public void resetPosition() {
        MyticClient.config().positions.remove(id);
    }

    protected void panel(GuiGraphics g, int w, int h) {
        if (background.get()) {
            Ui.rect(g, 0, 0, w, h, rounded.get() ? 3 : 0, Ui.withAlpha(0x0B0812, (int) Math.round(backgroundOpacity.get() * 2.55)));
        }
    }

    protected int color() {
        return textColor.get();
    }

    protected boolean textShadow() {
        return shadow.get();
    }
}
