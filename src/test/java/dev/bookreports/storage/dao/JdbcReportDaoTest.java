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
    void chatContextRoundTripsThroughInsertAndRead() {
        Report withContext = new Report(0, UUID.randomUUID(), UUID.randomUUID(), "Reporter", UUID.randomUUID(),
                "Target", "chat_abuse", null, null, "default", ReportStatus.PENDING, Priority.MEDIUM, null, null,
                Instant.now(), null, null, 0, "hello | world", null, null, null);

        Report inserted = dao.insert(withContext);

        assertEquals("hello | world", dao.findById(inserted.id()).orElseThrow().chatContext());
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
    void findByStatusOrdersBySeverityNotAlphabetically() {
        UUID target = UUID.randomUUID();
        dao.insert(draftWithPriority(target, Priority.LOW));
        dao.insert(draftWithPriority(target, Priority.HIGH));
        dao.insert(draftWithPriority(target, Priority.MEDIUM));

        List<Report> queue = dao.findByStatus(ReportStatus.PENDING, 0, 10);

        assertEquals(List.of(Priority.HIGH, Priority.MEDIUM, Priority.LOW),
                queue.stream().map(Report::priority).toList());
    }

    @Test
    void findByStatusFiltersByCategoryWhenGiven() {
        UUID target = UUID.randomUUID();
        Report hacks = dao.insert(draft(UUID.randomUUID(), target));
        Report other = dao.insert(new Report(0, UUID.randomUUID(), UUID.randomUUID(), "Reporter", target, "Target",
                "chat_abuse", null, null, "default", ReportStatus.PENDING, Priority.LOW, null, null, Instant.now(),
                null, null, 0, null, null, null, null));

        List<Report> hacksOnly = dao.findByStatus(ReportStatus.PENDING, "hacks", 0, 10);

        assertEquals(1, hacksOnly.size());
        assertEquals(hacks.id(), hacksOnly.get(0).id());
        assertTrue(dao.findByStatus(ReportStatus.PENDING, "chat_abuse", 0, 10).stream()
                .anyMatch(r -> r.id() == other.id()));
    }

    private Report draftWithPriority(UUID target, Priority priority) {
        return new Report(0, UUID.randomUUID(), UUID.randomUUID(), "Reporter", target, "Target", "hacks", null, null,
                "default", ReportStatus.PENDING, priority, null, null, Instant.now(), null, null, 0, null, null, null,
                null);
    }

    @Test
    void countByStatusOnlyCountsMatchingStatus() {
        dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(2, dao.countByStatus(ReportStatus.PENDING));
        assertEquals(0, dao.countByStatus(ReportStatus.RESOLVED_ACTION));
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
    void updateStatusRejectsResolvingAnAlreadyResolvedReport() {
        Report report = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        UUID firstReviewer = UUID.randomUUID();
        UUID secondReviewer = UUID.randomUUID();

        assertTrue(dao.updateStatus(report.id(), ReportStatus.RESOLVED_ACTION, firstReviewer, "Banned", Instant.now()));
        // A second staff member resolving the same report concurrently must not overwrite the first resolution.
        assertFalse(dao.updateStatus(report.id(), ReportStatus.RESOLVED_REJECTED, secondReviewer, "No evidence",
                Instant.now()));

        Report stillFirst = dao.findById(report.id()).orElseThrow();
        assertEquals(ReportStatus.RESOLVED_ACTION, stillFirst.status());
        assertEquals(firstReviewer, stillFirst.reviewerUuid());
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

    @Test
    void reporterStatsCountsActionedAndRejectedButNotDuplicates() {
        UUID reporter = UUID.randomUUID();
        Report actioned = dao.insert(draft(reporter, UUID.randomUUID()));
        Report rejected = dao.insert(draft(reporter, UUID.randomUUID()));
        Report duplicate = dao.insert(draft(reporter, UUID.randomUUID()));
        dao.insert(draft(reporter, UUID.randomUUID())); // still PENDING, shouldn't count either way
        dao.updateStatus(actioned.id(), ReportStatus.RESOLVED_ACTION, UUID.randomUUID(), "banned", Instant.now());
        dao.updateStatus(rejected.id(), ReportStatus.RESOLVED_REJECTED, UUID.randomUUID(), "no evidence",
                Instant.now());
        dao.updateStatus(duplicate.id(), ReportStatus.RESOLVED_DUPLICATE, UUID.randomUUID(), "dup", Instant.now());

        var stats = dao.reporterStats(reporter);

        assertEquals(4, stats.total());
        assertEquals(1, stats.actioned());
        assertEquals(1, stats.rejectedOrFalse());
        assertEquals(50, stats.accuracyPercent());
    }

    @Test
    void reporterStatsIsZeroForAReporterWithNoHistory() {
        var stats = dao.reporterStats(UUID.randomUUID());

        assertEquals(0, stats.total());
        assertEquals(0, stats.accuracyPercent());
    }

    @Test
    void staffStatsCountsResolvedReportsAndAverageResolutionTime() {
        UUID reviewer = UUID.randomUUID();
        Report first = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        Report second = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));
        Instant claimedAt = Instant.now().minusSeconds(600);
        dao.claim(first.id(), reviewer, claimedAt);
        dao.claim(second.id(), reviewer, claimedAt);
        dao.updateStatus(first.id(), ReportStatus.RESOLVED_ACTION, reviewer, "banned", claimedAt.plusSeconds(60));
        dao.updateStatus(second.id(), ReportStatus.RESOLVED_ACTION, reviewer, "banned", claimedAt.plusSeconds(180));

        var stats = dao.staffStats(reviewer);

        assertEquals(2, stats.resolvedCount());
        assertEquals(2.0, stats.avgResolutionMinutes(), 0.01);
    }

    private Report draft(UUID reporter, UUID target) {
        return new Report(0, UUID.randomUUID(), reporter, "Reporter", target, "Target", "hacks", "killaura", null,
                "default", ReportStatus.PENDING, Priority.HIGH, null, null, Instant.now(), null, null, 0, null, null,
                null, null);
    }

    @Test
    void recordSanctionSetsBothColumnsAndRoundTripsThroughRead() {
        Report report = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));

        assertTrue(dao.recordSanction(report.id(), "BAN", "7d"));

        Report reread = dao.findById(report.id()).orElseThrow();
        assertEquals("BAN", reread.sanctionType());
        assertEquals("7d", reread.sanctionDuration());
    }

    @Test
    void recordSanctionAllowsANullDurationForKicks() {
        Report report = dao.insert(draft(UUID.randomUUID(), UUID.randomUUID()));

        assertTrue(dao.recordSanction(report.id(), "KICK", null));

        assertNull(dao.findById(report.id()).orElseThrow().sanctionDuration());
    }

    @Test
    void recordSanctionReturnsFalseForUnknownId() {
        assertFalse(dao.recordSanction(999, "BAN", "7d"));
    }

    @Test
    void findByReporterOrdersMostRecentFirstAndRespectsLimit() throws InterruptedException {
        UUID reporter = UUID.randomUUID();
        Report first = dao.insert(draft(reporter, UUID.randomUUID()));
        Thread.sleep(15);
        Report second = dao.insert(draft(reporter, UUID.randomUUID()));
        dao.insert(draft(UUID.randomUUID(), UUID.randomUUID())); // different reporter, must not appear

        List<Report> own = dao.findByReporter(reporter, 1);

        assertEquals(List.of(second.id()), own.stream().map(Report::id).toList());
    }

    @Test
    void findByStatusFiltersByPriorityTargetNameAndClaimedBy() {
        UUID target = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        Report matching = dao.insert(new Report(0, UUID.randomUUID(), UUID.randomUUID(), "Reporter", target, "Steve",
                "hacks", null, null, "default", ReportStatus.PENDING, Priority.HIGH, null, null, Instant.now(), null,
                null, 0, null, null, null, null));
        dao.claim(matching.id(), reviewer, Instant.now());
        dao.insert(draftWithPriority(UUID.randomUUID(), Priority.LOW));

        List<Report> byPriority = dao.findByStatus(ReportStatus.IN_REVIEW, null, Priority.HIGH, null, null, 0, 10);
        assertEquals(1, byPriority.size());
        assertEquals(matching.id(), byPriority.get(0).id());

        List<Report> byName = dao.findByStatus(ReportStatus.IN_REVIEW, null, null, "tev", null, 0, 10);
        assertEquals(1, byName.size());
        assertEquals(matching.id(), byName.get(0).id());
        assertTrue(dao.findByStatus(ReportStatus.IN_REVIEW, null, null, "nosuchname", null, 0, 10).isEmpty());

        List<Report> byClaimedBy = dao.findByStatus(ReportStatus.IN_REVIEW, null, null, null, reviewer, 0, 10);
        assertEquals(1, byClaimedBy.size());
        assertEquals(matching.id(), byClaimedBy.get(0).id());
        assertTrue(dao.findByStatus(ReportStatus.IN_REVIEW, null, null, null, UUID.randomUUID(), 0, 10).isEmpty());
    }
}
