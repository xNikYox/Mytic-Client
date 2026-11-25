package de.myticlegacy.client.gui.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Köpfe beliebiger Minecraft-Konten (z. B. Konten im Launcher) über die öffentliche Mojang-Profil-Schnittstelle:
 * Skin laden, Gesicht und Hut-Ebene (8 × 8) zu 64 ARGB-Pixeln zusammensetzen – wie die Köpfe der Freundesliste.
 * Einmal je UUID im Hintergrund, Ergebnis bleibt im Speicher.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class SkinHeads {
    private static final int[] NONE = new int[0];
    private static final Map<String, int[]> CACHE = new ConcurrentHashMap<String, int[]>();
    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"(https?://textures\\.minecraft\\.net/texture/[0-9a-fA-F]+)\"");
    private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");

    private SkinHeads() {
    }

    /** 64 Pixel oder null (lädt noch bzw. nicht verfügbar) */
    public static int[] get(String uuid) {
        if (uuid == null) return null;
        final String id = uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        if (!id.matches("[0-9a-f]{32}")) return null;
        int[] cached = CACHE.get(id);
        if (cached != null) return cached.length == 64 ? cached : null;
        CACHE.put(id, NONE);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    String profile = read("https://sessionserver.mojang.com/session/minecraft/profile/" + id, 64 * 1024);
                    Matcher v = VALUE.matcher(profile);
                    if (!v.find()) return;
                    String textures = new String(java.util.Base64.getDecoder().decode(v.group(1)), StandardCharsets.UTF_8);
                    Matcher u = URL_PATTERN.matcher(textures);
                    if (!u.find()) return;
                    HttpURLConnection c = open(u.group(1));
                    BufferedImage skin;
                    InputStream in = c.getInputStream();
                    try {
                        skin = javax.imageio.ImageIO.read(in);
                    } finally {
                        in.close();
                    }
                    if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) return;
                    int[] px = new int[64];
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            int face = skin.getRGB(8 + x, 8 + y) | 0xFF000000;
                            int hat = skin.getRGB(40 + x, 8 + y);
                            int a = (hat >>> 24) & 0xFF;
                            px[y * 8 + x] = a == 0 ? face : Draw.mix(face, hat | 0xFF000000, a / 255f);
                        }
                    }
                    CACHE.put(id, px);
                } catch (Exception ignored) {
                    // ohne Kopf: Anfangsbuchstabe
                }
            }
        }, "Mytic Skin Head");
        t.setDaemon(true);
        t.start();
        return null;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "MyticClient");
        return c;
    }

    private static String read(String url, int max) throws Exception {
        HttpURLConnection c = open(url);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0 && out.size() < max) out.write(b, 0, n);
        } finally {
            in.close();
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
