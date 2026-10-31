package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

import java.util.Arrays;

/** Auswahl aus festen Optionen, z. B. Crosshair-Stil. */
public class ModeSetting extends Setting<String> {
    public final String[] options;

    public ModeSetting(Module module, String key, String label, String defaultValue, String... options) {
        super(module, key, label, defaultValue);
        this.options = options;
    }

    @Override
    public String get() {
        return raw() instanceof String s && Arrays.asList(options).contains(s) ? s : defaultValue;
    }

    @Override
    public Object sanitize(Object value) {
        return value instanceof String s && Arrays.asList(options).contains(s) ? s : null;
    }

    public boolean is(String option) {
        return get().equals(option);
    }

    public void cycle(int direction) {
        int index = Arrays.asList(options).indexOf(get());
        set(options[Math.floorMod(index + direction, options.length)]);
    }
}
