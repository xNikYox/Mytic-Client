package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.emotes.Emotes;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;

/**
 * Emotes: Taste (Standard G) öffnet das Emote-Rad, das eigene Emote sehen alle Mytic-Spieler auf demselben Server.
 * Bewegen, Springen, Schleichen, Angreifen oder Schaden brechen es ab. Optional kurz in die Third-Person-Ansicht.
 */
public class EmoteModule extends Module {
    public final BoolSetting showOthers;
    public final BoolSetting thirdPerson;
    private double sx, sy, sz;
    private Emotes.Active tracked;
    private boolean switchedView;

    public EmoteModule() {
        super("emotes", "Emotes", "Emote-Rad per Taste (G); andere Mytic-Spieler auf dem Server sehen es", Category.CLIENT, () -> Items.NOTE_BLOCK, true);
        showOthers = new BoolSetting(this, "showOthers", "Emotes anderer anzeigen", true);
        thirdPerson = new BoolSetting(this, "thirdPerson", "Beim Emote in die Third-Person-Ansicht", true);
    }

    /** Adresse des Multiplayer-Servers für die Emotes (null = Menü, Einzelspieler, Realms) */
    private static String server(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        ServerData data = mc.getCurrentServer();
        if (data == null || data.isRealm() || mc.isLocalServer()) return null;
        return data.ip;
    }

    public void tick(Minecraft mc) {
        Emotes.enabled = enabled();
        Emotes.showOthers = showOthers.get();
        Emotes.tick(server(mc));
        while (MyticClient.emoteKey.consumeClick()) {
            if (Compat.screen(mc) == null && mc.player != null && enabled()) Compat.setScreen(new de.myticlegacy.client.gui.EmoteWheelScreen());
        }
        // andere: losgelaufen = Emote vorbei
        if (mc.level != null) {
            for (Map.Entry<String, Emotes.Active> e : Emotes.remotes()) remoteMoved(mc, e.getKey(), e.getValue());
        }
        Player p = mc.player;
        Emotes.Active self = Emotes.self();
        if (p == null || self == null) {
            if (tracked != null) end(mc);
            return;
        }
        if (self != tracked) {
            tracked = self;
            sx = p.getX();
            sy = p.getY();
            sz = p.getZ();
            if (thirdPerson.get() && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                switchedView = true;
            }
        }
        // Abbruch: losgelaufen, gesprungen, geschlichen, angegriffen/benutzt oder getroffen
        double dx = p.getX() - sx, dz = p.getZ() - sz;
        boolean moved = dx * dx + dz * dz > 0.04 || Math.abs(p.getY() - sy) > 0.6;
        // (Tasten erst nach einer kurzen Schonzeit – der Klick im Emote-Rad soll nicht gleich abbrechen)
        boolean keys = System.currentTimeMillis() - self.start > 300
                && (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown() || mc.options.keyJump.isDown());
        if (moved || keys || p.isShiftKeyDown() || p.isUsingItem() || p.hurtTime > 0 || p.isPassenger()) {
            Emotes.stopSelf(true);
            end(mc);
        }
    }

    private void end(Minecraft mc) {
        tracked = null;
        if (switchedView && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.FIRST_PERSON);
        switchedView = false;
    }

    private static void remoteMoved(Minecraft mc, String key, Emotes.Active a) {
        Player other;
        try {
            other = mc.level.getPlayerByUUID(UUID.fromString(key.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (other == null) return; // Offline-Server (andere UUID) oder nicht in Sicht: nur die Zeit beendet es
        if (Double.isNaN(a.ax)) {
            a.ax = other.getX();
            a.ay = other.getY();
            a.az = other.getZ();
            return;
        }
        double dx = other.getX() - a.ax, dz = other.getZ() - a.az;
        if (dx * dx + dz * dz > 0.09 || Math.abs(other.getY() - a.ay) > 0.8) Emotes.dropRemote(key, null);
    }
}
