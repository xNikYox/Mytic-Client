package de.myticlegacy.client.mixin.boost;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.module.PvpModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PvP-Optik: Schild in der Hand tiefer halten (1.21.9+; ältere: eigene Fassung) */
@Mixin(ItemInHandRenderer.class)
public abstract class ShieldMixin {
    private static boolean mytic$pushed;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), require = 0)
    private void mytic$lower(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector out, int light, CallbackInfo ci) {
        float off = PvpModule.shieldOffset();
        mytic$pushed = off > 0 && stack.is(Items.SHIELD);
        if (mytic$pushed) {
            pose.pushPose();
            pose.translate(0, -off * 0.6f, 0);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
    private void mytic$restore(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector out, int light, CallbackInfo ci) {
        if (mytic$pushed) pose.popPose();
        mytic$pushed = false;
    }
}
