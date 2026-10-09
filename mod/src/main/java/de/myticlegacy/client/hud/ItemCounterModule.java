package de.myticlegacy.client.hud;

import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Zählt wichtige Items im Inventar: Pfeile, Enderperlen, Goldäpfel, Totems, Erfahrungsfläschchen. */
public class ItemCounterModule extends HudModule {
    private record Entry(BoolSetting setting, Item item) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public ItemCounterModule() {
        super("items", "Item-Zähler", "Pfeile, Perlen, Goldäpfel und Totems im Inventar", () -> Items.ARROW, false, 10000, 120);
        entries.add(new Entry(new BoolSetting(this, "arrows", "Pfeile", true), Items.ARROW));
        entries.add(new Entry(new BoolSetting(this, "pearls", "Enderperlen", true), Items.ENDER_PEARL));
        entries.add(new Entry(new BoolSetting(this, "gapples", "Goldäpfel", true), Items.GOLDEN_APPLE));
        entries.add(new Entry(new BoolSetting(this, "totems", "Totems", true), Items.TOTEM_OF_UNDYING));
        entries.add(new Entry(new BoolSetting(this, "xp", "Erfahrungsfläschchen", false), Items.EXPERIENCE_BOTTLE));
    }

    private record Count(Item item, int amount) {
    }

    private List<Count> counts(boolean preview) {
        var player = Minecraft.getInstance().player;
        List<Count> list = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.setting.get()) continue;
            int amount = 0;
            if (player != null) {
                var inventory = player.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack.is(entry.item)) amount += stack.getCount();
                }
            }
            if (amount > 0 || preview) list.add(new Count(entry.item, amount > 0 ? amount : 16));
        }
        return list;
    }

    @Override
    public boolean hasContent() {
        return !counts(false).isEmpty();
    }

    @Override
    public int baseWidth() {
        return 46;
    }

    @Override
    public int baseHeight() {
        return Math.max(1, counts(true).size()) * 18 + 2;
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        List<Count> list = counts(preview);
        if (list.isEmpty()) return;
        panel(g, baseWidth(), list.size() * 18 + 2);
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < list.size(); i++) {
            int y = 1 + i * 18;
            g.renderItem(Module.stack(list.get(i).item), 2, y);
            g.drawString(font, String.valueOf(list.get(i).amount), 21, y + 5, color(), textShadow());
        }
    }
}
