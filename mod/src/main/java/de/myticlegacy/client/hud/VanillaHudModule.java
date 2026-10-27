package de.myticlegacy.client.hud;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.gui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Vanilla-Anzeige (Bossleiste, Scoreboard, Titel, Aktionsleiste, Effekte) im HUD-Editor verschieben und skalieren.
 * Minecraft zeichnet weiter selbst; ein Mixin verschiebt und skaliert dabei die Zeichenfläche so, dass der Ankerpunkt
 * der Vanilla-Anzeige (z. B. „oben Mitte“ bei der Bossleiste) auf denselben Punkt dieses Kastens fällt.
 * Aus = alles wie bei Vanilla. Im Editor wird ein Platzhalter gezeigt.
 */
public class VanillaHudModule extends HudModule {
    public static VanillaHudModule bossbar, scoreboard, title, actionbar, effects;

    private final int w, h;
    private final float ax, ay;
    private final Anchor anchor;
    private final Sample sample;

    interface Anchor {
        /** Ankerpunkt der Vanilla-Anzeige bei gegebener Bildschirmgröße (GUI-Einheiten) */
        float[] at(int sw, int sh);
    }

    interface Sample {
        void draw(GuiGraphics g, int w, int h);
    }

    private VanillaHudModule(String id, String name, String description, Supplier<Item> icon, int w, int h, float ax, float ay, Anchor anchor, Sample sample) {
        super(id, name, description, icon, false, 0, 0);
        this.w = w;
        this.h = h;
        this.ax = ax;
        this.ay = ay;
        this.anchor = anchor;
        this.sample = sample;
    }

    public static void createAll() {
        bossbar = new VanillaHudModule("vanilla_bossbar", "Bossleiste", "Vanilla-Bossleisten verschieben und skalieren", () -> Items.DRAGON_HEAD,
                182, 19, 0.5f, 0f, (sw, sh) -> new float[]{sw / 2f, 2}, (g, w, h) -> {
            Ui.centered(g, "Ender-Drache", w / 2, 1, 0xFFFFFFFF, true);
            g.fill(0, 11, w, 16, 0xFF3A0F4A);
            g.fill(0, 11, w * 2 / 3, 16, 0xFFD24BFF);
        });
        scoreboard = new VanillaHudModule("vanilla_scoreboard", "Scoreboard", "Vanilla-Scoreboard (rechts) verschieben und skalieren", () -> Items.OAK_SIGN,
                100, 63, 1f, 0.5f, (sw, sh) -> new float[]{sw - 1, sh / 2f}, (g, w, h) -> {
            g.fill(0, 0, w, 9, 0x66000000);
            g.fill(0, 9, w, h, 0x4D000000);
            Ui.centered(g, "Scoreboard", w / 2, 1, 0xFFFFFFFF, false);
            String[] lines = {"Spieler: 12", "Kills: 4", "Team: Rot", "Zeit: 08:15", "mytic.gg"};
            for (int i = 0; i < lines.length; i++) {
                Ui.text(g, lines[i], 2, 10 + i * 9, 0xFFFFFFFF, false);
                Ui.text(g, String.valueOf(5 - i), w - 8, 10 + i * 9, 0xFFFF5555, false);
            }
        });
        title = new VanillaHudModule("vanilla_title", "Titel", "Vanilla-Titel und -Untertitel verschieben und skalieren", () -> Items.NAME_TAG,
                220, 70, 0.5f, 42f / 70f, (sw, sh) -> new float[]{sw / 2f, sh / 2f}, (g, w, h) -> {
            Gfx.push(g);
            Gfx.translate(g, w / 2f, 4);
            Gfx.scale(g, 4, 4);
            Ui.centered(g, "Titel", 0, 0, 0xFFFFFFFF, true);
            Gfx.pop(g);
            Gfx.push(g);
            Gfx.translate(g, w / 2f, 48);
            Gfx.scale(g, 2, 2);
            Ui.centered(g, "Untertitel", 0, 0, 0xFFDDDDDD, true);
            Gfx.pop(g);
        });
        actionbar = new VanillaHudModule("vanilla_actionbar", "Aktionsleiste", "Vanilla-Text über der Hotbar verschieben und skalieren", () -> Items.WRITABLE_BOOK,
                182, 12, 0.5f, 0.5f, (sw, sh) -> new float[]{sw / 2f, sh - 68}, (g, w, h) ->
                Ui.centered(g, "Aktionsleiste", w / 2, 2, 0xFFFFFFFF, true));
        effects = new VanillaHudModule("vanilla_effects", "Effekte (Vanilla)", "Vanilla-Effektsymbole (oben rechts) verschieben und skalieren", () -> Items.POTION,
                100, 52, 1f, 0f, (sw, sh) -> new float[]{sw, 0}, (g, w, h) -> {
            int[] colors = {0xFF7CAFC6, 0xFF932423, 0xFFF8F8F8, 0xFF4E9331};
            for (int i = 0; i < 4; i++) {
                int x = w - 25 * (i + 1), y = i == 3 ? 27 : 1;
                g.fill(x, y, x + 24, y + 24, 0xCC1E1E1E);
                g.fill(x + 6, y + 6, x + 18, y + 18, colors[i]);
            }
        });
    }

    public static VanillaHudModule[] all() {
        return new VanillaHudModule[]{bossbar, scoreboard, title, actionbar, effects};
    }

    @Override
    protected boolean defaultBackground() {
        return false;
    }

    @Override
    public int baseWidth() {
        return w;
    }

    @Override
    public int baseHeight() {
        return h;
    }

    /** Im Spiel zeichnet Minecraft selbst – hier nur der Platzhalter im Editor */
    @Override
    public boolean hasContent() {
        return false;
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        if (!preview) return;
        g.fill(0, 0, w, h, 0x22FFFFFF);
        sample.draw(g, w, h);
    }

    private float[] vanillaAnchor() {
        var win = Minecraft.getInstance().getWindow();
        return anchor.at(win.getGuiScaledWidth(), win.getGuiScaledHeight());
    }

    /** Ohne gespeicherte Position: genau dort, wo Vanilla zeichnet */
    @Override
    public int x() {
        if (MyticClient.config().positions.get(id) == null) return Math.round(vanillaAnchor()[0] - ax * width());
        return super.x();
    }

    @Override
    public int y() {
        if (MyticClient.config().positions.get(id) == null) return Math.round(vanillaAnchor()[1] - ay * height());
        return super.y();
    }

    /** Vom Mixin am Anfang der Vanilla-Zeichnung: Zeichenfläche verschieben/skalieren (immer mit end() paaren) */
    public static void begin(GuiGraphics g, VanillaHudModule m) {
        Gfx.push(g);
        if (m == null || !m.enabled()) return;
        float s = m.scaleFactor();
        float[] d = m.vanillaAnchor();
        float mx = m.x() + m.ax * m.width(), my = m.y() + m.ay * m.height();
        Gfx.translate(g, mx - s * d[0], my - s * d[1]);
        Gfx.scale(g, s, s);
    }

    public static void end(GuiGraphics g) {
        Gfx.pop(g);
    }
}
