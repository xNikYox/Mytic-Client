package de.myticlegacy.client.setting;

import de.myticlegacy.client.module.Module;

/** Zahlenwert mit Schieberegler. */
public class SliderSetting extends Setting<Double> {
    public final double min;
    public final double max;
    public final double step;
    private final String suffix;

    public SliderSetting(Module module, String key, String label, double min, double max, double step, double defaultValue, String suffix) {
        super(module, key, label, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
    }

    @Override
    public Double get() {
        Object raw = raw();
        return raw instanceof Number ? clamp(((Number) raw).doubleValue()) : defaultValue;
    }

    @Override
    public void set(Double value) {
        store(clamp(value));
    }

    public float floatValue() {
        return get().floatValue();
    }

    public int intValue() {
        return (int) Math.round(get());
    }

    private double clamp(double value) {
        double stepped = Math.round((value - min) / step) * step + min;
        return Math.max(min, Math.min(max, Math.round(stepped * 1000.0) / 1000.0));
    }

    /** Position 0..1 auf dem Regler. */
    public double fraction() {
        return (get() - min) / (max - min);
    }

    public void setFraction(double fraction) {
        set(min + Math.max(0, Math.min(1, fraction)) * (max - min));
    }

    public String display() {
        double value = get();
        String number = step >= 1 ? String.valueOf((int) Math.round(value)) : String.format("%.2f", value).replaceAll("0+$", "").replaceAll("[.,]$", "");
        return number + suffix;
    }
}
