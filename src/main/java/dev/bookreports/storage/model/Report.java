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
 * {@code chatContext}, {@code reviewerUuid}, {@code resolutionNote}, {@code claimedAt}, {@code resolvedAt},
 * {@code sanctionType}, {@code sanctionDuration} and {@code coreProtectContext} are nullable. {@code chatContext} and
 * {@code coreProtectContext} are captured automatically (recent chat and recent CoreProtect-logged block activity,
 * respectively) — unlike {@code evidenceText}, which the reporter typed in themselves. {@code sanctionType} and
 * {@code sanctionDuration} record what a punishment bridge actually applied (e.g. {@code "BAN"}/{@code "7d"}), set only
 * when a report is resolved through the staff panel's sanction menu. {@code targetLocation}/{@code reporterLocation}
 * are a snapshot of both players' positions at submission time, encoded by {@link dev.bookreports.util.LocationCodec} —
 * a single delimited string rather than separate numeric columns, same compact-auxiliary-data pattern as
 * {@code chatContext}/{@code coreProtectContext}. Nullable: the target snapshot is skipped entirely for an offline
 * target (see {@code ReportCommand}'s offline-reporting path), and either can fail to decode later if its world was
 * since removed. {@code archived} hides an already-resolved report from the staff queue without deleting it —
 * everything about the report, including its notes, stays intact and reachable by direct id/uuid lookup.
 */
public record Report(long id, UUID uuid, UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
        String categoryId, String subReasonId, String evidenceText, String server, ReportStatus status,
        Priority priority, UUID reviewerUuid, String resolutionNote, Instant createdAt, Instant claimedAt,
        Instant resolvedAt, int claimVersion, String chatContext, String sanctionType, String sanctionDuration,
        String coreProtectContext, String targetLocation, String reporterLocation, boolean archived) {

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
