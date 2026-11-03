package de.myticlegacy.client.styles;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.function.Supplier;

/** Kategorien im Mytic-Styles-Menü (übernommen aus der Freddi Mod). Ordnernamen dürfen englisch oder deutsch sein (z. B. sword_… oder schwert_…). */
public enum Category {
    WHOLE("Komplett", Group.MAIN, () -> Items.PAINTING, "whole", "komplett"),
    // PvP
    SWORD("Schwert", Group.FIGHT, () -> Items.DIAMOND_SWORD, "sword", "schwert"),
    AXE("Axt", Group.FIGHT, () -> Items.DIAMOND_AXE, "axe", "axt"),
    BOW("Bogen & Feuerwerk", Group.FIGHT, () -> Items.BOW, "bow", "bogen"),
    ARMOR("Rüstung", Group.FIGHT, () -> Items.DIAMOND_CHESTPLATE, "armor", "ruestung", "rüstung"),
    TOTEM("Totem", Group.FIGHT, () -> Items.TOTEM_OF_UNDYING, "totem"),
    CRYSTAL("Kristall", Group.FIGHT, () -> Items.END_CRYSTAL, "crystal", "kristall"),
    OBSIDIAN("Obsidian", Group.FIGHT, () -> Items.OBSIDIAN, "obsidian"),
    ANCHOR("Anker", Group.FIGHT, () -> Items.RESPAWN_ANCHOR, "anchor", "anker"),
    GLOWSTONE("Glowstone", Group.FIGHT, () -> Items.GLOWSTONE, "glowstone"),
    PEARL("Enderperle", Group.FIGHT, () -> Items.ENDER_PEARL, "pearl", "enderperle"),
    GAPPLE("Goldapfel", Group.FIGHT, () -> Items.GOLDEN_APPLE, "gapple", "goldapfel"),
    POTION("Tränke", Group.FIGHT, () -> Items.SPLASH_POTION, "potion", "traenke", "tränke"),
    XP("XP-Flasche", Group.FIGHT, () -> Items.EXPERIENCE_BOTTLE, "xp", "xp-flasche"),
    GLINT("Glint", Group.FIGHT, () -> Items.ENCHANTED_BOOK, "glint"),
    FIRE("Feuer", Group.FIGHT, () -> Items.BLAZE_POWDER, "fire", "feuer"),
    // Werkzeuge
    PICKAXE("Spitzhacke", Group.TOOLS, () -> Items.DIAMOND_PICKAXE, "pickaxe", "spitzhacke"),
    TOOLS("Schaufel & Hacke", Group.TOOLS, () -> Items.DIAMOND_SHOVEL, "tools", "werkzeuge"),
    // Welt
    ORES("Erze", Group.WORLD, () -> Items.DIAMOND_ORE, "ores", "erze", "ore"),
    STONE("Stein", Group.WORLD, () -> Items.STONE, "stone", "stein"),
    // Sonstiges
    FOOD("Essen", Group.MISC, () -> Items.COOKED_BEEF, "food", "essen"),
    PARTICLES("Partikel", Group.MISC, () -> Items.FIREWORK_STAR, "particles", "partikel", "particle"),
    INVENTORY("GUI", Group.MISC, () -> Items.CHEST, "gui", "inventory", "inventar"),
    SOUNDS("Sounds", Group.MISC, () -> Items.NOTE_BLOCK, "sounds", "sound"),
    OTHER("Sonstiges", Group.MISC, () -> Items.ITEM_FRAME, "other", "sonstiges");

    /** Abschnitte in der Seitenleiste */
    public enum Group {
        MAIN(""), FIGHT("PvP"), TOOLS("Werkzeuge"), WORLD("Welt"), MISC("Sonstiges");

        public final String title;

        Group(String title) {
            this.title = title;
        }
    }

    public final String title;
    public final Group group;
    private final Supplier<Item> icon;
    private final String[] keys;

    Category(String title, Group group, Supplier<Item> icon, String... keys) {
        this.title = title;
        this.group = group;
        this.icon = icon;
        this.keys = keys;
    }

    /** Ordnername-Kürzel (englisch) */
    public String key() {
        return keys[0];
    }

    public ItemStack icon() {
        return de.myticlegacy.client.module.Module.stack(icon.get());
    }

    /** Kategorie zu einem Kürzel, z. B. "schwert" → SWORD; unbekannt → null */
    public static Category byKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (Category c : values()) for (String s : c.keys) if (s.equals(k)) return c;
        return null;
    }

    /** Kategorie aus einem Paketnamen wie "sword_flammen" (Teil vor dem ersten "_") */
    public static Category ofPackName(String name) {
        int i = name.indexOf('_');
        return i > 0 ? byKey(name.substring(0, i)) : null;
    }
}
