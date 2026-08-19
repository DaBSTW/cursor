package dev.bookreports.config;

import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns the plugin's {@code config.yml} lifecycle: initial load, validation and hot reload. */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private final ConfigParser parser;
    private volatile BookReportsConfig config;

    public ConfigManager(JavaPlugin plugin) {
        this(plugin, new ConfigParser());
    }

    ConfigManager(JavaPlugin plugin, ConfigParser parser) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    /** Loads (or reloads) {@code config.yml} from disk. Throws {@link ConfigurationException} on any invalid value. */
    public BookReportsConfig load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        BookReportsConfig parsed = parser.parse(plugin.getConfig());
        this.config = parsed;
        return parsed;
    }

    public BookReportsConfig current() {
        BookReportsConfig snapshot = config;
        if (snapshot == null) {
            throw new IllegalStateException("Configuration has not been loaded yet");
        }
        return snapshot;
    }
}
