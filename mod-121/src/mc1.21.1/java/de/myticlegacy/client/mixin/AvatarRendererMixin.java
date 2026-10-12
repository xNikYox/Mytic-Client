package de.myticlegacy.client.mixin;

import de.myticlegacy.client.render.MiniMeLayer;
import de.myticlegacy.client.render.WingsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21 und 1.21.1: MiniMe- und Flügel-Ebene (Wind gibt es hier nicht, es fehlt der Render-Zustand für den Umhang). */
@Mixin(PlayerRenderer.class)
abstract class AvatarRendererMixin {
    private static final ThreadLocal<Boolean> MYTIC_NAMETAG = ThreadLocal.withInitial(() -> false);

    @org.spongepowered.asm.mixin.Shadow
    protected abstract void renderNameTag(net.minecraft.client.player.AbstractClientPlayer player, net.minecraft.network.chat.Component name,
                                          com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partialTick);

    /** Nametag: erst Name (und Punkte) wie gewohnt, dann eine Zeile höher der Nametag. */
    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), cancellable = true)
    private void mytic$nametag(net.minecraft.client.player.AbstractClientPlayer player, net.minecraft.network.chat.Component name,
                               com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partialTick, CallbackInfo ci) {
        if (MYTIC_NAMETAG.get()) return;
        net.minecraft.network.chat.Component[] lines = de.myticlegacy.client.cosmetics.Nametags.decorate(player, name, null);
        if (lines == null) return;
        ci.cancel();
        MYTIC_NAMETAG.set(true);
        try {
            renderNameTag(player, name, pose, buffers, light, partialTick);
            pose.pushPose();
            pose.translate(0f, 9f * 1.15f * 0.025f, 0f);
            renderNameTag(player, lines[0], pose, buffers, light, partialTick);
            pose.popPose();
        } finally {
            MYTIC_NAMETAG.set(false);
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mytic$miniMeLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new MiniMeLayer((PlayerRenderer) (Object) this, context.getModelSet()));
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new WingsLayer((PlayerRenderer) (Object) this));
        ((LivingEntityRendererInvoker) (Object) this).mytic$addLayer(new de.myticlegacy.client.render.AuraLayer((PlayerRenderer) (Object) this));
    }
}
