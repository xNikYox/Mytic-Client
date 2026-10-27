package de.myticlegacy.client.mixin.boost;

import de.myticlegacy.client.module.StreamerModule;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collection;

/** Streamer-Modus: Koordinaten in F3 verstecken (1.21.9+; ältere: eigene Fassung) */
@Pseudo
@Mixin(targets = {"net.minecraft.client.gui.components.debug.DebugEntryPosition", "net.minecraft.client.gui.components.debug.DebugEntryLookingAtBlock",
        "net.minecraft.client.gui.components.debug.DebugEntryLookingAtFluid", "net.minecraft.client.gui.components.debug.DebugEntrySectionPosition"})
public abstract class F3Mixin {
    @ModifyVariable(method = "display", at = @At("HEAD"), argsOnly = true, require = 0)
    private DebugScreenDisplayer mytic$hide(DebugScreenDisplayer d) {
        if (!StreamerModule.hidesCoords()) return d;
        return new DebugScreenDisplayer() {
            @Override
            public void addPriorityLine(String line) {
                d.addPriorityLine(hide(line));
            }

            @Override
            public void addLine(String line) {
                d.addLine(hide(line));
            }

            @Override
            public void addToGroup(Identifier group, Collection<String> lines) {
                d.addToGroup(group, lines.stream().map(F3Mixin::hide).toList());
            }

            @Override
            public void addToGroup(Identifier group, String line) {
                d.addToGroup(group, hide(line));
            }
        };
    }

    private static String hide(String line) {
        if (line == null) return null;
        int colon = line.indexOf(':');
        return line.matches("(?s).*-?\\d+.*") && colon > 0 ? line.substring(0, colon + 1) + " ???" : line;
    }
}
