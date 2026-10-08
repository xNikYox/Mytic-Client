package de.myticlegacy.client.hud;

import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Rüstung und Item in der Hand mit verbleibender Haltbarkeit, senkrecht oder waagerecht. */
public class ArmorModule extends HudModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};

    private final ModeSetting layout;
    private final BoolSetting showHand;
    private final ModeSetting durability;

    public ArmorModule() {
        super("armor", "Rüstung", "Rüstung und Haltbarkeit", () -> Items.DIAMOND_CHESTPLATE, true, 4, 146);
        layout = new ModeSetting(this, "layout", "Anordnung", "Senkrecht", "Senkrecht", "Waagerecht");
        showHand = new BoolSetting(this, "hand", "Item in der Hand", true);
        durability = new ModeSetting(this, "durability", "Haltbarkeit", "Zahl", "Zahl", "Prozent", "Aus");
    }

    @Override
    protected boolean defaultBackground() {
        return false;
    }

    private List<ItemStack> stacks(boolean preview) {
        var player = Minecraft.getInstance().player;
        List<ItemStack> list = new ArrayList<>();
        ItemStack[] demo = {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS),
                new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_SWORD)};
        for (int i = 0; i < SLOTS.length; i++) {
            if (i == 4 && !showHand.get()) continue;
            ItemStack stack = player != null ? player.getItemBySlot(SLOTS[i]) : ItemStack.EMPTY;
            if (stack.isEmpty() && (preview || player == null)) stack = demo[i];
            if (!stack.isEmpty()) list.add(stack);
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
        var font = Minecraft.getInstance().font;
        panel(g, baseWidth(), baseHeight());
        List<ItemStack> list = stacks(preview);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            int x = horizontal() ? 2 + i * 20 : 2;
            int y = horizontal() ? 2 : 1 + i * 18;
            g.renderItem(stack, x, y);
            String text = "";
            int color = color();
            if (stack.isDamageableItem() && !durability.is("Aus")) {
                int left = stack.getMaxDamage() - stack.getDamageValue();
                float ratio = left / (float) stack.getMaxDamage();
                text = durability.is("Prozent") ? Math.round(ratio * 100) + "%" : String.valueOf(left);
                color = ratio > 0.5f ? 0xFF7CF29A : ratio > 0.2f ? 0xFFFFD24A : 0xFFFF5C72;
            } else if (stack.getCount() > 1) {
                text = String.valueOf(stack.getCount());
            }
            if (text.isEmpty()) continue;
            if (horizontal()) {
                g.pose().pushMatrix();
                g.pose().translate(x + 8, y + 18);
                g.pose().scale(0.75f, 0.75f);
                g.drawString(font, text, -font.width(text) / 2, 0, color, textShadow());
                g.pose().popMatrix();
            } else {
                g.drawString(font, text, x + 20, y + 4, color, textShadow());
            }
        }
    }
}
