package de.myticlegacy.client.hud;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Warnungen mit Ton und großer Anzeige: Rüstung fast kaputt, wenig Leben, Totem verbraucht (und wie viele noch da sind).
 * Jede Warnung kommt einmal, bis sich die Lage wieder bessert (Rüstung repariert/getauscht, Leben wieder höher).
 */
public class WarningsModule extends HudModule {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES = {"Helm", "Brustplatte", "Hose", "Schuhe"};

    private final BoolSetting armor;
    private final SliderSetting armorPercent;
    private final BoolSetting health;
    private final SliderSetting healthHearts;
    private final BoolSetting totem;
    private final BoolSetting sound;
    private final SliderSetting seconds;
    private final ColorSetting alertColor;

    private String text = "";
    private long until;
    private final ItemStack[] warnedArmor = new ItemStack[ARMOR.length];
    private boolean lowHealthWarned;
    private int lastTotems = -1;

    public WarningsModule() {
        super("warnings", "Warnungen", "Ton und Hinweis bei kaputter Rüstung, wenig Leben und verbrauchtem Totem",
                () -> Items.BELL, false, -1, -1);
        armor = new BoolSetting(this, "armor", "Rüstung fast kaputt", true);
        armorPercent = new SliderSetting(this, "armorPercent", "Ab Haltbarkeit", 5, 50, 5, 15, " %");
        health = new BoolSetting(this, "health", "Wenig Leben", true);
        healthHearts = new SliderSetting(this, "healthHearts", "Ab Herzen", 1, 10, 0.5, 4, " ♥");
        totem = new BoolSetting(this, "totem", "Totem verbraucht / letztes Totem", true);
        sound = new BoolSetting(this, "sound", "Ton", true);
        seconds = new SliderSetting(this, "seconds", "Anzeigedauer", 1, 5, 0.5, 2.5, " s");
        alertColor = new ColorSetting(this, "alertColor", "Warnfarbe", 0xFFFF5C72);
    }

    @Override
    protected boolean defaultBackground() {
        return true;
    }

    // ohne gespeicherte Position: mittig, etwas über dem Fadenkreuz
    @Override
    public int x() {
        if (MyticClient.config().positions.containsKey(id)) return super.x();
        return Math.max(0, (Minecraft.getInstance().getWindow().getGuiScaledWidth() - width()) / 2);
    }

    @Override
    public int y() {
        if (MyticClient.config().positions.containsKey(id)) return super.y();
        return Math.max(0, Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2 - 50);
    }

    public void tick(Minecraft mc) {
        var player = mc.player;
        if (!enabled() || player == null || mc.level == null) {
            lastTotems = -1;
            lowHealthWarned = false;
            java.util.Arrays.fill(warnedArmor, null);
            return;
        }
        if (armor.get()) {
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack stack = player.getItemBySlot(ARMOR[i]);
                if (stack.isEmpty() || !stack.isDamageableItem()) {
                    warnedArmor[i] = null;
                    continue;
                }
                int percent = Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
                if (percent <= armorPercent.get() && warnedArmor[i] != stack) {
                    warnedArmor[i] = stack;
                    alert(ARMOR_NAMES[i] + " fast kaputt (" + percent + " %)", false);
                } else if (percent > armorPercent.get() + 5) {
                    warnedArmor[i] = null; // repariert oder getauscht
                }
            }
        }
        if (health.get()) {
            float limit = (float) (healthHearts.get() * 2);
            float hp = player.getHealth();
            if (hp > 0 && hp <= limit && !lowHealthWarned) {
                lowHealthWarned = true;
                alert("Wenig Leben: " + format(hp / 2) + " ♥", true);
            } else if (hp > limit + 2) {
                lowHealthWarned = false;
            }
        }
        int totems = countTotems(player.getInventory());
        // weniger Totems, während man gerade Schaden bekommt = verbraucht (nicht weggeworfen oder abgelegt)
        if (totem.get() && lastTotems >= 0 && totems < lastTotems && player.hurtTime > 0) {
            alert(totems == 0 ? "Totem weg – keins mehr!" : totems == 1 ? "Totem weg – letztes Totem!" : "Totem weg – noch " + totems, totems <= 1);
        }
        lastTotems = totems;
    }

    private static int countTotems(net.minecraft.world.entity.player.Inventory inventory) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) n += stack.getCount();
        }
        return n;
    }

    private static String format(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10f);
    }

    private void alert(String message, boolean urgent) {
        text = message;
        until = System.currentTimeMillis() + Math.round(seconds.get() * 1000);
        if (sound.get()) {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING, urgent ? 0.6f : 1.2f));
        }
    }

    @Override
    public boolean hasContent() {
        return System.currentTimeMillis() < until;
    }

    private String shown(boolean preview) {
        return hasContent() ? text : preview ? "Totem weg – letztes Totem!" : "";
    }

    @Override
    public int baseWidth() {
        return Minecraft.getInstance().font.width(shown(true)) + 12;
    }

    @Override
    public int baseHeight() {
        return 16;
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        String s = shown(preview);
        if (s.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int w = font.width(s) + 12;
        panel(g, w, baseHeight());
        // in der letzten halben Sekunde ausblenden
        long left = until - System.currentTimeMillis();
        int alpha = preview || left > 500 ? 255 : Math.max(20, (int) (255 * left / 500));
        int c = (alertColor.get() & 0xFFFFFF) | alpha << 24;
        g.drawString(font, s, 6, 4, c, textShadow());
    }
}
