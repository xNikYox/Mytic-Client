package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.ChatModule;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Chat+ (nur Anzeige): Zeitstempel/Kompakt/Hervorheben am angezeigten Text, Filter (BLOCKED wird verworfen),
 * Tabs und Suche beim Aufbau der Anzeigezeilen, längerer Verlauf. Signaturen bleiben unberührt.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component mytic$decorate(Component message) {
        return ChatModule.decorate((ChatComponent) (Object) this, message);
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$filterLines(GuiMessage message, CallbackInfo ci) {
        ChatModule.bind((ChatComponent) (Object) this);
        if (!ChatModule.visible(message)) {
            ChatModule.hidden(message);
            ci.cancel();
        }
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"), require = 0)
    private void mytic$rememberLines(GuiMessage message, CallbackInfo ci) {
        ChatModule.linesAdded(trimmedMessages, message);
    }

    @Inject(method = "addMessageToQueue", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$queue(GuiMessage message, CallbackInfo ci) {
        if (message.content() == ChatModule.BLOCKED) ci.cancel();
        else ChatModule.arrived(message);
    }

    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void mytic$log(GuiMessage message, CallbackInfo ci) {
        if (message.content() == ChatModule.BLOCKED) ci.cancel();
    }

    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100), require = 0)
    private int mytic$history(int max) {
        return ChatModule.historySize(max);
    }
}
