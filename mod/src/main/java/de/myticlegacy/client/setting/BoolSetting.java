package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(Module module, String key, String label, boolean defaultValue) {
        super(module, key, label, defaultValue);
    }

    @Override
    public Boolean get() {
        return raw() instanceof Boolean b ? b : defaultValue;
    }

    public void toggle() {
        set(!get());
    }
}
