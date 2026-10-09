package de.myticlegacy.client.hud;

import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;

import java.util.function.Function;
import java.util.function.Supplier;

/** Einzeilige Anzeige im Lunar-Stil: optional "[Name: Wert]", Name und Wert in eigenen Farben. */
public class TextModule extends HudModule {
    private final String label;
    private final Function<Boolean, String> value;
    public final BoolSetting showLabel;
    public final BoolSetting brackets;
    public final ColorSetting labelColor;

    public TextModule(String id, String name, String description, Supplier<Item> icon, boolean enabledByDefault, int x, int y,
                      String label, Function<Boolean, String> value) {
        super(id, name, description, icon, enabledByDefault, x, y);
        this.label = label;
        this.value = value;
        showLabel = new BoolSetting(this, "showLabel", "Bezeichnung zeigen", !label.isEmpty());
        brackets = new BoolSetting(this, "brackets", "Klammern [ ]", false);
        labelColor = new ColorSetting(this, "labelColor", "Farbe der Bezeichnung", 0xFFC9B0FF);
    }

    private String value(boolean preview) {
        String v = value.apply(preview);
        return v == null ? "" : v;
    }

    /** Bezeichnungen, die bei Lunar als Einheit hinter dem Wert stehen ("120 FPS"). */
    private boolean unit() {
        return label.equals("FPS") || label.equals("CPS");
    }

    private String labelPart() {
        if (!showLabel.get() || label.isEmpty()) return "";
        return lunar() && !unit() ? label + ": " : label + " ";
    }

    private String full(boolean preview) {
        String text = lunar() && unit() && showLabel.get() ? value(preview) + " " + label : labelPart() + value(preview);
        return brackets.get() ? "[" + text + "]" : text;
    }

    @Override
    public boolean hasContent() {
        return !value(false).isEmpty();
    }

    @Override
    public int baseWidth() {
        return Math.max(background.get() ? 52 : 10, Ui.font().width(full(true)) + 12);
    }

    @Override
    public int baseHeight() {
        return 17;
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        String v = value(preview);
        if (v.isEmpty()) return;
        int w = baseWidth();
        panel(g, w, baseHeight());
        if (lunar()) {
            String text = full(preview);
            g.drawString(Ui.font(), text, (w - Ui.font().width(text)) / 2, 5, color(), textShadow());
            return;
        }
        var font = Ui.font();
        String open = brackets.get() ? "[" : "";
        String close = brackets.get() ? "]" : "";
        String name = labelPart();
        int total = font.width(open + name + v + close);
        int x = (w - total) / 2;
        int y = 5;
        g.drawString(font, open, x, y, color(), textShadow());
        x += font.width(open);
        g.drawString(font, name, x, y, labelColor.get(), textShadow());
        x += font.width(name);
        g.drawString(font, v, x, y, color(), textShadow());
        x += font.width(v);
        g.drawString(font, close, x, y, color(), textShadow());
    }
}
