package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.PvpModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * PvP-Optik „Rüstung auch färben“: Rüstung getroffener Wesen bekommt die Treffer-Farbe (ab 1.21.2).
 * Ob das Wesen getroffen ist, merkt sich HurtOverlayMixin kurz vorher. Je nach Version zeichnet Minecraft die Rüstung
 * über submitModel (ab 1.21.9) oder renderToBuffer (1.21.2 – 1.21.8) – die jeweils andere Stelle gibt es nicht (require = 0).
 */
// als Text: in 1.21 – 1.21.1 gibt es die Klasse noch nicht (Mixin wird dort übersprungen)
@Mixin(targets = "net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer")
public abstract class ArmorHitColorMixin {
    @ModifyArg(method = "renderLayers", require = 0, index = 6,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel"))
    private int mytic$submitColor(int color) {
        return PvpModule.armorColor(color);
    }

    @ModifyArg(method = "renderLayers", require = 0, index = 4,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private int mytic$bufferColor(int color) {
        return PvpModule.armorColor(color);
    }
}
