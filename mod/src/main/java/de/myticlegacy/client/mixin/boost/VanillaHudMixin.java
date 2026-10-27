package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.hud.VanillaHudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scoreboard, Titel, Aktionsleiste und Effekte an die Position aus dem HUD-Editor verschieben */
@Mixin(Gui.class)
public class VanillaHudMixin {
    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"))
    private void mytic$scoreStart(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.scoreboard);
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("RETURN"))
    private void mytic$scoreEnd(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }

    @Inject(method = "renderTitle", at = @At("HEAD"))
    private void mytic$titleStart(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.title);
    }

    @Inject(method = "renderTitle", at = @At("RETURN"))
    private void mytic$titleEnd(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"))
    private void mytic$actionStart(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.actionbar);
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"))
    private void mytic$actionEnd(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }

    @Inject(method = "renderEffects", at = @At("HEAD"))
    private void mytic$effectsStart(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.effects);
    }

    @Inject(method = "renderEffects", at = @At("RETURN"))
    private void mytic$effectsEnd(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }
}
