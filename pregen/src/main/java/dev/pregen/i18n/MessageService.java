package dev.pregen.i18n;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Renders {@link MessageKey}s with MiniMessage in the reader's language. Thread-safe: the template tables are immutable
 * and swapped atomically on reload.
 */
public final class MessageService {

    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile Catalog catalog;

    public MessageService(Map<String, Map<MessageKey, String>> templates, String defaultLanguage,
            boolean useClientLocale) {
        reload(templates, defaultLanguage, useClientLocale);
    }

    public void reload(Map<String, Map<MessageKey, String>> templates, String defaultLanguage,
            boolean useClientLocale) {
        Map<String, Map<MessageKey, String>> copy = new HashMap<>();
        templates.forEach((language, table) -> copy.put(language, Map.copyOf(table)));
        if (!copy.containsKey(defaultLanguage)) {
            throw new IllegalArgumentException("No templates for default language '" + defaultLanguage + "'");
        }
        this.catalog = new Catalog(Map.copyOf(copy), defaultLanguage, useClientLocale);
    }

    public void send(CommandSender recipient, MessageKey key, Arg... args) {
        recipient.sendMessage(render(recipient, key, args));
    }

    public Component render(CommandSender recipient, MessageKey key, Arg... args) {
        return render(localeOf(recipient), key, args);
    }

    public Component render(Locale locale, MessageKey key, Arg... args) {
        Catalog current = catalog;
        String template = current.template(languageOf(locale), key);
        TagResolver.Builder resolvers = TagResolver.builder();
        if (key != MessageKey.PREFIX) {
            resolvers.resolver(Placeholder.parsed("prefix", current.template(languageOf(locale), MessageKey.PREFIX)));
        }
        for (Arg arg : args) {
            resolvers.resolver(arg.resolve(locale, this));
        }
        return miniMessage.deserialize(template, resolvers.build());
    }

    /** Locale used for the console and for players whose client language is not supported. */
    public Locale defaultLocale() {
        return toLocale(catalog.defaultLanguage());
    }

    public Locale localeOf(CommandSender recipient) {
        Catalog current = catalog;
        if (current.useClientLocale() && recipient instanceof Player player) {
            String language = player.locale().getLanguage();
            if (current.templates().containsKey(language)) {
                return toLocale(language);
            }
        }
        return toLocale(current.defaultLanguage());
    }

    private String languageOf(Locale locale) {
        return locale.getLanguage();
    }

    private static Locale toLocale(String language) {
        return "es".equals(language) ? SPANISH : Locale.US;
    }

    private record Catalog(Map<String, Map<MessageKey, String>> templates, String defaultLanguage,
            boolean useClientLocale) {

        String template(String language, MessageKey key) {
            Map<MessageKey, String> table = templates.getOrDefault(language, templates.get(defaultLanguage));
            String template = table.get(key);
            if (template == null) {
                template = templates.get(defaultLanguage).get(key);
            }
            return template == null ? key.path() : template;
        }
    }
}
