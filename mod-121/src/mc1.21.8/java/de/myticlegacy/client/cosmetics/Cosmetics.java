package de.myticlegacy.client.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cosmetics für 1.21–1.21.8 (Umhang als ResourceLocation): Umhang-Texturen aus dem Cosmetics-Paket (Fabric lädt es als Mod). */
public final class Cosmetics {
    private static final Map<String, ResourceLocation[]> TEXTURES = new HashMap<>();

    private Cosmetics() {
    }

    private static ResourceLocation texture(CosmeticsCore.Cape cape) {
        ResourceLocation[] frames = TEXTURES.computeIfAbsent(cape.id, id -> {
            ResourceLocation[] list = new ResourceLocation[cape.frames];
            for (int i = 0; i < cape.frames; i++) list[i] = ResourceLocation.fromNamespaceAndPath("myticclient", "textures/cape/" + id + "_" + i + ".png");
            return list;
        });
        return frames[cape.frame()];
    }

    private static String uuid(Player player) {
        return player.getUUID().toString().replace("-", "");
    }

    /** Umhang für diesen Spieler oder null (dann bleibt der normale). */
    public static ResourceLocation cape(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && player.getUUID().equals(mc.player.getUUID());
        CosmeticsCore.Cape cape = CosmeticsCore.capeOf(uuid(player), self);
        return cape == null ? null : texture(cape);
    }

    /** Trägt dieser Umhang ein Mytic-Cosmetic? (für die Wind-Animation) */
    public static boolean isMytic(ResourceLocation cape) {
        return cape != null && "myticclient".equals(cape.getNamespace());
    }

    /** Alle 2 Sekunden: Umhänge der Spieler in der Nähe beim Friends-Server nachfragen. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.level.getGameTime() % 40 != 0) return;
        List<String> nearby = new ArrayList<>();
        for (Player p : mc.level.players()) if (p != mc.player) nearby.add(uuid(p));
        CosmeticsCore.tick(nearby);
    }

    /**
     * Wind wie bei Feather: zusätzliches sanftes Schwingen, auch im Stehen. Mehrere Sinuswellen, damit es nicht
     * mechanisch wirkt; pro Spieler versetzt.
     */
    public static float[] wind(int seed) {
        double t = System.nanoTime() / 1_000_000_000.0 + (seed & 0xFF) * 0.37;
        float lean = (float) (5 + 3.5 * Math.sin(t * 1.3) + 1.5 * Math.sin(t * 2.9 + 1.1));
        float side = (float) (4 * Math.sin(t * 0.8 + 0.5) + 1.5 * Math.sin(t * 2.1));
        float flap = (float) (1.2 * Math.sin(t * 3.7));
        return new float[]{lean, side, flap};
    }
}
