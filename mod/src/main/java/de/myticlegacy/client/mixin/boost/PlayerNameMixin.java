package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.StreamerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Streamer-Modus: eigenes Namensschild mit Ersatznamen; Totem-Pops hinter dem Namen */
@Mixin(Player.class)
public abstract class PlayerNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void mytic$name(CallbackInfoReturnable<Component> cir) {
        if ((Object) this == Minecraft.getInstance().player) cir.setReturnValue(StreamerModule.filter(cir.getReturnValue()));
        Component pops = de.myticlegacy.client.module.TotemPopModule.suffix((Player) (Object) this); // Totem-Pops hinter dem Namen
        if (pops != null) cir.setReturnValue(cir.getReturnValue().copy().append(pops));
    }
}
