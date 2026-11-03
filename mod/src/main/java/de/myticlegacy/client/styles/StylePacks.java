package de.myticlegacy.client.styles;

import de.myticlegacy.client.MyticClient;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Alle Pakete aus dem resourcepacks-Ordner: jedes erscheint unter „Komplett“ und zusätzlich als Teil in jeder
 * Kategorie, für die es Dateien hat (Schwert, Totem, GUI …) – so lassen sich Pakete mischen.
 */
public final class StylePacks {
    private static volatile List<StylePack> packs = List.of();
    private static volatile boolean scanned;

    private StylePacks() {
    }

    public static Path dir() {
        return Minecraft.getInstance().getResourcePackDirectory();
    }

    public static boolean scanned() {
        return scanned;
    }

    public static List<StylePack> all() {
        return packs;
    }

    public static List<StylePack> of(Category c) {
        return packs.stream().filter(p -> p.category() == c).toList();
    }

    public static StylePack byId(String id) {
        for (StylePack p : packs) if (p.id().equals(id)) return p;
        return null;
    }

    static synchronized void ensureScanned() {
        if (!scanned) rescan();
    }

    /** Ordner neu einlesen (beim ersten Bedarf und bei „Neu laden“) */
    public static synchronized List<StylePack> rescan() {
        List<StylePack> out = new ArrayList<>();
        Path dir = dir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                for (Path p : s.sorted().toList()) if (isPack(p)) addWithParts(new PackFiles(p), out);
            } catch (IOException e) {
                MyticClient.LOG.warn("Mytic Styles: Ordner {} nicht lesbar", dir, e);
            }
        }
        out.sort(Comparator.comparing((StylePack p) -> p.category().ordinal()).thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
        packs = List.copyOf(out);
        scanned = true;
        return packs;
    }

    private static void addWithParts(PackFiles p, List<StylePack> out) {
        if (p.read("pack.mcmeta") == null) return; // kein Ressourcenpaket (oder Inhalt in einem Unterordner)
        String file = p.path().getFileName().toString();
        String name = prettify(stripZip(file));
        out.add(new StylePack(file, Category.WHOLE, name, p, p.read("pack.png"), null, PackSniffer.collage(p)));
        for (Map.Entry<Category, Integer> e : PackSniffer.scan(p).entrySet()) {
            Category c = e.getKey();
            List<byte[]> tiers = PackSniffer.tierPreview(p, c);
            byte[] preview = tiers.isEmpty() ? PackSniffer.preview(p, c) : null;
            Set<String> files = PackSniffer.partFiles(p, c);
            if (files.isEmpty()) continue;
            byte[] icon = preview != null ? preview : tiers.isEmpty() ? p.read("pack.png") : null;
            out.add(new StylePack(file + "#" + c.key(), c, name, p, icon, files, tiers));
        }
    }

    private static boolean isPack(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isRegularFile(p)) return n.endsWith(".zip");
        return Files.isDirectory(p) && Files.exists(p.resolve("pack.mcmeta"));
    }

    private static String stripZip(String file) {
        return file.toLowerCase(Locale.ROOT).endsWith(".zip") ? file.substring(0, file.length() - 4) : file;
    }

    private static final Pattern COLOR = Pattern.compile("§.");

    /** "flammen_rot-2" → "Flammen Rot 2" (Farbcodes wie §b werden entfernt) */
    static String prettify(String raw) {
        StringBuilder b = new StringBuilder();
        Matcher m = COLOR.matcher(raw);
        for (String w : m.replaceAll("").split("[_\\-\\s]+")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.isEmpty() ? raw : b.toString();
    }
}
