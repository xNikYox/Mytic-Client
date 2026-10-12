package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Nametags: eigene Zeile über dem Namen. Minecraft zeigt unter dem Namen eine zweite Zeile (Scoreboard-Punkte); diese
 * nutzt die Mod für den Namen, die obere Zeile für den Nametag. Gibt es schon Punkte, stehen sie hinter dem Namen.
 */
public final class Nametags {
    private Nametags() {
    }

    /** {obere Zeile, untere Zeile} für diese Figur oder null (nichts ändern). */
    public static Component[] decorate(Object entity, Component name, Component score) {
        Minecraft mc = Minecraft.getInstance();
        if (!(entity instanceof Entity e) || name == null || mc.player == null) return null;
        String tag = CosmeticsCore.nametagOf(e.getUUID().toString().replace("-", ""), e.getUUID().equals(mc.player.getUUID()));
        if (tag == null) return null;
        Component bottom = score == null ? name : name.copy().append(Component.literal(" §7· ")).append(score);
        return new Component[]{Component.literal(tag), bottom};
    }

    /** Eigenen Namen in F5 zeigen? (nur mit Nametag und eingeschalteter Option) */
    public static boolean showOwn(Object entity) {
        Minecraft mc = Minecraft.getInstance();
        return entity == mc.player && mc.player != null && !mc.player.isInvisible() && MyticClient.config().nametagSelf
                && mc.options.getCameraType() != CameraType.FIRST_PERSON && CosmeticsCore.formatNametag(CosmeticsCore.ownNametag()) != null
                && de.myticlegacy.client.compat.Compat.screen(mc) == null;
    }
}
