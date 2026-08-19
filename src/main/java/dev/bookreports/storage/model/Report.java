package dev.bookreports.storage.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A player report, mapped 1:1 to a {@code br_reports} row.
 *
 * <p>
 * Before the first {@link dev.bookreports.storage.dao.ReportDao#insert} call, {@code id} is {@code 0} and
 * {@code claimVersion} is {@code 0} — both are assigned by the database. {@code subReasonId}, {@code evidenceText},
 * {@code reviewerUuid}, {@code resolutionNote}, {@code claimedAt} and {@code resolvedAt} are nullable.
 */
public record Report(long id, UUID uuid, UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
        String categoryId, String subReasonId, String evidenceText, String server, ReportStatus status,
        Priority priority, UUID reviewerUuid, String resolutionNote, Instant createdAt, Instant claimedAt,
        Instant resolvedAt, int claimVersion) {

    public Report {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(reporterUuid, "reporterUuid");
        Objects.requireNonNull(reporterName, "reporterName");
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public boolean isResolved() {
        return status != ReportStatus.PENDING && status != ReportStatus.IN_REVIEW;
    }
}
