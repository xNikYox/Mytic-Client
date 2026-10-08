package de.myticlegacy.client.mixin;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    /** Fenstertitel: "Mytic Client 1.21.11 - Multiplayer" statt "Minecraft 1.21.11 - Multiplayer". */
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void mytic$title(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(cir.getReturnValue().replaceFirst("^Minecraft\\*?", MyticClient.NAME));
    }
}
