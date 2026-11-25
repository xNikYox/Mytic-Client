package de.myticlegacy.client.gui;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.ui.Crosshair;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Kit;
import de.myticlegacy.client.gui.ui.Motion;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.gui.ui.Widgets;
import de.myticlegacy.client.module.CrosshairModule;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Fadenkreuz-Editor im Mytic-Design: links die Live-Vorschau (groß und in Originalgröße, normal / auf Ziel / Treffer)
 * und Vorlagen, rechts das 15 × 15-Pixelraster (Form „Pixelbild“) und alle Einstellungen. Unten Teilen per Code.
 * Änderungen gelten sofort. (Java-8-Stil, gleiche Fassung in der 1.8.9-Mod.)
 */
public class CrosshairScreen extends DesignScreen {
    private static final float WIN_W = 1600, WIN_H = 880, HEAD = 88, FOOT = 76, LEFT = 640;
    private static final String[] MODES = {"Normal", "Auf Ziel", "Treffer"};

    private final Screen parent;
    private final CrosshairModule xh;
    private final SettingRows rows = new SettingRows();
    private final Kit.Scroll scroll = new Kit.Scroll();
    private int mode;
    /** Malen im Pixelraster: Wert des aktuellen Strichs (null = kein Strich) */
    private Boolean stroke;
    private boolean pixelsDirty;

    public CrosshairScreen(Screen parent) {
        super("crosshair", "Fadenkreuz-Editor");
        this.parent = parent;
        this.xh = MyticClient.crosshair;
    }

    @Override
    protected void frame(GuiGraphics g) {
        float[] r = center(WIN_W, WIN_H);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        kit.window(x, y, w, h, minecraft.level == null);
        float pad = 32 * d;
        Crosshair.Spec spec = xh.spec();

        // Kopf
        float is = 42 * d, iy = y + (HEAD * d - is) / 2f;
        Draw.gradient(g, x + pad, iy, is, is, 12 * d, 0x739B5CFF, 0x59EC5FBF);
        Icons.draw(g, "crosshair", x + pad + 10 * d, iy + 10 * d, 22 * d, 0xFFFFFFFF);
        float tx = x + pad + is + 16 * d;
        kit.text("Fadenkreuz-Editor", tx, y + 25 * d, 21, Tokens.SEMIBOLD, Tokens.TEXT);
        kit.text("Form, Farbe und Pixelbild – Änderungen gelten sofort", tx, y + 54 * d, 14.5f, Tokens.MEDIUM, Tokens.SUBTLE);
        kit.iconButton("close", x + w - pad - 40 * d, y + 24 * d, 40 * d, "x", true, new Runnable() {
            public void run() {
                onClose();
            }
        });
        float tgX = x + w - pad - 40 * d - 20 * d - 46 * d;
        kit.toggle("enabled", tgX, y + HEAD * d / 2f - 13 * d, xh.enabled(), new Runnable() {
            public void run() {
                xh.toggle();
                MyticClient.config().save();
            }
        });
        kit.textRight(xh.enabled() ? "Aktiv" : "Aus – Minecraft-Fadenkreuz", tgX - 12 * d, y + HEAD * d / 2f, 14.5f, Tokens.SEMIBOLD,
                xh.enabled() ? Tokens.ACC_TEXT : Tokens.SUBTLE);
        kit.hline(x, y + HEAD * d, w);

        // links: Vorschau und Vorlagen
        float lx = x + pad, lw = LEFT * d - 2 * pad, ly = y + HEAD * d + 24 * d;
        float ph = 330 * d;
        preview(spec, lx, ly, lw, ph);
        kit.seg("mode", lx, ly + ph + 14 * d, MODES, new String[]{"crosshair", "target", "zap"}, null, mode, new Kit.IntSink() {
            public void accept(int v) {
                mode = v;
            }
        });
        float py = ly + ph + 14 * d + 42 * d + 24 * d;
        kit.caption("Vorlagen", lx + 4 * d, py);
        presets(lx, py + 24 * d, lw, y + h - FOOT * d - 20 * d - (py + 24 * d));
        kit.vline(x + LEFT * d, y + HEAD * d, h - HEAD * d - FOOT * d);

        // rechts: Pixelraster + Einstellungen
        float rx = x + LEFT * d, rw = w - LEFT * d, by = y + HEAD * d, bh = h - HEAD * d - FOOT * d;
        float cx = rx + pad, cw = rw - 2 * pad;
        List<Setting<?>> list = settings();
        boolean pixels = Crosshair.PIXELS.equals(spec.form);
        float content = 24 * d + (pixels ? gridBlockHeight() + 24 * d : 0) + 24 * d + 24 * d;
        for (Setting<?> s : list) content += SettingRows.stackedHeight(s) * d;
        float off = kit.scroll(scroll, rx, by, rw, bh, content);
        kit.clip(rx, by + 1, rw, bh - 2);
        float cy = by + 24 * d - off;
        if (pixels) {
            kit.caption("Pixelbild (15 × 15)", cx + 4 * d, cy);
            grid(spec, cx, cy + 24 * d, cw);
            cy += gridBlockHeight() + 24 * d;
        }
        kit.caption("Einstellungen", cx + 4 * d, cy);
        cy += 24 * d;
        float lh = 0;
        for (Setting<?> s : list) lh += SettingRows.stackedHeight(s) * d;
        Draw.rect(g, cx, cy, cw, lh + 8 * d, 16 * d, Tokens.SURFACE);
        Draw.ring(g, cx, cy, cw, lh + 8 * d, 16 * d, 1, Tokens.LINE);
        float sy = cy + 4 * d;
        for (int i = 0; i < list.size(); i++) {
            Setting<?> s = list.get(i);
            float sh = SettingRows.stackedHeight(s) * d;
            if (sy + sh >= by && sy <= by + bh) {
                if (i > 0) SettingRows.divider(kit, cx + 20 * d, sy, cw - 40 * d);
                rows.stacked(kit, s, cx + 20 * d, sy, cw - 40 * d);
            }
            sy += sh;
        }
        kit.unclip();
        kit.scrollbar("body", scroll, x + w - 12 * d, by + 8 * d, bh - 16 * d);

        // Fuß
        float fy = y + h - FOOT * d, bh2 = 38 * d, fby = fy + (FOOT * d - bh2) / 2f;
        kit.hline(x, fy, w);
        float bx = x + pad;
        bx += kit.button("copy", bx, fby, bh2, "Code kopieren", "link", Widgets.SECONDARY, new Runnable() {
            public void run() {
                xh.copyCode();
            }
        }) + 10 * d;
        bx += kit.button("paste", bx, fby, bh2, "Code einfügen", "plus", Widgets.SECONDARY, new Runnable() {
            public void run() {
                xh.pasteCode();
            }
        }) + 10 * d;
        kit.button("reset", bx, fby, bh2, "Standard", "rotL", Widgets.GHOST, new Runnable() {
            public void run() {
                xh.resetSettings();
                MyticClient.config().save();
            }
        });
        float dw = kit.buttonWidth("Fertig", null, bh2) + 16 * d;
        kit.button("done", x + w - pad - dw, fby, dw, bh2, "Fertig", null, Widgets.PRIMARY, new Runnable() {
            public void run() {
                onClose();
            }
        });
        String hint = "MYTIC-XH:… teilt Form und Farben";
        kit.textRight(hint, x + w - pad - dw - 16 * d, fy + FOOT * d / 2f, 13, Tokens.MEDIUM, Tokens.SUBTLE);
    }

    /** Sichtbare Einstellungen (ohne Knöpfe und internes Pixelbild) */
    private List<Setting<?>> settings() {
        List<Setting<?>> out = new ArrayList<Setting<?>>();
        for (Setting<?> s : xh.settings) if (s.visible() && !(s instanceof ActionSetting)) out.add(s);
        return out;
    }

    private int previewColor(Crosshair.Spec s) {
        if (mode == 1) return s.dynamic ? s.targetColor : s.color;
        if (mode == 2) return xh.hitFlash.get() ? xh.hitColor.get() : s.color;
        return s.color;
    }

    // ------------------------------------------------------------------------------------------ Vorschau

    private void preview(Crosshair.Spec spec, float x, float y, float w, float h) {
        GuiGraphics g = kit.g;
        float r = 16 * d, sky = h * 0.58f;
        Draw.rect(g, x, y, w, sky + r, r, 0xFF4B2F78);
        Draw.vgradient(g, x, y + r, w, sky - r, 0xFF4B2F78, 0xFF9A4F86);
        Draw.rect(g, x, y + sky, w, h - sky, r, 0xFF2D5230);
        Draw.vgradient(g, x, y + sky, w, h - sky - r, 0xFF3D6B3C, 0xFF2D5230);
        Draw.ring(g, x, y, w, h, r, 1, Tokens.LINE);
        kit.text("LIVE-VORSCHAU", x + 14 * d, y + 14 * d, 11, Tokens.SEMIBOLD, 0xB3FFFFFF);
        int ccx = Math.round(x + w * 0.42f), ccy = Math.round(y + h / 2f);
        if (mode == 1) target(g, ccx, ccy, h);
        int ext = Crosshair.extent(spec);
        int unit = Math.max(2, Math.min(Math.round(11 * d), (int) Math.floor(h * 0.78f / (2 * ext + 1))));
        Crosshair.draw(g, ccx - unit / 2, ccy - unit / 2, spec, previewColor(spec), unit);

        // Originalgröße (wie im Spiel bei der aktuellen GUI-Größe)
        float bw = 150 * d, bhh = 150 * d, bx = x + w - bw - 16 * d, by = y + 16 * d;
        Draw.glass(g, bx, by, bw, bhh, 12 * d, 0x7308060E, Tokens.LINE);
        int scale = Math.max(1, Compat.guiScale());
        int ox = Math.round(bx + bw / 2f), oy = Math.round(by + bhh / 2f + 8 * d);
        Crosshair.draw(g, ox - scale / 2, oy - scale / 2, spec, previewColor(spec), scale);
        kit.textCenter("Originalgröße", bx + bw / 2f, by + 16 * d, 11.5f, Tokens.SEMIBOLD, 0xB3FFFFFF);
    }

    /** Angedeuteter Spieler hinter dem Fadenkreuz („Auf Ziel“) */
    private void target(GuiGraphics g, int cx, int cy, float h) {
        float s = h * 0.75f / 32f;
        int c = 0xCC1A1424;
        Draw.rect(g, cx - 4 * s, cy - 16 * s, 8 * s, 8 * s, 1.5f * s, c);
        Draw.rect(g, cx - 4 * s, cy - 8 * s, 8 * s, 12 * s, 0, c);
        Draw.rect(g, cx - 8 * s, cy - 8 * s, 4 * s, 12 * s, 0, c);
        Draw.rect(g, cx + 4 * s, cy - 8 * s, 4 * s, 12 * s, 0, c);
        Draw.rect(g, cx - 4 * s, cy + 4 * s, 8 * s, 12 * s, 0, c);
    }

    private void presets(float x, float y, float w, float h) {
        int cols = 4;
        float gap = 12 * d, tw = (w - (cols - 1) * gap) / cols;
        float th = Math.max(64 * d, Math.min(112 * d, (h - gap) / 2f));
        for (int i = 0; i < Crosshair.PRESETS.length; i++) {
            final int idx = i;
            float px = x + (i % cols) * (tw + gap), py = y + (i / cols) * (th + gap);
            String id = "preset" + i;
            boolean hv = kit.hit(id, px, py, tw, th, new Runnable() {
                public void run() {
                    applyPreset(idx);
                }
            });
            float hvT = Motion.to(kit.key(id) + ":hv", hv, Tokens.MS_HOVER);
            Draw.glass(kit.g, px, py, tw, th, 14 * d, Draw.mix(Tokens.SURFACE, Tokens.SURFACE_HOVER, hvT), Draw.mix(Tokens.LINE, Tokens.LINE_STRONG, hvT));
            Crosshair.Spec p = Crosshair.preset(i);
            float area = th - 34 * d;
            int ext = Crosshair.extent(p);
            int unit = Math.max(1, Math.min(Math.round(4 * d), (int) Math.floor(area * 0.8f / (2 * ext + 1))));
            int ccx = Math.round(px + tw / 2f), ccy = Math.round(py + 8 * d + area / 2f);
            Crosshair.draw(kit.g, ccx - unit / 2, ccy - unit / 2, p, p.color, unit);
            kit.textCenter(Crosshair.PRESETS[i], px + tw / 2f, py + th - 17 * d, 13, Tokens.SEMIBOLD, hv ? Tokens.TEXT : Tokens.MUTED);
        }
    }

    private void applyPreset(int i) {
        Crosshair.Spec p = Crosshair.preset(i);
        Crosshair.Spec cur = xh.spec();
        // Farbe auf Ziel und Mobs bleiben, wie sie eingestellt sind
        p.dynamic = cur.dynamic;
        p.mobs = cur.mobs;
        p.targetColor = cur.targetColor;
        if (!Crosshair.PIXELS.equals(p.form)) p.pixels = cur.pixels;
        xh.apply(p);
        if (!xh.enabled()) xh.setEnabled(true);
        de.myticlegacy.client.gui.ui.Toasts.ok("Vorlage „" + Crosshair.PRESETS[i] + "“ übernommen");
    }

    // ------------------------------------------------------------------------------------------ Pixelraster

    private float cell() {
        return 22 * d;
    }

    private float gridBlockHeight() {
        return Crosshair.GRID * cell() + 2 * 16 * d + 24 * d;
    }

    private void grid(final Crosshair.Spec spec, float x, float y, float w) {
        GuiGraphics g = kit.g;
        final float c = cell();
        float gs = Crosshair.GRID * c, bh = gridBlockHeight() - 24 * d;
        Draw.rect(g, x, y, w, bh, 16 * d, Tokens.SURFACE);
        Draw.ring(g, x, y, w, bh, 16 * d, 1, Tokens.LINE);
        final float gx = x + 16 * d, gy = y + 16 * d;
        Draw.rect(g, gx, gy, gs, gs, 6 * d, Tokens.INSET);
        int center = Crosshair.GRID / 2;
        final boolean[] px = spec.pixels;
        for (int i = 0; i < Crosshair.GRID * Crosshair.GRID; i++) {
            int cxi = i % Crosshair.GRID, cyi = i / Crosshair.GRID;
            float ex = gx + cxi * c, ey = gy + cyi * c;
            if (px[i]) Draw.rect(g, ex + 1, ey + 1, c - 2, c - 2, 3 * d, spec.color | 0xFF000000);
            else if (cxi == center || cyi == center) Draw.rect(g, ex + 1, ey + 1, c - 2, c - 2, 3 * d, 0x10FFFFFF);
            else Draw.rect(g, ex + 1, ey + 1, c - 2, c - 2, 3 * d, 0x08FFFFFF);
        }
        if (kit.over(gx, gy, gs, gs)) {
            int hx = (int) ((kit.mx - gx) / c), hy = (int) ((kit.my - gy) / c);
            if (hx >= 0 && hy >= 0 && hx < Crosshair.GRID && hy < Crosshair.GRID)
                Draw.ring(g, gx + hx * c, gy + hy * c, c, c, 3 * d, 1, Tokens.ACC_LINE);
        }
        kit.dragArea("pixels", gx, gy, gs, gs, false, new Kit.FloatSink() {
            public void accept(float ignored) {
                int hx = (int) Math.floor((kit.mx - gx) / c), hy = (int) Math.floor((kit.my - gy) / c);
                if (hx < 0 || hy < 0 || hx >= Crosshair.GRID || hy >= Crosshair.GRID) return;
                boolean[] cur = xh.spec().pixels;
                int i = hy * Crosshair.GRID + hx;
                if (stroke == null) stroke = !cur[i];
                if (cur[i] != stroke) {
                    cur[i] = stroke;
                    xh.pixels.set(Crosshair.pixelsHex(cur));
                    pixelsDirty = true;
                }
            }
        });
        // Werkzeuge rechts neben dem Raster
        float tx = gx + gs + 20 * d, tw = x + w - 16 * d - tx, bh2 = 38 * d;
        if (tw > 120 * d) {
            kit.paragraph("Klicken und ziehen malt, auf einem gesetzten Feld beginnen radiert. Die Mitte ist markiert.", tx, gy + 2 * d, tw, 13,
                    Tokens.MEDIUM, Tokens.MUTED, 19, 4);
            float ty = gy + 90 * d;
            kit.button("pxClear", tx, ty, tw, bh2, "Leeren", "trash", Widgets.GHOST, new Runnable() {
                public void run() {
                    setPixels(new boolean[Crosshair.GRID * Crosshair.GRID]);
                }
            });
            kit.button("pxInvert", tx, ty + bh2 + 8 * d, tw, bh2, "Umkehren", "swap", Widgets.GHOST, new Runnable() {
                public void run() {
                    boolean[] cur = xh.spec().pixels;
                    for (int i = 0; i < cur.length; i++) cur[i] = !cur[i];
                    setPixels(cur);
                }
            });
            kit.button("pxMirror", tx, ty + 2 * (bh2 + 8 * d), tw, bh2, "Spiegeln", "rotR", Widgets.GHOST, new Runnable() {
                public void run() {
                    boolean[] cur = xh.spec().pixels, out = new boolean[cur.length];
                    int n = Crosshair.GRID;
                    for (int i = 0; i < cur.length; i++) out[(i / n) * n + (n - 1 - i % n)] = cur[i];
                    setPixels(out);
                }
            });
            kit.button("pxSym", tx, ty + 3 * (bh2 + 8 * d), tw, bh2, "Symmetrisch", "grid", Widgets.GHOST, new Runnable() {
                public void run() {
                    boolean[] cur = xh.spec().pixels;
                    int n = Crosshair.GRID;
                    for (int i = 0; i < cur.length; i++) {
                        int cx = i % n, cy = i / n;
                        if (cur[i]) {
                            cur[cy * n + (n - 1 - cx)] = true;
                            cur[(n - 1 - cy) * n + cx] = true;
                            cur[(n - 1 - cy) * n + (n - 1 - cx)] = true;
                        }
                    }
                    setPixels(cur);
                }
            });
        }
    }

    private void setPixels(boolean[] px) {
        xh.pixels.set(Crosshair.pixelsHex(px));
        MyticClient.config().save();
    }

    // ------------------------------------------------------------------------------------------ Eingaben

    @Override
    protected boolean release(Input.Click event) {
        boolean r = super.release(event);
        stroke = null;
        if (pixelsDirty) {
            pixelsDirty = false;
            MyticClient.config().save();
        }
        rows.saveIfDirty();
        return r;
    }

    @Override
    public void onClose() {
        rows.saveIfDirty();
        MyticClient.config().save();
        Compat.setScreen(parent);
    }
}
