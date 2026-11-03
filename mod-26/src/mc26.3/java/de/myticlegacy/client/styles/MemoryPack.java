package de.myticlegacy.client.styles;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackMetadataResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** „Mytic Styles“ als Paket im Arbeitsspeicher – Fassung für 26.3 (getrennte Metadaten- und Inhalts-Schnittstelle). */
final class MemoryPack extends AbstractPackMetadataResources implements PackResources {
    MemoryPack(PackLocationInfo info) {
        super(info);
    }

    static Pack.ResourcesSupplier supplier() {
        return new Pack.ResourcesSupplier() {
            @Override
            public PackMetadataResources openMetadata(PackLocationInfo info) {
                return new MemoryPack(info);
            }

            @Override
            public Stream<PackResources> openResources(PackLocationInfo info, Pack.Metadata meta) {
                return Stream.of(new MemoryPack(info));
            }
        };
    }

    private static IoSupplier<InputStream> supply(byte[] data) {
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return String.join("/", path).equals("pack.mcmeta") ? supply(MergedPack.meta()) : null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        return supply(MergedPack.content().get("assets/" + id.getNamespace() + "/" + id.getPath()));
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
        if (type != PackType.CLIENT_RESOURCES) return;
        String prefix = "assets/" + namespace + "/" + path + "/";
        for (Map.Entry<String, byte[]> e : MergedPack.content().entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            Identifier id = Identifier.tryBuild(namespace, e.getKey().substring(("assets/" + namespace + "/").length()));
            if (id != null) out.accept(id, supply(e.getValue()));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> out = new HashSet<>();
        if (type != PackType.CLIENT_RESOURCES) return out;
        for (String k : MergedPack.content().keySet()) {
            int a = k.indexOf('/'), b = k.indexOf('/', a + 1);
            if (a > 0 && b > a) out.add(k.substring(a + 1, b));
        }
        return out;
    }

    @Override
    public void close() {
    }
}
