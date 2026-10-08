package de.myticlegacy.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Setzt einen Optionswert ohne die Bereichsprüfung (für Fullbright: Helligkeit über 100 %). */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("value")
    void mytic$setValue(Object value);
}
