package dev.bookreports.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bookreports.storage.model.Priority;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigParserTest {

    private final ConfigParser parser = new ConfigParser();

    @Test
    void parsesTheBundledDefaultConfig() throws IOException {
        BookReportsConfig config = parser.parse(loadResource("/config.yml"));

        assertEquals(StorageType.SQLITE, config.storageType());
        assertEquals(120, config.cooldownSeconds());
        assertEquals(10, config.dailyLimit());
        assertTrue(config.preventSelfReport());
        assertTrue(config.preventDuplicatePending());
        assertEquals(6, config.categories().size());
        assertEquals(Priority.HIGH, config.category("hacks").orElseThrow().priority());
        assertTrue(config.category("hacks").orElseThrow().hasSubReasons());
        assertFalse(config.category("bug_abuse").orElseThrow().hasSubReasons());
        assertEquals(3, config.priorityEscalation().distinctReportersThreshold());
        assertEquals("es_ES", config.locale());
        assertEquals("7d", config.punishments().defaultBanDuration());
        assertEquals("1h", config.punishments().defaultMuteDuration());
    }

    @Test
    void rejectsUnknownCategoryPriority() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    hacks:
                      display: "Hacks"
                      priority: SUPER_HIGH
                """));

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> parser.parse(yaml));
        assertTrue(exception.getMessage().contains("hacks"));
        assertTrue(exception.getMessage().contains("SUPER_HIGH"));
    }

    @Test
    void rejectsCategoryMissingDisplay() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    hacks:
                      priority: HIGH
                """));

        assertThrows(ConfigurationException.class, () -> parser.parse(yaml));
    }

    @Test
    void rejectsEmptyCategoryMap() {
        YamlConfiguration yaml = new YamlConfiguration();

        assertThrows(ConfigurationException.class, () -> parser.parse(yaml));
    }

    @Test
    void categoryWithoutSubReasonsParsesAsEmptyList() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                """));

        BookReportsConfig config = parser.parse(yaml);
        assertFalse(config.category("other").orElseThrow().hasSubReasons());
    }

    @Test
    void rejectsInvalidPriorityEscalationTarget() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                priority-escalation:
                  escalate-to: NOT_A_PRIORITY
                """));

        assertThrows(ConfigurationException.class, () -> parser.parse(yaml));
    }

    @Test
    void rejectsDiscordEnabledWithoutWebhookUrl() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                discord:
                  enabled: true
                """));

        assertThrows(ConfigurationException.class, () -> parser.parse(yaml));
    }

    private YamlConfiguration loadResource(String path) throws IOException {
        try (InputStream in = ConfigParserTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
