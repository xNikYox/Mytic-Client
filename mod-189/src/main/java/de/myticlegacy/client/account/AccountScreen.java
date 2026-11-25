package de.myticlegacy.client.account;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.DesignScreen;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.SkinHeads;
import de.myticlegacy.client.gui.ui.Toasts;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.client.gui.GuiScreen;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Konten (Hauptmenü → Name oben rechts): alle Konten aus dem Mytic Launcher mit Skin-Kopf, Art und Gültigkeit der
 * Anmeldung. Ein Konto auswählen, dann Wechseln, „Neu einloggen“ (Anmeldung im Launcher erzwungen erneuern und im
 * Spiel übernehmen), Entfernen oder ein Microsoft-Konto hinzufügen – alles über die lokale Launcher-Verbindung (1.8.9).
 */
public class AccountScreen extends DesignScreen {
    private static final float W = 940, H = 760, HEAD = 88, FOOT = 76, ROW = 76;
    private final GuiScreen parent;
    private List<Accounts.Account> accounts;
    private String selected;
    private String status;
    private int statusKind; // 0 neutral, 1 ok, 2 Fehler, 3 läuft
    /** Konto, mit dem gerade etwas passiert („switch“/„refresh“/„remove“/„add“) */
    private String working, workingWhat;
    private Accounts.Account confirmRemove;
    private final Kit.Scroll scroll = new Kit.Scroll();

    public AccountScreen(GuiScreen parent) {
        super("acc", "Konten");
        this.parent = parent;
        load();
    }

    private void load() {
        if (!Accounts.available()) return;
        if (accounts == null) setStatus("Konten werden geladen …", 3);
        Accounts.list(list -> {
            accounts = list;
            if (selected == null || list.stream().noneMatch(a -> a.uuid().equals(selected))) selected = current();
            if (statusKind == 3 && working == null) setStatus(list.isEmpty() ? "Im Launcher ist kein Konto eingeloggt" : null, 0);
        }, error -> setStatus(error, 2));
    }

    private void setStatus(String text, int kind) {
        status = text;
        statusKind = kind;
    }

    @Override
    protected void back() {
        if (confirmRemove != null) confirmRemove = null;
        else Compat.setScreen(parent);
    }

    @Override
    public void onClose() {
        Compat.setScreen(parent);
    }

    private String current() {
        return mc.getSession().getPlayerID().replace("-", "");
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.replace("-", "").equalsIgnoreCase(b.replace("-", ""));
    }

    private Accounts.Account selectedAccount() {
        if (accounts == null) return null;
        for (Accounts.Account a : accounts) if (same(a.uuid(), selected)) return a;
        return null;
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void frame(GuiGraphics g) {
        int rows = accounts == null ? 3 : accounts.size() + (Accounts.supports("add") ? 1 : 0);
        float[] r = center(W, Math.max(440, Math.min(H, HEAD + FOOT + 24 + 22 + Math.max(1, rows) * ROW + 26)));
        float x = r[0], y = r[1], w = r[2], h = r[3];
        kit.window(x, y, w, h, mc.theWorld == null);

        // Kopf: Titel, Untertitel, Schließen
        String name = mc.getSession().getUsername();
        kit.text("Konten", x + 32 * d, y + 26 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.text("Im Spiel angemeldet als " + name, x + 32 * d, y + 54 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        if (Accounts.available()) {
            kit.iconButton("reload", x + w - 32 * d - 96 * d, y + 24 * d, 40 * d, "refresh", true, working == null ? this::load : null);
            if (kit.hover("reload")) kit.tooltip("Liste neu laden", x + w - 32 * d - 76 * d, y + 70 * d);
        }
        kit.iconButton("close", x + w - 32 * d - 40 * d, y + 24 * d, 40 * d, "x", true, this::back);
        kit.hline(x, y + HEAD * d, w);

        float bodyY = y + HEAD * d, bodyH = h - HEAD * d - FOOT * d;
        if (!Accounts.available()) {
            empty(x, bodyY, w, bodyH, "user", "Nur über den Mytic Launcher",
                    "Konten wechselst du im Spiel, wenn es über den Mytic Launcher gestartet wurde.");
        } else if (accounts == null) {
            empty(x, bodyY, w, bodyH, statusKind == 2 ? "info" : "refresh", statusKind == 2 ? "Konten nicht geladen" : "Lädt …",
                    statusKind == 2 ? status : "Konten kommen vom Mytic Launcher.");
        } else {
            drawList(x, bodyY, w, bodyH);
        }

        // Fuß: Rückmeldung links, Aktionen rechts
        float fy = y + h - FOOT * d;
        kit.hline(x, fy, w);
        drawFooter(x, fy, w);
        if (confirmRemove != null) drawConfirm();
    }

    private void empty(float x, float y, float w, float h, String icon, String title, String text) {
        float cx = x + w / 2f, cy = y + h / 2f - 30 * d;
        Draw.rect(kit.g, cx - 32 * d, cy - 32 * d, 64 * d, 64 * d, 18 * d, Tokens.SURFACE);
        if (icon.equals("refresh") && working == null && statusKind == 3) spin(icon, cx - 14 * d, cy - 14 * d, 28 * d, Tokens.MUTED);
        else Icons.draw(kit.g, icon, cx - 14 * d, cy - 14 * d, 28 * d, Tokens.MUTED);
        kit.textCenter(title, cx, cy + 60 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT);
        if (text != null) kit.textCenter(text, cx, cy + 88 * d, 14.5f, Tokens.MEDIUM, Tokens.MUTED);
    }

    /** Drehendes Icon (läuft gerade) */
    private void spin(String icon, float x, float y, float s, int color) {
        Gfx.push(kit.g);
        Gfx.translate(kit.g, x + s / 2f, y + s / 2f);
        Gfx.rotate(kit.g, (System.currentTimeMillis() % 1000) * 0.36f);
        Icons.draw(kit.g, icon, -s / 2f, -s / 2f, s, color);
        Gfx.pop(kit.g);
    }

    private void drawList(float x, float y, float w, float h) {
        float lx = x + 32 * d, lw = w - 64 * d, top = y + 24 * d;
        kit.caption("Konten im Launcher · " + accounts.size(), lx + 4 * d, top);
        float boxY = top + 22 * d, viewH = y + h - 16 * d - boxY;
        boolean canAdd = Accounts.supports("add");
        int rows = accounts.size() + (canAdd ? 1 : 0);
        float contentH = Math.max(1, rows) * ROW * d;
        float off = kit.scroll(scroll, lx, boxY, lw, viewH, contentH);
        float boxH = Math.min(viewH, contentH);
        Draw.rect(kit.g, lx, boxY, lw, boxH, 16 * d, Tokens.SURFACE);
        Draw.ring(kit.g, lx, boxY, lw, boxH, 16 * d, 1, Tokens.LINE);
        kit.clip(lx, boxY, lw, boxH);
        String cur = current();
        for (int i = 0; i < accounts.size(); i++) {
            float ry = boxY + i * ROW * d - off;
            if (ry + ROW * d < boxY || ry > boxY + boxH) continue;
            drawRow(accounts.get(i), lx, ry, lw, same(accounts.get(i).uuid(), cur), i > 0);
        }
        if (canAdd) drawAddRow(lx, boxY + accounts.size() * ROW * d - off, lw, !accounts.isEmpty());
        kit.unclip();
        kit.scrollbar("list", scroll, lx + lw + 10 * d, boxY, boxH);
    }

    private void drawRow(Accounts.Account a, float x, float y, float w, boolean active, boolean divider) {
        final String uuid = a.uuid();
        boolean sel = same(uuid, selected);
        boolean hv = kit.hit("row:" + uuid, x, y, w, ROW * d, () -> selected = uuid);
        if (divider) kit.hline(x + 22 * d, y, w - 44 * d);
        float s = anim(sel ? 1 : 0, "rowsel:" + uuid), hvT = anim(hv ? 1 : 0, "rowhv:" + uuid);
        if (s > 0.01f || hvT > 0.01f) {
            Draw.rect(kit.g, x + 6 * d, y + 6 * d, w - 12 * d, ROW * d - 12 * d, 12 * d, Draw.mix(Draw.alpha(Tokens.SURFACE, hvT), 0x179B5CFF, s));
            if (s > 0.01f) Draw.ring(kit.g, x + 6 * d, y + 6 * d, w - 12 * d, ROW * d - 12 * d, 12 * d, 1, Draw.alpha(Tokens.ACC_LINE, s));
        }
        float hs = 40 * d, hx = x + 22 * d, hy = y + (ROW * d - hs) / 2f;
        int[] px = active && Friends.me() != null && Friends.me().head != null ? Friends.me().head : SkinHeads.get(uuid);
        kit.head(px, a.name(), hx, hy, hs, active ? Tokens.OK : 0, false);
        float tx = hx + hs + 14 * d;
        boolean busyHere = same(uuid, working);
        // rechts: Abzeichen
        float right = x + w - 22 * d;
        if (busyHere) {
            spin("refresh", right - 20 * d, y + ROW * d / 2 - 10 * d, 20 * d, Tokens.ACC);
            right -= 32 * d;
        }
        if (active) {
            float bw = kit.width("Aktiv", 12.5f, Tokens.SEMIBOLD) + 18 * d;
            kit.badge("Aktiv", right - bw, y + ROW * d / 2 - 12 * d, true);
            right -= bw + 10 * d;
        }
        if (a.needsLogin()) {
            float bw = kit.width("Anmeldung nötig", 12.5f, Tokens.SEMIBOLD) + 18 * d;
            kit.badge("Anmeldung nötig", null, right - bw, y + ROW * d / 2 - 12 * d, Tokens.WARN);
            right -= bw + 10 * d;
        }
        float tw = right - tx - 8 * d;
        kit.textMid(a.name(), tx, y + 28 * d, 16, Tokens.SEMIBOLD, Tokens.TEXT, tw);
        kit.textMid(detail(a, active), tx, y + 50 * d, 13, Tokens.MEDIUM, a.needsLogin() ? Draw.mix(Tokens.WARN, Tokens.MUTED, 0.4f) : Tokens.MUTED, tw);
    }

    private float anim(float target, String key) {
        return de.myticlegacy.client.gui.ui.Motion.value(kit.key(key), target, Tokens.MS_HOVER);
    }

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm");
    private static final SimpleDateFormat DAY = new SimpleDateFormat("dd.MM. HH:mm");

    /** Zweite Zeile: Art und Gültigkeit der Anmeldung */
    private static String detail(Accounts.Account a, boolean active) {
        if (a.offline()) return "Offline-Konto (nur Entwicklung) · keine Anmeldung";
        String kind = "Microsoft";
        if (a.needsLogin()) return kind + " · Anmeldung abgelaufen – „Neu einloggen“";
        long exp = a.expiresAt();
        if (exp <= 0) return kind + " · Minecraft: Java Edition";
        long now = System.currentTimeMillis();
        if (exp <= now) return kind + " · Sitzung abgelaufen – wird beim Wechseln erneuert";
        Date date = new Date(exp);
        String when = exp - now < 20 * 3600_000L ? "bis " + TIME.format(date) + " Uhr" : "bis " + DAY.format(date);
        return kind + " · Sitzung gültig " + when;
    }

    private void drawAddRow(float x, float y, float w, boolean divider) {
        if (divider) kit.hline(x + 22 * d, y, w - 44 * d);
        float hs = 40 * d, hx = x + 22 * d, hy = y + (ROW * d - hs) / 2f;
        Draw.rect(kit.g, hx, hy, hs, hs, 9 * d, Tokens.SURFACE);
        Draw.ring(kit.g, hx, hy, hs, hs, 9 * d, 1, Tokens.LINE);
        Icons.draw(kit.g, "plus", hx + 12 * d, hy + 12 * d, 16 * d, Tokens.MUTED);
        float tx = hx + hs + 14 * d;
        kit.textMid("Konto hinzufügen", tx, y + 28 * d, 16, Tokens.SEMIBOLD, Tokens.TEXT, 0);
        kit.textMid("Anmeldung mit Microsoft im Launcher-Fenster", tx, y + 50 * d, 13, Tokens.MEDIUM, Tokens.MUTED, 0);
        float bh = 38 * d, bw = kit.buttonWidth("Anmelden", "external", bh);
        boolean adding = "add".equals(workingWhat);
        kit.button("add", x + w - 22 * d - bw, y + (ROW * d - bh) / 2f, bw, bh, adding ? "Warte …" : "Anmelden", "external", Widgets.PRIMARY,
                working == null ? this::add : null);
    }

    private void drawFooter(float x, float fy, float w) {
        Accounts.Account a = selectedAccount();
        boolean active = a != null && same(a.uuid(), current());
        boolean idle = working == null && Accounts.available() && accounts != null;
        float bh = 38 * d, by = fy + (FOOT * d - bh) / 2f, right = x + w - 32 * d;

        // Wechseln / Einloggen (Hauptaktion)
        String main = a != null && a.needsLogin() ? "Einloggen" : "Wechseln";
        float mw = kit.buttonWidth(main, "swap", bh) + 8 * d;
        right -= mw;
        kit.button("switch", right, by, mw, bh, main, "swap", Widgets.PRIMARY, idle && a != null && !active ? () -> {
            if (a.needsLogin()) refresh(a);
            else switchTo(a);
        } : null);

        // Neu einloggen (Anmeldung erneuern + übernehmen)
        float rw = kit.buttonWidth("Neu einloggen", "refresh", bh);
        right -= rw + 10 * d;
        kit.button("refresh", right, by, rw, bh, "Neu einloggen", "refresh", Widgets.SECONDARY, idle && a != null && !a.offline() ? () -> refresh(a) : null);
        if (kit.hover("refresh")) kit.tooltip(a != null && a.offline() ? "Offline-Konten haben keine Anmeldung" : "Anmeldung erneuern und im Spiel übernehmen",
                right + rw / 2f, by - 36 * d);
        else if (a != null && a.offline() && kit.over(right, by, rw, bh)) kit.tooltip("Offline-Konten haben keine Anmeldung", right + rw / 2f, by - 36 * d);

        // Entfernen
        if (Accounts.supports("remove")) {
            float ew = kit.buttonWidth("Entfernen", "trash", bh);
            right -= ew + 10 * d;
            kit.button("remove", right, by, ew, bh, "Entfernen", "trash", Widgets.DANGER, idle && a != null && !active ? () -> confirmRemove = a : null);
        }

        // Rückmeldung
        String text = status;
        int kind = statusKind;
        if (text == null) {
            text = a == null ? "Konto auswählen" : active ? "Dieses Konto ist im Spiel angemeldet" : "Ausgewählt: " + a.name();
            kind = 0;
        }
        float tx = x + 32 * d, cy = fy + FOOT * d / 2f, maxW = right - tx - 16 * d;
        int color = kind == 1 ? Draw.mix(Tokens.OK, Tokens.TEXT, 0.35f) : kind == 2 ? Draw.mix(Tokens.BAD, Tokens.TEXT, 0.25f) : Tokens.MUTED;
        String icon = kind == 1 ? "check" : kind == 2 ? "info" : kind == 3 ? "refresh" : null;
        if (icon != null) {
            int ic = kind == 1 ? Tokens.OK : kind == 2 ? Tokens.BAD : Tokens.ACC;
            if (kind == 3) spin(icon, tx, cy - 9 * d, 18 * d, ic);
            else Icons.draw(kit.g, icon, tx, cy - 9 * d, 18 * d, ic);
            tx += 28 * d;
            maxW -= 28 * d;
        }
        kit.textMid(text, tx, cy, 14.5f, Tokens.MEDIUM, color, maxW);
    }

    private void drawConfirm() {
        final Accounts.Account a = confirmRemove;
        float w = 520 * d, h = 236 * d, x = (vw - w) / 2f, y = (vh - h) / 2f;
        kit.modal(x, y, w, h, () -> confirmRemove = null);
        kit.text("Konto entfernen?", x + 28 * d, y + 30 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.paragraph(a.name() + " wird aus dem Mytic Launcher entfernt. Du kannst es jederzeit wieder hinzufügen.", x + 28 * d, y + 72 * d, w - 56 * d,
                15, Tokens.MEDIUM, Tokens.MUTED, 22, 3);
        float bh = 44 * d, by = y + h - 28 * d - bh;
        float ew = kit.buttonWidth("Entfernen", "trash", bh);
        kit.button("cfm:ok", x + w - 28 * d - ew, by, ew, bh, "Entfernen", "trash", Widgets.DANGER, () -> {
            confirmRemove = null;
            remove(a);
        });
        float cw = kit.buttonWidth("Abbrechen", null, bh);
        kit.button("cfm:no", x + w - 28 * d - ew - 10 * d - cw, by, cw, bh, "Abbrechen", null, Widgets.GHOST, () -> confirmRemove = null);
    }

    // ------------------------------------------------------------------------------------------ Aktionen

    private void begin(Accounts.Account a, String what, String text) {
        working = a == null ? "~" : a.uuid();
        workingWhat = what;
        setStatus(text, 3);
    }

    private void finish() {
        working = null;
        workingWhat = null;
    }

    private void switchTo(Accounts.Account a) {
        begin(a, "switch", "Wechsle zu " + a.name() + " …");
        Accounts.switchTo(a, () -> {
            finish();
            setStatus("Angemeldet als " + a.name(), 1);
            Toasts.show("Konto gewechselt: " + a.name(), "swap", Toasts.OK);
            load();
        }, error -> {
            finish();
            setStatus(error, 2);
            Toasts.error("Wechsel fehlgeschlagen");
        });
    }

    private void refresh(Accounts.Account a) {
        begin(a, "refresh", Accounts.supports("refresh") ? "Anmeldung von " + a.name() + " wird erneuert … (ggf. im Launcher-Fenster anmelden)"
                : "Anmeldung von " + a.name() + " wird übernommen …");
        Accounts.refresh(a, relogin -> {
            finish();
            setStatus((relogin ? "Neu angemeldet als " : "Anmeldung erneuert: ") + a.name(), 1);
            Toasts.show(relogin ? "Neu angemeldet: " + a.name() : "Anmeldung erneuert: " + a.name(), "refresh", Toasts.OK);
            load();
        }, error -> {
            finish();
            setStatus(error, 2);
            Toasts.error("Neu einloggen fehlgeschlagen");
        });
    }

    private void add() {
        begin(null, "add", "Melde dich im Launcher-Fenster mit Microsoft an …");
        Accounts.add(name -> {
            finish();
            setStatus("Hinzugefügt: " + name + " – auswählen und wechseln", 1);
            Toasts.ok("Konto hinzugefügt: " + name);
            load();
        }, error -> {
            finish();
            setStatus(error, 2);
        });
    }

    private void remove(Accounts.Account a) {
        begin(a, "remove", a.name() + " wird entfernt …");
        Accounts.remove(a, () -> {
            finish();
            setStatus(a.name() + " entfernt", 1);
            Toasts.ok("Konto entfernt: " + a.name());
            selected = current();
            load();
        }, error -> {
            finish();
            setStatus(error, 2);
        });
    }
}
