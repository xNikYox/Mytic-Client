package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.emotes.EmoteCatalog;
import de.myticlegacy.client.emotes.EmoteLink;
import de.myticlegacy.client.emotes.Emotes;
import de.myticlegacy.client.gui.ui.EmoteWheel;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.compat.GuiGraphics;

import java.util.List;

/**
 * Emote-Rad: Taste (G) halten, Maus in Richtung des Emotes, loslassen = abspielen. Kurz antippen lässt das Rad offen –
 * dann per Klick oder Taste 1–8 wählen; Escape, Rechtsklick oder ein Klick daneben schließt es.
 */
public class EmoteWheelScreen extends DesignScreen {
    private static final long HOLD_MS = 220;
    private boolean holding = true;
    /** Meldet diese Version die gehaltene Taste auch bei offenem Bildschirm? (sonst nur Klick/1–8) */
    private boolean sawHeld;
    private int hover = -1;
    private String error;
    private long errorAt;

    public EmoteWheelScreen() {
        super("emotewheel", "Emotes");
    }

    private static List<EmoteCatalog.Emote> emotes() {
        return EmoteCatalog.ALL;
    }

    @Override
    protected void frame(GuiGraphics g) {
        List<EmoteCatalog.Emote> list = emotes();
        boolean[] usable = new boolean[list.size()];
        for (int i = 0; i < usable.length; i++) usable[i] = EmoteLink.usable(list.get(i));
        float cx = vw / 2f, cy = vh / 2f;
        float wd = Math.min(d * 1.15f, Math.min(vw, vh) / 620f);
        hover = EmoteWheel.hovered(list.size(), cx, cy, wd, kit.mx, kit.my);
        float open = Math.min(1, (System.currentTimeMillis() - openedAt) / 260f);
        String sub;
        int subColor = Tokens.MUTED;
        long cd = Emotes.cooldown();
        if (error != null && System.currentTimeMillis() - errorAt < 2500) {
            sub = error;
            subColor = Tokens.WARN;
        } else if (cd > 0) {
            sub = "Noch " + Math.max(1, (cd + 999) / 1000) + " s warten";
            subColor = Tokens.WARN;
        } else if (hover >= 0) {
            sub = holding && sawHeld ? "Loslassen zum Abspielen" : "Klicken zum Abspielen";
        } else {
            sub = EmoteLink.connected() ? "Mytic-Spieler sehen es" : "Nur für dich sichtbar";
        }
        EmoteWheel.draw(g, list, usable, cx, cy, wd, open, hover, null, sub, subColor);

        // Taste gehalten und losgelassen: gewähltes Emote abspielen (kurz angetippt: Rad bleibt offen)
        boolean down = Compat.held(MyticClient.emoteKey);
        if (down) sawHeld = true;
        if (holding && sawHeld && !down) {
            holding = false;
            if (System.currentTimeMillis() - openedAt >= HOLD_MS) {
                if (hover >= 0) choose(hover);
                else Compat.setScreen(null);
            }
        }
    }

    private void choose(int i) {
        List<EmoteCatalog.Emote> list = emotes();
        if (i < 0 || i >= list.size()) return;
        String why = Emotes.play(list.get(i).id);
        if (why == null) {
            Compat.setScreen(null);
        } else {
            error = why;
            errorAt = System.currentTimeMillis();
            holding = false;
        }
    }

    @Override
    protected void renderBackground(GuiGraphics g) {
        // keine Unschärfe – man soll die Welt (und sich selbst) weiter sehen
        g.fill(0, 0, width, height, 0x40070510);
    }

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        if (event.button() == 0 && hover >= 0) choose(hover);
        else Compat.setScreen(null);
        return true;
    }

    @Override
    protected boolean shortcut(int key, boolean ctrl) {
        // 1–8 wählen direkt (Escape, Rechtsklick oder Klick daneben schließen)
        if (key >= 2 && key <= 9) { // LWJGL 2: Tasten 1–8
            choose(key - 2);
            return true;
        }
        return false;
    }
}
