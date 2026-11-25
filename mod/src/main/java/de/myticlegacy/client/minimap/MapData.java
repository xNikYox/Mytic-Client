package de.myticlegacy.client.minimap;

import de.myticlegacy.client.MyticClient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Erkundete Karte einer Welt/Dimension: je Chunk 16 × 16 Farben (ARGB, 0 = unbekannt). Nur Oberflächen-Farben
 * geladener Chunks, die der Spieler selbst gesehen hat. Gespeichert in Regionsdateien (32 × 32 Chunks, gzip),
 * Laden und Speichern im Hintergrund.
 */
public final class MapData {
    private static final int MAGIC = 0x4D4D5031; // "MMP1"
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mytic Minimap IO");
        t.setDaemon(true);
        return t;
    });

    public final String key;
    private final Path dir;
    private final Map<Long, int[]> chunks = new ConcurrentHashMap<>();
    private final Set<Long> requested = ConcurrentHashMap.newKeySet();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    /** Zählt jede Änderung (Minimap/Weltkarte zeichnen dann neu) */
    private final AtomicInteger changes = new AtomicInteger();
    /** Änderungszähler je 128er-Kachel der Weltkarte */
    private final Map<Long, Integer> tileChanges = new ConcurrentHashMap<>();

    public MapData(String key, Path dir) {
        this.key = key;
        this.dir = dir;
    }

    public static long pack(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    public int changes() {
        return changes.get();
    }

    public int tileChanges(int tx, int tz) {
        return tileChanges.getOrDefault(pack(tx, tz), 0);
    }

    private void touch(int cx, int cz) {
        changes.incrementAndGet();
        tileChanges.merge(pack(cx >> 3, cz >> 3), 1, Integer::sum);
    }

    // ------------------------------------------------------------------------------------------ Lesen

    /** Farben eines Chunks (nicht verändern) oder null; lädt die Region bei Bedarf im Hintergrund */
    public int[] chunk(int cx, int cz) {
        int[] c = chunks.get(pack(cx, cz));
        if (c == null) request(cx >> 5, cz >> 5);
        return c;
    }

    /** Farbe eines Blocks (ARGB, 0 = unbekannt) */
    public int color(int x, int z) {
        int[] c = chunk(x >> 4, z >> 4);
        return c == null ? 0 : c[(z & 15) << 4 | (x & 15)];
    }

    // ------------------------------------------------------------------------------------------ Schreiben

    /** Neue Farben eines Chunks übernehmen; true = etwas hat sich geändert */
    public boolean put(int cx, int cz, int[] colors) {
        long k = pack(cx, cz);
        int[] old = chunks.get(k);
        if (old != null && java.util.Arrays.equals(old, colors)) return false;
        chunks.put(k, colors);
        dirty.add(pack(cx >> 5, cz >> 5));
        touch(cx, cz);
        return true;
    }

    public int chunkCount() {
        return chunks.size();
    }

    // ------------------------------------------------------------------------------------------ Dateien

    private Path file(int rx, int rz) {
        return dir.resolve("r." + rx + "." + rz + ".mmap");
    }

    private void request(int rx, int rz) {
        long r = pack(rx, rz);
        if (!requested.add(r)) return;
        IO.execute(() -> load(rx, rz));
    }

    private void load(int rx, int rz) {
        Path f = file(rx, rz);
        if (!Files.isRegularFile(f)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(f))))) {
            if (in.readInt() != MAGIC) return;
            int n = in.readInt();
            byte[] rgb = new byte[256 * 3];
            for (int i = 0; i < n && i < 1024; i++) {
                int idx = in.readUnsignedShort();
                in.readFully(rgb);
                int cx = (rx << 5) + (idx & 31), cz = (rz << 5) + (idx >> 5);
                int[] c = new int[256];
                for (int p = 0; p < 256; p++) {
                    int v = (rgb[p * 3] & 255) << 16 | (rgb[p * 3 + 1] & 255) << 8 | (rgb[p * 3 + 2] & 255);
                    c[p] = v == 0 ? 0 : 0xFF000000 | v;
                }
                // frisch gescannte Daten haben Vorrang vor der Datei
                if (chunks.putIfAbsent(pack(cx, cz), c) == null) touch(cx, cz);
            }
        } catch (IOException | RuntimeException e) {
            MyticClient.LOG.warn("Minimap: Region {} {} nicht lesbar: {}", rx, rz, e.toString());
        }
    }

    /** Geänderte Regionen im Hintergrund speichern */
    public void save() {
        if (dirty.isEmpty()) return;
        List<Long> regions = new ArrayList<>(dirty);
        dirty.removeAll(regions);
        for (long r : regions) {
            int rx = (int) (r >> 32), rz = (int) r;
            // Momentaufnahme jetzt (Hauptthread), Schreiben im Hintergrund. Die Farb-Felder werden nie verändert, nur
            // ersetzt (put/load legen neue an) – deshalb reicht die Referenz, ohne Kopie (spart bis zu 1 MB je Region)
            Map<Integer, int[]> snap = new HashMap<>();
            for (int i = 0; i < 1024; i++) {
                int[] c = chunks.get(pack((rx << 5) + (i & 31), (rz << 5) + (i >> 5)));
                if (c != null) snap.put(i, c);
            }
            IO.execute(() -> write(rx, rz, snap));
        }
    }

    private void write(int rx, int rz, Map<Integer, int[]> snap) {
        try {
            Files.createDirectories(dir);
            Path f = file(rx, rz), tmp = dir.resolve(f.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                out.writeInt(MAGIC);
                out.writeInt(snap.size());
                byte[] rgb = new byte[256 * 3];
                for (Map.Entry<Integer, int[]> e : snap.entrySet()) {
                    out.writeShort(e.getKey());
                    int[] c = e.getValue();
                    for (int p = 0; p < 256; p++) {
                        int v = (c[p] >>> 24) == 0 ? 0 : c[p];
                        rgb[p * 3] = (byte) (v >> 16);
                        rgb[p * 3 + 1] = (byte) (v >> 8);
                        rgb[p * 3 + 2] = (byte) v;
                    }
                    out.write(rgb);
                }
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            MyticClient.LOG.warn("Minimap: Region {} {} nicht gespeichert: {}", rx, rz, e.toString());
        }
    }

    /** Alles dieser Welt/Dimension vergessen (Dateien werden gelöscht) */
    public void clear() {
        chunks.clear();
        dirty.clear();
        changes.incrementAndGet();
        tileChanges.clear();
        IO.execute(() -> {
            try (var files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".mmap")).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // nächstes Mal
                    }
                });
            } catch (IOException ignored) {
                // Ordner gibt es (noch) nicht
            }
        });
    }
}
