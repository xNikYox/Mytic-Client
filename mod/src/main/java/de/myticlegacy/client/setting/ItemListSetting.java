package de.myticlegacy.client.setting;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.module.Module;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Liste von Items (Kennungen wie "minecraft:totem_of_undying") mit Auswahl im Mod-Menü: gewählte Items als Kacheln
 * (Klick entfernt), darunter aufklappbar Suche und Raster aller Items (Klick fügt hinzu bzw. entfernt).
 */
public class ItemListSetting extends CustomSetting<List<String>> {
    private static final float CELL = 36, GAP = 6, GRID_ROWS = 4;
    public final int max;
    private final BooleanSupplier visible;

    // Zustand der Auswahl (nur im Mod-Menü)
    private boolean open;
    private int page;
    private final Kit.Field search = new Kit.Field(30);
    private String lastQuery = "";
    private List<Item> filtered;
    private static List<Item> all;
    private static List<String> names;

    public ItemListSetting(Module module, String key, String label, int max, BooleanSupplier visible, String... defaults) {
        super(module, key, label, List.of(defaults));
        this.max = max;
        this.visible = visible;
    }

    // ------------------------------------------------------------------------------------------ Wert

    @Override
    public List<String> get() {
        Object raw = raw();
        if (!(raw instanceof List<?> list)) return new ArrayList<>(defaultValue);
        List<String> out = new ArrayList<>();
        for (Object o : list) if (o instanceof String s && !out.contains(s) && out.size() < max) out.add(s);
        return out;
    }

    @Override
    public void set(List<String> value) {
        store(new ArrayList<>(value));
    }

    @Override
    public Object sanitize(Object value) {
        if (!(value instanceof List<?> list)) return null;
        List<String> out = new ArrayList<>();
        for (Object o : list) if (o instanceof String s && s.length() <= 100 && !out.contains(s) && out.size() < max) out.add(s);
        return out;
    }

    public static Item item(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        return item == Items.AIR ? null : item;
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private void toggle(Item item) {
        List<String> list = get();
        String id = id(item);
        if (!list.remove(id)) {
            if (list.size() >= max) return;
            list.add(id);
        }
        set(list);
        MyticClient.config().save();
    }

    // ------------------------------------------------------------------------------------------ Bedienelement

    @Override
    public float height() {
        if (!visible.getAsBoolean()) return 0;
        // Kopf + zwei Reihen gewählter Items + Knopf bzw. Suche, Raster und Seitenwahl
        return 44 + 2 * (CELL + GAP) + 8 + (open ? 40 + 12 + GRID_ROWS * (CELL + GAP) + 34 : 38) + 14;
    }

    private static void ensureItems() {
        if (all != null) return;
        all = new ArrayList<>();
        names = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            all.add(item);
            String name;
            try {
                name = Module.stack(item).getHoverName().getString();
            } catch (RuntimeException e) {
                name = "";
            }
            names.add((name + " " + BuiltInRegistries.ITEM.getKey(item).getPath()).toLowerCase(Locale.ROOT));
        }
    }

    private List<Item> filtered() {
        ensureItems();
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        if (filtered == null || !q.equals(lastQuery)) {
            lastQuery = q;
            page = 0;
            if (q.isEmpty()) {
                filtered = all;
            } else {
                filtered = new ArrayList<>();
                for (int i = 0; i < all.size(); i++) if (names.get(i).contains(q)) filtered.add(all.get(i));
            }
        }
        return filtered;
    }

    @Override
    public void draw(Kit kit, float x, float y, float w) {
        if (!visible.getAsBoolean()) return;
        float d = kit.d;
        String id = "il:" + path();
        List<String> list = get();
        kit.text(label, x, y + 14 * d, 15, Tokens.MEDIUM, Tokens.TEXT);
        kit.textRight(list.size() + "/" + max, x + w, y + 14 * d + 5 * d, 13, Tokens.SEMIBOLD, list.size() >= max ? Tokens.WARN : Tokens.SUBTLE);
        float cy = y + 40 * d, cell = CELL * d, gap = GAP * d;
        int perRow = Math.max(1, (int) ((w + gap) / (cell + gap)));
        if (list.isEmpty()) {
            kit.textMid("Noch keine Items gewählt", x, cy + cell / 2, 14, Tokens.MEDIUM, Tokens.SUBTLE, w);
        }
        for (int i = 0; i < list.size(); i++) {
            final String itemId = list.get(i);
            Item item = item(itemId);
            float cx = x + (i % perRow) * (cell + gap), ccy = cy + (i / perRow) * (cell + gap);
            if (i / perRow >= 2) break;
            boolean hv = kit.hit(id + ":sel" + i, cx, ccy, cell, cell, () -> {
                List<String> l = get();
                l.remove(itemId);
                set(l);
                MyticClient.config().save();
            });
            Draw.glass(kit.g, cx, ccy, cell, cell, 10 * d, hv ? 0x29FF5C72 : Tokens.SURFACE, hv ? 0x99FF5C72 : Tokens.ACC_LINE);
            if (item != null) item(kit, Module.stack(item), cx + cell / 2, ccy + cell / 2, cell * 0.6f);
            else Icons.draw(kit.g, "info", cx + cell / 2 - 9 * d, ccy + cell / 2 - 9 * d, 18 * d, Tokens.SUBTLE);
            if (hv) {
                Icons.draw(kit.g, "x", cx + cell - 13 * d, ccy + 1 * d, 12 * d, Tokens.BAD);
                kit.tooltip((item != null ? Module.stack(item).getHoverName().getString() : itemId) + " entfernen", cx + cell / 2, ccy + cell + 6 * d);
            }
        }
        float by = cy + 2 * (cell + gap) + 8 * d;
        if (!open) {
            kit.button(id + ":open", x, by, w, 34 * d, "Items hinzufügen", "plus", Widgets.SECONDARY, () -> open = true);
            return;
        }
        // Suche + Schließen
        kit.field(id + ":search", search, x, by, w - 46 * d, 40 * d, "Items suchen, z. B. Perle oder Apfel", "search");
        kit.iconButton(id + ":close", x + w - 40 * d, by, 40 * d, "chevD", true, () -> open = false);
        List<Item> items = filtered();
        float gy = by + 40 * d + 12 * d;
        int cols = perRow, rows = (int) GRID_ROWS, per = cols * rows;
        int pages = Math.max(1, (items.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < per && page * per + i < items.size(); i++) {
            final Item item = items.get(page * per + i);
            float gx = x + (i % cols) * (cell + gap), gyy = gy + (i / cols) * (cell + gap);
            boolean sel = list.contains(id(item));
            boolean hv = kit.hit(id + ":g" + i, gx, gyy, cell, cell, () -> toggle(item));
            Draw.glass(kit.g, gx, gyy, cell, cell, 10 * d, hv ? Tokens.SURFACE_HOVER : Tokens.SURFACE, sel ? Tokens.ACC_LINE : hv ? Tokens.LINE_STRONG : Tokens.LINE);
            if (sel) Draw.rect(kit.g, gx + 1, gyy + 1, cell - 2, cell - 2, 9 * d, Tokens.ACC_SOFT);
            item(kit, Module.stack(item), gx + cell / 2, gyy + cell / 2, cell * 0.6f);
            if (hv) kit.tooltip(Module.stack(item).getHoverName().getString() + (sel ? " entfernen" : list.size() >= max ? " (Liste voll)" : " hinzufügen"), gx + cell / 2, gyy + cell + 6 * d);
        }
        if (items.isEmpty()) kit.textMid("Keine Items gefunden", x, gy + cell / 2, 14, Tokens.MEDIUM, Tokens.SUBTLE, w);
        float py = gy + rows * (cell + gap) + 2 * d;
        kit.iconButton(id + ":prev", x, py, 30 * d, "chevL", false, () -> page = Math.max(0, page - 1));
        kit.iconButton(id + ":next", x + w - 30 * d, py, 30 * d, "chevR", false, () -> page++);
        kit.textCenter("Seite " + (page + 1) + " von " + pages + " · " + items.size() + " Items", x + w / 2, py + 15 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE);
    }

    /** Item mittig bei cx, cy in Kantenlänge size zeichnen */
    public static void item(Kit kit, ItemStack stack, float cx, float cy, float size) {
        if (stack == null || stack.isEmpty()) return;
        Gfx.push(kit.g);
        Gfx.translate(kit.g, cx - size / 2f, cy - size / 2f);
        Gfx.scale(kit.g, size / 16f, size / 16f);
        kit.g.renderItem(stack, 0, 0);
        Gfx.pop(kit.g);
    }
}
