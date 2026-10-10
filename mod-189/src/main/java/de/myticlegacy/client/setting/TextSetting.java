package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

import java.util.function.Supplier;

/** Freier Text, im Mod-Menü direkt eintippbar (z. B. für Auto Text). hint: kleiner Zusatz hinter der Bezeichnung. */
public class TextSetting extends Setting<String> {
    public static final int MAX = 100;
    private final Supplier<String> hint;

    public TextSetting(Module module, String key, String label, String defaultValue, Supplier<String> hint) {
        super(module, key, label, defaultValue);
        this.hint = hint;
    }

    @Override
    public String get() {
        Object raw = raw();
        return raw instanceof String ? (String) raw : defaultValue;
    }

    @Override
    public void set(String value) {
        store(value.length() > MAX ? value.substring(0, MAX) : value);
    }

    public String hint() {
        return hint == null ? "" : hint.get();
    }
}
