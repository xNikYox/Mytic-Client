package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.PvpModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** PvP-Optik: Blockrahmen in eigener Farbe und Dicke (Farbe ab 1.21.2, Dicke ab 1.21.11) */
@Mixin(LevelRenderer.class)
public abstract class OutlineMixin {
    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int mytic$color(int color) {
        Integer c = PvpModule.outlineColor();
        return c == null ? color : c;
    }

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float mytic$width(float width) {
        return PvpModule.outlineWidth(width);
    }
}
