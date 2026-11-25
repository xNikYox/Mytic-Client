package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.compat.ScreenHooks;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Type;
import de.myticlegacy.client.mixin.boost.ChatScreenAccessor;
import de.myticlegacy.client.module.ChatModule;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat+ im Chat-Fenster: Tab-Leiste über dem Eingabefeld (Alle, Privat, Mytic, Server, eigene Tabs mit Zahl ungelesener
 * Nachrichten) und Suche (Lupe oder Strg + F – dann ist das Eingabefeld das Suchfeld, Enter beendet). Strg + Klick
 * auf eine Nachricht kopiert sie. Im Mytic-Design (dunkles Glas, Akzent), in GUI-Pixeln gezeichnet.
 */
public final class ChatBar {
    private static final float DV = 0.4f, H = 11, FS = 16 * DV;
    /** Klickflächen des letzten Bildes: {x, y, w, h, Tab (−1 = Suche)} */
    private static final List<float[]> AREAS = new ArrayList<float[]>();

    private ChatBar() {
    }

    public static void attach(Screen screen) {
        if (!(screen instanceof ChatScreen) || ChatModule.get() == null) return;
        ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> render(s, g, mx, my));
        ScreenHooks.register(screen, (x, y, button) -> click(screen, x, y, button), (key, ctrl) -> key(screen, key, ctrl));
        ScreenEvents.remove(screen).register(s -> {
            ChatModule.setSearching(false);
            AREAS.clear();
        });
    }

    private static boolean active() {
        ChatModule m = ChatModule.get();
        return m != null && m.enabled() && m.tabs.get();
    }

    private static EditBox input(Screen s) {
        try {
            return ((ChatScreenAccessor) s).mytic$input();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static void render(Screen s, GuiGraphics g, int mx, int my) {
        AREAS.clear();
        if (!active()) return;
        EditBox in = input(s);
        String typed = in == null ? "" : in.getValue();
        if (ChatModule.searching()) {
            ChatModule.setQuery(typed);
            if (typed.isEmpty()) Type.draw(g, "Chat durchsuchen … (Enter oder Strg + F beendet)", 4, s.height - 10.5f, FS, Tokens.MEDIUM, 0xFF8A849E);
        } else if (typed.startsWith("/")) {
            return; // Befehlsvorschläge nicht verdecken
        }
        float x = 2, y = s.height - 14 - 2 - H;
        List<String> tabs = ChatModule.tabNames();
        for (int i = 0; i < tabs.size(); i++) {
            String label = tabs.get(i);
            int n = ChatModule.unread(i);
            String count = n <= 0 ? null : n > 99 ? "99+" : String.valueOf(n);
            float w = Type.width(g, label, FS, Tokens.SEMIBOLD) + 10 + (count == null ? 0 : Type.width(g, count, FS * 0.9f, Tokens.SEMIBOLD) + 4);
            boolean on = i == ChatModule.activeTab();
            pill(g, x, y, w, on, mx >= x && mx < x + w && my >= y && my < y + H);
            Type.middle(g, label, x + 5, y + H / 2f, FS, Tokens.SEMIBOLD, on ? 0xFFFFFFFF : Tokens.MUTED);
            if (count != null) Type.middle(g, count, x + 5 + Type.width(g, label, FS, Tokens.SEMIBOLD) + 4, y + H / 2f, FS * 0.9f, Tokens.SEMIBOLD,
                    on ? 0xFFFFFFFF : Tokens.ACC);
            AREAS.add(new float[]{x, y, w, H, i});
            x += w + 2;
        }
        // Suche
        boolean search = ChatModule.searching();
        String label = search ? "Suche" : "";
        float w = 7 + 8 + (search ? Type.width(g, label, FS, Tokens.SEMIBOLD) + 3 : 0);
        pill(g, x, y, w, search, mx >= x && mx < x + w && my >= y && my < y + H);
        Icons.draw(g, "search", x + 4, y + H / 2f - 3.5f, 7, search ? 0xFFFFFFFF : Tokens.MUTED);
        if (search) Type.middle(g, label, x + 4 + 7 + 3, y + H / 2f, FS, Tokens.SEMIBOLD, 0xFFFFFFFF);
        AREAS.add(new float[]{x, y, w, H, -1});
    }

    private static void pill(GuiGraphics g, float x, float y, float w, boolean on, boolean hover) {
        if (on) {
            Draw.accentRect(g, x, y, w, H, 3, 1f);
        } else {
            Draw.glass(g, x, y, w, H, 3, hover ? 0xCC1E1830 : 0xB3100C18, hover ? Tokens.LINE_STRONG : Tokens.LINE);
        }
    }

    private static boolean click(Screen s, double x, double y, int button) {
        if (!active() && (ChatModule.get() == null || !ChatModule.get().enabled())) return false;
        if (button == 0) {
            for (float[] a : AREAS) {
                if (x >= a[0] && x < a[0] + a[2] && y >= a[1] && y < a[1] + a[3]) {
                    int tab = (int) a[4];
                    if (tab < 0) toggleSearch(s);
                    else ChatModule.selectTab(tab);
                    return true;
                }
            }
            if (de.myticlegacy.client.compat.Compat.ctrlDown()) return ChatModule.copyAt(x, y);
        }
        return false;
    }

    private static boolean key(Screen s, int key, boolean ctrl) {
        if (!active()) return false;
        if (ctrl && key == InputConstants.KEY_F) {
            toggleSearch(s);
            return true;
        }
        if (ChatModule.searching() && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            toggleSearch(s);
            return true;
        }
        return false;
    }

    private static void toggleSearch(Screen s) {
        boolean on = !ChatModule.searching();
        EditBox in = input(s);
        if (in != null) in.setValue("");
        ChatModule.setSearching(on);
    }
}
