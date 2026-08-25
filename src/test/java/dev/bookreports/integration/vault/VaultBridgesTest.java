package dev.bookreports.integration.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import java.util.Optional;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaultBridgesTest {

    private ServerMock server;
    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("BookReports");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void returnsEmptyWhenVaultIsNotInstalled() {
        assertEquals(Optional.empty(), VaultBridges.detect(server.getPluginManager()));
    }

    @Test
    void returnsEmptyWhenVaultIsInstalledButNoChatProviderIsRegistered() {
        MockBukkit.createMockPlugin("Vault");

        assertEquals(Optional.empty(), VaultBridges.detect(server.getPluginManager()));
    }

    @Test
    void detectsAChatProviderWhenVaultAndOneAreBothPresent() {
        MockBukkit.createMockPlugin("Vault");
        server.getServicesManager().register(Chat.class, mock(Chat.class), plugin, ServicePriority.Normal);

        Optional<VaultBridge> bridge = VaultBridges.detect(server.getPluginManager());

        assertTrue(bridge.isPresent());
    }
}
