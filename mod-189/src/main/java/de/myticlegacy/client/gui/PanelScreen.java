package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.GuiGraphics;

/** Unterseite des Mytic-Menüs (Freunde, Cosmetics): gleiches Panel wie das Mod-Menü, mit Zurück zur Übersicht. */
public abstract class PanelScreen extends MyticScreen {
    protected static final int HEADER = 34;
    protected static final int FOOTER = 28;
    private final long openedAt = System.currentTimeMillis();
    protected int px, py, pw, ph;

    protected PanelScreen(String title) {
        super(title);
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
    protected void renderBackground(GuiGraphics g) {
        ModMenuScreen.background(g, width, height);
    }

    /** Design-Faktor in Panel-Einheiten (Kopfzeile 34 Einheiten = 88 Design-Pixel) */
    protected static float dv() {
        return HEADER / 88f;
    }

    @Override
    protected final void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 350f);
        float ease = de.myticlegacy.client.gui.ui.Motion.ease(open);
        float d = dv();
        Gfx.push(g);
        Gfx.translate(g, 0, (1 - ease) * 8 * d);
        de.myticlegacy.client.gui.ui.Draw.shadow(g, px, py + 30 * d, pw, ph, 28 * d, 80 * d, -30 * d, 0xA6000000);
        de.myticlegacy.client.gui.ui.Draw.glass(g, px, py, pw, ph, 28 * d, mc.theWorld == null ? 0xE6120E1C : 0xD8120E1C, 0x24FFFFFF);
        boolean backHover = Ui.inside(mouseX, mouseY, px + 10, py + 9, 16, 16);
        de.myticlegacy.client.gui.ui.Widgets.iconButton(g, "panel:back", px + 10, py + 9, 16, d, "chevL", 8, true, null, backHover, false);
        String title = title();
        de.myticlegacy.client.gui.ui.Type.middle(g, title, px + 34, py + 17, 21 * d, de.myticlegacy.client.gui.ui.Tokens.SEMIBOLD, de.myticlegacy.client.gui.ui.Tokens.TEXT);
        float titleW = de.myticlegacy.client.gui.ui.Type.width(g, title, 21 * d, de.myticlegacy.client.gui.ui.Tokens.SEMIBOLD);
        de.myticlegacy.client.gui.ui.Draw.rect(g, px, py + HEADER, pw, 1f / de.myticlegacy.client.gui.ui.Draw.k(g), 0, 0x17FFFFFF);
        de.myticlegacy.client.gui.ui.Draw.rect(g, px, py + ph - FOOTER, pw, 1f / de.myticlegacy.client.gui.ui.Draw.k(g), 0, 0x17FFFFFF);
        drawContent(g, mouseX, mouseY, px + 34 + Math.round(titleW) + 12);
        Gfx.pop(g);
    }

    /** Überschrift oben links. */
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
    public void onGuiClosed() {
        Compat.textInput(this, false);
        ModMenuScreen.blur(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
