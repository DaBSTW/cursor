package dev.bookreports.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.book.BookBuilder;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.config.TestConfigs;
import dev.bookreports.service.CooldownService;
import dev.bookreports.service.DailyLimitService;
import dev.bookreports.service.PriorityCalculator;
import dev.bookreports.service.ReportService;
import dev.bookreports.session.SessionManager;
import dev.bookreports.storage.TestDatabases;
import dev.bookreports.storage.dao.JdbcPenaltyDao;
import dev.bookreports.storage.dao.JdbcReportDao;
import dev.bookreports.storage.dao.PenaltyDao;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import dev.bookreports.util.ImmediateSchedulerAdapter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    private HikariDataSource dataSource;
    private ReportDao reportDao;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockPlugin plugin = MockBukkit.createMockPlugin("BookReports");
        LocaleManager locale = new LocaleManager(plugin);
        locale.load("en_US");

        SessionManager sessions = new SessionManager(TestConfigs::minimal, Clock.systemUTC());
        BookBuilder books = new BookBuilder(locale);
        RateLimiter rateLimiter = new RateLimiter(Duration.ofMillis(1));

        dataSource = TestDatabases.freshSqlite();
        reportDao = new JdbcReportDao(dataSource);
        PenaltyDao penaltyDao = new JdbcPenaltyDao(dataSource);
        Clock clock = Clock.systemUTC();
        ReportService reportService = new ReportService(reportDao, penaltyDao, new CooldownService(clock),
                new DailyLimitService(reportDao, TestConfigs::minimal, clock),
                new PriorityCalculator(TestConfigs::minimal, clock), TestConfigs::minimal, server.getPluginManager(),
                new ImmediateSchedulerAdapter(), Runnable::run, clock, Logger.getLogger("BookReportsTest"));

        command = new ReportCommand(sessions, TestConfigs::minimal, locale, books, rateLimiter, reportService,
                new ImmediateSchedulerAdapter());

        reporter = server.addPlayer("Reporter");
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
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

    @Test
    void suggestsStatusWhenItMatchesThePartialArgument() {
        var suggestions = command.onTabComplete(reporter, null, "report", new String[]{"sta"});

        assertTrue(suggestions.contains("status"));
    }

    @Test
    void statusTellsAReporterTheyHaveNoReportsYet() {
        boolean handled = command.onCommand(reporter, null, "report", new String[]{"status"});

        assertTrue(handled);
        assertTrue(reporter.nextComponentMessage() != null);
    }

    @Test
    void statusListsTheReportersOwnTicketsMostRecentFirst() {
        UUID reporterId = reporter.getUniqueId();
        Report older = reportDao.insert(draft(reporterId, "hacks"));
        Report newer = reportDao.insert(draft(reporterId, "chat_abuse"));
        reportDao.insert(draft(UUID.randomUUID(), "hacks")); // a different reporter's ticket, must not appear

        command.onCommand(reporter, null, "report", new String[]{"status"});

        // First line is the title, then most-recent-first tickets.
        reporter.nextComponentMessage();
        assertEquals("#" + newer.id(), firstToken(reporter.nextComponentMessage()));
        assertEquals("#" + older.id(), firstToken(reporter.nextComponentMessage()));
    }

    private String firstToken(Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return plain.split(" ", 2)[0];
    }

    private Report draft(UUID reporterId, String categoryId) {
        return new Report(0, UUID.randomUUID(), reporterId, "Reporter", UUID.randomUUID(), "Target", categoryId, null,
                null, "default", ReportStatus.PENDING, Priority.LOW, null, null, Instant.now(), null, null, 0, null,
                null, null, null);
    }
}
