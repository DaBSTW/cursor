package dev.bookreports.integration.coreprotect;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class CoreProtectBridgesTest {

    @Test
    void detectsCoreProtectWhenPresentEnabledAndReady() {
        CoreProtectAPI api = mock(CoreProtectAPI.class);
        when(api.isEnabled()).thenReturn(true);
        when(api.APIVersion()).thenReturn(9);
        CoreProtect coreProtect = mock(CoreProtect.class);
        when(coreProtect.isEnabled()).thenReturn(true);
        when(coreProtect.getAPI()).thenReturn(api);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("CoreProtect")).thenReturn(coreProtect);

        Optional<CoreProtectBridge> bridge = CoreProtectBridges.detect(pluginManager, 300, 20);

        assertTrue(bridge.isPresent());
    }

    @Test
    void returnsEmptyWhenPluginAbsent() {
        PluginManager pluginManager = mock(PluginManager.class);

        assertTrue(CoreProtectBridges.detect(pluginManager, 300, 20).isEmpty());
    }

    @Test
    void returnsEmptyWhenPluginPresentButNotACoreProtectInstance() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("CoreProtect")).thenReturn(mock(Plugin.class));

        assertTrue(CoreProtectBridges.detect(pluginManager, 300, 20).isEmpty());
    }

    @Test
    void returnsEmptyWhenCoreProtectPluginIsDisabled() {
        CoreProtect coreProtect = mock(CoreProtect.class);
        when(coreProtect.isEnabled()).thenReturn(false);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("CoreProtect")).thenReturn(coreProtect);

        assertTrue(CoreProtectBridges.detect(pluginManager, 300, 20).isEmpty());
    }

    @Test
    void returnsEmptyWhenApiReportsNotEnabled() {
        CoreProtectAPI api = mock(CoreProtectAPI.class);
        when(api.isEnabled()).thenReturn(false);
        CoreProtect coreProtect = mock(CoreProtect.class);
        when(coreProtect.isEnabled()).thenReturn(true);
        when(coreProtect.getAPI()).thenReturn(api);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("CoreProtect")).thenReturn(coreProtect);

        assertTrue(CoreProtectBridges.detect(pluginManager, 300, 20).isEmpty());
    }

    @Test
    void returnsEmptyWhenApiVersionIsTooOld() {
        CoreProtectAPI api = mock(CoreProtectAPI.class);
        when(api.isEnabled()).thenReturn(true);
        when(api.APIVersion()).thenReturn(3);
        CoreProtect coreProtect = mock(CoreProtect.class);
        when(coreProtect.isEnabled()).thenReturn(true);
        when(coreProtect.getAPI()).thenReturn(api);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("CoreProtect")).thenReturn(coreProtect);

        assertTrue(CoreProtectBridges.detect(pluginManager, 300, 20).isEmpty());
    }

    @Test
    void returnsEmptyWhenGetApiReturnsNull() {
        CoreProtect coreProtect = mock(CoreProtect.class);
        when(coreProtect.isEnabled()).thenReturn(true);
        when(coreProtect.getAPI()).thenReturn(null);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("CoreProtect")).thenReturn(coreProtect);

        assertTrue(CoreProtectBridges.detect(pluginManager, 300, 20).isEmpty());
    }
}
