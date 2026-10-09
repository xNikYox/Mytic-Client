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
        Object raw = raw();
        return raw instanceof String && Arrays.asList(options).contains(raw) ? (String) raw : defaultValue;
    }

    public boolean is(String option) {
        return get().equals(option);
    }

    public void cycle(int direction) {
        int index = Arrays.asList(options).indexOf(get());
        set(options[Math.floorMod(index + direction, options.length)]);
    }
}
