package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.compat.Blit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    /**
     * Vorschau im Cosmetics-Menü: Außenseite des Umhangs direkt aus der Textur (volle Auflösung). Nur animiert, wenn
     * gewünscht (ausgewählt oder unter der Maus) – sonst das erste Bild, damit nicht alle Bilder geladen werden.
     */
    public static boolean drawPreview(GuiGraphics g, CosmeticsCore.Cape cape, int x, int y, int w, int h, boolean animated) {
        int k = cape.scale;
        int frame = animated ? cape.frame() : 0;
        Blit.region(g, ResourceLocation.fromNamespaceAndPath("myticclient", "textures/cape/" + cape.id + "_" + frame + ".png"),
                x, y, w, h, k, k, 10 * k, 16 * k, 64 * k, 32 * k);
        return true;
    }

    /** Flügel dieser Figur oder null. */
    public static String wingsFor(Object entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!(entity instanceof net.minecraft.world.entity.Entity e) || mc.player == null) return null;
        return CosmeticsCore.wingsOf(e.getUUID().toString().replace("-", ""), e.getUUID().equals(mc.player.getUUID()));
    }

    /** Ein Aura-Sprite im Menü (aus aura/<id>_icon.png: Sprites je 32x32 nebeneinander). */
    public static void drawAuraSprite(GuiGraphics g, CosmeticsCore.Aura aura, int sprite, int x, int y, int size, int fallback) {
        Blit.region(g, ResourceLocation.fromNamespaceAndPath("myticclient", "textures/aura/" + aura.id + "_icon.png"), x, y, size, size, sprite * 32, 0, 32, 32, 32 * aura.sprites, 32);
    }

    /** Vorschau eines Schilds im Cosmetics-Menü: Vorderseite (6,6 / 72x132 in der 384x384-Textur), animiert wenn gewünscht. */
    public static void drawShieldPreview(GuiGraphics g, String id, int x, int y, int w, int h, boolean animated) {
        int frame = animated ? CosmeticsCore.shieldFrame(id) : 0;
        Blit.region(g, ResourceLocation.fromNamespaceAndPath("myticclient", "textures/shield/" + id + "_" + frame + ".png"), x, y, w, h, 6, 6, 72, 132, 384, 384);
    }

    /** Vorschau eines Flügelpaars im Cosmetics-Menü (eigenes kleines Vorschaubild aus dem Paket) mit animiertem Glanz. */
    public static void drawWingPreview(GuiGraphics g, String id, int x, int y, int w, int h) {
        Blit.region(g, ResourceLocation.fromNamespaceAndPath("myticclient", "textures/wings/" + id + "_icon.png"), x, y, w, h, 0, 0, 240, 144, 240, 144);
        long frame = System.currentTimeMillis() / CosmeticsCore.WING_FX_MS % CosmeticsCore.WING_FX_FRAMES;
        Blit.region(g, ResourceLocation.fromNamespaceAndPath("myticclient", "textures/wings/" + id + "_icon_fx_" + frame + ".png"), x, y, w, h, 0, 0, 240, 144, 240, 144);
    }

    /** Eigener Spielername (Vorschau im Nametag-Editor) */
    public static String playerName() {
        return Minecraft.getInstance().getUser().getName();
    }
}
