package de.myticlegacy.client.hud;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.module.Module;
import net.minecraft.client.Minecraft;
import de.myticlegacy.client.compat.GuiGraphics;
import de.myticlegacy.client.compat.Font;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

import java.util.ArrayList;
import java.util.List;

/** Rüstung und Item in der Hand mit verbleibender Haltbarkeit, senkrecht oder waagerecht. */
public class ArmorModule extends HudModule {
    /** Rüstungsplätze in 1.8.9: 3 = Helm … 0 = Schuhe, -1 = Hand. */
    private static final int[] SLOTS = {3, 2, 1, 0, -1};

    private final ModeSetting layout;
    private final BoolSetting showHand;
    private final ModeSetting durability;

    public ArmorModule() {
        super("armor", "Rüstung", "Rüstung und Haltbarkeit", () -> Items.diamond_chestplate, true, 4, 146);
        layout = new ModeSetting(this, "layout", "Anordnung", "Senkrecht", "Senkrecht", "Waagerecht");
        showHand = new BoolSetting(this, "hand", "Item in der Hand", true);
        durability = new ModeSetting(this, "durability", "Haltbarkeit", "Zahl", "Zahl", "Prozent", "Aus");
    }

    @Override
    protected boolean defaultBackground() {
        return false;
    }

    private List<ItemStack> stacks(boolean preview) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        List<ItemStack> list = new ArrayList<ItemStack>();
        ItemStack[] demo = {Module.stack(Items.diamond_helmet), Module.stack(Items.diamond_chestplate), Module.stack(Items.diamond_leggings),
                Module.stack(Items.diamond_boots), Module.stack(Items.diamond_sword)};
        for (int i = 0; i < SLOTS.length; i++) {
            if (i == 4 && !showHand.get()) continue;
            ItemStack stack = player == null ? null : SLOTS[i] < 0 ? player.getHeldItem() : player.inventory.armorInventory[SLOTS[i]];
            if (stack == null && (preview || player == null)) stack = demo[i];
            if (stack != null) list.add(stack);
        }
        return list;
    }

    private boolean horizontal() {
        return layout.is("Waagerecht");
    }

    @Override
    public boolean hasContent() {
        return !stacks(false).isEmpty();
    }

    @Override
    public int baseWidth() {
        return horizontal() ? 5 * 20 + 4 : (durability.is("Aus") ? 20 : 60);
    }

    @Override
    public int baseHeight() {
        return horizontal() ? (durability.is("Aus") ? 20 : 30) : 5 * 18 + 2;
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        Font font = Ui.font();
        panel(g, baseWidth(), baseHeight());
        List<ItemStack> list = stacks(preview);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            int x = horizontal() ? 2 + i * 20 : 2;
            int y = horizontal() ? 2 : 1 + i * 18;
            g.renderItem(stack, x, y);
            String text = "";
            int color = color();
            if (stack.isItemStackDamageable() && !durability.is("Aus")) {
                int left = stack.getMaxDamage() - stack.getItemDamage();
                float ratio = left / (float) stack.getMaxDamage();
                text = durability.is("Prozent") ? Math.round(ratio * 100) + "%" : String.valueOf(left);
                color = ratio > 0.5f ? 0xFF7CF29A : ratio > 0.2f ? 0xFFFFD24A : 0xFFFF5C72;
            } else if (stack.stackSize > 1) {
                text = String.valueOf(stack.stackSize);
            }
            if (text.isEmpty()) continue;
            if (horizontal()) {
                Gfx.push(g);
                Gfx.translate(g, x + 8, y + 18);
                Gfx.scale(g, 0.75f, 0.75f);
                g.drawString(font, text, -font.width(text) / 2, 0, color, textShadow());
                Gfx.pop(g);
            } else {
                g.drawString(font, text, x + 20, y + 4, color, textShadow());
            }
        }
    }
}
