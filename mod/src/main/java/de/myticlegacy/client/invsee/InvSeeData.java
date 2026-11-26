package de.myticlegacy.client.invsee;

import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.setting.ItemListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zeilen der InvSee-Übersicht: gezählte Items und Rüstung mit Haltbarkeit und Schutzart (Prot/Blast/Fire/Proj).
 * Nach InvSee Summary von Noah (MIT-Lizenz), für das Mytic-Design neu geschrieben.
 */
public final class InvSeeData {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private record ProtType(ResourceKey<Enchantment> key, String label, int color) {
    }

    private static final ProtType[] PROT_TYPES = {
            new ProtType(Enchantments.PROTECTION, "Prot", 0xFF6FB7FF),
            new ProtType(Enchantments.BLAST_PROTECTION, "Blast", 0xFFFF9F43),
            new ProtType(Enchantments.FIRE_PROTECTION, "Fire", 0xFFFF5E3A),
            new ProtType(Enchantments.PROJECTILE_PROTECTION, "Proj", 0xFFC9A0FF),
    };

    /** Textstück in einer Farbe */
    public record Part(String text, int color) {
    }

    /**
     * Eine Zeile: Symbol, Name (optional, Rüstung immer mit Schutzart), Wert. fraction ≥ 0 = Haltbarkeitsbalken.
     */
    public record Row(String key, ItemStack icon, List<Part> name, String value, int valueColor, boolean alwaysName, float fraction) {
        public boolean showsName(boolean names) {
            return names || alwaysName;
        }

        public String nameText() {
            StringBuilder b = new StringBuilder();
            for (Part p : name) b.append(p.text);
            return b.toString();
        }
    }

    /** Ein erfasstes Inventar */
    public record Snapshot(String player, Map<String, Integer> counts, List<ItemStack> armor) {
    }

    private InvSeeData() {
    }

    public static Map<String, Integer> countAll(List<ItemStack> stacks) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            counts.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return counts;
    }

    public static Snapshot snapshot(String player, List<ItemStack> stacks) {
        return new Snapshot(player, countAll(stacks), findArmor(stacks));
    }

    public static List<Row> itemRows(List<String> ids, boolean hideMissing, Map<String, Integer> counts) {
        List<Row> rows = new ArrayList<>();
        for (String id : ids) {
            Item item = ItemListSetting.item(id);
            if (item == null) continue;
            ItemStack stack = Module.stack(item);
            int n = counts.getOrDefault(id, 0);
            if (hideMissing && n == 0) continue; // Option: fehlende Items weglassen
            List<Part> name = List.of(new Part(stack.getHoverName().getString(), n > 0 ? Tokens.TEXT : Tokens.SUBTLE));
            rows.add(new Row(id, stack, name, "×" + n, n > 0 ? Tokens.OK : Tokens.BAD, false, -1));
        }
        return rows;
    }

    public static List<Row> armorRows(List<ItemStack> armor) {
        List<Row> rows = new ArrayList<>();
        for (ItemStack s : armor) {
            int max = Math.max(1, s.getMaxDamage());
            int left = max - s.getDamageValue();
            float f = (float) left / max;
            rows.add(new Row("armor:" + BuiltInRegistries.ITEM.getKey(s.getItem()), s, protLabel(s), left + "/" + max, durabilityColor(f), true, f));
        }
        return rows;
    }

    /** z. B. „Prot IV + Blast II“ in den Farben der Schutzart */
    public static List<Part> protLabel(ItemStack s) {
        List<Part> out = new ArrayList<>();
        ItemEnchantments ench = s.getEnchantments();
        for (ProtType t : PROT_TYPES) {
            for (Holder<Enchantment> h : ench.keySet()) {
                if (!h.is(t.key())) continue;
                int lvl = ench.getLevel(h);
                if (!out.isEmpty()) out.add(new Part(" + ", Tokens.SUBTLE));
                out.add(new Part(t.label() + " " + (lvl >= 0 && lvl < ROMAN.length ? ROMAN[lvl] : String.valueOf(lvl)), t.color()));
            }
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
        List<ItemStack> out = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack found = null;
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack s = stacks.get(i);
                if (!isArmorFor(s, slot)) continue;
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

    private static boolean isArmorFor(ItemStack s, EquipmentSlot slot) {
        if (s.isEmpty() || !s.isDamageableItem()) return false;
        var player = Minecraft.getInstance().player;
        return player != null && player.getEquipmentSlotForItem(s) == slot;
    }
}
