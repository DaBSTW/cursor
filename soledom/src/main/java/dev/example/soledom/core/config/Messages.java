package dev.example.soledom.core.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * MiniMessage-formatted, user-editable messages from {@code messages.yml}. Keys missing from the user's file fall
 * back to the copy bundled in the jar, so upgrades that add messages never show raw keys.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private volatile YamlConfiguration messages = new YamlConfiguration();

    public void load(@NotNull JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in,
                        StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled messages.yml", e);
        }
        this.messages = loaded;
    }

    /** Raw MiniMessage template for {@code key}. */
    public @NotNull String raw(@NotNull String key) {
        String value = messages.getString(key);
        return value == null ? "<red>Missing message: " + key : value;
    }

    public @NotNull Component component(@NotNull String key, @NotNull TagResolver... placeholders) {
        return MINI.deserialize(raw(key), TagResolver.resolver(TagResolver.resolver(placeholders),
                Placeholder.parsed("prefix", messages.getString("prefix", ""))));
    }

    public void send(@NotNull CommandSender sender, @NotNull String key, @NotNull TagResolver... placeholders) {
        String template = raw(key);
        if (template.isEmpty()) {
            return;
        }
        sender.sendMessage(component(key, placeholders));
    }

    /** Shorthand for a plain-text placeholder that is never parsed as MiniMessage. */
    public static @NotNull TagResolver ph(@NotNull String name, @NotNull Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static @NotNull Component parse(@NotNull String miniMessage) {
        return MINI.deserialize(miniMessage);
    }
}
