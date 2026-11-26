package de.myticlegacy.client.diag;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Klasse → Mod-ID über die Wurzeln der Fabric-Mods (eingebettete Mods werden ihrer Eltern-Mod zugerechnet). */
public final class FabricMods implements SamplingProfiler.ModResolver {
    private final List<ModContainer> mods = new ArrayList<>();
    private final Map<String, ModContainer> byPackage = new HashMap<>();

    public FabricMods() {
        for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
            String id = m.getMetadata().getId();
            if (id.equals("java")) continue;
            mods.add(m);
        }
    }

    @Override
    public String modOf(String className) {
        String file = className.replace('.', '/') + ".class";
        int dot = className.lastIndexOf('.');
        String pkg = dot < 0 ? "" : className.substring(0, dot);
        ModContainer guess = byPackage.get(pkg);
        if (guess != null && contains(guess, file)) return top(guess);
        for (ModContainer m : mods) {
            if (m != guess && contains(m, file)) {
                byPackage.put(pkg, m);
                return top(m);
            }
        }
        return null;
    }

    private static boolean contains(ModContainer m, String file) {
        try {
            for (Path root : m.getRootPaths()) if (Files.exists(root.resolve(file))) return true;
        } catch (Exception ignored) {
            // Wurzel nicht lesbar
        }
        return false;
    }

    /** Fabric-API-Module usw. der Eltern-Mod zurechnen */
    private static String top(ModContainer m) {
        ModContainer cur = m;
        for (int i = 0; i < 4; i++) {
            ModContainer parent = cur.getContainingMod().orElse(null);
            if (parent == null) break;
            cur = parent;
        }
        return cur.getMetadata().getId();
    }
}
