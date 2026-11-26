package de.myticlegacy.client.invsee;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.ScreenMouse;
import de.myticlegacy.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Glas-Panel neben einem geöffneten Inventar (/invsee oder – optional – jede Kiste): gezählte Items und Rüstung.
 * Als Liste oder als einzelne Kästchen, mit der Maus verschiebbar (Versatz wird gespeichert).
 */
public final class InvSeeOverlay {
    private static final Map<AbstractContainerScreen<?>, InvSeeOverlay> ACTIVE = new WeakHashMap<>();
    private static final int GAP = 6;
    private final AbstractContainerScreen<?> screen;
    private final String title;
    private final boolean invsee;
    private final List<float[]> boxes = new ArrayList<>();
    private final List<String> boxKeys = new ArrayList<>();
    private String dragKey;
    private double dragMouseX, dragMouseY;
    private int dragStartX, dragStartY;

    private InvSeeOverlay(AbstractContainerScreen<?> screen, String title, boolean invsee) {
        this.screen = screen;
        this.title = title;
        this.invsee = invsee;
    }

    public static InvSeeOverlay previous(AbstractContainerScreen<?> screen) {
        return ACTIVE.get(screen);
    }

    /** An einen Bildschirm hängen (auch nach Neuaufbau, z. B. Fenstergröße geändert) */
    public static void attach(AbstractContainerScreen<?> screen, String title, boolean invsee) {
        InvSeeOverlay o = ACTIVE.computeIfAbsent(screen, s -> new InvSeeOverlay(s, title, invsee));
        ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> o.render(g, mouseX, mouseY));
        ScreenMouse.attach(screen, new ScreenMouse.Handler() {
            @Override
            public boolean down(double x, double y, int button) {
                return button == 0 && o.startDrag(x, y);
            }

            @Override
            public boolean up(double x, double y, int button) {
                return o.stopDrag();
            }
        });
        ScreenEvents.remove(screen).register(s -> o.stopDrag());
    }

    public String title() {
        return title;
    }

    public boolean isInvsee() {
        return invsee;
    }

    /** nur die Plätze des fremden Inventars (nicht das eigene) */
    List<ItemStack> containerStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            out.add(slot.getItem());
        }
        return out;
    }

    boolean hasForeignItems() {
        for (Slot slot : screen.getMenu().slots) if (!(slot.container instanceof Inventory) && slot.hasItem()) return true;
        return false;
    }

    public void captureForHud() {
        if (invsee) InvSeeModule.lastSeen(InvSeeData.snapshot(title, containerStacks()));
    }

    private void render(GuiGraphics g, int mouseX, int mouseY) {
        InvSeeModule m = InvSeeModule.get();
        if (m == null || !m.enabled() || InvSeeAutoClose.isHidden(screen)) return;
        InvSeeData.Snapshot snap = InvSeeData.snapshot(title, containerStacks());
        if (invsee) InvSeeModule.lastSeen(snap);
        if (!m.summary.get()) return;
        List<InvSeeData.Row> rows = InvSeeData.itemRows(m.items.get(), m.hideMissing.get(), snap.counts());
        if (m.showArmor.get()) rows.addAll(InvSeeData.armorRows(snap.armor()));
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int left = acc.mytic$left(), right = left + acc.mytic$imageWidth(), top = acc.mytic$top();
        float scale = m.overlayScale.floatValue() / 100f;
        float k = Compat.guiScale() * scale;
        int bg = (int) Math.round(m.backgroundOpacity.get());
        boolean list = m.layout.is("Liste");
        int room = Math.max(left, screen.width - right) - GAP;
        // Namen nur, wenn genug Platz ist
        boolean names = m.itemNames.get() && neededWidth(rows, true, list, k) * scale <= room;
        int needed = Math.round(neededWidth(rows, names, list, k) * scale);
        boolean rightSide = left - GAP - needed < 0 && screen.width - right - GAP >= needed;
        int anchorX = rightSide ? right + GAP + needed : left - GAP;
        if (dragKey != null) updateDrag(mouseX, mouseY);
        boxes.clear();
        boxKeys.clear();
        Gfx.push(g);
        Gfx.raise(g, 300);
        if (list) {
            float w = InvSeePanel.width(title, invsee ? "InvSee" : null, rows, names, k);
            float h = InvSeePanel.height(rows);
            int sw = Math.round(w * scale), sh = Math.round(h * scale);
            int[] off = m.offset("list");
            int x = clampX(anchorX - sw + off[0], sw), y = clampY(top + off[1], sh);
            Gfx.push(g);
            Gfx.translate(g, x, y);
            Gfx.scale(g, scale, scale);
            InvSeePanel.drawList(g, title, invsee ? "InvSee" : null, rows, w, names, bg);
            Gfx.pop(g);
            box("list", x, y, sw, sh);
        } else {
            int y0 = top;
            for (InvSeeData.Row row : rows) {
                float w = InvSeePanel.separateWidth(row, names, k);
                int sw = Math.round(w * scale), sh = Math.round(InvSeePanel.SEP_H * scale);
                int[] off = m.offset(row.key());
                int x = clampX(anchorX - sw + off[0], sw), y = clampY(y0 + off[1], sh);
                Gfx.push(g);
                Gfx.translate(g, x, y);
                Gfx.scale(g, scale, scale);
                InvSeePanel.drawSeparate(g, row, w, names, bg);
                Gfx.pop(g);
                box(row.key(), x, y, sw, sh);
                y0 += sh + 2;
            }
        }
        Gfx.pop(g);
    }

    private void box(String key, int x, int y, int w, int h) {
        boxes.add(new float[]{x, y, w, h});
        boxKeys.add(key);
    }

    private float neededWidth(List<InvSeeData.Row> rows, boolean names, boolean list, float k) {
        if (list) return InvSeePanel.width(title, invsee ? "InvSee" : null, rows, names, k);
        float w = 0;
        for (InvSeeData.Row r : rows) w = Math.max(w, InvSeePanel.separateWidth(r, names, k));
        return w;
    }

    private int clampX(int x, int w) {
        return Math.max(0, Math.min(x, screen.width - w));
    }

    private int clampY(int y, int h) {
        return Math.max(0, Math.min(y, screen.height - h));
    }

    private boolean startDrag(double mx, double my) {
        InvSeeModule m = InvSeeModule.get();
        if (m == null) return false;
        for (int i = boxes.size() - 1; i >= 0; i--) {
            float[] b = boxes.get(i);
            if (mx < b[0] || my < b[1] || mx >= b[0] + b[2] || my >= b[1] + b[3]) continue;
            dragKey = boxKeys.get(i);
            dragMouseX = mx;
            dragMouseY = my;
            int[] off = m.offset(dragKey);
            dragStartX = off[0];
            dragStartY = off[1];
            return true;
        }
        return false;
    }

    private void updateDrag(double mx, double my) {
        InvSeeModule m = InvSeeModule.get();
        if (m != null) m.setOffset(dragKey, dragStartX + (int) Math.round(mx - dragMouseX), dragStartY + (int) Math.round(my - dragMouseY), false);
    }

    private boolean stopDrag() {
        if (dragKey == null) return false;
        dragKey = null;
        InvSeeModule m = InvSeeModule.get();
        if (m != null) m.saveOffsets();
        return true;
    }
}
