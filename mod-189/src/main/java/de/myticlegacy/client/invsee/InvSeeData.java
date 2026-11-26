package de.myticlegacy.client.invsee;

import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.setting.ItemListSetting;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Zeilen der InvSee-Übersicht (1.8.9): gezählte Items und Rüstung mit Haltbarkeit und Schutzart.
 * Nach InvSee Summary von Noah (MIT-Lizenz), für das Mytic-Design neu geschrieben.
 */
public final class InvSeeData {
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final Enchantment[] PROT = {Enchantment.protection, Enchantment.blastProtection, Enchantment.fireProtection, Enchantment.projectileProtection};
    private static final String[] PROT_LABEL = {"Prot", "Blast", "Fire", "Proj"};
    private static final int[] PROT_COLOR = {0xFF6FB7FF, 0xFFFF9F43, 0xFFFF5E3A, 0xFFC9A0FF};

    public static final class Part {
        public final String text;
        public final int color;

        Part(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    public static final class Row {
        public final String key;
        public final ItemStack icon;
        public final List<Part> name;
        public final String value;
        public final int valueColor;
        public final boolean alwaysName;
        public final float fraction;

        Row(String key, ItemStack icon, List<Part> name, String value, int valueColor, boolean alwaysName, float fraction) {
            this.key = key;
            this.icon = icon;
            this.name = name;
            this.value = value;
            this.valueColor = valueColor;
            this.alwaysName = alwaysName;
            this.fraction = fraction;
        }

        public boolean showsName(boolean names) {
            return names || alwaysName;
        }
    }

    public static final class Snapshot {
        public final String player;
        public final List<ItemStack> stacks;
        public final List<ItemStack> armor;

        Snapshot(String player, List<ItemStack> stacks, List<ItemStack> armor) {
            this.player = player;
            this.stacks = stacks;
            this.armor = armor;
        }
    }

    private InvSeeData() {
    }

    public static Snapshot snapshot(String player, List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<ItemStack>();
        for (ItemStack s : stacks) if (s != null) copy.add(s.copy());
        return new Snapshot(player, copy, findArmor(stacks));
    }

    public static int count(String id, List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack s : stacks) if (ItemListSetting.matches(id, s)) n += s.stackSize;
        return n;
    }

    public static List<Row> itemRows(List<String> ids, boolean hideMissing, List<ItemStack> stacks) {
        List<Row> rows = new ArrayList<Row>();
        for (String id : ids) {
            ItemStack icon = ItemListSetting.stack(id);
            if (icon == null) continue;
            int n = count(id, stacks);
            if (hideMissing && n == 0) continue; // Option: fehlende Items weglassen
            List<Part> name = Collections.singletonList(new Part(icon.getDisplayName(), n > 0 ? Tokens.TEXT : Tokens.SUBTLE));
            rows.add(new Row(id, icon, name, "×" + n, n > 0 ? Tokens.OK : Tokens.BAD, false, -1));
        }
        return rows;
    }

    public static List<Row> armorRows(List<ItemStack> armor) {
        List<Row> rows = new ArrayList<Row>();
        for (ItemStack s : armor) {
            int max = Math.max(1, s.getMaxDamage());
            int left = max - s.getItemDamage();
            float f = (float) left / max;
            rows.add(new Row("armor:" + ItemListSetting.id(s), s, protLabel(s), left + "/" + max, durabilityColor(f), true, f));
        }
        return rows;
    }

    public static List<Part> protLabel(ItemStack s) {
        List<Part> out = new ArrayList<Part>();
        Map<Integer, Integer> ench = EnchantmentHelper.getEnchantments(s);
        for (int i = 0; i < PROT.length; i++) {
            Integer lvl = ench.get(PROT[i].effectId);
            if (lvl == null) continue;
            if (!out.isEmpty()) out.add(new Part(" + ", Tokens.SUBTLE));
            out.add(new Part(PROT_LABEL[i] + " " + (lvl >= 0 && lvl < ROMAN.length ? ROMAN[lvl] : String.valueOf(lvl)), PROT_COLOR[i]));
        }
        if (out.isEmpty()) out.add(new Part("Kein Prot", Tokens.SUBTLE));
        return out;
    }

    public static int durabilityColor(float f) {
        if (f > 0.6f) return Tokens.OK;
        if (f > 0.3f) return 0xFFE8C547;
        if (f > 0.15f) return 0xFFE8873A;
        return Tokens.BAD;
    }

    /** je Rüstungsplatz ein Teil – getragene (Plätze ab 36) haben Vorrang vor Teilen im Inventar */
    public static List<ItemStack> findArmor(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (int type = 0; type < 4; type++) {
            ItemStack found = null;
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack s = stacks.get(i);
                if (s == null || !(s.getItem() instanceof ItemArmor) || ((ItemArmor) s.getItem()).armorType != type || !s.isItemStackDamageable()) continue;
                if (i >= 36) {
                    found = s;
                    break;
                }
                if (found == null) found = s;
            }
            if (found != null) out.add(found.copy());
        }
        return out;
    }
}
