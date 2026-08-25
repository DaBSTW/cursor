package dev.bookreports.integration.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.milkbowl.vault.chat.Chat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaultBridgeImplTest {

    private ServerMock server;
    private Chat chat;
    private VaultBridge bridge;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        chat = mock(Chat.class);
        bridge = new VaultBridgeImpl(chat);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void prefixReturnsWhatTheChatProviderReports() {
        PlayerMock player = server.addPlayer("Steve");
        when(chat.getPlayerPrefix(null, player)).thenReturn("&c[Admin] ");

        assertEquals("&c[Admin] ", bridge.prefix(player));
    }

    @Test
    void suffixReturnsWhatTheChatProviderReports() {
        PlayerMock player = server.addPlayer("Steve");
        when(chat.getPlayerSuffix(null, player)).thenReturn(" &7VIP");

        assertEquals(" &7VIP", bridge.suffix(player));
    }

    @Test
    void prefixIsEmptyStringNotNullWhenTheProviderReturnsNull() {
        PlayerMock player = server.addPlayer("Steve");
        when(chat.getPlayerPrefix(null, player)).thenReturn(null);

        assertEquals("", bridge.prefix(player));
    }

    @Test
    void prefixIsEmptyStringWhenTheProviderThrows() {
        PlayerMock player = server.addPlayer("Steve");
        when(chat.getPlayerPrefix(null, player)).thenThrow(new RuntimeException("boom"));

        assertEquals("", bridge.prefix(player));
    }

    @Test
    void suffixIsEmptyStringWhenTheProviderThrows() {
        PlayerMock player = server.addPlayer("Steve");
        when(chat.getPlayerSuffix(null, player)).thenThrow(new RuntimeException("boom"));

        assertEquals("", bridge.suffix(player));
    }
}
