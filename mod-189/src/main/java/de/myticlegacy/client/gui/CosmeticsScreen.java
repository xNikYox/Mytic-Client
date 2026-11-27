package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.cosmetics.AdminCore;
import de.myticlegacy.client.cosmetics.CoinsCore;
import de.myticlegacy.client.cosmetics.Cosmetics;
import de.myticlegacy.client.cosmetics.CosmeticsCore;
import de.myticlegacy.client.cosmetics.MiniMeCore;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.ui.Bake;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Motion;
import de.myticlegacy.client.gui.ui.SkinHeads;
import de.myticlegacy.client.gui.ui.Toasts;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Type;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.compat.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kosmetik im neuen Design (wie die UI-Vorschau): links die Bühne mit dem eigenen Spieler (3D-Modell mit allen
 * aktiven Cosmetics, mit der Maus drehbar), rechts Kategorien als Segmente, Raster mit Vorschauen (Umhänge, Flügel,
 * Schilde, Auren), Formulare für MiniMe und Nametag, für Admins das Freischalten. Auswahl wirkt sofort und wird
 * gespeichert; andere Mytic-Spieler sehen sie.
 * Shop: jedes Teil kostet Coins (Preis auf der Karte, „Kaufen“ mit Rückfrage und Vorschau an der Figur), Sets mit
 * Rabatt, Reiter „Coins“ mit Tagesbonus, Verschenken an Freunde und Verlauf; Admins buchen Coins im Reiter „Admin“.
 */
public class CosmeticsScreen extends DesignScreen {
    private static final float W = 1600, H = 880, STAGE = 620, HEAD = 88, SUB = 72, FOOT = 92;
    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    // 1.8.9 hat keine Schilde
    private static final String[] BASE_TABS = {"Umhänge", "MiniMe", "Flügel", "Auren", "Kopf & Hals", "Nametag"};
    private static final String ACC_TAB = "Kopf & Hals";
    /** Gold für Coins */
    private static final int GOLD = 0xFFFFC23A;
    /** Plätze im Reiter „Kopf & Hals“: Platz im Katalog, Beschriftung */
    private static final String[][] ACC_SLOTS = {{"kopf", "Kopf"}, {"hals", "Hals"}};
    /** &-Farbcodes in Minecraft-Reihenfolge und ihre Farben */
    private static final String COLOR_CODES = "0123456789abcdef";
    private static final int[] COLORS = {0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};
    /** Format-Knöpfe: Beschriftung, Code */
    private static final String[][] FORMATS = {{"§lF", "l"}, {"§oK", "o"}, {"§nU", "n"}, {"§mD", "m"}, {"§k?", "k"}, {"Reset", "r"}};

    /** Reiter; "Admin" nur für Konten mit Admin-Recht */
    private final String[] TABS;
    /** zuletzt offener Reiter bleibt beim nächsten Öffnen */
    private static int tab;
    /** Stelle, deren MiniMe gerade bearbeitet wird (Index in MiniMeCore.POSITIONS) */
    private static int slot;
    /** Platz im Reiter „Kopf & Hals“ (Index in ACC_SLOTS) */
    private static int accSlot;
    private final Kit.Field search = new Kit.Field(24);
    private final Kit.Field mini = new Kit.Field(16);
    private final Kit.Field tag = new Kit.Field(CosmeticsCore.NAMETAG_RAW);
    private final Kit.Field admin = new Kit.Field(32);
    private final Kit.Field coinAmount = new Kit.Field(7);
    private final Kit.Field coinReason = new Kit.Field(60);
    private final Kit.Field giftAmount = new Kit.Field(3);
    private final Kit.Scroll setScroll = new Kit.Scroll();
    private final Kit.Scroll txScroll = new Kit.Scroll();

    /** Offener Kauf-Dialog: Teil (kind/id/item) oder Set (set) */
    private static final class Buy {
        String kind, id, item, name;
        CoinsCore.SetInfo set;
        int price;
        volatile String error;
        volatile boolean running;
    }

    private Buy buying;
    /** Freund, dem Coins geschenkt werden sollen; true = Rückfrage offen */
    private String giftTo;
    private boolean giftConfirm;
    /** Nach einem Kauf ausrüsten – im Spiel-Thread (frame), nicht im Hintergrund */
    private volatile String equipKind, equipId;
    private volatile boolean closeBuy;
    private final Kit.Scroll scroll = new Kit.Scroll();
    private String message;
    /** Drehung der Figur (Grad, 0 = von vorn) */
    private float yaw = -18, yawTarget = -18, dragLast = Float.NaN;

    public CosmeticsScreen() {
        super("cos", "Kosmetik");
        CosmeticsCore.refreshOwned();
        // Shop-Reiter nur mit Freundesliste (Coins liegen auf dem Friends-Server), „Admin“ nur mit Admin-Recht
        List<String> tabs = new ArrayList<String>(java.util.Arrays.asList(BASE_TABS));
        if (Friends.available()) {
            tabs.add("Sets");
            tabs.add("Coins");
        }
        if (CosmeticsCore.isAdmin()) tabs.add("Admin");
        TABS = tabs.toArray(new String[0]);
        if (tab >= TABS.length) tab = 0;
        String own = CosmeticsCore.ownNametag();
        tag.text = own == null ? "" : own;
        tag.onEnter = () -> {
            CosmeticsCore.selectNametag(tag.text);
            kit.focus(null);
        };
        mini.allowed = NAME_CHARS;
        mini.onEnter = this::applyMini;
        admin.allowed = NAME_CHARS;
        admin.onEnter = () -> {
            AdminCore.load(admin.text);
            CoinsCore.adminLoad(admin.text);
            kit.focus(null);
        };
        coinAmount.allowed = "0123456789";
        giftAmount.allowed = "0123456789";
    }

    // ------------------------------------------------------------------------------------------ Versionsteile

    private boolean inWorld() {
        return mc.theWorld != null;
    }

    /** Eigener Spieler in den Bereich (Pixel); ohne Welt (Hauptmenü) gibt es in 1.8.9 kein Spieler-Objekt */
    private boolean drawPlayer(float x0, float y0, float x1, float y1) {
        if (mc.thePlayer == null) return false;
        float h = y1 - y0;
        int size = Math.round(h / 2.15f);
        // Blickrichtung über die Maus-Vorgabe der Inventar-Vorschau (begrenzt): Drehung bis etwa ±60°
        float look = (float) Math.tan(Math.toRadians(Math.max(-60, Math.min(60, -yaw)) / 2.4)) * 40f;
        net.minecraft.client.gui.inventory.GuiInventory.drawEntityOnScreen(Math.round((x0 + x1) / 2f), Math.round(y1 - h * 0.04f), size, look, -8f, mc.thePlayer);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        return true;
    }

    private String playerUuid() {
        return mc.getSession().getPlayerID();
    }

    // ------------------------------------------------------------------------------------------ Daten

    private boolean isTab(String name) {
        return name.equals(TABS[tab]);
    }

    private boolean gridTab() {
        return isTab("Umhänge") || isTab("Flügel") || isTab("Schilde") || isTab("Auren") || isTab(ACC_TAB);
    }

    private static String icon(String tabName) {
        if (tabName.equals("Umhänge")) return "cape";
        if (tabName.equals("MiniMe")) return "mini";
        if (tabName.equals("Flügel")) return "wings";
        if (tabName.equals("Schilde")) return "shield";
        if (tabName.equals("Auren")) return "sparkle";
        if (tabName.equals("Nametag")) return "tag";
        if (tabName.equals(ACC_TAB)) return "hat";
        if (tabName.equals("Sets")) return "gift";
        if (tabName.equals("Coins")) return "coin";
        return "lock";
    }

    /** Art der Cosmetics im aktuellen Reiter (für die Freischaltung) */
    private String kindOfTab() {
        return isTab("Flügel") ? "wings" : isTab("Schilde") ? "shield" : isTab("Auren") ? "aura" : isTab(ACC_TAB) ? "acc" : "cape";
    }

    /** Einträge des Raster-Reiters als {id, name}; id null = „Keiner“ */
    private List<String[]> items() {
        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{null, isTab("Flügel") || isTab("Auren") ? "Keine" : isTab(ACC_TAB) ? "Keins" : "Keiner"});
        if (isTab("Umhänge")) for (CosmeticsCore.Cape c : CosmeticsCore.capes()) out.add(new String[]{c.id, c.name});
        else if (isTab("Schilde")) for (String[] s : CosmeticsCore.shields()) out.add(new String[]{s[0], s[1]});
        else if (isTab("Auren")) for (CosmeticsCore.Aura a : CosmeticsCore.auras()) out.add(new String[]{a.id, a.name});
        else if (isTab(ACC_TAB)) {
            for (CosmeticsCore.Acc a : CosmeticsCore.accessories()) if (a.slot.equals(ACC_SLOTS[accSlot][0])) out.add(new String[]{a.id, a.name});
        }
        else for (String[] w : CosmeticsCore.wings()) out.add(new String[]{w[0], w[1]});
        return out;
    }

    private String selectedOfTab() {
        if (isTab("Flügel")) return CosmeticsCore.selectedWings();
        if (isTab("Schilde")) return CosmeticsCore.selectedShield();
        if (isTab("Auren")) return CosmeticsCore.selectedAura();
        if (isTab(ACC_TAB)) return CosmeticsCore.selectedAcc(ACC_SLOTS[accSlot][0]);
        return CosmeticsCore.selected();
    }

    private void pick(String id) {
        if (isTab("Flügel")) CosmeticsCore.selectWings(id);
        else if (isTab("Schilde")) CosmeticsCore.selectShield(id);
        else if (isTab("Auren")) CosmeticsCore.selectAura(id);
        else if (isTab(ACC_TAB)) CosmeticsCore.selectAcc(ACC_SLOTS[accSlot][0], id);
        else CosmeticsCore.select(id);
    }

    private void unequipAll() {
        CosmeticsCore.select(null);
        CosmeticsCore.selectWings(null);
        if (java.util.Arrays.asList(BASE_TABS).contains("Schilde")) CosmeticsCore.selectShield(null);
        CosmeticsCore.selectAura(null);
        for (String[] s : ACC_SLOTS) if (CosmeticsCore.selectedAcc(s[0]) != null) CosmeticsCore.selectAcc(s[0], null);
        Toasts.ok("Alles abgelegt");
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void frame(GuiGraphics g) {
        // nach einem Kauf (Antwort kam im Hintergrund): ausrüsten und Dialog schließen – hier im Spiel-Thread
        String ek = equipKind, ei = equipId;
        if (ek != null) {
            equipKind = null;
            equip(ek, ei);
        }
        if (closeBuy) {
            closeBuy = false;
            buying = null;
        }
        float[] r = center(W, H);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        kit.window(x, y, w, h, !inWorld());
        float sw = STAGE * d;
        drawStage(x, y, sw, h);
        kit.vline(x + sw, y, h);
        float mx = x + sw, mw = w - sw;
        drawHeader(mx, y, mw);
        kit.hline(mx, y + HEAD * d, mw);
        drawTabs(mx, y + HEAD * d, mw);
        float bodyY = y + (HEAD + SUB) * d, bodyH = h - (HEAD + SUB + FOOT) * d;
        drawBody(mx, bodyY, mw, bodyH);
        float fy = y + h - FOOT * d;
        kit.hline(mx, fy, mw);
        drawFooter(mx, fy, mw);
        if (buying != null) drawBuy();
        else if (giftConfirm && giftTo != null) drawGiftConfirm();
    }

    // ------------------------------------------------------------------------------------------ Bühne

    private void drawStage(float x, float y, float w, float h) {
        // linke Fläche dunkler, links an die Rundung des Fensters angepasst
        float r = Tokens.RAD_XL * d, inset = 1f / Draw.k(kit.g);
        Draw.rect(kit.g, x + r, y + inset, w - r, h - 2 * inset, 0, 0x1F000000);
        kit.clip(x, y, r, h);
        Draw.rect(kit.g, x + inset, y + inset, 2 * r, h - 2 * inset, r, 0x1F000000);
        kit.unclip();
        float cx = x + w / 2f, gy = y + h * 0.46f;
        // weicher Akzent-Schein
        for (int i = 0; i < 28; i++) {
            float t = i / 28f, rr = 290 * d * (1 - t * t * 0.0f - t * 0.93f);
            Draw.circle(kit.g, cx, gy, rr, 0x039B5CFF);
        }
        // Boden: Schatten und Ring (Ellipsen)
        float floorY = y + 700 * d;
        ellipse(cx, floorY, 190 * d, 45 * d, 0x2E000000, 0);
        boolean aura = CosmeticsCore.selectedAura() != null;
        float pulse = aura && animate() ? 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 380.0) : aura ? 1f : 0.45f;
        ellipse(cx, floorY - 14 * d, 210 * d, 65 * d, 0, Draw.alpha(0x8CB56CFF, pulse));

        // Figur
        yaw += (yawTarget - yaw) * (Float.isNaN(dragLast) ? 0.12f : 0.6f);
        float top = y + 150 * d, bottom = y + 690 * d;
        boolean model = drawPlayer(cx - 200 * d, top, cx + 200 * d, bottom);
        if (!model) {
            Friends.Friend me = Friends.me();
            int[] head = me != null && me.head != null ? me.head : SkinHeads.get(playerUuid());
            float hs = 168 * d;
            Draw.shadow(kit.g, cx - hs / 2f, y + 330 * d + 20 * d, hs, hs, 32 * d, 50 * d, -10 * d, 0x80000000);
            Draw.head(kit.g, head, Cosmetics.playerName(), cx - hs / 2f, y + 330 * d, hs, 32 * d);
            kit.textCenter(Cosmetics.playerName(), cx, y + 540 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT);
            kit.textCenter("Vorschau der Figur in der Welt", cx, y + 568 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE);
        } else {
            kit.dragArea("stage", x, y + 110 * d, w, h - 220 * d, false, px -> {
                if (!Float.isNaN(dragLast)) yawTarget += (px - dragLast) * 0.6f / Math.max(0.4f, d);
                dragLast = px;
            });
            // Nametag über dem Kopf
            String shown = CosmeticsCore.formatNametag(tag.text);
            if (shown != null) drawMcText(shown, cx, y + 128 * d, true);
        }

        // oben: Name und Hinweis
        float k = Draw.begin(kit.g);
        int es = Math.max(8, Math.round(40 * d * k));
        Draw.pxBaked(kit.g, Bake.emblem(es), Math.round((x + 28 * d) * k), Math.round((y + 28 * d) * k), 0xFFFFFFFF);
        Draw.end(kit.g);
        kit.text("Kosmetik", x + 80 * d, y + 33 * d, 19, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.text(Friends.available() ? "Sichtbar für alle Mytic-Spieler" : "Nur du siehst sie (ohne Mytic Launcher)", x + 80 * d, y + 56 * d, 12.5f,
                Tokens.MEDIUM, Tokens.SUBTLE);
        if (model) {
            float bw = kit.width("Ziehen zum Drehen", 12.5f, Tokens.SEMIBOLD) + 36 * d;
            kit.badge("Ziehen zum Drehen", "move", x + w - 28 * d - bw, y + 36 * d, Tokens.MUTED);
            // unten: drehen
            float by = y + h - 28 * d - 40 * d;
            kit.iconButton("rotl", x + 28 * d, by, 40 * d, "rotL", true, () -> yawTarget -= 45);
            kit.iconButton("rotr", x + 74 * d, by, 40 * d, "rotR", true, () -> yawTarget += 45);
            float bh = 38 * d, bx = x + 128 * d;
            bx += kit.button("front", bx, by + 1 * d, bh, "Vorderseite", null, Widgets.GHOST, () -> yawTarget = 0) + 6 * d;
            kit.button("back", bx, by + 1 * d, bh, "Rückseite", null, Widgets.GHOST, () -> yawTarget = 180);
        }
        if (inWorld()) kit.textRight("F5: Ansicht wechseln", x + w - 28 * d, y + h - 48 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE);
    }

    /** Ellipse (Fläche und/oder Ring) um cx/cy */
    private void ellipse(float cx, float cy, float rx, float ry, int fill, int line) {
        Gfx.push(kit.g);
        Gfx.translate(kit.g, cx, cy);
        Gfx.scale(kit.g, 1, ry / rx);
        if (fill != 0) Draw.rect(kit.g, -rx, -rx, 2 * rx, 2 * rx, rx, fill);
        if (line != 0) Draw.ring(kit.g, -rx, -rx, 2 * rx, 2 * rx, rx, 2, line);
        Gfx.pop(kit.g);
    }

    /** Minecraft-Text mit Formatierung (Nametag), mittig um cx, in ganzer Pixelschrift-Vergrößerung */
    private void drawMcText(String text, float cx, float y, boolean box) {
        int s = Type.pixelScale(kit.g, 11 * d);
        float k = Draw.k(kit.g);
        float tw = font.width(text) * s / k, th = 9f * s / k;
        if (box) Draw.rect(kit.g, cx - tw / 2f - 6 * d, y - 4 * d, tw + 12 * d, th + 6 * d, 6 * d, 0x59000000);
        Type.pixel(kit.g, text, cx - tw / 2f, y, s, 0, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------------------------------ Kopf, Reiter, Fuß

    private void drawHeader(float x, float y, float w) {
        String title = TABS[tab];
        String sub;
        if (gridTab()) {
            List<String[]> list = items();
            int owned = 0;
            for (int i = 1; i < list.size(); i++) if (CosmeticsCore.owns(kindOfTab(), list.get(i)[0])) owned++;
            sub = owned + " von " + (list.size() - 1) + (Friends.available() ? " gekauft" : " freigeschaltet");
        } else if (isTab("MiniMe")) {
            sub = "Ein kleiner Begleiter mit dem Skin eines beliebigen Spielers";
        } else if (isTab("Nametag")) {
            sub = "Text über deinem Namen, mit Farben und Formaten";
        } else if (isTab("Sets")) {
            sub = "Ganze Sets auf einmal – " + CoinsCore.discount() + " % günstiger als einzeln";
        } else if (isTab("Coins")) {
            sub = "Tagesbonus, Geschenke an Freunde und dein Verlauf";
        } else {
            sub = "Cosmetics freischalten und Coins buchen";
        }
        kit.text(title, x + 32 * d, y + 24 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.text(sub, x + 32 * d, y + 54 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        float right = x + w - 32 * d - 40 * d;
        kit.iconButton("close", right, y + 24 * d, 40 * d, "x", true, () -> Compat.setScreen(null));
        float left = right - 12 * d;
        if (gridTab()) {
            left -= 280 * d;
            kit.field("search", search, left, y + 24 * d, 280 * d, 40 * d, "Suchen …", "search");
            left -= 12 * d;
        }
        // Kontostand (kommt mit dem Lebenszeichen); Klick öffnet den Reiter „Coins“
        if (CoinsCore.supported()) {
            String text = (CoinsCore.balance() < 0 ? "…" : CoinsCore.fmt(CoinsCore.balance())) + " Coins";
            float bw = kit.width(text, 12.5f, Tokens.SEMIBOLD) + 36 * d;
            left -= bw;
            kit.badge(text, "coin", left, y + 32 * d, GOLD);
            final int coinsTab = java.util.Arrays.asList(TABS).indexOf("Coins");
            if (coinsTab >= 0) kit.hit("coinsbadge", left, y + 32 * d, bw, 24 * d, () -> openTab(coinsTab));
        }
    }

    private void openTab(int i) {
        tab = i;
        kit.focus(null);
        scroll.reset();
        message = null;
        if (isTab("Coins") && !CoinsCore.infoLoaded) CoinsCore.loadInfo();
    }

    private void drawTabs(float x, float y, float w) {
        String[] icons = new String[TABS.length];
        // Breite wie in Kit.seg; passen nicht alle Reiter mit Symbol hinein, ohne Symbole
        float total = 6 * d;
        for (int i = 0; i < TABS.length; i++) {
            icons[i] = icon(TABS[i]);
            total += 54 * d + kit.width(TABS[i], 14.5f, Tokens.SEMIBOLD);
        }
        if (total > w - 64 * d) icons = null;
        kit.seg("tabs", x + 32 * d, y + (SUB * d - 44 * d) / 2f, TABS, icons, null, tab, this::openTab);
    }

    private void drawFooter(float x, float y, float w) {
        float cy = y + FOOT * d / 2f, tx = x + 32 * d;
        kit.caption("Ausgerüstet", tx, cy - 4.5f * d);
        tx += kit.width("AUSGERÜSTET", 12.5f, Tokens.SEMIBOLD) + 16 * d;
        float bh = 38 * d, right = x + w - 32 * d;
        float sw = kit.width("Gespeichert", 12.5f, Tokens.SEMIBOLD) + 36 * d;
        right -= sw;
        kit.badge("Gespeichert", "check", right, cy - 12 * d, Tokens.OK);
        float gw = kit.buttonWidth("Alles ablegen", null, bh);
        right -= gw + 12 * d;
        kit.button("off", right, cy - bh / 2f, gw, bh, "Alles ablegen", null, Widgets.GHOST, this::unequipAll);
        List<String[]> chips = new ArrayList<String[]>();
        CosmeticsCore.Cape cape = CosmeticsCore.byId(CosmeticsCore.selected());
        if (cape != null) chips.add(new String[]{"cape", cape.name});
        String wings = CosmeticsCore.selectedWings();
        if (wings != null) chips.add(new String[]{"wings", nameOf(CosmeticsCore.wings(), wings)});
        if (java.util.Arrays.asList(BASE_TABS).contains("Schilde")) {
            String shield = CosmeticsCore.selectedShield();
            if (shield != null) chips.add(new String[]{"shield", nameOf(CosmeticsCore.shields(), shield)});
        }
        CosmeticsCore.Aura aura = CosmeticsCore.aura(CosmeticsCore.selectedAura());
        if (aura != null) chips.add(new String[]{"sparkle", aura.name});
        for (String[] s : ACC_SLOTS) {
            CosmeticsCore.Acc acc = CosmeticsCore.acc(CosmeticsCore.selectedAcc(s[0]));
            if (acc != null) chips.add(new String[]{"hat", acc.name});
        }
        if (MiniMeCore.any()) chips.add(new String[]{"mini", "MiniMe"});
        if (CosmeticsCore.ownNametag() != null) chips.add(new String[]{"tag", "Nametag"});
        float limit = right - 12 * d;
        if (chips.isEmpty()) kit.textMid("Nichts ausgerüstet", tx, cy, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE, 0);
        for (String[] c : chips) {
            float cw = kit.width(c[1], 12.5f, Tokens.SEMIBOLD) + 36 * d;
            if (tx + cw > limit) break;
            kit.badge(c[1], c[0], tx, cy - 12 * d, Tokens.ACC);
            tx += cw + 6 * d;
        }
    }

    private static String nameOf(List<String[]> list, String id) {
        for (String[] e : list) if (e[0].equals(id)) return e[1];
        return id;
    }

    // ------------------------------------------------------------------------------------------ Inhalt

    private void drawBody(float x, float y, float w, float h) {
        if (isTab("Admin")) {
            drawAdmin(x, y, w, h);
        } else if (isTab("Sets")) {
            drawSets(x, y, w, h);
        } else if (isTab("Coins")) {
            drawCoins(x, y, w, h);
        } else if (isTab("MiniMe") && !CosmeticsCore.owns("minime", null)) {
            buyCard(x, y, w, h, "mini", "MiniMe", "minime", "Ein kleiner Begleiter mit dem Skin eines beliebigen Spielers – an Schulter, Kopf, Hand oder Bein.");
        } else if (isTab("Nametag") && !CosmeticsCore.owns("nametag", null) && !anyPresetOwned() && CoinsCore.price("nametag") == null) {
            empty(x, y, w, h, "lock", "Der Nametag ist für dich noch nicht freigeschaltet", Friends.available() ? "Preise werden geladen …" : "Nur über den Mytic Launcher.");
        } else if (isTab("MiniMe")) {
            drawMiniMe(x, y, w, h);
        } else if (isTab("Nametag")) {
            drawNametag(x, y, w, h);
        } else if (!CosmeticsCore.available()) {
            empty(x, y, w, h, "sparkle", "Cosmetics nur über den Mytic Launcher", "Der Launcher lädt das Cosmetics-Paket beim Spielstart herunter.");
        } else if (isTab(ACC_TAB)) {
            drawAcc(x, y, w, h);
        } else {
            drawGrid(x, y, w, h);
        }
    }

    private void empty(float x, float y, float w, float h, String icon, String title, String text) {
        float cx = x + w / 2f, cy = y + h / 2f - 40 * d;
        Draw.rect(kit.g, cx - 32 * d, cy - 32 * d, 64 * d, 64 * d, 18 * d, Tokens.SURFACE);
        Icons.draw(kit.g, icon, cx - 14 * d, cy - 14 * d, 28 * d, Tokens.MUTED);
        kit.textCenter(title, cx, cy + 60 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT);
        if (text != null) kit.textCenter(text, cx, cy + 88 * d, 14.5f, Tokens.MEDIUM, Tokens.MUTED);
    }

    private void drawGrid(float x, float y, float w, float h) {
        List<String[]> all = items();
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        List<String[]> list = new ArrayList<String[]>();
        for (String[] e : all) if (q.isEmpty() || (e[0] != null && e[1].toLowerCase(Locale.ROOT).contains(q))) list.add(e);
        if (list.isEmpty()) {
            empty(x, y, w, h, "search", "Nichts gefunden", null);
            return;
        }
        float gx = x + 32 * d, gw = w - 64 * d, gap = 14 * d, top = y + 8 * d, viewH = h - 8 * d;
        int cols = 4;
        float cw = (gw - gap * (cols - 1)) / cols, ch = 212 * d;
        int rows = (list.size() + cols - 1) / cols;
        float off = kit.scroll(scroll, gx, top, gw, viewH, rows * (ch + gap) + 20 * d);
        kit.clip(gx - 4 * d, top, gw + 8 * d, viewH);
        String sel = selectedOfTab();
        for (int i = 0; i < list.size(); i++) {
            float cx = gx + (i % cols) * (cw + gap), cy = top + (i / cols) * (ch + gap) - off + 4 * d;
            if (cy + ch < top || cy > top + viewH) continue;
            drawItem(list.get(i), cx, cy, cw, ch, sel);
        }
        kit.unclip();
        kit.scrollbar("grid", scroll, gx + gw + 16 * d, top, viewH);
    }

    /** Reiter „Kopf & Hals“: Platz wählen, Helm-Schalter, darunter das Raster des Platzes. */
    private void drawAcc(float x, float y, float w, float h) {
        float lx = x + 32 * d, top = y + 8 * d;
        String[] labels = new String[ACC_SLOTS.length];
        String[] counts = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = ACC_SLOTS[i][1];
            counts[i] = CosmeticsCore.selectedAcc(ACC_SLOTS[i][0]) != null ? "1" : null;
        }
        kit.seg("accslot", lx, top, labels, null, counts, accSlot, i -> {
            accSlot = i;
            scroll.reset();
        });
        float right = x + w - 32 * d;
        kit.toggle("acchelm", right - 46 * d, top + 9 * d, MyticClient.config().accHideHelmet, () -> {
            MyticClient.config().accHideHelmet = !MyticClient.config().accHideHelmet;
            MyticClient.config().save();
        });
        kit.textRight("Kopf-Accessoire unter Helm ausblenden", right - 58 * d, top + 22 * d, 14, Tokens.MEDIUM, Tokens.MUTED);
        if (items().size() <= 1) {
            empty(x, y + 56 * d, w, h - 56 * d, "hat", "Noch keine Accessoires im Cosmetics-Paket", "Der Launcher lädt das neue Paket beim nächsten Spielstart.");
            return;
        }
        drawGrid(x, y + 56 * d, w, h - 56 * d);
    }

    private void drawItem(final String[] e, float x, float y, float w, float h, String sel) {
        final String id = e[0];
        final boolean locked = id != null && !CosmeticsCore.owns(kindOfTab(), id);
        boolean eq = id == null ? sel == null : id.equals(sel);
        String key = "it:" + kindOfTab() + ":" + id;
        final String kind = kindOfTab();
        boolean hv = kit.hit(key, x, y, w, h, () -> {
            if (locked) openBuy(kind, id, e[1]);
            else pick(id);
        });
        float ht = Motion.to(kit.key(key + ":h"), hv, Tokens.MS_HOVER), et = Motion.to(kit.key(key + ":e"), eq, 200);
        float yy = y - 3 * d * ht;
        Draw.rect(kit.g, x, yy, w, h, 16 * d, Draw.mix(Draw.mix(Tokens.SURFACE, Tokens.SURFACE_HOVER, ht), 0x1A9B5CFF, et));
        Draw.ring(kit.g, x, yy, w, h, 16 * d, 1, Draw.mix(Draw.mix(Tokens.LINE, Tokens.LINE_STRONG, ht), Tokens.ACC_LINE, et));
        // Bildfläche
        float ax = x + 12 * d, ay = yy + 12 * d, aw = w - 24 * d, ah = 136 * d;
        Draw.rect(kit.g, ax, ay, aw, ah, 11 * d, 0x38000000);
        boolean animated = hv || eq;
        int stripe = locked ? Tokens.LINE_STRONG : eq ? Tokens.ACC : Draw.alpha(Tokens.R_RARE, 0.8f);
        drawArt(kind, id, ax, ay, aw, ah, animated);
        if (locked) Draw.rect(kit.g, ax, ay, aw, ah, 11 * d, 0x8C0B0912);
        // Preis auf jeder Karte (gekauft: „Gekauft“)
        Integer price = id == null ? null : CoinsCore.price(kind + ":" + id);
        if (id != null && price != null && Friends.available()) {
            if (locked) {
                String pt = CoinsCore.fmt(price);
                boolean afford = CoinsCore.balance() >= price;
                kit.badge(pt, "coin", ax + 8 * d, ay + 8 * d, afford ? GOLD : Tokens.SUBTLE);
            } else if (!CosmeticsCore.isAdmin()) {
                kit.badge("Gekauft", "check", ax + 8 * d, ay + 8 * d, Tokens.OK);
            }
        }
        if (id != null) Draw.rect(kit.g, ax + 8 * d, ay + ah - 3 * d, aw - 16 * d, 3 * d, 1.5f * d, stripe);
        kit.textMid(e[1], x + 14 * d, yy + 12 * d + ah + 22 * d, 15, Tokens.SEMIBOLD, locked ? Tokens.MUTED : Tokens.TEXT, w - 28 * d);
        String state = eq ? "Ausgerüstet" : locked ? (price != null ? "Kaufen · " + CoinsCore.fmt(price) + " Coins" : "Gesperrt") : id == null ? "Ablegen" : "Verfügbar";
        int sc = eq ? 0xFFD9C2FF : locked ? (price != null ? Draw.mix(GOLD, Tokens.TEXT, 0.35f) : Tokens.SUBTLE) : Tokens.MUTED;
        kit.textMid(state, x + 14 * d, yy + 12 * d + ah + 44 * d, 12.5f, Tokens.SEMIBOLD, sc, w - 28 * d);
        if (et > 0.01f) {
            float s = 26 * d, mx = x + w - 20 * d - s, my = yy + 20 * d;
            Draw.shadow(kit.g, mx, my + 4 * d, s, s, s / 2, 12 * d, 0, Draw.alpha(0x66000000, et));
            Draw.accentRect(kit.g, mx, my, s, s, s / 2, et);
            Icons.draw(kit.g, "check", mx + 5 * d, my + 5 * d, 16 * d, Draw.alpha(0xFFFFFFFF, et));
        } else if (locked) {
            Icons.draw(kit.g, "lock", x + w - 20 * d - 18 * d, yy + 22 * d, 18 * d, Tokens.SUBTLE);
        }
    }

    /** Vorschau eines Eintrags in der Bildfläche (Pixel) */
    private void drawArt(String kind, String id, float x, float y, float w, float h, boolean animated) {
        GuiGraphics g = kit.g;
        float cx = x + w / 2f;
        if (id == null) {
            Icons.draw(g, "x", cx - 18 * d, y + h / 2f - 18 * d, 36 * d, Tokens.SUBTLE);
            return;
        }
        if (kind.equals("cape")) {
            CosmeticsCore.Cape cape = CosmeticsCore.byId(id);
            int ph = Math.round(h - 24 * d), pw = Math.round(ph * 10f / 16f);
            if (cape == null || !Cosmetics.drawPreview(g, cape, Math.round(cx - pw / 2f), Math.round(y + 12 * d), pw, ph, animated)) {
                Icons.draw(g, "cape", cx - 16 * d, y + h / 2f - 16 * d, 32 * d, Tokens.MUTED);
            }
        } else if (kind.equals("shield")) {
            int ph = Math.round(h - 24 * d), pw = Math.round(ph * 12f / 22f);
            Cosmetics.drawShieldPreview(g, id, Math.round(cx - pw / 2f), Math.round(y + 12 * d), pw, ph, animated);
        } else if (kind.equals("aura")) {
            drawAuraPreview(CosmeticsCore.aura(id), x, y, w, h, animated);
        } else if (kind.equals("acc")) {
            int s = Math.round(h - 12 * d);
            Cosmetics.drawAccPreview(g, id, Math.round(cx - s / 2f), Math.round(y + 6 * d), s, s);
        } else {
            int ww = Math.round(Math.min(w - 16 * d, (h - 20 * d) * 5f / 3f)), wh = ww * 3 / 5;
            Cosmetics.drawWingPreview(g, id, Math.round(cx - ww / 2f), Math.round(y + (h - wh) / 2f), ww, wh);
        }
    }

    /** Vorschau einer Aura: kleine Figur, um die Punkte in den Farben und im Muster der Aura laufen. */
    private void drawAuraPreview(CosmeticsCore.Aura aura, float x, float y, float w, float h, boolean animated) {
        float s = (h - 16 * d) / 64f;
        float cx = x + w / 2f, feet = y + h - 10 * d;
        Draw.rect(kit.g, cx - 4 * s, feet - 44 * s, 8 * s, 8 * s, 1.5f * s, 0xFF6B6B78);
        Draw.rect(kit.g, cx - 5 * s, feet - 36 * s, 10 * s, 14 * s, 1.5f * s, 0xFF55556A);
        Draw.rect(kit.g, cx - 5 * s, feet - 22 * s, 4 * s, 22 * s, 1.5f * s, 0xFF4A4A5C);
        Draw.rect(kit.g, cx + 1 * s, feet - 22 * s, 4 * s, 22 * s, 1.5f * s, 0xFF4A4A5C);
        if (aura == null) return;
        double t = animated ? System.currentTimeMillis() / 1000.0 : 1.3;
        int n = 10;
        for (int i = 0; i < n; i++) {
            double ph = (t * 0.5 + i / (double) n) % 1.0;
            double px, py;
            if ("orbit".equals(aura.pattern)) {
                double a = t * 2.2 + i * Math.PI * 2 / n;
                px = Math.cos(a) * 15;
                py = 26 + 12 * Math.sin(t * 1.3 + i);
            } else if ("halo".equals(aura.pattern)) {
                double a = t * 1.6 + i * Math.PI * 2 / n;
                px = Math.cos(a) * 9;
                py = 50 + Math.sin(a) * 2;
            } else if ("feet".equals(aura.pattern)) {
                double a = i * Math.PI * 2 / n;
                px = Math.cos(a) * (14 - ph * 8);
                py = 2 + ph * 6;
            } else if ("rise".equals(aura.pattern)) {
                px = Math.sin(i * 2.4) * 12;
                py = ph * 50;
            } else if ("fall".equals(aura.pattern)) {
                px = Math.sin(i * 2.4) * 13;
                py = 50 - ph * 50;
            } else if ("spiral".equals(aura.pattern)) {
                double a = t * 3.5 + i * 0.9;
                px = Math.cos(a) * 13;
                py = ((t * 0.9 + i * 0.15) % 2.1) * 24;
            } else if ("shoot".equals(aura.pattern)) {
                double q = (t * 1.1 + i / (double) n) % 1.0;
                px = Math.sin(i * 2.4) * 13 + q * 5;
                py = 56 - q * 54;
            } else {
                px = Math.sin(i * 3.7) * 14;
                py = 4 + ((i * 7.3) % 46);
            }
            float alpha = "sparkle".equals(aura.pattern) ? (float) Math.max(0, Math.sin((t * 2 + i * 0.37) * Math.PI)) : 0.85f;
            int col = aura.colors[i % aura.colors.length];
            int a = Math.round(alpha * 255) << 24;
            int dx = Math.round((float) (cx + px * s)), dy = Math.round((float) (feet - py * s));
            if (dy < y + 1 || dy > y + h - 2) continue;
            int sz = Math.max(2, Math.round((alpha < 0.3f ? 4 : 7) * s));
            Cosmetics.drawAuraSprite(kit.g, aura, i % aura.sprites, dx - sz / 2, dy - sz / 2, sz, (col & 0xFFFFFF) | a);
        }
    }

    // ------------------------------------------------------------------------------------------ MiniMe

    private static String slotPos() {
        return MiniMeCore.POSITIONS[slot][0];
    }

    private static String placeText() {
        String p = slotPos();
        if (p.equals("head")) return "Sitzt auf deinem Kopf";
        if (p.equals("arm")) return "Hängt an deiner linken Hand";
        if (p.equals("leg")) return "Hält sich an deinem rechten Bein fest";
        return "Sitzt auf deiner linken Schulter";
    }

    private void applyMini() {
        String name = mini.text.trim();
        if (!name.matches(MiniMeCore.NAME)) {
            message = "Name: 3–16 Zeichen, nur Buchstaben, Zahlen und _";
            return;
        }
        MiniMeCore.select(slotPos(), name);
        message = null;
        mini.text = "";
        kit.focus(null);
    }

    private void drawMiniMe(float x, float y, float w, float h) {
        float lx = x + 32 * d, top = y + 16 * d;
        String name = MiniMeCore.selected(slotPos());
        MiniMeCore.Profile profile = MiniMeCore.profile(name);
        // Stelle
        kit.caption("Stelle – je Stelle ein eigener Skin", lx + 2 * d, top + 4 * d);
        String[] labels = new String[MiniMeCore.POSITIONS.length];
        String[] counts = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = MiniMeCore.POSITIONS[i][1];
            counts[i] = MiniMeCore.selected(MiniMeCore.POSITIONS[i][0]) != null ? "1" : null;
        }
        kit.seg("slot", lx, top + 22 * d, labels, null, counts, slot, i -> {
            slot = i;
            message = null;
            mini.text = "";
        });

        // Vorschau-Karte
        float cy = top + 92 * d, cw = 220 * d, ch = 250 * d;
        Draw.rect(kit.g, lx, cy, cw, ch, 16 * d, Tokens.SURFACE);
        Draw.ring(kit.g, lx, cy, cw, ch, 16 * d, 1, name != null ? Tokens.ACC_LINE : Tokens.LINE);
        if (name == null) {
            Icons.draw(kit.g, "mini", lx + cw / 2f - 24 * d, cy + 70 * d, 48 * d, Tokens.SUBTLE);
            kit.textCenter("Kein MiniMe", lx + cw / 2f, cy + ch - 34 * d, 15, Tokens.SEMIBOLD, Tokens.MUTED);
        } else {
            float hs = 112 * d;
            if (profile != null && profile.head != null) Draw.head(kit.g, profile.head, name, lx + (cw - hs) / 2f, cy + 34 * d, hs, 22 * d);
            else Icons.draw(kit.g, MiniMeCore.missing(name) ? "info" : "refresh", lx + cw / 2f - 20 * d, cy + 70 * d, 40 * d, Tokens.MUTED);
            kit.textCenter(Type.ellipsize(kit.g, profile != null ? profile.name : name, cw - 24 * d, 16 * d, Tokens.SEMIBOLD), lx + cw / 2f, cy + ch - 46 * d, 16,
                    Tokens.SEMIBOLD, Tokens.TEXT);
            kit.textCenter(MiniMeCore.POSITIONS[slot][1], lx + cw / 2f, cy + ch - 24 * d, 13, Tokens.MEDIUM, Tokens.MUTED);
        }

        // Eingabe
        float fx = lx + cw + 28 * d, fw = Math.min(420 * d, x + w - 32 * d - fx);
        kit.caption("Skin von Spieler", fx + 2 * d, cy + 4 * d);
        kit.field("mini", mini, fx, cy + 22 * d, fw, 46 * d, "Minecraft-Name …", "user");
        float bh = 38 * d, by = cy + 82 * d;
        float bw = kit.button("mini:ok", fx, by, bh, "Übernehmen", "check", Widgets.PRIMARY, mini.isEmpty() ? null : (Runnable) this::applyMini);
        kit.button("mini:rm", fx + bw + 8 * d, by, bh, "Entfernen", "trash", Widgets.GHOST, name == null ? null : () -> {
            MiniMeCore.select(slotPos(), null);
            message = null;
        });
        String status = message;
        if (status == null && name != null) status = MiniMeCore.missing(name) ? "Den Spieler „" + name + "“ gibt es nicht" : profile == null ? "Skin wird geladen …" : placeText();
        if (status == null) status = "Stelle „" + MiniMeCore.POSITIONS[slot][1] + "“: noch kein MiniMe – Namen eingeben";
        boolean bad = message != null || MiniMeCore.missing(name);
        kit.textMid(status, fx, by + bh + 24 * d, 14, Tokens.MEDIUM, bad ? Draw.mix(Tokens.BAD, Tokens.TEXT, 0.25f) : Tokens.MUTED, x + w - 32 * d - fx);

        // Schnellauswahl: Freunde
        List<Friends.Friend> list = new ArrayList<Friends.Friend>(Friends.friends());
        if (!list.isEmpty()) {
            float qy = by + bh + 56 * d;
            kit.caption("Freunde", fx + 2 * d, qy);
            int max = Math.max(1, (int) ((x + w - 32 * d - fx) / (46 * d)));
            for (int i = 0; i < Math.min(max, list.size()); i++) {
                final Friends.Friend f = list.get(i);
                float hx = fx + i * 46 * d, hy = qy + 18 * d;
                boolean hv = kit.hit("fr:" + f.uuid, hx, hy, 40 * d, 40 * d, () -> {
                    MiniMeCore.select(slotPos(), f.name);
                    message = null;
                });
                kit.head(f.head, f.name, hx, hy - (hv ? 2 * d : 0), 40 * d, 0, false);
                if (hv) kit.tooltip(f.name, hx + 20 * d, hy + 48 * d);
            }
        }
        kit.textMid(Friends.available() ? "Andere Mytic-Spieler sehen deinen MiniMe" : "Nur du siehst ihn (ohne Mytic Launcher)", lx, y + h - 24 * d, 13,
                Tokens.MEDIUM, Tokens.SUBTLE, 0);
    }

    // ------------------------------------------------------------------------------------------ Nametag

    private void drawNametag(float x, float y, float w, float h) {
        float lx = x + 32 * d, lw = w - 64 * d, top = y + 16 * d;
        // Vorschau wie im Spiel
        float ph = 130 * d;
        Draw.rect(kit.g, lx, top, lw, ph, 16 * d, 0x38000000);
        Draw.ring(kit.g, lx, top, lw, ph, 16 * d, 1, Tokens.LINE);
        String shown = CosmeticsCore.formatNametag(tag.text);
        float cx = lx + lw / 2f;
        if (shown != null) drawMcText(shown, cx, top + 24 * d, true);
        drawMcText(Cosmetics.playerName(), cx, top + 58 * d, true);
        kit.textCenter(shown == null ? "Noch kein Nametag – schreib unten etwas" : "So sehen dich andere Mytic-Spieler", cx, top + ph - 20 * d, 13, Tokens.MEDIUM,
                Tokens.SUBTLE);

        float fy = top + ph + 20 * d;
        // ohne freien Nametag: Kaufen-Zeile statt Eingabe; gekaufte Vorlagen lassen sich trotzdem wählen
        final boolean free = CosmeticsCore.owns("nametag", null);
        float py;
        if (!free) {
            float bh0 = 96 * d;
            Draw.rect(kit.g, lx, fy, lw, bh0, 16 * d, Tokens.SURFACE);
            Draw.ring(kit.g, lx, fy, lw, bh0, 16 * d, 1, Draw.alpha(GOLD, 0.45f));
            kit.text("Eigener Nametag", lx + 20 * d, fy + 20 * d, 16, Tokens.SEMIBOLD, Tokens.TEXT);
            kit.textMid("Freier Text mit allen Farben und Formaten – oder unten einzelne Vorlagen kaufen", lx + 20 * d, fy + 62 * d, 13.5f, Tokens.MEDIUM, Tokens.MUTED, lw - 240 * d);
            final Integer np = CoinsCore.price("nametag");
            String label = np == null ? "Preis wird geladen …" : "Kaufen · " + CoinsCore.fmt(np);
            float bw = kit.buttonWidth(label, "coin", 42 * d);
            kit.button("tag:buy", lx + lw - 20 * d - bw, fy + (bh0 - 42 * d) / 2f, bw, 42 * d, label, "coin", Widgets.PRIMARY,
                    np == null ? null : (Runnable) () -> openBuy("nametag", null, "Eigener Nametag"));
            py = fy + bh0 + 20 * d;
        } else {
        kit.field("tag", tag, lx, fy, lw, 46 * d, "Text eingeben, z. B. &b&lMytic &7| &dPro", "pencil");
        // Farben
        float sy = fy + 62 * d, s = 30 * d;
        kit.caption("Farbe", lx + 2 * d, sy);
        for (int i = 0; i < 16; i++) {
            final int idx = i;
            float sx = lx + i * (s + 8 * d);
            if (sx + s > lx + lw) break;
            boolean hv = kit.hit("sw:" + i, sx, sy + 16 * d, s, s, () -> addTag("&" + COLOR_CODES.charAt(idx)));
            Draw.rect(kit.g, sx, sy + 16 * d - (hv ? 2 * d : 0), s, s, 9 * d, COLORS[i]);
            Draw.ring(kit.g, sx, sy + 16 * d - (hv ? 2 * d : 0), s, s, 9 * d, 1, hv ? 0xCCFFFFFF : 0x33FFFFFF);
        }
        // Formate
        float oy = sy + 66 * d;
        kit.caption("Format", lx + 2 * d, oy);
        float bx = lx;
        for (int i = 0; i < FORMATS.length; i++) {
            final String code = FORMATS[i][1];
            int sc = Type.pixelScale(kit.g, 11 * d);
            float k = Draw.k(kit.g);
            float tw = font.width(FORMATS[i][0]) * sc / k, bw = Math.max(44 * d, tw + 24 * d), bh = 36 * d;
            boolean hv = kit.hit("fmt:" + i, bx, oy + 16 * d, bw, bh, () -> addTag("&" + code));
            Draw.glass(kit.g, bx, oy + 16 * d, bw, bh, 10 * d, hv ? Tokens.SURFACE_HOVER : Tokens.SURFACE, hv ? Tokens.LINE_STRONG : Tokens.LINE);
            Type.pixel(kit.g, FORMATS[i][0], bx + (bw - tw) / 2f, oy + 16 * d + (bh - 7f * sc / k) / 2f, sc, 0, Tokens.TEXT);
            bx += bw + 8 * d;
        }
        py = oy + 76 * d;
        }
        // Vorlagen (aus dem Cosmetics-Paket): setzen den Text ins Feld; ohne freien Nametag einzeln kaufbar
        java.util.List<String[]> presets = CosmeticsCore.nametagPresets();
        if (!presets.isEmpty()) {
            kit.caption("Vorlagen", lx + 2 * d, py);
            float px = lx, row = 0;
            for (int i = 0; i < presets.size(); i++) {
                final String raw = presets.get(i)[2], pid = presets.get(i)[0], pname = presets.get(i)[1];
                final boolean pLocked = !free && !CosmeticsCore.owns("nametag", pid);
                final Integer pPrice = CoinsCore.price("nametag:" + pid);
                String pLabel = pLocked && pPrice != null ? CoinsCore.fmt(pPrice) : null;
                float extra = pLabel != null ? kit.width(pLabel, 12.5f, Tokens.SEMIBOLD) + 30 * d : 0;
                String shownPreset = CosmeticsCore.formatNametag(raw);
                if (shownPreset == null) continue;
                int sc = Type.pixelScale(kit.g, 11 * d);
                float k = Draw.k(kit.g);
                float tw = font.width(shownPreset) * sc / k, bw = tw + 24 * d + extra, bh = 36 * d;
                if (px + bw > lx + lw && px > lx) {
                    // bis zu drei Zeilen (passt im Fenster noch über die Knöpfe)
                    if (row >= 2) break;
                    row++;
                    px = lx;
                    py += bh + 8 * d;
                }
                boolean on = raw.equals(tag.text);
                boolean hv = kit.hit("preset:" + i, px, py + 16 * d, bw, bh, () -> {
                    if (pLocked) {
                        openBuy("nametag", pid, "Vorlage „" + pname + "“");
                        return;
                    }
                    tag.text = raw;
                    kit.focus(null);
                    if (!free) CosmeticsCore.selectNametag(raw);
                });
                Draw.glass(kit.g, px, py + 16 * d, bw, bh, 10 * d, on ? 0x299B5CFF : hv ? Tokens.SURFACE_HOVER : Tokens.SURFACE,
                        on ? Tokens.ACC_LINE : hv ? Tokens.LINE_STRONG : Tokens.LINE);
                Type.pixel(kit.g, shownPreset, px + 12 * d, py + 16 * d + (bh - 7f * sc / k) / 2f, sc, 0, pLocked ? 0x99FFFFFF : 0xFFFFFFFF);
                if (pLabel != null) {
                    float qx = px + 12 * d + tw + 10 * d;
                    Icons.draw(kit.g, "coin", qx, py + 16 * d + bh / 2f - 7 * d, 14 * d, GOLD);
                    kit.textMid(pLabel, qx + 18 * d, py + 16 * d + bh / 2f, 12.5f, Tokens.SEMIBOLD, Draw.mix(GOLD, Tokens.TEXT, 0.35f), 0);
                }
                px += bw + 8 * d;
            }
            py += 66 * d;
        }
        // Übernehmen / Entfernen
        float ay = py, bh = 38 * d;
        float w0 = kit.button("tag:ok", lx, ay, bh, "Übernehmen", "check", Widgets.PRIMARY, free ? (Runnable) () -> {
            CosmeticsCore.selectNametag(tag.text);
            kit.focus(null);
        } : null);
        float w1 = kit.button("tag:rm", lx + w0 + 8 * d, ay, bh, "Entfernen", "trash", Widgets.GHOST, () -> {
            tag.text = "";
            CosmeticsCore.selectNametag(null);
        });
        boolean saved = tag.text.equals(MyticClient.config().nametag == null ? "" : MyticClient.config().nametag);
        float tx = lx + w0 + w1 + 24 * d;
        if (saved) kit.badge("Gespeichert", "check", tx, ay + 7 * d, Tokens.OK);
        else kit.badge("Noch nicht übernommen", null, tx, ay + 7 * d, Tokens.WARN);
        // Eigener Name in F5
        float ty = ay + 62 * d;
        kit.textMid("Eigenen Nametag in F5 zeigen", lx, ty + 13 * d, 15, Tokens.SEMIBOLD, Tokens.TEXT, 0);
        kit.toggle("self", lx + lw - 46 * d, ty, MyticClient.config().nametagSelf, () -> {
            MyticClient.config().nametagSelf = !MyticClient.config().nametagSelf;
            MyticClient.config().save();
        });
    }

    private void addTag(String code) {
        if (tag.text.length() + code.length() <= CosmeticsCore.NAMETAG_RAW) tag.text += code;
        kit.focus(tag);
    }

    // ------------------------------------------------------------------------------------------ Admin

    private void drawAdmin(float x, float y, float w, float h) {
        float lx = x + 32 * d, lw = w - 64 * d, top = y + 16 * d;
        float fw = Math.min(320 * d, lw * 0.45f);
        kit.field("admin", admin, lx, top, fw, 46 * d, "Minecraft-Name", "user");
        float bh = 46 * d, bx = lx + fw + 10 * d;
        bx += kit.button("adm:load", bx, top, bh, "Laden", "search", Widgets.SECONDARY, () -> {
            AdminCore.load(admin.text);
            CoinsCore.adminLoad(admin.text);
            scroll.reset();
        }) + 8 * d;
        kit.button("adm:save", bx, top, bh, "Speichern", "check", Widgets.PRIMARY, AdminCore.uuid != null && AdminCore.dirty ? (Runnable) AdminCore::save : null);
        String status = AdminCore.message;
        if (status == null && AdminCore.uuid != null) status = AdminCore.name + (AdminCore.dirty ? " – Änderungen noch nicht gespeichert" : " – " + AdminCore.ITEMS.size() + " Freischaltungen");
        if (status == null) status = "Name eingeben und laden, dann Häkchen setzen und speichern.";
        int sc = AdminCore.error ? Draw.mix(Tokens.BAD, Tokens.TEXT, 0.25f) : AdminCore.dirty ? Tokens.WARN : Tokens.MUTED;
        kit.textMid(status, lx, top + 70 * d, 14, Tokens.MEDIUM, sc, lw);
        if (AdminCore.uuid == null) return;

        // Coins des Spielers: Stand, Menge, Grund, Geben/Nehmen (wie /mytic coins give|take)
        float cy0 = top + 96 * d;
        boolean known = AdminCore.uuid.equals(CoinsCore.adminUuid);
        kit.caption("Coins", lx + 2 * d, cy0);
        String bal = known ? CoinsCore.fmt(CoinsCore.adminBalance) : "…";
        kit.badge(bal + " Coins", "coin", lx + 2 * d, cy0 + 26 * d, GOLD);
        float cfx = lx + 150 * d;
        kit.field("coinamt", coinAmount, cfx, cy0 + 18 * d, 110 * d, 40 * d, "Menge", "coin");
        kit.field("coinwhy", coinReason, cfx + 118 * d, cy0 + 18 * d, Math.max(120 * d, lw - (cfx - lx) - 118 * d - 230 * d), 40 * d, "Grund (sieht der Spieler)", "pencil");
        final int amount = parse(coinAmount.text);
        final String who = AdminCore.uuid;
        boolean can = amount > 0 && !CoinsCore.busy() && CoinsCore.supported();
        float gbw = kit.buttonWidth("Geben", "plus", 40 * d), tbw = kit.buttonWidth("Nehmen", null, 40 * d);
        kit.button("coin:take", lx + lw - tbw, cy0 + 18 * d, tbw, 40 * d, "Nehmen", null, Widgets.GHOST, can ? (Runnable) () -> CoinsCore.adminBook(who, -amount, coinReason.text, (ok, msg) -> {
            if (ok) Toasts.ok(msg);
            else Toasts.error(msg);
        }) : null);
        kit.button("coin:give", lx + lw - tbw - 8 * d - gbw, cy0 + 18 * d, gbw, 40 * d, "Geben", "plus", Widgets.PRIMARY, can ? (Runnable) () -> CoinsCore.adminBook(who, amount, coinReason.text, (ok, msg) -> {
            if (ok) {
                Toasts.ok(msg);
                coinAmount.text = "";
            } else {
                Toasts.error(msg);
            }
        }) : null);
        String last = CoinsCore.adminMessage;
        if (last == null && known && !CoinsCore.adminTx.isEmpty()) {
            CoinsCore.Tx t = CoinsCore.adminTx.get(0);
            last = "Zuletzt: " + (t.delta > 0 ? "+" : "") + CoinsCore.fmt(t.delta) + " · " + CoinsCore.describe(t);
        }
        if (last != null) kit.textMid(last, lx, cy0 + 76 * d, 13, Tokens.MEDIUM, CoinsCore.adminError ? Draw.mix(Tokens.BAD, Tokens.TEXT, 0.25f) : Tokens.MUTED, lw);

        // Häkchen-Felder in Zeilen (Kopfzeilen ohne Eintrag)
        List<String[]> chips = new ArrayList<String[]>();
        chips.add(new String[]{null, "Allgemein"});
        chips.add(new String[]{"*", "Alles"});
        chips.add(new String[]{"admin", "Admin"});
        chips.add(new String[]{"minime", "MiniMe"});
        chips.add(new String[]{"nametag", "Nametag"});
        String[][] kinds = {{"cape", "Umhänge"}, {"wings", "Flügel"}, {"shield", "Schilde"}, {"aura", "Auren"}, {"acc", ACC_TAB}};
        for (String[] k : kinds) {
            if (k[0].equals("shield") && !java.util.Arrays.asList(BASE_TABS).contains("Schilde")) continue;
            chips.add(new String[]{null, k[1]});
            chips.add(new String[]{k[0] + ":*", "Alle"});
            if (k[0].equals("cape")) for (CosmeticsCore.Cape c : CosmeticsCore.capes()) chips.add(new String[]{"cape:" + c.id, c.name});
            if (k[0].equals("wings")) for (String[] e : CosmeticsCore.wings()) chips.add(new String[]{"wings:" + e[0], e[1]});
            if (k[0].equals("shield")) for (String[] e : CosmeticsCore.shields()) chips.add(new String[]{"shield:" + e[0], e[1]});
            if (k[0].equals("aura")) for (CosmeticsCore.Aura a : CosmeticsCore.auras()) chips.add(new String[]{"aura:" + a.id, a.name});
            if (k[0].equals("acc")) for (CosmeticsCore.Acc a : CosmeticsCore.accessories()) chips.add(new String[]{"acc:" + a.id, a.name});
        }
        // Lage berechnen
        float ch = 34 * d, gap = 8 * d;
        float[][] pos = new float[chips.size()][3];
        float cx = 0, cy = 0;
        boolean first = true;
        for (int i = 0; i < chips.size(); i++) {
            String[] c = chips.get(i);
            if (c[0] == null) {
                cy += first ? 0 : ch + 22 * d;
                first = false;
                pos[i] = new float[]{0, cy, lw};
                cy += 24 * d;
                cx = 0;
                continue;
            }
            float cw = kit.width(c[1], 14, Tokens.SEMIBOLD) + 52 * d;
            if (cx + cw > lw) {
                cx = 0;
                cy += ch + gap;
            }
            pos[i] = new float[]{cx, cy, cw};
            cx += cw + gap;
        }
        float listTop = top + 196 * d, viewH = y + h - listTop - 8 * d;
        float off = kit.scroll(scroll, lx, listTop, lw, viewH, cy + ch + 12 * d);
        kit.clip(lx - 4 * d, listTop, lw + 8 * d, viewH);
        for (int i = 0; i < chips.size(); i++) {
            final String[] c = chips.get(i);
            float px = lx + pos[i][0], py = listTop + pos[i][1] - off;
            if (py + ch < listTop || py > listTop + viewH) continue;
            if (c[0] == null) {
                kit.caption(c[1], px + 2 * d, py + 4 * d);
                continue;
            }
            boolean on = AdminCore.has(c[0]);
            boolean hv = kit.hit("chip:" + c[0], px, py, pos[i][2], ch, () -> AdminCore.toggle(c[0]));
            Draw.rect(kit.g, px, py, pos[i][2], ch, ch / 2, on ? 0x299B5CFF : hv ? Tokens.SURFACE_HOVER : Tokens.SURFACE);
            Draw.ring(kit.g, px, py, pos[i][2], ch, ch / 2, 1, on ? Tokens.ACC_LINE : hv ? Tokens.LINE_STRONG : Tokens.LINE);
            float bs = 18 * d, bxx = px + 10 * d, byy = py + (ch - bs) / 2f;
            if (on) {
                Draw.accentRect(kit.g, bxx, byy, bs, bs, 6 * d, 1f);
                Icons.draw(kit.g, "check", bxx + 2 * d, byy + 2 * d, 14 * d, 0xFFFFFFFF);
            } else {
                Draw.ring(kit.g, bxx, byy, bs, bs, 6 * d, 1, Tokens.LINE_STRONG);
            }
            kit.textMid(c[1], px + 36 * d, py + ch / 2f, 14, Tokens.SEMIBOLD, on ? 0xFFFFFFFF : Tokens.TEXT, 0);
        }
        kit.unclip();
        kit.scrollbar("admin", scroll, lx + lw + 16 * d, listTop, viewH);
    }

    // ------------------------------------------------------------------------------------------ Shop

    /** Kauf-Dialog für ein Teil öffnen (kind: cape, wings, shield, aura, acc, minime, nametag; id null bei minime/nametag) */
    private void openBuy(String kind, String id, String name) {
        if (!Friends.available()) {
            Toasts.show("Kaufen geht nur über den Mytic Launcher", "lock", Toasts.INFO);
            return;
        }
        String item = id == null ? kind : kind + ":" + id;
        Integer price = CoinsCore.price(item);
        if (price == null) {
            Toasts.show(CoinsCore.supported() ? "Preise werden geladen – gleich noch einmal" : "Der Shop kommt mit dem nächsten Server-Update", "coin", Toasts.INFO);
            CosmeticsCore.refreshOwned();
            return;
        }
        Buy b = new Buy();
        b.kind = kind;
        b.id = id;
        b.item = item;
        b.name = name;
        b.price = price;
        buying = b;
        kit.focus(null);
    }

    private void openBuySet(CoinsCore.SetInfo set) {
        if (!Friends.available()) return;
        Buy b = new Buy();
        b.kind = "set";
        b.set = set;
        b.name = set.name;
        b.price = CoinsCore.bundlePrice(set);
        buying = b;
        kit.focus(null);
    }

    /** Gekauftes gleich ausrüsten */
    private void equip(String kind, String id) {
        if (kind.equals("cape")) CosmeticsCore.select(id);
        else if (kind.equals("wings")) CosmeticsCore.selectWings(id);
        else if (kind.equals("shield")) CosmeticsCore.selectShield(id);
        else if (kind.equals("aura")) CosmeticsCore.selectAura(id);
        else if (kind.equals("acc")) {
            CosmeticsCore.Acc a = CosmeticsCore.acc(id);
            if (a != null) CosmeticsCore.selectAcc(a.slot, id);
        } else if (kind.equals("nametag") && id != null) {
            for (String[] p : CosmeticsCore.nametagPresets()) {
                if (p[0].equals(id)) {
                    tag.text = p[2];
                    CosmeticsCore.selectNametag(p[2]);
                }
            }
        }
    }

    private boolean anyPresetOwned() {
        for (String[] p : CosmeticsCore.nametagPresets()) if (CosmeticsCore.owns("nametag", p[0])) return true;
        return false;
    }

    /** Gesperrter Reiter (MiniMe): Erklärung, Preis und „Kaufen“ */
    private void buyCard(float x, float y, float w, float h, String icon, final String name, final String item, String text) {
        float cx = x + w / 2f, cy = y + h / 2f - 70 * d;
        Draw.rect(kit.g, cx - 32 * d, cy - 32 * d, 64 * d, 64 * d, 18 * d, Tokens.SURFACE);
        Icons.draw(kit.g, icon, cx - 14 * d, cy - 14 * d, 28 * d, Tokens.MUTED);
        kit.textCenter(name + " ist noch nicht gekauft", cx, cy + 60 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.textCenter(text, cx, cy + 88 * d, 14.5f, Tokens.MEDIUM, Tokens.MUTED);
        final Integer price = CoinsCore.price(item);
        if (!Friends.available()) {
            kit.textCenter("Kaufen geht nur über den Mytic Launcher", cx, cy + 116 * d, 14, Tokens.MEDIUM, Tokens.SUBTLE);
            return;
        }
        String label = price == null ? "Preis wird geladen …" : "Kaufen · " + CoinsCore.fmt(price) + " Coins";
        float bh = 44 * d, bw = kit.buttonWidth(label, "coin", bh);
        kit.button("buycard:" + item, cx - bw / 2f, cy + 120 * d, bw, bh, label, "coin", Widgets.PRIMARY,
                price == null ? null : (Runnable) () -> openBuy(item, null, name));
    }

    /** Kauf-Dialog mit Vorschau (an der Figur und im Dialog), Kontostand vorher/nachher und Rückfrage */
    private void drawBuy() {
        final Buy b = buying;
        boolean isSet = b.set != null;
        // Vorschau an der eigenen Figur, solange der Dialog offen ist
        if (!isSet) {
            if (b.kind.equals("nametag") && b.id != null) {
                for (String[] p : CosmeticsCore.nametagPresets()) if (p[0].equals(b.id)) CoinsCore.preview("nametag", p[2]);
            } else if (b.id != null) {
                CoinsCore.preview(b.kind, b.id);
            }
        }
        int bal = CoinsCore.balance();
        boolean enough = bal >= b.price;
        float w = 640 * d, h = (isSet ? 360 : 330) * d, x = (vw - w) / 2f, y = (vh - h) / 2f;
        kit.modal(x, y, w, h, () -> {
            if (!b.running) buying = null;
        });
        // Bild links
        float ax = x + 28 * d, ay = y + 28 * d, aw = 190 * d, ah = 170 * d;
        Draw.rect(kit.g, ax, ay, aw, ah, 14 * d, 0x38000000);
        Draw.ring(kit.g, ax, ay, aw, ah, 14 * d, 1, Tokens.LINE);
        if (isSet) {
            Icons.draw(kit.g, "gift", ax + aw / 2f - 36 * d, ay + ah / 2f - 36 * d, 72 * d, GOLD);
        } else if (b.kind.equals("minime")) {
            Icons.draw(kit.g, "mini", ax + aw / 2f - 36 * d, ay + ah / 2f - 36 * d, 72 * d, Tokens.MUTED);
        } else if (b.kind.equals("nametag")) {
            String raw = null;
            if (b.id != null) for (String[] p : CosmeticsCore.nametagPresets()) if (p[0].equals(b.id)) raw = p[2];
            String shown = CosmeticsCore.formatNametag(raw);
            if (shown != null) drawMcText(shown, ax + aw / 2f, ay + ah / 2f - 30 * d, true);
            else Icons.draw(kit.g, "tag", ax + aw / 2f - 36 * d, ay + ah / 2f - 36 * d, 72 * d, Tokens.MUTED);
            drawMcText(Cosmetics.playerName(), ax + aw / 2f, ay + ah / 2f + 4 * d, true);
        } else {
            drawArt(b.kind, b.id, ax + 6 * d, ay + 6 * d, aw - 12 * d, ah - 12 * d, true);
        }
        // Text rechts
        float tx = ax + aw + 26 * d, tw = x + w - 28 * d - tx;
        kit.text(isSet ? "Set kaufen?" : "Kaufen?", tx, y + 30 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.textMid(Type.ellipsize(kit.g, "„" + b.name + "“", tw, 17 * d, Tokens.SEMIBOLD), tx, y + 76 * d, 17, Tokens.SEMIBOLD, Tokens.TEXT, tw);
        float ly = y + 104 * d;
        if (isSet) {
            int missing = CoinsCore.missing(b.set), sum = 0;
            for (String item : b.set.items) if (!CoinsCore.ownsItem(item)) sum += CoinsCore.price(item) == null ? 0 : CoinsCore.price(item);
            kit.textMid(missing + " von " + b.set.items.size() + " Teilen fehlen dir – einzeln " + CoinsCore.fmt(sum) + " Coins", tx, ly, 14, Tokens.MEDIUM, Tokens.MUTED, tw);
            ly += 26 * d;
            kit.badge("−" + CoinsCore.discount() + " % im Set", "gift", tx, ly - 12 * d, Tokens.OK);
            ly += 26 * d;
        } else {
            kit.textMid(kindName(b.kind), tx, ly, 14, Tokens.MEDIUM, Tokens.MUTED, tw);
            ly += 26 * d;
            if (!b.kind.equals("minime") && !b.kind.equals("nametag")) {
                kit.textMid(mc.thePlayer != null ? "Vorschau links an deiner Figur" : "Vorschau im Bild", tx, ly, 13, Tokens.MEDIUM, Tokens.SUBTLE, tw);
                ly += 26 * d;
            }
        }
        // Preis und Kontostand
        Icons.draw(kit.g, "coin", tx, ly + 2 * d, 22 * d, GOLD);
        kit.textMid(CoinsCore.fmt(b.price) + " Coins", tx + 30 * d, ly + 13 * d, 19, Tokens.SEMIBOLD, Tokens.TEXT, 0);
        ly += 36 * d;
        String after = bal < 0 ? "Kontostand wird geladen …" : "Kontostand: " + CoinsCore.fmt(bal) + (enough ? " → " + CoinsCore.fmt(bal - b.price) + " Coins" : " Coins");
        kit.textMid(after, tx, ly, 14, Tokens.MEDIUM, Tokens.MUTED, tw);
        ly += 26 * d;
        String err = b.error;
        if (err != null) {
            kit.paragraph(err, tx, ly - 8 * d, tw, 13.5f, Tokens.MEDIUM, Draw.mix(Tokens.BAD, Tokens.TEXT, 0.25f), 19, 2);
        } else if (bal >= 0 && !enough) {
            kit.paragraph("Dir fehlen " + CoinsCore.fmt(b.price - bal) + " Coins. Coins gibt es täglich beim Spielen und als Geschenk von Freunden.", tx, ly - 8 * d, tw, 13.5f,
                    Tokens.MEDIUM, Draw.mix(Tokens.WARN, Tokens.TEXT, 0.2f), 19, 2);
        }
        // Knöpfe
        float bh = 44 * d, by = y + h - 28 * d - bh;
        String label = b.running ? "Wird gekauft …" : isSet ? "Set kaufen" : "Kaufen";
        float ew = kit.buttonWidth(label, "coin", bh);
        kit.button("buy:ok", x + w - 28 * d - ew, by, ew, bh, label, "coin", Widgets.PRIMARY, !enough || b.running || CoinsCore.busy() ? null : (Runnable) () -> {
            b.running = true;
            b.error = null;
            CoinsCore.buy(isSetItem(b) ? null : b.item, isSetItem(b) ? b.set.id : null, b.price, (ok, msg) -> {
                b.running = false;
                if (ok) {
                    Toasts.ok(msg);
                    if (!isSetItem(b) && b.id != null) {
                        equipId = b.id;
                        equipKind = b.kind;
                    }
                    closeBuy = true;
                } else {
                    b.error = msg;
                    Toasts.error(msg);
                }
            });
        });
        float cw = kit.buttonWidth("Abbrechen", null, bh);
        kit.button("buy:no", x + w - 28 * d - ew - 10 * d - cw, by, cw, bh, "Abbrechen", null, Widgets.GHOST, b.running ? null : (Runnable) () -> buying = null);
    }

    private static boolean isSetItem(Buy b) {
        return b.set != null;
    }

    private static String kindName(String kind) {
        if (kind.equals("cape")) return "Umhang";
        if (kind.equals("wings")) return "Flügel";
        if (kind.equals("shield")) return "Schild";
        if (kind.equals("aura")) return "Aura";
        if (kind.equals("acc")) return "Accessoire (Kopf & Hals)";
        if (kind.equals("minime")) return "MiniMe";
        if (kind.equals("nametag")) return "Nametag";
        return kind;
    }

    /** Reiter „Sets“: Karten mit Teilen, Preis einzeln/Set und „Set kaufen“ */
    private void drawSets(float x, float y, float w, float h) {
        List<CoinsCore.SetInfo> list = CoinsCore.sets();
        if (!Friends.available()) {
            empty(x, y, w, h, "gift", "Sets gibt es nur über den Mytic Launcher", null);
            return;
        }
        if (list.isEmpty()) {
            empty(x, y, w, h, "gift", CoinsCore.supported() ? "Sets werden geladen …" : "Der Shop kommt mit dem nächsten Server-Update", null);
            return;
        }
        float gx = x + 32 * d, gw = w - 64 * d, top = y + 8 * d, viewH = h - 8 * d, ch = 228 * d, gap = 14 * d;
        float off = kit.scroll(setScroll, gx, top, gw, viewH, list.size() * (ch + gap) + 12 * d);
        kit.clip(gx - 4 * d, top, gw + 8 * d, viewH);
        for (int i = 0; i < list.size(); i++) {
            final CoinsCore.SetInfo set = list.get(i);
            float cy = top + 4 * d + i * (ch + gap) - off;
            if (cy + ch < top || cy > top + viewH) continue;
            Draw.rect(kit.g, gx, cy, gw, ch, 16 * d, Tokens.SURFACE);
            Draw.ring(kit.g, gx, cy, gw, ch, 16 * d, 1, Tokens.LINE);
            int missing = CoinsCore.missing(set), price = CoinsCore.bundlePrice(set);
            kit.text(set.name, gx + 20 * d, cy + 18 * d, 18, Tokens.SEMIBOLD, Tokens.TEXT);
            float bx = gx + 20 * d + kit.width(set.name, 18, Tokens.SEMIBOLD) + 12 * d;
            bx += kit.badge("Event · immer kaufbar", "star", bx, cy + 18 * d, GOLD) + 6 * d;
            kit.badge((set.items.size() - missing) + " / " + set.items.size() + " Teile", "check", bx, cy + 18 * d, missing == 0 ? Tokens.OK : Tokens.MUTED);
            // Vorschauen der Teile in einer Reihe
            float px = gx + 20 * d, py = cy + 56 * d, pw = 86 * d, ph = 86 * d;
            int fit = Math.max(1, (int) ((gw - 40 * d + 8 * d) / (pw + 8 * d)));
            int shown = 0;
            for (String item : set.items) {
                if (shown >= fit) break;
                int c = item.indexOf(':');
                if (c < 0) continue;
                String kind = item.substring(0, c), id = item.substring(c + 1);
                if (kind.equals("shield") && !java.util.Arrays.asList(BASE_TABS).contains("Schilde")) continue;
                float qx = px + shown * (pw + 8 * d);
                Draw.rect(kit.g, qx, py, pw, ph, 11 * d, 0x38000000);
                drawArt(kind, id, qx + 3 * d, py + 3 * d, pw - 6 * d, ph - 6 * d, false);
                if (CoinsCore.ownsItem(item)) {
                    float s = 18 * d;
                    Draw.accentRect(kit.g, qx + pw - s - 5 * d, py + 5 * d, s, s, s / 2, 1f);
                    Icons.draw(kit.g, "check", qx + pw - s - 2 * d, py + 8 * d, 12 * d, 0xFFFFFFFF);
                }
                shown++;
            }
            if (set.items.size() > shown) kit.textMid("+" + (set.items.size() - shown) + " weitere", px, py + ph + 18 * d, 12.5f, Tokens.MEDIUM, Tokens.SUBTLE, 0);
            // Preis und Knopf
            float by = cy + ch - 20 * d - 42 * d, right = gx + gw - 20 * d;
            if (missing == 0) {
                kit.badge("Komplett", "check", right - kit.width("Komplett", 12.5f, Tokens.SEMIBOLD) - 36 * d, by + 9 * d, Tokens.OK);
                continue;
            }
            String label = "Set kaufen · " + CoinsCore.fmt(price);
            float bw = kit.buttonWidth(label, "coin", 42 * d);
            kit.button("set:" + set.id, right - bw, by, bw, 42 * d, label, "coin", Widgets.PRIMARY, () -> openBuySet(set));
            int sum = 0;
            for (String item : set.items) if (!CoinsCore.ownsItem(item) && CoinsCore.price(item) != null) sum += CoinsCore.price(item);
            String was = "einzeln " + CoinsCore.fmt(sum);
            float ww = kit.width(was, 14, Tokens.MEDIUM);
            float wx = right - bw - 16 * d - ww;
            kit.textMid(was, wx, by + 21 * d, 14, Tokens.MEDIUM, Tokens.SUBTLE, 0);
            Draw.rect(kit.g, wx - 2 * d, by + 21 * d, ww + 4 * d, 1.5f * d, 0, Tokens.SUBTLE);
            kit.badge("−" + CoinsCore.discount() + " %", null, wx - 12 * d - kit.width("−" + CoinsCore.discount() + " %", 12.5f, Tokens.SEMIBOLD) - 18 * d, by + 9 * d, Tokens.OK);
        }
        kit.unclip();
        kit.scrollbar("sets", setScroll, gx + gw + 16 * d, top, viewH);
    }

    /** Reiter „Coins“: Kontostand, Tagesbonus mit Serie, Verschenken an Freunde, Verlauf */
    private void drawCoins(float x, float y, float w, float h) {
        if (!CoinsCore.supported()) {
            empty(x, y, w, h, "coin", "Coins kommen mit dem nächsten Server-Update", null);
            return;
        }
        if (!CoinsCore.infoLoaded) CoinsCore.loadInfo();
        float lx = x + 32 * d, top = y + 12 * d, colW = (w - 64 * d - 24 * d) * 0.5f;
        // Kontostand
        float ch = 108 * d;
        Draw.rect(kit.g, lx, top, colW, ch, 16 * d, Tokens.SURFACE);
        Draw.ring(kit.g, lx, top, colW, ch, 16 * d, 1, Draw.alpha(GOLD, 0.45f));
        Icons.draw(kit.g, "coin", lx + 20 * d, top + 26 * d, 52 * d, GOLD);
        kit.caption("Kontostand", lx + 88 * d, top + 26 * d);
        kit.text((CoinsCore.balance() < 0 ? "…" : CoinsCore.fmt(CoinsCore.balance())) + " Coins", lx + 88 * d, top + 48 * d, 26, Tokens.SEMIBOLD, Tokens.TEXT);
        // Tagesbonus
        float dy = top + ch + 16 * d, dh = 132 * d;
        Draw.rect(kit.g, lx, dy, colW, dh, 16 * d, Tokens.SURFACE);
        Draw.ring(kit.g, lx, dy, colW, dh, 16 * d, 1, Tokens.LINE);
        kit.caption("Tagesbonus", lx + 20 * d, dy + 18 * d);
        String daily = CoinsCore.dailyClaimed ? "Heute abgeholt: +" + CoinsCore.dailyAmount + " Coins (Tag " + CoinsCore.dailyStreak + ")"
                : "Heute: +" + CoinsCore.dailyAmount + " Coins – kommt automatisch beim Spielen";
        kit.textMid(daily, lx + 20 * d, dy + 50 * d, 14.5f, Tokens.SEMIBOLD, Tokens.TEXT, colW - 40 * d);
        // Serie: 7 Punkte
        int streak = Math.min(7, CoinsCore.dailyStreak);
        float sx = lx + 20 * d, sy = dy + 72 * d, s = 26 * d;
        for (int i = 0; i < 7; i++) {
            boolean on = i < streak;
            float qx = sx + i * (s + 8 * d);
            if (on) Draw.accentRect(kit.g, qx, sy, s, s, s / 2, 1f);
            else Draw.ring(kit.g, qx, sy, s, s, s / 2, 1, Tokens.LINE_STRONG);
            kit.textCenter(String.valueOf(i + 1), qx + s / 2f, sy + s / 2f, 11.5f, Tokens.SEMIBOLD, on ? 0xFFFFFFFF : Tokens.SUBTLE);
        }
        kit.textMid("+5 je Tag in Folge, bis 40 – ein verpasster Tag beginnt wieder bei 10", sx, sy + s + 16 * d, 12.5f, Tokens.MEDIUM, Tokens.SUBTLE, colW - 40 * d);
        // Verschenken
        float gy = dy + dh + 16 * d, gh = y + h - 12 * d - gy;
        Draw.rect(kit.g, lx, gy, colW, gh, 16 * d, Tokens.SURFACE);
        Draw.ring(kit.g, lx, gy, colW, gh, 16 * d, 1, Tokens.LINE);
        kit.caption("An Freunde verschenken", lx + 20 * d, gy + 18 * d);
        List<Friends.Friend> friends = new ArrayList<Friends.Friend>(Friends.friends());
        if (friends.isEmpty()) {
            kit.textMid("Füge Freunde hinzu, um ihnen Coins zu schenken.", lx + 20 * d, gy + 54 * d, 14, Tokens.MEDIUM, Tokens.MUTED, colW - 40 * d);
        } else {
            int max = Math.max(1, (int) ((colW - 40 * d) / (44 * d)));
            for (int i = 0; i < Math.min(max, friends.size()); i++) {
                final Friends.Friend f = friends.get(i);
                float hx = lx + 20 * d + i * 44 * d, hy = gy + 40 * d;
                boolean sel = f.name != null && f.name.equals(giftTo);
                boolean hv = kit.hit("gf:" + f.uuid, hx, hy, 38 * d, 38 * d, () -> giftTo = f.name);
                if (sel) Draw.ring(kit.g, hx - 3 * d, hy - 3 * d, 44 * d, 44 * d, 12 * d, 2, Tokens.ACC);
                kit.head(f.head, f.name, hx, hy - (hv ? 2 * d : 0), 38 * d, 0, false);
                if (hv) kit.tooltip(f.name, hx + 19 * d, hy + 46 * d);
            }
            float fy = gy + 94 * d;
            kit.field("giftamt", giftAmount, lx + 20 * d, fy, 120 * d, 42 * d, "Menge", "coin");
            String label = giftTo == null ? "Freund wählen" : "An " + giftTo + " schenken";
            float bw = Math.min(colW - 40 * d - 132 * d, kit.buttonWidth(label, "gift", 42 * d));
            final int amount = parse(giftAmount.text);
            boolean ok = giftTo != null && amount >= CoinsCore.GIFT_MIN && amount <= CoinsCore.balance() && !CoinsCore.busy();
            kit.button("gift:go", lx + 152 * d, fy, bw, 42 * d, label, "gift", Widgets.PRIMARY, ok ? (Runnable) () -> giftConfirm = true : null);
            kit.textMid("Heute verschenkt: " + CoinsCore.fmt(CoinsCore.giftSent) + " / " + CoinsCore.fmt(CoinsCore.giftLimit) + " · mindestens " + CoinsCore.GIFT_MIN,
                    lx + 20 * d, fy + 62 * d, 12.5f, Tokens.MEDIUM, Tokens.SUBTLE, colW - 40 * d);
        }
        // Verlauf
        float rx = lx + colW + 24 * d, rw = colW, ry = top, rh = y + h - 12 * d - ry;
        Draw.rect(kit.g, rx, ry, rw, rh, 16 * d, Tokens.SURFACE);
        Draw.ring(kit.g, rx, ry, rw, rh, 16 * d, 1, Tokens.LINE);
        kit.caption("Verlauf", rx + 20 * d, ry + 18 * d);
        kit.iconButton("txrefresh", rx + rw - 20 * d - 30 * d, ry + 10 * d, 30 * d, "refresh", false, () -> {
            CoinsCore.infoLoaded = false;
            CoinsCore.loadInfo();
        });
        List<CoinsCore.Tx> tx = CoinsCore.history;
        float listTop = ry + 48 * d, viewH = rh - 56 * d, rowH = 44 * d;
        if (tx.isEmpty()) {
            kit.textMid(CoinsCore.infoLoaded ? "Noch keine Buchungen" : "Lade …", rx + 20 * d, listTop + 16 * d, 14, Tokens.MEDIUM, Tokens.MUTED, rw - 40 * d);
            return;
        }
        float off = kit.scroll(txScroll, rx, listTop, rw, viewH, tx.size() * rowH);
        kit.clip(rx, listTop, rw, viewH);
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY);
        for (int i = 0; i < tx.size(); i++) {
            CoinsCore.Tx t = tx.get(i);
            float ty = listTop + i * rowH - off;
            if (ty + rowH < listTop || ty > listTop + viewH) continue;
            if (i > 0) kit.hline(rx + 20 * d, ty, rw - 40 * d);
            String delta = (t.delta > 0 ? "+" : "") + CoinsCore.fmt(t.delta);
            float dw = kit.width(delta, 15, Tokens.SEMIBOLD);
            kit.textMid(CoinsCore.describe(t), rx + 20 * d, ty + 15 * d, 14, Tokens.SEMIBOLD, Tokens.TEXT, rw - 60 * d - dw);
            kit.textMid(df.format(new java.util.Date(t.at)) + " · Stand " + CoinsCore.fmt(t.balance), rx + 20 * d, ty + 32 * d, 12, Tokens.MEDIUM, Tokens.SUBTLE, rw - 60 * d - dw);
            kit.textRight(delta, rx + rw - 20 * d, ty + 22 * d, 15, Tokens.SEMIBOLD, t.delta >= 0 ? Tokens.OK : Draw.mix(Tokens.BAD, Tokens.TEXT, 0.2f));
        }
        kit.unclip();
        kit.scrollbar("tx", txScroll, rx + rw - 10 * d, listTop, viewH);
    }

    private static int parse(String text) {
        try {
            return text == null || text.isEmpty() ? 0 : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Rückfrage „Coins schenken?“ */
    private void drawGiftConfirm() {
        final String to = giftTo;
        final int amount = parse(giftAmount.text);
        float w = 520 * d, h = 236 * d, x = (vw - w) / 2f, y = (vh - h) / 2f;
        kit.modal(x, y, w, h, () -> giftConfirm = false);
        kit.text(CoinsCore.fmt(amount) + " Coins schenken?", x + 28 * d, y + 30 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.paragraph(to + " bekommt " + CoinsCore.fmt(amount) + " Coins von dir und sieht einen Hinweis im Spiel. Das lässt sich nicht rückgängig machen.",
                x + 28 * d, y + 72 * d, w - 56 * d, 15, Tokens.MEDIUM, Tokens.MUTED, 22, 3);
        float bh = 44 * d, by = y + h - 28 * d - bh;
        float ew = kit.buttonWidth("Schenken", "gift", bh);
        kit.button("gift:ok", x + w - 28 * d - ew, by, ew, bh, "Schenken", "gift", Widgets.PRIMARY, CoinsCore.busy() ? null : (Runnable) () -> {
            giftConfirm = false;
            CoinsCore.gift(to, amount, (ok, msg) -> {
                if (ok) {
                    Toasts.ok(msg);
                    giftAmount.text = "";
                } else {
                    Toasts.error(msg);
                }
            });
        });
        float cw = kit.buttonWidth("Abbrechen", null, bh);
        kit.button("gift:no", x + w - 28 * d - ew - 10 * d - cw, by, cw, bh, "Abbrechen", null, Widgets.GHOST, () -> giftConfirm = false);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        dragLast = Float.NaN;
        return super.click(event, doubleClick);
    }

    @Override
    protected boolean release(Input.Click event) {
        dragLast = Float.NaN;
        return super.release(event);
    }

    @Override
    protected boolean onChar(Input.Typed event) {
        if (event.codepointAsString().indexOf('§') >= 0) return true; // nur &-Codes, keine echten Formatzeichen
        return super.onChar(event);
    }

    @Override
    protected boolean typedFree(String s) {
        // im MiniMe-Reiter einfach lostippen (die Taste aus der Übersicht kurz nach dem Öffnen nicht mitzählen)
        if (System.currentTimeMillis() - openedAt < 250 || buying != null || giftConfirm || !isTab("MiniMe") || !CosmeticsCore.owns("minime", null)) return false;
        if (s.length() != 1 || NAME_CHARS.indexOf(s.charAt(0)) < 0) return false;
        kit.focus(mini);
        return kit.typed(s);
    }

    @Override
    protected void back() {
        if (buying != null) {
            if (!buying.running) buying = null;
            return;
        }
        if (giftConfirm) {
            giftConfirm = false;
            return;
        }
        Compat.setScreen(inWorld() ? new HubScreen() : null);
    }
}
