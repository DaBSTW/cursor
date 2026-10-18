package dev.pregen.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsLoaderTest {

    private final List<String> warnings = new ArrayList<>();

    private PluginSettings load(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return SettingsLoader.load(config, warnings::add);
    }

    @Test
    void bundledConfigLoadsWithoutWarnings() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            YamlConfiguration config = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            PluginSettings settings = SettingsLoader.load(config, warnings::add);
            assertTrue(warnings.isEmpty(), warnings.toString());
            assertEquals("es", settings.language());
            assertEquals(32, settings.generation().maxConcurrentChunks());
            assertEquals(BossBar.Color.GREEN, settings.progress().bossBarColor());
        }
    }

    @Test
    void emptyConfigUsesSafeDefaults() throws Exception {
        PluginSettings settings = load("");
        assertTrue(warnings.isEmpty());
        assertTrue(settings.generation().skipExistingChunks());
        assertTrue(settings.throttle().enabled());
        assertTrue(settings.trim().backup());
        assertEquals(0.0, settings.generation().radiusLimit());
    }

    @Test
    void invalidValuesFallBackToDefaultsWithOneWarningEach() throws Exception {
        PluginSettings settings = load("""
                language: fr
                generation:
                  max-concurrent-chunks: 0
                  skip-existing-chunks: "maybe"
                  radius-limit: -5
                progress:
                  bossbar:
                    color: ORANGE
                trim:
                  backup-directory: "../../etc"
                """);
        assertEquals("es", settings.language());
        assertEquals(32, settings.generation().maxConcurrentChunks());
        assertTrue(settings.generation().skipExistingChunks());
        assertEquals(0.0, settings.generation().radiusLimit());
        assertEquals(BossBar.Color.GREEN, settings.progress().bossBarColor());
        assertEquals("backups/trim", settings.trim().backupDirectory());
        assertEquals(6, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().allMatch(warning -> warning.contains("key=")));
    }

    @Test
    void readsValidCustomValues() throws Exception {
        PluginSettings settings = load("""
                language: en
                generation:
                  max-concurrent-chunks: 128
                  pause-when-players-online: true
                  adaptive-throttle: {enabled: false, target-mspt: 30}
                progress: {silent: true, bossbar: {overlay: notched_10}}
                """);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals("en", settings.language());
        assertEquals(128, settings.generation().maxConcurrentChunks());
        assertTrue(settings.generation().pauseWhenPlayersOnline());
        assertFalse(settings.throttle().enabled());
        assertEquals(30.0, settings.throttle().targetMspt());
        assertTrue(settings.progress().silent());
        assertEquals(BossBar.Overlay.NOTCHED_10, settings.progress().bossBarOverlay());
    }
}
