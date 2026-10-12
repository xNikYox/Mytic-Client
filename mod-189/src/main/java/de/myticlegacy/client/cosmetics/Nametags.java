package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Method;

/** Nametags für 1.8.9: eigene Zeile über dem Namen (nach dem Namen von Minecraft gezeichnet, eine Zeile höher). */
public final class Nametags {
    private static final float LINE = 9f * 1.15f * 0.025f;
    private static Method label;

    @SubscribeEvent
    @SuppressWarnings("rawtypes")
    public void onSpecials(RenderLivingEvent.Specials.Post event) {
        if (!(event.entity instanceof EntityPlayer)) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = (EntityPlayer) event.entity;
        if (player.isInvisible() || player.isSneaking() || mc.thePlayer == null || player.getDistanceSqToEntity(mc.getRenderViewEntity()) > 64 * 64) return;
        boolean self = player == mc.getRenderViewEntity();
        if (self && (!MyticClient.config().nametagSelf || mc.gameSettings.thirdPersonView == 0 || mc.currentScreen != null)) return;
        String tag = CosmeticsCore.nametagOf(player.getUniqueID().toString().replace("-", ""), self);
        if (tag == null) return;
        try {
            if (label == null) {
                label = ReflectionHelper.findMethod(Render.class, event.renderer, new String[]{"renderLivingLabel", "func_147906_a"},
                        Entity.class, String.class, double.class, double.class, double.class, int.class);
            }
            double y = event.y;
            // eigener Name wird von Minecraft nicht gezeichnet: hier mitzeichnen
            if (self) label.invoke(event.renderer, player, player.getDisplayName().getFormattedText(), event.x, y, event.z, 64);
            // Punkte unter dem Namen (Scoreboard) nehmen eine Zeile ein
            if (!self && player.getDistanceSqToEntity(mc.getRenderViewEntity()) < 100 && player.getWorldScoreboard().getObjectiveInDisplaySlot(2) != null) y += LINE;
            label.invoke(event.renderer, player, tag, event.x, y + LINE, event.z, 64);
        } catch (Exception e) {
            MyticClient.LOG.warn("Nametag: " + e);
        }
    }
}
