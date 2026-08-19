package dev.bookreports.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.DiscordSettings;
import dev.bookreports.config.FalseReportPenaltySettings;
import dev.bookreports.config.MySqlSettings;
import dev.bookreports.config.PriorityEscalationSettings;
import dev.bookreports.config.StaffSettings;
import dev.bookreports.config.StorageType;
import dev.bookreports.storage.TestDatabases;
import dev.bookreports.storage.dao.JdbcReportDao;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailyLimitServiceTest {

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
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

    private HikariDataSource dataSource;
    private ReportDao reportDao;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabases.freshSqlite();
        reportDao = new JdbcReportDao(dataSource);
        clock = new MutableClock(Instant.parse("2026-01-15T23:59:00Z"));
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void reachesLimitAfterConfiguredNumberOfReportsToday() {
        DailyLimitService service = new DailyLimitService(reportDao, () -> configWithDailyLimit(2), clock);
        UUID reporter = UUID.randomUUID();

        assertFalse(service.hasReachedDailyLimit(reporter));
        insertReport(reporter, clock.instant());
        service.invalidate(reporter);
        assertFalse(service.hasReachedDailyLimit(reporter));

        insertReport(reporter, clock.instant());
        service.invalidate(reporter);
        assertTrue(service.hasReachedDailyLimit(reporter));
    }

    @Test
    void countResetsAtUtcMidnight() {
        DailyLimitService service = new DailyLimitService(reportDao, () -> configWithDailyLimit(1), clock);
        UUID reporter = UUID.randomUUID();

        insertReport(reporter, clock.instant());
        service.invalidate(reporter);
        assertTrue(service.hasReachedDailyLimit(reporter));

        // Cross into the next UTC day — yesterday's report must no longer count.
        clock.set(Instant.parse("2026-01-16T00:00:01Z"));
        service.invalidate(reporter);
        assertFalse(service.hasReachedDailyLimit(reporter));
    }

    @Test
    void limitOfZeroMeansUnlimited() {
        DailyLimitService service = new DailyLimitService(reportDao, () -> configWithDailyLimit(0), clock);
        UUID reporter = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            insertReport(reporter, clock.instant());
        }
        service.invalidate(reporter);

        assertFalse(service.hasReachedDailyLimit(reporter));
    }

    private void insertReport(UUID reporter, Instant createdAt) {
        reportDao.insert(new Report(0, UUID.randomUUID(), reporter, "Reporter", UUID.randomUUID(), "Target", "hacks",
                null, null, "default", ReportStatus.PENDING, Priority.HIGH, null, null, createdAt, null, null, 0));
    }

    private BookReportsConfig configWithDailyLimit(int dailyLimit) {
        return new BookReportsConfig(StorageType.SQLITE, new MySqlSettings("localhost", 3306, "db", "root", "", 10),
                120, dailyLimit, 300, true, true, Map.of(), new PriorityEscalationSettings(3, 600, Priority.HIGH),
                new FalseReportPenaltySettings(true, 3, 4, 0), false,
                new StaffSettings("ENTITY_EXPERIENCE_ORB_PICKUP", 15), new DiscordSettings(false, "", Priority.HIGH),
                true, "es_ES", "default");
    }
}
