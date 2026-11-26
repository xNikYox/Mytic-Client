package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.Setting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Ein ein- und ausschaltbares Feature des Clients mit optionalen Einstellungen. */
public class Module {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    private final Supplier<Item> icon;
    private final boolean enabledByDefault;
    private ItemStack iconStack;

    public Module(String id, String name, String description, Category category, Supplier<Item> icon, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.enabledByDefault = enabledByDefault;
    }

    /** Gemerkter Zustand (an/aus) samt Zählerstand der Konfiguration – enabled() wird pro Bild sehr oft gefragt */
    private static final class Cached {
        final int generation;
        final Object map;
        final boolean on;

        Cached(int generation, Object map, boolean on) {
            this.generation = generation;
            this.map = map;
            this.on = on;
        }
    }

    private volatile Cached cached;

    public boolean enabled() {
        java.util.Map<String, Boolean> map = MyticClient.config().enabled;
        int generation = de.myticlegacy.client.setting.TrackedMap.generation();
        Cached c = cached;
        if (c != null && c.generation == generation && c.map == map) return c.on;
        boolean on = map.getOrDefault(id, enabledByDefault);
        cached = new Cached(generation, map, on);
        return on;
    }

    public void setEnabled(boolean value) {
        MyticClient.config().enabled.put(id, value);
        MyticClient.config().save();
    }

    public void toggle() {
        setEnabled(!enabled());
    }

    public ItemStack icon() {
        if (iconStack == null) iconStack = stack(icon.get());
        return iconStack;
    }

    /**
     * ItemStack, ohne abzustürzen: in 1.8.9 ist ein fehlendes Item einfach null.
     */
    public static ItemStack stack(Item item) {
        try {
            return new ItemStack(item);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void resetSettings() {
        settings.forEach(Setting::reset);
        MyticClient.config().save();
    }
}
