package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Toasts;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.compat.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Freunde im neuen Design (wie die UI-Vorschau): links die Liste, gruppiert nach „Im Spiel“, „Online“, „Offline“,
 * mit Suche und den Reitern Alle/Online/Anfragen; oben Freund per Minecraft-Name hinzufügen; rechts das Profil des
 * gewählten Freundes mit Aktivität, Herausfordern und Entfernen (mit Rückfrage) sowie die Privatsphäre-Schalter.
 */
public class FriendsScreen extends DesignScreen {
    private static final float W = 1600, H = 880, SIDE = 480, HEAD = 88, SUB = 72, ROW = 64;
    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    private int tab;
    private String selected;
    private Friends.Friend confirmRemove;
    private final Kit.Field add = new Kit.Field(16);
    private final Kit.Field search = new Kit.Field(16);
    private final Kit.Scroll scroll = new Kit.Scroll();
    private String lastMessage;

    public FriendsScreen() {
        super("friends", "Freunde");
        add.allowed = NAME_CHARS;
        search.allowed = NAME_CHARS;
        add.onEnter = this::send;
        Friends.refresh();
    }

    /** Kopf aus 8×8 Pixeln zeichnen; ohne Skin ein Feld mit Anfangsbuchstaben (für Spiele und andere Bildschirme). */
    public static void drawHead(GuiGraphics g, int[] pixels, String name, int x, int y, int size) {
        if (pixels == null) {
            Ui.rect(g, x, y, size, size, 2, Ui.withAlpha(Ui.accent(), 150));
            if (name != null && !name.isEmpty()) Ui.centered(g, name.substring(0, 1).toUpperCase(), x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF, true);
            return;
        }
        float p = size / 8f;
        for (int py = 0; py < 8; py++) {
            for (int px = 0; px < 8; px++) {
                int x1 = x + Math.round(px * p);
                int y1 = y + Math.round(py * p);
                g.fill(x1, y1, x + Math.round((px + 1) * p), y + Math.round((py + 1) * p), pixels[py * 8 + px] | 0xFF000000);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Versionsteile

    /** Mytic Games gibt es in 1.8.9 nicht */
    private static boolean canChallenge() {
        return false;
    }

    private static void challenge(Friends.Friend f) {
    }

    private boolean inWorld() {
        return mc.theWorld != null;
    }

    // ------------------------------------------------------------------------------------------ Daten

    private static int dot(Friends.Friend f) {
        return f.inGame() ? Tokens.ACC_1 : f.online ? Tokens.OK : 0;
    }

    private List<Friends.Friend> filtered() {
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        List<Friends.Friend> out = new ArrayList<Friends.Friend>();
        for (Friends.Friend f : Friends.friends()) {
            if (tab == 1 && !f.online) continue;
            if (!q.isEmpty() && !f.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(f);
        }
        return out;
    }

    private Friends.Friend selectedFriend() {
        for (Friends.Friend f : Friends.friends()) if (f.uuid.equals(selected)) return f;
        List<Friends.Friend> list = Friends.friends();
        return list.isEmpty() ? null : list.get(0);
    }

    private void send() {
        String name = add.text.trim();
        if (name.length() < 3) {
            Toasts.error("Minecraft-Name: mindestens 3 Zeichen");
            return;
        }
        Friends.request(name);
        add.text = "";
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void frame(GuiGraphics g) {
        String msg = Friends.message();
        if (msg != null && !msg.equals(lastMessage)) Toasts.show(msg, Friends.messageError() ? "info" : "check", Friends.messageError() ? Toasts.ERROR : Toasts.OK);
        lastMessage = msg;

        float[] r = center(W, H);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        kit.window(x, y, w, h, !inWorld());
        float side = SIDE * d, mainW = w - side;
        sidePane(x + mainW, y, w - mainW, h, x + w);
        kit.vline(x + mainW, y, h);

        drawHeader(x, y, mainW);
        kit.hline(x, y + HEAD * d, mainW);
        float bodyY = y + (HEAD + SUB) * d;
        if (!Friends.available()) {
            empty(x, y + HEAD * d, mainW, h - HEAD * d, "users", "Freunde nur über den Mytic Launcher",
                    "Starte über den Mytic Launcher und melde dich dort mit deinem Minecraft-Konto an.");
        } else if (!Friends.loaded()) {
            String error = Friends.error();
            empty(x, y + HEAD * d, mainW, h - HEAD * d, error != null ? "info" : "refresh", error != null ? "Freunde nicht geladen" : "Freunde werden geladen …", error);
        } else {
            drawSub(x, y + HEAD * d, mainW);
            drawList(x, bodyY, mainW, y + h - bodyY);
        }
        drawDetail(x + mainW, y, side, h);
        if (confirmRemove != null) drawConfirm();
    }

    /** Dunklere Fläche der rechten Spalte, rechts an die Rundung des Fensters angepasst */
    private void sidePane(float x, float y, float w, float h, float winRight) {
        float r = Tokens.RAD_XL * d, inset = 1f / Draw.k(kit.g);
        Draw.rect(kit.g, x, y + inset, w - r, h - 2 * inset, 0, 0x24000000);
        kit.clip(winRight - r, y, r, h);
        Draw.rect(kit.g, winRight - 2 * r - inset, y + inset, 2 * r, h - 2 * inset, r, 0x24000000);
        kit.unclip();
    }

    private void drawHeader(float x, float y, float w) {
        kit.text("Freunde", x + 32 * d, y + 24 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        String sub = Friends.available() && Friends.loaded() ? Friends.onlineCount() + " von " + Friends.friends().size() + " online" : "Freundesliste";
        kit.text(sub, x + 32 * d, y + 54 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        float right = x + w - 32 * d;
        if (!Friends.available()) return;
        float bh = 38 * d, bw = kit.buttonWidth("Anfrage senden", "plus", bh);
        right -= bw;
        kit.button("send", right, y + (HEAD * d - bh) / 2f, bw, bh, "Anfrage senden", "plus", Widgets.PRIMARY, add.isEmpty() ? null : (Runnable) this::send);
        right -= 300 * d + 12 * d;
        kit.field("add", add, right, y + (HEAD * d - 40 * d) / 2f, 300 * d, 40 * d, "Minecraft-Name eingeben", "userPlus");
    }

    private void drawSub(float x, float y, float w) {
        int in = Friends.incoming().size();
        String[] labels = {"Alle", "Online", "Anfragen"};
        String[] counts = {String.valueOf(Friends.friends().size()), String.valueOf(Friends.onlineCount()), in > 0 ? "!" + in : null};
        kit.seg("tabs", x + 32 * d, y + (SUB * d - 44 * d) / 2f, labels, null, counts, tab, i -> {
            tab = i;
            scroll.reset();
        });
        if (tab != 2) kit.field("search", search, x + w - 32 * d - 300 * d, y + (SUB * d - 40 * d) / 2f, 300 * d, 40 * d, "Freunde suchen …", "search");
    }

    private void empty(float x, float y, float w, float h, String icon, String title, String text) {
        float cx = x + w / 2f, cy = y + h / 2f - 40 * d;
        Draw.rect(kit.g, cx - 32 * d, cy - 32 * d, 64 * d, 64 * d, 18 * d, Tokens.SURFACE);
        Icons.draw(kit.g, icon, cx - 14 * d, cy - 14 * d, 28 * d, Tokens.MUTED);
        kit.textCenter(title, cx, cy + 60 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT);
        if (text != null) kit.textCenter(text, cx, cy + 88 * d, 14.5f, Tokens.MEDIUM, Tokens.MUTED);
    }

    private void drawList(float x, float y, float w, float h) {
        float lx = x + 32 * d, lw = w - 64 * d, top = y + 4 * d, viewH = h - 4 * d - 20 * d;
        if (tab == 2) {
            drawRequests(lx, top, lw, viewH);
            return;
        }
        List<Friends.Friend> list = filtered();
        if (Friends.friends().isEmpty()) {
            empty(x, y, w, h, "userPlus", "Noch keine Freunde", "Oben einen Minecraft-Namen eingeben – dein Freund muss den Mytic Client einmal gestartet haben.");
            return;
        }
        if (list.isEmpty()) {
            empty(x, y, w, h, "search", "Niemand gefunden", null);
            return;
        }
        List<Friends.Friend> game = new ArrayList<Friends.Friend>(), on = new ArrayList<Friends.Friend>(), off = new ArrayList<Friends.Friend>();
        for (Friends.Friend f : list) (f.inGame() ? game : f.online ? on : off).add(f);
        float content = 0;
        for (List<Friends.Friend> grp : java.util.Arrays.asList(game, on, off)) if (!grp.isEmpty()) content += 34 * d + grp.size() * ROW * d;
        float off0 = kit.scroll(scroll, lx, top, lw, viewH, content);
        kit.clip(lx - 8 * d, top, lw + 16 * d, viewH);
        float yy = top - off0;
        Friends.Friend sel = selectedFriend();
        String[] names = {"Im Spiel", "Online", "Offline"};
        List<List<Friends.Friend>> groups = java.util.Arrays.asList(game, on, off);
        for (int gi = 0; gi < 3; gi++) {
            List<Friends.Friend> grp = groups.get(gi);
            if (grp.isEmpty()) continue;
            kit.caption(names[gi] + " — " + grp.size(), lx + 6 * d, yy + 16 * d);
            yy += 34 * d;
            for (Friends.Friend f : grp) {
                if (yy + ROW * d >= top && yy <= top + viewH) drawRow(f, lx, yy, lw, sel != null && sel.uuid.equals(f.uuid));
                yy += ROW * d;
            }
        }
        kit.unclip();
        kit.scrollbar("list", scroll, lx + lw + 14 * d, top, viewH);
    }

    private void drawRow(final Friends.Friend f, float x, float y, float w, boolean sel) {
        float rh = ROW * d - 4 * d;
        boolean hv = kit.hit("row:" + f.uuid, x, y, w, rh, () -> selected = f.uuid);
        float s = anim("sel:" + f.uuid, sel), ht = anim("hv:" + f.uuid, hv);
        if (s > 0.01f || ht > 0.01f) {
            Draw.rect(kit.g, x, y, w, rh, 14 * d, Draw.mix(Draw.alpha(Tokens.SURFACE, ht), 0x179B5CFF, s));
            if (s > 0.01f) Draw.ring(kit.g, x, y, w, rh, 14 * d, 1, Draw.alpha(Tokens.ACC_LINE, s));
        }
        float hs = 40 * d, hx = x + 14 * d, hy = y + (rh - hs) / 2f;
        kit.head(f.head, f.name, hx, hy, hs, dot(f), !f.online);
        float tx = hx + hs + 16 * d;
        // Aktionen beim Überfahren / ausgewählt
        float right = x + w - 14 * d;
        if (hv || sel) {
            right -= 34 * d;
            kit.iconButton("more:" + f.uuid, right, y + (rh - 34 * d) / 2f, 34 * d, "more", false, () -> selected = f.uuid);
            if (f.online && canChallenge()) {
                float bh = 32 * d, bw = kit.buttonWidth("Herausfordern", null, bh);
                right -= bw + 8 * d;
                kit.button("ch:" + f.uuid, right, y + (rh - bh) / 2f, bw, bh, "Herausfordern", null, Widgets.SECONDARY, () -> challenge(f));
            }
        }
        float tw = right - tx - 12 * d;
        kit.textMid(f.name, tx, y + rh / 2f - 10 * d, 16, Tokens.SEMIBOLD, f.online ? Tokens.TEXT : Tokens.MUTED, tw);
        kit.textMid(f.status(), tx, y + rh / 2f + 11 * d, 14, Tokens.MEDIUM, f.inGame() ? 0xFFD6C0FF : Tokens.MUTED, tw);
    }

    private float anim(String key, boolean on) {
        return de.myticlegacy.client.gui.ui.Motion.to(kit.key(key), on, Tokens.MS_HOVER);
    }

    private void drawRequests(float x, float y, float w, float viewH) {
        List<Friends.Friend> in = Friends.incoming(), out = Friends.outgoing();
        float req = 76 * d;
        float content = 34 * d + Math.max(1, in.size()) * req + 22 * d + 34 * d + Math.max(1, out.size()) * req;
        float off = kit.scroll(scroll, x, y, w, viewH, content);
        kit.clip(x - 8 * d, y, w + 16 * d, viewH);
        float yy = y - off;
        kit.caption("Eingehend", x + 6 * d, yy + 16 * d);
        yy += 34 * d;
        if (in.isEmpty()) {
            kit.textMid("Keine offenen Anfragen", x + 6 * d, yy + 24 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE, 0);
            yy += req;
        }
        for (final Friends.Friend f : in) {
            card(f, x, yy, w, "möchte mit dir befreundet sein");
            float bh = 38 * d, rx = x + w - 14 * d;
            float bw = kit.buttonWidth("Ablehnen", null, bh);
            rx -= bw;
            kit.button("deny:" + f.uuid, rx, yy + (req - 10 * d - bh) / 2f, bw, bh, "Ablehnen", null, Widgets.GHOST, () -> Friends.deny(f.uuid));
            float aw = kit.buttonWidth("Annehmen", "check", bh);
            rx -= aw + 8 * d;
            kit.button("acc:" + f.uuid, rx, yy + (req - 10 * d - bh) / 2f, aw, bh, "Annehmen", "check", Widgets.PRIMARY, () -> Friends.accept(f.uuid));
            yy += req;
        }
        yy += 22 * d;
        kit.caption("Gesendet", x + 6 * d, yy + 16 * d);
        yy += 34 * d;
        if (out.isEmpty()) kit.textMid("Keine gesendeten Anfragen", x + 6 * d, yy + 24 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE, 0);
        for (final Friends.Friend f : out) {
            card(f, x, yy, w, "Anfrage gesendet · wartet auf Antwort");
            float bh = 38 * d, bw = kit.buttonWidth("Zurückziehen", null, bh);
            kit.button("undo:" + f.uuid, x + w - 14 * d - bw, yy + (req - 10 * d - bh) / 2f, bw, bh, "Zurückziehen", null, Widgets.GHOST, () -> Friends.remove(f.uuid));
            yy += req;
        }
        kit.unclip();
        kit.scrollbar("req", scroll, x + w + 14 * d, y, viewH);
    }

    /** Anfrage als Karte (glass-soft, Rahmen) */
    private void card(Friends.Friend f, float x, float y, float w, String text) {
        float ch = 66 * d;
        Draw.rect(kit.g, x, y, w, ch, 14 * d, Tokens.SURFACE);
        Draw.ring(kit.g, x, y, w, ch, 14 * d, 1, Tokens.LINE);
        float hs = 40 * d;
        kit.head(f.head, f.name, x + 14 * d, y + (ch - hs) / 2f, hs, 0, false);
        float tx = x + 14 * d + hs + 16 * d;
        kit.textMid(f.name, tx, y + ch / 2f - 10 * d, 16, Tokens.SEMIBOLD, Tokens.TEXT, w * 0.45f);
        kit.textMid(text, tx, y + ch / 2f + 11 * d, 14, Tokens.MEDIUM, Tokens.MUTED, w * 0.45f);
    }

    private void drawDetail(float x, float y, float w, float h) {
        kit.iconButton("close", x + w - 32 * d - 40 * d, y + 24 * d, 40 * d, "x", true, () -> Compat.setScreen(null));
        float px = x + 32 * d, pw = w - 64 * d;
        Friends.Friend f = Friends.available() && Friends.loaded() ? selectedFriend() : null;
        float top = y + HEAD * d;
        if (f != null) {
            // Profil-Karte
            float ch = 270 * d;
            Draw.rect(kit.g, px, top, pw, ch, 20 * d, 0x169B5CFF);
            Draw.ring(kit.g, px, top, pw, ch, 20 * d, 1, Tokens.LINE);
            float hs = 104 * d, cx = px + pw / 2f;
            float hr = 22 * d;
            Draw.head(kit.g, f.head, f.name, cx - hs / 2f, top + 26 * d, hs, hr);
            if (!f.online) Draw.rect(kit.g, cx - hs / 2f, top + 26 * d, hs, hs, hr, 0x80140F20);
            int dc = dot(f);
            if (dc != 0) {
                Draw.circle(kit.g, cx + hs / 2f - 8 * d, top + 26 * d + hs - 8 * d, 12 * d, 0xFF141020);
                Draw.circle(kit.g, cx + hs / 2f - 8 * d, top + 26 * d + hs - 8 * d, 8 * d, dc);
            }
            kit.textCenter(f.name, cx, top + 160 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
            kit.textCenter(de.myticlegacy.client.gui.ui.Type.ellipsize(kit.g, f.status(), pw - 24 * d, 14.5f * d, Tokens.MEDIUM), cx, top + 188 * d, 14.5f,
                    Tokens.MEDIUM, f.inGame() ? 0xFFD6C0FF : Tokens.MUTED);
            float bh = 38 * d, by = top + 212 * d;
            if (canChallenge()) {
                final Friends.Friend ff = f;
                kit.button("dch", px + 18 * d, by, pw - 36 * d, bh, "Herausfordern", "play", Widgets.PRIMARY, f.online ? () -> challenge(ff) : null);
            } else {
                kit.badge(f.online ? "Online" : "Offline", null, cx - kit.width(f.online ? "Online" : "Offline", 12.5f, Tokens.SEMIBOLD) / 2f - 9 * d, by + 7 * d,
                        f.online ? Tokens.OK : Tokens.SUBTLE);
            }

            // Aktivität
            float ay = top + ch + 26 * d;
            kit.caption("Aktivität", px + 2 * d, ay);
            ay += 26 * d;
            List<String[]> rows = new ArrayList<String[]>();
            if (f.inGame()) rows.add(new String[]{"servers", f.server == null ? "Im Spiel" : f.server.equals("Einzelspieler") ? "Spielt im Einzelspieler" : "Spielt auf " + f.server});
            else if (f.online) rows.add(new String[]{"monitor", "Ist im Mytic Launcher"});
            if (f.version != null && !f.version.isEmpty()) rows.add(new String[]{"cube", "Minecraft " + f.version});
            if (!f.online) rows.add(new String[]{"clock", Character.toUpperCase(f.status().charAt(0)) + f.status().substring(1)});
            rows.add(new String[]{"users", "In deiner Freundesliste"});
            for (String[] row : rows) {
                Icons.draw(kit.g, row[0], px + 2 * d, ay - 1 * d, 18 * d, Tokens.SUBTLE);
                kit.textMid(row[1], px + 30 * d, ay + 8 * d, 14.5f, Tokens.MEDIUM, Tokens.TEXT, pw - 32 * d);
                ay += 30 * d;
            }
        } else if (Friends.available() && Friends.me() != null) {
            Friends.Friend me = Friends.me();
            float hs = 72 * d;
            kit.head(me.head, me.name, px, top + 8 * d, hs, Tokens.OK, false);
            kit.text(me.name, px + hs + 16 * d, top + 22 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT);
            kit.text("Dein Profil", px + hs + 16 * d, top + 48 * d, 14, Tokens.MEDIUM, Tokens.MUTED);
        }

        // Privatsphäre und Entfernen unten
        if (!Friends.available()) return;
        float by = y + h - 32 * d;
        if (f != null) {
            final Friends.Friend ff = f;
            float bh = 38 * d;
            by -= bh;
            kit.button("remove", px, by, pw, bh, "Entfernen", "trash", Widgets.DANGER, () -> confirmRemove = ff);
            by -= 22 * d;
        }
        by -= 3 * 44 * d;
        kit.caption("Privatsphäre", px + 2 * d, by - 18 * d);
        privacy("vis", "Online-Status sichtbar", MyticClient.friends.enabled(), px, by, pw, () -> {
            MyticClient.friends.toggle();
            Friends.refresh();
        });
        privacy("srv", "Server für Freunde anzeigen", MyticClient.friends.showServer.get(), px, by + 44 * d, pw, () -> {
            MyticClient.friends.showServer.toggle();
            MyticClient.config().save();
            Friends.refresh();
        });
        privacy("ntf", "Benachrichtigen, wenn Freunde online kommen", MyticClient.friends.notify.get(), px, by + 88 * d, pw, () -> {
            MyticClient.friends.notify.toggle();
            MyticClient.config().save();
        });
    }

    private void privacy(String id, String label, boolean on, float x, float y, float w, Runnable toggle) {
        kit.textMid(label, x + 2 * d, y + 22 * d, 14.5f, Tokens.MEDIUM, Tokens.TEXT, w - 60 * d);
        kit.toggle("pv:" + id, x + w - 46 * d, y + 9 * d, on, toggle);
    }

    private void drawConfirm() {
        final Friends.Friend f = confirmRemove;
        float w = 520 * d, h = 236 * d, x = (vw - w) / 2f, y = (vh - h) / 2f;
        kit.modal(x, y, w, h, () -> confirmRemove = null);
        kit.text("Freund entfernen?", x + 28 * d, y + 30 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.paragraph(f.name + " wird aus deiner Freundesliste entfernt. Ihr könnt euch jederzeit wieder eine Anfrage schicken.", x + 28 * d, y + 72 * d,
                w - 56 * d, 15, Tokens.MEDIUM, Tokens.MUTED, 22, 3);
        float bh = 44 * d, by = y + h - 28 * d - bh;
        float ew = kit.buttonWidth("Entfernen", "trash", bh);
        kit.button("cfm:ok", x + w - 28 * d - ew, by, ew, bh, "Entfernen", "trash", Widgets.DANGER, () -> {
            confirmRemove = null;
            Friends.remove(f.uuid);
            if (f.uuid.equals(selected)) selected = null;
        });
        float cw = kit.buttonWidth("Abbrechen", null, bh);
        kit.button("cfm:no", x + w - 28 * d - ew - 10 * d - cw, by, cw, bh, "Abbrechen", null, Widgets.GHOST, () -> confirmRemove = null);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean typedFree(String s) {
        // einfach lostippen = Freund hinzufügen (die Taste aus der Übersicht kurz nach dem Öffnen nicht mitzählen)
        if (!Friends.available() || confirmRemove != null || System.currentTimeMillis() - openedAt < 250) return false;
        if (s.length() != 1 || NAME_CHARS.indexOf(s.charAt(0)) < 0) return false;
        kit.focus(add);
        return kit.typed(s);
    }

    @Override
    protected void back() {
        if (confirmRemove != null) confirmRemove = null;
        else Compat.setScreen(inWorld() ? new HubScreen() : null);
    }
}
