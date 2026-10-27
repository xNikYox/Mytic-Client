package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.StreamerModule;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Streamer-Modus (1.21 – 1.21.8): Koordinaten in F3 verstecken */
@Mixin(DebugScreenOverlay.class)
public abstract class F3Mixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true, require = 0)
    private void mytic$hide(CallbackInfoReturnable<List<String>> cir) {
        if (!StreamerModule.hidesCoords()) return;
        List<String> out = new ArrayList<>();
        for (String line : cir.getReturnValue()) {
            int colon = line.indexOf(':');
            boolean coords = line.startsWith("XYZ") || line.startsWith("Block:") || line.startsWith("Chunk") || line.startsWith("Targeted") || line.startsWith("Looking");
            out.add(coords && colon > 0 ? line.substring(0, colon + 1) + " ???" : line);
        }
        cir.setReturnValue(out);
    }
}
