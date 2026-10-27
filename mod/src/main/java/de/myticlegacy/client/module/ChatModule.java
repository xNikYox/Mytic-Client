package de.myticlegacy.client.module;

import de.myticlegacy.client.mixin.boost.ChatComponentAccessor;
import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Items;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Chat: Zeitstempel, gleiche Nachrichten stapeln („×3“), längerer Verlauf */
public class ChatModule extends Module {
    private static ChatModule instance;
    public final BoolSetting timestamps;
    public final BoolSetting seconds;
    public final BoolSetting stack;
    public final BoolSetting longHistory;
    private String lastText;
    private int lastCount;

    public ChatModule() {
        super("chat", "Chat", "Zeitstempel, gleiche Nachrichten stapeln, längerer Verlauf", Category.CLIENT, () -> Items.WRITABLE_BOOK, true);
        timestamps = new BoolSetting(this, "timestamps", "Zeitstempel", true);
        seconds = new BoolSetting(this, "seconds", "Mit Sekunden", false);
        stack = new BoolSetting(this, "stack", "Gleiche Nachrichten stapeln (×2, ×3 …)", true);
        longHistory = new BoolSetting(this, "longHistory", "Längerer Verlauf (500 statt 100 Zeilen)", true);
        instance = this;
    }

    public static int historySize(int vanilla) {
        return instance != null && instance.enabled() && instance.longHistory.get() ? 500 : vanilla;
    }

    public static Component decorate(ChatComponent chat, Component message) {
        ChatModule m = instance;
        if (m == null || !m.enabled() || message == null) return message;
        String text = message.getString();
        Component shown = message;
        if (m.stack.get()) {
            if (text.equals(m.lastText) && !text.isBlank()) {
                m.lastCount++;
                removeNewest((ChatComponentAccessor) chat);
                shown = Component.empty().append(message).append(Component.literal(" ×" + m.lastCount).withColor(0xFF9AA0AA));
            } else {
                m.lastText = text;
                m.lastCount = 1;
            }
        }
        if (m.timestamps.get()) {
            String t = LocalTime.now().format(DateTimeFormatter.ofPattern(m.seconds.get() ? "HH:mm:ss" : "HH:mm"));
            MutableComponent out = Component.literal("[" + t + "] ").withColor(0xFF7C8799);
            shown = out.append(shown);
        }
        return shown;
    }

    /** Neueste Nachricht (und ihre Zeilen) entfernen – sie wird mit Zähler neu eingefügt */
    private static void removeNewest(ChatComponentAccessor chat) {
        List<GuiMessage> all = chat.mytic$all();
        if (all.isEmpty()) return;
        all.remove(0);
        chat.mytic$refresh(); // Anzeigezeilen aus den übrigen Nachrichten neu aufbauen
    }
}
