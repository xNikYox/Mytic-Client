package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.TextSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pack-Overlays: bis zu drei Texturpakete per Taste an- und ausschalten – sie kommen ganz oben über die übrigen Pakete.
 * Paketname (oder ein Teil davon) im Mod-Menü eintragen, Tasten unter Optionen → Steuerung → Mytic Client.
 */
public class PackOverlayModule extends Module {
    public static final int SLOTS = 3;
    private final TextSetting[] names = new TextSetting[SLOTS];

    public PackOverlayModule() {
        super("packoverlays", "Pack-Overlays", "Texturpakete per Taste an- und ausschalten (Belegung: Optionen → Steuerung)", Category.VISUELL, () -> Items.PAINTING, false);
        for (int i = 0; i < SLOTS; i++) {
            final int slot = i;
            names[i] = new TextSetting(this, "pack" + (i + 1), "Paket " + (i + 1), "", () -> hint(slot));
        }
        new ActionSetting(this, "list", "Vorhandene Pakete", () -> "Im Chat zeigen", this::listPacks);
    }

    private String hint(int slot) {
        KeyMapping key = MyticClient.packKeys[slot];
        String k = key == null || key.isUnbound() ? "keine Taste" : key.getTranslatedKeyMessage().getString();
        String name = names[slot].get().trim();
        if (name.isEmpty()) return k;
        Pack pack = find(Minecraft.getInstance().getResourcePackRepository(), name);
        if (pack == null) return k + " · nicht gefunden";
        return k + " · " + (Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains(pack.getId()) ? "an" : "aus");
    }

    /** Paket nach Name: genau gleich, sonst Teil von Kennung oder Titel (Groß-/Kleinschreibung egal) */
    private static Pack find(PackRepository repo, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        Pack partial = null;
        for (Pack p : repo.getAvailablePacks()) {
            if (p.isRequired()) continue;
            String id = p.getId().toLowerCase(Locale.ROOT), title = p.getTitle().getString().toLowerCase(Locale.ROOT);
            if (id.equals(n) || id.equals("file/" + n) || title.equals(n)) return p;
            if (partial == null && (id.contains(n) || title.contains(n))) partial = p;
        }
        return partial;
    }

    public void tick(Minecraft mc) {
        for (int i = 0; i < SLOTS; i++) {
            KeyMapping key = MyticClient.packKeys[i];
            if (key == null) continue;
            while (key.consumeClick()) if (enabled()) toggle(mc, names[i].get().trim());
        }
    }

    private static void toggle(Minecraft mc, String name) {
        if (name.isEmpty()) {
            Msg.actionBar("Kein Paket eingetragen (Mod-Menü → Pack-Overlays)");
            return;
        }
        PackRepository repo = mc.getResourcePackRepository();
        Pack pack = find(repo, name);
        if (pack == null) {
            Msg.actionBar("Paket „" + name + "“ nicht gefunden");
            return;
        }
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        boolean on = !selected.remove(pack.getId());
        if (on) selected.add(pack.getId()); // ganz oben = höchste Priorität
        repo.setSelected(selected);
        mc.options.updateResourcePacks(repo); // speichert und lädt neu
        Msg.actionBar(pack.getTitle().getString() + (on ? " an" : " aus"));
    }

    private void listPacks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            Msg.actionBar("Erst einer Welt beitreten");
            return;
        }
        PackRepository repo = mc.getResourcePackRepository();
        Compat.chat(mc, "§8[§dMytic§8] §7Texturpakete (Name zum Eintragen):");
        for (Pack p : repo.getAvailablePacks()) {
            if (p.isRequired()) continue;
            boolean on = repo.getSelectedIds().contains(p.getId());
            Compat.chat(mc, " " + (on ? "§a● " : "§8○ ") + "§f" + p.getId().replaceFirst("^file/", ""));
        }
    }
}
