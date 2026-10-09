package de.myticlegacy.client.mixin;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelMixin {
    /** Time Changer: eigene Tageszeit, nur in der Client-Welt (26.x: Welt-Uhr). */
    @Inject(method = {"getDefaultClockTime", "getOverworldClockTime"}, at = @At("RETURN"), cancellable = true)
    private void mytic$time(CallbackInfoReturnable<Long> cir) {
        if ((Object) this instanceof ClientLevel && MyticClient.timeChanger != null && MyticClient.timeChanger.enabled()) {
            cir.setReturnValue(MyticClient.timeChanger.dayTime());
        }
    }

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void mytic$noRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel && MyticClient.clearWeather != null && MyticClient.clearWeather.enabled()) cir.setReturnValue(0f);
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void mytic$noThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel && MyticClient.clearWeather != null && MyticClient.clearWeather.enabled()) cir.setReturnValue(0f);
    }
}
