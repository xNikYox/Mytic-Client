package de.myticlegacy.client.mixin.boost;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Chat+: Eingabefeld des Chat-Fensters (die Suche nutzt es als Suchfeld) */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor("input")
    EditBox mytic$input();
}
