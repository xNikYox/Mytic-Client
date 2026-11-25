package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.ui.Brand;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.setting.Setting;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-Einstellungen im neuen Design (Vorschau „Einstellungen“): Bereiche links (Allgemein, Leistung, Steuerung,
 * Konto, Über), rechts gruppierte Optionszeilen mit Titel, Beschreibung und Bedienelement – alles echte
 * Einstellungen der Mytic-Module. Suche über alle Bereiche; Änderungen werden sofort gespeichert.
 */
public class SettingsScreen extends DesignScreen {
    private static final float WIN_W = 1600, WIN_H = 880, SIDE = 264, HEAD = 88, FOOT = 76;
    private static final String[][] SECTIONS = {
            {"Allgemein", "gear", "Hauptmenü, Menüs und Erscheinungsbild"},
            {"Leistung", "bolt", "Bildrate und integrierte Optimierungen"},
            {"Steuerung", "keyboard", "Tastenbelegung der Mytic-Funktionen"},
            {"Konto", "user", "Konten und Privatsphäre"},
            {"Über", "info", "Version und Hinweise"},
    };

    /** Eine Optionszeile */
    private abstract static class Row {
        final String title, desc;

        Row(String title, String desc) {
            this.title = title;
            this.desc = desc == null ? "" : desc;
        }

        abstract float height();

        abstract void draw(float x, float y, float w);

        boolean matches(String q) {
            return title.toLowerCase(Locale.ROOT).contains(q) || desc.toLowerCase(Locale.ROOT).contains(q);
        }
    }

    private static final class Group {
        final String caption;
        final List<Row> rows = new ArrayList<Row>();

        Group(String caption) {
            this.caption = caption;
        }
    }

    private final GuiScreen parent;
    private int section;
    private final Kit.Field search = new Kit.Field(40);
    private final Kit.Scroll scroll = new Kit.Scroll();
    private final SettingRows widgets = new SettingRows();
    private final List<List<Group>> groups = new ArrayList<List<Group>>();
    private String lastQuery = "";

    public SettingsScreen(GuiScreen parent) {
        super("settings", "Einstellungen");
        this.parent = parent;
        build();
    }

    private static Module module(String id) {
        for (Module m : MyticClient.MODULES) if (m.id.equals(id)) return m;
        return null;
    }

    private Row setting(final Setting<?> s, String title, String desc) {
        return new Row(title == null ? s.label : title, desc) {
            float height() {
                return SettingRows.rowHeight(s);
            }

            void draw(float x, float y, float w) {
                widgets.row(kit, s, title, this.desc, x, y, w);
            }
        };
    }

    /** Modul an/aus als Zeile */
    private Row toggleModule(final Module m, String title, String desc) {
        return new Row(title == null ? m.name : title, desc == null ? m.description : desc) {
            float height() {
                return 76;
            }

            void draw(float x, float y, float w) {
                float h = 76 * d, pad = 22 * d;
                kit.toggle("mod:" + m.id, x + w - pad - 46 * d, y + h / 2 - 13 * d, m.enabled(), () -> {
                    m.toggle();
                    MyticClient.config().save();
                });
                SettingRows.option(kit, title, desc, x + pad, y, w - 2 * pad - 70 * d, h);
            }
        };
    }

    private void build() {
        for (int i = 0; i < SECTIONS.length; i++) groups.add(new ArrayList<Group>());
        // Allgemein
        if (MyticClient.theme != null) {
            de.myticlegacy.client.module.SimpleModules.Theme t = MyticClient.theme;
            Group ui = new Group("Oberfläche");
            ui.rows.add(setting(t.customMenu, "Mytic-Hauptmenü", "Eigenes Hauptmenü statt des Minecraft-Menüs"));
            ui.rows.add(setting(t.titleBackground, "Hauptmenü-Hintergrund", "Was hinter dem Hauptmenü zu sehen ist"));
            ui.rows.add(setting(t.menuBackground, "Menü-Hintergrund im Spiel", "Spielwelt hinter Mytic-Menüs unscharf oder abgedunkelt"));
            ui.rows.add(setting(t.menuSize, "Menügröße", "Wie viel vom Bildschirm Mytic-Fenster einnehmen"));
            ui.rows.add(setting(t.menuAnimations, "Animationen", "Weiche Übergänge und Bewegung in Menüs"));
            groups.get(0).add(ui);
            Group acc = new Group("Akzent & HUD");
            acc.rows.add(setting(t.accent, "Akzentfarbe", "Für HUD-Anzeigen und Hervorhebungen"));
            acc.rows.add(setting(t.hudStyle, "HUD-Stil", "Aussehen aller HUD-Anzeigen"));
            groups.get(0).add(acc);
        }
        // Leistung
        Module boost = module("fpsboost"), bg = module("backgroundfps");
        if (boost != null) {
            Group g = new Group("FPS-Boost");
            g.rows.add(toggleModule(boost, null, null));
            for (Setting<?> s : boost.settings) g.rows.add(setting(s, null, null));
            groups.get(1).add(g);
        }
        if (bg != null) {
            Group g = new Group("Im Hintergrund");
            g.rows.add(toggleModule(bg, null, null));
            for (Setting<?> s : bg.settings) g.rows.add(setting(s, null, null));
            groups.get(1).add(g);
        }
        // Steuerung
        Group keys = new Group("Mytic-Tasten");
        for (final KeyBinding k : mc.gameSettings.keyBindings) {
            if (!k.getKeyDescription().startsWith("key.myticclient.")) continue;
            String label = I18n.format(k.getKeyDescription());
            keys.rows.add(new Row(label, null) {
                float height() {
                    return 64;
                }

                void draw(float x, float y, float w) {
                    float h = 64 * d, pad = 22 * d;
                    String key = k.getKeyCode() == 0 ? "—" : GameSettings.getKeyDisplayString(k.getKeyCode()).toUpperCase(Locale.ROOT);
                    float kh = 34 * d;
                    float kw = kbdWidth(key, kh);
                    kit.kbd(key, x + w - pad - kw, y + (h - kh) / 2f, kh, false, false);
                    SettingRows.option(kit, title, null, x + pad, y, w - 2 * pad - kw - 20 * d, h);
                }
            });
        }
        keys.rows.add(new Row("Tasten ändern", "In den Minecraft-Optionen unter Steuerung → Tastenbelegung") {
            float height() {
                return 76;
            }

            void draw(float x, float y, float w) {
                float h = 76 * d, pad = 22 * d, bh = 38 * d, bw = kit.buttonWidth("Optionen öffnen", "keyboard", bh);
                kit.button("controls", x + w - pad - bw, y + (h - bh) / 2f, bw, bh, "Optionen öffnen", "keyboard", Widgets.SECONDARY,
                        () -> Compat.setScreen(Compat.optionsScreen(SettingsScreen.this)));
                SettingRows.option(kit, title, desc, x + pad, y, w - 2 * pad - bw - 20 * d, h);
            }
        });
        groups.get(2).add(keys);
        Group hints = new Group("Kürzel");
        hints.rows.add(new Row("Mods direkt öffnen", "Strg + Rechts-Shift") {
            float height() {
                return 76;
            }

            void draw(float x, float y, float w) {
                SettingRows.option(kit, title, desc, x + 22 * d, y, w - 44 * d, 76 * d);
            }
        });
        groups.get(2).add(hints);
        // Konto
        Group acc = new Group("Konto");
        acc.rows.add(new Row("Konto", "Konto wechseln") {
            float height() {
                return 80;
            }

            void draw(float x, float y, float w) {
                float h = 80 * d, pad = 22 * d, hs = 40 * d;
                String name = mc.getSession().getUsername();
                Friends.Friend me = Friends.me();
                kit.head(me == null ? null : me.head, name, x + pad, y + (h - hs) / 2f, hs, Tokens.OK, false);
                float tx = x + pad + hs + 14 * d;
                kit.textMid(name, tx, y + h / 2 - 10 * d, 16, Tokens.SEMIBOLD, Tokens.TEXT, 0);
                kit.textMid("Konten wechselst du in 1.8.9 im Mytic Launcher", tx, y + h / 2 + 12 * d, 13, Tokens.MEDIUM, Tokens.MUTED, w - (tx - x) - pad);
            }
        });
        groups.get(3).add(acc);
        Module friends = module("friends");
        if (friends != null) {
            Group p = new Group("Privatsphäre");
            p.rows.add(toggleModule(friends, "Online-Status zeigen", "Freunde sehen, wann du online bist"));
            for (Setting<?> s : friends.settings) p.rows.add(setting(s, null, null));
            groups.get(3).add(p);
        }
        Module cloud = module("cloudsync");
        if (cloud != null) {
            Group c = new Group("Sicherung");
            c.rows.add(toggleModule(cloud, null, null));
            groups.get(3).add(c);
        }
        // Über: eigener Inhalt (about())
    }

    private float kbdWidth(String text, float h) {
        int s = de.myticlegacy.client.gui.ui.Type.pixelScale(kit.g, Math.min(h * 0.32f, 9 * d));
        float t = de.myticlegacy.client.gui.ui.Type.pixelWidth(kit.g, text, s, 0);
        return Math.max(40 * d * h / (34 * d), t + 24 * d * h / (34 * d));
    }

    static String modVersion() {
        String v = MyticClient.VERSION;
        try {
            java.io.InputStream in = SettingsScreen.class.getResourceAsStream("/mcmod.info");
            if (in != null) {
                try {
                    com.google.gson.JsonArray arr = new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(in, "UTF-8")).getAsJsonArray();
                    v = arr.get(0).getAsJsonObject().get("version").getAsString();
                } finally {
                    in.close();
                }
            }
        } catch (Exception ignored) {
            // dann die eingebaute Angabe
        }
        int plus = v.indexOf('+');
        return plus > 0 ? v.substring(0, plus) : v;
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    protected void frame(GuiGraphics g) {
        if (!search.text.equals(lastQuery)) {
            lastQuery = search.text;
            scroll.reset();
        }
        float[] r = center(WIN_W, WIN_H);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        kit.window(x, y, w, h, mc.theWorld == null);
        float sw = SIDE * d;
        Draw.rect(g, x + 1, y + 1, sw, h - 2, Tokens.RAD_XL * d, 0x04FFFFFF);
        kit.vline(x + sw, y, h);
        Brand.side(kit, x + 28 * d, y + 30 * d, "Einstellungen", "Mytic Client");
        float ny = y + 110 * d;
        for (int i = 0; i < SECTIONS.length; i++) {
            final int idx = i;
            kit.nav("sec" + i, x + 24 * d, ny, sw - 48 * d, SECTIONS[i][1], SECTIONS[i][0], null, section == i && search.isEmpty(), () -> {
                section = idx;
                search.text = "";
                scroll.reset();
            });
            ny += 46 * d;
        }
        kit.text("MYTIC " + modVersion(), x + 36 * d, y + h - 40 * d, 11, Tokens.SEMIBOLD, Tokens.SUBTLE);

        // Kopf
        float mx = x + sw, mw = w - sw, pad = 32 * d;
        boolean searching = !search.isEmpty();
        kit.text(searching ? "Suche" : SECTIONS[section][0], mx + pad, y + 24 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.text(searching ? "Treffer in allen Bereichen" : SECTIONS[section][2], mx + pad, y + 54 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        kit.iconButton("close", mx + mw - pad - 40 * d, y + 24 * d, 40 * d, "x", true, this::onClose);
        float fw = Math.min(320 * d, mw * 0.3f);
        kit.field("search", search, mx + mw - pad - 40 * d - 12 * d - fw, y + 24 * d, fw, 40 * d, "Einstellungen durchsuchen …", "search");
        kit.hline(mx, y + HEAD * d, mw);

        // Inhalt
        float by = y + HEAD * d, bh = h - HEAD * d - FOOT * d;
        float cx = mx + pad, cw = mw - 2 * pad;
        List<Group> shown = shownGroups();
        float content = 28 * d;
        if (!searching && section == 4) content += aboutHeight();
        for (Group gr : shown) content += groupHeight(gr) + 30 * d;
        float off = kit.scroll(scroll, mx, by, mw, bh, content);
        kit.clip(mx, by + 1, mw, bh - 2);
        float cy = by + 28 * d - off;
        if (!searching && section == 4) {
            about(cx, cy, cw);
            cy += aboutHeight();
        }
        if (searching && shown.isEmpty()) {
            Icons.draw(g, "search", cx + cw / 2f - 16 * d, cy + 60 * d, 32 * d, Tokens.MUTED);
            kit.textCenter("Keine Einstellung gefunden", cx + cw / 2f, cy + 120 * d, 15, Tokens.MEDIUM, Tokens.MUTED);
        }
        for (Group gr : shown) {
            float gh = groupHeight(gr);
            if (cy + gh > by && cy < by + bh) drawGroup(gr, cx, cy, cw, by, bh);
            cy += gh + 30 * d;
        }
        kit.unclip();
        kit.scrollbar("body", scroll, mx + mw - 12 * d, by + 8 * d, bh - 16 * d);

        // Fuß
        float fy = y + h - FOOT * d;
        kit.hline(mx, fy, mw);
        Icons.draw(g, "check", mx + pad, fy + FOOT * d / 2f - 8 * d, 16 * d, Tokens.SUBTLE);
        kit.textMid("Änderungen werden sofort gespeichert", mx + pad + 26 * d, fy + FOOT * d / 2f, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE, 0);
        float bh2 = 38 * d, bw = kit.buttonWidth("Fertig", null, bh2) + 16 * d;
        kit.button("done", mx + mw - pad - bw, fy + (FOOT * d - bh2) / 2f, bw, bh2, "Fertig", null, Widgets.PRIMARY, this::onClose);
    }

    private List<Group> shownGroups() {
        if (search.isEmpty()) return groups.get(section);
        String q = search.text.toLowerCase(Locale.ROOT).trim();
        List<Group> out = new ArrayList<Group>();
        for (int i = 0; i < groups.size(); i++) {
            for (Group gr : groups.get(i)) {
                Group f = new Group(SECTIONS[i][0] + " · " + gr.caption);
                for (Row r : gr.rows) if (r.matches(q)) f.rows.add(r);
                if (!f.rows.isEmpty()) out.add(f);
            }
        }
        return out;
    }

    private float groupHeight(Group gr) {
        float h = 24 * d;
        for (Row r : gr.rows) h += r.height() * d;
        return h;
    }

    private void drawGroup(Group gr, float x, float y, float w, float clipY, float clipH) {
        kit.caption(gr.caption, x + 4 * d, y);
        float by = y + 24 * d, bh = groupHeight(gr) - 24 * d;
        Draw.rect(kit.g, x, by, w, bh, 16 * d, Tokens.SURFACE);
        Draw.ring(kit.g, x, by, w, bh, 16 * d, 1, Tokens.LINE);
        float ry = by;
        for (int i = 0; i < gr.rows.size(); i++) {
            Row r = gr.rows.get(i);
            float rh = r.height() * d;
            if (ry + rh > clipY && ry < clipY + clipH) {
                if (i > 0) SettingRows.divider(kit, x, ry, w);
                r.draw(x, ry, w);
            }
            ry += rh;
        }
    }

    private float aboutHeight() {
        return 150 * d;
    }

    private void about(float x, float y, float w) {
        Brand.emblem(kit.g, x + 8 * d, y + 12 * d, 84 * d);
        float tx = x + 8 * d + 84 * d + 24 * d;
        kit.text("Mytic Client", tx, y + 16 * d, 30, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.text("Version " + modVersion() + " · Minecraft " + Compat.versionName() + " · Forge", tx, y + 58 * d, 15, Tokens.MEDIUM, Tokens.MUTED);
        kit.paragraph("Mytic Client ist ein Fan-Projekt und nicht offiziell mit Mojang Studios oder Microsoft verbunden.", tx, y + 88 * d,
                w - (tx - x), 13, Tokens.MEDIUM, Tokens.SUBTLE, 19, 2);
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean release(Input.Click event) {
        boolean r = super.release(event);
        widgets.saveIfDirty();
        return r;
    }

    @Override
    protected boolean typedFree(String s) {
        if (s.isEmpty() || s.charAt(0) < ' ') return false;
        kit.focus(search);
        return kit.typed(s);
    }

    @Override
    protected void back() {
        if (!search.text.isEmpty()) search.text = "";
        else onClose();
    }

    @Override
    public void onClose() {
        widgets.saveIfDirty();
        MyticClient.config().save();
        Compat.setScreen(parent);
    }
}
