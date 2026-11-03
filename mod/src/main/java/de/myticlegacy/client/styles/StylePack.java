package de.myticlegacy.client.styles;

import java.util.List;
import java.util.Set;

/**
 * Ein auswählbarer Eintrag: ein ganzes Paket (Kategorie „Komplett“) oder ein Teil davon (nur die Dateien einer
 * Kategorie plus alles, worauf deren Modelle verweisen).
 *
 * @param id       eindeutige Kennung, z. B. "faithful.zip" oder "faithful.zip#sword"
 * @param files    nur diese Dateien ("minecraft:textures/…"; null = ganzes Paket)
 * @param icon     Vorschaubild (PNG) oder null
 * @param collage  Bilder für die 2×2- bzw. Material-Vorschau (leer = icon)
 */
public record StylePack(String id, Category category, String name, PackFiles source, byte[] icon, Set<String> files, List<byte[]> collage) {
}
