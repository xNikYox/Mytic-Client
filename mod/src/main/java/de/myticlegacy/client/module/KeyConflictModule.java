package de.myticlegacy.client.module;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.ActionSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Findet doppelt belegte Tasten (Minecraft, Mytic und andere Mods) und schlägt freie Tasten vor.
 * Meldet sich im Chat beim Betreten einer Welt und sobald sich die Belegung ändert.
 */
public class KeyConflictModule extends Module {
    /** Gut erreichbare Tasten, die als freie Vorschläge in Frage kommen */
    private static final String[] CANDIDATES = {"g", "h", "j", "k", "b", "n", "m", "z", "x", "u", "i", "o", "p", "y", "r", "f7", "f9", "f10", "f12",
            "keypad.0", "keypad.1", "keypad.2", "keypad.3", "keypad.4", "keypad.5", "keypad.6", "keypad.7", "keypad.8", "keypad.9"};

    private String lastSignature = "";
    private boolean wasInWorld;
    private int ticks;

    public KeyConflictModule() {
        super("keyconflicts", "Tastenkonflikte", "Zeigt doppelt belegte Tasten im Chat und schlägt freie Tasten vor", Category.CLIENT, () -> Items.TRIPWIRE_HOOK, true);
        new ActionSetting(this, "check", "Belegung prüfen", () -> "Jetzt prüfen", () -> report(Minecraft.getInstance(), true));
    }

    /** Taste → alle Belegungen mit dieser Taste (nur Tasten, die mehr als einmal vorkommen) */
    private static Map<String, List<KeyMapping>> conflicts(Minecraft mc) {
        Map<String, List<KeyMapping>> byKey = new LinkedHashMap<>();
        for (KeyMapping km : mc.options.keyMappings) {
            if (km.isUnbound()) continue;
            byKey.computeIfAbsent(km.saveString(), k -> new ArrayList<>()).add(km);
        }
        byKey.values().removeIf(list -> list.size() < 2);
        return byKey;
    }

    public void tick(Minecraft mc) {
        boolean inWorld = mc.player != null;
        if (!enabled() || !inWorld) {
            wasInWorld = inWorld;
            return;
        }
        if (!wasInWorld) {
            wasInWorld = true;
            ticks = 60; // kurz nach dem Betreten melden
            lastSignature = "";
        }
        if (--ticks > 0) return;
        ticks = 40;
        Map<String, List<KeyMapping>> found = conflicts(mc);
        String signature = found.keySet().toString();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        if (!found.isEmpty()) report(mc, false);
    }

    /** Konflikte im Chat auflisten; manual = auch melden, wenn alles frei ist */
    public void report(Minecraft mc, boolean manual) {
        if (mc.player == null) {
            Msg.actionBar("Erst einer Welt beitreten");
            return;
        }
        Map<String, List<KeyMapping>> found = conflicts(mc);
        if (found.isEmpty()) {
            if (manual) Compat.chat(mc, "§8[§dMytic§8] §aKeine Taste ist doppelt belegt.");
            return;
        }
        Compat.chat(mc, "§8[§dMytic§8] §c" + found.size() + (found.size() == 1 ? " Taste ist" : " Tasten sind") + " doppelt belegt:");
        for (List<KeyMapping> list : found.values()) {
            List<String> names = new ArrayList<>();
            for (KeyMapping km : list) names.add(Component.translatable(km.getName()).getString());
            Compat.chat(mc, " §e" + list.get(0).getTranslatedKeyMessage().getString() + "§7: " + String.join("§8, §7", names));
        }
        List<String> free = freeKeys(mc, 6);
        if (!free.isEmpty()) Compat.chat(mc, " §7Frei wären z. B.: §a" + String.join("§7, §a", free) + " §8(Optionen → Steuerung)");
    }

    private static List<String> freeKeys(Minecraft mc, int max) {
        Set<String> used = new HashSet<>();
        for (KeyMapping km : mc.options.keyMappings) used.add(km.saveString());
        List<String> out = new ArrayList<>();
        for (String c : CANDIDATES) {
            String name = "key.keyboard." + c;
            if (used.contains(name)) continue;
            try {
                out.add(InputConstants.getKey(name).getDisplayName().getString());
            } catch (RuntimeException e) {
                continue;
            }
            if (out.size() >= max) break;
        }
        return out;
    }
}
