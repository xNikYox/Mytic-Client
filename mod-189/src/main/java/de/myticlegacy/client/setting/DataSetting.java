package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

/** Interner Text-Wert (z. B. Pixelbild des Fadenkreuzes): gespeichert und synchronisiert, aber ohne eigene Zeile im Menü. */
public class DataSetting extends Setting<String> {
    private final int max;

    public DataSetting(Module module, String key, String defaultValue, int max) {
        super(module, key, key, defaultValue);
        this.max = max;
    }

    @Override
    public String get() {
        return cachedValue();
    }

    @Override
    protected String convert(Object raw) {
        return raw instanceof String && ((String) raw).length() <= max ? (String) raw : defaultValue;
    }

    @Override
    public boolean visible() {
        return false;
    }
}
