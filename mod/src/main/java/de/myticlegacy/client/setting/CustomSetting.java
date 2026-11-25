package de.myticlegacy.client.setting;

import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.module.Module;

/**
 * Einstellung mit eigenem Bedienelement im Detailbereich des Mod-Menüs (z. B. Item-Auswahl). Höhe in Design-Pixeln;
 * 0 = gerade ausgeblendet.
 */
public abstract class CustomSetting<T> extends Setting<T> {
    protected CustomSetting(Module module, String key, String label, T defaultValue) {
        super(module, key, label, defaultValue);
    }

    /** Höhe im Detailbereich (Design-Pixel) */
    public abstract float height();

    /** Zeichnen bei (x, y) mit Breite w (Einheiten des Kits) */
    public abstract void draw(Kit kit, float x, float y, float w);
}
