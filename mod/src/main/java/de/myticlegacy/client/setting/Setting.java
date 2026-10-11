package de.myticlegacy.client.setting;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.module.Module;

/** Eine Einstellung eines Moduls. Wird automatisch beim Modul registriert und in der Konfiguration gespeichert. */
public abstract class Setting<T> {
    public final Module module;
    public final String key;
    public final String label;
    protected final T defaultValue;
    /** Schlüssel in der Konfiguration – einmal gebildet, weil Einstellungen in jedem Bild oft gelesen werden. */
    private final String path;

    protected Setting(Module module, String key, String label, T defaultValue) {
        this.module = module;
        this.key = key;
        this.label = label;
        this.defaultValue = defaultValue;
        this.path = module.id + "." + key;
        module.settings.add(this);
    }

    protected String path() {
        return path;
    }

    protected Object raw() {
        return MyticClient.config().settings.get(path());
    }

    protected void store(Object value) {
        MyticClient.config().settings.put(path(), value);
    }

    public abstract T get();

    public void set(T value) {
        store(value);
    }

    public void reset() {
        MyticClient.config().settings.remove(path());
    }
}
