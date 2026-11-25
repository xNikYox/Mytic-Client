package de.myticlegacy.client.mixin;

import de.myticlegacy.client.invsee.InvSeeAutoClose;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * InvSee-Übersicht: ein gleich wieder schließendes /invsee (Taste „wiederholen“) wird gar nicht erst gezeichnet.
 * Der Methodenname unterscheidet sich je nach Version, einer davon passt immer.
 */
@Mixin(Screen.class)
abstract class InvSeeHiddenScreenMixin {
    @Inject(method = {"renderWithTooltip", "renderWithTooltipAndSubtitles", "extractRenderStateWithTooltipAndSubtitles"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$hideAutoClosed(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (InvSeeAutoClose.isHidden((Screen) (Object) this)) ci.cancel();
    }
}
