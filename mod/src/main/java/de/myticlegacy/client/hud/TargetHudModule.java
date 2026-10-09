package de.myticlegacy.client.hud;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.Ui;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Zeigt den anvisierten bzw. zuletzt getroffenen Gegner: Kopf, Name, Lebensbalken, Distanz. */
public class TargetHudModule extends HudModule {
    private final BoolSetting showDistance;
    private float shownHealth = -1;

    public TargetHudModule() {
        super("targethud", "Target-HUD", "Leben und Name deines Gegners", () -> Items.PLAYER_HEAD, false, 10000, 200);
        showDistance = new BoolSetting(this, "distance", "Distanz zeigen", true);
    }

    @Override
    public boolean hasContent() {
        return CombatTracker.target() != null;
    }

    @Override
    public int baseWidth() {
        return 128;
    }

    @Override
    public int baseHeight() {
        return 38;
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity target = CombatTracker.target();
        if (target == null && !preview) return;
        String name = target != null ? target.getDisplayName().getString() : "Gegner";
        float health = target != null ? target.getHealth() + target.getAbsorptionAmount() : 14.5f;
        float max = target != null ? target.getMaxHealth() : 20f;
        if (shownHealth < 0 || Math.abs(shownHealth - health) > max) shownHealth = health;
        shownHealth += (health - shownHealth) * 0.15f;

        int w = baseWidth();
        int h = baseHeight();
        if (background.get()) {
            Ui.rect(g, 0, 0, w, h, rounded.get() ? 4 : 0, Ui.withAlpha(0x0B0812, (int) Math.round(backgroundOpacity.get() * 2.55)));
            neonEdge(g, h);
        }
        int face = 30;
        if (target instanceof Player player && mc.getConnection() != null && mc.getConnection().getPlayerInfo(player.getUUID()) != null) {
            Compat.drawFace(g, mc.getConnection().getPlayerInfo(player.getUUID()), 4, 4, face);
        } else {
            Ui.rect(g, 4, 4, face, face, 3, Ui.withAlpha(Ui.accent(), 160));
            Ui.centered(g, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 4 + face / 2, 4 + face / 2 - 4, 0xFFFFFFFF, true);
        }
        var font = mc.font;
        int textX = face + 10;
        String clipped = font.plainSubstrByWidth(name, w - textX - 4);
        g.drawString(font, clipped, textX, 6, color(), textShadow());

        float ratio = Math.max(0, Math.min(1, shownHealth / max));
        int barW = w - textX - 6;
        int barY = 18;
        Ui.rect(g, textX, barY, barW, 5, 2, 0x80000000);
        int barColor = ratio > 0.5f ? 0xFF5BE38A : ratio > 0.25f ? 0xFFFFD84A : 0xFFFF5C72;
        Ui.rect(g, textX, barY, Math.max(4, Math.round(barW * ratio)), 5, 2, barColor);

        String hp = String.format("%.1f ❤", health);
        g.drawString(font, hp, textX, 27, 0xFFFF8A9E, textShadow());
        if (showDistance.get()) {
            String distance = target != null && mc.player != null ? String.format("%.1f m", mc.player.distanceTo(target)) : "2.8 m";
            g.drawString(font, distance, w - 6 - font.width(distance), 27, 0xFFB0A8C8, textShadow());
        }
    }
}
