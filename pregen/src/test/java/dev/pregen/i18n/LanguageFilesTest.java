package dev.pregen.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pregen.config.SettingsLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageFilesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("(?<!\\\\)<([a-z_]+)>");
    private final List<String> warnings = new ArrayList<>();

    @TempDir
    Path langDirectory;

    private Map<String, Map<MessageKey, String>> bundled() {
        return LanguageFiles.load(langDirectory, SettingsLoader.LANGUAGES,
                getClass().getClassLoader()::getResourceAsStream, warnings::add);
    }

    @ParameterizedTest
    @ValueSource(strings = {"es", "en"})
    void everyKeyHasATemplate(String language) {
        Map<MessageKey, String> table = bundled().get(language);
        for (MessageKey key : MessageKey.values()) {
            assertNotNull(table.get(key), language + " is missing " + key.path());
        }
        assertTrue(warnings.isEmpty());
    }

    @Test
    void bothLanguagesUseTheSamePlaceholders() {
        Map<String, Map<MessageKey, String>> tables = bundled();
        for (MessageKey key : MessageKey.values()) {
            assertEquals(placeholders(tables.get("en").get(key)), placeholders(tables.get("es").get(key)),
                    "placeholders differ for " + key.path());
        }
    }

    @Test
    void customFileOverridesAndMissingKeysFallBack() throws IOException {
        Files.writeString(langDirectory.resolve("en.yml"), "general:\n  internal-error: \"custom\"\n",
                StandardCharsets.UTF_8);
        Map<MessageKey, String> en = bundled().get("en");
        assertEquals("custom", en.get(MessageKey.INTERNAL_ERROR));
        assertNotNull(en.get(MessageKey.START_STARTED));
        assertFalse(warnings.isEmpty());
    }

    @Test
    void rendersPlaceholdersWithoutParsingUserText() {
        MessageService messages = new MessageService(bundled(), "en", true);
        Component rendered = messages.render(Locale.US, MessageKey.WORLD_NOT_FOUND, Arg.text("world", "<red>evil"));
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        assertTrue(plain.contains("<red>evil"), plain);
        assertTrue(plain.startsWith("[Pregen]"), plain);
    }

    @Test
    void formatsNumbersForTheReadersLanguage() {
        MessageService messages = new MessageService(bundled(), "es", true);
        String spanish = PlainTextComponentSerializer.plainText()
                .serialize(messages.render(Locale.forLanguageTag("es-ES"), MessageKey.START_EMPTY));
        assertTrue(spanish.contains("ningún"));
        String english = PlainTextComponentSerializer.plainText()
                .serialize(messages.render(Locale.US, MessageKey.DOCTOR_SAVED_TASKS, Arg.number("count", 1234567)));
        assertTrue(english.contains("1,234,567"), english);
    }

    @Test
    void helpLinesShowArgumentNamesLiterally() {
        MessageService messages = new MessageService(bundled(), "es", true);
        String help = PlainTextComponentSerializer.plainText()
                .serialize(messages.render(Locale.forLanguageTag("es-ES"), MessageKey.HELP_CORNERS));
        assertTrue(help.contains("/pregen corners <x1> <z1> <x2> <z2>"), help);
    }

    private static List<String> placeholders(String template) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!isStyleTag(name)) {
                names.add(name);
            }
        }
        names.sort(null);
        return names;
    }

    private static boolean isStyleTag(String name) {
        return List.of("red", "green", "gray", "white", "yellow", "gold", "aqua", "dark_gray", "prefix").contains(name);
    }
}
