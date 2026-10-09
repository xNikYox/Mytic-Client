package de.myticlegacy.client.mixin;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bis 1.21.1: Bossbar ausblenden. */
@Mixin(BossHealthOverlay.class)
abstract class BossOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mytic$hide(GuiGraphics g, CallbackInfo ci) {
        if (MyticClient.hideBossbar.enabled()) ci.cancel();
    }
}
