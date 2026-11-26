package de.myticlegacy.client.invsee;

import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.compat.Gfx;
import de.myticlegacy.client.compat.GuiGraphics;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Glas-Panel neben einem geöffneten Inventar (1.8.9), als Liste oder einzeln, mit der Maus verschiebbar. */
public final class InvSeeOverlay {
    private static final Map<GuiContainer, InvSeeOverlay> ACTIVE = new WeakHashMap<GuiContainer, InvSeeOverlay>();
    private static final int GAP = 6;
    private final GuiContainer screen;
    private final String title;
    private final boolean invsee;
    private final List<float[]> boxes = new ArrayList<float[]>();
    private final List<String> boxKeys = new ArrayList<String>();
    private String dragKey;
    private double dragMouseX, dragMouseY;
    private int dragStartX, dragStartY;

    private InvSeeOverlay(GuiContainer screen, String title, boolean invsee) {
        this.screen = screen;
        this.title = title;
        this.invsee = invsee;
    }

    public static InvSeeOverlay of(GuiContainer screen) {
        return ACTIVE.get(screen);
    }

    public static InvSeeOverlay attach(GuiContainer screen, String title, boolean invsee) {
        InvSeeOverlay o = ACTIVE.get(screen);
        if (o == null) {
            o = new InvSeeOverlay(screen, title, invsee);
            ACTIVE.put(screen, o);
        }
        return o;
    }

    private static int field(GuiContainer gui, String mcp, String srg) {
        try {
            Integer v = ReflectionHelper.getPrivateValue(GuiContainer.class, gui, mcp, srg);
            return v == null ? 0 : v;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    List<ItemStack> containerStacks() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (Slot slot : screen.inventorySlots.inventorySlots) {
            if (slot.inventory instanceof InventoryPlayer) continue;
            out.add(slot.getStack());
        }
        return out;
    }

    boolean hasForeignItems() {
        for (Slot slot : screen.inventorySlots.inventorySlots) if (!(slot.inventory instanceof InventoryPlayer) && slot.getHasStack()) return true;
        return false;
    }

    public void captureForHud() {
        if (invsee) InvSeeModule.lastSeen(InvSeeData.snapshot(title, containerStacks()));
    }

    public void render(int mouseX, int mouseY) {
        InvSeeModule m = InvSeeModule.get();
        if (m == null || !m.enabled() || InvSeeAutoClose.isHidden(screen)) return;
        InvSeeData.Snapshot snap = InvSeeData.snapshot(title, containerStacks());
        if (invsee) InvSeeModule.lastSeen(snap);
        if (!m.summary.get()) return;
        List<InvSeeData.Row> rows = InvSeeData.itemRows(m.items.get(), m.hideMissing.get(), snap.stacks);
        if (m.showArmor.get()) rows.addAll(InvSeeData.armorRows(snap.armor));
        int left = field(screen, "guiLeft", "field_147003_i"), top = field(screen, "guiTop", "field_147009_r");
        int right = left + field(screen, "xSize", "field_146999_f");
        float scale = m.overlayScale.floatValue() / 100f;
        float k = Compat.guiScale() * scale;
        int bg = (int) Math.round(m.backgroundOpacity.get());
        boolean list = m.layout.is("Liste");
        int room = Math.max(left, screen.width - right) - GAP;
        boolean names = m.itemNames.get() && neededWidth(rows, true, list, k) * scale <= room;
        int needed = Math.round(neededWidth(rows, names, list, k) * scale);
        boolean rightSide = left - GAP - needed < 0 && screen.width - right - GAP >= needed;
        int anchorX = rightSide ? right + GAP + needed : left - GAP;
        if (dragKey != null) updateDrag(mouseX, mouseY);
        boxes.clear();
        boxKeys.clear();
        GuiGraphics g = new GuiGraphics();
        Gfx.push(g);
        net.minecraft.client.renderer.GlStateManager.translate(0, 0, 300);
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
                int[] off = m.offset(row.key);
                int x = clampX(anchorX - sw + off[0], sw), y = clampY(y0 + off[1], sh);
                Gfx.push(g);
                Gfx.translate(g, x, y);
                Gfx.scale(g, scale, scale);
                InvSeePanel.drawSeparate(g, row, w, names, bg);
                Gfx.pop(g);
                box(row.key, x, y, sw, sh);
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

    public boolean startDrag(double mx, double my) {
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
        if (m != null) m.setOffset(dragKey, dragStartX + (int) Math.round(mx - dragMouseX), dragStartY + (int) Math.round(my - dragMouseY));
    }

    public boolean stopDrag() {
        if (dragKey == null) return false;
        dragKey = null;
        de.myticlegacy.client.MyticClient.config().save();
        return true;
    }

    public String title() {
        return title;
    }
}
