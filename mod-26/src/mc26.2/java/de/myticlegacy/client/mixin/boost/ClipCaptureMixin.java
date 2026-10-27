package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.ClipModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clips (26.2): fertiges Bild abgreifen, kurz bevor es auf den Bildschirm kommt */
@Mixin(Minecraft.class)
public class ClipCaptureMixin {
    @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"))
    private void mytic$clipFrame(boolean renderLevel, CallbackInfo ci) {
        ClipModule.onFrame();
    }
}
