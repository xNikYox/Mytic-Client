package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.hud.HudModule;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Editor: Anzeigen ziehen (mit Hilfslinien und Einrasten), Mausrad ändert die Größe,
 * Rechtsklick öffnet die Einstellungen der Anzeige.
 */
public class HudEditScreen extends InputScreen {
    private static final int SNAP = 4;

    private final GuiScreen parent;
    private HudModule dragging;
    private double offsetX;
    private double offsetY;
    private final List<int[]> guides = new ArrayList<int[]>();

    public HudEditScreen(GuiScreen parent) {
        super("HUD bearbeiten");
        this.parent = parent;
    }

    private List<HudModule> active() {
        List<HudModule> list = new ArrayList<HudModule>();
        for (HudModule m : MyticClient.HUD) if (m.enabled()) list.add(m);
        return list;
    }

    private HudModule at(double mx, double my) {
        List<HudModule> list = active();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule m = list.get(i);
            if (Ui.inside(mx, my, m.x(), m.y(), m.width(), m.height())) return m;
        }
        return null;
    }

    /** Werkzeugleiste und Hinweis werden kompakt gezeichnet (eine GUI-Stufe kleiner, scharf). */
    private float ui() {
        int k = 1;
        for (int t = 10; t > 1; t--) {
            if (210 * t <= mc.displayWidth * 0.35) {
                k = t;
                break;
            }
        }
        return (float) k / Compat.guiScale();
    }

    /** Werkzeugleiste in kompakten Koordinaten. */
    private int[] toolbar() {
        float s = ui();
        int w = 210;
        int vw = Math.round(width / s);
        int vh = Math.round(height / s);
        return new int[]{(vw - w) / 2, vh - 24, w, 18};
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float delta) {
        GuiGraphics g = new GuiGraphics();
        Ui.frame();
        Gfx.reset();
        g.fill(0, 0, width, height, 0x55000000);
        for (int x = 0; x < width; x += 20) g.fill(x, 0, x + 1, height, 0x0CFFFFFF);
        for (int y = 0; y < height; y += 20) g.fill(0, y, width, y + 1, 0x0CFFFFFF);

        HudModule hover = dragging != null ? dragging : at(mouseX, mouseY);
        for (HudModule m : active()) {
            m.draw(g, true);
            float t = Ui.animate("edit:" + m.id, m == hover, 16f);
            int color = Ui.mix(0x50FFFFFF, Ui.accent(), t);
            if (Ui.lunar()) {
                // Lunar: weißer Rahmen, beim Überfahren deutlicher
                g.renderOutline(m.x() - 1, m.y() - 1, m.width() + 2, m.height() + 2, Ui.mix(0x50FFFFFF, 0xF0FFFFFF, t));
            } else {
                if (t > 0.01f) Ui.glow(g, m.x() - 1, m.y() - 1, m.width() + 2, m.height() + 2, 0, Ui.accent2(), 3, t * 0.8f);
                g.renderOutline(m.x() - 1, m.y() - 1, m.width() + 2, m.height() + 2, Ui.mix(Ui.withAlpha(Ui.accent2(), 90), Ui.accent2(), t));
            }
        }
        for (int[] guide : guides) {
            if (guide[0] == 0) g.fill(guide[1], 0, guide[1] + 1, height, Ui.withAlpha(Ui.accent(), 200));
            else g.fill(0, guide[1], width, guide[1] + 1, Ui.withAlpha(Ui.accent(), 200));
        }
        if (hover != null) {
            String label = hover.name + "  " + Math.round(hover.scaleFactor() * 100) + " %";
            int lw = font.width(label) + 10;
            int lx = Math.max(2, Math.min(width - lw - 2, hover.x() + hover.width() / 2 - lw / 2));
            int ly = hover.y() > 16 ? hover.y() - 15 : hover.y() + hover.height() + 3;
            if (Ui.lunar()) {
                Ui.rect(g, lx, ly, lw, 12, 3, 0xE0181818);
            } else {
                Ui.glow(g, lx, ly, lw, 12, 6, Ui.accent(), 3, 0.8f);
                Ui.rect(g, lx, ly, lw, 12, 6, Ui.accent());
                Ui.hGradient(g, lx + 6, ly, lw - 12, 12, Ui.accent(), Ui.mix(Ui.accent(), Ui.PINK, 0.6f));
                Ui.rect(g, lx + lw - 12, ly, 12, 12, 6, Ui.mix(Ui.accent(), Ui.PINK, 0.6f));
            }
            Ui.text(g, label, lx + 5, ly + 2, 0xFFFFFFFF, false);
        }

        float s = ui();
        int mx = Math.round(mouseX / s);
        int my = Math.round(mouseY / s);
        Gfx.push(g);
        Gfx.scale(g, s, s);
        int[] bar = toolbar();
        String hint = "Ziehen · Mausrad: Größe · Rechtsklick: Einstellungen";
        int hw = font.width(hint) + 16;
        Ui.rect(g, bar[0] + bar[2] / 2 - hw / 2, bar[1] - 17, hw, 13, 6, 0xB0000000);
        Ui.centered(g, hint, bar[0] + bar[2] / 2, bar[1] - 14, 0xFFD9D2EA, false);
        if (Ui.lunar()) Ui.lunarPanel(g, bar[0], bar[1], bar[2], bar[3], 5);
        else Ui.neonPanel(g, bar[0], bar[1], bar[2], bar[3], 9, 1f);
        String[] labels = {"Mods", "Zurücksetzen", "Fertig"};
        int bw = (bar[2] - 8 - 8) / 3;
        for (int i = 0; i < labels.length; i++) {
            int bx = bar[0] + 4 + i * (bw + 4);
            boolean h = Ui.inside(mx, my, bx, bar[1] + 2, bw, 14);
            if (!Ui.lunar() && (i == 2 || h)) Ui.glow(g, bx, bar[1] + 2, bw, 14, 7, i == 2 ? Ui.accent() : Ui.accent2(), 2, h ? 1f : 0.5f);
            int bg = Ui.lunar() ? (i == 2 ? Ui.L_GREEN : 0xFF2A2A2A) : (i == 2 ? Ui.accent() : Ui.SURFACE);
            Ui.rect(g, bx, bar[1] + 2, bw, 14, 7, h ? Ui.mix(bg, 0xFFFFFFFF, 0.15f) : bg);
            Ui.centered(g, labels[i], bx + bw / 2, bar[1] + 5, 0xFFFFFFFF, false);
        }
        Gfx.pop(g);
    }

    @Override
    protected boolean onClick(Input.Click event) {
        double mx = event.x();
        double my = event.y();
        int[] bar = toolbar();
        double tx = mx / ui();
        double ty = my / ui();
        if (Ui.inside(tx, ty, bar[0], bar[1], bar[2], bar[3])) {
            int bw = (bar[2] - 16) / 3;
            int index = (int) ((tx - bar[0] - 4) / (bw + 4));
            switch (index) {
                case 0:
                    Compat.setScreen(new ModMenuScreen(this));
                    break;
                case 1:
                    for (HudModule m : MyticClient.HUD) m.resetPosition();
                    MyticClient.config().save();
                    break;
                default:
                    onClose();
            }
            return true;
        }
        HudModule module = at(mx, my);
        if (module == null) return false;
        if (event.button() == 1) {
            Compat.setScreen(new ModMenuScreen(this, module));
            return true;
        }
        dragging = module;
        offsetX = mx - module.x();
        offsetY = my - module.y();
        return true;
    }

    @Override
    protected boolean onDrag(Input.Click event, double dx, double dy) {
        if (dragging == null) return false;
        int w = dragging.width();
        int h = dragging.height();
        int x = (int) Math.round(event.x() - offsetX);
        int y = (int) Math.round(event.y() - offsetY);
        guides.clear();

        // Kandidaten: Bildschirmränder und -mitte, Kanten und Mitten der anderen Anzeigen
        List<Integer> xs = new ArrayList<Integer>(java.util.Arrays.asList(0, width / 2, width));
        List<Integer> ys = new ArrayList<Integer>(java.util.Arrays.asList(0, height / 2, height));
        for (HudModule other : active()) {
            if (other == dragging) continue;
            xs.add(other.x());
            xs.add(other.x() + other.width());
            xs.add(other.x() + other.width() / 2);
            ys.add(other.y());
            ys.add(other.y() + other.height());
            ys.add(other.y() + other.height() / 2);
        }
        x = snap(x, w, xs, 0);
        y = snap(y, h, ys, 1);
        dragging.moveTo(Math.max(0, Math.min(width - w, x)), Math.max(0, Math.min(height - h, y)));
        return true;
    }

    /** Rastet linke Kante, Mitte oder rechte Kante an der nächsten Linie ein. */
    private int snap(int pos, int size, List<Integer> lines, int axis) {
        int best = SNAP + 1;
        int result = pos;
        int guide = -1;
        for (int line : lines) {
            int[] offsets = {0, size / 2, size};
            for (int offset : offsets) {
                int d = Math.abs(pos + offset - line);
                if (d < best) {
                    best = d;
                    result = line - offset;
                    guide = line;
                }
            }
        }
        if (guide >= 0) guides.add(new int[]{axis, Math.min(guide, (axis == 0 ? width : height) - 1)});
        return result;
    }

    @Override
    protected boolean onRelease(Input.Click event) {
        if (dragging != null) {
            dragging = null;
            guides.clear();
            MyticClient.config().save();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double vertical) {
        HudModule module = at(mouseX, mouseY);
        if (module == null) return false;
        module.scale.set(module.scale.get() + (vertical > 0 ? 0.05 : -0.05));
        MyticClient.config().save();
        return true;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        if (event.key() == InputConstants.KEY_RSHIFT) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        MyticClient.config().save();
        Compat.setScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
