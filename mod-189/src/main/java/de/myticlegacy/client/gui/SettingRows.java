package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.gui.ui.ColorField;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.CustomSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.Setting;
import de.myticlegacy.client.setting.SliderSetting;
import de.myticlegacy.client.setting.TextSetting;

import java.util.HashMap;
import java.util.Map;

/**
 * Bedienelemente für Mod-Einstellungen im neuen Design: Schalter, Regler mit Wert, Aufklappliste, Farbwahl,
 * Textfeld, Aktions-Button. Zwei Formen: gestapelt (Detailbereich des Mod-Menüs: Bezeichnung oben, Element darunter
 * bzw. rechts) und als Optionszeile (Einstellungen: Titel + Beschreibung links, Element rechts). Änderungen werden
 * gespeichert (Ziehen: beim Loslassen über {@link #saveIfDirty()}).
 */
public final class SettingRows {
    private final Map<Setting<?>, ColorField> colors = new HashMap<Setting<?>, ColorField>();
    private final Map<Setting<?>, Kit.Field> texts = new HashMap<Setting<?>, Kit.Field>();
    private boolean dirty;

    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        MyticClient.config().save();
    }

    private void save() {
        dirty = false;
        MyticClient.config().save();
    }

    // ------------------------------------------------------------------------------------------ gestapelt

    /** Höhe einer Zeile (Design-Pixel) im Detailbereich */
    public static float stackedHeight(Setting<?> s) {
        if (s instanceof CustomSetting) return ((CustomSetting<?>) s).height();
        if (s instanceof SliderSetting) return 84;
        if (s instanceof ColorSetting) return 44 + ColorField.HEIGHT + 16;
        if (s instanceof TextSetting) return 44 + 40 + 16;
        if (s instanceof ModeSetting) return 64;
        if (s instanceof ActionSetting) return 62;
        return 56;
    }

    /** Zeile im Detailbereich zeichnen (y = Oberkante, w = Breite in Einheiten) */
    public void stacked(Kit kit, final Setting<?> s, float x, float y, float w) {
        float d = kit.d;
        String id = "set:" + s.path();
        float h = stackedHeight(s) * d;
        if (s instanceof CustomSetting) {
            ((CustomSetting<?>) s).draw(kit, x, y, w);
            return;
        }
        if (s instanceof BoolSetting || s instanceof ModeSetting || s instanceof ActionSetting) {
            float cy = y + h / 2;
            float ctlW = control(kit, s, id, x, y, w, h, true);
            kit.textMid(s.label, x, cy, 15, Tokens.MEDIUM, Tokens.TEXT, w - ctlW - 16 * d);
            return;
        }
        float ly = y + 14 * d;
        if (s instanceof SliderSetting) {
            final SliderSetting sl = (SliderSetting) s;
            kit.text(s.label, x, ly, 15, Tokens.MEDIUM, Tokens.TEXT);
            float vw = 64 * d;
            slider(kit, id, sl, x + 9 * d, y + 44 * d, w - vw - 18 * d);
            kit.textRight(sl.display(), x + w, y + 44 * d + 11 * d, 14.5f, Tokens.SEMIBOLD, Tokens.MUTED);
            return;
        }
        if (s instanceof TextSetting) {
            TextSetting ts = (TextSetting) s;
            kit.text(s.label, x, ly, 15, Tokens.MEDIUM, Tokens.TEXT);
            String hint = ts.hint();
            if (!hint.isEmpty()) kit.textRight(hint, x + w, ly + 5 * d, 13, Tokens.MEDIUM, Tokens.SUBTLE);
            text(kit, id, ts, x, y + 44 * d, w);
            return;
        }
        if (s instanceof ColorSetting) {
            kit.text(s.label, x, ly, 15, Tokens.MEDIUM, Tokens.TEXT);
            color(kit, id, (ColorSetting) s, x, y + 44 * d, w);
        }
    }

    // ------------------------------------------------------------------------------------------ Optionszeile

    /** Höhe einer Optionszeile (Design-Pixel) */
    public static float rowHeight(Setting<?> s) {
        if (s instanceof CustomSetting) return ((CustomSetting<?>) s).height() + 16;
        if (s instanceof ColorSetting) return Math.max(76, 36 + ColorField.HEIGHT);
        return 76;
    }

    /** Optionszeile: Titel/Beschreibung links, Element rechts (breite Elemente 380) */
    public void row(Kit kit, Setting<?> s, String title, String desc, float x, float y, float w) {
        float d = kit.d;
        String id = "opt:" + s.path();
        float h = rowHeight(s) * d;
        if (s instanceof CustomSetting) {
            ((CustomSetting<?>) s).draw(kit, x + 22 * d, y + 8 * d, w - 44 * d);
            return;
        }
        float pad = 22 * d;
        float ctlW = control(kit, s, id, x + pad, y, w - 2 * pad, h, false);
        float tw = w - 2 * pad - ctlW - 24 * d;
        option(kit, title == null ? s.label : title, desc, x + pad, y, tw, h);
    }

    /** Titel + Beschreibung einer Optionszeile */
    public static void option(Kit kit, String title, String desc, float x, float y, float tw, float h) {
        float d = kit.d;
        boolean two = desc != null && !desc.isEmpty();
        float cy = y + h / 2;
        if (!two) {
            kit.textMid(title, x, cy, 16, Tokens.SEMIBOLD, Tokens.TEXT, tw);
            return;
        }
        if (h > 90 * d) cy = y + 38 * d;
        kit.textMid(title, x, cy - 11 * d, 16, Tokens.SEMIBOLD, Tokens.TEXT, tw);
        kit.textMid(desc, x, cy + 12 * d, 14, Tokens.MEDIUM, Tokens.MUTED, tw);
    }

    /** Element rechts in der Zeile (x..x+w), gibt seine Breite zurück */
    private float control(Kit kit, final Setting<?> s, String id, float x, float y, float w, float h, boolean stacked) {
        float d = kit.d;
        float right = x + w;
        if (s instanceof BoolSetting) {
            final BoolSetting b = (BoolSetting) s;
            kit.toggle(id, right - 46 * d, y + h / 2 - 13 * d, b.get(), new Runnable() {
                public void run() {
                    b.toggle();
                    save();
                }
            });
            return 46 * d;
        }
        if (s instanceof ModeSetting) {
            final ModeSetting m = (ModeSetting) s;
            float dw = Math.min(stacked ? 220 * d : 260 * d, w * 0.55f);
            kit.dropdown(id, right - dw, y + h / 2 - 20 * d, dw, m.options, m.get(), new Kit.TextSink() {
                public void accept(String v) {
                    m.set(v);
                    save();
                }
            });
            return dw;
        }
        if (s instanceof ActionSetting) {
            final ActionSetting a = (ActionSetting) s;
            float bh = 38 * d;
            String label = a.button();
            float bw = kit.buttonWidth(label, null, bh);
            kit.button(id, right - bw, y + h / 2 - bh / 2, bw, bh, label, null, Widgets.SECONDARY, new Runnable() {
                public void run() {
                    a.run();
                    save();
                }
            });
            return bw;
        }
        float cw = Math.min(380 * d, w * 0.5f);
        if (s instanceof SliderSetting) {
            SliderSetting sl = (SliderSetting) s;
            float vw = 74 * d;
            slider(kit, id, sl, right - cw + 9 * d, y + h / 2 - 11 * d, cw - vw - 18 * d);
            kit.textRight(sl.display(), right, y + h / 2, 14.5f, Tokens.SEMIBOLD, Tokens.MUTED);
            return cw;
        }
        if (s instanceof TextSetting) {
            text(kit, id, (TextSetting) s, right - cw, y + h / 2 - 20 * d, cw);
            return cw;
        }
        if (s instanceof ColorSetting) {
            color(kit, id, (ColorSetting) s, right - cw, y + (h - ColorField.HEIGHT * d) / 2, cw);
            return cw;
        }
        return 0;
    }

    private void slider(Kit kit, String id, final SliderSetting sl, float x, float y, float w) {
        kit.slider(id, x, y, w, (float) sl.fraction(), new Kit.FloatSink() {
            public void accept(float v) {
                sl.setFraction(v);
                dirty = true;
            }
        }, new Runnable() {
            public void run() {
                save();
            }
        });
    }

    private void text(Kit kit, String id, final TextSetting ts, float x, float y, float w) {
        Kit.Field f = texts.get(ts);
        if (f == null) {
            f = new Kit.Field(ts.max);
            f.text = ts.get();
            final Kit.Field ff = f;
            final Kit k = kit;
            f.onEnter = new Runnable() {
                public void run() {
                    k.focus(null);
                }
            };
            texts.put(ts, f);
            ff.allowed = null;
        }
        if (kit.focused() == f) {
            if (!f.text.equals(ts.get())) {
                ts.set(f.text);
                save();
            }
        } else {
            f.text = ts.get();
        }
        kit.field(id, f, x, y, w, 40 * kit.d, "leer", "pencil");
    }

    private void color(Kit kit, String id, final ColorSetting c, float x, float y, float w) {
        ColorField cf = colors.get(c);
        if (cf == null) {
            cf = new ColorField();
            colors.put(c, cf);
        }
        cf.draw(kit, id, x, y, w, c.get(), ColorSetting.PALETTE, new Kit.IntSink() {
            public void accept(int v) {
                c.set(v);
                dirty = true;
            }
        }, new Runnable() {
            public void run() {
                save();
            }
        });
    }

    /** Trennlinie zwischen Zeilen */
    public static void divider(Kit kit, float x, float y, float w) {
        Draw.rect(kit.g, x, y, w, Math.max(1f / Draw.k(kit.g), kit.d), 0, Tokens.LINE);
    }
}
