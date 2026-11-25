package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.mixin.boost.ChatComponentAccessor;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import de.myticlegacy.client.setting.TextSetting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat+: Zeitstempel (Format wählbar), Kompakt-Chat („(×3)“), Chat-Tabs (Alle, Privat, Mytic, Server und eigene per
 * Wort/Regex), Suche im Chat-Fenster, Hervorheben (eigener Name + Wörter) mit Ton und Hinweis, Spam-/Wortfilter,
 * Strg + Klick kopiert eine Nachricht, längerer Verlauf. Nur Anzeige: Signaturen und gesendete Nachrichten bleiben
 * unverändert (es wird nur der angezeigte Text und die Auswahl der angezeigten Zeilen geändert).
 */
public class ChatModule extends Module {
    private static ChatModule instance;
    public static final String[] TIME_FORMATS = {"[14:05]", "[14:05:09]", "14:05 ›", "[2:05 PM]"};
    private static final String[] TIME_PATTERNS = {"[HH:mm] ", "[HH:mm:ss] ", "HH:mm › ", "[h:mm a] "};
    public static final String[] FIXED_TABS = {"Alle", "Privat", "Mytic", "Server"};
    /** Markierung vor hervorgehobenen Nachrichten */
    private static final String MARK = "▍ ";
    /** Ersatz für ausgefilterte Nachrichten: wird von den Mixins verworfen */
    public static final Component BLOCKED = Component.literal("");

    public final BoolSetting timestamps;
    public final ModeSetting timeFormat;
    public final BoolSetting stack;
    public final SliderSetting history;
    public final BoolSetting tabs;
    public final TextSetting customTabs;
    public final BoolSetting highlightName;
    public final TextSetting highlightWords;
    public final BoolSetting highlightSound;
    public final BoolSetting highlightToast;
    public final BoolSetting spamFilter;
    public final TextSetting blockedWords;
    public final BoolSetting copy;

    private String lastText;
    private int lastCount;
    /** Zuletzt gesehene Nachrichten (Spam-Filter): Text → Zeitpunkte */
    private final Deque<Object[]> recent = new ArrayDeque<Object[]>();

    // Tabs und Suche (nur für die Anzeige)
    private static int activeTab;
    private static final int[] unread = new int[16];
    private static boolean searching;
    private static String query = "";
    /** Angezeigte Zeile → Nachrichtentext (ohne Zeitstempel), für Strg + Klick */
    private static final Map<Object, String> LINE_TEXT = Collections.synchronizedMap(new WeakHashMap<Object, String>());

    // Hinweis oben rechts bei Erwähnung
    private static String toastText;
    private static long toastAt;

    public ChatModule() {
        super("chat", "Chat+", "Zeitstempel, Kompakt-Chat, Tabs, Suche, Hervorheben, Filter, Kopieren per Strg + Klick", Category.CLIENT, () -> Items.WRITABLE_BOOK, true);
        Map<String, Object> raw = MyticClient.config().settings;
        if (Boolean.TRUE.equals(raw.get("chat.seconds")) && !raw.containsKey("chat.timeFormat")) raw.put("chat.timeFormat", TIME_FORMATS[1]);
        if (Boolean.FALSE.equals(raw.get("chat.longHistory")) && !raw.containsKey("chat.historyLines")) raw.put("chat.historyLines", 100.0);
        timestamps = new BoolSetting(this, "timestamps", "Zeitstempel", true);
        timeFormat = new ModeSetting(this, "timeFormat", "Zeitformat", TIME_FORMATS[0], TIME_FORMATS);
        stack = new BoolSetting(this, "stack", "Kompakt: gleiche Nachrichten zusammenfassen (×3)", true);
        history = new SliderSetting(this, "historyLines", "Verlauf", 100, 2000, 100, 500, " Zeilen");
        tabs = new BoolSetting(this, "tabs", "Chat-Tabs und Suche im Chat-Fenster", true);
        customTabs = new TextSetting(this, "customTabs", "Eigene Tabs", "", () -> "Name: Wort, Wort | Name: re:Regex", 300);
        highlightName = new BoolSetting(this, "highlightName", "Eigenen Namen hervorheben", true);
        highlightWords = new TextSetting(this, "highlightWords", "Wörter hervorheben", "", () -> "durch Komma getrennt", 200);
        highlightSound = new BoolSetting(this, "highlightSound", "Ton bei Erwähnung", true);
        highlightToast = new BoolSetting(this, "highlightToast", "Hinweis oben rechts bei Erwähnung", false);
        spamFilter = new BoolSetting(this, "spamFilter", "Spam-Filter (gleiche Nachricht öfter als 3× in 30 s)", false);
        blockedWords = new TextSetting(this, "blockedWords", "Nachrichten ausblenden mit", "", () -> "Wörter durch Komma", 300);
        copy = new BoolSetting(this, "copy", "Strg + Klick kopiert eine Nachricht", true);
        instance = this;
    }

    public static ChatModule get() {
        return instance;
    }

    private static boolean on() {
        return instance != null && instance.enabled();
    }

    public static int historySize(int vanilla) {
        return on() ? Math.max(vanilla, instance.history.intValue()) : vanilla;
    }

    // ------------------------------------------------------------------------------------------ neue Nachricht

    /** Angezeigten Text einer neuen Nachricht bilden (Signatur und Original bleiben unberührt); BLOCKED = nicht zeigen */
    public static Component decorate(ChatComponent chat, Component message) {
        ChatModule m = instance;
        chatRef = chat;
        message = StreamerModule.filter(message); // Streamer-Modus gilt auch ohne Chat-Modul
        if (m == null || !m.enabled() || message == null) return message;
        String text = message.getString();
        if (m.blocked(text)) return BLOCKED;
        Component shown = message;
        boolean repeat = false;
        if (m.stack.get()) {
            repeat = text.equals(m.lastText) && !text.isBlank();
            if (repeat) {
                m.lastCount++;
                removeNewest((ChatComponentAccessor) chat);
                shown = Component.empty().append(message).append(Component.literal(" (×" + m.lastCount + ")").withColor(0xFF9AA0AA));
            } else {
                m.lastText = text;
                m.lastCount = 1;
            }
        }
        boolean mention = m.mentions(text);
        if (mention) {
            shown = Component.empty().append(Component.literal(MARK).withColor(MyticClient.accent())).append(shown);
            if (!repeat) m.notifyMention(text);
        }
        if (m.timestamps.get()) {
            int i = Math.max(0, java.util.Arrays.asList(TIME_FORMATS).indexOf(m.timeFormat.get()));
            String t = LocalTime.now().format(DateTimeFormatter.ofPattern(TIME_PATTERNS[i], Locale.ENGLISH));
            MutableComponent out = Component.literal(t).withColor(0xFF7C8799);
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

    /** Wortfilter und Spam-Filter */
    private boolean blocked(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String w : words(blockedWords.get())) if (low.contains(w)) return true;
        if (!spamFilter.get() || text.isBlank()) return false;
        long now = System.currentTimeMillis();
        while (!recent.isEmpty() && now - (Long) recent.peekFirst()[1] > 30_000) recent.pollFirst();
        int n = 0;
        for (Object[] r : recent) if (r[0].equals(text)) n++;
        recent.addLast(new Object[]{text, now});
        while (recent.size() > 200) recent.pollFirst();
        return n >= 3;
    }

    private static List<String> words(String list) {
        List<String> out = new ArrayList<String>();
        for (String w : list.split(",")) {
            w = w.trim().toLowerCase(Locale.ROOT);
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    /** Erwähnt die Nachricht den eigenen Namen (nicht als Absender) oder ein Hervorhebungs-Wort? */
    private boolean mentions(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String w : words(highlightWords.get())) if (low.contains(w)) return true;
        if (!highlightName.get()) return false;
        String name = Minecraft.getInstance().getUser().getName();
        if (name == null || name.length() < 3) return false;
        String n = name.toLowerCase(Locale.ROOT);
        int at = low.indexOf(n);
        while (at >= 0) {
            boolean wordStart = at == 0 || !Character.isLetterOrDigit(low.charAt(at - 1)) && low.charAt(at - 1) != '_';
            int end = at + n.length();
            boolean wordEnd = end >= low.length() || !Character.isLetterOrDigit(low.charAt(end)) && low.charAt(end) != '_';
            String rest = low.substring(end).trim();
            boolean sender = at < 40 && (rest.startsWith(">") || rest.startsWith(":") || rest.startsWith("»") || rest.startsWith("›"));
            if (wordStart && wordEnd && !sender) return true;
            at = low.indexOf(n, at + 1);
        }
        return false;
    }

    private void notifyMention(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (highlightSound.get()) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING, 1.6f));
        }
        if (highlightToast.get()) {
            toastText = text;
            toastAt = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------------------------------ Tabs

    /** Alle Tabs: feste + eigene (Name → Regel) */
    public static List<String> tabNames() {
        List<String> out = new ArrayList<String>();
        Collections.addAll(out, FIXED_TABS);
        if (instance != null) for (Object[] t : instance.custom()) out.add((String) t[0]);
        return out;
    }

    private String parsedFrom;
    private List<Object[]> parsed = new ArrayList<Object[]>();

    /** Eigene Tabs aus „Name: Wort, Wort | Name: re:Regex“ (höchstens 6) */
    private List<Object[]> custom() {
        String src = customTabs.get();
        if (src.equals(parsedFrom)) return parsed;
        List<Object[]> out = new ArrayList<Object[]>();
        for (String part : src.split("\\|")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;
            String name = part.substring(0, colon).trim();
            String rule = part.substring(colon + 1).trim();
            if (name.isEmpty() || rule.isEmpty()) continue;
            if (name.length() > 14) name = name.substring(0, 14);
            Object matcher;
            if (rule.startsWith("re:")) {
                try {
                    matcher = Pattern.compile(rule.substring(3).trim(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                } catch (PatternSyntaxException e) {
                    continue;
                }
            } else {
                matcher = words(rule);
            }
            out.add(new Object[]{name, matcher});
            if (out.size() >= 6) break;
        }
        parsedFrom = src;
        parsed = out;
        return out;
    }

    public static int activeTab() {
        return activeTab;
    }

    public static int unread(int tab) {
        return tab >= 0 && tab < unread.length ? unread[tab] : 0;
    }

    public static void selectTab(int tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        if (tab < unread.length) unread[tab] = 0;
        refreshChat();
    }

    public static boolean searching() {
        return searching;
    }

    public static String query() {
        return query;
    }

    public static void setSearching(boolean on) {
        if (searching == on) return;
        searching = on;
        if (!on) setQuery("");
    }

    public static void setQuery(String q) {
        q = q == null ? "" : q.trim();
        if (q.equals(query)) return;
        query = q;
        refreshChat();
    }

    /** Chat-Anzeige (wird von den Mixins gesetzt; je nach Version liegt sie an anderer Stelle) */
    private static ChatComponent chatRef;

    public static void bind(ChatComponent chat) {
        chatRef = chat;
    }

    public static void refreshChat() {
        ChatComponent chat = chatRef;
        if (chat == null) return;
        chat.resetChatScroll();
        ((ChatComponentAccessor) chat).mytic$refresh();
    }

    /** Text ohne Zeitstempel, Markierung und Zähler */
    private static final Pattern DECOR = Pattern.compile("^(?:\\[?\\d{1,2}:\\d{2}(?::\\d{2})?(?: ?[AP]M)?]? ?(?:› )?)?(?:▍ )?");
    private static final Pattern COUNT = Pattern.compile(" \\(×\\d+\\)$");

    public static String plain(Component c) {
        String s = c.getString();
        if (instance != null && instance.timestamps.get() || s.contains("▍")) s = DECOR.matcher(s).replaceFirst("");
        return COUNT.matcher(s).replaceFirst("");
    }

    private static final Pattern PRIVATE = Pattern.compile(
            "^\\[?\\s*(?:\\[[^\\]]{1,20}]\\s*)?[\\w§&]{2,20}\\s*(?:->|→|➜|➥|»|>>|⇒)\\s*(?:me|mir|dir|ich|you|du)\\s*]?"
                    + "|^\\[?\\s*(?:me|ich|du|you)\\s*(?:->|→|➜|➥|»|>>|⇒)\\s*(?:\\[[^\\]]{1,20}]\\s*)?\\w{2,16}\\s*]?"
                    + "|^\\[(?:msg|pm|privat|private|whisper|tell|w|dm)]"
                    + "|^(?:from|to|von|an)\\s+(?:\\[[^\\]]{1,20}]\\s*)?\\w{2,16}\\s*:"
                    + "|\\b(?:whispers to you|whispers:|flüstert dir zu|flüstert dir:|you whisper to|du flüsterst)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PARTY = Pattern.compile("^(?:\\[(?:party|p|gruppe|team)]|(?:party|gruppe)\\s*(?:>|»|:))", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENDER = Pattern.compile("^(?:\\[[^\\]]{0,24}]\\s*)*<?([A-Za-z0-9_]{3,16})>?\\s*(?:[:»›]|>>|$)");
    private static final Pattern PLAYER_CHAT = Pattern.compile("^(?:\\[[^\\]]{0,24}]\\s*)*(?:<[A-Za-z0-9_]{3,16}>|[A-Za-z0-9_]{3,16}\\s*(?::|»|›|>>)\\s)");

    private static boolean hasKey(Component c, String prefix, int depth) {
        if (c.getContents() instanceof TranslatableContents tc && tc.getKey().startsWith(prefix)) return true;
        if (depth > 3) return false;
        for (Component s : c.getSiblings()) if (hasKey(s, prefix, depth + 1)) return true;
        return false;
    }

    private static boolean isPrivate(Component c, String plain) {
        return hasKey(c, "commands.message.display.", 0) || PRIVATE.matcher(plain).find();
    }

    private static boolean isMytic(String plain) {
        if (plain.startsWith("[Mytic]") || PARTY.matcher(plain).find()) return true;
        var m = SENDER.matcher(plain);
        if (!m.find()) return false;
        String name = m.group(1);
        for (Friends.Friend f : Friends.friends()) if (f.name != null && f.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Gehört die Nachricht in den Tab? */
    private static boolean inTab(int tab, GuiMessage gm, String plain) {
        switch (tab) {
            case 0:
                return true;
            case 1:
                return isPrivate(gm.content(), plain);
            case 2:
                return isMytic(plain);
            case 3:
                return gm.signature() == null && !isPrivate(gm.content(), plain) && !PLAYER_CHAT.matcher(plain).find() && !plain.startsWith("[Mytic]");
            default:
                List<Object[]> c = instance.custom();
                int i = tab - FIXED_TABS.length;
                if (i >= c.size()) return true;
                Object rule = c.get(i)[1];
                if (rule instanceof Pattern p) return p.matcher(plain).find();
                String low = plain.toLowerCase(Locale.ROOT);
                for (Object w : (List<?>) rule) if (low.contains((String) w)) return true;
                return false;
        }
    }

    /** Wird die Nachricht im Chat angezeigt? (aktiver Tab und Suche) */
    public static boolean visible(GuiMessage gm) {
        if (!on() || gm.content() == BLOCKED) return gm.content() != BLOCKED;
        boolean tabsOn = instance.tabs.get();
        int tab = tabsOn ? activeTab : 0;
        if (tab >= tabNames().size()) tab = activeTab = 0;
        boolean search = tabsOn && searching && !query.isEmpty();
        if (tab == 0 && !search) return true;
        String plain = plain(gm.content());
        if (!inTab(tab, gm, plain)) return false;
        return !search || plain.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    /** Neue Nachricht im Verlauf: ungelesene Tabs zählen */
    public static void arrived(GuiMessage gm) {
        if (!on() || !instance.tabs.get() || gm.content() == BLOCKED) return;
        String plain = plain(gm.content());
        int n = Math.min(unread.length, tabNames().size());
        for (int i = 1; i < n; i++) if (i != activeTab && inTab(i, gm, plain)) unread[i]++;
    }

    /** Zeilen einer gerade angezeigten Nachricht merken (Strg + Klick) */
    public static void linesAdded(List<?> trimmed, GuiMessage gm) {
        if (trimmed.isEmpty()) return;
        String text = plain(gm.content());
        for (int i = 0; i < trimmed.size(); i++) {
            Object line = trimmed.get(i);
            LINE_TEXT.put(line, text);
            if (i + 1 < trimmed.size() && endOfEntry(trimmed.get(i + 1))) break;
        }
    }

    private static boolean endOfEntry(Object line) {
        return line instanceof GuiMessage.Line l && l.endOfEntry();
    }

    /** Text der Nachricht an dieser Bildschirmposition (GUI-Pixel) oder null */
    public static String messageAt(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = chatRef;
        if (chat == null) return null;
        ChatComponentAccessor a = (ChatComponentAccessor) chat;
        double scale = a.mytic$scale();
        int lineH = Math.max(1, a.mytic$lineHeight());
        double chatY = (mc.getWindow().getGuiScaledHeight() - mouseY - 40.0) / (scale * lineH);
        double chatX = mouseX / scale - 4.0;
        List<?> lines = a.mytic$trimmed();
        int shown = Math.min(chat.getLinesPerPage(), lines.size());
        if (chatX < -4 || chatX > a.mytic$width() / scale + 4 || chatY < 0 || chatY >= shown) return null;
        int k = (int) Math.floor(chatY) + a.mytic$scrollPos();
        if (k < 0 || k >= lines.size()) return null;
        return LINE_TEXT.get(lines.get(k));
    }

    /** Strg + Klick im Chat-Fenster: Nachricht kopieren; true = Klick verbraucht */
    public static boolean copyAt(double mouseX, double mouseY) {
        if (!on() || !instance.copy.get()) return false;
        String text = messageAt(mouseX, mouseY);
        if (text == null) return false;
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        Msg.actionBar("Nachricht kopiert");
        return true;
    }

    // ------------------------------------------------------------------------------------------ Hinweis

    /** Hinweis bei Erwähnung (oben rechts, unter einer Spiel-Herausforderung) */
    public static void renderToast(net.minecraft.client.gui.GuiGraphics g) {
        if (toastText == null) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > 4000) {
            toastText = null;
            return;
        }
        float in = Math.min(1f, age / 220f), out = age > 3700 ? (4000 - age) / 300f : 1f;
        float a = Math.max(0, Math.min(in, out)), ease = 1f - (1f - in) * (1f - in) * (1f - in);
        int W = 184, H = 30;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        float x = sw - W - 6 + (1 - ease) * (W + 10), y = 64;
        float dv = 0.4f;
        de.myticlegacy.client.gui.ui.Draw.shadow(g, x, y + 6, W, H, 12 * dv, 30 * dv, -6 * dv, de.myticlegacy.client.gui.ui.Draw.alpha(0x99000000, a));
        de.myticlegacy.client.gui.ui.Draw.glass(g, x, y, W, H, 12 * dv, de.myticlegacy.client.gui.ui.Draw.alpha(0xEB14101E, a),
                de.myticlegacy.client.gui.ui.Draw.alpha(0x2BFFFFFF, a));
        de.myticlegacy.client.gui.ui.Draw.accentRect(g, x + 3, y + 6, 2, H - 12, 1, a);
        de.myticlegacy.client.gui.ui.Icons.draw(g, "chat", x + 9, y + 5, 8, de.myticlegacy.client.gui.ui.Draw.alpha(de.myticlegacy.client.gui.ui.Tokens.ACC, a));
        de.myticlegacy.client.gui.ui.Type.draw(g, "Erwähnung im Chat", x + 21, y + 6.5f, 14 * dv, de.myticlegacy.client.gui.ui.Tokens.SEMIBOLD,
                de.myticlegacy.client.gui.ui.Draw.alpha(de.myticlegacy.client.gui.ui.Tokens.TEXT, a));
        String t = de.myticlegacy.client.gui.ui.Type.ellipsize(g, toastText, W - 18, 12.5f * dv, de.myticlegacy.client.gui.ui.Tokens.MEDIUM);
        de.myticlegacy.client.gui.ui.Type.draw(g, t, x + 9, y + 17.5f, 12.5f * dv, de.myticlegacy.client.gui.ui.Tokens.MEDIUM,
                de.myticlegacy.client.gui.ui.Draw.alpha(de.myticlegacy.client.gui.ui.Tokens.MUTED, a));
    }
}
