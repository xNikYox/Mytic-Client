package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.hud.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Editor: Anzeigen ziehen (mit Hilfslinien und Einrasten), Mausrad ändert die Größe,
 * Rechtsklick öffnet die Einstellungen der Anzeige.
 */
public class HudEditScreen extends Screen {
    private static final int SNAP = 4;

    private final Screen parent;
    private HudModule dragging;
    private double offsetX;
    private double offsetY;
    private final List<int[]> guides = new ArrayList<>();

    public HudEditScreen(Screen parent) {
        super(Component.literal("HUD bearbeiten"));
        this.parent = parent;
    }

    private List<HudModule> active() {
        List<HudModule> list = new ArrayList<>();
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

    private int[] toolbar() {
        int w = 260;
        return new int[]{(width - w) / 2, height - 30, w, 20};
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Ui.frame();
        for (int x = 0; x < width; x += 20) g.fill(x, 0, x + 1, height, 0x0CFFFFFF);
        for (int y = 0; y < height; y += 20) g.fill(0, y, width, y + 1, 0x0CFFFFFF);

        HudModule hover = dragging != null ? dragging : at(mouseX, mouseY);
        for (HudModule m : active()) {
            m.draw(g, true);
            float t = Ui.animate("edit:" + m.id, m == hover, 16f);
            int color = Ui.mix(0x50FFFFFF, Ui.accent(), t);
            g.renderOutline(m.x() - 1, m.y() - 1, m.width() + 2, m.height() + 2, color);
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
            Ui.rect(g, lx, ly, lw, 12, 6, Ui.accent());
            Ui.text(g, label, lx + 5, ly + 2, 0xFFFFFFFF, false);
        }

        Ui.centered(g, "Ziehen zum Verschieben  ·  Mausrad: Größe  ·  Rechtsklick: Einstellungen", width / 2, 8, 0xFFE8E2F5, true);

        int[] bar = toolbar();
        Ui.rect(g, bar[0], bar[1], bar[2], bar[3], 10, Ui.PANEL);
        String[] labels = {"Mods", "Zurücksetzen", "Fertig"};
        int bw = (bar[2] - 8 - 8) / 3;
        for (int i = 0; i < labels.length; i++) {
            int bx = bar[0] + 4 + i * (bw + 4);
            boolean h = Ui.inside(mouseX, mouseY, bx, bar[1] + 3, bw, 14);
            int bg = i == 2 ? Ui.accent() : Ui.SURFACE;
            Ui.rect(g, bx, bar[1] + 3, bw, 14, 7, h ? Ui.mix(bg, 0xFFFFFFFF, 0.15f) : bg);
            Ui.centered(g, labels[i], bx + bw / 2, bar[1] + 6, 0xFFFFFFFF, false);
        }
        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int[] bar = toolbar();
        if (Ui.inside(mx, my, bar[0], bar[1], bar[2], bar[3])) {
            int bw = (bar[2] - 16) / 3;
            int index = (int) ((mx - bar[0] - 4) / (bw + 4));
            switch (index) {
                case 0 -> minecraft.setScreen(new ModMenuScreen(this));
                case 1 -> {
                    MyticClient.HUD.forEach(HudModule::resetPosition);
                    MyticClient.config().save();
                }
                default -> onClose();
            }
            return true;
        }
        HudModule module = at(mx, my);
        if (module == null) return super.mouseClicked(event, doubleClick);
        if (event.button() == 1) {
            minecraft.setScreen(new ModMenuScreen(this, module));
            return true;
        }
        dragging = module;
        offsetX = mx - module.x();
        offsetY = my - module.y();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        int w = dragging.width();
        int h = dragging.height();
        int x = (int) Math.round(event.x() - offsetX);
        int y = (int) Math.round(event.y() - offsetY);
        guides.clear();

        // Kandidaten: Bildschirmränder und -mitte, Kanten und Mitten der anderen Anzeigen
        List<Integer> xs = new ArrayList<>(List.of(0, width / 2, width));
        List<Integer> ys = new ArrayList<>(List.of(0, height / 2, height));
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
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            guides.clear();
            MyticClient.config().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        HudModule module = at(mouseX, mouseY);
        if (module == null) return false;
        module.scale.set(module.scale.get() + (vertical > 0 ? 0.05 : -0.05));
        MyticClient.config().save();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        MyticClient.config().save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
