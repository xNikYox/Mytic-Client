package de.myticlegacy.client.module;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;

/** Eigenes Fadenkreuz: Stil, Größe, Abstand, Dicke, Farbe, Umrandung. */
public class CrosshairModule extends Module {
    private final ModeSetting style;
    private final SliderSetting size;
    private final SliderSetting gap;
    private final SliderSetting thickness;
    private final ColorSetting color;
    private final BoolSetting outline;
    private final BoolSetting dot;

    public CrosshairModule() {
        super("crosshair", "Crosshair", "Eigenes Fadenkreuz in Form und Farbe", Category.VISUELL, () -> Items.TARGET, false);
        style = new ModeSetting(this, "style", "Stil", "Kreuz", "Kreuz", "Punkt", "Kreis", "T-Form");
        size = new SliderSetting(this, "size", "Länge", 1, 10, 1, 4, "");
        gap = new SliderSetting(this, "gap", "Abstand", 0, 8, 1, 2, "");
        thickness = new SliderSetting(this, "thickness", "Dicke", 1, 3, 1, 1, "");
        color = new ColorSetting(this, "color", "Farbe", 0xFFFFFFFF);
        outline = new BoolSetting(this, "outline", "Umrandung", true);
        dot = new BoolSetting(this, "dot", "Punkt in der Mitte", false);
    }

    /** true = eigenes Fadenkreuz gezeichnet, false = das normale verwenden. */
    public boolean render(GuiGraphics g) {
        if (!enabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson() || Compat.hudHidden(mc)) return true;
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int len = size.intValue();
        int gp = gap.intValue();
        int t = thickness.intValue();
        int half = t / 2;
        int c = color.get();
        switch (style.get()) {
            case "Punkt" -> bar(g, cx - half - 1, cy - half - 1, t + 2, t + 2, c);
            case "Kreis" -> {
                int r = gp + len / 2 + 1;
                for (int a = 0; a < 360; a += 10) {
                    double rad = Math.toRadians(a);
                    int x = cx + (int) Math.round(Math.cos(rad) * r);
                    int y = cy + (int) Math.round(Math.sin(rad) * r);
                    bar(g, x - half, y - half, t, t, c);
                }
            }
            default -> {
                bar(g, cx - gp - len - half, cy - half, len, t, c);
                bar(g, cx + gp + 1 - half + (t > 1 ? 0 : 0), cy - half, len, t, c);
                bar(g, cx - half, cy + gp + 1 - half, t, len, c);
                if (!style.is("T-Form")) bar(g, cx - half, cy - gp - len - half, t, len, c);
            }
        }
        if (dot.get() && !style.is("Punkt")) bar(g, cx - half, cy - half, t, t, c);
        return true;
    }

    private void bar(GuiGraphics g, int x, int y, int w, int h, int c) {
        if (outline.get()) g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        g.fill(x, y, x + w, y + h, c);
    }
}
