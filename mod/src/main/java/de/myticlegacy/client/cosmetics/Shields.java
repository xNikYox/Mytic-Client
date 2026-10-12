package de.myticlegacy.client.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Schild-Cosmetics: Beim Auflösen eines gehaltenen Items merkt sich die Mod den Besitzer (OWNER); hat er einen Mytic-Schild,
 * bekommt das Argument des Schild-Renderers eine Markierung (unsichtbarer Name mit MARK + ID), und der Renderer zeichnet
 * dann mit der animierten Textur aus dem Cosmetics-Paket.
 */
public final class Shields {
    public static final ThreadLocal<Object> OWNER = new ThreadLocal<>();
    public static final String MARK = "\u0001mytic-shield:";

    private Shields() {
    }

    /** Schild-Cosmetic des gerade aufgelösten Besitzers oder null. */
    public static String forOwner() {
        Object owner = OWNER.get();
        Minecraft mc = Minecraft.getInstance();
        if (!(owner instanceof Entity e) || mc.player == null) return null;
        return CosmeticsCore.shieldOf(e.getUUID().toString().replace("-", ""), e.getUUID().equals(mc.player.getUUID()));
    }

    /** ID aus einer Markierung oder null. */
    public static String fromMark(String name) {
        return name != null && name.startsWith(MARK) ? name.substring(MARK.length()) : null;
    }

    public static Identifier texture(String id) {
        return Identifier.fromNamespaceAndPath("myticclient", "textures/shield/" + id + "_" + CosmeticsCore.shieldFrame(id) + ".png");
    }

    /** Vorschau (-Dmytic.shieldDemo): im Einzelspieler einen Schild in die Nebenhand legen. */
    public static void demoTick(Minecraft mc) {
        if (System.getProperty("mytic.shieldDemo") == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null && !sp.getOffhandItem().is(Items.SHIELD)) sp.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        });
    }
}
