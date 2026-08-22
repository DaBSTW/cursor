package dev.bookreports.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import dev.bookreports.book.BookBuilder;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.config.TestConfigs;
import dev.bookreports.session.SessionManager;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /report <player>} already matches an exact, case-insensitive online player name (SPECS.md §4.1); this covers
 * that path plus the tab-completion suggestions that make it discoverable.
 */
class ReportCommandTest {

    private ServerMock server;
    private ReportCommand command;
    private PlayerMock reporter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockPlugin plugin = MockBukkit.createMockPlugin("BookReports");
        LocaleManager locale = new LocaleManager(plugin);
        locale.load("en_US");

        SessionManager sessions = new SessionManager(TestConfigs::minimal, Clock.systemUTC());
        BookBuilder books = new BookBuilder(locale);
        RateLimiter rateLimiter = new RateLimiter(Duration.ofMillis(1));
        command = new ReportCommand(sessions, TestConfigs::minimal, locale, books, rateLimiter);

        reporter = server.addPlayer("Reporter");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void opensASessionWhenGivenAnExactOnlinePlayerName() {
        server.addPlayer("Steve");

        boolean handled = command.onCommand(reporter, null, "report", new String[]{"Steve"});

        assertTrue(handled);
        assertTrue(reporter.nextComponentMessage() == null);
    }

    @Test
    void matchesAnOnlinePlayerNameCaseInsensitively() {
        server.addPlayer("Steve");

        boolean handled = command.onCommand(reporter, null, "report", new String[]{"sTeVe"});

        assertTrue(handled);
        assertTrue(reporter.nextComponentMessage() == null);
    }

    @Test
    void reportsTargetNotFoundForAnUnknownName() {
        boolean handled = command.onCommand(reporter, null, "report", new String[]{"NoSuchPlayer"});

        assertTrue(handled);
        assertFalse(reporter.nextComponentMessage() == null);
    }

    @Test
    void suggestsOnlinePlayerNamesMatchingThePartialArgument() {
        server.addPlayer("Steve");
        server.addPlayer("Stella");
        server.addPlayer("Alex");

        var suggestions = command.onTabComplete(reporter, null, "report", new String[]{"ste"});

        assertTrue(suggestions.contains("Steve"));
        assertTrue(suggestions.contains("Stella"));
        assertFalse(suggestions.contains("Alex"));
    }

    @Test
    void suggestionsExcludeTheSenderThemselves() {
        var suggestions = command.onTabComplete(reporter, null, "report", new String[]{""});

        assertFalse(suggestions.contains(reporter.getName()));
    }

    @Test
    void suggestsToolWhenItMatchesThePartialArgument() {
        var suggestions = command.onTabComplete(reporter, null, "report", new String[]{"to"});

        assertTrue(suggestions.contains("tool"));
    }

    @Test
    void suggestsNothingPastTheFirstArgument() {
        var suggestions = command.onTabComplete(reporter, null, "report", new String[]{"Steve", "extra"});

        assertTrue(suggestions.isEmpty());
    }
}
