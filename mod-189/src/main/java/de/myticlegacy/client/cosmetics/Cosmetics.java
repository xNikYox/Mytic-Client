package de.myticlegacy.client.cosmetics;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Cosmetics für 1.8.9: Umhang-Bilder aus dem Cosmetics-Paket als eigene Texturen; der Umhang eines Spielers wird
 * in jedem Bild über NetworkPlayerInfo.locationCape gesetzt (dort liest die Umhang-Ebene von Minecraft).
 */
public final class Cosmetics {
    private static Field capeField;
    /** fertig hochgeladene Bilder je Umhang */
    private static final Map<String, ResourceLocation[]> READY = new HashMap<String, ResourceLocation[]>();
    /** im Hintergrund gelesene Bilder, die noch hochgeladen werden müssen */
    private static final Map<String, BufferedImage[]> DECODED = new ConcurrentHashMap<String, BufferedImage[]>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<String, Boolean>();
    /** erstes Bild je Umhang für die Vorschau im Menü */
    private static final Map<String, ResourceLocation> FIRST = new HashMap<String, ResourceLocation>();
    private static final Map<String, BufferedImage> FIRST_DECODED = new ConcurrentHashMap<String, BufferedImage>();
    private static final Map<String, Boolean> FIRST_LOADING = new ConcurrentHashMap<String, Boolean>();
    /** ursprünglicher Umhang (z. B. Minecon) je Spieler, um ihn zurückzusetzen */
    private static final Map<NetworkPlayerInfo, ResourceLocation> ORIGINAL = new HashMap<NetworkPlayerInfo, ResourceLocation>();

    private Cosmetics() {
    }

    private static String uuid(EntityPlayer player) {
        return player.getUniqueID().toString().replace("-", "");
    }

    private static java.lang.reflect.Method infoMethod;

    /** Die NetworkPlayerInfo, die die Spielfigur selbst benutzt (kann von der Spielerliste abweichen). */
    private static NetworkPlayerInfo info(AbstractClientPlayer player) throws Exception {
        if (infoMethod == null) {
            infoMethod = ReflectionHelper.findMethod(AbstractClientPlayer.class, player, new String[]{"getPlayerInfo", "func_175155_b"});
        }
        return (NetworkPlayerInfo) infoMethod.invoke(player);
    }

    private static Field field() {
        if (capeField == null) capeField = ReflectionHelper.findField(NetworkPlayerInfo.class, "locationCape", "field_178862_f");
        return capeField;
    }

    /** Bilder eines Umhangs im Hintergrund lesen (einmal). */
    private static void request(final CosmeticsCore.Cape cape) {
        if (LOADING.putIfAbsent(cape.id, Boolean.TRUE) != null) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ZipFile zip = new ZipFile(CosmeticsCore.packPath());
                    try {
                        BufferedImage[] frames = new BufferedImage[cape.frames];
                        for (int f = 0; f < cape.frames; f++) {
                            ZipEntry entry = zip.getEntry("assets/myticclient/textures/cape/" + cape.id + "_" + f + ".png");
                            InputStream in = zip.getInputStream(entry);
                            try {
                                frames[f] = ImageIO.read(in);
                            } finally {
                                in.close();
                            }
                        }
                        DECODED.put(cape.id, frames);
                    } finally {
                        zip.close();
                    }
                } catch (Exception e) {
                    MyticClient.LOG.warn("Umhang " + cape.id + " nicht geladen: " + e);
                }
            }
        }, "Mytic Umhang " + cape.id);
        t.setDaemon(true);
        t.start();
    }

    /** Textur des aktuellen Bilds oder null, solange der Umhang noch lädt. */
    private static ResourceLocation texture(CosmeticsCore.Cape cape) {
        ResourceLocation[] frames = READY.get(cape.id);
        if (frames == null) {
            BufferedImage[] images = DECODED.remove(cape.id);
            if (images == null) {
                request(cape);
                return null;
            }
            frames = new ResourceLocation[images.length];
            for (int i = 0; i < images.length; i++) {
                frames[i] = new ResourceLocation("myticclient", "cape/" + cape.id + "_" + i);
                Minecraft.getMinecraft().getTextureManager().loadTexture(frames[i], new DynamicTexture(images[i]));
            }
            READY.put(cape.id, frames);
        }
        return frames[cape.frame()];
    }

    /** In jedem Bild (Render-Thread): Umhänge der Spieler in der Welt setzen. */
    public static void frame(Minecraft mc) {
        if (mc.theWorld == null || mc.getNetHandler() == null || !CosmeticsCore.available()) return;
        try {
            for (EntityPlayer p : mc.theWorld.playerEntities) {
                if (!(p instanceof AbstractClientPlayer)) continue;
                NetworkPlayerInfo info = info((AbstractClientPlayer) p);
                if (info == null) continue;
                CosmeticsCore.Cape cape = CosmeticsCore.capeOf(uuid(p), p == mc.thePlayer);
                ResourceLocation tex = cape == null ? null : texture(cape);
                Field f = field();
                if (tex != null) {
                    if (!ORIGINAL.containsKey(info)) ORIGINAL.put(info, (ResourceLocation) f.get(info));
                    f.set(info, tex);
                } else if (cape == null && ORIGINAL.containsKey(info)) {
                    f.set(info, ORIGINAL.remove(info));
                }
            }
        } catch (Exception e) {
            MyticClient.LOG.warn("Cosmetics: " + e);
        }
    }

    /** Alle 2 Sekunden: Umhänge der Spieler in der Nähe beim Friends-Server nachfragen. */
    public static void tick(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.theWorld.getTotalWorldTime() % 40 != 0) return;
        List<String> nearby = new ArrayList<String>();
        for (EntityPlayer p : mc.theWorld.playerEntities) if (p != mc.thePlayer) nearby.add(uuid(p));
        CosmeticsCore.tick(nearby);
    }

    /** Erstes Bild eines Umhangs (für die Vorschau), im Hintergrund gelesen; null, solange es lädt. */
    private static ResourceLocation firstFrame(final CosmeticsCore.Cape cape) {
        ResourceLocation[] all = READY.get(cape.id);
        if (all != null) return all[0];
        ResourceLocation first = FIRST.get(cape.id);
        if (first != null) return first;
        BufferedImage image = FIRST_DECODED.remove(cape.id);
        if (image != null) {
            first = new ResourceLocation("myticclient", "cape_preview/" + cape.id);
            Minecraft.getMinecraft().getTextureManager().loadTexture(first, new DynamicTexture(image));
            FIRST.put(cape.id, first);
            return first;
        }
        if (FIRST_LOADING.putIfAbsent(cape.id, Boolean.TRUE) == null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ZipFile zip = new ZipFile(CosmeticsCore.packPath());
                        try {
                            InputStream in = zip.getInputStream(zip.getEntry("assets/myticclient/textures/cape/" + cape.id + "_0.png"));
                            try {
                                FIRST_DECODED.put(cape.id, ImageIO.read(in));
                            } finally {
                                in.close();
                            }
                        } finally {
                            zip.close();
                        }
                    } catch (Exception e) {
                        MyticClient.LOG.warn("Vorschau " + cape.id + " nicht geladen: " + e);
                    }
                }
            }, "Mytic Vorschau " + cape.id);
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    /**
     * Vorschau im Cosmetics-Menü: Außenseite des Umhangs direkt aus der Textur (volle Auflösung). Nur animiert, wenn
     * gewünscht (ausgewählt oder unter der Maus) – sonst das erste Bild.
     */
    public static boolean drawPreview(GuiGraphics g, CosmeticsCore.Cape cape, int x, int y, int w, int h, boolean animated) {
        ResourceLocation tex = animated ? texture(cape) : null;
        if (tex == null) tex = firstFrame(cape);
        if (tex == null) return false;
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        int k = cape.scale;
        Gui.drawScaledCustomSizeModalRect(x, y, k, k, 10 * k, 16 * k, w, h, 64 * k, 32 * k);
        return true;
    }
}
