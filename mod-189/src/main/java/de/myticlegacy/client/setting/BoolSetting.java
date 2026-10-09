package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(Module module, String key, String label, boolean defaultValue) {
        super(module, key, label, defaultValue);
    }

    @Override
    public Boolean get() {
        Object raw = raw();
        return raw instanceof Boolean ? (Boolean) raw : defaultValue;
    }

    public void toggle() {
        set(!get());
    }
}
