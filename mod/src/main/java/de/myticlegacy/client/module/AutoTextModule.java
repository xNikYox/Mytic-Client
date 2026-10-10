package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.TextSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/**
 * Auto Text wie bei Lunar: Tasten, die einen Text in den Chat schreiben oder einen Befehl ausführen
 * (z. B. „gg“ oder „/lobby“). Die Tasten werden unter Optionen → Steuerung → Mytic Client belegt.
 */
public class AutoTextModule extends Module {
    public static final int SLOTS = 6;
    private final TextSetting[] texts = new TextSetting[SLOTS];

    public AutoTextModule() {
        super("autotext", "Auto Text", "Tasten senden einen Text oder Befehl (Belegung: Optionen → Steuerung)", Category.MECHANIK, () -> Items.WRITABLE_BOOK, false);
        String[] defaults = {"gg", "/lobby", "", "", "", ""};
        for (int i = 0; i < SLOTS; i++) {
            final int slot = i;
            texts[i] = new TextSetting(this, "text" + (i + 1), "Text " + (i + 1), defaults[i], () -> keyName(slot));
        }
    }

    private static String keyName(int slot) {
        KeyMapping key = MyticClient.autoTextKeys[slot];
        if (key == null || key.isUnbound()) return "keine Taste";
        return key.getTranslatedKeyMessage().getString();
    }

    public void tick(Minecraft mc) {
        for (int i = 0; i < SLOTS; i++) {
            KeyMapping key = MyticClient.autoTextKeys[i];
            if (key == null) continue;
            while (key.consumeClick()) {
                if (enabled() && mc.player != null) send(mc, texts[i].get().trim());
            }
        }
    }

    private static void send(Minecraft mc, String text) {
        if (text.isEmpty()) return;
        if (text.startsWith("/")) mc.player.connection.sendCommand(text.substring(1));
        else mc.player.connection.sendChat(text);
    }
}
