package de.myticlegacy.client.mixin;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    /** Beim Zoomen ändert das Mausrad die Zoomstärke statt den Hotbar-Slot. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mytic$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (vertical != 0 && MyticClient.zoom.scroll(vertical)) ci.cancel();
    }
}
