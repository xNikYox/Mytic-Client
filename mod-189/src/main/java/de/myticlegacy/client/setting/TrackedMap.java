package de.myticlegacy.client.setting;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Map der Konfiguration (an/aus der Module, Einstellungen), die jede Änderung meldet: Ein gemeinsamer Zähler steigt nach
 * jedem Schreiben – auch über keySet()/entrySet()/values() und Entry.setValue(). Module und Einstellungen merken sich
 * ihren gelesenen Wert samt Zählerstand und lesen erst nach einer Änderung neu (statt in jedem Bild nachzuschlagen).
 * Reihenfolge wie LinkedHashMap; Gson speichert sie wie jede andere Map.
 */
public final class TrackedMap<V> extends AbstractMap<String, V> {
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private final LinkedHashMap<String, V> map;
    private Set<Map.Entry<String, V>> entries;

    public TrackedMap() {
        map = new LinkedHashMap<String, V>();
    }

    public TrackedMap(Map<String, ? extends V> source) {
        map = source == null ? new LinkedHashMap<String, V>() : new LinkedHashMap<String, V>(source);
        changed();
    }

    /** Als TrackedMap übernehmen (unverändert, falls es schon eine ist) */
    public static <V> Map<String, V> of(Map<String, V> source) {
        return source instanceof TrackedMap ? source : new TrackedMap<V>(source);
    }

    /** Aktueller Zählerstand; ändert sich nach jedem Schreiben in eine TrackedMap */
    public static int generation() {
        return GENERATION.get();
    }

    /** Gemerkte Werte verwerfen (nach einer Änderung, die nicht über eine TrackedMap lief) */
    public static void changed() {
        GENERATION.incrementAndGet();
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    // Erst ändern, dann den Zähler erhöhen: wer den neuen Zählerstand sieht, liest auch den neuen Wert.
    @Override
    public V put(String key, V value) {
        V old = map.put(key, value);
        changed();
        return old;
    }

    @Override
    public V remove(Object key) {
        if (!map.containsKey(key)) return null;
        V old = map.remove(key);
        changed();
        return old;
    }

    @Override
    public void clear() {
        if (map.isEmpty()) return;
        map.clear();
        changed();
    }

    @Override
    public Set<Map.Entry<String, V>> entrySet() {
        if (entries == null) entries = new AbstractSet<Map.Entry<String, V>>() {
            @Override
            public Iterator<Map.Entry<String, V>> iterator() {
                final Iterator<Map.Entry<String, V>> it = map.entrySet().iterator();
                return new Iterator<Map.Entry<String, V>>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Map.Entry<String, V> next() {
                        final Map.Entry<String, V> e = it.next();
                        return new Map.Entry<String, V>() {
                            @Override
                            public String getKey() {
                                return e.getKey();
                            }

                            @Override
                            public V getValue() {
                                return e.getValue();
                            }

                            @Override
                            public V setValue(V value) {
                                V old = e.setValue(value);
                                changed();
                                return old;
                            }

                            @Override
                            public boolean equals(Object o) {
                                return e.equals(o);
                            }

                            @Override
                            public int hashCode() {
                                return e.hashCode();
                            }

                            @Override
                            public String toString() {
                                return e.toString();
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        it.remove();
                        changed();
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public void clear() {
                TrackedMap.this.clear();
            }
        };
        return entries;
    }
}
