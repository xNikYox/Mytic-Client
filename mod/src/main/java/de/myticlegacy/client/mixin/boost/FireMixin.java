package de.myticlegacy.client.mixin.boost;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.module.PvpModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** PvP-Optik: Feuer im Bild niedriger zeichnen */
@Mixin(ScreenEffectRenderer.class)
public abstract class FireMixin {
    @ModifyVariable(method = "renderFire", at = @At("HEAD"), argsOnly = true, require = 0)
    private static PoseStack mytic$lower(PoseStack pose) {
        float off = PvpModule.fireOffset();
        pose.pushPose();
        if (off > 0) pose.translate(0, -off, 0);
        return pose;
    }

    @ModifyVariable(method = "renderFire", at = @At("RETURN"), argsOnly = true, require = 0)
    private static PoseStack mytic$restore(PoseStack pose) {
        pose.popPose();
        return pose;
    }
}
