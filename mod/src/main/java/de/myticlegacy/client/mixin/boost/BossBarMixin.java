package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.hud.VanillaHudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bossleisten an die Position aus dem HUD-Editor verschieben */
@Mixin(BossHealthOverlay.class)
public class BossBarMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void mytic$start(GuiGraphics g, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.bossbar);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mytic$end(GuiGraphics g, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }
}
