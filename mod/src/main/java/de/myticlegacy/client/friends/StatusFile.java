package de.myticlegacy.client.friends;

import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kleine Statusdatei für den Launcher (Discord-Anzeige "auf &lt;Server&gt;"): Der Launcher gibt den Pfad als
 * -Dmytic.status.file mit und liest die Datei lokal – so muss er den Server nicht beim Friends-Server erfragen.
 * Inhalt: {"place":"game","server":…|null,"version":…,"ts":…}. Geschrieben bei jeder Änderung und spätestens jede Minute.
 * (Gleicher Code für alle Minecraft-Versionen, daher Java 8.)
 */
public final class StatusFile {
    private static final String PATH = System.getProperty("mytic.status.file", "").trim();
    private static final long KEEPALIVE_MS = 60_000;
    private static String last;
    private static long writtenAt;
    private static ExecutorService writer;

    private StatusFile() {
    }

    /** Aus dem Spiel-Thread, jeden Tick: server null = Menü/Server verborgen, "Einzelspieler" = eigene Welt. */
    public static void update(String server, String version) {
        if (PATH.isEmpty()) return;
        String key = server + "|" + version;
        long now = System.currentTimeMillis();
        if (key.equals(last) && now - writtenAt < KEEPALIVE_MS) return;
        last = key;
        writtenAt = now;
        JsonObject o = new JsonObject();
        o.addProperty("place", "game");
        o.addProperty("server", server);
        o.addProperty("version", version);
        o.addProperty("ts", now);
        final byte[] data = o.toString().getBytes(StandardCharsets.UTF_8);
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Mytic Status");
                t.setDaemon(true);
                return t;
            });
        }
        writer.execute(() -> write(data));
    }

    private static void write(byte[] data) {
        try {
            Path target = new File(PATH).toPath();
            Path dir = target.getParent();
            if (dir != null) Files.createDirectories(dir);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                // z. B. Windows, während der Launcher die Datei gerade liest: direkt schreiben
                Files.write(target, data);
                Files.deleteIfExists(tmp);
            }
        } catch (Exception ignored) {
            // nur für Discord – nächster Versuch bei der nächsten Änderung
        }
    }
}
