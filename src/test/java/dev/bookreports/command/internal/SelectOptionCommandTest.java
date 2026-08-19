package dev.bookreports.command.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.book.BookBuilder;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.config.TestConfigs;
import dev.bookreports.gui.AnvilInputGUI;
import dev.bookreports.service.CooldownService;
import dev.bookreports.service.DailyLimitService;
import dev.bookreports.service.PriorityCalculator;
import dev.bookreports.service.ReportService;
import dev.bookreports.session.ReportSession;
import dev.bookreports.session.ReportState;
import dev.bookreports.session.SessionManager;
import dev.bookreports.storage.TestDatabases;
import dev.bookreports.storage.dao.JdbcPenaltyDao;
import dev.bookreports.storage.dao.JdbcReportDao;
import dev.bookreports.storage.dao.PenaltyDao;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.model.ReportStatus;
import dev.bookreports.util.ImmediateSchedulerAdapter;
import java.time.Clock;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The security-critical path SPECS.md §13/§4.3 calls out: session ownership, stale-session-id rejection and
 * whitelist-only state transitions. A player pasting or replaying another session's {@code /breport:select} command
 * must never advance a report.
 */
class SelectOptionCommandTest {

    private ServerMock server;
    private MockPlugin plugin;
    private HikariDataSource dataSource;
    private ReportDao reportDao;
    private SessionManager sessions;
    private SelectOptionCommand command;
    private BookReportsConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("BookReports");
        LocaleManager locale = new LocaleManager(plugin);
        locale.load("es_ES");

        dataSource = TestDatabases.freshSqlite();
        reportDao = new JdbcReportDao(dataSource);
        PenaltyDao penaltyDao = new JdbcPenaltyDao(dataSource);
        Clock clock = Clock.systemUTC();
        config = TestConfigs.minimal();

        ReportService reportService = new ReportService(reportDao, penaltyDao, new CooldownService(clock),
                new DailyLimitService(reportDao, () -> config, clock), new PriorityCalculator(() -> config, clock),
                () -> config, server.getPluginManager(), new ImmediateSchedulerAdapter(), Runnable::run, clock,
                Logger.getLogger("BookReportsTest"));

        sessions = new SessionManager(() -> config, clock);
        BookBuilder books = new BookBuilder(locale);
        AnvilInputGUI anvilInputGUI = new AnvilInputGUI(locale);

        command = new SelectOptionCommand(sessions, () -> config, locale, books, reportService, anvilInputGUI,
                new ImmediateSchedulerAdapter(), Logger.getLogger("BookReportsTest"));
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
        MockBukkit.unmock();
    }

    @Test
    void rejectsWhenNoSessionExists() {
        PlayerMock player = server.addPlayer();

        run(player, UUID.randomUUID(), "confirm-target");

        assertTrue(sessions.find(player.getUniqueId()).isEmpty());
    }

    @Test
    void rejectsAStaleSessionIdFromASupersededSession() {
        PlayerMock player = server.addPlayer();
        ReportSession stale = sessions.startWithTarget(player.getUniqueId(), UUID.randomUUID());
        // A second /report supersedes the first — the session id from the first page is now stale.
        sessions.startWithTarget(player.getUniqueId(), UUID.randomUUID());

        run(player, stale.sessionId(), "confirm-target");

        ReportSession current = sessions.find(player.getUniqueId()).orElseThrow();
        assertEquals(ReportState.TARGET_CONFIRM, current.state());
    }

    @Test
    void rejectsAnActionThatSkipsAheadOfTheWhitelist() {
        PlayerMock player = server.addPlayer();
        ReportSession session = sessions.startWithTarget(player.getUniqueId(), UUID.randomUUID());

        run(player, session.sessionId(), "confirm-submit");

        ReportSession stillPending = sessions.find(player.getUniqueId()).orElseThrow();
        assertEquals(ReportState.TARGET_CONFIRM, stillPending.state());
    }

    @Test
    void rejectsAnUnknownCategoryId() {
        PlayerMock player = server.addPlayer();
        ReportSession session = sessions.startWithTarget(player.getUniqueId(), UUID.randomUUID());
        run(player, session.sessionId(), "confirm-target");

        run(player, session.sessionId(), "category:does-not-exist");

        ReportSession stillOnCategory = sessions.find(player.getUniqueId()).orElseThrow();
        assertEquals(ReportState.CATEGORY, stillOnCategory.state());
    }

    @Test
    void rejectsASubReasonThatDoesNotBelongToTheChosenCategory() {
        PlayerMock player = server.addPlayer();
        ReportSession session = sessions.startWithTarget(player.getUniqueId(), UUID.randomUUID());
        run(player, session.sessionId(), "confirm-target");
        run(player, session.sessionId(), "category:hacks");

        run(player, session.sessionId(), "subreason:not-a-real-subreason");

        ReportSession stillOnSubReason = sessions.find(player.getUniqueId()).orElseThrow();
        assertEquals(ReportState.SUBREASON, stillOnSubReason.state());
    }

    @Test
    void cancelInvalidatesTheSessionFromAnyState() {
        PlayerMock player = server.addPlayer();
        ReportSession session = sessions.startWithTarget(player.getUniqueId(), UUID.randomUUID());

        run(player, session.sessionId(), "cancel");

        assertTrue(sessions.find(player.getUniqueId()).isEmpty());
    }

    @Test
    void happyPathAdvancesThroughEveryStateAndPersistsTheReport() {
        PlayerMock player = server.addPlayer();
        UUID target = UUID.randomUUID();
        ReportSession session = sessions.startWithTarget(player.getUniqueId(), target);

        run(player, session.sessionId(), "confirm-target");
        assertEquals(ReportState.CATEGORY, sessions.find(player.getUniqueId()).orElseThrow().state());

        run(player, session.sessionId(), "category:hacks");
        assertEquals(ReportState.SUBREASON, sessions.find(player.getUniqueId()).orElseThrow().state());

        run(player, session.sessionId(), "subreason:killaura");
        assertEquals(ReportState.EVIDENCE, sessions.find(player.getUniqueId()).orElseThrow().state());

        run(player, session.sessionId(), "evidence-skip");
        assertEquals(ReportState.SUMMARY, sessions.find(player.getUniqueId()).orElseThrow().state());

        run(player, session.sessionId(), "confirm-submit");

        assertTrue(sessions.find(player.getUniqueId()).isEmpty());
        var reports = reportDao.findByTarget(target);
        assertEquals(1, reports.size());
        assertEquals("hacks", reports.get(0).categoryId());
        assertEquals("killaura", reports.get(0).subReasonId());
        assertEquals(ReportStatus.PENDING, reports.get(0).status());
    }

    @Test
    void categoryWithoutSubReasonsSkipsStraightToEvidence() {
        PlayerMock player = server.addPlayer();
        ReportSession session = sessions.startWithTarget(player.getUniqueId(), UUID.randomUUID());
        run(player, session.sessionId(), "confirm-target");

        run(player, session.sessionId(), "category:other");

        assertEquals(ReportState.EVIDENCE, sessions.find(player.getUniqueId()).orElseThrow().state());
    }

    private void run(PlayerMock player, UUID sessionId, String actionId) {
        command.onCommand(player, null, "select", new String[]{sessionId.toString(), actionId});
    }
}
