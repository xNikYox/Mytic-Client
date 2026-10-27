package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.hud.VanillaHudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scoreboard, Titel, Aktionsleiste und Effekte an die Position aus dem HUD-Editor verschieben */
@Mixin(Hud.class)
public class VanillaHudMixin {
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"))
    private void mytic$scoreStart(GuiGraphicsExtractor g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.scoreboard);
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("RETURN"))
    private void mytic$scoreEnd(GuiGraphicsExtractor g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }

    @Inject(method = "extractTitle", at = @At("HEAD"))
    private void mytic$titleStart(GuiGraphicsExtractor g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.title);
    }

    @Inject(method = "extractTitle", at = @At("RETURN"))
    private void mytic$titleEnd(GuiGraphicsExtractor g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"))
    private void mytic$actionStart(GuiGraphicsExtractor g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.actionbar);
    }

    @Inject(method = "extractOverlayMessage", at = @At("RETURN"))
    private void mytic$actionEnd(GuiGraphicsExtractor g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }

    @Inject(method = "extractEffects", at = @At("HEAD"))
    private void mytic$effectsStart(GuiGraphicsExtractor g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.begin(g, VanillaHudModule.effects);
    }

    @Inject(method = "extractEffects", at = @At("RETURN"))
    private void mytic$effectsEnd(GuiGraphicsExtractor g, DeltaTracker d, CallbackInfo ci) {
        VanillaHudModule.end(g);
    }
}
