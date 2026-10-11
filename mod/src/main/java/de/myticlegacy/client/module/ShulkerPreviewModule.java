package de.myticlegacy.client.module;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.mixin.AbstractContainerScreenAccessor;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/** Shulker-Vorschau wie bei Lunar: Inhalt einer Shulker-Kiste als Raster beim Drüberfahren im Inventar. */
public class ShulkerPreviewModule extends Module {
    public final BoolSetting shiftOnly;
    public final BoolSetting tint;

    public ShulkerPreviewModule() {
        super("shulkerpreview", "Shulker-Vorschau", "Zeigt den Inhalt von Shulker-Kisten beim Drüberfahren im Inventar", Category.VISUELL, () -> Items.SHULKER_BOX, true);
        shiftOnly = new BoolSetting(this, "shiftOnly", "Nur mit gedrückter Shift-Taste", false);
        tint = new BoolSetting(this, "tint", "In der Farbe der Kiste", true);
    }

    private boolean active() {
        return enabled() && (!shiftOnly.get() || Compat.shiftDown());
    }

    private static ShulkerBoxBlock shulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock box ? box : null;
    }

    /** Inhalt (27 Plätze) oder null, wenn es keine Shulker-Kiste ist oder sie leer ist. */
    private static NonNullList<ItemStack> contents(ItemStack stack) {
        if (shulker(stack) == null) return null;
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) return null;
        NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
        container.copyInto(list);
        for (ItemStack s : list) if (!s.isEmpty()) return list;
        return null;
    }

    /** Vom normalen Tooltip nur den Namen behalten – den Inhalt zeigt das Raster. */
    public void trimTooltip(ItemStack stack, List<Component> lines) {
        if (lines.size() > 1 && active() && contents(stack) != null) lines.subList(1, lines.size()).clear();
    }

    /** Nach dem Zeichnen eines Inventars: Raster unter dem Tooltip der Kiste unter der Maus. */
    public void draw(Screen screen, GuiGraphics g, int mouseX, int mouseY) {
        if (!active() || !(screen instanceof AbstractContainerScreen<?>)) return;
        Slot slot = ((AbstractContainerScreenAccessor) screen).mytic$hoveredSlot();
        if (slot == null || !slot.hasItem()) return;
        ItemStack box = slot.getItem();
        NonNullList<ItemStack> items = contents(box);
        if (items == null) return;

        int w = 9 * 18 + 8;
        int h = 3 * 18 + 8;
        int x = mouseX + 12;
        int y = mouseY + 2;
        if (x + w > screen.width - 2) x = Math.max(2, mouseX - 12 - w);
        if (y + h > screen.height - 2) y = Math.max(2, screen.height - 2 - h);

        DyeColor dye = shulker(box).getColor();
        int color = 0xFF000000 | (dye != null ? dye.getTextureDiffuseColor() : 0x8E6B8E);
        if (!tint.get()) color = 0xFF8B8B8B;
        int bg = Ui.mix(0xF0100A18, color, 0.22f) | 0xF0000000;
        int line = Ui.mix(color, 0xFFFFFFFF, 0.25f);

        var font = Minecraft.getInstance().font;
        Gfx.push(g);
        Gfx.raise(g, 400);
        Ui.rect(g, x - 1, y - 1, w + 2, h + 2, 4, Ui.withAlpha(line, 220));
        Ui.rect(g, x, y, w, h, 3, bg);
        for (int i = 0; i < 27; i++) {
            int sx = x + 4 + (i % 9) * 18;
            int sy = y + 4 + (i / 9) * 18;
            g.fill(sx, sy, sx + 16, sy + 16, Ui.withAlpha(0x000000, 70));
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            g.renderItem(s, sx, sy);
            if (s.getCount() > 1) {
                String n = String.valueOf(s.getCount());
                Gfx.push(g);
                Gfx.raise(g, 200);
                g.drawString(font, n, sx + 17 - font.width(n), sy + 9, 0xFFFFFFFF, true);
                Gfx.pop(g);
            }
        }
        Gfx.pop(g);
    }
}
