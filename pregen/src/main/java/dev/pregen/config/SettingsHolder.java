package dev.pregen.config;

import java.util.Objects;

/** The current settings, swapped atomically on reload so readers always see one consistent snapshot. */
public final class SettingsHolder {

    private volatile PluginSettings current;

    public SettingsHolder(PluginSettings initial) {
        this.current = Objects.requireNonNull(initial, "initial");
    }

    public PluginSettings get() {
        return current;
    }

    public void set(PluginSettings settings) {
        this.current = Objects.requireNonNull(settings, "settings");
    }
}
