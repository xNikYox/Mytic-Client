package de.myticlegacy.client.mixin.boost;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> mytic$all();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> mytic$trimmed();

    @Accessor("chatScrollbarPos")
    int mytic$scrollPos();

    @Invoker("refreshTrimmedMessages")
    void mytic$refresh();

    @Invoker("getScale")
    double mytic$scale();

    @Invoker("getLineHeight")
    int mytic$lineHeight();

    @Invoker("getWidth")
    int mytic$width();
}
