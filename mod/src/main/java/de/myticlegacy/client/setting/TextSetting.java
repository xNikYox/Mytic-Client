package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

import java.util.function.Supplier;

/** Freier Text, im Mod-Menü direkt eintippbar (z. B. für Auto Text). hint: kleiner Zusatz hinter der Bezeichnung. */
public class TextSetting extends Setting<String> {
    public static final int MAX = 100;
    private final Supplier<String> hint;
    /** Höchstlänge dieses Felds (Standard {@link #MAX}) */
    public final int max;

    public TextSetting(Module module, String key, String label, String defaultValue, Supplier<String> hint) {
        this(module, key, label, defaultValue, hint, MAX);
    }

    public TextSetting(Module module, String key, String label, String defaultValue, Supplier<String> hint, int max) {
        super(module, key, label, defaultValue);
        this.hint = hint;
        this.max = max;
    }

    @Override
    public String get() {
        Object raw = raw();
        return raw instanceof String ? clip((String) raw) : defaultValue; // Länge auch für geladene Werte begrenzen
    }

    @Override
    public void set(String value) {
        store(clip(value));
    }

    @Override
    public Object sanitize(Object value) {
        return value instanceof String ? clip((String) value) : null;
    }

    private String clip(String value) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    public String hint() {
        return hint == null ? "" : hint.get();
    }
}
