package dev.bookreports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.api.event.ReportClaimedEvent;
import dev.bookreports.api.event.ReportCreateEvent;
import dev.bookreports.api.event.ReportCreatedEvent;
import dev.bookreports.api.event.ReportFalseMarkedEvent;
import dev.bookreports.api.event.ReportResolvedEvent;
import dev.bookreports.chat.ChatContextTracker;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.DiscordSettings;
import dev.bookreports.config.FalseReportPenaltySettings;
import dev.bookreports.config.MySqlSettings;
import dev.bookreports.config.PriorityEscalationSettings;
import dev.bookreports.config.PunishmentSettings;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.config.StaffSettings;
import dev.bookreports.config.StorageType;
import dev.bookreports.storage.TestDatabases;
import dev.bookreports.storage.dao.JdbcPenaltyDao;
import dev.bookreports.storage.dao.JdbcReportDao;
import dev.bookreports.storage.dao.PenaltyDao;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import dev.bookreports.util.ImmediateSchedulerAdapter;
import dev.bookreports.util.TextSanitizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final List<ReportCreatedEvent> createdEvents = new ArrayList<>();
    private final List<ReportClaimedEvent> claimedEvents = new ArrayList<>();
    private final List<ReportResolvedEvent> resolvedEvents = new ArrayList<>();
    private final List<ReportFalseMarkedEvent> falseMarkedEvents = new ArrayList<>();

    private ServerMock server;
    private MockPlugin plugin;
    private HikariDataSource dataSource;
    private ReportDao reportDao;
    private PenaltyDao penaltyDao;
    private MutableClock clock;
    private BookReportsConfig config;
    private ReportService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("BookReports");
        server.getPluginManager().registerEvents(new RecordingListener(), plugin);
        dataSource = TestDatabases.freshSqlite();
        reportDao = new JdbcReportDao(dataSource);
        penaltyDao = new JdbcPenaltyDao(dataSource);
        clock = new MutableClock(Instant.parse("2026-01-15T12:00:00Z"));
        config = defaultConfig();

        service = new ReportService(reportDao, penaltyDao, new CooldownService(clock),
                new DailyLimitService(reportDao, () -> config, clock), new PriorityCalculator(() -> config, clock),
                () -> config, server.getPluginManager(), new ImmediateSchedulerAdapter(), Runnable::run, clock,
                Logger.getLogger("BookReportsTest"));
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
        MockBukkit.unmock();
    }

    @Test
    void happyPathPersistsAndFiresCreatedEvent() {
        Report report = submit(request(UUID.randomUUID(), UUID.randomUUID()));

        assertTrue(report.id() > 0);
        assertEquals(ReportStatus.PENDING, report.status());
        assertEquals(1, createdEvents.size());
        assertEquals(report.id(), createdEvents.get(0).report().id());
    }

    @Test
    void evidenceTextIsSanitizedServerSideEvenWhenTheCallerDoesNotGoThroughTheAnvilGui() {
        String malicious = "&c".repeat(60) + "still too long after stripping the color codes above";
        SubmitReportRequest request = new SubmitReportRequest(UUID.randomUUID(), "Reporter", UUID.randomUUID(),
                "Target", "hacks", "killaura", malicious, "default", null);

        Report report = submit(request);

        assertTrue(report.evidenceText().length() <= TextSanitizer.EVIDENCE_MAX_LENGTH);
        assertFalse(report.evidenceText().contains("&c"));
    }

    @Test
    void chatContextIsSanitizedAndCappedServerSide() {
        String longChat = "&c".repeat(60) + "x".repeat(600);
        SubmitReportRequest request = new SubmitReportRequest(UUID.randomUUID(), "Reporter", UUID.randomUUID(),
                "Target", "hacks", "killaura", "evidence", "default", longChat);

        Report report = submit(request);

        assertTrue(report.chatContext().length() <= ChatContextTracker.MAX_TOTAL_LENGTH);
        assertFalse(report.chatContext().contains("&c"));
    }

    @Test
    void selfReportIsRejected() {
        UUID player = UUID.randomUUID();
        ReportRejectedException e = assertRejected(request(player, player));
        assertEquals(ReportRejectedException.Reason.SELF_REPORT, e.reason());
    }

    @Test
    void duplicatePendingReportIsRejected() {
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        submit(request(reporter, target));

        ReportRejectedException e = assertRejected(request(reporter, target));
        assertEquals(ReportRejectedException.Reason.DUPLICATE_PENDING, e.reason());
    }

    @Test
    void cooldownRejectsASecondReportUntilItExpires() {
        UUID reporter = UUID.randomUUID();
        submit(request(reporter, UUID.randomUUID()));

        ReportRejectedException e = assertRejected(request(reporter, UUID.randomUUID()));
        assertEquals(ReportRejectedException.Reason.COOLDOWN, e.reason());

        clock.advance(java.time.Duration.ofSeconds(config.cooldownSeconds() + 1));
        Report second = submit(request(reporter, UUID.randomUUID()));
        assertTrue(second.id() > 0);
    }

    @Test
    void dailyLimitRejectsOnceReached() {
        UUID reporter = UUID.randomUUID();
        config = withDailyLimit(1);
        submit(request(reporter, UUID.randomUUID()));
        clock.advance(java.time.Duration.ofSeconds(config.cooldownSeconds() + 1));

        ReportRejectedException e = assertRejected(request(reporter, UUID.randomUUID()));
        assertEquals(ReportRejectedException.Reason.DAILY_LIMIT, e.reason());
    }

    @Test
    void cancelledCreateEventRejectsTheReport() {
        Listener canceller = new Listener() {
            @EventHandler
            public void onCreate(ReportCreateEvent event) {
                event.setCancelled(true);
            }
        };
        server.getPluginManager().registerEvents(canceller, plugin);

        ReportRejectedException e = assertRejected(request(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(ReportRejectedException.Reason.CANCELLED, e.reason());
    }

    @Test
    void claimSucceedsOnceAndFiresEvent() throws Exception {
        Report report = submit(request(UUID.randomUUID(), UUID.randomUUID()));
        UUID reviewer = UUID.randomUUID();

        boolean first = service.claim(report.id(), reviewer).get(2, TimeUnit.SECONDS);
        boolean second = service.claim(report.id(), UUID.randomUUID()).get(2, TimeUnit.SECONDS);

        assertTrue(first);
        assertFalse(second);
        assertEquals(1, claimedEvents.size());
    }

    @Test
    void resolveUpdatesStatusAndFiresEvent() throws Exception {
        Report report = submit(request(UUID.randomUUID(), UUID.randomUUID()));
        UUID reviewer = UUID.randomUUID();

        boolean updated = service.resolve(report.id(), ReportStatus.RESOLVED_ACTION, reviewer, "Banned").get(2,
                TimeUnit.SECONDS);

        assertTrue(updated);
        assertEquals(1, resolvedEvents.size());
        assertEquals(ReportStatus.RESOLVED_ACTION, reportDao.findById(report.id()).orElseThrow().status());
    }

    @Test
    void resolveRejectsNonTerminalStatuses() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(1, ReportStatus.IN_REVIEW, UUID.randomUUID(), "n/a"));
    }

    @Test
    void markFalseRecordsPenaltyAndFiresEvent() throws Exception {
        UUID reporter = UUID.randomUUID();
        Report report = submit(request(reporter, UUID.randomUUID()));
        UUID reviewer = UUID.randomUUID();

        boolean updated = service.markFalse(report.id(), reviewer, "No evidence").get(2, TimeUnit.SECONDS);

        assertTrue(updated);
        assertEquals(1, falseMarkedEvents.size());
        assertEquals(1, penaltyDao.countByPlayerSince(reporter, "FALSE_REPORT", clock.instant().minusSeconds(60)));
    }

    @Test
    void releaseStaleClaimsReturnsExpiredClaimsToPending() throws Exception {
        Report report = submit(request(UUID.randomUUID(), UUID.randomUUID()));
        UUID reviewer = UUID.randomUUID();
        service.claim(report.id(), reviewer).get(2, TimeUnit.SECONDS);

        int releasedTooEarly = service.releaseStaleClaims().get(2, TimeUnit.SECONDS);
        assertEquals(0, releasedTooEarly);

        clock.advance(java.time.Duration.ofMinutes(config.staff().claimTimeoutMinutes() + 1));
        int released = service.releaseStaleClaims().get(2, TimeUnit.SECONDS);

        assertEquals(1, released);
        assertEquals(ReportStatus.PENDING, reportDao.findById(report.id()).orElseThrow().status());
    }

    @Test
    void isOnCooldownReflectsCooldownServiceWithoutBlocking() {
        UUID reporter = UUID.randomUUID();
        assertFalse(service.isOnCooldown(reporter));

        submit(request(reporter, UUID.randomUUID()));
        assertTrue(service.isOnCooldown(reporter));
    }

    private ReportRejectedException assertRejected(SubmitReportRequest request) {
        try {
            service.submitReport(request).get(2, TimeUnit.SECONDS);
            throw new AssertionError("Expected submitReport to be rejected");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ReportRejectedException,
                    "Expected ReportRejectedException, got " + e.getCause());
            return (ReportRejectedException) e.getCause();
        } catch (InterruptedException | TimeoutException e) {
            throw new AssertionError(e);
        }
    }

    private Report submit(SubmitReportRequest request) {
        try {
            return service.submitReport(request).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(e);
        }
    }

    private final class RecordingListener implements Listener {
        @EventHandler
        public void onCreated(ReportCreatedEvent event) {
            createdEvents.add(event);
        }

        @EventHandler
        public void onClaimed(ReportClaimedEvent event) {
            claimedEvents.add(event);
        }

        @EventHandler
        public void onResolved(ReportResolvedEvent event) {
            resolvedEvents.add(event);
        }

        @EventHandler
        public void onFalseMarked(ReportFalseMarkedEvent event) {
            falseMarkedEvents.add(event);
        }
    }

    private SubmitReportRequest request(UUID reporter, UUID target) {
        return new SubmitReportRequest(reporter, "Reporter", target, "Target", "hacks", "killaura", "evidence",
                "default", null);
    }

    private BookReportsConfig withDailyLimit(int dailyLimit) {
        return new BookReportsConfig(config.storageType(), config.mysql(), config.cooldownSeconds(), dailyLimit,
                config.sessionTimeoutSeconds(), config.preventSelfReport(), config.preventDuplicatePending(),
                config.categories(), config.priorityEscalation(), config.falseReportPenalty(),
                config.enableReportTool(), config.staff(), config.discord(), config.punishments(),
                config.placeholderApiEnabled(), config.locale(), config.serverId());
    }

    private BookReportsConfig defaultConfig() {
        ReportCategory hacks = new ReportCategory("hacks", "Hacks", Priority.HIGH, List.of("killaura"));
        return new BookReportsConfig(StorageType.SQLITE, new MySqlSettings("localhost", 3306, "db", "root", "", 10), 5,
                10, 300, true, true, Map.of("hacks", hacks), new PriorityEscalationSettings(3, 600, Priority.HIGH),
                new FalseReportPenaltySettings(false, 3, 4, 0), false,
                new StaffSettings("ENTITY_EXPERIENCE_ORB_PICKUP", 15), new DiscordSettings(false, "", Priority.HIGH),
                new PunishmentSettings("7d", "1h"), true, "es_ES", "default");
    }
}
