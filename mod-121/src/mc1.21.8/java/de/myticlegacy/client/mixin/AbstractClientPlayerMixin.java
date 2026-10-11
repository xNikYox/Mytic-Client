package de.myticlegacy.client.mixin;

import de.myticlegacy.client.cosmetics.Cosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
    /** Cosmetics: Mytic-Umhang statt (oder zusätzlich zum fehlenden) Minecraft-Umhang. */
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void mytic$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        ResourceLocation cape = Cosmetics.cape((AbstractClientPlayer) (Object) this);
        if (cape == null) return;
        PlayerSkin skin = cir.getReturnValue();
        cir.setReturnValue(new PlayerSkin(skin.texture(), skin.textureUrl(), cape, skin.elytraTexture(), skin.model(), skin.secure()));
    }
}
