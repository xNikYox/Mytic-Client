package de.myticlegacy.client.hud;

import de.myticlegacy.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Aktive Trankeffekte mit Stufe und Restzeit; läuft ein Effekt bald ab, blinkt er. */
public class PotionModule extends HudModule {
    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    private final BoolSetting blink;

    public PotionModule() {
        super("potions", "Tränke", "Aktive Effekte mit Restzeit", () -> Items.POTION, true, 4, 262);
        blink = new BoolSetting(this, "blink", "Blinken kurz vor Ablauf", true);
    }

    private record Line(String name, String time, int color, boolean ending) {
    }

    private List<Line> lines(boolean preview) {
        var mc = Minecraft.getInstance();
        List<Line> lines = new ArrayList<>();
        if (mc.player != null) {
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                int level = effect.getAmplifier() + 1;
                String name = I18n.get(effect.getDescriptionId()) + (level < ROMAN.length ? ROMAN[level] : " " + level);
                int seconds = effect.getDuration() / 20;
                String time = effect.getDuration() < 0 ? "∞" : String.format("%d:%02d", seconds / 60, seconds % 60);
                int color = effect.getEffect().value().getColor() | 0xFF000000;
                lines.add(new Line(name, time, color, effect.getDuration() >= 0 && seconds < 10));
            }
        }
        if (lines.isEmpty() && preview) {
            lines.add(new Line("Schnelligkeit II", "1:30", 0xFF33EBFF, false));
            lines.add(new Line("Stärke", "0:45", 0xFFFFC700, false));
        }
        return lines;
    }

    @Override
    public boolean hasContent() {
        return !lines(false).isEmpty();
    }

    @Override
    public int baseWidth() {
        var font = Minecraft.getInstance().font;
        int w = 70;
        for (Line line : lines(true)) w = Math.max(w, font.width(line.name + "  " + line.time) + 16);
        return w;
    }

    @Override
    public int baseHeight() {
        return Math.max(1, lines(true).size()) * 12 + 5;
    }

    @Override
    protected void render(GuiGraphics g, boolean preview) {
        List<Line> lines = lines(preview);
        if (lines.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int w = baseWidth();
        panel(g, w, lines.size() * 12 + 5);
        boolean blinkOff = blink.get() && (System.currentTimeMillis() / 400) % 2 == 0;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.ending && blinkOff) continue;
            int y = 4 + i * 12;
            g.fill(4, y, 6, y + 8, line.color);
            g.drawString(font, line.name, 10, y, color(), textShadow());
            g.drawString(font, line.time, w - 5 - font.width(line.time), y, 0xFFB0A8C8, textShadow());
        }
    }
}
