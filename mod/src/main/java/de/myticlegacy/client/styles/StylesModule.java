package de.myticlegacy.client.styles;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.module.Category;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.setting.ActionSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * Mytic Styles: Texturpakete aus dem resourcepacks-Ordner nach Kategorien mischen (Schwert aus Paket A, GUI aus
 * Paket B …). An/Aus im Mod-Menü, Menü über eigene Taste (Standard B) oder die Einstellung „Styles-Menü“.
 */
public class StylesModule extends Module {
    private static StylesModule instance;
    private KeyMapping key;
    private Boolean lastEnabled;
    /** Aussehen der Minecraft-Menüs (Stil, Farben) beim letzten Laden */
    private String skinKey;
    private int skinCheck;

    public StylesModule() {
        super("styles", "Mytic Styles", "Texturpakete mischen: Schwert, Totem, GUI … aus verschiedenen Paketen", Category.VISUELL, () -> Items.PAINTING, true);
        new ActionSetting(this, "open", "Styles-Menü", () -> "Öffnen", () -> Compat.setScreen(new StylesScreen(Compat.screen(Minecraft.getInstance()))));
        instance = this;
    }

    /** Wird die Auswahl gerade angewendet? (aus dem Lade-Thread von Minecraft gelesen) */
    public static boolean active() {
        return instance == null || instance.enabled();
    }

    public void registerKey() {
        key = Compat.registerKey("key.myticclient.styles", InputConstants.KEY_B);
    }

    public void tick(Minecraft mc) {
        if (key != null) while (key.consumeClick()) if (Compat.screen(mc) == null) Compat.setScreen(new StylesScreen(null));
        boolean on = enabled();
        boolean reload = lastEnabled != null && lastEnabled != on && !Selection.isEmpty(); // an/aus: Texturen neu laden
        lastEnabled = on;
        // Menü-Stil oder Farben geändert: Grafiken der Minecraft-Menüs neu erzeugen – erst wenn das Mod-Menü zu ist
        if (++skinCheck >= 20) {
            skinCheck = 0;
            String key = GuiSkin.key();
            boolean editing = Compat.screen(mc) instanceof de.myticlegacy.client.gui.ModMenuScreen;
            if (skinKey == null) skinKey = key;
            else if (!key.equals(skinKey) && !editing) {
                skinKey = key;
                reload = true;
            }
        }
        if (reload) MergedPack.reload();
    }
}
