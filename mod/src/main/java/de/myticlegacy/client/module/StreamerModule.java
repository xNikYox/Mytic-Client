package de.myticlegacy.client.module;

import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streamer-Modus: versteckt je nach Einstellung den eigenen Namen (Chat, Tab-Liste, Namensschild), die Server-IP,
 * Koordinaten (HUD, F3, Chat) und den Inhalt privater Nachrichten. Mit eigener Taste schnell an/aus.
 */
public class StreamerModule extends Module {
    private static StreamerModule instance;
    public final BoolSetting hideName;
    public final TextSetting fakeName;
    public final BoolSetting hideIp;
    public final BoolSetting hideCoords;
    public final BoolSetting hideMessages;
    private static final Pattern PM = Pattern.compile("^(\\[[^\\]]{1,20}\\]\\s*)?([.*]?[A-Za-z0-9_]{2,16})\\s*(->|→|»|➜)\\s*([.*]?[A-Za-z0-9_]{2,16})\\s*:\\s*.+$|(whispers to you|flüstert dir|tells you|sagt dir)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COORDS = Pattern.compile("(?i)\\b([xyz])\\s*[:=]\\s*-?\\d+(\\.\\d+)?|(-?\\d{2,8})(\\s*[ ,/]\\s*)(-?\\d{1,3})(\\s*[ ,/]\\s*)(-?\\d{2,8})");

    public StreamerModule() {
        super("streamer", "Streamer-Modus", "Name, Server-IP, Koordinaten und private Nachrichten verstecken", Category.CLIENT, () -> Items.ENDER_EYE, false);
        hideName = new BoolSetting(this, "hideName", "Eigenen Namen verstecken", true);
        fakeName = new TextSetting(this, "fakeName", "Ersatzname", "Spieler", () -> "z. B. dein Twitch-Name");
        hideIp = new BoolSetting(this, "hideIp", "Server-IP verstecken", true);
        hideCoords = new BoolSetting(this, "hideCoords", "Koordinaten verstecken (HUD, F3, Chat)", true);
        hideMessages = new BoolSetting(this, "hideMessages", "Private Nachrichten verdecken", true);
        instance = this;
    }

    private static boolean on() {
        return instance != null && instance.enabled();
    }

    public static boolean hidesIp() {
        return on() && instance.hideIp.get();
    }

    public static boolean hidesCoords() {
        return on() && instance.hideCoords.get();
    }

    private static String ownName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() == null ? null : mc.getUser().getName();
    }

    private static String fake() {
        String f = instance.fakeName.get();
        return f == null || f.isBlank() ? "Spieler" : f.trim();
    }

    /** Text (Chat, Tab-Liste, Namensschild, F3) filtern; Farben und Stile bleiben erhalten */
    public static Component filter(Component in) {
        if (!on() || in == null) return in;
        String plain = in.getString();
        if (instance.hideMessages.get() && PM.matcher(plain).find()) {
            int colon = plain.indexOf(':');
            String head = colon > 0 && !plain.toLowerCase().contains("whispers") ? plain.substring(0, colon + 1) : "Private Nachricht:";
            return Component.literal(replaceText(head) + " ••••••").withColor(0xFF9AA0AA);
        }
        String name = ownName();
        boolean name1 = instance.hideName.get() && name != null && plain.contains(name);
        boolean coords = instance.hideCoords.get() && COORDS.matcher(plain).find();
        if (!name1 && !coords) return in;
        MutableComponent out = Component.empty();
        in.visit((style, text) -> {
            out.append(Component.literal(replaceText(text)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** Für einfachen Text (F3-Zeilen) */
    public static String filter(String text) {
        return on() ? replaceText(text) : text;
    }

    private static String replaceText(String text) {
        if (text == null || text.isEmpty()) return text;
        String name = ownName();
        if (instance.hideName.get() && name != null && !name.isEmpty()) text = text.replace(name, fake());
        if (instance.hideCoords.get()) {
            Matcher m = COORDS.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String r = m.group(1) != null ? m.group(1) + ": ???" : "??? ??? ???";
                m.appendReplacement(sb, Matcher.quoteReplacement(r));
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }
}
