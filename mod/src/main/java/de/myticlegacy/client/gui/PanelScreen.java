package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Unterseite des Mytic-Menüs (Freunde, Cosmetics): gleiches Panel wie das Mod-Menü, mit Zurück zur Übersicht. */
public abstract class PanelScreen extends MyticScreen {
    protected static final int HEADER = 34;
    protected static final int FOOTER = 28;
    private final long openedAt = System.currentTimeMillis();
    protected int px, py, pw, ph;

    protected PanelScreen(String title) {
        super(Component.literal(title));
    }

    private double shareW() {
        return large() ? 0.78 : 0.6;
    }

    private double shareH() {
        return large() ? 0.86 : 0.78;
    }

    @Override
    protected int pixelScale() {
        return fit(380, 240, shareW(), shareH());
    }

    @Override
    protected void layout() {
        Compat.textInput(this, true);
        pw = Math.min(560, Math.max(340, (int) (vw * shareW())));
        ph = Math.min(340, Math.max(200, (int) (vh * shareH())));
        pw = Math.min(pw, vw - 8);
        ph = Math.min(ph, vh - 8);
        px = (vw - pw) / 2;
        py = (vh - ph) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        String mode = MyticClient.theme.menuBackground.get();
        if (mode.equals("Unschärfe")) super.renderBackground(g, mouseX, mouseY, delta);
        else if (mode.equals("Abdunkeln") || minecraft.level == null) g.fill(0, 0, width, height, 0x99000000);
    }

    @Override
    protected final void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        float ease = 1f - (1f - open) * (1f - open) * (1f - open);
        Gfx.push(g);
        Gfx.translate(g, 0, (1 - ease) * 10);
        Ui.rect(g, px - 3, py + 3, pw + 6, ph + 3, 10, 0x50000000);
        boolean lunar = Ui.lunar();
        if (lunar) {
            Ui.lunarPanel(g, px, py, pw, ph, 6);
        } else {
            Ui.neonPanel(g, px, py, pw, ph, 8, 1f);
            g.fillGradient(px + 4, py + 2, px + pw - 4, py + HEADER, Ui.withAlpha(Ui.accent(), 42), Ui.withAlpha(Ui.accent(), 0));
        }
        boolean backHover = Ui.inside(mouseX, mouseY, px + 10, py + 9, 16, 16);
        Ui.rect(g, px + 10, py + 9, 16, 16, lunar ? 4 : 8, lunar ? (backHover ? Ui.L_CARD_HOVER : Ui.L_CARD) : (backHover ? Ui.SURFACE_HOVER : Ui.SURFACE));
        Ui.centered(g, "‹", px + 18, py + 13, Ui.TEXT, false);
        String title = title();
        if (lunar) Ui.text(g, title, px + 34, py + 13, 0xFFFFFFFF, true);
        else Ui.glowText(g, title, px + 34, py + 13, Ui.accent(), 1f);
        Ui.rect(g, px + 10, py + HEADER, pw - 20, 1, 0, lunar ? Ui.L_LINE : Ui.LINE);
        Ui.rect(g, px + 10, py + ph - FOOTER, pw - 20, 1, 0, lunar ? Ui.L_LINE : Ui.LINE);
        drawContent(g, mouseX, mouseY, px + 34 + font.width(title) + 16);
        Gfx.pop(g);
    }

    /** Überschrift oben links (Großbuchstaben). */
    protected abstract String title();

    /** Inhalt zeichnen; headerX = erste freie Stelle in der Kopfzeile rechts vom Titel. */
    protected abstract void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX);

    protected boolean clickContent(double mx, double my) {
        return false;
    }

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        if (event.button() != 0) return false;
        if (Ui.inside(event.x(), event.y(), px + 10, py + 9, 16, 16)) {
            back();
            return true;
        }
        return clickContent(event.x(), event.y());
    }

    protected void back() {
        Compat.setScreen(new HubScreen());
    }

    @Override
    protected boolean onKey(Input.Key event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            back();
            return true;
        }
        if (event.key() == InputConstants.KEY_RSHIFT) {
            Compat.setScreen(null);
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        Compat.textInput(this, false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
