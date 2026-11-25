package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.TitleBackground;
import de.myticlegacy.client.gui.ui.Toasts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Basis der Mytic-Bildschirme im neuen Design: zeichnet in echten Pixeln (Design-Faktor d aus dem
 * 1920×1080-Entwurf, Fenster 1600 × 880), Bausteine und Klickflächen über {@link Kit}, Rückmeldungen über
 * {@link Toasts}. Hintergrund: im Spiel Unschärfe/Abdunkeln nach Einstellung, im Hauptmenü der Mytic-Hintergrund;
 * darüber der dunkle Schleier aus der Vorschau.
 */
public abstract class DesignScreen extends MyticScreen {
    protected final Kit kit;
    protected float d = 1;
    protected final long openedAt = System.currentTimeMillis();

    protected DesignScreen(String id, String title) {
        super(Component.literal(title));
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

    /** Design-Faktor: das 1600 × 880-Fenster füllt (Kompakt) 84 % bzw. (Groß) 94 % des Bildschirms */
    protected float factor() {
        boolean big = large();
        return Math.max(0.3f, Math.min(vw * (big ? 0.94f : 0.84f) / 1600f, vh * (big ? 0.93f : 0.82f) / 880f));
    }

    protected boolean animate() {
        return MyticClient.theme == null || MyticClient.theme.menuAnimations.get();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) {
            String bg = MyticClient.theme == null ? "Mytic (animiert)" : MyticClient.theme.titleBackground.get();
            if (bg.equals("Minecraft-Panorama")) {
                renderPanorama(g, delta);
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
            String mode = MyticClient.theme == null ? "Unschärfe" : MyticClient.theme.menuBackground.get();
            if (mode.equals("Unschärfe")) super.renderBackground(g, mouseX, mouseY, delta);
            else if (mode.equals("Abdunkeln")) g.fill(0, 0, width, height, 0x66000000);
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

    /** Inhalt zeichnen (über kit; Maße × d) */
    protected abstract void frame(GuiGraphics g);

    /** Zentriertes Fenster dw × dh (Design-Pixel): {x, y, w, h} */
    protected float[] center(float dw, float dh) {
        float w = dw * d, h = dh * d;
        return new float[]{Math.round((vw - w) / 2f), Math.round((vh - h) / 2f), Math.round(w), Math.round(h)};
    }

    // ------------------------------------------------------------------------------------------ Eingaben

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

    /** Eigene Tasten vor allem anderen (z. B. Tastenbelegung aufnehmen); true = verbraucht */
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
        if (kit.typing()) return true; // Tippen nicht als Tastenkürzel werten
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

    /** Tastenkürzel des Bildschirms (nicht beim Tippen) */
    protected boolean shortcut(int key, boolean ctrl) {
        return false;
    }

    @Override
    protected boolean onChar(Input.Typed event) {
        if (!kit.typing()) return typedFree(event.codepointAsString());
        return kit.typed(event.codepointAsString());
    }

    /** Zeichen ohne fokussiertes Feld (z. B. „einfach lostippen“ für die Suche) */
    protected boolean typedFree(String s) {
        return false;
    }

    /** Zurück (Escape, Zurück-Button) */
    protected void back() {
        onClose();
    }

    @Override
    public void removed() {
        Compat.textInput(this, false);
        kit.forget();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
