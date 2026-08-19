package dev.bookreports.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads player-facing text from {@code locale/<locale>.yml} and renders it with MiniMessage.
 *
 * <p>
 * Every string a player or staff member can read comes from here — see CODESTYLE.md §2.
 */
public final class LocaleManager {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile Map<String, String> messages = Map.of();
    private volatile String activeLocale = "";

    public LocaleManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Loads the given locale from the plugin data folder, extracting the bundled default on first run. */
    public void load(String locale) {
        Objects.requireNonNull(locale, "locale");
        String resourcePath = "locale/" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, String> flattened = new HashMap<>();
        flatten(yaml, "", flattened);
        if (flattened.isEmpty()) {
            throw new ConfigurationException("Locale file '" + resourcePath + "' has no keys");
        }

        this.messages = Map.copyOf(flattened);
        this.activeLocale = locale;
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    public Component get(String key, Map<String, String> placeholders) {
        Objects.requireNonNull(key, "key");
        String raw = messages.get(key);
        if (raw == null) {
            plugin.getLogger().warning("Missing locale key: key=" + key + " locale=" + activeLocale);
            return Component.text(key);
        }
        return miniMessage.deserialize(raw, resolvers(placeholders));
    }

    public boolean hasKey(String key) {
        return messages.containsKey(key);
    }

    private TagResolver resolvers(Map<String, String> placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        placeholders.forEach((placeholderKey, value) -> builder.resolver(Placeholder.unparsed(placeholderKey, value)));
        return builder.build();
    }

    private void flatten(ConfigurationSection section, String prefix, Map<String, String> out) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                flatten(section.getConfigurationSection(key), path, out);
            } else {
                Object value = section.get(key);
                if (value != null) {
                    out.put(path, String.valueOf(value));
                }
            }
        }
    }
}
