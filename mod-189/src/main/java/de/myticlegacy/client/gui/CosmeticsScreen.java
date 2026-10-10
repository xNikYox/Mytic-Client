package de.myticlegacy.client.gui;

import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.module.Module;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

/** Cosmetics (Umhänge, Flügel …) – noch leer, kommt in einem späteren Update. */
public class CosmeticsScreen extends PanelScreen {
    private ItemStack icon;

    public CosmeticsScreen() {
        super("Cosmetics");
    }

    @Override
    protected String title() {
        return "COSMETICS";
    }

    @Override
    protected void drawContent(GuiGraphics g, int mouseX, int mouseY, int headerX) {
        int cx = px + pw / 2;
        int cy = py + HEADER + (ph - HEADER - FOOTER) / 2;
        if (icon == null) icon = Module.stack(Items.leather_chestplate);
        Gfx.push(g);
        Gfx.translate(g, cx - 24, cy - 52);
        Gfx.scale(g, 3f, 3f);
        g.renderItem(icon, 0, 0);
        Gfx.pop(g);
        Ui.scaled(g, "Bald verfügbar", cx - font.width("Bald verfügbar") * 0.75f, cy + 4, 1.5f, Ui.TEXT, true);
        Ui.centered(g, "Umhänge, Flügel, Hüte und mehr kommen in einem der nächsten Updates.", cx, cy + 24, Ui.MUTED, false);
        Ui.centered(g, "ESC → zurück zur Übersicht", cx, py + ph - FOOTER + 10, Ui.withAlpha(Ui.MUTED, 160), false);
    }
}
