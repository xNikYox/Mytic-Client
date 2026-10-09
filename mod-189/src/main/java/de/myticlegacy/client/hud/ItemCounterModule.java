package de.myticlegacy.client.hud;

import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.module.Module;
import net.minecraft.client.Minecraft;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

import java.util.ArrayList;
import java.util.List;

/** Zählt wichtige Items im Inventar: Pfeile, Enderperlen, Goldäpfel, Totems, Erfahrungsfläschchen. */
public class ItemCounterModule extends HudModule {
    private static final class Entry {
        final BoolSetting setting;
        final Item item;

        Entry(BoolSetting setting, Item item) {
            this.setting = setting;
            this.item = item;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();

    public ItemCounterModule() {
        super("items", "Item-Zähler", "Pfeile, Perlen und Goldäpfel im Inventar", () -> Items.arrow, false, 10000, 120);
        entries.add(new Entry(new BoolSetting(this, "arrows", "Pfeile", true), Items.arrow));
        entries.add(new Entry(new BoolSetting(this, "pearls", "Enderperlen", true), Items.ender_pearl));
        entries.add(new Entry(new BoolSetting(this, "gapples", "Goldäpfel", true), Items.golden_apple));
        entries.add(new Entry(new BoolSetting(this, "xp", "Erfahrungsfläschchen", false), Items.experience_bottle));
    }

    private static final class Count {
        final Item item;
        final int amount;

        Count(Item item, int amount) {
            this.item = item;
            this.amount = amount;
        }
    }

    private List<Count> counts(boolean preview) {
        net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        List<Count> list = new ArrayList<Count>();
        for (Entry entry : entries) {
            if (!entry.setting.get()) continue;
            int amount = 0;
            if (player != null) {
                for (ItemStack stack : player.inventory.mainInventory) {
                    if (stack != null && stack.getItem() == entry.item) amount += stack.stackSize;
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
        de.myticlegacy.client.compat.Font font = de.myticlegacy.client.gui.Ui.font();
        for (int i = 0; i < list.size(); i++) {
            int y = 1 + i * 18;
            g.renderItem(Module.stack(list.get(i).item), 2, y);
            g.drawString(font, String.valueOf(list.get(i).amount), 21, y + 5, color(), textShadow());
        }
    }
}
