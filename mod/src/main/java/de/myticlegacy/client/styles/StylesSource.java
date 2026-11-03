package de.myticlegacy.client.styles;

import de.myticlegacy.client.MyticClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.util.Optional;
import java.util.function.Consumer;

/** Meldet „Mytic Styles“ bei Minecraft an: immer aktiv, ganz oben, Inhalt nur im Arbeitsspeicher. */
public final class StylesSource implements RepositorySource {
    public static final StylesSource INSTANCE = new StylesSource();
    private static final PackSource SOURCE = PackSource.create(c -> Component.empty().append(c).append(Component.literal(" (Mytic)").withColor(0x9B5CFF)), false);

    private StylesSource() {
    }

    @Override
    public void loadPacks(Consumer<Pack> out) {
        PackLocationInfo info = new PackLocationInfo(MergedPack.PACK_ID, Component.literal("Mytic Styles"), SOURCE, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, MemoryPack.supplier(), PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, true));
        if (pack != null) out.accept(pack);
        else MyticClient.LOG.error("Mytic Styles: Paket konnte nicht angelegt werden");
    }
}
