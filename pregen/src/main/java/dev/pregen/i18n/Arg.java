package dev.pregen.i18n;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * A message placeholder whose final text depends on the reader's language (number grouping, decimals). Text values are
 * inserted unparsed, so user input such as a world name can never inject MiniMessage tags.
 */
public sealed interface Arg {

    String name();

    TagResolver resolve(Locale locale, MessageService messages);

    static Arg text(String name, Object value) {
        return new Text(name, String.valueOf(value));
    }

    static Arg number(String name, long value) {
        return new Formatted(name, "%,d", value);
    }

    static Arg decimal(String name, double value) {
        return new Formatted(name, "%,.1f", value);
    }

    static Arg duration(String name, long seconds) {
        return new Duration(name, seconds);
    }

    static Arg message(String name, MessageKey key) {
        return new Nested(name, key);
    }

    static Arg component(String name, Component value) {
        return new Rich(name, value);
    }

    record Text(String name, String value) implements Arg {
        @Override
        public TagResolver resolve(Locale locale, MessageService messages) {
            return Placeholder.unparsed(name, value);
        }
    }

    record Formatted(String name, String format, Object value) implements Arg {
        @Override
        public TagResolver resolve(Locale locale, MessageService messages) {
            return Placeholder.unparsed(name, String.format(locale, format, value));
        }
    }

    record Duration(String name, long seconds) implements Arg {
        @Override
        public TagResolver resolve(Locale locale, MessageService messages) {
            return Placeholder.unparsed(name, Durations.format(seconds));
        }
    }

    record Nested(String name, MessageKey key) implements Arg {
        @Override
        public TagResolver resolve(Locale locale, MessageService messages) {
            return Placeholder.component(name, messages.render(locale, key));
        }
    }

    record Rich(String name, Component value) implements Arg {
        @Override
        public TagResolver resolve(Locale locale, MessageService messages) {
            return Placeholder.component(name, value);
        }
    }
}
