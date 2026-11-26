package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.cosmetics.AdminCore;
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
 */
public class CosmeticsScreen extends DesignScreen {
    private static final float W = 1600, H = 880, STAGE = 620, HEAD = 88, SUB = 72, FOOT = 92;
    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    // 1.8.9 hat keine Schilde
    private static final String[] BASE_TABS = {"Umhänge", "MiniMe", "Flügel", "Auren", "Kopf & Hals", "Nametag"};
    private static final String ACC_TAB = "Kopf & Hals";
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
    private final Kit.Scroll scroll = new Kit.Scroll();
    private String message;
    /** Drehung der Figur (Grad, 0 = von vorn) */
    private float yaw = -18, yawTarget = -18, dragLast = Float.NaN;

    public CosmeticsScreen() {
        super("cos", "Kosmetik");
        CosmeticsCore.refreshOwned();
        if (CosmeticsCore.isAdmin()) {
            TABS = java.util.Arrays.copyOf(BASE_TABS, BASE_TABS.length + 1);
            TABS[BASE_TABS.length] = "Admin";
        } else {
            TABS = BASE_TABS;
        }
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
            kit.focus(null);
        };
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
            sub = owned + " von " + (list.size() - 1) + " freigeschaltet";
        } else if (isTab("MiniMe")) {
            sub = "Ein kleiner Begleiter mit dem Skin eines beliebigen Spielers";
        } else if (isTab("Nametag")) {
            sub = "Text über deinem Namen, mit Farben und Formaten";
        } else {
            sub = "Cosmetics für Spieler freischalten";
        }
        kit.text(title, x + 32 * d, y + 24 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.text(sub, x + 32 * d, y + 54 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        float right = x + w - 32 * d - 40 * d;
        kit.iconButton("close", right, y + 24 * d, 40 * d, "x", true, () -> Compat.setScreen(null));
        if (gridTab()) kit.field("search", search, right - 12 * d - 280 * d, y + 24 * d, 280 * d, 40 * d, "Suchen …", "search");
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
        kit.seg("tabs", x + 32 * d, y + (SUB * d - 44 * d) / 2f, TABS, icons, null, tab, i -> {
            tab = i;
            kit.focus(null);
            scroll.reset();
            message = null;
        });
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
        } else if (isTab("MiniMe") && !CosmeticsCore.owns("minime", null)) {
            empty(x, y, w, h, "lock", "MiniMe ist für dich noch nicht freigeschaltet", "Ein Admin kann es dir freischalten.");
        } else if (isTab("Nametag") && !CosmeticsCore.owns("nametag", null)) {
            empty(x, y, w, h, "lock", "Der Nametag ist für dich noch nicht freigeschaltet", "Ein Admin kann es dir freischalten.");
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
        boolean hv = kit.hit(key, x, y, w, h, () -> {
            if (locked) Toasts.show("Gesperrt – ein Admin kann es freischalten", "lock", Toasts.INFO);
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
        drawArt(id, ax, ay, aw, ah, animated);
        if (locked) Draw.rect(kit.g, ax, ay, aw, ah, 11 * d, 0x8C0B0912);
        if (id != null) Draw.rect(kit.g, ax + 8 * d, ay + ah - 3 * d, aw - 16 * d, 3 * d, 1.5f * d, stripe);
        kit.textMid(e[1], x + 14 * d, yy + 12 * d + ah + 22 * d, 15, Tokens.SEMIBOLD, locked ? Tokens.MUTED : Tokens.TEXT, w - 28 * d);
        String state = eq ? "Ausgerüstet" : locked ? "Gesperrt" : id == null ? "Ablegen" : "Verfügbar";
        int sc = eq ? 0xFFD9C2FF : locked ? Tokens.SUBTLE : Tokens.MUTED;
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
    private void drawArt(String id, float x, float y, float w, float h, boolean animated) {
        GuiGraphics g = kit.g;
        float cx = x + w / 2f;
        if (id == null) {
            Icons.draw(g, "x", cx - 18 * d, y + h / 2f - 18 * d, 36 * d, Tokens.SUBTLE);
            return;
        }
        if (isTab("Umhänge")) {
            CosmeticsCore.Cape cape = CosmeticsCore.byId(id);
            int ph = Math.round(h - 24 * d), pw = Math.round(ph * 10f / 16f);
            if (cape == null || !Cosmetics.drawPreview(g, cape, Math.round(cx - pw / 2f), Math.round(y + 12 * d), pw, ph, animated)) {
                Icons.draw(g, "cape", cx - 16 * d, y + h / 2f - 16 * d, 32 * d, Tokens.MUTED);
            }
        } else if (isTab("Schilde")) {
            int ph = Math.round(h - 24 * d), pw = Math.round(ph * 12f / 22f);
            Cosmetics.drawShieldPreview(g, id, Math.round(cx - pw / 2f), Math.round(y + 12 * d), pw, ph, animated);
        } else if (isTab("Auren")) {
            drawAuraPreview(CosmeticsCore.aura(id), x, y, w, h, animated);
        } else if (isTab(ACC_TAB)) {
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
        // Vorlagen (aus dem Cosmetics-Paket): setzen den Text ins Feld
        java.util.List<String[]> presets = CosmeticsCore.nametagPresets();
        float py = oy + 76 * d;
        if (!presets.isEmpty()) {
            kit.caption("Vorlagen", lx + 2 * d, py);
            float px = lx, row = 0;
            for (int i = 0; i < presets.size(); i++) {
                final String raw = presets.get(i)[2];
                String shownPreset = CosmeticsCore.formatNametag(raw);
                if (shownPreset == null) continue;
                int sc = Type.pixelScale(kit.g, 11 * d);
                float k = Draw.k(kit.g);
                float tw = font.width(shownPreset) * sc / k, bw = tw + 24 * d, bh = 36 * d;
                if (px + bw > lx + lw && px > lx) {
                    // zweite Zeile, mehr nicht
                    if (row >= 1) break;
                    row++;
                    px = lx;
                    py += bh + 8 * d;
                }
                boolean on = raw.equals(tag.text);
                boolean hv = kit.hit("preset:" + i, px, py + 16 * d, bw, bh, () -> {
                    tag.text = raw;
                    kit.focus(null);
                });
                Draw.glass(kit.g, px, py + 16 * d, bw, bh, 10 * d, on ? 0x299B5CFF : hv ? Tokens.SURFACE_HOVER : Tokens.SURFACE,
                        on ? Tokens.ACC_LINE : hv ? Tokens.LINE_STRONG : Tokens.LINE);
                Type.pixel(kit.g, shownPreset, px + 12 * d, py + 16 * d + (bh - 7f * sc / k) / 2f, sc, 0, 0xFFFFFFFF);
                px += bw + 8 * d;
            }
            py += 66 * d;
        }
        // Übernehmen / Entfernen
        float ay = py, bh = 38 * d;
        float w0 = kit.button("tag:ok", lx, ay, bh, "Übernehmen", "check", Widgets.PRIMARY, () -> {
            CosmeticsCore.selectNametag(tag.text);
            kit.focus(null);
        });
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
            scroll.reset();
        }) + 8 * d;
        kit.button("adm:save", bx, top, bh, "Speichern", "check", Widgets.PRIMARY, AdminCore.uuid != null && AdminCore.dirty ? (Runnable) AdminCore::save : null);
        String status = AdminCore.message;
        if (status == null && AdminCore.uuid != null) status = AdminCore.name + (AdminCore.dirty ? " – Änderungen noch nicht gespeichert" : " – " + AdminCore.ITEMS.size() + " Freischaltungen");
        if (status == null) status = "Name eingeben und laden, dann Häkchen setzen und speichern.";
        int sc = AdminCore.error ? Draw.mix(Tokens.BAD, Tokens.TEXT, 0.25f) : AdminCore.dirty ? Tokens.WARN : Tokens.MUTED;
        kit.textMid(status, lx, top + 70 * d, 14, Tokens.MEDIUM, sc, lw);
        if (AdminCore.uuid == null) return;

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
        float listTop = top + 96 * d, viewH = y + h - listTop - 8 * d;
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
        if (System.currentTimeMillis() - openedAt < 250 || !isTab("MiniMe") || !CosmeticsCore.owns("minime", null)) return false;
        if (s.length() != 1 || NAME_CHARS.indexOf(s.charAt(0)) < 0) return false;
        kit.focus(mini);
        return kit.typed(s);
    }

    @Override
    protected void back() {
        Compat.setScreen(inWorld() ? new HubScreen() : null);
    }
}
