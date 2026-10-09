package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.MyticClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/** Hauptmenü des Mytic Client: Panorama, Logo, schlanke Knöpfe (Optionen und Beenden nebeneinander). */
public class MyticTitleScreen extends MyticScreen {
    private static final int BW = 190;
    private static final int BH = 20;
    private static final int GAP = 5;
    private final long openedAt = System.currentTimeMillis();
    private int top;

    private record Button(String label, int x, int y, int w, int danger) {
    }

    public MyticTitleScreen() {
        super(Component.literal(MyticClient.NAME));
    }

    @Override
    protected int pixelScale() {
        // Inhalt (Logo + Knöpfe) etwa ein Drittel der Breite, höchstens zwei Drittel der Höhe
        return fit(210, 170, large() ? 0.44 : 0.36, 0.7);
    }

    @Override
    protected void layout() {
        int block = 44 + 14 + 4 * (BH + GAP);
        top = Math.max(8, (vh - block) / 2 - 6);
    }

    private Button[] buttons() {
        int x = vw / 2 - BW / 2;
        int y = top + 58;
        int half = (BW - GAP) / 2;
        return new Button[]{
                new Button("Einzelspieler", x, y, BW, 0),
                new Button("Mehrspieler", x, y + (BH + GAP), BW, 0),
                new Button("Mods & HUD", x, y + 2 * (BH + GAP), BW, 0),
                new Button("Optionen", x, y + 3 * (BH + GAP), half, 0),
                new Button("Beenden", x + half + GAP, y + 3 * (BH + GAP), BW - half - GAP, 1),
        };
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderPanorama(g, delta);
        g.fillGradient(0, 0, width, height, 0x46140832, 0xC8070412);
    }

    /** Neon-Sonne am Horizont: Verlauf Gelb → Orange → Pink, unten mit Streifen. */
    private void drawSun(GuiGraphics g, int cx, int horizon, int r, float ease) {
        Ui.glow(g, cx - r, horizon - r, 2 * r, 2 * r, r, Ui.PINK, 6, 0.18f * ease);
        for (int dy = -r; dy < 0; dy++) {
            float f = (dy + r) / (float) r;
            if (f > 0.45f && ((dy + r) % 7) < Math.round(1 + 5 * (f - 0.45f))) continue;
            int half = (int) Math.round(Math.sqrt(r * r - (dy + 0.5) * (dy + 0.5)));
            int color = f < 0.5f ? Ui.mix(0xFFFFE66B, 0xFFFF7A59, f * 2) : Ui.mix(0xFFFF7A59, Ui.PINK, (f - 0.5f) * 2);
            g.fill(cx - half, horizon + dy, cx + half, horizon + dy + 1, Ui.alpha(color, ease * 0.9f));
        }
    }

    /** Synthwave-Boden: Horizont, nach vorn laufende Linien und Linien zum Fluchtpunkt. */
    private void drawFloor(GuiGraphics g, float ease) {
        int horizon = Math.round(vh * 0.74f);
        int depth = vh - horizon;
        if (depth < 12) return;
        drawSun(g, vw / 2, horizon, Math.min(90, vw / 7), ease);
        g.fillGradient(0, horizon, vw, vh, Ui.alpha(0xE0100626, ease), Ui.alpha(0xF5050210, ease));
        float phase = (System.currentTimeMillis() % 1600) / 1600f;
        for (int i = 0; i < 10; i++) {
            float z = (i + phase) / 10f;
            int y = horizon + Math.round(depth * z * z);
            g.fill(0, y, vw, y + 1, Ui.alpha(Ui.withAlpha(Ui.PINK, Math.round(40 + 200 * z)), ease));
        }
        int cx = vw / 2;
        for (int k = -14; k <= 14; k++) {
            int bottomX = cx + k * vw / 9;
            for (int y = horizon; y < vh; y += 2) {
                float t = (float) (y - horizon) / depth;
                int x = Math.round(cx + (bottomX - cx) * t);
                g.fill(x, y, x + 1, y + 2, Ui.alpha(Ui.withAlpha(Ui.accent2(), Math.round(30 + 170 * t)), ease));
            }
        }
        Ui.glow(g, 0, horizon, vw, 1, 0, Ui.accent2(), 4, 0.8f * ease);
        g.fill(0, horizon, vw, horizon + 1, Ui.alpha(Ui.accent2(), ease));
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 450f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        drawFloor(g, ease);

        // Logo: doppelte Pixelgröße, ein sauberer Schatten, Akzentlinie darunter
        float logoScale = 3f;
        int logoW = Math.round(font.width("MYTIC CLIENT") * logoScale);
        float lx = vw / 2f - logoW / 2f;
        float ly = top + (1 - ease) * 8;
        Gfx.push(g);
        Gfx.translate(g, lx, ly);
        Gfx.scale(g, logoScale, logoScale);
        int mytic = font.width("MYTIC ");
        g.drawString(font, "MYTIC", 1, 1, Ui.alpha(0xFF1A0B33, ease), false);
        g.drawString(font, "CLIENT", mytic + 1, 1, Ui.alpha(0xFF15121E, ease), false);
        Ui.glowText(g, "MYTIC", 0, 0, Ui.alpha(Ui.accent(), ease), 1f);
        Ui.glowText(g, "CLIENT", mytic, 0, Ui.alpha(Ui.mix(0xFFFFFFFF, Ui.accent2(), 0.45f), ease), 0.9f);
        Gfx.pop(g);
        int lineW = Math.round(logoW * ease * 0.5f);
        if (lineW > 2) Ui.neonLine(g, vw / 2 - lineW / 2, Math.round(ly + 30), lineW, 2, ease);
        Ui.centered(g, "Minecraft " + Compat.versionName() + "  ·  Fabric", vw / 2, Math.round(ly + 37), Ui.alpha(Ui.MUTED, ease), false);

        for (Button b : buttons()) {
            boolean hover = Ui.inside(mouseX, mouseY, b.x, b.y, b.w, BH);
            float h = Ui.animate("title:" + b.label, hover, 16f);
            int accent = b.danger == 1 ? Ui.RED : Ui.accent();
            int border = Ui.mix(Ui.withAlpha(b.danger == 1 ? Ui.RED : Ui.accent2(), 120), accent, h);
            int fill = Ui.mix(0xD00C0818, Ui.withAlpha(accent, 225), h * 0.9f);
            if (h > 0.01f) Ui.glow(g, b.x, b.y, b.w, BH, 6, accent, 5, h * ease);
            Ui.outline(g, b.x, b.y, b.w, BH, 6, Ui.alpha(border, ease), Ui.alpha(fill, ease));
            if (h > 0.01f) Ui.hGradient(g, b.x + 6, b.y + BH - 2, b.w - 12, 1, Ui.alpha(Ui.withAlpha(Ui.accent2(), Math.round(255 * h)), ease), Ui.alpha(Ui.withAlpha(Ui.PINK, Math.round(255 * h)), ease));
            Ui.centered(g, b.label, b.x + b.w / 2, b.y + 6, Ui.alpha(0xFFFFFFFF, ease), true);
        }

        String user = minecraft.getUser().getName();
        int uw = font.width(user) + 22;
        Ui.outline(g, vw - uw - 8, 8, uw, 16, 8, 0x40FFFFFF, 0xC0120E1C);
        Ui.rect(g, vw - uw - 1, 13, 6, 6, 3, Ui.GREEN);
        Ui.text(g, user, vw - uw + 9, 12, Ui.TEXT, false);

        Ui.text(g, MyticClient.NAME + " " + MyticClient.VERSION, 6, vh - 12, Ui.MUTED, false);
        String copyright = "Copyright Mojang AB. Nicht verbreiten!";
        Ui.text(g, copyright, vw - font.width(copyright) - 6, vh - 12, Ui.MUTED, false);
    }

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        for (Button b : buttons()) {
            if (!Ui.inside(event.x(), event.y(), b.x, b.y, b.w, BH)) continue;
            switch (b.label) {
                case "Einzelspieler" -> Compat.setScreen(new SelectWorldScreen(this));
                case "Mehrspieler" -> Compat.setScreen(new JoinMultiplayerScreen(this));
                case "Mods & HUD" -> Compat.setScreen(new ModMenuScreen(this));
                case "Optionen" -> Compat.setScreen(Compat.optionsScreen(this));
                default -> minecraft.stop();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
