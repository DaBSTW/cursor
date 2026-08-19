package dev.bookreports.storage.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.storage.TestDatabases;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcReportDaoTest {

    private HikariDataSource dataSource;
    private ReportDao dao;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabases.freshSqlite();
        dao = new JdbcReportDao(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void insertAssignsIdAndIsReadableByIdAndUuid() {
        Report inserted = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));

        assertTrue(inserted.id() > 0);
        assertEquals(inserted, dao.findById(inserted.id()).orElseThrow());
        assertEquals(inserted, dao.findByUuid(inserted.uuid()).orElseThrow());
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertTrue(dao.findById(999).isEmpty());
    }

    @Test
    void findByTargetOrdersMostRecentFirst() throws InterruptedException {
        UUID target = UUID.randomUUID();
        Report first = dao.insert(draft(UUID.randomUUID(), target));
        Thread.sleep(15);
        Report second = dao.insert(draft(UUID.randomUUID(), target));

        List<Report> history = dao.findByTarget(target);

        assertEquals(List.of(second.uuid(), first.uuid()), history.stream().map(Report::uuid).toList());
    }

    @Test
    void findByStatusPaginates() {
        UUID target = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            dao.insert(draft(UUID.randomUUID(), target));
        }

        assertEquals(2, dao.findByStatus(ReportStatus.PENDING, 0, 2).size());
        assertEquals(1, dao.findByStatus(ReportStatus.PENDING, 1, 2).size());
        assertTrue(dao.findByStatus(ReportStatus.RESOLVED_ACTION, 0, 10).isEmpty());
    }

    @Test
    void countByReporterSinceOnlyCountsWithinTheWindow() {
        UUID reporter = UUID.randomUUID();
        dao.insert(draft(reporter, UUID.randomUUID()));

        assertEquals(1, dao.countByReporterSince(reporter, Instant.now().minusSeconds(60)));
        assertEquals(0, dao.countByReporterSince(reporter, Instant.now().plusSeconds(60)));
    }

    @Test
    void updateStatusSetsResolvedAtOnlyForTerminalStatuses() {
        Report report = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        UUID reviewer = UUID.randomUUID();

        assertTrue(dao.updateStatus(report.id(), ReportStatus.RESOLVED_ACTION, reviewer, "Banned", Instant.now()));

        Report resolved = dao.findById(report.id()).orElseThrow();
        assertEquals(ReportStatus.RESOLVED_ACTION, resolved.status());
        assertEquals(reviewer, resolved.reviewerUuid());
        assertEquals("Banned", resolved.resolutionNote());
        assertTrue(resolved.isResolved());
        assertNotNull(resolved.resolvedAt());
    }

    @Test
    void updateStatusReturnsFalseForUnknownId() {
        assertFalse(dao.updateStatus(999, ReportStatus.RESOLVED_REJECTED, null, "n/a", Instant.now()));
    }

    @Test
    void claimSucceedsOnceAndRejectsASecondReviewer() {
        Report report = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(dao.claim(report.id(), first, Instant.now()));
        assertFalse(dao.claim(report.id(), second, Instant.now()));

        Report claimed = dao.findById(report.id()).orElseThrow();
        assertEquals(first, claimed.reviewerUuid());
        assertEquals(ReportStatus.IN_REVIEW, claimed.status());
        assertEquals(1, claimed.claimVersion());
    }

    @Test
    void findStaleClaimsOnlyReturnsClaimsOlderThanTheCutoff() {
        Report fresh = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        Report stale = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        dao.claim(fresh.id(), UUID.randomUUID(), Instant.now());
        dao.claim(stale.id(), UUID.randomUUID(), Instant.now().minusSeconds(3600));

        List<Report> found = dao.findStaleClaims(Instant.now().minusSeconds(1800));

        assertEquals(1, found.size());
        assertEquals(stale.id(), found.get(0).id());
    }

    @Test
    void releaseClaimReturnsItToPendingAndOnlyOnce() {
        Report report = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        UUID reviewer = UUID.randomUUID();
        dao.claim(report.id(), reviewer, Instant.now());

        assertTrue(dao.releaseClaim(report.id(), reviewer));

        Report released = dao.findById(report.id()).orElseThrow();
        assertEquals(ReportStatus.PENDING, released.status());
        assertNull(released.reviewerUuid());

        assertFalse(dao.releaseClaim(report.id(), reviewer));
    }

    private Report draft(UUID reporter, UUID target) {
        return new Report(0, UUID.randomUUID(), reporter, "Reporter", target, "Target", "hacks", "killaura", null,
                "default", ReportStatus.PENDING, Priority.HIGH, null, null, Instant.now(), null, null, 0);
    }
}
