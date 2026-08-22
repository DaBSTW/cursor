package dev.bookreports.storage.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bookreports.storage.TestDatabases;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ROADMAP.md Fase 7: seeds 500 reports (SPECS.md §15's load-test target) and measures the staff queue's page query and
 * a target's history lookup. Bounds are deliberately generous — this guards against a missing index regressing to a
 * full table scan, not a strict performance SLA.
 */
class ReportQueueLoadTest {

    private static final int SEED_COUNT = 500;

    private DataSource dataSource;
    private ReportDao dao;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabases.freshSqlite();
        dao = new JdbcReportDao(dataSource);
    }

    @AfterEach
    void tearDown() {
        ((com.zaxxer.hikari.HikariDataSource) dataSource).close();
    }

    @Test
    void staffQueuePageAndTargetHistoryStayFastWith500SeededReports() {
        UUID hotTarget = UUID.randomUUID();
        for (int i = 0; i < SEED_COUNT; i++) {
            UUID target = i % 20 == 0 ? hotTarget : UUID.randomUUID();
            dao.insert(draft(target, i % 3 == 0 ? Priority.HIGH : Priority.LOW));
        }

        long queueStart = System.nanoTime();
        var page = dao.findByStatus(ReportStatus.PENDING, 0, 45);
        long queueMillis = (System.nanoTime() - queueStart) / 1_000_000;

        long historyStart = System.nanoTime();
        var history = dao.findByTarget(hotTarget);
        long historyMillis = (System.nanoTime() - historyStart) / 1_000_000;

        assertEquals(45, page.size());
        assertEquals(25, history.size());
        assertTrue(queueMillis < 2000, "Queue page took " + queueMillis + "ms for " + SEED_COUNT + " reports");
        assertTrue(historyMillis < 2000, "Target history took " + historyMillis + "ms for " + SEED_COUNT + " reports");
    }

    private Report draft(UUID target, Priority priority) {
        return new Report(0, UUID.randomUUID(), UUID.randomUUID(), "Reporter", target, "Target", "hacks", null, null,
                "default", ReportStatus.PENDING, priority, null, null, Instant.now(), null, null, 0, null, null, null,
                null);
    }
}
