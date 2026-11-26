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

    public String path() {
        return path;
    }

    protected Object raw() {
        return MyticClient.config().settings.get(path());
    }

    /** Gemerkter, fertig geprüfter Wert samt Zählerstand der Konfiguration (siehe {@link TrackedMap}) */
    private static final class Cached {
        final int generation;
        final Object map;
        final Object value;

        Cached(int generation, Object map, Object value) {
            this.generation = generation;
            this.map = map;
            this.value = value;
        }
    }

    private volatile Cached cached;

    /**
     * Wert wie {@link #convert(Object)} aus dem gespeicherten Rohwert – aber nur nach einer Änderung neu gelesen, sonst
     * gemerkt (Einstellungen werden pro Bild und pro Entity sehr oft gelesen). Jeder Schreibweg (set, reset, Laden,
     * Cloud-Sync, Profile, HUD-Editor, Import) läuft über eine {@link TrackedMap} bzw. ersetzt sie, beides verwirft den Wert.
     */
    @SuppressWarnings("unchecked")
    protected final T cachedValue() {
        java.util.Map<String, Object> map = MyticClient.config().settings;
        int generation = TrackedMap.generation(); // vor dem Lesen: eine Änderung danach erhöht den Zähler erneut
        Cached c = cached;
        if (c != null && c.generation == generation && c.map == map) return (T) c.value;
        T value = convert(map.get(path));
        cached = new Cached(generation, map, value);
        return value;
    }

    /** Rohwert aus der Konfiguration in den gültigen Wert umwandeln (für {@link #cachedValue()}) */
    protected T convert(Object raw) {
        throw new UnsupportedOperationException();
    }

    protected void store(Object value) {
        MyticClient.config().settings.put(path(), value);
    }

    public abstract T get();

    public void set(T value) {
        store(value);
    }

    /** Im Mod-Menü bzw. in den Einstellungen als Zeile zeigen? (false = nur intern gespeichert, z. B. Pixelbild) */
    public boolean visible() {
        return true;
    }

    public void reset() {
        MyticClient.config().settings.remove(path());
    }
}
