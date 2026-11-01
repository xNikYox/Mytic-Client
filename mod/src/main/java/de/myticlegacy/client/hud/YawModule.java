package de.myticlegacy.client.hud;

import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Blickrichtung links/rechts (Yaw, -180° bis 180°) – eigene Anzeige, getrennt von Pitch verschiebbar. */
public class YawModule extends TextModule {
    private static YawModule instance;
    public final BoolSetting decimals;

    public YawModule() {
        super("yaw", "Yaw", "Blickrichtung nach links/rechts", () -> Items.COMPASS, false, 10000, 193, "Yaw", YawModule::text);
        decimals = new BoolSetting(this, "decimals", "Nachkommastelle", true);
        instance = this;
    }

    private static String text(boolean preview) {
        if (instance == null) return "";
        Minecraft mc = Minecraft.getInstance();
        float yaw = preview || mc.player == null ? -90f : Mth.wrapDegrees(mc.player.getYRot());
        return String.format(Locale.ROOT, instance.decimals.get() ? "%.1f°" : "%.0f°", yaw);
    }
}
