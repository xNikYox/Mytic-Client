package de.myticlegacy.client.module;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Totem-Pop-Zähler: zählt, wie oft andere Spieler ein Totem verbraucht haben (Ereignis 35 vom Server, TotemPopMixin).
 * Anzeige hinter dem Namen über dem Kopf und im Chat; beim Tod des Spielers und beim Weltwechsel wird zurückgesetzt.
 */
public class TotemPopModule extends Module {
    private static TotemPopModule instance;
    private static final byte TOTEM = 35, DEATH = 3;

    private final BoolSetting chat;
    private final BoolSetting nametag;
    private final BoolSetting own;
    private final Map<UUID, Integer> pops = new HashMap<>();
    private Object level;

    public TotemPopModule() {
        super("totempops", "Totem-Pops", "Zählt verbrauchte Totems anderer Spieler – über dem Kopf und im Chat", Category.VISUELL, () -> Items.TOTEM_OF_UNDYING, false);
        chat = new BoolSetting(this, "chat", "Meldung im Chat", true);
        nametag = new BoolSetting(this, "nametag", "Anzahl hinter dem Namen", true);
        own = new BoolSetting(this, "own", "Eigene Totems mitzählen", false);
        instance = this;
    }

    /** Vom Mixin bei jedem Entity-Ereignis auf dem Client */
    public static void event(LivingEntity entity, byte id) {
        if (instance == null || !instance.enabled() || !(entity instanceof Player p) || !entity.level().isClientSide()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean self = p == mc.player;
        if (self && !instance.own.get()) return;
        String name = p.getName().getString();
        if (id == TOTEM) {
            int n = instance.pops.merge(p.getUUID(), 1, Integer::sum);
            if (instance.chat.get()) Compat.chat(mc, "§8[§dMytic§8] §f" + (self ? "Du hast" : name + " hat") + " §c" + n + (n == 1 ? " Totem" : " Totems") + " §fverbraucht");
        } else if (id == DEATH) {
            Integer n = instance.pops.remove(p.getUUID());
            if (n != null && instance.chat.get()) Compat.chat(mc, "§8[§dMytic§8] §f" + (self ? "Du bist" : name + " ist") + " nach §c" + n + (n == 1 ? " Totem" : " Totems") + " §fgestorben");
        }
    }

    /** Zusatz hinter dem Namen über dem Kopf, z. B. „ [3]“ – oder null */
    public static Component suffix(Player p) {
        if (instance == null || !instance.enabled() || !instance.nametag.get()) return null;
        Integer n = instance.pops.get(p.getUUID());
        return n == null ? null : Component.literal(" §8[§c" + n + "§8]");
    }

    public void tick(Minecraft mc) {
        if (mc.level != level) { // neue Welt/Server: Zähler leeren
            level = mc.level;
            pops.clear();
        }
    }
}
