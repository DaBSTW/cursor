package dev.bookreports.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocaleManagerTest {

    private ServerMock server;
    private LocaleManager localeManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockPlugin plugin = MockBukkit.createMockPlugin("BookReports");
        localeManager = new LocaleManager(plugin);
        localeManager.load("es_ES");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesPlaceholdersInMiniMessageText() {
        var component = localeManager.get("report.submitted", Map.of("ticket_id", "123"));
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        assertEquals("✅ Reporte #123 enviado. Gracias.", plain);
    }

    @Test
    void missingKeyFallsBackToTheKeyItselfInsteadOfCrashing() {
        var component = localeManager.get("this.key.does.not.exist");
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        assertEquals("this.key.does.not.exist", plain);
    }

    @Test
    void switchingLocaleReplacesAllMessages() {
        localeManager.load("en_US");
        var component = localeManager.get("report.cancelled");
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        assertTrue(plain.equals("Report cancelled."));
    }
}
