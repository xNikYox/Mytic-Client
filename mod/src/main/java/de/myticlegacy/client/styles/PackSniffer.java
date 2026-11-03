package de.myticlegacy.client.styles;


import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ordnet Dateien eines Pakets den Kategorien zu (Texturen, Modelle, Item-Definitionen, Sounds):
 * z. B. textures/item/diamond_sword.png und models/item/diamond_sword.json → Schwert.
 */
public final class PackSniffer {
    private static final java.util.Set<String> FOOD = java.util.Set.of("apple", "bread", "beef", "cooked_beef", "chicken", "cooked_chicken", "porkchop", "cooked_porkchop",
            "mutton", "cooked_mutton", "rabbit", "cooked_rabbit", "cod", "cooked_cod", "salmon", "cooked_salmon", "cookie", "melon_slice", "carrot", "golden_carrot",
            "potato", "baked_potato", "poisonous_potato", "pumpkin_pie", "cake", "mushroom_stew", "rabbit_stew", "beetroot", "beetroot_soup", "suspicious_stew",
            "sweet_berries", "glow_berries", "dried_kelp", "honey_bottle", "chorus_fruit", "tropical_fish", "pufferfish", "rotten_flesh", "spider_eye");

    private PackSniffer() {
    }

    /** Kategorie einer Datei (Pfad mit oder ohne "assets/minecraft/"), sonst null */
    public static Category categoryOf(String path) {
        String p = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        String file = p.substring(p.lastIndexOf('/') + 1);
        int dot = file.indexOf('.');
        String stem = dot < 0 ? file : file.substring(0, dot);
        boolean item = p.contains("/item/") || p.startsWith("items/") || p.contains("/items/");
        if (p.contains("sounds/") || file.equals("sounds.json")) return Category.SOUNDS;
        if (p.contains("end_crystal")) return Category.CRYSTAL;
        if (p.contains("respawn_anchor")) return Category.ANCHOR;
        if (p.contains("totem_of_undying")) return Category.TOTEM;
        if (stem.contains("glint")) return Category.GLINT;
        if (p.contains("textures/particle/") || p.startsWith("particles/") || p.contains("/particles/")) return Category.PARTICLES;
        if (p.startsWith("textures/gui/") || p.contains("/textures/gui/") || p.startsWith("textures/font/") || p.contains("/textures/font/") || p.startsWith("font/") || p.contains("/font/")) return Category.INVENTORY;
        if (stem.startsWith("ender_pearl")) return Category.PEARL;
        if (stem.equals("golden_apple")) return Category.GAPPLE; // verzauberter Goldapfel nur im Komplett-Paket
        if (stem.contains("potion") && !stem.contains("baked_potato")) return Category.POTION;
        if (stem.startsWith("experience_bottle")) return Category.XP;
        if (stem.endsWith("_sword")) return Category.SWORD;
        if (stem.endsWith("_pickaxe")) return Category.PICKAXE;
        if (stem.endsWith("_axe")) return Category.AXE;
        if (stem.endsWith("_shovel") || stem.endsWith("_hoe")) return Category.TOOLS;
        if (stem.equals("bow") || stem.startsWith("bow_pulling") || stem.startsWith("crossbow") || stem.equals("arrow") || stem.startsWith("spectral_arrow")
                || stem.startsWith("tipped_arrow") || stem.startsWith("firework_rocket")) return Category.BOW;
        if (p.contains("models/armor/") || p.contains("entity/equipment/") || stem.endsWith("_helmet") || stem.endsWith("_chestplate") || stem.endsWith("_leggings") || stem.endsWith("_boots")) return Category.ARMOR;
        if (stem.startsWith("fire_") || stem.startsWith("soul_fire")) return Category.FIRE;
        if (stem.endsWith("_ore") || stem.startsWith("ancient_debris")) return Category.ORES;
        if (stem.equals("stone") || stem.startsWith("cobblestone")) return Category.STONE;
        if (stem.startsWith("glowstone")) return Category.GLOWSTONE;
        if (stem.equals("obsidian") || stem.startsWith("crying_obsidian")) return Category.OBSIDIAN;
        if (item && FOOD.contains(stem)) return Category.FOOD;
        return null;
    }

    /**
     * Zählt pro Kategorie die Dateien im Paket. Die Freddi-Pakete enthalten nur noch echte Änderungen (unveränderte
     * Original-Dateien entfernt das Import-Skript), daher zählen auch Modelle – z. B. ein kleineres Totem in der Hand.
     */
    public static Map<Category, Integer> scan(PackFiles pack) {
        Map<Category, Integer> hits = new EnumMap<>(Category.class);
        for (String n : pack.names()) {
            String p = n.toLowerCase(Locale.ROOT);
            if (!p.startsWith("assets/") || !(p.endsWith(".png") || p.endsWith(".ogg") || p.endsWith(".json"))) continue;
            Category c = categoryOf(p);
            if (c != null) hits.merge(c, 1, Integer::sum);
        }
        return hits;
    }

    /** Für Pakete in einem Kategorie-Ordner ohne Kategorie im Namen: Kategorie mit den meisten Treffern */
    static Category detect(PackFiles pack) {
        Category best = Category.OTHER;
        int max = 0;
        for (Map.Entry<Category, Integer> e : scan(pack).entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** Materialien in der Reihenfolge, in der sie für die Vorschau bevorzugt werden */
    private static final String[] TIERS = {"netherite", "diamond", "iron", "copper", "golden", "stone", "wooden", "chainmail", "leather"};
    private static final Map<Category, String[]> TIERED = Map.of(
            Category.SWORD, new String[]{"sword"}, Category.AXE, new String[]{"axe"}, Category.PICKAXE, new String[]{"pickaxe"},
            Category.TOOLS, new String[]{"shovel", "hoe"}, Category.ARMOR, new String[]{"helmet", "chestplate", "leggings", "boots"});

    /**
     * Vorschau für Waffen, Werkzeuge und Rüstung: Items des besten Materials, das das Paket ändert
     * (Netherite → Diamant → Eisen → Kupfer → Gold → Stein → Holz → Kette → Leder). Rüstung: alle Teile, Schaufel & Hacke: beide.
     * Leere Liste, wenn die Kategorie nicht dazugehört oder das Paket dort keine Item-Texturen hat.
     */
    static List<byte[]> tierPreview(PackFiles pack, Category c) {
        if (c == Category.ANCHOR) return anchorPreview(pack);
        String[] kinds = TIERED.get(c);
        if (kinds == null) return List.of();
        Set<String> present = new HashSet<>(pack.names());
        for (String tier : TIERS) {
            List<byte[]> out = new ArrayList<>();
            for (String kind : kinds) {
                String n = "assets/minecraft/textures/item/" + tier + "_" + kind + ".png";
                if (present.contains(n)) {
                    byte[] b = pack.read(n);
                    if (b != null) out.add(b);
                }
            }
            if (!out.isEmpty()) return out;
        }
        return List.of();
    }

    /**
     * Anker als 2×2: vorne | oben, Seite (geladen) | unten. Seiten, die das Paket nicht ändert, kommen aus dem Original –
     * so ist das Bild immer vollständig. Animierte Texturen (oben) zeigt die Collage mit dem ersten Bild.
     */
    static List<byte[]> anchorPreview(PackFiles pack) {
        Set<String> present = new HashSet<>(pack.names());
        String[] faces = {"respawn_anchor_side0", "respawn_anchor_top", "respawn_anchor_side4", "respawn_anchor_bottom"};
        List<byte[]> out = new ArrayList<>();
        boolean any = false;
        for (String f : faces) {
            String n = "assets/minecraft/textures/block/" + f + ".png";
            byte[] b = present.contains(n) ? pack.read(n) : null;
            if (b != null) any = true;
            else b = vanilla(n);
            if (b != null) out.add(b);
        }
        return any && out.size() == 4 ? out : List.of();
    }

    private static byte[] vanilla(String name) {
        try (java.io.InputStream in = net.minecraft.client.Minecraft.class.getResourceAsStream("/" + name)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Vorschaubild für einen Teil: passendste Textur der Kategorie (Diamant/Netherite bevorzugt) */
    static byte[] preview(PackFiles pack, Category c) {
        String best = null;
        int bestScore = -1;
        for (String n : pack.names()) {
            String p = n.toLowerCase(Locale.ROOT);
            if (!p.endsWith(".png") || !p.contains("textures/") || categoryOf(p) != c) continue;
            int score = (p.contains("/textures/item/") ? 6 : 0) + (p.contains("netherite") || p.contains("diamond") ? 4 : 0)
                    + (p.endsWith("container/inventory.png") ? 10 : 0) + (p.endsWith("/end_crystal.png") ? 3 : 0) + (p.endsWith("_chestplate.png") ? 3 : 0)
                    + (p.endsWith("/bow.png") ? 3 : 0) + (p.endsWith("/cooked_beef.png") ? 3 : 0) + (p.endsWith("/splash_potion.png") ? 3 : 0)
                    + (p.endsWith("/enchanted_golden_apple.png") ? 1 : 0) + (p.endsWith("/diamond_ore.png") ? 3 : 0) + (p.endsWith("enchanted_glint_item.png") ? 3 : 0)
                    + (p.endsWith("/obsidian.png") ? 3 : 0) + (p.endsWith("/fire_0.png") ? 3 : 0) + (p.contains("_overlay") ? -3 : 0) + (p.contains("pulling") ? -2 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best == null ? null : pack.read(best);
    }

    /**
     * Alle Dateien eines Teils als Kennungen ("minecraft:textures/item/x.png"): die Treffer der Kategorie plus alles,
     * worauf deren Modelle und Item-Definitionen verweisen (Eltern-Modelle, Texturen, Animationen) – sonst fehlen z. B.
     * Hilfsmodelle wie totem_util.json und das Item wird schwarz-pink.
     */
    static Set<String> partFiles(PackFiles pack, Category c) {
        List<String> all = pack.names();
        Set<String> present = new HashSet<>(all);
        Set<String> files = new HashSet<>();
        ArrayDeque<String> todo = new ArrayDeque<>();
        for (String n : all) {
            if (n.startsWith("assets/") && categoryOf(n) == c && files.add(n)) todo.add(n);
        }
        while (!todo.isEmpty()) {
            String n = todo.poll();
            if (!n.endsWith(".json")) {
                if (n.endsWith(".png") && present.contains(n + ".mcmeta")) files.add(n + ".mcmeta");
                continue;
            }
            byte[] data = pack.read(n);
            if (data == null) continue;
            List<String> refs = new ArrayList<>();
            try {
                collectRefs(JsonParser.parseString(new String(data, StandardCharsets.UTF_8)), refs);
            } catch (RuntimeException e) {
                continue; // kaputtes JSON – Minecraft meldet das selbst
            }
            for (String ref : refs) {
                int colon = ref.indexOf(':');
                String ns = colon < 0 ? "minecraft" : ref.substring(0, colon), path = colon < 0 ? ref : ref.substring(colon + 1);
                for (String cand : new String[]{"assets/" + ns + "/models/" + path + ".json", "assets/" + ns + "/textures/" + path + ".png"}) {
                    if (present.contains(cand) && files.add(cand)) todo.add(cand);
                }
            }
        }
        Set<String> ids = new HashSet<>();
        for (String f : files) {
            String rest = f.substring("assets/".length());
            int slash = rest.indexOf('/');
            if (slash > 0) ids.add(rest.substring(0, slash) + ":" + rest.substring(slash + 1));
        }
        return ids;
    }

    private static void collectRefs(JsonElement e, List<String> out) {
        if (e.isJsonObject()) for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) collectRefs(m.getValue(), out);
        else if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) collectRefs(x, out);
        else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString().toLowerCase(Locale.ROOT);
            if (s.indexOf('/') > 0 && s.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) out.add(s);
        }
    }

    /** Bilder für die 2×2-Collage unter „Komplett“: die ersten vier geänderten aus Schwert, Totem, Kristall, Enderperle, … */
    static List<byte[]> collage(PackFiles pack) {
        Set<String> present = new HashSet<>(pack.names());
        String[][] picks = {{"item/netherite_sword", "item/diamond_sword"}, {"item/totem_of_undying"}, {"item/end_crystal"}, {"item/ender_pearl"},
                {"item/golden_apple", "item/enchanted_golden_apple"}, {"block/obsidian"}, {"item/experience_bottle"}, {"item/netherite_pickaxe", "item/diamond_pickaxe"},
                {"item/diamond_chestplate", "item/netherite_chestplate"}, {"item/splash_potion"}, {"block/respawn_anchor_side0", "block/respawn_anchor_top_off"}, {"block/glowstone"}};
        List<byte[]> out = new ArrayList<>();
        for (String[] alts : picks) {
            if (out.size() == 4) break;
            for (String a : alts) {
                String n = "assets/minecraft/textures/" + a + ".png";
                if (present.contains(n)) {
                    byte[] b = pack.read(n);
                    if (b != null) {
                        out.add(b);
                        break;
                    }
                }
            }
        }
        return out;
    }

}
