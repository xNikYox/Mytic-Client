package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.GuiGraphics;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Type;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Items;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat+ (1.8.9): Zeitstempel, Kompakt-Chat („(×3)“), Tabs (Alle, Privat, Mytic, Server, eigene), Suche (Strg + F),
 * Hervorheben mit Ton und Hinweis, Spam-/Wortfilter, Strg + Klick kopiert. Nur Anzeige. Unterschied zu den neuen
 * Versionen: der Verlauf bleibt bei 100 Zeilen (fest im Spiel), Tabs filtern die vorhandenen Zeilen.
 */
public class ChatModule extends Module {
    private static ChatModule instance;
    public static final String[] TIME_FORMATS = {"[14:05]", "[14:05:09]", "14:05 ›", "[2:05 PM]"};
    private static final String[] TIME_PATTERNS = {"[HH:mm] ", "[HH:mm:ss] ", "HH:mm › ", "[h:mm a] "};
    public static final String[] FIXED_TABS = {"Alle", "Privat", "Mytic", "Server"};
    private static final String MARK = "▍ ";
    /** Eigene Zeilen-IDs (zum Ersetzen beim Kompakt-Chat und zum Kopieren) */
    private static final int ID_BASE = 0x4D590000;

    public final BoolSetting timestamps;
    public final ModeSetting timeFormat;
    public final BoolSetting stack;
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
    private int lastCount, lastId, nextId;
    private final Deque<Object[]> recent = new ArrayDeque<Object[]>();
    private final Map<Integer, String> originals = new LinkedHashMap<Integer, String>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> e) {
            return size() > 600;
        }
    };

    private int activeTab;
    private final int[] unread = new int[16];
    private boolean searching;
    private String query = "";
    private Object lastTop;
    private final List<float[]> areas = new ArrayList<float[]>();
    private String toastText;
    private long toastAt;

    public ChatModule() {
        super("chat", "Chat+", "Zeitstempel, Kompakt-Chat, Tabs, Suche, Hervorheben, Filter, Kopieren per Strg + Klick", Category.CLIENT, () -> Items.writable_book, true);
        Map<String, Object> raw = MyticClient.config().settings;
        if (Boolean.TRUE.equals(raw.get("chat.seconds")) && !raw.containsKey("chat.timeFormat")) raw.put("chat.timeFormat", TIME_FORMATS[1]);
        timestamps = new BoolSetting(this, "timestamps", "Zeitstempel", true);
        timeFormat = new ModeSetting(this, "timeFormat", "Zeitformat", TIME_FORMATS[0], TIME_FORMATS);
        stack = new BoolSetting(this, "stack", "Kompakt: gleiche Nachrichten zusammenfassen (×3)", true);
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

    private static GuiNewChat chat() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.ingameGUI == null ? null : mc.ingameGUI.getChatGUI();
    }

    // ------------------------------------------------------------------------------------------ neue Nachricht

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChat(ClientChatReceivedEvent event) {
        if (!enabled() || event.type == 2 || event.message == null || event.isCanceled()) return;
        GuiNewChat chat = chat();
        if (chat == null) return;
        IChatComponent message = event.message;
        String text = message.getUnformattedText();
        if (blocked(text)) {
            event.setCanceled(true);
            return;
        }
        int id;
        IChatComponent shown = message;
        boolean repeat = stack.get() && text.equals(lastText) && !text.trim().isEmpty();
        if (repeat) {
            lastCount++;
            id = lastId;
            chat.deleteChatLine(id);
            shown = new ChatComponentText("").appendSibling(message.createCopy())
                    .appendSibling(new ChatComponentText(" (×" + lastCount + ")").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        } else {
            lastText = text;
            lastCount = 1;
            id = ID_BASE + (nextId++ & 0xFFFF);
            lastId = id;
        }
        if (mentions(text)) {
            shown = new ChatComponentText("").appendSibling(new ChatComponentText(MARK).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE)))
                    .appendSibling(shown);
            if (!repeat) notifyMention(text);
        }
        if (timestamps.get()) {
            int i = Math.max(0, Arrays.asList(TIME_FORMATS).indexOf(timeFormat.get()));
            String t = new SimpleDateFormat(TIME_PATTERNS[i], Locale.ENGLISH).format(new Date());
            shown = new ChatComponentText("").appendSibling(new ChatComponentText(t).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY)))
                    .appendSibling(shown);
        }
        originals.put(id, text);
        if (!repeat) countUnread(message, text);
        event.setCanceled(true);
        chat.printChatMessageWithOptionalDeletion(shown, id);
        if (filtering()) rebuild();
    }

    private boolean blocked(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String w : words(blockedWords.get())) if (low.contains(w)) return true;
        if (!spamFilter.get() || text.trim().isEmpty()) return false;
        long now = System.currentTimeMillis();
        while (!recent.isEmpty() && now - (Long) recent.peekFirst()[1] > 30000) recent.pollFirst();
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

    private boolean mentions(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String w : words(highlightWords.get())) if (low.contains(w)) return true;
        if (!highlightName.get()) return false;
        String name = Minecraft.getMinecraft().getSession().getUsername();
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
        if (highlightSound.get()) Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 1.6f));
        if (highlightToast.get()) {
            toastText = text;
            toastAt = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------------------------------ Tabs

    public List<String> tabNames() {
        List<String> out = new ArrayList<String>();
        Collections.addAll(out, FIXED_TABS);
        for (Object[] t : custom()) out.add((String) t[0]);
        return out;
    }

    private String parsedFrom;
    private List<Object[]> parsed = new ArrayList<Object[]>();

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

    private static final Pattern DECOR = Pattern.compile("^(?:\\[?\\d{1,2}:\\d{2}(?::\\d{2})?(?: ?[AP]M)?]? ?(?:› )?)?(?:▍ )?");
    private static final Pattern COUNT = Pattern.compile(" \\(×\\d+\\)$");
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

    private static String plain(String s) {
        s = EnumChatFormatting.getTextWithoutFormattingCodes(s);
        if (s == null) return "";
        return COUNT.matcher(DECOR.matcher(s).replaceFirst("")).replaceFirst("");
    }

    private static boolean hasKey(IChatComponent c, String prefix, int depth) {
        if (c instanceof ChatComponentTranslation && ((ChatComponentTranslation) c).getKey().startsWith(prefix)) return true;
        if (depth > 3) return false;
        for (IChatComponent s : c.getSiblings()) if (hasKey(s, prefix, depth + 1)) return true;
        return false;
    }

    private boolean inTab(int tab, IChatComponent c, String plain) {
        switch (tab) {
            case 0:
                return true;
            case 1:
                return hasKey(c, "commands.message.display.", 0) || PRIVATE.matcher(plain).find();
            case 2: {
                if (plain.startsWith("[Mytic]") || PARTY.matcher(plain).find()) return true;
                java.util.regex.Matcher m = SENDER.matcher(plain);
                if (!m.find()) return false;
                for (Friends.Friend f : Friends.friends()) if (f.name != null && f.name.equalsIgnoreCase(m.group(1))) return true;
                return false;
            }
            case 3:
                return !inTab(1, c, plain) && !PLAYER_CHAT.matcher(plain).find() && !plain.startsWith("[Mytic]");
            default: {
                List<Object[]> cu = custom();
                int i = tab - FIXED_TABS.length;
                if (i >= cu.size()) return true;
                Object rule = cu.get(i)[1];
                if (rule instanceof Pattern) return ((Pattern) rule).matcher(plain).find();
                String low = plain.toLowerCase(Locale.ROOT);
                for (Object w : (List<?>) rule) if (low.contains((String) w)) return true;
                return false;
            }
        }
    }

    private void countUnread(IChatComponent c, String text) {
        if (!tabs.get()) return;
        String plain = plain(text);
        int n = Math.min(unread.length, tabNames().size());
        for (int i = 1; i < n; i++) if (i != activeTab && inTab(i, c, plain)) unread[i]++;
    }

    private boolean filtering() {
        return tabs.get() && (activeTab != 0 || searching && !query.isEmpty());
    }

    private boolean visible(IChatComponent c) {
        String plain = plain(c.getUnformattedText());
        if (!inTab(activeTab, c, plain)) return false;
        return !searching || query.isEmpty() || plain.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static Method setChatLine;

    /** Angezeigte Zeilen aus dem Verlauf neu aufbauen (nur passende Nachrichten) */
    @SuppressWarnings("unchecked")
    private void rebuild() {
        GuiNewChat chat = chat();
        if (chat == null) return;
        try {
            List<ChatLine> all = (List<ChatLine>) ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "chatLines", "field_146252_h");
            List<ChatLine> drawn = (List<ChatLine>) ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "drawnChatLines", "field_146253_i");
            if (!filtering()) {
                chat.refreshChat();
            } else {
                if (setChatLine == null) setChatLine = ReflectionHelper.findMethod(GuiNewChat.class, chat, new String[]{"setChatLine", "func_146237_a"},
                        IChatComponent.class, int.class, int.class, boolean.class);
                drawn.clear();
                chat.resetScroll();
                for (int i = all.size() - 1; i >= 0; i--) {
                    ChatLine l = all.get(i);
                    if (visible(l.getChatComponent())) setChatLine.invoke(chat, l.getChatComponent(), l.getChatLineID(), l.getUpdatedCounter(), true);
                }
            }
            lastTop = all.isEmpty() ? null : all.get(0);
        } catch (Exception e) {
            MyticClient.LOG.warn("Chat+: Tabs nicht verfügbar", e);
            tabs.set(false);
        }
    }

    /** Neue Zeilen anderer Mods (direkt gedruckt) auch filtern */
    @SuppressWarnings("unchecked")
    public void tick(Minecraft mc) {
        if (searching && !(mc.currentScreen instanceof GuiChat)) setSearching(false);
        if (!enabled() || !filtering()) return;
        GuiNewChat chat = chat();
        if (chat == null) return;
        try {
            List<ChatLine> all = (List<ChatLine>) ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "chatLines", "field_146252_h");
            Object top = all.isEmpty() ? null : all.get(0);
            if (top != lastTop) rebuild();
        } catch (RuntimeException ignored) {
            // nichts
        }
    }

    private void select(int tab) {
        if (tab == activeTab) return;
        activeTab = tab;
        if (tab < unread.length) unread[tab] = 0;
        rebuild();
    }

    private void setSearching(boolean on) {
        if (searching == on) return;
        searching = on;
        query = "";
        rebuild();
    }

    // ------------------------------------------------------------------------------------------ Chat-Fenster

    private static GuiTextField input(GuiScreen s) {
        try {
            return (GuiTextField) ReflectionHelper.getPrivateValue(GuiChat.class, (GuiChat) s, "inputField", "field_146415_a");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean barActive() {
        return enabled() && tabs.get();
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        areas.clear();
        if (!(event.gui instanceof GuiChat) || !barActive()) return;
        GuiScreen s = event.gui;
        GuiGraphics g = new GuiGraphics();
        GuiTextField in = input(s);
        String typed = in == null ? "" : in.getText();
        float fs = 16 * 0.4f, h = 11;
        if (searching) {
            String q = typed.trim();
            if (!q.equals(query)) {
                query = q;
                rebuild();
            }
            if (typed.isEmpty()) Type.draw(g, "Chat durchsuchen … (Enter oder Strg + F beendet)", 4, s.height - 10.5f, fs, Tokens.MEDIUM, 0xFF8A849E);
        } else if (typed.startsWith("/")) {
            return;
        }
        int mx = event.mouseX, my = event.mouseY;
        float x = 2, y = s.height - 14 - 2 - h;
        List<String> names = tabNames();
        for (int i = 0; i < names.size(); i++) {
            String label = names.get(i);
            int n = i < unread.length ? unread[i] : 0;
            String count = n <= 0 ? null : n > 99 ? "99+" : String.valueOf(n);
            float w = Type.width(g, label, fs, Tokens.SEMIBOLD) + 10 + (count == null ? 0 : Type.width(g, count, fs * 0.9f, Tokens.SEMIBOLD) + 4);
            boolean on = i == activeTab;
            pill(g, x, y, w, h, on, mx >= x && mx < x + w && my >= y && my < y + h);
            Type.middle(g, label, x + 5, y + h / 2f, fs, Tokens.SEMIBOLD, on ? 0xFFFFFFFF : Tokens.MUTED);
            if (count != null) Type.middle(g, count, x + 5 + Type.width(g, label, fs, Tokens.SEMIBOLD) + 4, y + h / 2f, fs * 0.9f, Tokens.SEMIBOLD,
                    on ? 0xFFFFFFFF : Tokens.ACC);
            areas.add(new float[]{x, y, w, h, i});
            x += w + 2;
        }
        String label = searching ? "Suche" : "";
        float w = 7 + 8 + (searching ? Type.width(g, label, fs, Tokens.SEMIBOLD) + 3 : 0);
        pill(g, x, y, w, h, searching, mx >= x && mx < x + w && my >= y && my < y + h);
        Icons.draw(g, "search", x + 4, y + h / 2f - 3.5f, 7, searching ? 0xFFFFFFFF : Tokens.MUTED);
        if (searching) Type.middle(g, label, x + 4 + 7 + 3, y + h / 2f, fs, Tokens.SEMIBOLD, 0xFFFFFFFF);
        areas.add(new float[]{x, y, w, h, -1});
    }

    private static void pill(GuiGraphics g, float x, float y, float w, float h, boolean on, boolean hover) {
        if (on) Draw.accentRect(g, x, y, w, h, 3, 1f);
        else Draw.glass(g, x, y, w, h, 3, hover ? 0xCC1E1830 : 0xB3100C18, hover ? Tokens.LINE_STRONG : Tokens.LINE);
    }

    private static boolean ctrl() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !enabled() || !Mouse.getEventButtonState() || Mouse.getEventButton() != 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen s = event.gui;
        float mx = Mouse.getEventX() * s.width / (float) mc.displayWidth;
        float my = s.height - Mouse.getEventY() * s.height / (float) mc.displayHeight - 1;
        for (float[] a : areas) {
            if (mx >= a[0] && mx < a[0] + a[2] && my >= a[1] && my < a[1] + a[3]) {
                int tab = (int) a[4];
                if (tab < 0) toggleSearch(s);
                else select(tab);
                event.setCanceled(true);
                return;
            }
        }
        if (copy.get() && ctrl()) {
            String text = messageAt(Mouse.getEventX(), Mouse.getEventY());
            if (text != null) {
                GuiScreen.setClipboardString(text);
                mc.ingameGUI.setRecordPlaying("Nachricht kopiert", false);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || !barActive() || !Keyboard.getEventKeyState()) return;
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_F && ctrl()) {
            toggleSearch(event.gui);
            event.setCanceled(true);
        } else if (searching && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)) {
            toggleSearch(event.gui);
            event.setCanceled(true);
        }
    }

    private void toggleSearch(GuiScreen s) {
        GuiTextField in = input(s);
        if (in != null) in.setText("");
        setSearching(!searching);
    }

    /** Nachricht unter der Maus (rohe Mauskoordinaten, wie GuiNewChat.getChatComponent) */
    @SuppressWarnings("unchecked")
    private String messageAt(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiNewChat chat = chat();
        if (chat == null || !chat.getChatOpen()) return null;
        try {
            List<ChatLine> drawn = (List<ChatLine>) ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "drawnChatLines", "field_146253_i");
            List<ChatLine> all = (List<ChatLine>) ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "chatLines", "field_146252_h");
            int scroll = (Integer) ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "scrollPos", "field_146250_j");
            ScaledResolution sr = new ScaledResolution(mc);
            int i = sr.getScaleFactor();
            float f = chat.getChatScale();
            int j = MathHelper.floor_float((mouseX / i - 3) / f);
            int k = MathHelper.floor_float((mouseY / i - 27) / f);
            if (j < 0 || k < 0) return null;
            int l = Math.min(chat.getLineCount(), drawn.size());
            if (j > MathHelper.floor_float(chat.getChatWidth() / f) || k >= mc.fontRendererObj.FONT_HEIGHT * l + l) return null;
            int idx = k / mc.fontRendererObj.FONT_HEIGHT + scroll;
            if (idx < 0 || idx >= drawn.size()) return null;
            ChatLine line = drawn.get(idx);
            String own = originals.get(line.getChatLineID());
            if (own != null && line.getChatLineID() != 0) return own;
            for (ChatLine c : all) {
                if (c.getChatLineID() == line.getChatLineID() && c.getUpdatedCounter() == line.getUpdatedCounter()) return plain(c.getChatComponent().getUnformattedText());
            }
            return plain(line.getChatComponent().getUnformattedText());
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------ Hinweis

    public void renderToast(GuiGraphics g) {
        if (toastText == null) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > 4000) {
            toastText = null;
            return;
        }
        float in = Math.min(1f, age / 220f), out = age > 3700 ? (4000 - age) / 300f : 1f;
        float a = Math.max(0, Math.min(in, out)), ease = 1f - (1f - in) * (1f - in) * (1f - in);
        int W = 184, H = 30;
        float x = g.guiWidth() - W - 6 + (1 - ease) * (W + 10), y = 64;
        float dv = 0.4f;
        Draw.shadow(g, x, y + 6, W, H, 12 * dv, 30 * dv, -6 * dv, Draw.alpha(0x99000000, a));
        Draw.glass(g, x, y, W, H, 12 * dv, Draw.alpha(0xEB14101E, a), Draw.alpha(0x2BFFFFFF, a));
        Draw.accentRect(g, x + 3, y + 6, 2, H - 12, 1, a);
        Icons.draw(g, "chat", x + 9, y + 5, 8, Draw.alpha(Tokens.ACC, a));
        Type.draw(g, "Erwähnung im Chat", x + 21, y + 6.5f, 14 * dv, Tokens.SEMIBOLD, Draw.alpha(Tokens.TEXT, a));
        String t = Type.ellipsize(g, toastText, W - 18, 12.5f * dv, Tokens.MEDIUM);
        Type.draw(g, t, x + 9, y + 17.5f, 12.5f * dv, Tokens.MEDIUM, Draw.alpha(Tokens.MUTED, a));
    }
}
