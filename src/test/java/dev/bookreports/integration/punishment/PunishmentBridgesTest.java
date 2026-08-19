package dev.bookreports.integration.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class PunishmentBridgesTest {

    @Test
    void detectsLiteBansWhenPresent() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("LiteBans")).thenReturn(mock(Plugin.class));

        Optional<PunishmentBridge> bridge = PunishmentBridges.detect(pluginManager);

        assertTrue(bridge.isPresent());
        assertEquals("LiteBans", bridge.get().name());
    }

    @Test
    void detectsAdvancedBanWhenLiteBansAbsent() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("AdvancedBan")).thenReturn(mock(Plugin.class));

        Optional<PunishmentBridge> bridge = PunishmentBridges.detect(pluginManager);

        assertTrue(bridge.isPresent());
        assertEquals("AdvancedBan", bridge.get().name());
    }

    @Test
    void detectsEssentialsWhenNothingElsePresent() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("Essentials")).thenReturn(mock(Plugin.class));

        Optional<PunishmentBridge> bridge = PunishmentBridges.detect(pluginManager);

        assertTrue(bridge.isPresent());
        assertEquals("Essentials", bridge.get().name());
    }

    @Test
    void prefersLiteBansOverAdvancedBanAndEssentials() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("LiteBans")).thenReturn(mock(Plugin.class));
        when(pluginManager.getPlugin("AdvancedBan")).thenReturn(mock(Plugin.class));
        when(pluginManager.getPlugin("Essentials")).thenReturn(mock(Plugin.class));

        assertEquals("LiteBans", PunishmentBridges.detect(pluginManager).orElseThrow().name());
    }

    @Test
    void returnsEmptyWhenNonePresent() {
        PluginManager pluginManager = mock(PluginManager.class);

        assertEquals(Optional.empty(), PunishmentBridges.detect(pluginManager));
    }
}
