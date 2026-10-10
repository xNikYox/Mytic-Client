package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;

/**
 * Auto Text wie bei Lunar: Tasten, die einen Text in den Chat schreiben oder einen Befehl ausführen
 * (z. B. „gg“ oder „/lobby“). Die Tasten werden unter Optionen → Steuerung → Mytic Client belegt.
 */
public class AutoTextModule extends Module {
    public static final int SLOTS = 6;
    private final TextSetting[] texts = new TextSetting[SLOTS];

    public AutoTextModule() {
        super("autotext", "Auto Text", "Tasten senden einen Text oder Befehl (Belegung: Optionen → Steuerung)", Category.MECHANIK, () -> Items.writable_book, false);
        String[] defaults = {"gg", "/lobby", "", "", "", ""};
        for (int i = 0; i < SLOTS; i++) {
            final int slot = i;
            texts[i] = new TextSetting(this, "text" + (i + 1), "Text " + (i + 1), defaults[i], () -> keyName(slot));
        }
    }

    private static String keyName(int slot) {
        KeyBinding key = MyticClient.autoTextKeys[slot];
        if (key == null || key.getKeyCode() == 0) return "keine Taste";
        return GameSettings.getKeyDisplayString(key.getKeyCode());
    }

    public void tick(Minecraft mc) {
        for (int i = 0; i < SLOTS; i++) {
            KeyBinding key = MyticClient.autoTextKeys[i];
            if (key == null) continue;
            while (key.isPressed()) {
                String text = texts[i].get().trim();
                // 1.8.9: Befehle und Chat gehen beide über sendChatMessage
                if (enabled() && mc.thePlayer != null && !text.isEmpty()) mc.thePlayer.sendChatMessage(text);
            }
        }
    }
}
