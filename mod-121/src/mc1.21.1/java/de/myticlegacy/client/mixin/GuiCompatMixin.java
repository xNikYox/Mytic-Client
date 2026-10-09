package de.myticlegacy.client.mixin;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Gfx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bis 1.21.1: Fadenkreuz und Scoreboard direkt im Gui ersetzen (keine HUD-Ebenen in Fabric API). */
@Mixin(Gui.class)
abstract class GuiCompatMixin {
    @Unique
    private boolean mytic$scaled;

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void mytic$crosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (MyticClient.crosshair.render(g)) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void mytic$scoreboardStart(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        mytic$scaled = false;
        if (!MyticClient.scoreboard.enabled()) return;
        if (MyticClient.scoreboard.hide.get()) {
            ci.cancel();
            return;
        }
        float s = MyticClient.scoreboard.scale.floatValue();
        Gfx.push(g);
        Gfx.translate(g, g.guiWidth(), g.guiHeight() / 2f);
        Gfx.scale(g, s, s);
        Gfx.translate(g, -g.guiWidth(), -g.guiHeight() / 2f);
        mytic$scaled = true;
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("RETURN"))
    private void mytic$scoreboardEnd(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (mytic$scaled) Gfx.pop(g);
        mytic$scaled = false;
    }
}
