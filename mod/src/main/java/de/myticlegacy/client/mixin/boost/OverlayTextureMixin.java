package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.PvpCompat;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PvP-Optik: Overlay-Textur merken, damit die Treffer-Farbe neu eingefärbt werden kann */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin {
    @Shadow
    @Final
    private DynamicTexture texture;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void mytic$remember(CallbackInfo ci) {
        PvpCompat.overlay = texture;
    }
}
