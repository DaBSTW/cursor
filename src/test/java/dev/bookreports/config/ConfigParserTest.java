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
        assertEquals("en_US", config.locale());
        assertEquals("7d", config.punishments().defaultBanDuration());
        assertEquals("1h", config.punishments().defaultMuteDuration());
        assertTrue(config.metricsEnabled());
        assertTrue(config.coreProtect().enabled());
        assertEquals(300, config.coreProtect().lookbackSeconds());
        assertEquals(20, config.coreProtect().maxEntries());
        assertTrue(config.updateChecker().enabled());
        assertEquals(UpdateSource.GITHUB, config.updateChecker().source());
        assertEquals("DaBSTW/cursor", config.updateChecker().resource());
        assertEquals(12, config.updateChecker().checkIntervalHours());
        assertTrue(config.updateChecker().notifyOpsOnJoin());
    }

    @Test
    void coreProtectDefaultsToEnabledWhenSectionIsMissing() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                """));

        CoreProtectSettings coreProtect = parser.parse(yaml).coreProtect();
        assertTrue(coreProtect.enabled());
        assertEquals(300, coreProtect.lookbackSeconds());
        assertEquals(20, coreProtect.maxEntries());
    }

    @Test
    void coreProtectCanBeDisabledAndTuned() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                coreprotect:
                  enabled: false
                  lookback-seconds: 60
                  max-entries: 5
                """));

        CoreProtectSettings coreProtect = parser.parse(yaml).coreProtect();
        assertFalse(coreProtect.enabled());
        assertEquals(60, coreProtect.lookbackSeconds());
        assertEquals(5, coreProtect.maxEntries());
    }

    @Test
    void metricsDefaultsToEnabledWhenSectionIsMissing() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                """));

        assertTrue(parser.parse(yaml).metricsEnabled());
    }

    @Test
    void metricsCanBeDisabled() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                metrics:
                  enabled: false
                """));

        assertFalse(parser.parse(yaml).metricsEnabled());
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
    void updateCheckerCanBeDisabledAndPointedAtModrinth() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                update-checker:
                  enabled: false
                  source: modrinth
                  resource: bookreports
                  check-interval-hours: 6
                  notify-ops-on-join: false
                """));

        UpdateCheckerSettings updateChecker = parser.parse(yaml).updateChecker();
        assertFalse(updateChecker.enabled());
        assertEquals(UpdateSource.MODRINTH, updateChecker.source());
        assertEquals("bookreports", updateChecker.resource());
        assertEquals(6, updateChecker.checkIntervalHours());
        assertFalse(updateChecker.notifyOpsOnJoin());
    }

    @Test
    void rejectsUpdateCheckerEnabledWithoutResource() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                report:
                  categories:
                    other:
                      display: "Other"
                      priority: LOW
                update-checker:
                  enabled: true
                  resource: ""
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
