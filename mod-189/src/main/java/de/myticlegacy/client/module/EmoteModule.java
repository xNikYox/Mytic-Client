package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.emotes.Emotes;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;

import java.util.Map;
import java.util.UUID;

/**
 * 1.8.9: Emotes – Taste (Standard G) öffnet das Emote-Rad, andere Mytic-Spieler auf demselben Server sehen das Emote.
 * Bewegen, Springen, Schleichen, Angreifen oder Schaden brechen es ab. Optional kurz in die Third-Person-Ansicht.
 */
public class EmoteModule extends Module {
    public final BoolSetting showOthers;
    public final BoolSetting thirdPerson;
    private double sx, sy, sz;
    private Emotes.Active tracked;
    private boolean switchedView;

    public EmoteModule() {
        super("emotes", "Emotes", "Emote-Rad per Taste (G); andere Mytic-Spieler auf dem Server sehen es", Category.CLIENT, () -> Items.record_cat, true);
        showOthers = new BoolSetting(this, "showOthers", "Emotes anderer anzeigen", true);
        thirdPerson = new BoolSetting(this, "thirdPerson", "Beim Emote in die Third-Person-Ansicht", true);
    }

    private static String server(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.isSingleplayer()) return null;
        ServerData data = mc.getCurrentServerData();
        return data == null ? null : data.serverIP;
    }

    public void tick(Minecraft mc) {
        Emotes.enabled = enabled();
        Emotes.showOthers = showOthers.get();
        Emotes.tick(server(mc));
        de.myticlegacy.client.render.EmoteModels.install(mc);
        while (MyticClient.emoteKey.isPressed()) {
            if (Compat.screen(mc) == null && mc.thePlayer != null && enabled()) Compat.setScreen(new de.myticlegacy.client.gui.EmoteWheelScreen());
        }
        if (mc.theWorld != null) {
            for (Map.Entry<String, Emotes.Active> e : Emotes.remotes()) remoteMoved(mc, e.getKey(), e.getValue());
        }
        EntityPlayer p = mc.thePlayer;
        Emotes.Active self = Emotes.self();
        if (p == null || self == null) {
            if (tracked != null) end(mc);
            return;
        }
        if (self != tracked) {
            tracked = self;
            sx = p.posX;
            sy = p.posY;
            sz = p.posZ;
            if (thirdPerson.get() && mc.gameSettings.thirdPersonView == 0) {
                mc.gameSettings.thirdPersonView = 1;
                switchedView = true;
            }
        }
        double dx = p.posX - sx, dz = p.posZ - sz;
        boolean moved = dx * dx + dz * dz > 0.04 || Math.abs(p.posY - sy) > 0.6;
        // (Tasten erst nach einer kurzen Schonzeit – der Klick im Emote-Rad soll nicht gleich abbrechen)
        boolean keys = System.currentTimeMillis() - self.start > 300 && (mc.gameSettings.keyBindAttack.isKeyDown()
                || mc.gameSettings.keyBindUseItem.isKeyDown() || mc.gameSettings.keyBindJump.isKeyDown());
        if (moved || keys || p.isSneaking() || p.isUsingItem() || p.hurtTime > 0 || p.isRiding()) {
            Emotes.stopSelf(true);
            end(mc);
        }
    }

    private void end(Minecraft mc) {
        tracked = null;
        if (switchedView && mc.gameSettings.thirdPersonView == 1) mc.gameSettings.thirdPersonView = 0;
        switchedView = false;
    }

    private static void remoteMoved(Minecraft mc, String key, Emotes.Active a) {
        EntityPlayer other;
        try {
            other = mc.theWorld.getPlayerEntityByUUID(UUID.fromString(key.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (other == null) return;
        if (Double.isNaN(a.ax)) {
            a.ax = other.posX;
            a.ay = other.posY;
            a.az = other.posZ;
            return;
        }
        double dx = other.posX - a.ax, dz = other.posZ - a.az;
        if (dx * dx + dz * dz > 0.09 || Math.abs(other.posY - a.ay) > 0.8) Emotes.dropRemote(key, null);
    }
}
