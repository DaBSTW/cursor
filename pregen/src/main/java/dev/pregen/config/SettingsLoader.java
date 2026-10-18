package dev.pregen.config;

import dev.pregen.config.PluginSettings.Generation;
import dev.pregen.config.PluginSettings.Progress;
import dev.pregen.config.PluginSettings.Throttle;
import dev.pregen.config.PluginSettings.Trim;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Reads {@code config.yml} into {@link PluginSettings}. Invalid values never stop the plugin: each one is replaced by
 * its safe default and reported once through the warning sink, naming the key and the accepted range.
 */
public final class SettingsLoader {

    public static final List<String> LANGUAGES = List.of("es", "en");

    private final ConfigurationSection root;
    private final Consumer<String> warnings;

    private SettingsLoader(ConfigurationSection root, Consumer<String> warnings) {
        this.root = root;
        this.warnings = warnings;
    }

    public static PluginSettings load(ConfigurationSection root, Consumer<String> warnings) {
        return new SettingsLoader(root, warnings).read();
    }

    private PluginSettings read() {
        String language = string("language", "es").toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(language)) {
            warn("language", language, "one of " + LANGUAGES, "es");
            language = "es";
        }
        return new PluginSettings(language, bool("use-client-locale", true),
                integer("confirm-timeout-seconds", 30, 5, 600), bool("debug", false), generation(), throttle(),
                progress(), trim());
    }

    private Generation generation() {
        int cores = Runtime.getRuntime().availableProcessors();
        return new Generation(integer("generation.max-concurrent-chunks", 32, 1, 1024),
                bool("generation.skip-existing-chunks", true), integer("generation.keep-ticking-seconds", 0, 0, 600),
                bool("generation.resume-on-restart", true), integer("generation.autosave-seconds", 30, 5, 3600),
                bool("generation.pause-when-players-online", false),
                decimal("generation.radius-limit", 0.0, 0.0, 30_000_000.0), bool("generation.check-disk-space", true),
                integer("generation.worker-threads", Math.max(2, Math.min(4, cores / 2)), 1, 32));
    }

    private Throttle throttle() {
        return new Throttle(bool("generation.adaptive-throttle.enabled", true),
                decimal("generation.adaptive-throttle.target-mspt", 45.0, 5.0, 1000.0),
                integer("generation.adaptive-throttle.min-concurrent-chunks", 4, 1, 1024));
    }

    private Progress progress() {
        return new Progress(integer("progress.console-interval-seconds", 10, 1, 3600), bool("progress.silent", false),
                enumValue("progress.bossbar.color", BossBar.Color.GREEN, BossBar.Color.class),
                enumValue("progress.bossbar.overlay", BossBar.Overlay.PROGRESS, BossBar.Overlay.class));
    }

    private Trim trim() {
        String directory = string("trim.backup-directory", "backups/trim");
        if (directory.isBlank() || directory.contains("..")) {
            warn("trim.backup-directory", directory, "a relative path without '..'", "backups/trim");
            directory = "backups/trim";
        }
        return new Trim(bool("trim.backup", true), directory);
    }

    private String string(String path, String fallback) {
        Object value = root.get(path);
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private boolean bool(String path, boolean fallback) {
        Object value = root.get(path);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        warn(path, value, "true or false", fallback);
        return fallback;
    }

    private int integer(String path, int fallback, int min, int max) {
        Object value = root.get(path);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer number && number >= min && number <= max) {
            return number;
        }
        warn(path, value, "a whole number between " + min + " and " + max, fallback);
        return fallback;
    }

    private double decimal(String path, double fallback, double min, double max) {
        Object value = root.get(path);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= min
                && number.doubleValue() <= max) {
            return number.doubleValue();
        }
        warn(path, value, "a number between " + min + " and " + max, fallback);
        return fallback;
    }

    private <E extends Enum<E>> E enumValue(String path, E fallback, Class<E> type) {
        String raw = string(path, fallback.name()).toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(raw)) {
                return constant;
            }
        }
        warn(path, raw, "one of " + List.of(type.getEnumConstants()), fallback);
        return fallback;
    }

    private void warn(String path, Object value, String expected, Object fallback) {
        warnings.accept("Invalid config value: key=" + path + " value='" + value + "' expected=" + expected + " using="
                + fallback);
    }
}
