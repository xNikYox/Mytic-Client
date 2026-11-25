package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.GuiGraphics;
import de.myticlegacy.client.compat.InputConstants;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.TitleBackground;
import de.myticlegacy.client.gui.ui.Toasts;

/**
 * Basis der Mytic-Bildschirme im neuen Design (1.8.9): zeichnet in echten Pixeln (Design-Faktor d aus dem
 * 1920×1080-Entwurf, Fenster 1600 × 880), Bausteine und Klickflächen über {@link Kit}, Rückmeldungen über
 * {@link Toasts}. Hintergrund: im Spiel Unschärfe/Abdunkeln nach Einstellung, im Hauptmenü der Mytic-Hintergrund.
 */
public abstract class DesignScreen extends MyticScreen {
    protected final Kit kit;
    protected float d = 1;
    protected final long openedAt = System.currentTimeMillis();

    protected DesignScreen(String id, String title) {
        super(title);
        kit = new Kit(id);
    }

    @Override
    protected int pixelScale() {
        return 1;
    }

    @Override
    protected void layout() {
        Compat.textInput(this, true);
    }

    protected float factor() {
        boolean big = large();
        return Math.max(0.3f, Math.min(vw * (big ? 0.94f : 0.84f) / 1600f, vh * (big ? 0.93f : 0.82f) / 880f));
    }

    protected boolean animate() {
        return MyticClient.theme == null || MyticClient.theme.menuAnimations.get();
    }

    @Override
    protected void renderBackground(GuiGraphics g) {
        if (mc.theWorld == null) {
            String bg = MyticClient.theme == null ? "Mytic (animiert)" : MyticClient.theme.titleBackground.get();
            int mouseX = org.lwjgl.input.Mouse.getX() * width / Math.max(1, mc.displayWidth);
            int mouseY = height - org.lwjgl.input.Mouse.getY() * height / Math.max(1, mc.displayHeight);
            if (bg.equals("Minecraft-Panorama")) {
                Panorama.render(width, height);
                float k = Draw.begin(g);
                TitleBackground.panoramaShade(g, Math.round(width * k), Math.round(height * k));
                Draw.end(g);
            } else if (!(bg.equals("Launcher-Design") && ThemeScene.draw(g, width, height, mouseX, mouseY))) {
                float k = Draw.begin(g);
                if (bg.equals("Einfarbig dunkel")) TitleBackground.solid(g, Math.round(width * k), Math.round(height * k));
                else TitleBackground.draw(g, Math.round(width * k), Math.round(height * k), mouseX * k, mouseY * k, animate());
                Draw.end(g);
            }
        } else {
            ModMenuScreen.background(g, width, height);
        }
        g.fill(0, 0, width, height, 0x61070510); // Schleier rgba(7,5,14,.38)
    }

    @Override
    protected final void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        d = factor();
        kit.begin(g, vw, vh, d, mouseX, mouseY);
        frame(g);
        kit.end();
        Toasts.draw(g, vw, vh, d);
    }

    protected abstract void frame(GuiGraphics g);

    protected float[] center(float dw, float dh) {
        float w = dw * d, h = dh * d;
        return new float[]{Math.round((vw - w) / 2f), Math.round((vh - h) / 2f), Math.round(w), Math.round(h)};
    }

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        return kit.mouseDown(event.x(), event.y(), event.button());
    }

    @Override
    protected boolean drag(Input.Click event, double dx, double dy) {
        return kit.mouseDrag(event.x(), event.y());
    }

    @Override
    protected boolean release(Input.Click event) {
        return kit.mouseUp(event.x(), event.y(), event.button());
    }

    @Override
    protected boolean scroll(double mouseX, double mouseY, double amount) {
        return kit.wheel(mouseX, mouseY, amount);
    }

    protected boolean keyFirst(int key, boolean ctrl) {
        return false;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        int key = event.key();
        if (keyFirst(key, event.hasControlDown())) return true;
        int k = key == InputConstants.KEY_BACKSPACE ? (event.hasControlDown() ? Kit.KEY_DELETE_WORD : Kit.KEY_BACKSPACE)
                : key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER ? Kit.KEY_ENTER
                : key == InputConstants.KEY_ESCAPE ? Kit.KEY_ESCAPE : key == InputConstants.KEY_TAB ? Kit.KEY_TAB : 0;
        if (k != 0 && kit.key(k)) return true;
        if (kit.typing()) return false; // 1.8.9: Zeichen kommen über dieselbe Taste (keyTyped) – nicht schlucken
        if (key == InputConstants.KEY_ESCAPE) {
            back();
            return true;
        }
        if (key == InputConstants.KEY_RSHIFT) {
            Compat.setScreen(null);
            return true;
        }
        return shortcut(key, event.hasControlDown());
    }

    protected boolean shortcut(int key, boolean ctrl) {
        return false;
    }

    @Override
    protected boolean onChar(Input.Typed event) {
        if (!kit.typing()) return typedFree(event.codepointAsString());
        return kit.typed(event.codepointAsString());
    }

    protected boolean typedFree(String s) {
        return false;
    }

    protected void back() {
        onClose();
    }

    @Override
    public void onGuiClosed() {
        Compat.textInput(this, false);
        kit.forget();
        super.onGuiClosed();
    }
}
