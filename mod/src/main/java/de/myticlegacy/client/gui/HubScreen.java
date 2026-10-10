package de.myticlegacy.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.friends.Friends;
import de.myticlegacy.client.module.Module;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Übersicht wie bei NRC (Rechts-Shift): drei große Knöpfe – Freunde, Mods, Cosmetics. */
public class HubScreen extends MyticScreen {
    private static final int TILE_W = 96;
    private static final int TILE_H = 108;
    private static final int GAP = 12;
    private static final String[] LABELS = {"Freunde", "Mods", "Cosmetics"};
    private final long openedAt = System.currentTimeMillis();
    private int tx, ty;

    public HubScreen() {
        super(Component.literal(MyticClient.NAME));
    }

    @Override
    protected int pixelScale() {
        return fit(3 * TILE_W + 2 * GAP + 40, TILE_H + 90, 0.62, 0.62);
    }

    @Override
    protected void layout() {
        tx = (vw - (3 * TILE_W + 2 * GAP)) / 2;
        ty = (vh - TILE_H) / 2 + 6;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        String mode = MyticClient.theme.menuBackground.get();
        if (mode.equals("Unschärfe")) super.renderBackground(g, mouseX, mouseY, delta);
        else if (mode.equals("Abdunkeln") || minecraft.level == null) g.fill(0, 0, width, height, 0x99000000);
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
        return "Bald verfügbar";
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 200f);
        float ease = 1f - (1f - open) * (1f - open) * (1f - open);
        boolean lunar = Ui.lunar();

        // Logo
        float logoScale = 2f;
        int logoW = Math.round(font.width("MYTIC CLIENT") * logoScale);
        Gfx.push(g);
        Gfx.translate(g, 0, (1 - ease) * -8);
        if (lunar) {
            Ui.scaled(g, "MYTIC", vw / 2f - logoW / 2f, ty - 44, logoScale, 0xFFFFFFFF, true);
            Ui.scaled(g, "CLIENT", vw / 2f - logoW / 2f + font.width("MYTIC ") * logoScale, ty - 44, logoScale, 0xFFB4B4B4, true);
        } else {
            Ui.logo(g, vw / 2f - logoW / 2f, ty - 44, logoScale);
        }
        Gfx.pop(g);

        for (int i = 0; i < 3; i++) {
            int x = tx + i * (TILE_W + GAP);
            float appear = Math.max(0f, Math.min(1f, (System.currentTimeMillis() - openedAt - i * 40) / 200f));
            float e = 1f - (1f - appear) * (1f - appear) * (1f - appear);
            int y = ty + Math.round((1 - e) * 14);
            boolean over = Ui.inside(mouseX, mouseY, x, y, TILE_W, TILE_H);
            float hv = Ui.animate("hub:" + i, over, 16f);
            y -= Math.round(hv * 2);

            if (lunar) {
                Ui.rect(g, x - 2, y + 2, TILE_W + 4, TILE_H + 2, 8, 0x45000000);
                Ui.outline(g, x, y, TILE_W, TILE_H, 6, Ui.mix(0x2EFFFFFF, 0x70FFFFFF, hv), Ui.mix(0xF2181818, 0xF2262626, hv));
            } else {
                Ui.glow(g, x, y, TILE_W, TILE_H, 8, i == 1 ? Ui.accent() : Ui.accent2(), 5, 0.35f + hv * 0.65f);
                Ui.neonPanel(g, x, y, TILE_W, TILE_H, 8, 1f);
                if (hv > 0.02f) Ui.corners(g, x, y, TILE_W, TILE_H, 8, Ui.alpha(Ui.accent(), hv), Ui.alpha(Ui.accent2(), hv));
            }

            // Symbol
            int cx = x + TILE_W / 2;
            int iy = y + 18;
            if (i == 0 && Friends.me() != null && Friends.me().head != null) {
                FriendsScreen.drawHead(g, Friends.me().head, null, cx - 18, iy, 36);
            } else {
                Gfx.push(g);
                Gfx.translate(g, cx - 18, iy);
                Gfx.scale(g, 2.25f, 2.25f);
                g.renderItem(icon(i), 0, 0);
                Gfx.pop(g);
            }

            Ui.centered(g, LABELS[i], cx, y + 66, Ui.TEXT, true);
            String sub = subtitle(i);
            Ui.scaled(g, sub, cx - font.width(sub) * 0.4f, y + 80, 0.8f, i == 0 && Friends.onlineCount() > 0 ? (lunar ? Ui.L_GREEN : Ui.GREEN) : Ui.MUTED, false);
            if (i == 2) {
                // "BALD"-Plakette
                int bw = font.width("BALD") + 8;
                Ui.rect(g, x + TILE_W - bw - 6, y + 6, bw, 11, 3, lunar ? 0xFF3A3A3A : Ui.withAlpha(Ui.PINK, 200));
                Ui.scaled(g, "BALD", x + TILE_W - bw - 2, y + 8.5f, 0.75f, 0xFFFFFFFF, false);
            }
            if (i == 0 && !Friends.incoming().isEmpty()) {
                String n = String.valueOf(Friends.incoming().size());
                int bw = Math.max(12, font.width(n) + 6);
                Ui.rect(g, x + TILE_W - bw - 6, y + 6, bw, 12, 6, Ui.RED);
                Ui.centered(g, n, x + TILE_W - bw / 2 - 6, y + 8, 0xFFFFFFFF, false);
            }
            Ui.scaled(g, String.valueOf(i + 1), x + 6, y + 6, 0.75f, Ui.withAlpha(Ui.MUTED, 140), false);
        }

        String hint = "Strg + Rechts-Shift öffnet direkt die Mods";
        Ui.scaled(g, hint, vw / 2f - font.width(hint) * 0.4f, ty + TILE_H + 18, 0.8f, Ui.withAlpha(Ui.MUTED, 200), false);
    }

    private static ItemStack[] icons;

    private static ItemStack icon(int i) {
        if (icons == null || icons[i].isEmpty()) icons = new ItemStack[]{Module.stack(Items.PLAYER_HEAD), Module.stack(Items.COMPARATOR), Module.stack(Items.ELYTRA)};
        return icons[i];
    }

    private void open(int i) {
        if (i == 0) Compat.setScreen(new FriendsScreen());
        else if (i == 1) Compat.setScreen(new ModMenuScreen(null));
        else Compat.setScreen(new CosmeticsScreen());
    }

    @Override
    protected boolean click(Input.Click event, boolean doubleClick) {
        if (event.button() != 0) return false;
        for (int i = 0; i < 3; i++) {
            if (Ui.inside(event.x(), event.y(), tx + i * (TILE_W + GAP), ty, TILE_W, TILE_H)) {
                open(i);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onKey(Input.Key event) {
        int key = event.key();
        if (key == InputConstants.KEY_1 || key == InputConstants.KEY_2 || key == InputConstants.KEY_3) {
            open(key - InputConstants.KEY_1);
            return true;
        }
        if (key == InputConstants.KEY_RSHIFT) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
