package de.myticlegacy.client.styles;

import de.myticlegacy.client.MyticClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Inhalt eines Ressourcenpakets aus dem resourcepacks-Ordner (Ordner oder .zip): Dateiliste und Lesen. */
public final class PackFiles {
    static final int MAX_FILES = 30000;
    /** Größte Datei, die übernommen wird (schützt vor kaputten oder riesigen Paketen) */
    static final int MAX_FILE = 16 * 1024 * 1024;
    private final Path pack;
    private final boolean zip;
    private List<String> names;

    PackFiles(Path pack) {
        this.pack = pack;
        this.zip = Files.isRegularFile(pack) && pack.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public Path path() {
        return pack;
    }

    /** Alle Dateien ("assets/minecraft/textures/item/x.png", …) */
    public synchronized List<String> names() {
        if (names != null) return names;
        List<String> out = new ArrayList<>();
        try {
            if (!zip) {
                try (Stream<Path> s = Files.walk(pack)) {
                    s.limit(MAX_FILES).filter(Files::isRegularFile).forEach(f -> out.add(pack.relativize(f).toString().replace('\\', '/')));
                }
            } else {
                try (ZipFile z = new ZipFile(pack.toFile())) {
                    Enumeration<? extends ZipEntry> e = z.entries();
                    for (int i = 0; e.hasMoreElements() && i < MAX_FILES; i++) {
                        ZipEntry entry = e.nextElement();
                        if (!entry.isDirectory()) out.add(entry.getName());
                    }
                }
            }
        } catch (IOException e) {
            MyticClient.LOG.warn("Mytic Styles: Inhalt von {} nicht lesbar", pack);
        }
        return names = List.copyOf(out);
    }

    /** Eine Datei lesen oder null */
    public byte[] read(String name) {
        if (name.contains("..")) return null;
        try {
            if (!zip) {
                Path f = pack.resolve(name).normalize();
                return f.startsWith(pack) && Files.isRegularFile(f) && Files.size(f) <= MAX_FILE ? Files.readAllBytes(f) : null;
            }
            try (ZipFile z = new ZipFile(pack.toFile())) {
                ZipEntry e = z.getEntry(name);
                if (e == null || e.getSize() > MAX_FILE) return null;
                try (InputStream in = z.getInputStream(e)) {
                    return in.readNBytes(MAX_FILE);
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** Viele Dateien in einem Durchgang lesen (eine .zip wird nur einmal geöffnet) */
    public void readAll(Predicate<String> wanted, BiConsumer<String, byte[]> out) {
        if (!zip) {
            for (String n : names()) if (wanted.test(n)) {
                byte[] data = read(n);
                if (data != null) out.accept(n, data);
            }
            return;
        }
        try (ZipFile z = new ZipFile(pack.toFile())) {
            Enumeration<? extends ZipEntry> e = z.entries();
            for (int i = 0; e.hasMoreElements() && i < MAX_FILES; i++) {
                ZipEntry entry = e.nextElement();
                String n = entry.getName();
                if (entry.isDirectory() || n.contains("..") || entry.getSize() > MAX_FILE || !wanted.test(n)) continue;
                try (InputStream in = z.getInputStream(entry)) {
                    out.accept(n, in.readNBytes(MAX_FILE));
                }
            }
        } catch (IOException ex) {
            MyticClient.LOG.warn("Mytic Styles: {} nicht lesbar", pack);
        }
    }

    @Override
    public String toString() {
        return pack.getFileName().toString();
    }
}
