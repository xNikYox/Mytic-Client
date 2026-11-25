package de.myticlegacy.client.module;

import de.myticlegacy.client.MyticClient;
import de.myticlegacy.client.compat.Compat;
import de.myticlegacy.client.gui.CrosshairScreen;
import de.myticlegacy.client.gui.ui.Crosshair;
import de.myticlegacy.client.gui.ui.Toasts;
import de.myticlegacy.client.hud.CombatTracker;
import de.myticlegacy.client.setting.ActionSetting;
import de.myticlegacy.client.setting.BoolSetting;
import de.myticlegacy.client.setting.ColorSetting;
import de.myticlegacy.client.setting.DataSetting;
import de.myticlegacy.client.setting.ModeSetting;
import de.myticlegacy.client.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Fadenkreuz-Editor: ersetzt das Minecraft-Fadenkreuz durch eine eigene Form (Kreuz, Plus mit Lücke, Punkt, Kreis, T,
 * Pfeil oder ein eigenes 15 × 15-Pixelbild) mit Länge, Dicke, Abstand, Umrandung, Farbe, Deckkraft und Mittelpunkt.
 * Auf Spielern/Mobs und bei Treffern wechselt die Farbe. Teilen über den Code „MYTIC-XH:…“ (Zwischenablage).
 */
public class CrosshairModule extends Module {
    public final ModeSetting form;
    public final SliderSetting size, thickness, gap, opacity;
    public final ColorSetting color, targetColor, hitColor;
    public final BoolSetting outline, dot, dynamic, mobs, hitFlash, hideThirdPerson, hideSpectator;
    public final DataSetting pixels;

    public CrosshairModule() {
        super("crosshair", "Fadenkreuz", "Eigenes Fadenkreuz: Form, Farbe, Pixel-Editor, Vorlagen und Teilen per Code", Category.VISUELL, () -> Items.TARGET, false);
        // „T-Form“ hieß bis 2.56 so – gespeicherten Wert übernehmen
        if ("T-Form".equals(MyticClient.config().settings.get("crosshair.style"))) MyticClient.config().settings.put("crosshair.style", "T");
        form = new ModeSetting(this, "style", "Form", "Plus mit Lücke", Crosshair.FORMS);
        size = new SliderSetting(this, "size", "Länge", 1, 12, 1, 4, "");
        thickness = new SliderSetting(this, "thickness", "Dicke", 1, 4, 1, 1, "");
        gap = new SliderSetting(this, "gap", "Abstand zur Mitte", 0, 10, 1, 2, "");
        color = new ColorSetting(this, "color", "Farbe", 0xFFFFFFFF);
        opacity = new SliderSetting(this, "opacity", "Deckkraft", 10, 100, 5, 100, " %");
        outline = new BoolSetting(this, "outline", "Umrandung", true);
        dot = new BoolSetting(this, "dot", "Punkt in der Mitte", false);
        dynamic = new BoolSetting(this, "targetColor", "Andere Farbe auf Spielern", true);
        targetColor = new ColorSetting(this, "targetColorValue", "Farbe auf Ziel", 0xFFFF5C72);
        mobs = new BoolSetting(this, "targetMobs", "Auch auf Mobs", false);
        hitFlash = new BoolSetting(this, "hitFlash", "Bei Treffer aufleuchten", true);
        hitColor = new ColorSetting(this, "hitColor", "Trefferfarbe", 0xFFFFD84A);
        hideThirdPerson = new BoolSetting(this, "hideThirdPerson", "In F5 ausblenden", true);
        hideSpectator = new BoolSetting(this, "hideSpectator", "Als Zuschauer ausblenden", true);
        pixels = new DataSetting(this, "pixels", Crosshair.pixelsHex(Crosshair.preset(6).pixels), 64);
        new ActionSetting(this, "editor", "Fadenkreuz-Editor", () -> "Editor öffnen",
                () -> Compat.setScreen(new CrosshairScreen(Compat.screen(Minecraft.getInstance()))));
        new ActionSetting(this, "copy", "Teilen", () -> "Code kopieren", this::copyCode);
        new ActionSetting(this, "paste", "Code eines Freundes", () -> "Code einfügen", this::pasteCode);
    }

    /** Aktuelles Aussehen */
    public Crosshair.Spec spec() {
        Crosshair.Spec s = new Crosshair.Spec();
        s.form = form.get();
        s.size = size.intValue();
        s.thickness = thickness.intValue();
        s.gap = gap.intValue();
        s.opacity = opacity.intValue();
        s.outline = outline.get();
        s.dot = dot.get();
        s.color = color.get();
        s.dynamic = dynamic.get();
        s.mobs = mobs.get();
        s.targetColor = targetColor.get();
        s.pixels = Crosshair.pixelsFromHex(pixels.get());
        return s;
    }

    /** Aussehen übernehmen (Vorlage, Code); speichert */
    public void apply(Crosshair.Spec s) {
        form.set(s.form);
        size.set((double) s.size);
        thickness.set((double) s.thickness);
        gap.set((double) s.gap);
        opacity.set((double) s.opacity);
        outline.set(s.outline);
        dot.set(s.dot);
        color.set(s.color);
        dynamic.set(s.dynamic);
        mobs.set(s.mobs);
        targetColor.set(s.targetColor);
        pixels.set(Crosshair.pixelsHex(s.pixels));
        MyticClient.config().save();
    }

    public void copyCode() {
        String code = Crosshair.encode(spec());
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        Toasts.ok("Fadenkreuz-Code kopiert");
    }

    public void pasteCode() {
        Crosshair.Spec s = Crosshair.decode(Minecraft.getInstance().keyboardHandler.getClipboard());
        if (s == null) {
            Toasts.error("Kein Fadenkreuz-Code (MYTIC-XH:…) in der Zwischenablage");
            return;
        }
        apply(s);
        if (!enabled()) setEnabled(true);
        Toasts.ok("Fadenkreuz übernommen");
    }

    /** true = eigenes Fadenkreuz gezeichnet (bzw. bewusst ausgeblendet), false = das normale verwenden. */
    public boolean render(GuiGraphics g) {
        if (!enabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (Compat.hudHidden(mc)) return true;
        if (hideThirdPerson.get() && !mc.options.getCameraType().isFirstPerson()) return true;
        if (hideSpectator.get() && mc.player != null && mc.player.isSpectator()) return true;
        Crosshair.Spec s = spec();
        int cx = (g.guiWidth() - 15) / 2 + 7, cy = (g.guiHeight() - 15) / 2 + 7;
        Crosshair.draw(g, cx, cy, s, currentColor(mc, s), 1);
        return true;
    }

    /** Farbe jetzt: Treffer (kurz) > Ziel > normal */
    private int currentColor(Minecraft mc, Crosshair.Spec s) {
        long since = System.currentTimeMillis() - CombatTracker.lastHitTime();
        if (hitFlash.get() && since >= 0 && since < 220) return hitColor.get();
        if (s.dynamic && onTarget(mc)) return s.targetColor;
        return s.color;
    }

    /** Zielt das Fadenkreuz gerade auf einen Spieler (bzw. Mob)? */
    private boolean onTarget(Minecraft mc) {
        Entity e = mc.crosshairPickEntity;
        return e instanceof Player || (mobs.get() && e instanceof LivingEntity);
    }
}
