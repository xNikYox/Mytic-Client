package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

/** Farbe aus einer festen Palette (ARGB). */
public class ColorSetting extends Setting<Integer> {
    public static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFB98BFF, 0xFF9B5CFF, 0xFF2FD0C0, 0xFF4DA3FF, 0xFF5BE38A,
            0xFFFFD84A, 0xFFFF9F43, 0xFFFF5C72, 0xFFFF6FB5, 0xFFA0A0B0, 0xFF000000};

    public ColorSetting(Module module, String key, String label, int defaultValue) {
        super(module, key, label, defaultValue);
    }

    @Override
    public Integer get() {
        return raw() instanceof Number n ? (int) n.longValue() : defaultValue;
    }

    @Override
    public void set(Integer value) {
        store((double) value);
    }
}
