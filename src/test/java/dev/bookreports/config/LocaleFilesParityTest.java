package dev.bookreports.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Both bundled locales must expose exactly the same keys, or a language switch would silently fall back to raw keys for
 * whatever is missing.
 */
class LocaleFilesParityTest {

    @Test
    void esAndEnDeclareTheSameKeys() throws IOException {
        Set<String> spanish = keysOf("/locale/es_ES.yml");
        Set<String> english = keysOf("/locale/en_US.yml");

        assertFalse(spanish.isEmpty());
        assertEquals(spanish, english);
    }

    private Set<String> keysOf(String resource) throws IOException {
        try (InputStream in = LocaleFilesParityTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resource);
            }
            YamlConfiguration yaml = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> keys = new TreeSet<>();
            flatten(yaml, "", keys);
            return keys;
        }
    }

    private void flatten(ConfigurationSection section, String prefix, Set<String> out) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                flatten(section.getConfigurationSection(key), path, out);
            } else {
                out.add(path);
            }
        }
    }
}
