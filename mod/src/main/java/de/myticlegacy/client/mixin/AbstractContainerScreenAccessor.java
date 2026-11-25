package de.myticlegacy.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot mytic$hoveredSlot();

    /** Lage des Inventar-Fensters (InvSee-Übersicht daneben) */
    @Accessor("leftPos")
    int mytic$left();

    @Accessor("topPos")
    int mytic$top();

    @Accessor("imageWidth")
    int mytic$imageWidth();

    @Accessor("imageHeight")
    int mytic$imageHeight();
}
