package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

import java.util.function.Supplier;

/** Ein Knopf im Mod-Menü (keine gespeicherte Einstellung): Beschriftung kann sich ändern, Klick führt die Aktion aus. */
public class ActionSetting extends Setting<Boolean> {
    private final Supplier<String> button;
    private final Runnable action;

    public ActionSetting(Module module, String key, String label, Supplier<String> button, Runnable action) {
        super(module, key, label, false);
        this.button = button;
        this.action = action;
    }

    @Override
    public Boolean get() {
        return false;
    }

    public String button() {
        return button.get();
    }

    public void run() {
        action.run();
    }

    @Override
    public void reset() {
        // nichts gespeichert
    }
}
