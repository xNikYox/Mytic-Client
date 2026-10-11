package de.myticlegacy.client.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cosmetics für diese Minecraft-Version: Umhang-Texturen aus dem Cosmetics-Paket (Fabric lädt es als Mod). */
public final class Cosmetics {
    private static final Map<String, ClientAsset.Texture[]> TEXTURES = new HashMap<>();

    private Cosmetics() {
    }

    private static ClientAsset.Texture texture(CosmeticsCore.Cape cape) {
        ClientAsset.Texture[] frames = TEXTURES.computeIfAbsent(cape.id, id -> {
            ClientAsset.Texture[] list = new ClientAsset.Texture[cape.frames];
            for (int i = 0; i < cape.frames; i++) list[i] = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("myticclient", "cape/" + id + "_" + i));
            return list;
        });
        return frames[cape.frame()];
    }

    private static String uuid(Player player) {
        return player.getUUID().toString().replace("-", "");
    }

    /** Umhang für diesen Spieler oder null (dann bleibt der normale). */
    public static ClientAsset.Texture cape(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && player.getUUID().equals(mc.player.getUUID());
        CosmeticsCore.Cape cape = CosmeticsCore.capeOf(uuid(player), self);
        return cape == null ? null : texture(cape);
    }

    /** Trägt dieser Umhang ein Mytic-Cosmetic? (für die Wind-Animation) */
    public static boolean isMytic(ClientAsset.Texture cape) {
        return cape != null && "myticclient".equals(cape.id().getNamespace());
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
