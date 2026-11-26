package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright: Während options.txt geschrieben wird, die echte Helligkeit (0–100 %) einsetzen statt 1500 %. Sonst meldet
 * Minecraft „Value 15.0 outside of range“ und lässt die Helligkeit ganz aus der Datei weg.
 */
@Mixin(Options.class)
public abstract class OptionsSaveMixin {
    @Inject(method = "save", at = @At("HEAD"), require = 0)
    private void mytic$realGamma(CallbackInfo ci) {
        MyticClient.beforeOptionsSave((Options) (Object) this);
    }

    @Inject(method = "save", at = @At("RETURN"), require = 0)
    private void mytic$fullGamma(CallbackInfo ci) {
        MyticClient.afterOptionsSave((Options) (Object) this);
    }
}
