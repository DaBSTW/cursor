package dev.pregen.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code lang/<language>.yml}. The editable copy in the plugin folder wins; keys missing from it fall back to the
 * copy bundled in the jar, so updates that add messages never show raw keys.
 */
public final class LanguageFiles {

    private LanguageFiles() {
    }

    public static Map<String, Map<MessageKey, String>> load(Path langDirectory, List<String> languages,
            Function<String, InputStream> bundled, Consumer<String> warnings) {
        Map<String, Map<MessageKey, String>> result = new HashMap<>();
        for (String language : languages) {
            YamlConfiguration defaults = readBundled(bundled, language);
            YamlConfiguration custom = readCustom(langDirectory.resolve(language + ".yml"), warnings);
            result.put(language, toTable(custom, defaults, language, warnings));
        }
        return result;
    }

    static Map<MessageKey, String> toTable(ConfigurationSection custom, ConfigurationSection defaults, String language,
            Consumer<String> warnings) {
        Map<MessageKey, String> table = new EnumMap<>(MessageKey.class);
        int missing = 0;
        for (MessageKey key : MessageKey.values()) {
            String value = custom == null ? null : custom.getString(key.path());
            if (value == null) {
                value = defaults.getString(key.path());
                missing += custom == null ? 0 : 1;
            }
            if (value != null) {
                table.put(key, value);
            }
        }
        if (missing > 0) {
            warnings.accept("Language file lang/" + language + ".yml is missing " + missing
                    + " message(s); using the bundled text for them. Delete the file to regenerate it.");
        }
        return table;
    }

    private static YamlConfiguration readBundled(Function<String, InputStream> bundled, String language) {
        String resource = "lang/" + language + ".yml";
        try (InputStream stream = bundled.apply(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Bundled resource missing from the jar: " + resource);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read bundled resource " + resource, e);
        }
    }

    private static YamlConfiguration readCustom(Path file, Consumer<String> warnings) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            warnings.accept("Could not parse " + file + ", using the bundled messages instead: " + e.getMessage());
            return null;
        }
    }
}
