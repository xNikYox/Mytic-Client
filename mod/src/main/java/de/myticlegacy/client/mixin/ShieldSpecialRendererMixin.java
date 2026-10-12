package de.myticlegacy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.myticlegacy.client.cosmetics.Shields;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Schild-Cosmetics: markierte Schilde mit der animierten Mytic-Textur zeichnen. */
@Mixin(ShieldSpecialRenderer.class)
abstract class ShieldSpecialRendererMixin {
    @Shadow
    @Final
    private ShieldModel model;

    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/component/DataComponentMap;", at = @At("RETURN"), cancellable = true)
    private void mytic$mark(ItemStack stack, CallbackInfoReturnable<DataComponentMap> cir) {
        String id = Shields.forOwner();
        if (id == null) return;
        cir.setReturnValue(DataComponentMap.builder().addAll(cir.getReturnValue()).set(DataComponents.CUSTOM_NAME, Component.literal(Shields.MARK + id)).build());
    }

    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At("HEAD"), cancellable = true)
    private void mytic$draw(DataComponentMap map, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil,
                            int outline, CallbackInfo ci) {
        if (map == null) return;
        Component name = map.get(DataComponents.CUSTOM_NAME);
        String id = Shields.fromMark(name == null ? null : name.getString());
        if (id == null) return;
        ci.cancel();
        RenderType type = RenderTypes.entitySolid(Shields.texture(id));
        pose.pushPose();
        pose.scale(1f, -1f, -1f);
        collector.submitModelPart(model.handle(), pose, type, light, overlay, null);
        collector.submitModelPart(model.plate(), pose, type, light, overlay, null);
        pose.popPose();
    }
}
