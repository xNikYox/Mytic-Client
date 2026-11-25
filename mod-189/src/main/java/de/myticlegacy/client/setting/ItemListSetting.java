package de.myticlegacy.client.setting;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.module.Module;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Liste von Items mit Auswahl im Mod-Menü (1.8.9). Kennung "minecraft:golden_apple" (alle Arten) bzw. mit Unterart
 * "minecraft:golden_apple@1" (verzauberter Goldapfel) oder "minecraft:potion@16421" (Wurftrank der Heilung II).
 */
public class ItemListSetting extends CustomSetting<List<String>> {
    private static final float CELL = 36, GAP = 6, GRID_ROWS = 4;
    public final int max;
    private final Visible visible;

    public interface Visible {
        boolean get();
    }

    private boolean open;
    private int page;
    private final Kit.Field search = new Kit.Field(30);
    private String lastQuery = "";
    private List<ItemStack> filtered;
    private static List<ItemStack> all;
    private static List<String> names;

    public ItemListSetting(Module module, String key, String label, int max, Visible visible, String... defaults) {
        super(module, key, label, Arrays.asList(defaults));
        this.max = max;
        this.visible = visible;
    }

    @Override
    public List<String> get() {
        Object raw = raw();
        if (!(raw instanceof List)) return new ArrayList<String>(defaultValue);
        List<String> out = new ArrayList<String>();
        for (Object o : (List<?>) raw) if (o instanceof String && !out.contains(o) && out.size() < max) out.add((String) o);
        return out;
    }

    @Override
    public void set(List<String> value) {
        store(new ArrayList<String>(value));
    }

    // ------------------------------------------------------------------------------------------ Kennungen

    public static String id(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation rl = (ResourceLocation) Item.itemRegistry.getNameForObject(item);
        String base = rl == null ? "?" : rl.toString();
        return item.getHasSubtypes() ? base + "@" + stack.getMetadata() : base;
    }

    /** Stack zum Anzeigen oder null */
    public static ItemStack stack(String id) {
        if (id == null) return null;
        int at = id.indexOf('@');
        String name = at >= 0 ? id.substring(0, at) : id;
        Item item = Item.getByNameOrId(name);
        if (item == null) return null;
        int meta = 0;
        if (at >= 0) {
            try {
                meta = Integer.parseInt(id.substring(at + 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new ItemStack(item, 1, meta);
    }

    /** Passt ein Stack zur Kennung? (ohne Unterart: jede Unterart) */
    public static boolean matches(String id, ItemStack s) {
        if (s == null || s.getItem() == null) return false;
        int at = id.indexOf('@');
        Item item = Item.getByNameOrId(at >= 0 ? id.substring(0, at) : id);
        if (item == null || s.getItem() != item) return false;
        if (at < 0) return true;
        try {
            return s.getMetadata() == Integer.parseInt(id.substring(at + 1));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void toggle(ItemStack stack) {
        List<String> list = get();
        String id = id(stack);
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
        if (!visible.get()) return 0;
        return 44 + 2 * (CELL + GAP) + 8 + (open ? 40 + 12 + GRID_ROWS * (CELL + GAP) + 34 : 38) + 14;
    }

    @SuppressWarnings("unchecked")
    private static void ensureItems() {
        if (all != null) return;
        all = new ArrayList<ItemStack>();
        names = new ArrayList<String>();
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            List<ItemStack> subs = new ArrayList<ItemStack>();
            try {
                item.getSubItems(item, item.getCreativeTab(), subs);
            } catch (RuntimeException ignored) {
                // manche Items mögen keinen leeren Reiter
            }
            if (subs.isEmpty()) subs.add(new ItemStack(item));
            for (ItemStack s : subs) {
                if (s == null || s.getItem() == null) continue;
                all.add(s);
                String name;
                try {
                    name = s.getDisplayName();
                } catch (RuntimeException e) {
                    name = "";
                }
                ResourceLocation rl = (ResourceLocation) Item.itemRegistry.getNameForObject(item);
                names.add((name + " " + (rl == null ? "" : rl.getResourcePath())).toLowerCase(Locale.ROOT));
            }
        }
    }

    private List<ItemStack> filtered() {
        ensureItems();
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        if (filtered == null || !q.equals(lastQuery)) {
            lastQuery = q;
            page = 0;
            if (q.isEmpty()) {
                filtered = all;
            } else {
                filtered = new ArrayList<ItemStack>();
                for (int i = 0; i < all.size(); i++) if (names.get(i).contains(q)) filtered.add(all.get(i));
            }
        }
        return filtered;
    }

    private static String name(ItemStack s) {
        try {
            return s.getDisplayName();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    @Override
    public void draw(final Kit kit, float x, float y, float w) {
        if (!visible.get()) return;
        float d = kit.d;
        String id = "il:" + path();
        List<String> list = get();
        kit.text(label, x, y + 14 * d, 15, Tokens.MEDIUM, Tokens.TEXT);
        kit.textRight(list.size() + "/" + max, x + w, y + 14 * d + 5 * d, 13, Tokens.SEMIBOLD, list.size() >= max ? Tokens.WARN : Tokens.SUBTLE);
        float cy = y + 40 * d, cell = CELL * d, gap = GAP * d;
        int perRow = Math.max(1, (int) ((w + gap) / (cell + gap)));
        if (list.isEmpty()) kit.textMid("Noch keine Items gewählt", x, cy + cell / 2, 14, Tokens.MEDIUM, Tokens.SUBTLE, w);
        for (int i = 0; i < list.size(); i++) {
            if (i / perRow >= 2) break;
            final String itemId = list.get(i);
            ItemStack stack = stack(itemId);
            float cx = x + (i % perRow) * (cell + gap), ccy = cy + (i / perRow) * (cell + gap);
            boolean hv = kit.hit(id + ":sel" + i, cx, ccy, cell, cell, new Runnable() {
                public void run() {
                    List<String> l = get();
                    l.remove(itemId);
                    set(l);
                    MyticClient.config().save();
                }
            });
            Draw.glass(kit.g, cx, ccy, cell, cell, 10 * d, hv ? 0x29FF5C72 : Tokens.SURFACE, hv ? 0x99FF5C72 : Tokens.ACC_LINE);
            if (stack != null) item(kit, stack, cx + cell / 2, ccy + cell / 2, cell * 0.6f);
            else Icons.draw(kit.g, "info", cx + cell / 2 - 9 * d, ccy + cell / 2 - 9 * d, 18 * d, Tokens.SUBTLE);
            if (hv) {
                Icons.draw(kit.g, "x", cx + cell - 13 * d, ccy + 1 * d, 12 * d, Tokens.BAD);
                kit.tooltip((stack != null ? name(stack) : itemId) + " entfernen", cx + cell / 2, ccy + cell + 6 * d);
            }
        }
        float by = cy + 2 * (cell + gap) + 8 * d;
        if (!open) {
            kit.button(id + ":open", x, by, w, 34 * d, "Items hinzufügen", "plus", Widgets.SECONDARY, new Runnable() {
                public void run() {
                    open = true;
                }
            });
            return;
        }
        kit.field(id + ":search", search, x, by, w - 46 * d, 40 * d, "Items suchen, z. B. Perle oder Apfel", "search");
        kit.iconButton(id + ":close", x + w - 40 * d, by, 40 * d, "chevD", true, new Runnable() {
            public void run() {
                open = false;
            }
        });
        List<ItemStack> items = filtered();
        float gy = by + 40 * d + 12 * d;
        int cols = perRow, rows = (int) GRID_ROWS, per = cols * rows;
        int pages = Math.max(1, (items.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < per && page * per + i < items.size(); i++) {
            final ItemStack stack = items.get(page * per + i);
            float gx = x + (i % cols) * (cell + gap), gyy = gy + (i / cols) * (cell + gap);
            boolean sel = list.contains(id(stack));
            boolean hv = kit.hit(id + ":g" + i, gx, gyy, cell, cell, new Runnable() {
                public void run() {
                    toggle(stack);
                }
            });
            Draw.glass(kit.g, gx, gyy, cell, cell, 10 * d, hv ? Tokens.SURFACE_HOVER : Tokens.SURFACE, sel ? Tokens.ACC_LINE : hv ? Tokens.LINE_STRONG : Tokens.LINE);
            if (sel) Draw.rect(kit.g, gx + 1, gyy + 1, cell - 2, cell - 2, 9 * d, Tokens.ACC_SOFT);
            item(kit, stack, gx + cell / 2, gyy + cell / 2, cell * 0.6f);
            if (hv) kit.tooltip(name(stack) + (sel ? " entfernen" : list.size() >= max ? " (Liste voll)" : " hinzufügen"), gx + cell / 2, gyy + cell + 6 * d);
        }
        if (items.isEmpty()) kit.textMid("Keine Items gefunden", x, gy + cell / 2, 14, Tokens.MEDIUM, Tokens.SUBTLE, w);
        float py = gy + rows * (cell + gap) + 2 * d;
        kit.iconButton(id + ":prev", x, py, 30 * d, "chevL", false, new Runnable() {
            public void run() {
                page = Math.max(0, page - 1);
            }
        });
        kit.iconButton(id + ":next", x + w - 30 * d, py, 30 * d, "chevR", false, new Runnable() {
            public void run() {
                page++;
            }
        });
        kit.textCenter("Seite " + (page + 1) + " von " + pages + " · " + items.size() + " Items", x + w / 2, py + 15 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE);
    }

    public static void item(Kit kit, ItemStack stack, float cx, float cy, float size) {
        if (stack == null) return;
        Gfx.push(kit.g);
        Gfx.translate(kit.g, cx - size / 2f, cy - size / 2f);
        Gfx.scale(kit.g, size / 16f, size / 16f);
        kit.g.renderItem(stack, 0, 0);
        Gfx.pop(kit.g);
    }
}
