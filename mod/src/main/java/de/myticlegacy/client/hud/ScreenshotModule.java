package de.myticlegacy.client.hud;

import de.myticlegacy.client.compat.Tex;
import de.myticlegacy.client.gui.Shots;
import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;

/** Vorschau nach einem Screenshot (F2): kleines Bild für ein paar Sekunden, optional automatisch kopiert */
public class ScreenshotModule extends HudModule {
    private static final long SHOW_MS = 6000;
    private static final int W = 128, IMG_H = 72;
    public final BoolSetting autoCopy;

    public ScreenshotModule() {
        super("screenshot", "Screenshot-Vorschau", "Nach F2 kurz eine Vorschau zeigen, auf Wunsch gleich kopieren", () -> Items.PAINTING, true, 10000, 200);
        autoCopy = new BoolSetting(this, "autoCopy", "Automatisch in die Zwischenablage kopieren", false);
    }

    private boolean showing() {
        return Shots.latest != null && System.currentTimeMillis() - Shots.latestAt < SHOW_MS;
    }

    @Override
    public boolean hasContent() {
        return showing();
    }

    @Override
    public int baseWidth() {
        return W;
    }

    @Override
    public int baseHeight() {
        return IMG_H + 18;
    }

    public void tick() {
        Shots.tick(enabled() && autoCopy.get());
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        if (!showing() && !preview) return;
        int h = baseHeight();
        panel(g, W, h);
        if (!background.get()) g.fill(0, 0, W, h, 0x99000000);
        Shots.Thumb t = showing() ? Shots.thumb(Shots.latest, Shots.px((W - 6) * scaleFactor())) : null;
        int ix = 3, iy = 3, iw = W - 6, ih = IMG_H - 4;
        if (t != null) {
            int dh = Math.min(ih, t.h() * iw / t.w());
            Tex.draw(g, t.texture(), ix, iy + (ih - dh) / 2, iw, dh, t.w(), t.h());
        } else {
            g.fill(ix, iy, ix + iw, iy + ih, 0x55FFFFFF);
            g.drawString(Ui.font(), "…", W / 2 - 4, iy + ih / 2 - 4, 0xFFFFFFFF, false);
        }
        String note = showing() ? Shots.latestNote : "Gespeichert ✓";
        g.drawString(Ui.font(), Ui.font().plainSubstrByWidth(note, W - 8), 4, IMG_H + 5, Ui.accent(), textShadow());
    }
}
