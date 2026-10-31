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

    /** Werte höchstens 20-mal pro Sekunde neu berechnen (sonst mehrmals pro Bild für Breite, Position und Text). */
    private final String[] cached = new String[2];
    // zuletzt gebauter Anzeigetext und dessen Breite – neu nur, wenn sich Wert oder Einstellungen ändern
    private final String[] fullFor = new String[2], full = new String[2];
    private final int[] fullFlags = new int[2], fullWidth = new int[2];
    private final long[] cachedAt = new long[2];

    private String value(boolean preview) {
        int i = preview ? 1 : 0;
        long now = System.nanoTime();
        if (cached[i] == null || now - cachedAt[i] >= REFRESH_NANOS) {
            String v = value.apply(preview);
            cached[i] = v == null ? "" : v;
            cachedAt[i] = now;
        }
        return cached[i];
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
        int i = preview ? 1 : 0;
        String v = value(preview);
        int flags = (lunar() ? 1 : 0) | (showLabel.get() ? 2 : 0) | (brackets.get() ? 4 : 0);
        if (v != fullFor[i] || flags != fullFlags[i] || full[i] == null) { // v ist bis zur nächsten Aktualisierung dasselbe Objekt
            String text = lunar() && unit() && showLabel.get() ? v + " " + label : labelPart() + v;
            full[i] = brackets.get() ? "[" + text + "]" : text;
            fullWidth[i] = Ui.font().width(full[i]);
            fullFor[i] = v;
            fullFlags[i] = flags;
        }
        return full[i];
    }

    @Override
    public boolean hasContent() {
        return !value(false).isEmpty();
    }

    @Override
    public int baseWidth() {
        full(true);
        return Math.max(background.get() ? 52 : 10, fullWidth[1] + 12);
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
            g.drawString(Ui.font(), text, (w - fullWidth[preview ? 1 : 0]) / 2, 5, color(), textShadow());
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
