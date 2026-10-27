package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.StreamerModule;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Streamer-Modus: eigener Name in der Tab-Liste */
@Mixin(PlayerTabOverlay.class)
public abstract class TabNameMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
    private void mytic$name(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(StreamerModule.filter(cir.getReturnValue()));
    }
}
