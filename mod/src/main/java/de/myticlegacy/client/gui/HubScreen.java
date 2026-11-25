package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.gui.ui.Bake;
import de.myticlegacy.client.gui.ui.Draw;
import de.myticlegacy.client.gui.ui.Icons;
import de.myticlegacy.client.gui.ui.Motion;
import de.myticlegacy.client.gui.ui.Tokens;
import de.myticlegacy.client.module.Module;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Übersicht (Rechts-Shift) im neuen Design: Wortmarke mit Emblem, darunter fünf Glas-Kacheln – Freunde, Mods,
 * Kosmetik, Screenshots, Statistiken – mit Linien-Icon (Freunde: eigener Kopf), Untertitel und Tasten 1–5.
 */
public class HubScreen extends DesignScreen {
    private static final String[] LABELS = {"Freunde", "Mods", "Kosmetik", "Screenshots", "Statistiken"};
    private static final String[] ICONS = {"users", "grid", "sparkle", "camera", "chart"};
    private static final int N = LABELS.length;
    private final int shotCount = Shots.list().size(); // einmal beim Öffnen zählen, nicht in jedem Bild

    public HubScreen() {
        super("hub", MyticClient.NAME);
        de.myticlegacy.client.stats.Stats.refresh(false);
    }

    @Override
    protected float factor() {
        return Math.max(0.3f, Math.min(vw / 1920f, vh / 1080f) * (large() ? 1.15f : 1f));
    }

    private String subtitle(int i) {
        if (i == 0) {
            if (!Friends.available()) return "Nur über den Launcher";
            if (!Friends.loaded()) return "Lädt …";
            return Friends.onlineCount() + " online";
        }
        if (i == 1) {
            int on = 0;
            for (Module m : MyticClient.MODULES) if (m.enabled()) on++;
            return on + " aktiv";
        }
        if (i == 3) return shotCount + (shotCount == 1 ? " Bild" : " Bilder");
        if (i == 4) {
            if (!Friends.available()) return "Nur über den Launcher";
            de.myticlegacy.client.stats.Stats.Data stats = de.myticlegacy.client.stats.Stats.data();
            return stats == null ? "Lädt …" : "Heute " + de.myticlegacy.client.stats.Stats.duration(stats.today);
        }
        de.myticlegacy.client.cosmetics.CosmeticsCore.Cape cape = de.myticlegacy.client.cosmetics.CosmeticsCore.byId(de.myticlegacy.client.cosmetics.CosmeticsCore.selected());
        return cape != null ? cape.name : "Kein Umhang";
    }

    @Override
    protected void frame(GuiGraphics g) {
        long now = System.currentTimeMillis();
        boolean anim = animate();
        float in = anim ? Motion.ease(Math.min(1f, (now - openedAt) / 450f)) : 1f;
        float tw = 224 * d, th = 252 * d, gap = 24 * d;
        float total = N * tw + (N - 1) * gap;
        float x0 = Math.round((vw - total) / 2f), ty = Math.round(vh / 2f - th / 2f + 40 * d);

        // Wortmarke mit Emblem
        int px = Math.max(10, Math.round(76 * d));
        Bake.Baked word = Bake.text("Mytic", px, -0.045f, 0xFFFFFFFF, new Bake.Shadow(0, 6 * d, 30 * d, 0x739B5CFF), new Bake.Shadow(0, 2 * d, 0, 0x40000000));
        int es = Math.max(8, Math.round(60 * d));
        float wordW = word == null ? 0 : word.extra;
        float lw = es + 18 * d + wordW, lx = vw / 2f - lw / 2f, rowTop = ty - 150 * d + (1 - in) * -8 * d;
        int tint = Draw.alpha(0xFFFFFFFF, in);
        float k = Draw.begin(g);
        Draw.pxBaked(g, Bake.emblem(es), Math.round(lx), Math.round(rowTop + 38 * d - es / 2f), tint);
        if (word != null) Draw.pxBaked(g, word, Math.round(lx + es + 18 * d), Math.round(rowTop + 38 * d - 0.363f * px), tint);
        Draw.end(g);

        for (int i = 0; i < N; i++) {
            final int idx = i;
            float appear = anim ? Motion.ease(Math.max(0f, Math.min(1f, (now - openedAt - i * 50) / 350f))) : 1f;
            float x = x0 + i * (tw + gap);
            String id = "tile" + i;
            boolean hv = kit.hit(id, x, ty, tw, th, () -> open(idx));
            float hvT = Motion.to(kit.key(id) + ":lift", hv, Tokens.MS_HOVER);
            float y = ty + (1 - appear) * 16 * d - 4 * d * hvT + (kit.pressed(id) ? 2 * d : 0);
            Draw.shadow(g, x, y + 24 * d, tw, th, 20 * d, 60 * d, -20 * d, Draw.alpha(0x8C000000, appear));
            Draw.glass(g, x, y, tw, th, 20 * d, Draw.mix(Tokens.GLASS_STRONG, 0xD11E182E, hvT), Draw.mix(Tokens.LINE, Tokens.ACC_LINE, hvT));

            // Symbol
            float is = 76 * d, ix = x + (tw - is) / 2f, iy = y + 40 * d;
            Friends.Friend me = Friends.me();
            if (i == 0 && me != null && me.head != null) {
                kit.head(me.head, me.name, ix + 4 * d, iy + 4 * d, is - 8 * d, Friends.onlineCount() > 0 ? Tokens.OK : 0, false);
            } else {
                Draw.rect(g, ix, iy, is, is, 22 * d, 0x14FFFFFF);
                Draw.accentRect(g, ix, iy, is, is, 22 * d, 0.30f + 0.25f * hvT);
                Draw.ring(g, ix, iy, is, is, 22 * d, 1, 0x1AFFFFFF);
                Icons.draw(g, ICONS[i], ix + (is - 34 * d) / 2f, iy + (is - 34 * d) / 2f, 34 * d, 0xFFFFFFFF);
            }
            kit.textCenter(LABELS[i], x + tw / 2f, y + 152 * d, 18, Tokens.SEMIBOLD, Tokens.TEXT);
            String sub = subtitle(i);
            boolean green = i == 0 && Friends.available() && Friends.onlineCount() > 0;
            kit.textCenter(de.myticlegacy.client.gui.ui.Type.ellipsize(g, sub, tw - 28 * d, 14 * d, Tokens.MEDIUM), x + tw / 2f, y + 180 * d, 14, Tokens.MEDIUM,
                    green ? Draw.mix(Tokens.OK, Tokens.TEXT, 0.3f) : Tokens.MUTED);
            // Taste
            kit.kbd(String.valueOf(i + 1), x + tw / 2f - 14 * d, y + th - 46 * d, 26 * d, false, hv);
            // Freundschaftsanfragen
            if (i == 0 && !Friends.incoming().isEmpty()) {
                String n = String.valueOf(Friends.incoming().size());
                float bh = 24 * d, bw = Math.max(bh, kit.width(n, 12.5f, Tokens.SEMIBOLD) + 14 * d);
                Draw.rect(g, x + tw - bw - 14 * d, y + 14 * d, bw, bh, bh / 2, Tokens.BAD);
                kit.textCenter(n, x + tw - bw / 2 - 14 * d, y + 14 * d + bh / 2, 12.5f, Tokens.SEMIBOLD, 0xFFFFFFFF);
                if (hv) kit.tooltip(n + (n.equals("1") ? " Freundschaftsanfrage" : " Freundschaftsanfragen"), x + tw / 2f, y + th + 10 * d);
            }
        }

        // Hinweise
        float hy = ty + th + 44 * d, fs = 13.5f;
        String a = "Strg + Rechts-Shift öffnet direkt die Mods";
        float kw = 0;
        float aw = kit.width(a, fs, Tokens.MEDIUM);
        String b = "Rechts-Shift schließt";
        float bw2 = kit.width(b, fs, Tokens.MEDIUM);
        float tot = aw + 28 * d + bw2;
        float hx = vw / 2f - tot / 2f + kw;
        kit.textMid(a, hx, hy, fs, Tokens.MEDIUM, 0x99FFFFFF, 0);
        Draw.circle(g, hx + aw + 14 * d, hy, 2 * d, 0x66FFFFFF);
        kit.textMid(b, hx + aw + 28 * d, hy, fs, Tokens.MEDIUM, 0x99FFFFFF, 0);
    }

    private void open(int i) {
        if (i == 0) Compat.setScreen(new FriendsScreen());
        else if (i == 1) Compat.setScreen(new ModMenuScreen(null));
        else if (i == 2) Compat.setScreen(new CosmeticsScreen());
        else if (i == 3) Compat.setScreen(new ScreenshotsScreen());
        else Compat.setScreen(new de.myticlegacy.client.stats.StatsScreen());
    }

    @Override
    protected boolean shortcut(int key, boolean ctrl) {
        if (key == InputConstants.KEY_1 || key == InputConstants.KEY_2 || key == InputConstants.KEY_3 || key == InputConstants.KEY_4 || key == InputConstants.KEY_5) {
            open(key - InputConstants.KEY_1);
            return true;
        }
        return false;
    }

    @Override
    protected void back() {
        onClose();
    }
}
