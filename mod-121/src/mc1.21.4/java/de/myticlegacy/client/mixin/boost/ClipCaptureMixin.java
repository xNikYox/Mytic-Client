package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.ClipModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clips: fertiges Bild (Welt + HUD + Menüs) abgreifen, kurz bevor es auf den Bildschirm kommt */
@Mixin(Minecraft.class)
public class ClipCaptureMixin {
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    private void mytic$clipFrame(boolean renderLevel, CallbackInfo ci) {
        ClipModule.onFrame();
    }
}
