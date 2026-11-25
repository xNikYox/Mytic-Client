package de.myticlegacy.client.module;

import de.myticlegacy.client.gui.ui.Toasts;
import de.myticlegacy.client.setting.ActionSetting;
import net.minecraft.world.item.Items;

/**
 * Eintrag „Leistungsprofil“ im Mod-Menü: an = ein Profil ist aktiv, aus = eigene Einstellungen. Die Knöpfe wählen das
 * Profil (dieselben wie unter Client-Einstellungen → Leistung).
 */
public class PerfProfileModule extends Module {
    private int last = 1;

    public PerfProfileModule() {
        super("perfprofile", "Leistungsprofil", "Ein Klick: Maximale FPS, Ausgewogen, Schön – oder zurück zu deinen eigenen Einstellungen",
                Category.VISUELL, () -> Items.COMPARATOR, false);
        for (int i = 0; i < PerfProfiles.NAMES.length; i++) {
            final int idx = i;
            new ActionSetting(this, "p" + i, PerfProfiles.NAMES[i], () -> PerfProfiles.active() == idx ? "Aktiv" : idx == PerfProfiles.OWN ? "Zurück" : "Aktivieren",
                    () -> pick(idx));
        }
    }

    private void pick(int i) {
        if (i != PerfProfiles.OWN) last = i;
        PerfProfiles.apply(i);
        Toasts.ok(i == PerfProfiles.OWN ? "Eigene Einstellungen wiederhergestellt" : "Profil „" + PerfProfiles.NAMES[i] + "“ aktiv");
    }

    @Override
    public boolean enabled() {
        return PerfProfiles.active() != PerfProfiles.OWN;
    }

    @Override
    public void setEnabled(boolean value) {
        if (value == enabled()) return;
        pick(value ? last : PerfProfiles.OWN);
    }
}
