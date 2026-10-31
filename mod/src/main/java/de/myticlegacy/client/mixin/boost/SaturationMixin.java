package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.compat.FrameGrab;
import de.myticlegacy.client.compat.GlSaturation;
import de.myticlegacy.client.module.SaturationModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Farbsättigung: direkt nachdem die Welt gezeichnet ist, vor HUD und Menüs (renderLevel gibt es in allen Versionen) */
@Mixin(GameRenderer.class)
public abstract class SaturationMixin {
    @Inject(method = "render", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel", shift = At.Shift.AFTER))
    private void mytic$saturation(CallbackInfo ci) {
        float f = SaturationModule.factor();
        if (f < 0) return;
        int fbo = FrameGrab.glFramebuffer(), tex = FrameGrab.glTexture();
        if (fbo != 0 || tex != 0) GlSaturation.apply(fbo, tex, FrameGrab.width(), FrameGrab.height(), f);
    }
}
